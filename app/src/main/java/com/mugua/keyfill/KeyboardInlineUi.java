package com.mugua.keyfill;

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.inputmethodservice.InputMethodService;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Size;
import android.view.AttachedSurfaceControl;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.SurfaceControl;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.inputmethod.InlineSuggestion;
import android.view.inputmethod.InlineSuggestionsRequest;
import android.view.inputmethod.InlineSuggestionsResponse;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.inline.InlineContentView;
import android.widget.inline.InlinePresentationSpec;

import androidx.autofill.inline.UiVersions;
import androidx.autofill.inline.common.ImageViewStyle;
import androidx.autofill.inline.common.TextViewStyle;
import androidx.autofill.inline.common.ViewStyle;
import androidx.autofill.inline.v1.InlineSuggestionUi;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

final class KeyboardInlineUi {
    private static final int MAX_SUGGESTIONS = 6;
    private static final int CARD_HEIGHT_DP = 40;
    private static final int BAR_PADDING_DP = 10;
    private static final int PINNED_GAP_DP = 8;
    private static final int MATCH_PARENT = ViewGroup.LayoutParams.MATCH_PARENT;
    private static final int WRAP_CONTENT = ViewGroup.LayoutParams.WRAP_CONTENT;

    private final InputMethodService service;
    private final KeyboardTarget target;
    private final Handler main = new Handler(Looper.getMainLooper());
    private List<InlineSuggestion> pending = List.of();
    private View strip;
    private int responseVersion;

    KeyboardInlineUi(InputMethodService service, KeyboardTarget target) {
        this.service = service;
        this.target = target;
    }

    InlineSuggestionsRequest createRequest() {
        boolean systemDark = isNightMode(Resources.getSystem().getConfiguration());
        boolean imeDark = isDarkTheme();
        Bundle styles = UiVersions.newStylesBuilder().addStyle(suggestionStyle(imeDark)).build();
        if (imeDark == systemDark) {
            styles.putBundle(InlineThemeRenderer.LIGHT_STYLE,
                    UiVersions.newStylesBuilder().addStyle(suggestionStyle(false)).build());
            styles.putBundle(InlineThemeRenderer.DARK_STYLE,
                    UiVersions.newStylesBuilder().addStyle(suggestionStyle(true)).build());
        }
        InlinePresentationSpec card = new InlinePresentationSpec.Builder(
                new Size(dp(32), dp(26)), new Size(dp(360), dp(CARD_HEIGHT_DP)))
                .setStyle(styles)
                .build();
        AutofillModule.log("Inline request created");
        return new InlineSuggestionsRequest.Builder(List.of(card))
                .setMaxSuggestionCount(MAX_SUGGESTIONS)
                .build();
    }

    @SuppressLint("RestrictedApi") // AndroidX 1.3.0 encodes this styling in the system's inline view.
    private InlineSuggestionUi.Style suggestionStyle(boolean dark) {
        int titleColor = dark ? 0xfff5f5f5 : 0xff222222;
        int subtitleColor = dark ? 0xffc4c4c4 : 0xff595959;
        int iconPadding = dp(target == KeyboardTarget.XIAO_AI ? 5 : 8);
        return InlineSuggestionUi.newStyleBuilder()
                .setChipStyle(new ViewStyle.Builder()
                        .setBackgroundColor(Color.TRANSPARENT)
                        .setPadding(dp(13), dp(5), dp(13), dp(5))
                        .build())
                .setSingleIconChipStyle(new ViewStyle.Builder()
                        .setBackgroundColor(Color.TRANSPARENT)
                        .setPadding(iconPadding, 0, iconPadding, 0)
                        .build())
                .setSingleIconChipIconStyle(new ImageViewStyle.Builder()
                        .setMaxWidth(dp(21))
                        .setMaxHeight(dp(21))
                        .build())
                .setTitleStyle(new TextViewStyle.Builder()
                        .setTextColor(titleColor)
                        .setTypeface("sans-serif-medium", Typeface.NORMAL)
                        .setTextSize(14)
                        .build())
                .setSubtitleStyle(new TextViewStyle.Builder()
                        .setTextColor(subtitleColor)
                        .setTextSize(12)
                        .build())
                .build();
    }

    void receive(InlineSuggestionsResponse response) {
        clear();
        pending = response.getInlineSuggestions();
        if (!pending.isEmpty()) {
            AutofillModule.log("Inline suggestions received");
            showWhenLaidOut();
        }
    }

    void onKeyboardShown() {
        showWhenLaidOut();
    }

    void onInputViewReplaced() {
        if (strip != null && !strip.isAttachedToWindow()) {
            strip = null;
        }
        showWhenLaidOut();
    }

    void onKeyboardHidden() {
        clear();
    }

    void clear() {
        responseVersion++;
        pending = List.of();
        if (strip != null && strip.getParent() instanceof ViewGroup parent) {
            parent.removeView(strip);
        }
        strip = null;
    }

    private void showWhenLaidOut() {
        if (pending.isEmpty()) {
            return;
        }
        int version = responseVersion;
        main.post(() -> tryRender(version, 20));
    }

    private void tryRender(int version, int attempts) {
        if (version != responseVersion || pending.isEmpty()) {
            return;
        }
        Dialog window = service.getWindow();
        if (window != null && window.getWindow() != null) {
            View decor = window.getWindow().getDecorView();
            ViewGroup candidate = findCandidate(decor);
            if (candidate == null && target == KeyboardTarget.XIAO_AI) {
                candidate = findXiaoAiRootView();
            }
            if (candidate != null && candidate.getWidth() > 0 && candidate.getHeight() > 0 && candidate.isShown()
                    && candidate.isAttachedToWindow()) {
                render(candidate);
                return;
            }
        }
        if (attempts > 1) {
            main.postDelayed(() -> tryRender(version, attempts - 1), 24);
        } else {
            AutofillModule.log("Inline candidate area unavailable");
        }
    }

    private void render(ViewGroup candidate) {
        List<InlineSuggestion> suggestions = pending;
        pending = List.of();
        int version = responseVersion;
        Context context = candidate.getContext();
        boolean dark = isDarkTheme();
        if (target != KeyboardTarget.WETYPE && target != KeyboardTarget.XIAO_AI) {
            renderImeToolbar(candidate, suggestions, version, dark);
            return;
        }
        boolean weType = target == KeyboardTarget.WETYPE;
        XiaoAiToolbar xiaoAi = weType ? null : XiaoAiToolbar.bind(service, candidate);
        if (!weType && xiaoAi == null) {
            return;
        }
        View logo = weType ? keyboardView(candidate, "getLogoContainerRl") : null;
        int logoInset = 0;
        if (logo != null && logo.getWidth() > 0) {
            Rect bounds = new Rect(0, 0, logo.getWidth(), logo.getHeight());
            candidate.offsetDescendantRectToMyCoords(logo, bounds);
            logoInset = Math.max(0, bounds.right - candidate.getPaddingLeft());
        }
        int leftPadding = logoInset > 0 ? 0 : dp(BAR_PADDING_DP);
        int rightPadding = dp(BAR_PADDING_DP);
        int availableHeight = candidate.getHeight();
        if (xiaoAi != null) {
            leftPadding = xiaoAi.edgePadding + xiaoAi.toolSize + dp(PINNED_GAP_DP);
            rightPadding = xiaoAi.edgePadding;
            availableHeight = xiaoAi.height;
        }
        int overlayInset = xiaoAi == null ? 0 : leftPadding;
        boolean hasPinned = suggestions.stream().anyMatch(suggestion -> suggestion.getInfo().isPinned());
        int cardCount = suggestions.size() - (hasPinned ? 1 : 0);
        boolean singleCard = cardCount == 1;
        LinearLayout bar = weType ? new WeTypeSuggestionBar(candidate) : xiaoAi;
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(weType ? leftPadding : 0, 0, rightPadding, 0);
        int backgroundColor = barColor(candidate, dark);
        boolean pinnedOnly = cardCount == 0;
        if (weType) {
            bar.setBackgroundColor(Color.TRANSPARENT);
        }
        RelativeLayout.LayoutParams rowLayout = new RelativeLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT);
        rowLayout.addRule(RelativeLayout.ALIGN_PARENT_RIGHT);
        if (logo != null && logo.getParent() == candidate && logo.getId() != View.NO_ID) {
            rowLayout.addRule(RelativeLayout.RIGHT_OF, logo.getId());
        } else {
            rowLayout.addRule(RelativeLayout.ALIGN_PARENT_LEFT);
            rowLayout.leftMargin = logoInset;
        }

        LinearLayout cards = new LinearLayout(context);
        cards.setGravity(singleCard ? Gravity.CENTER : Gravity.CENTER_VERTICAL);
        SuggestionScroller scroller = new SuggestionScroller(context, cards);
        bar.addView(scroller, new LinearLayout.LayoutParams(0, MATCH_PARENT, 1));

        LinearLayout pinned = new LinearLayout(context);
        pinned.setGravity(Gravity.CENTER_VERTICAL);
        if (xiaoAi != null) {
            xiaoAi.setToolbarBackground(pinnedOnly ? pinned : bar, backgroundColor, dark);
        } else if (pinnedOnly && weType) {
            pinned.setBackgroundColor(Color.TRANSPARENT);
        }
        bar.addView(pinned, new LinearLayout.LayoutParams(WRAP_CONTENT, MATCH_PARENT));

        Drawable.ConstantState toolbarBackground = weType ? toolbarBackground(candidate) : null;
        int cardHeight = weType ? Math.min(dp(CARD_HEIGHT_DP), Math.max(dp(28), availableHeight - dp(6)))
                : xiaoAi.toolSize;
        int availableWidth = candidate.getWidth() - logoInset - candidate.getPaddingLeft()
                - candidate.getPaddingRight() - leftPadding - rightPadding;
        if (hasPinned) {
            availableWidth -= cardHeight + dp(PINNED_GAP_DP);
        }
        for (InlineSuggestion suggestion : suggestions) {
            boolean isPinned = suggestion.getInfo().isPinned() && pinned.getChildCount() == 0;
            ViewGroup destination = isPinned ? pinned : cards;
            SuggestionSlot slot = new SuggestionSlot(context);
            if (xiaoAi == null || !isPinned) {
                Drawable background = xiaoAi != null ? xiaoAi.cardBackground()
                        : toolbarBackground == null ? null
                        : toolbarBackground.newDrawable(context.getResources(), context.getTheme()).mutate();
                slot.setBackground(background == null ? cardBackground(isPinned, dark) : background);
            }
            slot.setVisibility(View.GONE);
            int width = isPinned ? cardHeight : WRAP_CONTENT;
            LinearLayout.LayoutParams spacing = new LinearLayout.LayoutParams(width, cardHeight);
            if (isPinned) {
                spacing.setMarginStart(dp(PINNED_GAP_DP));
            } else if (!singleCard) {
                spacing.setMarginEnd(dp(7));
            }
            destination.addView(slot, spacing);
            int renderWidth = WRAP_CONTENT;
            if (singleCard && !isPinned) {
                InlinePresentationSpec spec = suggestion.getInfo().getInlinePresentationSpec();
                if (availableWidth >= spec.getMinSize().getWidth()) {
                    renderWidth = Math.min(availableWidth, spec.getMaxSize().getWidth());
                }
            }
            try {
                suggestion.inflate(context, new Size(renderWidth, WRAP_CONTENT),
                        service.getMainExecutor(), card -> {
                            if (version != responseVersion || card == null
                                    || !candidate.isShown() || !candidate.isAttachedToWindow()) {
                                return;
                            }
                            ViewGroup.LayoutParams size = card.getLayoutParams();
                            if (!isPinned) {
                                scroller.clipSurface(card);
                            }
                            slot.addView(card, new FrameLayout.LayoutParams(size.width, size.height,
                                    Gravity.CENTER));
                            slot.setVisibility(View.VISIBLE);
                            if (strip == null) {
                                strip = attachStrip(candidate, bar, rowLayout, overlayInset);
                                AutofillModule.log("Inline card attached in " + target.packageName);
                            }
                        });
            } catch (IllegalArgumentException | IllegalStateException failure) {
                AutofillModule.log("Inline card could not be rendered");
            }
        }
    }

    private RippleDrawable cardBackground(boolean pinned, boolean dark) {
        GradientDrawable shape = new GradientDrawable();
        shape.setCornerRadius(dp(pinned ? 13 : 17));
        shape.setColor(dark ? 0xff3d3d3d : 0xfff5f5f5);
        shape.setStroke(dp(1), dark ? 0xff4b4b4b : 0xffd4d5d9);
        return new RippleDrawable(ColorStateList.valueOf(dark ? 0x24ffffff : 0x19000000), shape, null);
    }

    private int barColor(ViewGroup candidate, boolean dark) {
        if (target == KeyboardTarget.WETYPE) {
            if (candidate.getBackground() instanceof ColorDrawable background && background.getAlpha() == 255) {
                return background.getColor();
            }
            return dark ? 0xff202020 : 0xffe3e4e9;
        }
        return dark ? 0xff18191b : 0xffe5e6e7;
    }

    private void renderImeToolbar(ViewGroup candidate, List<InlineSuggestion> suggestions,
            int version, boolean dark) {
        ImeToolbar toolbar = switch (target) {
            case SOGOU -> SogouToolbar.bind(candidate, dark);
            case BAIDU -> BaiduToolbar.bind(service, candidate, dark);
            case IFLYTEK -> IflytekToolbar.bind(service, candidate, dark);
            case DOUBAO -> DoubaoToolbar.bind(candidate, dark);
            default -> null;
        };
        if (toolbar == null) {
            return;
        }
        Context context = candidate.getContext();
        boolean hasPinned = suggestions.stream().anyMatch(s -> s.getInfo().isPinned());
        int cardCount = suggestions.size() - (hasPinned ? 1 : 0);
        boolean singleCard = cardCount == 1;
        LinearLayout bar = toolbar.bar();

        LinearLayout cards = new LinearLayout(context);
        cards.setGravity(singleCard ? Gravity.CENTER : Gravity.CENTER_VERTICAL);
        SuggestionScroller scroller = new SuggestionScroller(context, cards);
        bar.addView(scroller, new LinearLayout.LayoutParams(0, MATCH_PARENT, 1));

        LinearLayout pinned = new LinearLayout(context);
        pinned.setGravity(Gravity.CENTER_VERTICAL);
        bar.addView(pinned, new LinearLayout.LayoutParams(WRAP_CONTENT, MATCH_PARENT));

        int cardHeight = toolbar.cardHeight();
        int availableWidth = toolbar.availableWidth(hasPinned, dp(PINNED_GAP_DP));
        for (InlineSuggestion suggestion : suggestions) {
            boolean isPinned = suggestion.getInfo().isPinned() && pinned.getChildCount() == 0;
            ViewGroup destination = isPinned ? pinned : cards;
            SuggestionSlot slot = new SuggestionSlot(context);
            slot.setBackground(toolbar.cardBackground(isPinned, dark));
            slot.setVisibility(View.GONE);
            int width = isPinned ? cardHeight : WRAP_CONTENT;
            LinearLayout.LayoutParams spacing = new LinearLayout.LayoutParams(width, cardHeight);
            if (isPinned) {
                spacing.setMarginStart(dp(PINNED_GAP_DP));
            } else if (!singleCard) {
                spacing.setMarginEnd(dp(7));
            }
            destination.addView(slot, spacing);
            int renderWidth = WRAP_CONTENT;
            if (singleCard && !isPinned) {
                InlinePresentationSpec spec = suggestion.getInfo().getInlinePresentationSpec();
                if (availableWidth >= spec.getMinSize().getWidth()) {
                    renderWidth = Math.min(availableWidth, spec.getMaxSize().getWidth());
                }
            }
            try {
                suggestion.inflate(context, new Size(renderWidth, WRAP_CONTENT),
                        service.getMainExecutor(), card -> {
                            if (version != responseVersion || card == null
                                    || !candidate.isShown() || !candidate.isAttachedToWindow()) {
                                return;
                            }
                            ViewGroup.LayoutParams size = card.getLayoutParams();
                            if (!isPinned) {
                                scroller.clipSurface(card);
                            }
                            slot.addView(card, new FrameLayout.LayoutParams(size.width, size.height,
                                    Gravity.CENTER));
                            slot.setVisibility(View.VISIBLE);
                            if (strip == null) {
                                strip = toolbar.attach(candidate);
                                AutofillModule.log("Inline card attached in " + target.packageName);
                            }
                        });
            } catch (IllegalArgumentException | IllegalStateException failure) {
                AutofillModule.log("Inline card could not be rendered");
            }
        }
    }

    private ViewGroup findCandidate(View view) {
        if (view instanceof ViewGroup group) {
            if (target.containerClass.equals(group.getClass().getName())
                    && group.isShown() && group.getWidth() > 0 && group.getHeight() > 0) {
                return group;
            }
            for (int i = 0; i < group.getChildCount(); i++) {
                ViewGroup candidate = findCandidate(group.getChildAt(i));
                if (candidate != null) {
                    return candidate;
                }
            }
        }
        return null;
    }

    private ViewGroup findXiaoAiRootView() {
        for (Class<?> type = service.getClass(); type != null && type != Object.class; type = type.getSuperclass()) {
            try {
                Field field = type.getDeclaredField("currentImeRootView");
                field.setAccessible(true);
                if (field.get(service) instanceof ViewGroup group) {
                    return group;
                }
                return null;
            } catch (NoSuchFieldException ignored) {
                // Continue searching superclasses.
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                return null;
            }
        }
        return null;
    }

    private static View keyboardView(ViewGroup candidate, String getterName) {
        try {
            Method getter = candidate.getClass().getDeclaredMethod(getterName);
            getter.setAccessible(true);
            if (getter.invoke(candidate) instanceof View view) {
                return view;
            }
        } catch (ReflectiveOperationException ignored) {
            // The corresponding toolbar feature is optional.
        }
        return null;
    }

    private static Drawable.ConstantState toolbarBackground(ViewGroup candidate) {
        return findBackground(keyboardView(candidate, "getCustomToolbarHideKeyboardButton"));
    }

    private static Drawable.ConstantState findBackground(View view) {
        if (view == null) {
            return null;
        }
        Drawable background = view.getBackground();
        if (background != null && background.getConstantState() != null) {
            return background.getConstantState();
        }
        if (view instanceof ViewGroup group) {
            for (int i = 0; i < group.getChildCount(); i++) {
                Drawable.ConstantState child = findBackground(group.getChildAt(i));
                if (child != null) {
                    return child;
                }
            }
        }
        return null;
    }

    private View attachStrip(ViewGroup candidate, View row, RelativeLayout.LayoutParams rowLayout, int leftInset) {
        if (row instanceof XiaoAiToolbar toolbar) {
            FrameLayout overlay = new FrameLayout(candidate.getContext());
            FrameLayout.LayoutParams rowParams = new FrameLayout.LayoutParams(MATCH_PARENT, toolbar.height, Gravity.TOP);
            rowParams.leftMargin = leftInset;
            overlay.addView(row, rowParams);
            candidate.addView(overlay, new FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT));
            overlay.bringToFront();
            return overlay;
        }
        candidate.addView(row, rowLayout);
        row.bringToFront();
        return row;
    }

    private boolean isDarkTheme() {
        return switch (target) {
            case SOGOU -> SogouToolbar.isDark(service);
            case BAIDU -> BaiduToolbar.isDark(service);
            case IFLYTEK -> IflytekToolbar.isDark(service);
            case DOUBAO -> DoubaoToolbar.isDark(service);
            default -> isNightMode(service.getResources().getConfiguration());
        };
    }

    private static boolean isNightMode(Configuration configuration) {
        return (configuration.uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
    }

    private int dp(int dp) {
        return Math.round(dp * service.getResources().getDisplayMetrics().density);
    }

    private static final class WeTypeSuggestionBar extends LinearLayout
            implements ViewTreeObserver.OnPreDrawListener {
        private final ViewGroup candidate;
        private final View logo;
        private final View composing;
        private final View settings;
        private final Rect logoBounds = new Rect();
        private final List<View> nativeOverlays = new ArrayList<>();
        private final List<Float> nativeOverlayAlpha = new ArrayList<>();

        WeTypeSuggestionBar(ViewGroup candidate) {
            super(candidate.getContext());
            this.candidate = candidate;
            logo = keyboardView(candidate, "getLogoIv");
            addNativeOverlay(candidate, "getRightContainerLl");
            addNativeOverlay(candidate, "getShapeIv");
            addNativeOverlay(candidate, "getCloseCandidateIv");
            addNativeOverlay(candidate, "getPackUpIv");
            addNativeOverlay(candidate, "getCustomToolbarRv");
            addNativeOverlay(candidate, "getCustomToolbarHideKeyboardButton");
            composing = keyboardView(candidate, "getCandidateContainerRl");
            settings = keyboardView(candidate, "getMSettingContainer");
            setClickable(true);
            setVisibility(INVISIBLE);
        }

        @Override
        protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            getViewTreeObserver().addOnPreDrawListener(this);
        }

        @Override
        protected void onDetachedFromWindow() {
            getViewTreeObserver().removeOnPreDrawListener(this);
            restoreNativeToolbar();
            super.onDetachedFromWindow();
        }

        @Override
        public boolean onPreDraw() {
            if (candidate.getChildAt(candidate.getChildCount() - 1) != this) {
                bringToFront();
            }
            boolean show = (logo == null || logo.isShown()) && !isVisible(composing) && !isVisible(settings);
            setVisibility(show ? VISIBLE : INVISIBLE);
            if (show) {
                if (nativeOverlayAlpha.isEmpty()) {
                    for (View overlay : nativeOverlays) {
                        nativeOverlayAlpha.add(overlay.getAlpha());
                    }
                }
                // Keep native visibility updates available for composition and toolbar transitions.
                for (View overlay : nativeOverlays) {
                    if (overlay.getAlpha() != 0f) {
                        overlay.setAlpha(0f);
                    }
                }
            } else {
                restoreNativeToolbar();
            }
            if (show && isVisible(logo)) {
                logoBounds.set(0, 0, logo.getWidth(), logo.getHeight());
                candidate.offsetDescendantRectToMyCoords(logo, logoBounds);
                int offset = logoBounds.top + logoBounds.bottom - 2 * getTop() - getHeight();
                int top = Math.max(0, offset);
                int bottom = Math.max(0, -offset);
                if (top != getPaddingTop() || bottom != getPaddingBottom()) {
                    setPadding(getPaddingLeft(), top, getPaddingRight(), bottom);
                    return false;
                }
            }
            return true;
        }

        private void restoreNativeToolbar() {
            for (int i = 0; i < nativeOverlayAlpha.size(); i++) {
                nativeOverlays.get(i).setAlpha(nativeOverlayAlpha.get(i));
            }
            nativeOverlayAlpha.clear();
        }

        private static boolean isVisible(View view) {
            return view != null && view.isShown();
        }

        private void addNativeOverlay(ViewGroup candidate, String getterName) {
            View overlay = keyboardView(candidate, getterName);
            if (overlay != null && !nativeOverlays.contains(overlay)) {
                nativeOverlays.add(overlay);
            }
        }
    }

    private static final class SuggestionScroller extends HorizontalScrollView {
        private final int[] location = new int[2];
        private final Rect viewport = new Rect();
        private final Rect appliedViewport = new Rect();
        private boolean appliedVisible;
        private SurfaceControl clipLayer;
        private final ViewTreeObserver.OnPreDrawListener updateClip = () -> {
            updateClipLayer();
            return true;
        };

        SuggestionScroller(Context context, LinearLayout cards) {
            super(context);
            setHorizontalScrollBarEnabled(false);
            setOverScrollMode(View.OVER_SCROLL_NEVER);
            setFillViewport(true);
            addView(cards, new LayoutParams(WRAP_CONTENT, MATCH_PARENT));
        }

        void clipSurface(InlineContentView content) {
            content.setSurfaceControlCallback(new InlineContentView.SurfaceControlCallback() {
                @Override
                public void onCreated(SurfaceControl surface) {
                    attachSurface(surface);
                }

                @Override
                public void onDestroyed(SurfaceControl surface) {
                    // InlineContentView owns the card's surface lifecycle.
                }
            });
        }

        @Override
        protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            getViewTreeObserver().addOnPreDrawListener(updateClip);
        }

        @Override
        public void onVisibilityAggregated(boolean isVisible) {
            super.onVisibilityAggregated(isVisible);
            updateClipLayer();
        }

        @Override
        protected void onDetachedFromWindow() {
            getViewTreeObserver().removeOnPreDrawListener(updateClip);
            if (clipLayer != null) {
                try (SurfaceControl.Transaction transaction = new SurfaceControl.Transaction()) {
                    transaction.reparent(clipLayer, null).apply();
                } finally {
                    clipLayer.release();
                    clipLayer = null;
                    appliedViewport.setEmpty();
                    appliedVisible = false;
                }
            }
            super.onDetachedFromWindow();
        }

        private void attachSurface(SurfaceControl surface) {
            AttachedSurfaceControl root = getRootSurfaceControl();
            if (root == null || !surface.isValid()) {
                return;
            }
            if (clipLayer == null) {
                SurfaceControl layer = new SurfaceControl.Builder().setName("Inline autofill viewport").build();
                try (SurfaceControl.Transaction transaction = root.buildReparentTransaction(layer)) {
                    if (transaction == null) {
                        layer.release();
                        return;
                    }
                    clipLayer = layer;
                    updateViewport();
                    boolean visible = shouldShowSurface();
                    // Keep the parent layer at the window origin so Android can position each card.
                    transaction.setLayer(layer, 1).setCrop(layer, viewport)
                            .setVisibility(layer, visible).reparent(surface, layer);
                    root.applyTransactionOnDraw(transaction);
                    appliedViewport.set(viewport);
                    appliedVisible = visible;
                }
            } else {
                try (SurfaceControl.Transaction transaction = new SurfaceControl.Transaction()) {
                    root.applyTransactionOnDraw(transaction.reparent(surface, clipLayer));
                }
            }
            invalidate();
        }

        private void updateClipLayer() {
            AttachedSurfaceControl root = getRootSurfaceControl();
            if (clipLayer == null || root == null) {
                return;
            }
            updateViewport();
            boolean visible = shouldShowSurface();
            if (!viewport.equals(appliedViewport) || visible != appliedVisible) {
                try (SurfaceControl.Transaction transaction = new SurfaceControl.Transaction()) {
                    root.applyTransactionOnDraw(transaction.setCrop(clipLayer, viewport)
                            .setVisibility(clipLayer, visible));
                }
                appliedViewport.set(viewport);
                appliedVisible = visible;
            }
        }

        private boolean shouldShowSurface() {
            return isShown() && !viewport.isEmpty();
        }

        private void updateViewport() {
            getLocationInSurface(location);
            viewport.set(location[0], location[1], location[0] + getWidth(), location[1] + getHeight());
        }
    }

    private static final class SuggestionSlot extends FrameLayout {
        SuggestionSlot(Context context) {
            super(context);
        }

        @Override
        public boolean dispatchTouchEvent(MotionEvent event) {
            int action = event.getActionMasked();
            if (action == MotionEvent.ACTION_DOWN) {
                setPressed(true);
                drawableHotspotChanged(event.getX(), event.getY());
            } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                setPressed(false);
            }
            return super.dispatchTouchEvent(event);
        }
    }
}
