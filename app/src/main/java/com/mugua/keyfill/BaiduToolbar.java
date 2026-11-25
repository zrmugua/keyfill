package com.mugua.keyfill;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.inputmethodservice.InputMethodService;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.PopupWindow;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/** Hosts the inline autofill row inside Baidu IME's CandFrameAgent. */
@SuppressLint("ViewConstructor")
final class BaiduToolbar extends LinearLayout implements ImeToolbar, ViewTreeObserver.OnPreDrawListener {
    private static volatile Object activeCandHandler;
    private static volatile boolean inlineVisible;
    private static volatile int clipRightPx;
    private static final ThreadLocal<Integer> savedCanvasDepth = ThreadLocal.withInitial(() -> -1);

    private final InputMethodService service;
    private final ViewGroup candidate;
    private final Method getCandWrapper;
    private final Method getKeymapViewManager;
    private final Method getSmartPredictor;
    private final Method isStandardCandOn;
    private final Method isHardKeyboardCandState;
    private final Method isSearchServiceOn;
    private final Method globalIsFloating;
    private final Field serviceCoreField;
    private final Field logoPopupField;
    private final Method calculatorActiveMethod;
    private final int[] candLocation = new int[2];
    private final int[] agentLocation = new int[2];
    private final List<GradientDrawable> slotShapes = new ArrayList<>();

    private Method wrapperViewGetter;
    private Field inputContainerInnerField;
    private Field candidateHandlerField;
    private Field keymapMacField;
    private Field keymapHasToolbarField;
    private Field inputStatMacField;
    private Method inputStatVoiceHwMethod;
    private Method logoRectMethod;
    private Method handlerExtraStateMethod;
    private Method handlerPasteMethod;
    private Field kmvmAField;
    private Field kmvmBField;
    private Field kmvmCField;
    private Field kmvmDField;
    private Method kmvmSceneHeightMethod;
    private Field coreCandStateField;
    private Method candStateActiveMethod;
    private Field smartPredictorActiveField;

    private boolean dark;
    private int rowHeight;
    private int leftMarginPx;
    private int rightMarginPx;
    private final int leftPad;
    private final int rightPad;
    private final int heightPx;

    static void installHooks(AutofillModule module, ClassLoader loader) {
        try {
            Class<?> candRender = Class.forName("com.baidu.input.ime.render.CandRender", false, loader);
            Class<?> absHandler = Class.forName("com.baidu.input.panel.render.cand.AbsCandHandler", false, loader);
            module.hook(candRender.getDeclaredMethod("m", Canvas.class)).intercept(chain -> {
                savedCanvasDepth.set(-1);
                try {
                    return chain.proceed();
                } finally {
                    int saved = savedCanvasDepth.get();
                    if (saved >= 0 && chain.getArg(0) instanceof Canvas canvas) {
                        canvas.restoreToCount(saved);
                    }
                    savedCanvasDepth.set(-1);
                }
            });
            module.hook(absHandler.getDeclaredMethod("p0", Canvas.class)).intercept(chain -> {
                Object handler = chain.getThisObject();
                int right = clipRightPx;
                if (inlineVisible && right > 0 && handler != null && handler == activeCandHandler
                        && chain.getArg(0) instanceof Canvas canvas && savedCanvasDepth.get() < 0) {
                    savedCanvasDepth.set(canvas.save());
                    canvas.clipRect(0, 0, right, canvas.getHeight());
                }
                return chain.proceed();
            });
        } catch (ReflectiveOperationException | RuntimeException unavailable) {
            AutofillModule.log("Baidu canvas clip hook unavailable: " + unavailable.getClass().getSimpleName());
        }
    }

    static BaiduToolbar bind(InputMethodService service, ViewGroup candidate, boolean initialDark) {
        try {
            return new BaiduToolbar(service, candidate, initialDark);
        } catch (ReflectiveOperationException | RuntimeException unavailable) {
            AutofillModule.log("Baidu toolbar bindings unavailable: " + unavailable.getClass().getSimpleName());
            return null;
        }
    }

    static boolean isDark(Context context) {
        ClassLoader loader = context.getClassLoader();
        try {
            Class<?> skinRender = Class.forName("com.baidu.skinrender.SkinRender", false, loader);
            if (skinRender.getMethod("e").invoke(null) != null) {
                Object settings = skinRender.getMethod("o").invoke(null);
                if (settings != null && findField(settings.getClass(), "a").getBoolean(settings)) {
                    return true;
                }
                Object status = skinRender.getMethod("p").invoke(null);
                if (status != null && Boolean.TRUE.equals(status.getClass().getMethod("g").invoke(status))) {
                    return !Boolean.TRUE.equals(status.getClass().getMethod("j").invoke(status));
                }
                Class<?> picker = Class.forName("com.baidu.util.ColorPicker", false, loader);
                int fg = (int) picker.getMethod("getUnSelectedColor").invoke(null);
                if (Color.alpha(fg) > 0) {
                    return Color.luminance(fg | 0xff000000) > 0.5f;
                }
            }
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // Fall back to system configuration.
        }
        return (context.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES;
    }

    private BaiduToolbar(InputMethodService service, ViewGroup candidate, boolean initialDark)
            throws ReflectiveOperationException {
        super(candidate.getContext());
        this.service = service;
        this.candidate = candidate;
        this.dark = initialDark;
        Class<?> svcClass = service.getClass();
        ClassLoader loader = svcClass.getClassLoader();

        getCandWrapper = svcClass.getMethod("getCandViewWrapper");
        getKeymapViewManager = svcClass.getMethod("getKeymapViewManager");
        getSmartPredictor = svcClass.getMethod("getSmartPredictor");
        isStandardCandOn = svcClass.getMethod("isStandardCandOn");
        isHardKeyboardCandState = svcClass.getMethod("isHardKeyboardCandState");
        isSearchServiceOn = svcClass.getMethod("isSearchServiceOn");
        serviceCoreField = findField(svcClass, "u");

        Class<?> globalClass = Class.forName("com.baidu.input.pub.Global", false, loader);
        globalIsFloating = globalClass.getMethod("M");

        Field popup = null;
        try {
            popup = findField(Class.forName("com.baidu.input.logomenu.LogoGlobal", false, loader), "e");
        } catch (ReflectiveOperationException ignored) {
            // Optional logo popup window guard.
        }
        logoPopupField = popup;

        Method calc = null;
        try {
            calc = Class.forName("com.baidu.input.ime.newcore.operator.CalculatorHandler", false, loader)
                    .getMethod("a");
        } catch (ReflectiveOperationException ignored) {
            // Optional calculator state guard.
        }
        calculatorActiveMethod = calc;

        View candInputView = resolveCandInputView();
        Object candidateView = resolveCandidateView(candInputView);
        Object handler = resolveCandHandler(candidateView);
        rowHeight = (candInputView != null && candInputView.getHeight() > 0) ? candInputView.getHeight() : dp(42);
        Rect logoRect = resolveLogoRect(handler);
        int logoRight = (logoRect != null && logoRect.right > 0) ? logoRect.right : dp(58);
        computeMargins(candInputView, logoRight);
        leftPad = dp(6);
        rightPad = dp(10);
        heightPx = Math.min(dp(33), Math.max(dp(26), rowHeight - dp(9)));

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
        int width = candidate.getWidth() - leftMarginPx - rightMarginPx - leftPad - rightPad;
        if (hasPinned) {
            width -= heightPx + pinnedGapPx;
        }
        return width;
    }

    @Override
    public Drawable cardBackground(boolean pinned, boolean isDark) {
        GradientDrawable shape = new GradientDrawable();
        shape.setCornerRadius(dp(pinned ? 12 : 16));
        applySlotStyle(shape, isDark, isDefaultBuiltinSkin());
        slotShapes.add(shape);
        return new RippleDrawable(ColorStateList.valueOf(isDark ? 0x24ffffff : 0x19000000), shape, null);
    }

    @Override
    public View attach(ViewGroup host) {
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, rowHeight, Gravity.TOP);
        params.leftMargin = leftMarginPx;
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
        clearClipState(resolveCandInputView());
        super.onDetachedFromWindow();
    }

    @Override
    public boolean onPreDraw() {
        if (candidate.getChildAt(candidate.getChildCount() - 1) != this) {
            bringToFront();
        }
        syncTheme();
        View candInputView = resolveCandInputView();
        Object candidateView = resolveCandidateView(candInputView);
        Object handler = resolveCandHandler(candidateView);
        Rect logoRect = resolveLogoRect(handler);
        int logoRight = (logoRect != null && logoRect.right > 0) ? logoRect.right : dp(58);
        if (candInputView != null && candInputView.getHeight() > 0) {
            int prevLeft = leftMarginPx;
            int prevRight = rightMarginPx;
            int newHeight = candInputView.getHeight();
            int relTop = computeMargins(candInputView, logoRight);
            if ((newHeight != rowHeight || leftMarginPx != prevLeft || rightMarginPx != prevRight)
                    && getLayoutParams() instanceof FrameLayout.LayoutParams params) {
                rowHeight = newHeight;
                params.height = newHeight;
                params.leftMargin = leftMarginPx;
                params.rightMargin = rightMarginPx;
                setLayoutParams(params);
            }
            if (logoRect != null && logoRect.height() > 0) {
                int maxShift = Math.max(0, newHeight - heightPx);
                int offset = Math.max(-maxShift, Math.min(maxShift, logoRect.top + logoRect.bottom - newHeight));
                int topPad = Math.max(0, offset);
                int bottomPad = Math.max(0, -offset);
                if (topPad != getPaddingTop() || bottomPad != getPaddingBottom()) {
                    setPadding(leftPad, topPad, rightPad, bottomPad);
                }
            }
            setTranslationY(relTop - getTop());
        }
        boolean show = canShowSuggestions(candInputView, candidateView, handler);
        setVisibility(show ? VISIBLE : INVISIBLE);
        boolean clipChanged = (inlineVisible != show) || (show && (activeCandHandler != handler || clipRightPx != logoRight));
        activeCandHandler = show ? handler : null;
        clipRightPx = logoRight;
        inlineVisible = show;
        if (clipChanged && candInputView != null) {
            candInputView.invalidate();
        }
        return true;
    }

    private void clearClipState(View candInputView) {
        boolean wasVisible = inlineVisible;
        inlineVisible = false;
        activeCandHandler = null;
        clipRightPx = 0;
        if (wasVisible && candInputView != null) {
            candInputView.invalidate();
        }
    }

    private void syncTheme() {
        boolean currentDark = isDark(service);
        if (currentDark == dark) {
            return;
        }
        dark = currentDark;
        boolean builtin = isDefaultBuiltinSkin();
        for (GradientDrawable shape : slotShapes) {
            applySlotStyle(shape, currentDark, builtin);
        }
    }

    private boolean canShowSuggestions(View candInputView, Object candidateView, Object handler) {
        if (candInputView == null || candidateView == null || handler == null
                || !candInputView.isShown() || candInputView.getHeight() <= 0) {
            return false;
        }
        try {
            if (!service.isInputViewShown() || service.isFullscreenMode()
                    || !Boolean.TRUE.equals(isStandardCandOn.invoke(service))
                    || Boolean.TRUE.equals(isHardKeyboardCandState.invoke(service))
                    || Boolean.TRUE.equals(globalIsFloating.invoke(null))
                    || Boolean.TRUE.equals(isSearchServiceOn.invoke(service))) {
                return false;
            }
            if (!isMainKeymapActive() || !hasToolbarAndNormalMode(candidateView)
                    || isComposingOrCandidates(handler)) {
                return false;
            }
            return !(logoPopupField != null
                    && logoPopupField.get(null) instanceof PopupWindow popup
                    && popup.isShowing());
        } catch (ReflectiveOperationException | RuntimeException unavailable) {
            return false;
        }
    }

    private boolean isMainKeymapActive() throws ReflectiveOperationException {
        Object kmvm = getKeymapViewManager.invoke(service);
        if (kmvm == null) {
            return false;
        }
        if (kmvmAField == null) {
            Class<?> kmvmClass = kmvm.getClass();
            kmvmAField = findField(kmvmClass, "a");
            kmvmBField = findField(kmvmClass, "b");
            kmvmCField = findField(kmvmClass, "c");
            kmvmDField = findField(kmvmClass, "d");
            kmvmSceneHeightMethod = kmvmClass.getMethod("f");
        }
        Object activeCand = kmvmAField.get(kmvm);
        Object activeSoft = kmvmBField.get(kmvm);
        return activeCand != null && activeCand == kmvmCField.get(kmvm)
                && activeSoft != null && activeSoft == kmvmDField.get(kmvm)
                && ((int) kmvmSceneHeightMethod.invoke(kmvm)) == 0;
    }

    private boolean hasToolbarAndNormalMode(Object candidateView) throws ReflectiveOperationException {
        if (keymapMacField == null) {
            Class<?> cvClass = candidateView.getClass();
            keymapMacField = findField(cvClass, "b");
            keymapHasToolbarField = findField(keymapMacField.getType(), "a");
            inputStatMacField = findField(cvClass, "c");
            inputStatVoiceHwMethod = inputStatMacField.getType().getMethod("m");
        }
        Object keymapMac = keymapMacField.get(candidateView);
        Object statMac = inputStatMacField.get(candidateView);
        return keymapMac != null && keymapHasToolbarField.getBoolean(keymapMac)
                && (statMac == null || !Boolean.TRUE.equals(inputStatVoiceHwMethod.invoke(statMac)));
    }

    private boolean isComposingOrCandidates(Object handler) throws ReflectiveOperationException {
        Object core = serviceCoreField.get(service);
        if (core != null) {
            if (coreCandStateField == null) {
                coreCandStateField = findField(core.getClass(), "A");
                candStateActiveMethod = coreCandStateField.getType().getMethod("o");
            }
            Object candState = coreCandStateField.get(core);
            if (candState != null && Boolean.TRUE.equals(candStateActiveMethod.invoke(candState))) {
                return true;
            }
        }
        Object predictor = getSmartPredictor.invoke(service);
        if (predictor != null) {
            if (smartPredictorActiveField == null) {
                smartPredictorActiveField = findField(predictor.getClass(), "e");
            }
            if (smartPredictorActiveField.getBoolean(predictor)) {
                return true;
            }
        }
        if (calculatorActiveMethod != null && Boolean.TRUE.equals(calculatorActiveMethod.invoke(null))) {
            return true;
        }
        if (handlerExtraStateMethod == null) {
            Class<?> hClass = handler.getClass();
            handlerExtraStateMethod = hClass.getMethod("E2");
            handlerPasteMethod = hClass.getMethod("x");
        }
        return Boolean.TRUE.equals(handlerExtraStateMethod.invoke(handler))
                || Boolean.TRUE.equals(handlerPasteMethod.invoke(handler));
    }

    private View resolveCandInputView() {
        try {
            Object wrapper = getCandWrapper.invoke(service);
            if (wrapper == null) {
                return null;
            }
            if (wrapperViewGetter == null) {
                wrapperViewGetter = wrapper.getClass().getMethod("b");
            }
            return wrapperViewGetter.invoke(wrapper) instanceof View v ? v : null;
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return null;
        }
    }

    private Object resolveCandidateView(View candInputView) {
        if (candInputView == null) {
            return null;
        }
        try {
            if (inputContainerInnerField == null) {
                inputContainerInnerField = findField(candInputView.getClass(), "f");
            }
            return inputContainerInnerField.get(candInputView);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return null;
        }
    }

    private Object resolveCandHandler(Object candidateView) {
        if (candidateView == null) {
            return null;
        }
        try {
            if (candidateHandlerField == null) {
                candidateHandlerField = findField(candidateView.getClass(), "F");
            }
            return candidateHandlerField.get(candidateView);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return null;
        }
    }

    private Rect resolveLogoRect(Object handler) {
        if (handler != null) {
            try {
                if (logoRectMethod == null) {
                    logoRectMethod = handler.getClass().getMethod("N");
                }
                if (logoRectMethod.invoke(handler) instanceof Rect rect && rect.right > 0) {
                    return rect;
                }
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                // Use dp fallback.
            }
        }
        return null;
    }

    private int computeMargins(View candInputView, int logoRight) {
        if (candInputView == null || candInputView.getWidth() <= 0) {
            leftMarginPx = logoRight;
            rightMarginPx = 0;
            return 0;
        }
        candInputView.getLocationInWindow(candLocation);
        candidate.getLocationInWindow(agentLocation);
        int relLeft = candLocation[0] - agentLocation[0];
        int relTop = candLocation[1] - agentLocation[1];
        leftMarginPx = Math.max(0, relLeft + logoRight);
        rightMarginPx = Math.max(0, candidate.getWidth() - (relLeft + candInputView.getWidth()));
        return relTop;
    }

    private boolean isDefaultBuiltinSkin() {
        try {
            Class<?> skinRender = Class.forName("com.baidu.skinrender.SkinRender", false, service.getClassLoader());
            if (skinRender.getMethod("e").invoke(null) != null) {
                Object settings = skinRender.getMethod("o").invoke(null);
                if (settings != null && findField(settings.getClass(), "a").getBoolean(settings)) {
                    return false;
                }
                Object status = skinRender.getMethod("p").invoke(null);
                return status != null && Boolean.TRUE.equals(status.getClass().getMethod("g").invoke(status));
            }
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // Treat unknown skin as translucent-friendly.
        }
        return false;
    }

    private static void applySlotStyle(GradientDrawable shape, boolean dark, boolean builtinSkin) {
        if (builtinSkin) {
            shape.setColor(dark ? 0xff383a3f : 0xffffffff);
            shape.setStroke(1, dark ? 0xff4b4d52 : 0xffd8dce3);
        } else {
            shape.setColor(dark ? 0x2effffff : 0x88ffffff);
            shape.setStroke(1, dark ? 0x33ffffff : 0x18000000);
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

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
