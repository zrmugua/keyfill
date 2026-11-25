package com.mugua.keyfill;

import android.annotation.SuppressLint;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.inputmethodservice.InputMethodService;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.ViewTreeObserver;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/** Hosts the inline row beside the first tool in the docked Compose keyboard. */
@SuppressLint("ViewConstructor")
final class XiaoAiToolbar extends LinearLayout implements ViewTreeObserver.OnPreDrawListener {
    private static final String KEYBOARD_PACKAGE = "com.xiaomi.type";
    private static final int DARK_TOOLBAR_COLOR = 0xff18191b;
    private static final int LIGHT_TOOLBAR_COLOR = 0xffe5e6e7;

    final int height;
    final int toolSize;
    final int edgePadding;
    private final Object uiState;
    private final Method currentMode;
    private final Method isFloating;
    private final Method toolbarTop;
    private final Object candidates;
    private final Object composing;
    private final Method candidateValue;
    private final Method composingValue;
    private boolean stateAvailable = true;
    private final int[] parentLocation = new int[2];
    private final int[] surfaceLocation = new int[2];
    private final View keyboardContent;
    private final Object materialHelper;
    private final Method materialEnabled;
    private final Field materialView;
    private final Rect coveredToolbar = new Rect();
    private final Rect lastCoveredToolbar = new Rect();
    private final Path keyboardOutline = new Path();
    private final List<Drawable> cardBackgrounds = new ArrayList<>();
    private View backgroundHost;
    private int opaqueBackground;
    private boolean dark;
    private boolean materialVisible;
    private ViewOutlineProvider originalOutline;
    private boolean originalClipToOutline;
    private final ViewOutlineProvider toolbarOutline = new ViewOutlineProvider() {
        @Override
        public void getOutline(View view, Outline outline) {
            keyboardOutline.rewind();
            keyboardOutline.setFillType(Path.FillType.EVEN_ODD);
            keyboardOutline.addRect(0, 0, view.getWidth(), view.getHeight(), Path.Direction.CW);
            keyboardOutline.addRect(coveredToolbar.left, coveredToolbar.top, coveredToolbar.right,
                    coveredToolbar.bottom, Path.Direction.CW);
            outline.setPath(keyboardOutline);
        }
    };

    static XiaoAiToolbar bind(InputMethodService service, ViewGroup candidate) {
        try {
            return new XiaoAiToolbar(service, candidate);
        } catch (ReflectiveOperationException | RuntimeException unavailable) {
            AutofillModule.log("Super Xiao Ai toolbar bindings unavailable");
            return null;
        }
    }

    private XiaoAiToolbar(InputMethodService service, ViewGroup candidate) throws ReflectiveOperationException {
        super(candidate.getContext());
        uiState = resolveServiceComponent(service, "getUiStateManager", "uiStateManager");
        currentMode = resolveCurrentModeMethod(uiState);
        isFloating = accessibleMethod(uiState, "x");
        toolbarTop = resolveToolbarTopMethod(uiState);
        Object decoding = resolveServiceComponent(service, "getDecodingInfo", "decodingInfo");
        candidates = stateFlow(decoding, "b");
        composing = stateFlow(decoding, "j");
        candidateValue = accessibleMethod(candidates, "getValue");
        composingValue = accessibleMethod(composing, "getValue");
        height = dimension("keyboard_top_bar_height", 58);
        toolSize = dimension("toolbar_button_size", 32);
        edgePadding = dimension("toolbar_padding", 17);
        keyboardContent = resolveKeyboardContent(candidate);
        Object helper = null;
        Method enabled = null;
        Field backdrop = null;
        try {
            helper = resolveServiceComponent(service, "getHyperMaterialHelper", "hyperMaterialHelper");
            enabled = accessibleMethod(helper, "i");
            backdrop = resolveMaterialViewField(helper.getClass());
        } catch (ReflectiveOperationException | RuntimeException unavailable) {
            // The opaque toolbar is available without HyperMaterial bindings.
        }
        materialHelper = helper;
        materialEnabled = enabled;
        materialView = backdrop;
        setVisibility(INVISIBLE);
    }

    @SuppressLint("ClickableViewAccessibility")
    void setToolbarBackground(View host, int color, boolean dark) {
        backgroundHost = host;
        opaqueBackground = color;
        this.dark = dark;
        host.setBackgroundColor(color);
        // Block stray taps from reaching covered toolbar buttons while the translucent backdrop is active.
        host.setOnTouchListener((view, event) -> materialVisible);
    }

    Drawable cardBackground() {
        int id = getResources().getIdentifier("bg_display_candidate_size_preview_chip", "drawable", KEYBOARD_PACKAGE);
        if (id == 0) {
            return null;
        }
        Drawable raw = getContext().getDrawable(id);
        if (raw == null) {
            return null;
        }
        Drawable background = raw.mutate();
        background.setTint(cardTint(materialVisible));
        cardBackgrounds.add(background);
        return background;
    }

    private int cardTint(boolean translucent) {
        if (translucent) {
            return dark ? 0x28ffffff : 0x65ffffff;
        }
        return dark ? 0xff3d3d3d : 0xffeeeeee;
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        getViewTreeObserver().addOnPreDrawListener(this);
    }

    @Override
    protected void onDetachedFromWindow() {
        getViewTreeObserver().removeOnPreDrawListener(this);
        updateMaterial(false);
        super.onDetachedFromWindow();
    }

    @Override
    public boolean onPreDraw() {
        syncTheme();
        boolean visible = canShowSuggestions() && alignWithToolbar();
        setVisibility(visible ? VISIBLE : INVISIBLE);
        updateMaterial(visible && hasNativeBlur() && locateCoveredToolbar());
        return true;
    }

    private void syncTheme() {
        boolean currentDark = (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES;
        if (currentDark == dark) {
            return;
        }
        dark = currentDark;
        opaqueBackground = currentDark ? DARK_TOOLBAR_COLOR : LIGHT_TOOLBAR_COLOR;
        if (backgroundHost != null && !materialVisible) {
            backgroundHost.setBackgroundColor(opaqueBackground);
        }
        int tint = cardTint(materialVisible);
        for (Drawable background : cardBackgrounds) {
            background.setTint(tint);
        }
    }

    private boolean hasNativeBlur() {
        if (materialView == null || keyboardContent == null || !keyboardContent.isAttachedToWindow()) {
            return false;
        }
        try {
            return Boolean.TRUE.equals(materialEnabled.invoke(materialHelper))
                    && materialView.get(materialHelper) instanceof View backdrop && backdrop.isShown();
        } catch (ReflectiveOperationException | RuntimeException unavailable) {
            return false;
        }
    }

    private boolean locateCoveredToolbar() {
        if (backgroundHost == null || keyboardContent == null) {
            return false;
        }
        backgroundHost.getLocationInWindow(surfaceLocation);
        keyboardContent.getLocationInWindow(parentLocation);
        int left = surfaceLocation[0] - parentLocation[0];
        int top = surfaceLocation[1] - parentLocation[1];
        coveredToolbar.set(left, top, left + backgroundHost.getWidth(), top + backgroundHost.getHeight());
        return coveredToolbar.intersect(0, 0, keyboardContent.getWidth(), keyboardContent.getHeight());
    }

    private void updateMaterial(boolean enabled) {
        if (enabled != materialVisible) {
            materialVisible = enabled;
            if (keyboardContent != null) {
                if (enabled) {
                    originalOutline = keyboardContent.getOutlineProvider();
                    originalClipToOutline = keyboardContent.getClipToOutline();
                    keyboardContent.setOutlineProvider(toolbarOutline);
                    keyboardContent.setClipToOutline(true);
                } else {
                    if (keyboardContent.getOutlineProvider() == toolbarOutline) {
                        keyboardContent.setOutlineProvider(originalOutline);
                        keyboardContent.setClipToOutline(originalClipToOutline);
                    }
                    lastCoveredToolbar.setEmpty();
                }
            }
            if (backgroundHost != null) {
                backgroundHost.setBackgroundColor(enabled ? Color.TRANSPARENT : opaqueBackground);
            }
            int tint = cardTint(enabled);
            for (Drawable background : cardBackgrounds) {
                background.setTint(tint);
            }
        }
        if (enabled && keyboardContent != null && !coveredToolbar.equals(lastCoveredToolbar)) {
            lastCoveredToolbar.set(coveredToolbar);
            keyboardContent.invalidateOutline();
        }
    }

    private boolean alignWithToolbar() {
        if (!(getParent() instanceof View parent) || getHeight() == 0) {
            return false;
        }
        try {
            int topInWindow = (int) toolbarTop.invoke(uiState);
            parent.getLocationInWindow(parentLocation);
            int top = topInWindow - parentLocation[1];
            if (topInWindow <= 0 || top < 0 || top + getHeight() > parent.getHeight()) {
                return false;
            }
            setTranslationY(top - getTop());
            return true;
        } catch (ReflectiveOperationException | RuntimeException unavailable) {
            stateAvailable = false;
            AutofillModule.log("Super Xiao Ai toolbar position unavailable");
            return false;
        }
    }

    private boolean canShowSuggestions() {
        if (!stateAvailable) {
            return false;
        }
        try {
            return currentMode.invoke(uiState) instanceof Enum<?> mode && "MAIN".equals(mode.name())
                    && Boolean.FALSE.equals(isFloating.invoke(uiState))
                    && candidateValue.invoke(candidates) instanceof Collection<?> list && list.isEmpty()
                    && composingValue.invoke(composing) instanceof CharSequence text && text.length() == 0;
        } catch (ReflectiveOperationException | RuntimeException unavailable) {
            stateAvailable = false;
            AutofillModule.log("Super Xiao Ai candidate state unavailable");
            return false;
        }
    }

    private int dimension(String name, int fallbackDp) {
        int id = getResources().getIdentifier(name, "dimen", KEYBOARD_PACKAGE);
        return id == 0 ? Math.round(fallbackDp * getResources().getDisplayMetrics().density)
                : getResources().getDimensionPixelSize(id);
    }

    private static View resolveKeyboardContent(ViewGroup candidate) {
        View content = candidate.getChildAt(0);
        if (content == null) {
            return null;
        }
        if ("f8.d".equals(content.getClass().getName())
                || (content instanceof FrameLayout && !(content instanceof XiaoAiToolbar))) {
            return content;
        }
        return null;
    }

    private static Object resolveServiceComponent(InputMethodService service, String getterPrefix, String fieldName)
            throws ReflectiveOperationException {
        Method fallbackGetter = null;
        for (Method method : service.getClass().getMethods()) {
            if (method.getParameterCount() != 0) {
                continue;
            }
            String name = method.getName();
            if (name.equals(getterPrefix)) {
                method.setAccessible(true);
                Object value = method.invoke(service);
                if (value != null) {
                    return value;
                }
            } else if (name.startsWith(getterPrefix + "$") && fallbackGetter == null) {
                fallbackGetter = method;
            }
        }
        if (fallbackGetter != null) {
            fallbackGetter.setAccessible(true);
            Object value = fallbackGetter.invoke(service);
            if (value != null) {
                return value;
            }
        }
        for (Class<?> type = service.getClass(); type != null && type != Object.class; type = type.getSuperclass()) {
            try {
                Field field = type.getDeclaredField(fieldName);
                field.setAccessible(true);
                Object value = field.get(service);
                if (value != null) {
                    return value;
                }
            } catch (NoSuchFieldException ignored) {
                // Continue searching superclasses.
            }
        }
        throw new NoSuchMethodException(getterPrefix);
    }

    private static Method resolveCurrentModeMethod(Object uiState) throws NoSuchMethodException {
        try {
            Method known = accessibleMethod(uiState, "p");
            if (hasEnumConstant(known.getReturnType(), "MAIN")) {
                return known;
            }
        } catch (NoSuchMethodException ignored) {
            // Fall through to signature matching.
        }
        for (Method method : uiState.getClass().getMethods()) {
            if (method.getParameterCount() == 0 && hasEnumConstant(method.getReturnType(), "MAIN")) {
                method.setAccessible(true);
                return method;
            }
        }
        throw new NoSuchMethodException("currentMode");
    }

    private static boolean hasEnumConstant(Class<?> type, String constantName) {
        if (!type.isEnum()) {
            return false;
        }
        Object[] constants = type.getEnumConstants();
        if (constants == null) {
            return false;
        }
        for (Object constant : constants) {
            if (constant instanceof Enum<?> value && constantName.equals(value.name())) {
                return true;
            }
        }
        return false;
    }

    private static Method resolveToolbarTopMethod(Object uiState) throws NoSuchMethodException {
        try {
            Method known = accessibleMethod(uiState, "o");
            if (known.getReturnType() == int.class) {
                return known;
            }
        } catch (NoSuchMethodException ignored) {
            // Fall through to signature matching.
        }
        Method match = null;
        for (Method method : uiState.getClass().getDeclaredMethods()) {
            if (method.getParameterCount() == 0 && method.getReturnType() == int.class) {
                if (match != null) {
                    throw new NoSuchMethodException("toolbarTop");
                }
                match = method;
            }
        }
        if (match == null) {
            throw new NoSuchMethodException("toolbarTop");
        }
        match.setAccessible(true);
        return match;
    }

    private static Field resolveMaterialViewField(Class<?> helperClass) throws NoSuchFieldException {
        try {
            Field known = AutofillModule.accessibleField(helperClass, "j");
            if (View.class.isAssignableFrom(known.getType())) {
                return known;
            }
        } catch (NoSuchFieldException ignored) {
            // Fall through to type matching.
        }
        Field match = null;
        for (Field field : helperClass.getDeclaredFields()) {
            if (View.class.isAssignableFrom(field.getType())) {
                if (match != null) {
                    throw new NoSuchFieldException("materialView");
                }
                match = field;
            }
        }
        if (match == null) {
            throw new NoSuchFieldException("materialView");
        }
        match.setAccessible(true);
        return match;
    }

    private static Object stateFlow(Object decoding, String name) throws ReflectiveOperationException {
        Field field = decoding.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(decoding);
    }

    private static Method accessibleMethod(Object instance, String name) throws NoSuchMethodException {
        Method method = instance.getClass().getMethod(name);
        method.setAccessible(true);
        return method;
    }
}
