package com.mugua.keyfill;

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.content.Context;
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

/** Hosts the inline autofill row inside Doubao IME's CandidateToolbarHostLayout. */
@SuppressLint("ViewConstructor")
final class DoubaoToolbar extends LinearLayout implements ImeToolbar, ViewTreeObserver.OnPreDrawListener {
    private static final String KEYBOARD_PACKAGE = "com.bytedance.android.doubaoime";
    private static final String SOFT_KEYBOARD_CLASS = "com.bytedance.android.input.keyboard.KeyboardView";
    private static final String INPUT_VIEW_CLASS = "com.bytedance.android.input.keyboard.InputView";

    private final ViewGroup candidate;
    private final int btnLogoId;
    private final int btnLogoBlueDotId;
    private final int btnHideRemoveId;
    private final int commonPhraseEditId;
    private final int llmViewId;
    private final int keyboardWholeId;
    private final Method windowStyleMethod;
    private final Rect logoBounds = new Rect();
    private final Map<View, Float> savedAlphas = new IdentityHashMap<>();
    private final List<GradientDrawable> slotShapes = new ArrayList<>();
    private Method editOverflowMethod;
    private boolean dark;
    private int logoInset;
    private final int leftPad;
    private final int rightPad;
    private final int heightPx;

    static DoubaoToolbar bind(ViewGroup candidate, boolean initialDark) {
        try {
            return new DoubaoToolbar(candidate, initialDark);
        } catch (RuntimeException unavailable) {
            AutofillModule.log("Doubao toolbar bindings unavailable");
            return null;
        }
    }

    static boolean isDark(InputMethodService service) {
        Dialog window = service.getWindow();
        if (window != null && window.getWindow() != null) {
            View host = findToolbarHost(window.getWindow().getDecorView());
            if (host != null) {
                return isNightConfig(host.getContext());
            }
        }
        return isNightConfig(service);
    }

    private DoubaoToolbar(ViewGroup candidate, boolean initialDark) {
        super(candidate.getContext());
        this.candidate = candidate;
        this.dark = initialDark;
        btnLogoId = id("btn_logo");
        btnLogoBlueDotId = id("btn_logo_blue_dot");
        btnHideRemoveId = id("btn_hide_remove");
        commonPhraseEditId = id("common_phrase_edit_view");
        llmViewId = id("llm_view");
        keyboardWholeId = id("keyboard_whole");
        Method styleGetter = null;
        try {
            styleGetter = candidate.getClass().getMethod("getWindowStyle");
            styleGetter.setAccessible(true);
        } catch (ReflectiveOperationException ignored) {
            // Optional fallback if renamed.
        }
        windowStyleMethod = styleGetter;
        logoInset = resolveLogoInset();
        leftPad = dp(8);
        rightPad = dp(12);
        heightPx = Math.min(dp(34), Math.max(dp(28), candidate.getHeight() - dp(12)));
        setGravity(Gravity.CENTER_VERTICAL);
        setPadding(leftPad, 0, rightPad, 0);
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
        int width = candidate.getWidth() - logoInset - candidate.getPaddingLeft()
                - candidate.getPaddingRight() - leftPad - rightPad;
        if (hasPinned) {
            width -= heightPx + pinnedGapPx;
        }
        return width;
    }

    @Override
    public Drawable cardBackground(boolean pinned, boolean isDark) {
        GradientDrawable shape = new GradientDrawable();
        shape.setCornerRadius(heightPx / 2f);
        shape.setColor(pillFill(isDark));
        slotShapes.add(shape);
        return new RippleDrawable(ColorStateList.valueOf(isDark ? 0x24ffffff : 0x19000000), shape, null);
    }

    @Override
    public View attach(ViewGroup host) {
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        params.leftMargin = logoInset;
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
        if (candidate.getChildAt(candidate.getChildCount() - 1) != this) {
            bringToFront();
        }
        syncTheme();
        int currentInset = resolveLogoInset();
        if (currentInset != logoInset && getLayoutParams() instanceof FrameLayout.LayoutParams params) {
            logoInset = currentInset;
            params.leftMargin = currentInset;
            setLayoutParams(params);
        }
        ViewGroup idleView = findIdleView();
        boolean show = canShowSuggestions(idleView);
        setVisibility(show ? VISIBLE : INVISIBLE);
        if (show) {
            fadeIdleViews(idleView);
        } else {
            restoreIdleViews();
        }
        return true;
    }

    private void syncTheme() {
        boolean currentDark = isNightConfig(candidate.getContext());
        if (currentDark == dark) {
            return;
        }
        dark = currentDark;
        int fill = pillFill(currentDark);
        for (GradientDrawable shape : slotShapes) {
            shape.setColor(fill);
        }
    }

    private boolean canShowSuggestions(ViewGroup idleView) {
        if (idleView == null || !idleView.isShown()) {
            return false;
        }
        if (isEditingToolbar(idleView) || isFloatingWindow()) {
            return false;
        }
        if (!(candidate.getParent() instanceof ViewGroup mainArea)) {
            return false;
        }
        View softKeyboard = null;
        for (int i = 0; i < mainArea.getChildCount(); i++) {
            View child = mainArea.getChildAt(i);
            if (child == candidate) {
                continue;
            }
            if (SOFT_KEYBOARD_CLASS.equals(child.getClass().getName())) {
                softKeyboard = child;
            } else if (child.isShown()) {
                return false;
            }
        }
        if (softKeyboard == null || !softKeyboard.isShown()) {
            return false;
        }
        ViewGroup inputView = findInputViewAncestor();
        if (inputView != null) {
            if (isIdShown(inputView, commonPhraseEditId) || isIdShown(inputView, llmViewId)) {
                return false;
            }
            if (keyboardWholeId != 0 && inputView.findViewById(keyboardWholeId) instanceof ViewGroup whole) {
                if (whole.getChildCount() > 0 && whole.getChildAt(0).isShown()
                        && SOFT_KEYBOARD_CLASS.equals(whole.getChildAt(0).getClass().getName())) {
                    return false;
                }
                for (int i = 0; i < whole.getChildCount(); i++) {
                    View c = whole.getChildAt(i);
                    if (c.isShown() && c.getClass().getName().endsWith(".SuperAssociationView")) {
                        return false;
                    }
                }
            }
            for (int i = 0; i < inputView.getChildCount(); i++) {
                View c = inputView.getChildAt(i);
                if (c.isShown() && c.getClass().getName().endsWith(".AiPanelView")) {
                    return false;
                }
            }
        }
        return true;
    }

    private boolean isEditingToolbar(ViewGroup idleView) {
        if (btnHideRemoveId != 0 && isIdShown(idleView, btnHideRemoveId)) {
            return true;
        }
        try {
            if (editOverflowMethod == null) {
                editOverflowMethod = idleView.getClass().getMethod("getEditDrawingOverflow$tool_bar_release");
                editOverflowMethod.setAccessible(true);
            }
            return editOverflowMethod.invoke(idleView) instanceof Integer overflow && overflow != 0;
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return false;
        }
    }

    private boolean isFloatingWindow() {
        if (windowStyleMethod == null) {
            return false;
        }
        try {
            return windowStyleMethod.invoke(candidate) instanceof Enum<?> style
                    && "FLOATING".equals(style.name());
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return false;
        }
    }

    private void fadeIdleViews(ViewGroup idleView) {
        if (idleView.getChildCount() == 0 || !(idleView.getChildAt(0) instanceof ViewGroup rootCl)) {
            return;
        }
        for (int i = 0; i < rootCl.getChildCount(); i++) {
            View child = rootCl.getChildAt(i);
            int childId = child.getId();
            if (childId == btnLogoId || childId == btnLogoBlueDotId) {
                continue;
            }
            if (!savedAlphas.containsKey(child)) {
                savedAlphas.put(child, child.getAlpha());
            }
            if (child.getAlpha() != 0f) {
                child.setAlpha(0f);
            }
        }
    }

    private void restoreIdleViews() {
        for (Map.Entry<View, Float> entry : savedAlphas.entrySet()) {
            entry.getKey().setAlpha(entry.getValue());
        }
        savedAlphas.clear();
    }

    private ViewGroup findIdleView() {
        for (int i = 0; i < candidate.getChildCount(); i++) {
            View child = candidate.getChildAt(i);
            if (child instanceof ViewGroup group && child.getClass().getName().endsWith(".CandidateIdleView")) {
                return group;
            }
        }
        return null;
    }

    private ViewGroup findInputViewAncestor() {
        for (ViewParent p = candidate.getParent(); p instanceof ViewGroup group; p = p.getParent()) {
            if (INPUT_VIEW_CLASS.equals(group.getClass().getName())) {
                return group;
            }
        }
        return null;
    }

    private int resolveLogoInset() {
        View logo = btnLogoId == 0 ? null : candidate.findViewById(btnLogoId);
        if (logo != null && logo.getWidth() > 0) {
            logoBounds.set(0, 0, logo.getWidth(), logo.getHeight());
            candidate.offsetDescendantRectToMyCoords(logo, logoBounds);
            return Math.max(0, logoBounds.right - candidate.getPaddingLeft());
        }
        return dp(42);
    }

    private static View findToolbarHost(View root) {
        if (root instanceof ViewGroup group) {
            if (KeyboardTarget.DOUBAO.containerClass.equals(group.getClass().getName()) && group.isShown()) {
                return group;
            }
            for (int i = 0; i < group.getChildCount(); i++) {
                View found = findToolbarHost(group.getChildAt(i));
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private static boolean isIdShown(View root, int viewId) {
        if (viewId == 0) {
            return false;
        }
        View found = root.findViewById(viewId);
        return found != null && found.isShown();
    }

    private static boolean isNightConfig(Context context) {
        return (context.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES;
    }

    private static int pillFill(boolean dark) {
        return dark ? 0x1fffffff : 0xccffffff;
    }

    private int id(String name) {
        return getResources().getIdentifier(name, "id", KEYBOARD_PACKAGE);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
