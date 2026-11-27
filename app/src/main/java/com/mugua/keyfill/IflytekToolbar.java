package com.mugua.keyfill;

import android.annotation.SuppressLint;
import android.app.Dialog;
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
import android.view.ViewParent;
import android.view.ViewTreeObserver;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import java.lang.reflect.Field;
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
    private static final String GRID_GROUP_CLASS =
            "com.iflytek.inputmethod.common.view.widget.GridGroup";
    private static final String THEME_SERVICE =
            "com.iflytek.inputmethod.input.themeadapter.IThemeAdapterManager";
    private static final String CAND_NEXT_SERVICE =
            "com.iflytek.inputmethod.candidatenext.api.ICandidateNext";
    private static final String CAND_STATE_CENTER_CLASS =
            "com.iflytek.inputmethod.candidatenext.api.ICandidateStateCenter";
    private static final String FRAGMENT_SHOW_SERVICE =
            "com.iflytek.inputmethod.depend.main.services.ImeFragmentShow";
    private static final int SMART_LINE_LINEAR_ID = 100;
    private static final int SMART_LINE_OWL_ID = 101;
    private static final int CLASSIC_LOGO_GRID_ID = 1229;

    private static final int MODE_NONE = 0;
    private static final int MODE_CAND_NEXT = 1;
    private static final int MODE_CLASSIC = 2;
    private static final int MODE_SMART_LINE = 3;

    private static volatile Object activeClassicTopBar;
    private static volatile boolean classicInlineVisible;
    private static volatile int classicClipRightPx;

    private final InputMethodService service;
    private final ViewGroup inputView;
    private final int mainAgentId;
    private final int cardContainerId;
    private final int cardContainerRvId;
    private final int cardContainerBgId;
    private final int expandCandidateBtnId;
    private final int quickFunIconBgId;
    private final int hideEntranceId;
    private final int[] nonIdleSiblingIds;
    private final int floatLayoutId;
    private final int bizContainerId;
    private final int inputContainerId;
    private final Rect barBounds = new Rect();
    private final Rect tmpBounds = new Rect();
    private final Map<View, Float> savedAlphas = new IdentityHashMap<>();
    private final List<GradientDrawable> slotShapes = new ArrayList<>();

    private Object candStateLiveData;
    private Method liveDataGetValueMethod;
    private boolean candStateUnavailable;
    private Object fragShowService;
    private Method isFragShowingMethod;
    private boolean fragShowUnavailable;

    private Method contentGridMethod;
    private Method hk4ToolbarHostMethod;
    private Method rr8TopBarMethod;
    private Method rr8MainAreaMethod;
    private Method c28TopBarMethod;
    private Method gridFindByIdMethod;
    private Method gridIsShownMethod;
    private Method gridTopMethod;
    private Method gridRightMethod;
    private Method gridHeightMethod;
    private Field x18CandViewsField;
    private Method x18CandStripMethod;
    private Method candCountMethod;

    private boolean dark;
    private int barMode;
    private int topOffset;
    private int rowHeight;
    private int leftInset;
    private int rightInset;
    private int leftPad;
    private int rightPad;
    private int topPad;
    private int bottomPad;
    private int heightPx;

    static void installHooks(AutofillModule module, ClassLoader loader) {
        try {
            Class<?> gridGroup = Class.forName(GRID_GROUP_CLASS, false, loader);
            Method dispatchDraw = gridGroup.getDeclaredMethod("dispatchDraw", Canvas.class);
            dispatchDraw.setAccessible(true);
            module.hook(dispatchDraw).intercept(chain -> {
                Object group = chain.getThisObject();
                int right = classicClipRightPx;
                if (classicInlineVisible && right > 0 && group != null && group == activeClassicTopBar
                        && chain.getArg(0) instanceof Canvas canvas) {
                    int save = canvas.save();
                    canvas.clipRect(0, 0, right, canvas.getHeight());
                    try {
                        return chain.proceed();
                    } finally {
                        canvas.restoreToCount(save);
                    }
                }
                return chain.proceed();
            });
        } catch (ReflectiveOperationException | RuntimeException unavailable) {
            AutofillModule.log("iFlytek classic toolbar hook unavailable: " + unavailable.getClass().getSimpleName());
        }
    }

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
        cardContainerRvId = id("card_container_rv");
        cardContainerBgId = id("card_container_bg");
        expandCandidateBtnId = id("expand_candidate_btn");
        quickFunIconBgId = id("quick_fun_icon_bg");
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
        inputContainerId = id("input_container");

        ViewGroup candNext = findDescendantByClass(inputView, CAND_NEXT_CLASS);
        ViewGroup smartLine = findDescendantByClass(inputView, SMART_LINE_CLASS);
        View keyGrid = findShownKeyGrid();
        Object topBar = resolveClassicTopBar(keyGrid);
        updateGeometryFromActiveBar(candNext, smartLine, keyGrid, topBar);
        heightPx = computeTargetCardHeight();

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
        clearClassicClipState(findShownKeyGrid());
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
        View keyGrid = findShownKeyGrid();
        Object topBar = resolveClassicTopBar(keyGrid);
        int prevMode = barMode;
        int prevTop = topOffset;
        int prevHeight = rowHeight;
        int prevLeft = leftInset;
        int prevRight = rightInset;
        int prevLeftPad = leftPad;
        int prevRightPad = rightPad;
        int prevTopPad = topPad;
        int prevBottomPad = bottomPad;
        int clipRight = updateGeometryFromActiveBar(candNext, smartLine, keyGrid, topBar);
        syncSlotDimensions(computeTargetCardHeight());
        if (prevMode != barMode || prevLeftPad != leftPad || prevRightPad != rightPad
                || prevTopPad != topPad || prevBottomPad != bottomPad) {
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
        boolean show = canShowSuggestions(candNext, smartLine, keyGrid, topBar);
        setVisibility(show ? VISIBLE : INVISIBLE);
        updateClassicClipState(keyGrid, topBar, show && barMode == MODE_CLASSIC, clipRight);
        if (!show) {
            restoreIdleViews();
        } else {
            if (prevMode != barMode) {
                restoreIdleViews();
            }
            if (barMode == MODE_CAND_NEXT && candNext != null) {
                fadeCandNextIdleViews(candNext);
                fadeOverlappingOwl(smartLine);
            } else if (barMode == MODE_SMART_LINE && smartLine != null) {
                fadeSmartLineIdleBanner(smartLine);
                fadeView(smartLine.findViewById(SMART_LINE_OWL_ID));
            } else if (barMode == MODE_CLASSIC) {
                fadeOverlappingOwl(smartLine);
            }
        }
        return true;
    }

    private int updateGeometryFromActiveBar(ViewGroup candNext, ViewGroup smartLine, View keyGrid, Object topBar) {
        topPad = 0;
        bottomPad = 0;
        if (isCandNextActive(candNext)) {
            barMode = MODE_CAND_NEXT;
            barBounds.set(0, 0, candNext.getWidth(), candNext.getHeight());
            inputView.offsetDescendantRectToMyCoords(candNext, barBounds);
            int candLeft = barBounds.left;
            int candRight = barBounds.right;
            View cardContainer = findById(candNext, cardContainerId);
            if (cardContainer != null && cardContainer.getVisibility() == VISIBLE
                    && cardContainer.getHeight() >= dp(32)) {
                tmpBounds.set(0, 0, cardContainer.getWidth(), cardContainer.getHeight());
                inputView.offsetDescendantRectToMyCoords(cardContainer, tmpBounds);
                topOffset = Math.max(0, tmpBounds.top);
                rowHeight = tmpBounds.height();
            } else {
                topOffset = Math.max(0, barBounds.top);
                rowHeight = Math.max(dp(36), barBounds.height());
            }
            View mainAgent = findById(candNext, mainAgentId);
            if (mainAgent != null && mainAgent.getVisibility() == VISIBLE && mainAgent.getWidth() > 0) {
                int agentRight = mainAgent.getRight() > 0 ? mainAgent.getRight() : mainAgent.getWidth();
                leftInset = Math.max(0, candLeft + agentRight);
                leftPad = dp(4);
            } else {
                View firstQuickIcon = findLeadingQuickFunItem(cardContainer);
                if (firstQuickIcon != null) {
                    View iconBg = findById(firstQuickIcon, quickFunIconBgId);
                    View anchor = iconBg != null && iconBg.getVisibility() == VISIBLE && iconBg.getWidth() > 0
                            ? iconBg : firstQuickIcon;
                    tmpBounds.set(0, 0, anchor.getWidth(), anchor.getHeight());
                    inputView.offsetDescendantRectToMyCoords(anchor, tmpBounds);
                    leftInset = Math.max(0, tmpBounds.right);
                    leftPad = anchor == iconBg ? dp(8) : dp(4);
                } else {
                    leftInset = Math.max(0, candLeft);
                    leftPad = dp(12);
                }
            }
            rightInset = Math.max(0, inputView.getWidth() - candRight);
            rightPad = dp(12);
            return 0;
        }
        if (isClassicToolbarPresent(topBar) && keyGrid != null) {
            barMode = MODE_CLASSIC;
            barBounds.set(0, 0, keyGrid.getWidth(), keyGrid.getHeight());
            inputView.offsetDescendantRectToMyCoords(keyGrid, barBounds);
            int barTop = gridInt(gridTopMethod, topBar);
            int barH = Math.max(dp(36), gridInt(gridHeightMethod, topBar));
            int barRight = gridInt(gridRightMethod, topBar);
            Object logo = findGrid(topBar, CLASSIC_LOGO_GRID_ID);
            boolean logoShown = gridBoolean(gridIsShownMethod, logo);
            int logoRight = logoShown ? gridInt(gridRightMethod, logo) : 0;
            topOffset = Math.max(0, barBounds.top + barTop);
            rowHeight = barH;
            leftInset = Math.max(0, barBounds.left + (logoRight > 0 ? logoRight : dp(48)));
            rightInset = barRight > 0 ? Math.max(0, inputView.getWidth() - (barBounds.left + barRight))
                    : Math.max(0, inputView.getWidth() - barBounds.right);
            leftPad = logoRight > 0 ? (logoRight >= dp(56) ? 0 : dp(4)) : dp(12);
            rightPad = dp(12);
            if (logo != null) {
                int logoH = gridInt(gridHeightMethod, logo);
                if (logoH > 0) {
                    int targetH = Math.min(dp(34), Math.max(dp(28), barH - dp(10)));
                    int maxShift = Math.max(0, barH - targetH);
                    int offset = 2 * (gridInt(gridTopMethod, logo) - barTop) + logoH - barH;
                    offset = Math.max(-maxShift, Math.min(maxShift, offset));
                    topPad = Math.max(0, offset);
                    bottomPad = Math.max(0, -offset);
                }
            }
            return logoRight;
        }
        if (isSmartLineActive(smartLine)) {
            barMode = MODE_SMART_LINE;
            barBounds.set(0, 0, smartLine.getWidth(), smartLine.getHeight());
            inputView.offsetDescendantRectToMyCoords(smartLine, barBounds);
            topOffset = Math.max(0, barBounds.top);
            rowHeight = Math.max(dp(26), barBounds.height());
            leftInset = Math.max(0, barBounds.left);
            rightInset = Math.max(0, inputView.getWidth() - barBounds.right);
            leftPad = dp(12);
            rightPad = dp(12);
            return 0;
        }
        barMode = MODE_NONE;
        topOffset = 0;
        rowHeight = dp(42);
        leftInset = dp(62);
        rightInset = 0;
        leftPad = dp(4);
        rightPad = dp(12);
        return 0;
    }

    private int computeTargetCardHeight() {
        if (barMode == MODE_SMART_LINE) {
            return Math.min(dp(28), Math.max(dp(24), rowHeight - dp(3)));
        }
        if (barMode == MODE_CAND_NEXT) {
            return Math.min(dp(34), Math.max(dp(30), rowHeight - dp(6)));
        }
        return Math.min(dp(34), Math.max(dp(28), rowHeight - dp(10)));
    }

    private void syncSlotDimensions(int targetHeight) {
        if (targetHeight <= 0) {
            return;
        }
        if (targetHeight != heightPx) {
            heightPx = targetHeight;
            for (GradientDrawable shape : slotShapes) {
                shape.setCornerRadius(targetHeight / 2f);
            }
        }
        if (getChildCount() >= 2
                && getChildAt(0) instanceof ViewGroup scroller && scroller.getChildCount() > 0
                && scroller.getChildAt(0) instanceof ViewGroup cards
                && getChildAt(1) instanceof ViewGroup pinned) {
            boolean hasPinned = pinned.getChildCount() > 0;
            int availW = availableWidth(hasPinned, dp(8));
            boolean singleCard = cards.getChildCount() == 1;
            for (int i = 0; i < cards.getChildCount(); i++) {
                View slot = cards.getChildAt(i);
                ViewGroup.LayoutParams lp = slot.getLayoutParams();
                if (lp == null) {
                    continue;
                }
                int desiredW = lp.width;
                if (singleCard && availW > 0 && slot instanceof ViewGroup slotGroup && slotGroup.getChildCount() > 0) {
                    View card = slotGroup.getChildAt(0);
                    int cardW = card.getLayoutParams() != null ? card.getLayoutParams().width : 0;
                    if (cardW > 0) {
                        desiredW = Math.min(cardW, availW);
                    }
                }
                if (lp.height != targetHeight || lp.width != desiredW) {
                    lp.height = targetHeight;
                    lp.width = desiredW;
                    slot.setLayoutParams(lp);
                }
            }
            for (int i = 0; i < pinned.getChildCount(); i++) {
                View slot = pinned.getChildAt(i);
                ViewGroup.LayoutParams lp = slot.getLayoutParams();
                if (lp != null && (lp.width != targetHeight || lp.height != targetHeight)) {
                    lp.width = targetHeight;
                    lp.height = targetHeight;
                    slot.setLayoutParams(lp);
                }
            }
        }
    }

    private void applyModePadding() {
        setPadding(leftPad, topPad, rightPad, bottomPad);
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

    private boolean canShowSuggestions(ViewGroup candNext, ViewGroup smartLine, View keyGrid, Object topBar) {
        if (!inputView.isShown() || inputView.getWidth() < dp(240) || inputView.getHeight() < dp(120)) {
            return false;
        }
        if (isFloatingDecor() || hasDisallowedAncestor() || hasOverlayPanelOpen(candNext) || keyGrid == null) {
            return false;
        }
        if (isCandNextActive(candNext)) {
            return isCandNextIdle(candNext) && !isSmartLineComposing(smartLine)
                    && !(isClassicToolbarPresent(topBar) && hasClassicCandidateVisible(topBar));
        }
        if (isClassicToolbarPresent(topBar)) {
            return !hasClassicCandidateVisible(topBar)
                    && gridBoolean(gridIsShownMethod, findGrid(topBar, CLASSIC_LOGO_GRID_ID))
                    && !isSmartLineComposing(smartLine);
        }
        return isSmartLineActive(smartLine) && !isSmartLineComposing(smartLine);
    }

    private boolean isCandNextIdle(ViewGroup candNext) {
        View cardContainer = findById(candNext, cardContainerId);
        if (cardContainer == null || cardContainer.getVisibility() != VISIBLE) {
            return false;
        }
        if (!isCandNextStateIdle(candNext, cardContainer)) {
            return false;
        }
        View expandBtn = findById(candNext, expandCandidateBtnId);
        if (expandBtn != null && expandBtn.getVisibility() == VISIBLE) {
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

    private boolean isCandNextStateIdle(ViewGroup candNext, View cardContainer) {
        if (!candStateUnavailable && candStateLiveData == null) {
            try {
                ClassLoader loader = service.getClassLoader();
                Class<?> figi = Class.forName("com.iflytek.figi.FIGI", false, loader);
                Object bundleCtx = figi.getMethod("getBundleContext").invoke(null);
                if (bundleCtx != null) {
                    Method getService = bundleCtx.getClass().getMethod("getServiceSync", String.class);
                    getService.setAccessible(true);
                    Object candService = getService.invoke(bundleCtx, CAND_NEXT_SERVICE);
                    if (candService != null) {
                        Class<?> candApi = Class.forName(CAND_NEXT_SERVICE, false, loader);
                        Method getCenter = candApi.getMethod("getCandidateStateCenter");
                        Object center = getCenter.invoke(candService);
                        if (center != null) {
                            Class<?> centerApi = Class.forName(CAND_STATE_CENTER_CLASS, false, loader);
                            Method getState = centerApi.getMethod("getCandidateState");
                            Object liveData = getState.invoke(center);
                            if (liveData != null) {
                                Method getVal = liveData.getClass().getMethod("getValue");
                                getVal.setAccessible(true);
                                liveDataGetValueMethod = getVal;
                                candStateLiveData = liveData;
                            }
                        }
                    }
                }
            } catch (ReflectiveOperationException | RuntimeException unavailable) {
                candStateUnavailable = true;
            }
        }
        if (candStateLiveData != null && liveDataGetValueMethod != null) {
            try {
                Object state = liveDataGetValueMethod.invoke(candStateLiveData);
                if (state instanceof Enum<?> e) {
                    String name = e.name();
                    return "IDLE".equals(name) || "BEFORE_INPUT".equals(name);
                }
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                candStateLiveData = null;
                candStateUnavailable = true;
            }
        }
        View mainAgent = findById(candNext, mainAgentId);
        return (mainAgent != null && mainAgent.getVisibility() == VISIBLE && mainAgent.getWidth() > 0)
                || findLeadingQuickFunItem(cardContainer) != null;
    }

    private View findLeadingQuickFunItem(View cardContainer) {
        if (!(findById(cardContainer, cardContainerRvId) instanceof ViewGroup rv) || rv.getChildCount() < 2) {
            return null;
        }
        View first = rv.getChildAt(0);
        if (first == null || first.getVisibility() != VISIBLE || first.getWidth() <= 0
                || first.getLeft() < -dp(8) || first.getLeft() > dp(72)) {
            return null;
        }
        return (findById(first, quickFunIconBgId) != null || first.getWidth() <= dp(96)) ? first : null;
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
                    if (child.getId() != SMART_LINE_OWL_ID && child.getVisibility() == VISIBLE
                            && child.getWidth() > 0 && child instanceof ViewGroup comp
                            && hasVisibleMeasuredChild(comp)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static boolean hasVisibleMeasuredChild(ViewGroup group) {
        for (int i = 0; i < group.getChildCount(); i++) {
            View child = group.getChildAt(i);
            if (child.getVisibility() == VISIBLE && child.getWidth() > 0 && child.getHeight() > 0) {
                return true;
            }
        }
        return false;
    }

    private boolean hasOverlayPanelOpen(View candNext) {
        Boolean serviceShowing = queryBizFragmentShowing();
        if (serviceShowing != null) {
            return serviceShowing;
        }
        return findById(inputView, bizContainerId) instanceof ViewGroup biz
                && biz.getVisibility() == VISIBLE && biz.getWidth() > 0 && biz.getHeight() > 0
                && (candNext == null || !isDescendantOf(candNext, biz))
                && hasShownOverlayChild(biz);
    }

    private Boolean queryBizFragmentShowing() {
        if (!fragShowUnavailable && fragShowService == null) {
            try {
                ClassLoader loader = service.getClassLoader();
                Class<?> figi = Class.forName("com.iflytek.figi.FIGI", false, loader);
                Object bundleCtx = figi.getMethod("getBundleContext").invoke(null);
                if (bundleCtx != null) {
                    Method getService = bundleCtx.getClass().getMethod("getServiceSync", String.class);
                    getService.setAccessible(true);
                    Object svc = getService.invoke(bundleCtx, FRAGMENT_SHOW_SERVICE);
                    if (svc != null) {
                        Class<?> api = Class.forName(FRAGMENT_SHOW_SERVICE, false, loader);
                        Method m = api.getMethod("isFragmentShowing");
                        m.setAccessible(true);
                        isFragShowingMethod = m;
                        fragShowService = svc;
                    }
                }
            } catch (ReflectiveOperationException | RuntimeException unavailable) {
                fragShowUnavailable = true;
            }
        }
        if (fragShowService != null && isFragShowingMethod != null) {
            try {
                return Boolean.TRUE.equals(isFragShowingMethod.invoke(fragShowService));
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                fragShowService = null;
                fragShowUnavailable = true;
            }
        }
        return null;
    }

    private boolean hasShownOverlayChild(ViewGroup group) {
        for (int i = 0; i < group.getChildCount(); i++) {
            View child = group.getChildAt(i);
            if (child.getVisibility() == VISIBLE && child.getWidth() >= dp(120) && child.getHeight() >= dp(120)
                    && (!(child instanceof ViewGroup childGroup) || hasVisibleMeasuredChild(childGroup))) {
                return true;
            }
        }
        return false;
    }

    private static boolean isDescendantOf(View view, ViewGroup ancestor) {
        for (ViewParent p = view.getParent(); p != null; p = p.getParent()) {
            if (p == ancestor) {
                return true;
            }
        }
        return false;
    }

    private View findShownKeyGrid() {
        View host = findById(inputView, inputContainerId);
        if (host instanceof ViewGroup group) {
            return group.isShown() && group.getHeight() > 0 ? findShownViewByClass(group, KEY_GRID_CLASS) : null;
        }
        return findShownViewByClass(inputView, KEY_GRID_CLASS);
    }

    private static View findShownViewByClass(View root, String className) {
        if (className.equals(root.getClass().getName())) {
            return root.isShown() && root.getHeight() > 0 ? root : null;
        }
        if (root instanceof ViewGroup group) {
            for (int i = 0; i < group.getChildCount(); i++) {
                View found = findShownViewByClass(group.getChildAt(i), className);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private Object resolveClassicTopBar(View keyGrid) {
        if (keyGrid == null) {
            return null;
        }
        try {
            if (contentGridMethod == null) {
                contentGridMethod = findMethod(keyGrid.getClass(), "getContentGrid");
            }
            Object hk4 = contentGridMethod.invoke(keyGrid);
            if (hk4 == null) {
                return null;
            }
            if (hk4ToolbarHostMethod == null) {
                hk4ToolbarHostMethod = findMethod(hk4.getClass(), "z");
            }
            Object rr8 = hk4ToolbarHostMethod.invoke(hk4);
            if (rr8 == null) {
                return null;
            }
            if (rr8TopBarMethod == null) {
                rr8TopBarMethod = findOptionalMethod(rr8.getClass(), "u");
            }
            Object topBar = rr8TopBarMethod != null ? rr8TopBarMethod.invoke(rr8) : null;
            if (topBar == null) {
                if (rr8MainAreaMethod == null) {
                    rr8MainAreaMethod = findOptionalMethod(rr8.getClass(), "z");
                }
                Object c28 = rr8MainAreaMethod != null ? rr8MainAreaMethod.invoke(rr8) : null;
                if (c28 != null) {
                    if (c28TopBarMethod == null) {
                        c28TopBarMethod = findOptionalMethod(c28.getClass(), "p");
                    }
                    topBar = c28TopBarMethod != null ? c28TopBarMethod.invoke(c28) : null;
                }
            }
            if (topBar != null && gridIsShownMethod == null) {
                Class<?> barClass = topBar.getClass();
                gridFindByIdMethod = findMethod(barClass, "findViewById", int.class);
                gridIsShownMethod = findMethod(barClass, "isShown");
                gridTopMethod = findMethod(barClass, "getTop");
                gridRightMethod = findMethod(barClass, "getRight");
                gridHeightMethod = findMethod(barClass, "getHeight");
                x18CandViewsField = findOptionalField(barClass, "g");
                x18CandStripMethod = findOptionalMethod(barClass, "E");
            }
            return topBar;
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return null;
        }
    }

    private boolean isClassicToolbarPresent(Object topBar) {
        return topBar != null && gridBoolean(gridIsShownMethod, topBar)
                && gridInt(gridTopMethod, topBar) >= -dp(4)
                && gridInt(gridHeightMethod, topBar) >= dp(30)
                && (findGrid(topBar, CLASSIC_LOGO_GRID_ID) != null || hasClassicCandidateVisible(topBar));
    }

    private boolean hasClassicCandidateVisible(Object topBar) {
        if (topBar == null) {
            return false;
        }
        try {
            if (x18CandViewsField != null && x18CandViewsField.get(topBar) instanceof List<?> cands) {
                for (Object cand : cands) {
                    if (isCandidateGridActive(cand)) {
                        return true;
                    }
                }
                return false;
            }
            return x18CandStripMethod != null && isCandidateGridActive(x18CandStripMethod.invoke(topBar));
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return false;
        }
    }

    private boolean isCandidateGridActive(Object cand) {
        if (cand == null || !gridBoolean(gridIsShownMethod, cand)) {
            return false;
        }
        if (candCountMethod == null) {
            candCountMethod = findOptionalMethod(cand.getClass(), "getVisibleCandidateCount");
        }
        return candCountMethod == null || gridInt(candCountMethod, cand) > 0;
    }

    private Object findGrid(Object group, int gridId) {
        if (group == null || gridFindByIdMethod == null) {
            return null;
        }
        try {
            return gridFindByIdMethod.invoke(group, gridId);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return null;
        }
    }

    private static int gridInt(Method method, Object target) {
        if (method == null || target == null) {
            return 0;
        }
        try {
            return (int) method.invoke(target);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return 0;
        }
    }

    private static boolean gridBoolean(Method method, Object target) {
        if (method == null || target == null) {
            return false;
        }
        try {
            return Boolean.TRUE.equals(method.invoke(target));
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return false;
        }
    }

    private void updateClassicClipState(View keyGrid, Object topBar, boolean visible, int clipRight) {
        boolean active = visible && topBar != null && clipRight > 0;
        boolean changed = (classicInlineVisible != active)
                || (active && (activeClassicTopBar != topBar || classicClipRightPx != clipRight));
        activeClassicTopBar = active ? topBar : null;
        classicClipRightPx = active ? clipRight : 0;
        classicInlineVisible = active;
        if (changed && keyGrid != null) {
            keyGrid.invalidate();
        }
    }

    private void clearClassicClipState(View keyGrid) {
        boolean wasVisible = classicInlineVisible;
        classicInlineVisible = false;
        activeClassicTopBar = null;
        classicClipRightPx = 0;
        if (wasVisible && keyGrid != null) {
            keyGrid.invalidate();
        }
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

    private static Field findOptionalField(Class<?> start, String name) {
        for (Class<?> c = start; c != null && c != Object.class; c = c.getSuperclass()) {
            try {
                Field f = c.getDeclaredField(name);
                f.setAccessible(true);
                return f;
            } catch (NoSuchFieldException ignored) {
                // Keep searching superclasses.
            }
        }
        return null;
    }

    private static Method findOptionalMethod(Class<?> start, String name, Class<?>... parameterTypes) {
        try {
            return findMethod(start, name, parameterTypes);
        } catch (NoSuchMethodException ignored) {
            return null;
        }
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

    private void fadeCandNextIdleViews(ViewGroup candNext) {
        View mainAgent = findById(candNext, mainAgentId);
        View cardContainer = findById(candNext, cardContainerId);
        View hideEntrance = findById(candNext, hideEntranceId);
        if (mainAgent != null && mainAgent.getVisibility() == VISIBLE && mainAgent.getWidth() > 0) {
            unfadeView(mainAgent);
            fadeView(cardContainer);
            fadeView(hideEntrance);
            return;
        }
        View firstQuickIcon = findLeadingQuickFunItem(cardContainer);
        if (firstQuickIcon != null && firstQuickIcon.getParent() instanceof ViewGroup rv) {
            unfadeView(cardContainer);
            unfadeView(rv);
            unfadeView(firstQuickIcon);
            fadeView(findById(cardContainer, cardContainerBgId));
            for (int i = 1; i < rv.getChildCount(); i++) {
                fadeView(rv.getChildAt(i));
            }
            fadeView(hideEntrance);
            return;
        }
        fadeView(cardContainer);
        fadeView(hideEntrance);
    }

    private void fadeOverlappingOwl(ViewGroup smartLine) {
        if (smartLine == null || !smartLine.isShown()) {
            return;
        }
        View owl = smartLine.findViewById(SMART_LINE_OWL_ID);
        if (owl == null) {
            return;
        }
        tmpBounds.set(0, 0, smartLine.getWidth(), smartLine.getHeight());
        inputView.offsetDescendantRectToMyCoords(smartLine, tmpBounds);
        if (tmpBounds.bottom > topOffset + dp(4) && tmpBounds.top < topOffset + rowHeight - dp(4)) {
            fadeView(owl);
        } else {
            unfadeView(owl);
        }
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

    private void unfadeView(View target) {
        if (target == null) {
            return;
        }
        Float saved = savedAlphas.remove(target);
        if (saved != null) {
            target.setAlpha(saved);
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

    private static View findById(View root, int viewId) {
        return viewId == 0 || root == null ? null : root.findViewById(viewId);
    }

    private static void applySlotStyle(GradientDrawable shape, boolean dark) {
        shape.setColor(dark ? 0x2bffffff : 0xffffffff);
        shape.setStroke(0, Color.TRANSPARENT);
    }

    private int id(String name) {
        int resId = service.getResources().getIdentifier(name, "id", KEYBOARD_PACKAGE);
        return resId != 0 ? resId : getResources().getIdentifier(name, "id", KEYBOARD_PACKAGE);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
