package com.mugua.keyfill;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.util.SparseArray;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.ViewTreeObserver;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/** Hosts the inline autofill row inside Sogou IME's IMEInputCandidateViewContainer. */
@SuppressLint("ViewConstructor")
final class SogouToolbar extends LinearLayout implements ImeToolbar, ViewTreeObserver.OnPreDrawListener {
    private static final int LOGO_FUNC_ID = 6;

    private final ViewGroup candidate;
    private final View rootCanvasView;
    private final Method rowHeightMethod;
    private final Field pField;
    private final Field c0Field;
    private final Field d0Field;
    private final Field mField;
    private final Field aField;
    private final Field[] nonIdleCompFields;
    private final Field xFloatField;
    private final Field yViewField;
    private final Field zViewField;
    private final Method compVisibleMethod;
    private final Method compLeftMethod;
    private final Method compTopMethod;
    private final Method compHeightMethod;
    private final Field compScrollXField;
    private final Method findFuncIndexMethod;
    private final Method itemTopMethod;
    private final Field k1Field;
    private final Field f0RectField;
    private final Method itemBoundsMethod;
    private final Class<?> pinnedFuncClass;
    private final Class<?> mainKeyboardIface;
    private final Method mainDelInstance;
    private final Method mainDelViewType;
    private final Method funcMgrInstance;
    private final Method funcMgrCollapsed;
    private final Method floatHelperGet;
    private final Method floatHelperActive;
    private final Rect clipBounds = new Rect();
    private final List<GradientDrawable> slotShapes = new ArrayList<>();

    private Method isPageLayoutMethod;
    private Method getSlotViewMethod;
    private Field slotsField;
    private boolean dark;
    private boolean clippedByUs;
    private boolean alphaHiddenByUs;
    private int barHeight;
    private int topMarginPx;
    private int logoInset;
    private int rightMarginPx;
    private int leftPad;
    private final int rightPad;
    private final int heightPx;

    static SogouToolbar bind(ViewGroup candidate, boolean initialDark) {
        try {
            return new SogouToolbar(candidate, initialDark);
        } catch (ReflectiveOperationException | RuntimeException unavailable) {
            AutofillModule.log("Sogou toolbar bindings unavailable: " + unavailable.getClass().getSimpleName());
            return null;
        }
    }

    static boolean isDark(Context context) {
        ClassLoader loader = context.getClassLoader();
        try {
            Class<?> innerK = Class.forName("com.sogou.theme.innerapi.k", false, loader);
            Object cache = innerK.getMethod("m").invoke(null);
            boolean themeLoaded = cache != null && cache.getClass().getMethod("c").invoke(cache) != null;
            if (themeLoaded) {
                Class<?> uiT = Class.forName("com.sohu.inputmethod.ui.t", false, loader);
                int bgColor = (int) uiT.getMethod("c").invoke(null);
                if (Color.alpha(bgColor) > 0) {
                    return Color.luminance(bgColor | 0xff000000) < 0.45f;
                }
                int textColor = (int) uiT.getMethod("h").invoke(null);
                if (textColor != 0x7fffffff && Color.alpha(textColor) > 0) {
                    return Color.luminance(textColor | 0xff000000) > 0.55f;
                }
                Class<?> themeF = Class.forName("com.sogou.theme.impl.f", false, loader);
                if (themeF.getMethod("b").invoke(null) instanceof Boolean darkSkin) {
                    return darkSkin;
                }
            }
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // Fall back to system configuration when theme engine is not yet initialized.
        }
        return (context.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES;
    }

    private SogouToolbar(ViewGroup candidate, boolean initialDark) throws ReflectiveOperationException {
        super(candidate.getContext());
        this.candidate = candidate;
        this.dark = initialDark;
        Class<?> containerClass = candidate.getClass();
        ClassLoader loader = containerClass.getClassLoader();

        rowHeightMethod = containerClass.getMethod("j0");
        Field h0Field = findField(containerClass, "H0");
        rootCanvasView = h0Field.get(candidate) instanceof View v ? v : null;
        pField = findField(containerClass, "p");
        c0Field = findOptionalField(containerClass, "c0");
        d0Field = findField(containerClass, "d0");
        mField = findField(containerClass, "m");
        aField = findField(containerClass, "A");
        nonIdleCompFields = new Field[]{
                findField(containerClass, "c"),
                findField(containerClass, "t"),
                findField(containerClass, "u"),
                findField(containerClass, "v"),
                findField(containerClass, "s"),
                findField(containerClass, "e")
        };
        xFloatField = findField(containerClass, "x");
        yViewField = findField(containerClass, "y");
        zViewField = findField(containerClass, "z");

        Class<?> compClass = Class.forName("com.sogou.imskit.core.ui.virtualwidget.component.Component", false, loader);
        compVisibleMethod = compClass.getMethod("I1");
        compLeftMethod = compClass.getMethod("g1");
        compTopMethod = compClass.getMethod("v1");
        compHeightMethod = compClass.getMethod("l1");
        compScrollXField = findField(compClass, "o");

        Class<?> funcViewClass = Class.forName("com.sohu.inputmethod.sogou.NewIMEFunctionCandidateView", false, loader);
        findFuncIndexMethod = funcViewClass.getMethod("J3", int.class);
        itemTopMethod = findOptionalMethod(funcViewClass, "W3");
        k1Field = findField(funcViewClass, "k1");
        f0RectField = findField(funcViewClass, "F0");
        itemBoundsMethod = k1Field.getType().getMethod("e", int.class);

        Class<?> t1 = null;
        try {
            t1 = Class.forName("com.sohu.inputmethod.sogou.t1", false, loader);
        } catch (ClassNotFoundException ignored) {
            // Portrait pinned-logo subclass is optional.
        }
        pinnedFuncClass = t1;
        mainKeyboardIface = Class.forName("com.sogou.imskit.core.ui.keyboard.view.a", false, loader);

        Class<?> mainDel = Class.forName("com.sohu.inputmethod.sogou.MainImeServiceDel", false, loader);
        mainDelInstance = mainDel.getMethod("getInstance");
        mainDelViewType = mainDel.getMethod("S0");

        Class<?> funcMgr = Class.forName("com.sohu.inputmethod.main.manager.MainIMEFunctionManager", false, loader);
        funcMgrInstance = funcMgr.getMethod("Q");
        funcMgrCollapsed = funcMgr.getMethod("k");

        Method fGet = null;
        Method fActive = null;
        try {
            Class<?> floatHelper = Class.forName("com.sogou.imskit.feature.lib.keyboard.floating.d", false, loader);
            fGet = floatHelper.getMethod("n", Context.class);
            fActive = floatHelper.getMethod("t");
        } catch (ReflectiveOperationException ignored) {
            // xFloatField provides secondary floating guard.
        }
        floatHelperGet = fGet;
        floatHelperActive = fActive;

        int rowHeight = resolveRowHeight();
        heightPx = Math.min(dp(34), Math.max(dp(26), rowHeight - dp(8)));
        barHeight = Math.min(rowHeight, heightPx + dp(8));
        int rawLogoRight = resolveLogoRight();
        logoInset = rawLogoRight > 0 ? rawLogoRight : Math.max(0, d0Field.getInt(candidate));
        rightMarginPx = resolveRightMargin();
        leftPad = rawLogoRight > 0 ? 0 : dp(12);
        rightPad = dp(15);
        topMarginPx = computeVerticalPlacement(barHeight);

        setGravity(Gravity.CENTER_VERTICAL);
        setPadding(leftPad, getPaddingTop(), rightPad, getPaddingBottom());
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
        int width = candidate.getWidth() - logoInset - rightMarginPx - leftPad - rightPad;
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
        RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, barHeight);
        params.addRule(RelativeLayout.ALIGN_PARENT_TOP);
        params.addRule(RelativeLayout.ALIGN_PARENT_LEFT);
        params.addRule(RelativeLayout.ALIGN_PARENT_RIGHT);
        params.topMargin = topMarginPx;
        params.leftMargin = logoInset;
        params.rightMargin = rightMarginPx;
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
        restoreNativeToolbar();
        super.onDetachedFromWindow();
    }

    @Override
    public boolean onPreDraw() {
        if (candidate.getChildAt(candidate.getChildCount() - 1) != this) {
            bringToFront();
        }
        syncTheme();
        int currentRowHeight = resolveRowHeight();
        int currentBarHeight = Math.min(currentRowHeight, heightPx + dp(8));
        int rawLogoRight = resolveLogoRight();
        int currentInset = rawLogoRight > 0 ? rawLogoRight : fallbackLeftOffset();
        int currentRightMargin = resolveRightMargin();
        int currentLeftPad = rawLogoRight > 0 ? 0 : dp(12);
        int currentTopMargin = computeVerticalPlacement(currentBarHeight);
        if (currentLeftPad != leftPad) {
            leftPad = currentLeftPad;
            setPadding(leftPad, getPaddingTop(), rightPad, getPaddingBottom());
        }
        if ((currentBarHeight != barHeight || currentTopMargin != topMarginPx
                || currentInset != logoInset || currentRightMargin != rightMarginPx)
                && getLayoutParams() instanceof RelativeLayout.LayoutParams params) {
            barHeight = currentBarHeight;
            topMarginPx = currentTopMargin;
            logoInset = currentInset;
            rightMarginPx = currentRightMargin;
            params.height = currentBarHeight;
            params.topMargin = currentTopMargin;
            params.leftMargin = currentInset;
            params.rightMargin = currentRightMargin;
            setLayoutParams(params);
        }
        boolean show = canShowSuggestions();
        setVisibility(show ? VISIBLE : INVISIBLE);
        if (show && rootCanvasView != null) {
            if (rawLogoRight > 0) {
                clipBounds.set(0, 0, rawLogoRight, rootCanvasView.getHeight());
                if (!clipBounds.equals(rootCanvasView.getClipBounds())) {
                    rootCanvasView.setClipBounds(new Rect(clipBounds));
                }
                clippedByUs = true;
            } else {
                if (rootCanvasView.getAlpha() != 0f) {
                    rootCanvasView.setAlpha(0f);
                }
                alphaHiddenByUs = true;
            }
        } else {
            restoreNativeToolbar();
        }
        return true;
    }

    private void syncTheme() {
        boolean currentDark = isDark(getContext());
        if (currentDark == dark) {
            return;
        }
        dark = currentDark;
        for (GradientDrawable shape : slotShapes) {
            applySlotStyle(shape, currentDark);
        }
    }

    private boolean canShowSuggestions() {
        try {
            Object del = mainDelInstance.invoke(null);
            if (!(del != null && mainDelViewType.invoke(del) instanceof Enum<?> mode)
                    || !"KEYBOARD_VIEW".equals(mode.name())) {
                return false;
            }
            if (isFloatingActive() || isComponentVisible(xFloatField) || !isMainKeyboardBodyActive()) {
                return false;
            }
            Object mgr = funcMgrInstance.invoke(null);
            if (mgr != null && !Boolean.TRUE.equals(funcMgrCollapsed.invoke(mgr))) {
                return false;
            }
            if (!isComponentVisible(pField) || mField.getBoolean(candidate) || aField.getBoolean(candidate)) {
                return false;
            }
            for (Field field : nonIdleCompFields) {
                if (isComponentVisible(field)) {
                    return false;
                }
            }
            return !isRealViewShown(yViewField) && !isRealViewShown(zViewField);
        } catch (ReflectiveOperationException | RuntimeException unavailable) {
            return false;
        }
    }

    private boolean isMainKeyboardBodyActive() throws ReflectiveOperationException {
        ViewParent toolbarParent = candidate.getParent();
        if (!(toolbarParent != null && toolbarParent.getParent() instanceof ViewGroup inputArea)) {
            return false;
        }
        if (isPageLayoutMethod == null) {
            Class<?> areaClass = inputArea.getClass();
            isPageLayoutMethod = findMethod(areaClass, "G");
            getSlotViewMethod = findMethod(areaClass, "c", int.class);
            slotsField = findField(areaClass, "b");
        }
        if (Boolean.TRUE.equals(isPageLayoutMethod.invoke(inputArea))) {
            return false;
        }
        if (!(getSlotViewMethod.invoke(inputArea, 1) instanceof View slot1)
                || !slot1.isShown() || !mainKeyboardIface.isInstance(slot1)) {
            return false;
        }
        if (slotsField.get(inputArea) instanceof SparseArray<?> slots) {
            for (int i = 0; i < slots.size(); i++) {
                int key = slots.keyAt(i);
                if (key != 0 && key != 1 && key != 6
                        && slots.valueAt(i) instanceof View overlay && overlay.isShown()) {
                    return false;
                }
            }
        }
        return true;
    }

    private boolean isFloatingActive() {
        if (floatHelperGet == null || floatHelperActive == null) {
            return false;
        }
        try {
            Object helper = floatHelperGet.invoke(null, getContext());
            return helper != null && Boolean.TRUE.equals(floatHelperActive.invoke(helper));
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return false;
        }
    }

    private void restoreNativeToolbar() {
        if (rootCanvasView == null) {
            return;
        }
        if (clippedByUs) {
            try {
                if (!aField.getBoolean(candidate)) {
                    rootCanvasView.setClipBounds(null);
                }
            } catch (ReflectiveOperationException ignored) {
                rootCanvasView.setClipBounds(null);
            }
            clippedByUs = false;
        }
        if (alphaHiddenByUs) {
            rootCanvasView.setAlpha(1f);
            alphaHiddenByUs = false;
        }
    }

    private int resolveRowHeight() {
        try {
            int h = (int) rowHeightMethod.invoke(candidate);
            if (h > 0) {
                return h;
            }
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // Fall back to container height.
        }
        return candidate.getHeight();
    }

    private int resolveLogoRight() {
        try {
            Object p = pField.get(candidate);
            if (p == null) {
                return 0;
            }
            int logoIdx = (int) findFuncIndexMethod.invoke(p, LOGO_FUNC_ID);
            if (logoIdx < 0) {
                return 0;
            }
            Object layoutHelper = k1Field.get(p);
            if (layoutHelper == null || !(itemBoundsMethod.invoke(layoutHelper, logoIdx) instanceof RectF rect)) {
                return 0;
            }
            int pLeft = (int) compLeftMethod.invoke(p);
            Rect pad = (Rect) f0RectField.get(p);
            int padLeft = pad != null ? pad.left : 0;
            if (pinnedFuncClass != null && pinnedFuncClass.isInstance(p)) {
                return Math.max(0, Math.round(pLeft + padLeft + rect.width()));
            }
            int scrollX = compScrollXField.getInt(p);
            return Math.max(pLeft, Math.round(pLeft + rect.right) - scrollX);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return dp(44);
        }
    }

    private int computeVerticalPlacement(int currentBarHeight) {
        int containerHeight = candidate.getHeight();
        float targetCenterY = resolveLogoCenterY(containerHeight, currentBarHeight);
        int maxTop = Math.max(0, containerHeight - currentBarHeight);
        int topMargin = containerHeight > 0
                ? Math.max(0, Math.min(maxTop, Math.round(targetCenterY - currentBarHeight / 2f)))
                : 0;
        int residual = containerHeight > 0
                ? Math.round(2f * targetCenterY - 2f * topMargin - currentBarHeight)
                : 0;
        int maxShift = Math.max(0, currentBarHeight - heightPx);
        residual = Math.max(-maxShift, Math.min(maxShift, residual));
        int topPad = Math.max(0, residual);
        int bottomPad = Math.max(0, -residual);
        if (topPad != getPaddingTop() || bottomPad != getPaddingBottom()) {
            setPadding(leftPad, topPad, rightPad, bottomPad);
        }
        return topMargin;
    }

    private float resolveLogoCenterY(int containerHeight, int currentBarHeight) {
        int rootTop = rootCanvasView != null ? rootCanvasView.getTop() : 0;
        try {
            Object p = pField.get(candidate);
            if (p != null) {
                int pTop = (int) compTopMethod.invoke(p);
                int pHeight = (int) compHeightMethod.invoke(p);
                int logoIdx = (int) findFuncIndexMethod.invoke(p, LOGO_FUNC_ID);
                Object layoutHelper = logoIdx >= 0 ? k1Field.get(p) : null;
                if (layoutHelper != null && itemBoundsMethod.invoke(layoutHelper, logoIdx) instanceof RectF rect
                        && rect.height() > 0f) {
                    if (itemTopMethod != null) {
                        int itemTop = (int) itemTopMethod.invoke(p);
                        return rootTop + pTop + itemTop + rect.height() / 2f;
                    }
                    Rect pad = (Rect) f0RectField.get(p);
                    float centerInP = (pad != null && pinnedFuncClass != null
                            && pinnedFuncClass.isInstance(p) && rect.top < pad.top)
                            ? (pad.top + rect.centerY()) : rect.centerY();
                    if (centerInP > 0f && (pHeight <= 0 || centerInP < pHeight)) {
                        return rootTop + pTop + centerInP;
                    }
                }
                if (pHeight > 0) {
                    return rootTop + pTop + pHeight / 2f;
                }
            }
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // Fall back to container center.
        }
        return containerHeight > 0 ? containerHeight / 2f : currentBarHeight / 2f;
    }

    private int resolveRightMargin() {
        if (c0Field != null && candidate.getWidth() > 0) {
            try {
                int c0 = c0Field.getInt(candidate);
                if (c0 > 0 && candidate.getWidth() > c0) {
                    return candidate.getWidth() - c0;
                }
            } catch (ReflectiveOperationException ignored) {
                // Ignore optional split/one-handed right bound.
            }
        }
        return 0;
    }

    private int fallbackLeftOffset() {
        try {
            return Math.max(0, d0Field.getInt(candidate));
        } catch (ReflectiveOperationException ignored) {
            return 0;
        }
    }

    private boolean isComponentVisible(Field field) throws ReflectiveOperationException {
        Object comp = field.get(candidate);
        return comp != null && Boolean.TRUE.equals(compVisibleMethod.invoke(comp));
    }

    private boolean isRealViewShown(Field field) throws ReflectiveOperationException {
        return field.get(candidate) instanceof View view && view.isShown();
    }

    private static Method findMethod(Class<?> start, String name, Class<?>... parameterTypes)
            throws NoSuchMethodException {
        for (Class<?> c = start; c != null && c != Object.class; c = c.getSuperclass()) {
            try {
                Method m = c.getDeclaredMethod(name, parameterTypes);
                m.setAccessible(true);
                return m;
            } catch (NoSuchMethodException ignored) {
                // Keep searching superclasses.
            }
        }
        throw new NoSuchMethodException(start.getName() + "." + name);
    }

    private static Method findOptionalMethod(Class<?> start, String name, Class<?>... parameterTypes) {
        try {
            return findMethod(start, name, parameterTypes);
        } catch (NoSuchMethodException ignored) {
            return null;
        }
    }

    private static Field findField(Class<?> start, String name) throws NoSuchFieldException {
        for (Class<?> c = start; c != null && c != Object.class; c = c.getSuperclass()) {
            try {
                Field f = c.getDeclaredField(name);
                f.setAccessible(true);
                return f;
            } catch (NoSuchFieldException ignored) {
                // Keep searching superclasses.
            }
        }
        throw new NoSuchFieldException(name);
    }

    private static Field findOptionalField(Class<?> start, String name) {
        try {
            return findField(start, name);
        } catch (NoSuchFieldException ignored) {
            return null;
        }
    }

    private static void applySlotStyle(GradientDrawable shape, boolean dark) {
        shape.setColor(dark ? 0x2bffffff : 0xffffffff);
        shape.setStroke(0, Color.TRANSPARENT);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
