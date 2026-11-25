package com.mugua.keyfill;

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.inputmethodservice.InputMethodService;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.ViewTreeObserver;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/** Hosts the inline autofill row inside iFlytek IME's docked InputView across CandidateNext and SmartLine modes. */
@SuppressLint("ViewConstructor")
final class IflytekToolbar extends LinearLayout implements ImeToolbar, ViewTreeObserver.OnPreDrawListener {
    private static final String KEYBOARD_PACKAGE = "com.iflytek.inputmethod";
    private static final String CAND_NEXT_CLASS =
            "com.iflytek.inputmethod.candidatenext.ui.main.CandidateNextConstraintLayout";
    private static final String SMART_LINE_CLASS =
            "com.iflytek.inputmethod.input.view.display.impl.SmartLineLayout";
    private static final String KEY_GRID_CLASS =
            "com.iflytek.inputmethod.input.view.display.impl.InputGridRootView";
    private static final String COMPLEX_VIEW_CLASS =
            "com.iflytek.inputmethod.input.hcr.view.ComplexView";
    private static final String THEME_SERVICE =
            "com.iflytek.inputmethod.input.themeadapter.IThemeAdapterManager";
    private static final int SMART_LINE_LINEAR_ID = 100;
    private static final int SMART_LINE_OWL_ID = 101;

    private final InputMethodService service;
    private final ViewGroup inputView;
    private final int mainAgentId;
    private final int cardContainerId;
    private final int hideEntranceId;
    private final int[] nonIdleSiblingIds;
    private final int floatLayoutId;
    private final int bizContainerId;
    private final int topExtendedId;
    private final int popupLineId;
    private final int inputContainerId;
    private final Rect barBounds = new Rect();
    private final Map<View, Float> savedAlphas = new IdentityHashMap<>();
    private final List<GradientDrawable> slotShapes = new ArrayList<>();

    private boolean dark;
    private boolean smartLineMode;
    private int topOffset;
    private int rowHeight;
    private int leftInset;
    private int rightInset;
    private int leftPad;
    private int rightPad;
    private final int heightPx;

    static IflytekToolbar bind(InputMethodService service, ViewGroup inputView, boolean initialDark) {
        try {
            IflytekToolbar toolbar = new IflytekToolbar(service, inputView, initialDark);
            return toolbar.isFloatingDecor() || toolbar.hasDisallowedAncestor() ? null : toolbar;
        } catch (RuntimeException unavailable) {
            AutofillModule.log("iFlytek toolbar bindings unavailable: " + unavailable);
            return null;
        }
    }

    static boolean isDark(InputMethodService service) {
        try {
            ClassLoader loader = service.getClassLoader();
            Class<?> figi = Class.forName("com.iflytek.figi.FIGI", false, loader);
            Object bundleCtx = figi.getMethod("getBundleContext").invoke(null);
            if (bundleCtx != null) {
                Method getService = bundleCtx.getClass().getMethod("getServiceSync", String.class);
                Object manager = getService.invoke(bundleCtx, THEME_SERVICE);
                if (manager != null) {
                    Object adapter = manager.getClass().getMethod("getThemeAdapter").invoke(manager);
                    if (adapter != null) {
                        Object themeColor = adapter.getClass().getMethod("getThemeColor").invoke(adapter);
                        if (themeColor != null && themeColor.getClass()
                                .getMethod("isMainColorDark").invoke(themeColor) instanceof Boolean darkSkin) {
                            return darkSkin;
                        }
                    }
                }
            }
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // Fall back to system configuration.
        }
        return (service.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES;
    }

    private IflytekToolbar(InputMethodService service, ViewGroup inputView, boolean initialDark) {
        super(inputView.getContext());
        this.service = service;
        this.inputView = inputView;
        this.dark = initialDark;
        mainAgentId = id("main_agent_container");
        cardContainerId = id("card_container");
        hideEntranceId = id("hide_entrance_container");
        nonIdleSiblingIds = new int[]{
                id("clipboard_container"),
                id("ai_key_speech_container"),
                id("after_input_close_container"),
                id("assistant_container"),
                id("candidate_expand_composing_overlay")
        };
        floatLayoutId = id("input_floatable_layout");
        bizContainerId = id("biz_fragment_container");
        topExtendedId = id("top_extended_container");
        popupLineId = id("popup_line_container");
        inputContainerId = id("input_container");

        ViewGroup candNext = findDescendantByClass(inputView, CAND_NEXT_CLASS);
        ViewGroup smartLine = findDescendantByClass(inputView, SMART_LINE_CLASS);
        updateGeometryFromActiveBar(candNext, smartLine);
        heightPx = Math.min(dp(33), Math.max(dp(26), rowHeight - dp(4)));

        setGravity(Gravity.CENTER_VERTICAL);
        applyModePadding();
        setBackgroundColor(Color.TRANSPARENT);
        setClickable(true);
        setVisibility(INVISIBLE);
    }

    @Override
    public LinearLayout bar() {
        return this;
    }

    @Override
    public int cardHeight() {
        return heightPx;
    }

    @Override
    public int availableWidth(boolean hasPinned, int pinnedGapPx) {
        int width = inputView.getWidth() - leftInset - rightInset - leftPad - rightPad;
        if (hasPinned) {
            width -= heightPx + pinnedGapPx;
        }
        return width;
    }

    @Override
    public Drawable cardBackground(boolean pinned, boolean isDark) {
        GradientDrawable shape = new GradientDrawable();
        shape.setCornerRadius(heightPx / 2f);
        applySlotStyle(shape, isDark);
        slotShapes.add(shape);
        return new RippleDrawable(ColorStateList.valueOf(isDark ? 0x24ffffff : 0x19000000), shape, null);
    }

    @Override
    public View attach(ViewGroup host) {
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, rowHeight, Gravity.TOP);
        params.topMargin = topOffset;
        params.leftMargin = leftInset;
        params.rightMargin = rightInset;
        host.addView(this, params);
        bringToFront();
        return this;
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        getViewTreeObserver().addOnPreDrawListener(this);
    }

    @Override
    protected void onDetachedFromWindow() {
        getViewTreeObserver().removeOnPreDrawListener(this);
        restoreIdleViews();
        super.onDetachedFromWindow();
    }

    @Override
    public boolean onPreDraw() {
        if (inputView.getChildAt(inputView.getChildCount() - 1) != this) {
            bringToFront();
        }
        syncTheme();
        ViewGroup candNext = findDescendantByClass(inputView, CAND_NEXT_CLASS);
        ViewGroup smartLine = findDescendantByClass(inputView, SMART_LINE_CLASS);
        boolean prevSmartMode = smartLineMode;
        int prevTop = topOffset;
        int prevHeight = rowHeight;
        int prevLeft = leftInset;
        int prevRight = rightInset;
        updateGeometryFromActiveBar(candNext, smartLine);
        if (prevSmartMode != smartLineMode) {
            applyModePadding();
        }
        if ((prevTop != topOffset || prevHeight != rowHeight || prevLeft != leftInset || prevRight != rightInset)
                && getLayoutParams() instanceof FrameLayout.LayoutParams params) {
            params.topMargin = topOffset;
            params.height = rowHeight;
            params.leftMargin = leftInset;
            params.rightMargin = rightInset;
            setLayoutParams(params);
        }
        boolean show = canShowSuggestions(candNext, smartLine);
        setVisibility(show ? VISIBLE : INVISIBLE);
        if (!show) {
            restoreIdleViews();
        } else {
            if (prevSmartMode != smartLineMode) {
                restoreIdleViews();
            }
            if (!smartLineMode && candNext != null) {
                fadeView(findById(candNext, cardContainerId));
                fadeView(findById(candNext, hideEntranceId));
            } else if (smartLineMode && smartLine != null) {
                fadeSmartLineIdleBanner(smartLine);
            }
        }
        return true;
    }

    private void updateGeometryFromActiveBar(ViewGroup candNext, ViewGroup smartLine) {
        if (isCandNextActive(candNext)) {
            smartLineMode = false;
            barBounds.set(0, 0, candNext.getWidth(), candNext.getHeight());
            inputView.offsetDescendantRectToMyCoords(candNext, barBounds);
            topOffset = Math.max(0, barBounds.top);
            rowHeight = Math.max(dp(36), barBounds.height());
            View mainAgent = findById(candNext, mainAgentId);
            int agentRight = (mainAgent != null && mainAgent.getRight() > 0) ? mainAgent.getRight()
                    : (mainAgent != null && mainAgent.getWidth() > 0 ? mainAgent.getWidth() : dp(62));
            leftInset = Math.max(0, barBounds.left + agentRight);
            rightInset = Math.max(0, inputView.getWidth() - barBounds.right);
            leftPad = dp(4);
            rightPad = dp(10);
        } else if (isSmartLineActive(smartLine)) {
            smartLineMode = true;
            barBounds.set(0, 0, smartLine.getWidth(), smartLine.getHeight());
            inputView.offsetDescendantRectToMyCoords(smartLine, barBounds);
            topOffset = Math.max(0, barBounds.top);
            rowHeight = Math.max(dp(28), barBounds.height());
            leftInset = Math.max(0, barBounds.left);
            View owl = smartLine.findViewById(SMART_LINE_OWL_ID);
            int owlWidth = (owl != null && owl.isShown() && owl.getWidth() > 0)
                    ? smartLine.getWidth() - owl.getLeft() : 0;
            rightInset = Math.max(0, inputView.getWidth() - barBounds.right + Math.max(0, owlWidth));
            leftPad = dp(10);
            rightPad = owlWidth > 0 ? dp(4) : dp(10);
        } else {
            smartLineMode = false;
            topOffset = 0;
            rowHeight = dp(42);
            leftInset = dp(62);
            rightInset = 0;
            leftPad = dp(4);
            rightPad = dp(10);
        }
    }

    private void applyModePadding() {
        setPadding(leftPad, smartLineMode ? 0 : dp(2), rightPad, 0);
    }

    private void syncTheme() {
        boolean currentDark = isDark(service);
        if (currentDark == dark) {
            return;
        }
        dark = currentDark;
        for (GradientDrawable shape : slotShapes) {
            applySlotStyle(shape, currentDark);
        }
    }

    private boolean canShowSuggestions(ViewGroup candNext, ViewGroup smartLine) {
        if (!inputView.isShown() || inputView.getWidth() < dp(240) || inputView.getHeight() < dp(120)) {
            return false;
        }
        if (isFloatingDecor() || hasDisallowedAncestor() || hasOverlayPanelOpen() || !hasShownKeyGrid()) {
            return false;
        }
        if (isCandNextActive(candNext)) {
            return isCandNextIdle(candNext) && !isSmartLineComposing(smartLine);
        }
        return isSmartLineActive(smartLine) && !isSmartLineComposing(smartLine);
    }

    private boolean isCandNextIdle(ViewGroup candNext) {
        View mainAgent = findById(candNext, mainAgentId);
        View cardContainer = findById(candNext, cardContainerId);
        if (mainAgent == null || mainAgent.getVisibility() != VISIBLE || mainAgent.getWidth() <= 0) {
            return false;
        }
        if (cardContainer == null || cardContainer.getVisibility() != VISIBLE) {
            return false;
        }
        for (int siblingId : nonIdleSiblingIds) {
            View sibling = findById(candNext, siblingId);
            if (sibling != null && sibling.getVisibility() == VISIBLE) {
                return false;
            }
        }
        return true;
    }

    private static boolean isSmartLineComposing(ViewGroup smartLine) {
        if (smartLine == null || !smartLine.isShown()) {
            return false;
        }
        View linear = smartLine.findViewById(SMART_LINE_LINEAR_ID);
        if (linear instanceof ViewGroup g) {
            for (int i = 1; i < g.getChildCount(); i++) {
                View extra = g.getChildAt(i);
                if (extra.getVisibility() == VISIBLE && extra.getHeight() > 0) {
                    return true;
                }
            }
            if (g.getChildCount() > 0 && g.getChildAt(0) instanceof ViewGroup c) {
                for (int i = 0; i < c.getChildCount(); i++) {
                    View child = c.getChildAt(i);
                    if (child.getId() != SMART_LINE_OWL_ID && child instanceof ViewGroup comp
                            && comp.getChildCount() > 0) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private boolean hasOverlayPanelOpen() {
        for (int i = 0; i < inputView.getChildCount(); i++) {
            View child = inputView.getChildAt(i);
            if (COMPLEX_VIEW_CLASS.equals(child.getClass().getName()) && child.isShown()) {
                return true;
            }
        }
        if (findById(inputView, bizContainerId) instanceof ViewGroup biz
                && biz.getVisibility() == VISIBLE && biz.getWidth() > 0 && biz.getHeight() > 0
                && hasNonGoneChild(biz)) {
            return true;
        }
        if (findById(inputView, topExtendedId) instanceof ViewGroup topExt
                && topExt.getVisibility() == VISIBLE && topExt.getHeight() > 0 && hasShownChild(topExt)) {
            return true;
        }
        return findById(inputView, popupLineId) instanceof ViewGroup popup
                && popup.getVisibility() == VISIBLE && popup.getHeight() > 0 && hasShownChild(popup);
    }

    private boolean hasShownKeyGrid() {
        View host = findById(inputView, inputContainerId);
        if (host instanceof ViewGroup group) {
            return group.isShown() && group.getHeight() > 0 && hasShownViewByClass(group, KEY_GRID_CLASS);
        }
        return hasShownViewByClass(inputView, KEY_GRID_CLASS);
    }

    private static boolean hasShownViewByClass(View root, String className) {
        if (className.equals(root.getClass().getName())) {
            return root.isShown() && root.getHeight() > 0;
        }
        if (root instanceof ViewGroup group) {
            for (int i = 0; i < group.getChildCount(); i++) {
                if (hasShownViewByClass(group.getChildAt(i), className)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean isCandNextActive(ViewGroup candNext) {
        return candNext != null && candNext.isShown() && candNext.getWidth() >= dp(200)
                && candNext.getHeight() >= dp(32);
    }

    private boolean isSmartLineActive(ViewGroup smartLine) {
        return smartLine != null && smartLine.isShown() && smartLine.getWidth() >= dp(200)
                && smartLine.getHeight() >= dp(22);
    }

    private boolean isFloatingDecor() {
        View decor = decorView();
        if (decor != null && floatLayoutId != 0) {
            View floatView = decor.findViewById(floatLayoutId);
            if (floatView != null && floatView.isShown()) {
                return true;
            }
        }
        return false;
    }

    private boolean hasDisallowedAncestor() {
        for (ViewParent p = inputView.getParent(); p != null; p = p.getParent()) {
            String name = p.getClass().getName();
            if (name.contains("FloatContainerView") || name.contains("SeparateFrameLayout")) {
                return true;
            }
        }
        return false;
    }

    private void fadeSmartLineIdleBanner(ViewGroup smartLine) {
        for (int i = 0; i < smartLine.getChildCount(); i++) {
            View child = smartLine.getChildAt(i);
            if (child instanceof ViewGroup && child.getId() != SMART_LINE_LINEAR_ID) {
                fadeView(child);
            }
        }
    }

    private void fadeView(View target) {
        if (target == null) {
            return;
        }
        if (!savedAlphas.containsKey(target)) {
            savedAlphas.put(target, target.getAlpha());
        }
        if (target.getAlpha() != 0f) {
            target.setAlpha(0f);
        }
    }

    private void restoreIdleViews() {
        for (Map.Entry<View, Float> entry : savedAlphas.entrySet()) {
            entry.getKey().setAlpha(entry.getValue());
        }
        savedAlphas.clear();
    }

    private View decorView() {
        Dialog window = service.getWindow();
        return window != null && window.getWindow() != null ? window.getWindow().getDecorView() : null;
    }

    private static ViewGroup findDescendantByClass(View root, String className) {
        if (root instanceof ViewGroup group) {
            if (className.equals(group.getClass().getName())) {
                return group;
            }
            for (int i = 0; i < group.getChildCount(); i++) {
                ViewGroup found = findDescendantByClass(group.getChildAt(i), className);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private static boolean hasNonGoneChild(ViewGroup group) {
        for (int i = 0; i < group.getChildCount(); i++) {
            if (group.getChildAt(i).getVisibility() != GONE) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasShownChild(ViewGroup group) {
        for (int i = 0; i < group.getChildCount(); i++) {
            if (group.getChildAt(i).isShown() && group.getChildAt(i).getHeight() > 0) {
                return true;
            }
        }
        return false;
    }

    private static View findById(View root, int viewId) {
        return viewId == 0 || root == null ? null : root.findViewById(viewId);
    }

    private static void applySlotStyle(GradientDrawable shape, boolean dark) {
        shape.setColor(dark ? 0x2effffff : 0xd9ffffff);
        shape.setStroke(1, dark ? 0x22ffffff : 0x18000000);
    }

    private int id(String name) {
        int resId = service.getResources().getIdentifier(name, "id", KEYBOARD_PACKAGE);
        return resId != 0 ? resId : getResources().getIdentifier(name, "id", KEYBOARD_PACKAGE);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
