package com.mugua.keyfill

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.OutputTransformation
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import io.github.libxposed.service.HookedTarget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.ColorPalette
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.SnackbarHost
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.rememberTopAppBarState
import top.yukonga.miuix.kmp.blur.Backdrop
import top.yukonga.miuix.kmp.blur.BlendColorEntry
import top.yukonga.miuix.kmp.blur.BlurBlendMode
import top.yukonga.miuix.kmp.blur.BlurDefaults
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.blur.textureBlur
import top.yukonga.miuix.kmp.glass.GlassDialog
import top.yukonga.miuix.kmp.glass.GlassDialogButton
import top.yukonga.miuix.kmp.glass.GlassDialogButtonRole
import top.yukonga.miuix.kmp.glass.GlassDropdownPreference
import top.yukonga.miuix.kmp.glass.GlassIconButton
import top.yukonga.miuix.kmp.glass.GlassNavigationBar
import top.yukonga.miuix.kmp.glass.GlassNavigationItem
import top.yukonga.miuix.kmp.glass.GlassTextField
import top.yukonga.miuix.kmp.glass.GlassTopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.ArrowRight
import top.yukonga.miuix.kmp.icon.extended.Background
import top.yukonga.miuix.kmp.icon.extended.Clear
import top.yukonga.miuix.kmp.icon.extended.Contacts
import top.yukonga.miuix.kmp.icon.extended.Copy
import top.yukonga.miuix.kmp.icon.extended.Hide
import top.yukonga.miuix.kmp.icon.extended.Home
import top.yukonga.miuix.kmp.icon.extended.Info
import top.yukonga.miuix.kmp.icon.extended.Lock
import top.yukonga.miuix.kmp.icon.extended.Refresh
import top.yukonga.miuix.kmp.icon.extended.Settings
import top.yukonga.miuix.kmp.icon.extended.Show
import top.yukonga.miuix.kmp.icon.extended.Tasks
import top.yukonga.miuix.kmp.icon.extended.Theme
import top.yukonga.miuix.kmp.icon.extended.Tune
import top.yukonga.miuix.kmp.icon.extended.Unlock
import top.yukonga.miuix.kmp.material.GlassEdgeStrokes
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController
import top.yukonga.miuix.kmp.theme.darkColorScheme
import top.yukonga.miuix.kmp.theme.lightColorScheme
import top.yukonga.miuix.kmp.utils.PagerGestureNestedScrollConnection
import top.yukonga.miuix.kmp.utils.PagerInterceptionMode
import top.yukonga.miuix.kmp.utils.PagerNavigationSpringSpec
import top.yukonga.miuix.kmp.utils.PressFeedbackType
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.pagerGestureOverride
import top.yukonga.miuix.kmp.utils.scrollEndHaptic
import top.yukonga.miuix.kmp.utils.springAnimateToPage

private val CardMargin = PaddingValues(0.dp)
private val RowMargin = PaddingValues(horizontal = 16.dp, vertical = 15.dp)
private val SectionTitleMargin = PaddingValues(horizontal = 16.dp, vertical = 10.dp)

private const val COOLAPK_URL = "https://www.coolapk.com/u/9898174"
private const val GITHUB_URL = "https://github.com/zrmugua/keyfill"
private const val ISSUES_URL = "https://github.com/zrmugua/keyfill/issues"
private const val LICENSE_URL = "https://github.com/zrmugua/keyfill/blob/main/LICENSE"

private enum class HealthState {
    WORKING,
    RELOAD_NEEDED,
    SETUP_NEEDED,
    INACTIVE,
}

@Stable
private class PagerTabState(
    val pagerState: PagerState,
    private val scope: CoroutineScope,
) {
    var selectedPage by mutableIntStateOf(pagerState.currentPage)
        private set
    private var navJob: Job? = null
    private var isNavigating = false

    fun animateToPage(target: Int) {
        if (target == selectedPage) return
        navJob?.cancel()
        selectedPage = target
        isNavigating = true
        navJob = scope.launch {
            val myJob = coroutineContext.job
            try {
                pagerState.springAnimateToPage(target)
            } finally {
                if (navJob == myJob) {
                    isNavigating = false
                    if (pagerState.currentPage != target) {
                        selectedPage = pagerState.currentPage
                    }
                }
            }
        }
    }

    fun syncPage() {
        if (!isNavigating && selectedPage != pagerState.currentPage) {
            selectedPage = pagerState.currentPage
        }
    }
}

@Composable
private fun PagerBackHandler(tabState: PagerTabState) {
    BackHandler(enabled = tabState.selectedPage != 0) {
        tabState.animateToPage(0)
    }
}

@Composable
private fun BottomNavigationPill(
    tabs: List<GlassNavigationItem>,
    tabState: PagerTabState,
    backdrop: Backdrop,
    dark: Boolean,
    accent: Color,
) {
    val barInsets = WindowInsets.systemBars.union(WindowInsets.displayCutout)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(
                barInsets.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal),
            )
            .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        GlassNavigationBar(
            items = tabs,
            selectedIndex = tabState.selectedPage,
            onSelect = { tabState.animateToPage(it) },
            backdrop = backdrop,
            defaultWindowInsetsPadding = false,
            selectedColor = accent,
            height = 60.dp,
            stroke = GlassEdgeStrokes.forTheme(
                dark,
                GlassEdgeStrokes.SmallLight,
                GlassEdgeStrokes.SmallDark,
            ),
            modifier = Modifier.widthIn(max = 304.dp).fillMaxWidth(),
        )
    }
}

@Composable
internal fun ManagerApp(
    status: DeviceStatus,
    xposed: XposedStatus,
    metadata: AppMetadata,
    preferences: UiPreferences,
    actions: ManagerActions,
) {
    val systemDark = isSystemInDarkTheme()
    val dark = when (preferences.themeMode) {
        1 -> false
        2 -> true
        else -> systemDark
    }
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = !dark
            controller.isAppearanceLightNavigationBars = !dark
        }
    }

    val schemeMode = when {
        preferences.monetEnabled && preferences.themeMode == 1 -> ColorSchemeMode.MonetLight
        preferences.monetEnabled && preferences.themeMode == 2 -> ColorSchemeMode.MonetDark
        preferences.monetEnabled -> ColorSchemeMode.MonetSystem
        preferences.themeMode == 1 -> ColorSchemeMode.Light
        preferences.themeMode == 2 -> ColorSchemeMode.Dark
        else -> ColorSchemeMode.System
    }
    val keyColorObj = preferences.keyColor
        .takeIf { preferences.monetEnabled && it != 0 }
        ?.let { Color(it or -0x1000000) }
    val themeController = remember(schemeMode, dark, keyColorObj) {
        ThemeController(
            colorSchemeMode = schemeMode,
            lightColors = lightColorScheme(primary = Color(0xFF1B5EF2)),
            darkColors = darkColorScheme(primary = Color(0xFF6DA8FF)),
            keyColor = keyColorObj,
            isDark = dark,
        )
    }

    MiuixTheme(controller = themeController) {
        val colors = MiuixTheme.colorScheme
        val pagerState = rememberPagerState(initialPage = 0, pageCount = { 3 })
        val scope = rememberCoroutineScope()
        val tabState = remember(pagerState, scope) { PagerTabState(pagerState, scope) }
        LaunchedEffect(tabState) {
            snapshotFlow { tabState.pagerState.currentPage }
                .collect { tabState.syncPage() }
        }
        PagerBackHandler(tabState)

        val snackbarHostState = remember { SnackbarHostState() }
        var showTestDialog by rememberSaveable { mutableStateOf(false) }
        val pageState = rememberSaveableStateHolder()
        val backdrop = rememberLayerBackdrop()

        val homeLabel = stringResource(R.string.manager_home)
        val settingsLabel = stringResource(R.string.manager_settings)
        val aboutLabel = stringResource(R.string.manager_about)
        val refreshLabel = stringResource(R.string.action_refresh)
        val refreshedMessage = stringResource(R.string.snackbar_refreshed)
        val keyboardVector = ImageVector.vectorResource(R.drawable.ic_keyboard)
        val tabs = remember(homeLabel, settingsLabel, aboutLabel) {
            listOf(
                GlassNavigationItem(icon = MiuixIcons.Medium.Home, label = homeLabel),
                GlassNavigationItem(icon = MiuixIcons.Medium.Settings, label = settingsLabel),
                GlassNavigationItem(icon = MiuixIcons.Medium.Info, label = aboutLabel),
            )
        }
        val showMessage: (String) -> Unit = remember(scope, snackbarHostState) {
            { message -> scope.launch { snackbarHostState.showSnackbar(message) } }
        }
        val flingBehavior = PagerDefaults.flingBehavior(
            state = pagerState,
            snapAnimationSpec = PagerNavigationSpringSpec,
        )

        val safeSystemInsets = WindowInsets.systemBars.union(WindowInsets.displayCutout)
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            contentWindowInsets = safeSystemInsets,
            snackbarHost = { SnackbarHost(state = snackbarHostState) },
            bottomBar = {
                BottomNavigationPill(
                    tabs = tabs,
                    tabState = tabState,
                    backdrop = backdrop,
                    dark = dark,
                    accent = colors.primary,
                )
            },
        ) { outerInsets ->
            val layoutDirection = LocalLayoutDirection.current
            val isAboutActive by remember(pagerState) {
                derivedStateOf { pagerState.settledPage == 2 && !pagerState.isScrollInProgress }
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .layerBackdrop(backdrop)
                    .background(colors.surface),
            ) {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier
                        .fillMaxSize()
                        .pagerGestureOverride(
                            pagerState = pagerState,
                            flingBehavior = flingBehavior,
                            mode = PagerInterceptionMode.CrossAxis,
                        ),
                    userScrollEnabled = false,
                    pageNestedScrollConnection = PagerGestureNestedScrollConnection,
                    beyondViewportPageCount = 2,
                    flingBehavior = flingBehavior,
                ) { pageIndex ->
                    pageState.SaveableStateProvider(pageIndex) {
                        if (pageIndex == 2) {
                            HyperCeilerAboutPage(
                                outerInsets = outerInsets,
                                metadata = metadata,
                                xposed = xposed,
                                dark = dark,
                                playing = isAboutActive,
                                actions = actions,
                                showMessage = showMessage,
                            )
                        } else {
                            val pageListState = rememberLazyListState()
                            val pageBackdrop = rememberLayerBackdrop()
                            val scrollBehavior = MiuixScrollBehavior(rememberTopAppBarState())
                            val pageTitle = if (pageIndex == 0) stringResource(R.string.module_name) else settingsLabel
                            Scaffold(
                                modifier = Modifier.fillMaxSize(),
                                containerColor = Color.Transparent,
                                popupHost = {},
                                topBar = {
                                    GlassTopAppBar(
                                        title = pageTitle,
                                        isContentScrolled = pageListState.canScrollBackward,
                                        expandable = true,
                                        progressiveBlur = true,
                                        backdrop = pageBackdrop,
                                        scrollBehavior = scrollBehavior,
                                        actions = {
                                            if (pageIndex == 0) {
                                                GlassIconButton(
                                                    onClick = {
                                                        actions.refresh()
                                                        showMessage(refreshedMessage)
                                                    },
                                                ) {
                                                    Icon(
                                                        imageVector = MiuixIcons.Refresh,
                                                        contentDescription = refreshLabel,
                                                        tint = colors.onSurface,
                                                    )
                                                }
                                            }
                                        },
                                    )
                                },
                            ) { pageInsets ->
                                val contentPadding = PaddingValues(
                                    start = outerInsets.calculateStartPadding(layoutDirection) + 12.dp,
                                    end = outerInsets.calculateEndPadding(layoutDirection) + 12.dp,
                                    top = pageInsets.calculateTopPadding() + 8.dp,
                                    bottom = outerInsets.calculateBottomPadding() + 16.dp,
                                )
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .layerBackdrop(pageBackdrop)
                                        .background(colors.surface),
                                ) {
                                    LazyColumn(
                                        state = pageListState,
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .scrollEndHaptic()
                                            .overScrollVertical()
                                            .nestedScroll(scrollBehavior.nestedScrollConnection),
                                        contentPadding = contentPadding,
                                        verticalArrangement = Arrangement.spacedBy(12.dp),
                                    ) {
                                        if (pageIndex == 0) {
                                            item {
                                                StatusHeroCard(
                                                    status = status,
                                                    xposed = xposed,
                                                    metadata = metadata,
                                                    dark = dark,
                                                    actions = actions,
                                                    showMessage = showMessage,
                                                )
                                            }
                                            item {
                                                QuickServicesSection(
                                                    status = status,
                                                    xposed = xposed,
                                                    keyboardIcon = keyboardVector,
                                                    actions = actions,
                                                    showMessage = showMessage,
                                                )
                                            }
                                            if (status.hyperPasskey.supported) {
                                                item {
                                                    HyperPasskeySection(
                                                        passkey = status.hyperPasskey,
                                                        xposed = xposed,
                                                        actions = actions,
                                                        showMessage = showMessage,
                                                    )
                                                }
                                            }
                                            item {
                                                SupportedKeyboardsSection(
                                                    status = status,
                                                    xposed = xposed,
                                                    keyboardIcon = keyboardVector,
                                                    actions = actions,
                                                    showMessage = showMessage,
                                                )
                                            }
                                        } else {
                                            item {
                                                SettingsSection(
                                                    status = status,
                                                    xposed = xposed,
                                                    preferences = preferences,
                                                    backdrop = backdrop,
                                                    keyboardIcon = keyboardVector,
                                                    actions = actions,
                                                    onOpenTest = { showTestDialog = true },
                                                    showMessage = showMessage,
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            AutofillTestDialog(
                show = showTestDialog,
                backdrop = backdrop,
                onDismiss = { showTestDialog = false },
            )
        }
    }
}

private fun missingScopePackages(status: DeviceStatus, xposed: XposedStatus): List<String> {
    if (!xposed.active) return emptyList()
    return buildList {
        if (!xposed.hasSystemScope) {
            add("android")
        }
        val targetKeyboards = status.keyboards.filter {
            it.installed || it.app.packageName == status.defaultKeyboard?.packageName
        }
        val candidates = targetKeyboards.ifEmpty { status.keyboards }
        for (keyboard in candidates) {
            if (!xposed.isScoped(keyboard.app.packageName)) {
                add(keyboard.app.packageName)
            }
        }
    }
}

@Composable
private fun StatusHeroCard(
    status: DeviceStatus,
    xposed: XposedStatus,
    metadata: AppMetadata,
    dark: Boolean,
    actions: ManagerActions,
    showMessage: (String) -> Unit,
) {
    val provider = status.autofillProvider
    val keyboard = status.defaultKeyboard
    val currentKeyboardUnscoped = xposed.active &&
        keyboard != null &&
        status.hasSupportedKeyboard &&
        !xposed.isScoped(keyboard.packageName)

    val health = when {
        !xposed.active -> HealthState.INACTIVE
        !xposed.hasSystemScope || currentKeyboardUnscoped || provider == null || !status.hasSupportedKeyboard ->
            HealthState.SETUP_NEEDED
        xposed.hasStaleTarget -> HealthState.RELOAD_NEEDED
        else -> HealthState.WORKING
    }

    val title = when (health) {
        HealthState.WORKING -> stringResource(R.string.status_working)
        HealthState.RELOAD_NEEDED -> stringResource(R.string.status_reload_needed)
        HealthState.SETUP_NEEDED -> stringResource(R.string.status_setup)
        HealthState.INACTIVE -> stringResource(R.string.status_inactive)
    }

    val summary = when {
        health == HealthState.INACTIVE -> stringResource(R.string.status_inactive_summary)
        !xposed.hasSystemScope -> stringResource(R.string.status_setup_system_scope)
        currentKeyboardUnscoped -> stringResource(R.string.status_setup_keyboard_scope, checkNotNull(keyboard).label)
        provider == null && !status.hasSupportedKeyboard -> stringResource(R.string.status_setup_all)
        provider == null -> stringResource(R.string.status_setup_provider)
        !status.hasSupportedKeyboard -> stringResource(R.string.status_setup_ime)
        health == HealthState.RELOAD_NEEDED -> stringResource(R.string.status_reload_summary)
        else -> stringResource(
            R.string.status_working_summary,
            checkNotNull(keyboard).label,
            checkNotNull(provider).label,
        )
    }

    val meta = if (xposed.active) {
        stringResource(R.string.status_framework_meta, xposed.frameworkName, xposed.frameworkVersion, xposed.apiVersion)
    } else {
        stringResource(R.string.status_version_meta, metadata.versionName, metadata.versionCode)
    }

    val primary = MiuixTheme.colorScheme.primary
    val container = when (health) {
        HealthState.WORKING -> primary.copy(alpha = if (dark) 0.22f else 0.13f)
        HealthState.INACTIVE -> if (dark) Color(0xFF352222) else Color(0xFFFCE8E6)
        HealthState.SETUP_NEEDED, HealthState.RELOAD_NEEDED -> if (dark) Color(0xFF362B1B) else Color(0xFFFFEFD7)
    }
    val markColor = when (health) {
        HealthState.WORKING -> primary
        HealthState.INACTIVE -> if (dark) Color(0xFFF09893) else Color(0xFF9C2B27)
        HealthState.SETUP_NEEDED, HealthState.RELOAD_NEEDED -> if (dark) Color(0xFFF1BE78) else Color(0xFF855210)
    }

    val scopeApprovedMsg = stringResource(R.string.snackbar_scope_approved)
    val scopeFailedPattern = stringResource(R.string.snackbar_scope_failed)
    val refreshedMsg = stringResource(R.string.snackbar_refreshed)
    val openFailedMsg = stringResource(R.string.snackbar_open_failed)
    val missingScopes = remember(status, xposed) { missingScopePackages(status, xposed) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 20.dp,
        colors = CardDefaults.defaultColors(color = container),
        insideMargin = CardMargin,
        pressFeedbackType = PressFeedbackType.Sink,
        showIndication = true,
        onClick = {
            when {
                !xposed.active || health == HealthState.WORKING -> {
                    actions.refresh()
                    showMessage(refreshedMsg)
                }
                health == HealthState.RELOAD_NEEDED && keyboard != null -> {
                    if (!actions.openAppDetails(keyboard.packageName)) showMessage(openFailedMsg)
                }
                !xposed.hasSystemScope || currentKeyboardUnscoped -> {
                    actions.requestScope(missingScopes) { ok, reason ->
                        showMessage(
                            if (ok) scopeApprovedMsg else String.format(scopeFailedPattern, reason ?: "-"),
                        )
                    }
                }
                provider == null -> {
                    if (!actions.openAutofillSettings()) showMessage(openFailedMsg)
                }
                !status.hasSupportedKeyboard -> {
                    if (!actions.openInputMethodPicker()) showMessage(openFailedMsg)
                }
            }
        },
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 128.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            Canvas(Modifier.matchParentSize()) {
                val center = Offset(size.width - 26.dp.toPx(), size.height - 26.dp.toPx())
                val stroke = 7.dp.toPx()
                drawCircle(
                    color = markColor.copy(alpha = 0.62f),
                    radius = 40.dp.toPx(),
                    center = center,
                    style = Stroke(stroke),
                )
                val ink = markColor.copy(alpha = 0.74f)
                when (health) {
                    HealthState.WORKING -> {
                        val corner = center + Offset(-5.dp.toPx(), 9.dp.toPx())
                        drawLine(ink, center + Offset(-16.dp.toPx(), -2.dp.toPx()), corner, stroke, StrokeCap.Round)
                        drawLine(ink, corner, center + Offset(15.dp.toPx(), -12.dp.toPx()), stroke, StrokeCap.Round)
                    }
                    HealthState.INACTIVE -> {
                        val delta = 11.dp.toPx()
                        drawLine(ink, center + Offset(-delta, -delta), center + Offset(delta, delta), stroke, StrokeCap.Round)
                        drawLine(ink, center + Offset(-delta, delta), center + Offset(delta, -delta), stroke, StrokeCap.Round)
                    }
                    HealthState.SETUP_NEEDED, HealthState.RELOAD_NEEDED -> {
                        drawLine(ink, center - Offset(0f, 16.dp.toPx()), center + Offset(0f, 1.dp.toPx()), stroke, StrokeCap.Round)
                        drawCircle(ink, stroke / 2, center + Offset(0f, 13.dp.toPx()))
                    }
                }
            }
            Column(
                modifier = Modifier.padding(start = 18.dp, end = 86.dp, top = 18.dp, bottom = 18.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = title,
                    style = MiuixTheme.textStyles.headline1.copy(fontSize = 23.sp, fontWeight = FontWeight.Bold),
                    color = MiuixTheme.colorScheme.onSurface,
                )
                Text(
                    text = summary,
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.82f),
                )
                Text(
                    text = meta,
                    modifier = Modifier.padding(top = 4.dp),
                    style = MiuixTheme.textStyles.body2.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold),
                    color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.66f),
                )
            }
        }
    }
}

private data class IconBadgeStyle(
    val topColor: Color,
    val bottomColor: Color,
    val tint: Color = Color.White,
)

private val BadgeBlue = IconBadgeStyle(Color(0xFF4C8DFF), Color(0xFF1B5EF2))
private val BadgeEmerald = IconBadgeStyle(Color(0xFF34D399), Color(0xFF059669))
private val BadgeIndigo = IconBadgeStyle(Color(0xFF818CF8), Color(0xFF4F46E5))
private val BadgeSky = IconBadgeStyle(Color(0xFF38BDF8), Color(0xFF0284C7))
private val BadgeAmber = IconBadgeStyle(Color(0xFFFBBF24), Color(0xFFF97316))
private val BadgeViolet = IconBadgeStyle(Color(0xFFF472B6), Color(0xFF9333EA))
private val BadgeSlate = IconBadgeStyle(Color(0xFF94A3B8), Color(0xFF475569))

private fun allMissingScopePackages(status: DeviceStatus, xposed: XposedStatus): List<String> {
    if (!xposed.active) return emptyList()
    val result = LinkedHashSet<String>()
    result.addAll(missingScopePackages(status, xposed))
    if (status.hyperPasskey.supported) {
        if (!xposed.isScoped(HyperPasskeyHooks.SETTINGS_PACKAGE)) {
            result.add(HyperPasskeyHooks.SETTINGS_PACKAGE)
        }
        if (!xposed.isScoped(HyperPasskeyHooks.SECURITY_CENTER_PACKAGE)) {
            result.add(HyperPasskeyHooks.SECURITY_CENTER_PACKAGE)
        }
        if (status.hyperPasskey.scannerInstalled && !xposed.isScoped(HyperPasskeyHooks.SCANNER_PACKAGE)) {
            result.add(HyperPasskeyHooks.SCANNER_PACKAGE)
        }
    }
    return result.toList()
}

@Composable
private fun QuickServicesSection(
    status: DeviceStatus,
    xposed: XposedStatus,
    keyboardIcon: ImageVector,
    actions: ManagerActions,
    showMessage: (String) -> Unit,
) {
    val unset = stringResource(R.string.status_not_configured)
    val systemScopeLabel = stringResource(R.string.status_scope_system)
    val scopeApprovedMsg = stringResource(R.string.snackbar_scope_approved)
    val scopeFailedPattern = stringResource(R.string.snackbar_scope_failed)
    val refreshedMsg = stringResource(R.string.snackbar_refreshed)
    val openFailedMsg = stringResource(R.string.snackbar_open_failed)

    val missingScopes = remember(status, xposed) { missingScopePackages(status, xposed) }
    val missingLabels = remember(missingScopes, status, systemScopeLabel) {
        missingScopes.map { pkg ->
            if (pkg == "android") {
                systemScopeLabel
            } else {
                status.keyboards.firstOrNull { it.app.packageName == pkg }?.app?.label ?: pkg
            }
        }
    }
    val showScopeWarning = !xposed.active || missingScopes.isNotEmpty()
    val scopeSummary = when {
        !xposed.active -> stringResource(R.string.status_scope_inactive)
        xposed.scope.isEmpty() -> stringResource(R.string.status_scope_empty)
        else -> stringResource(R.string.status_scope_missing, missingLabels.joinToString("、"))
    }

    Column {
        SmallTitle(text = stringResource(R.string.section_environment), insideMargin = SectionTitleMargin)
        Card(modifier = Modifier.fillMaxWidth(), cornerRadius = 20.dp, insideMargin = CardMargin) {
            ManagerRow(
                title = stringResource(R.string.status_autofill),
                summary = status.autofillProvider?.label ?: unset,
                icon = MiuixIcons.Medium.Lock,
                badge = BadgeBlue,
                packageName = status.autofillProvider?.packageName,
                showChevron = true,
                onClick = {
                    if (!actions.openAutofillSettings()) {
                        showMessage(openFailedMsg)
                    }
                },
            )
            ManagerRow(
                title = stringResource(R.string.status_ime),
                summary = status.defaultKeyboard?.label ?: unset,
                icon = keyboardIcon,
                badge = BadgeSky,
                packageName = status.defaultKeyboard?.packageName,
                showChevron = true,
                onClick = {
                    if (!actions.openInputMethodPicker()) {
                        showMessage(openFailedMsg)
                    }
                },
            )
            if (showScopeWarning) {
                ManagerRow(
                    title = stringResource(R.string.status_scope),
                    summary = scopeSummary,
                    icon = MiuixIcons.Medium.Tune,
                    badge = BadgeIndigo,
                    showChevron = xposed.active,
                    onClick = if (!xposed.active) {
                        {
                            actions.refresh()
                            showMessage(refreshedMsg)
                        }
                    } else {
                        {
                            actions.requestScope(missingScopes) { ok, reason ->
                                showMessage(
                                    if (ok) scopeApprovedMsg else String.format(scopeFailedPattern, reason ?: "-"),
                                )
                            }
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun HyperPasskeySection(
    passkey: HyperPasskeyStatus,
    xposed: XposedStatus,
    actions: ManagerActions,
    showMessage: (String) -> Unit,
) {
    val openFailedMsg = stringResource(R.string.snackbar_open_failed)
    val scopeApprovedMsg = stringResource(R.string.snackbar_scope_approved)
    val scopeFailedPattern = stringResource(R.string.snackbar_scope_failed)
    val refreshedMsg = stringResource(R.string.snackbar_refreshed)
    val systemLabel = stringResource(R.string.status_scope_system)
    val settingsLabel = stringResource(R.string.passkey_scope_settings)
    val securityLabel = stringResource(R.string.passkey_scope_security)
    val scannerLabel = stringResource(R.string.passkey_scope_scanner)

    val requiredPackages = remember(passkey.scannerInstalled) {
        buildList {
            add("android")
            add(HyperPasskeyHooks.SETTINGS_PACKAGE)
            add(HyperPasskeyHooks.SECURITY_CENTER_PACKAGE)
            if (passkey.scannerInstalled) {
                add(HyperPasskeyHooks.SCANNER_PACKAGE)
            }
        }
    }
    val missingPackages = remember(xposed, requiredPackages) {
        if (!xposed.active) {
            emptyList()
        } else {
            requiredPackages.filter { pkg ->
                if (pkg == "android") !xposed.hasSystemScope else !xposed.isScoped(pkg)
            }
        }
    }
    val labelForPkg: (String) -> String = { pkg ->
        when (pkg) {
            "android" -> systemLabel
            HyperPasskeyHooks.SETTINGS_PACKAGE -> settingsLabel
            HyperPasskeyHooks.SECURITY_CENTER_PACKAGE -> securityLabel
            HyperPasskeyHooks.SCANNER_PACKAGE -> scannerLabel
            else -> pkg
        }
    }
    val missingLabels = remember(missingPackages, systemLabel, settingsLabel, securityLabel, scannerLabel) {
        missingPackages.map(labelForPkg)
    }
    val activeScopeLabels = remember(passkey.scannerInstalled, xposed, settingsLabel, securityLabel, scannerLabel) {
        buildList {
            if (xposed.isScoped(HyperPasskeyHooks.SETTINGS_PACKAGE)) add(settingsLabel)
            if (xposed.isScoped(HyperPasskeyHooks.SECURITY_CENTER_PACKAGE)) add(securityLabel)
            if (passkey.scannerInstalled && xposed.isScoped(HyperPasskeyHooks.SCANNER_PACKAGE)) add(scannerLabel)
        }
    }

    val providerSummary = when {
        passkey.providers.isEmpty() -> stringResource(R.string.passkey_provider_empty)
        passkey.providers.size <= 3 -> stringResource(
            R.string.passkey_provider_list,
            passkey.providers.joinToString("、") { it.label },
        )
        else -> stringResource(
            R.string.passkey_provider_many,
            passkey.providers.take(2).joinToString("、") { it.label },
            passkey.providers.size,
        )
    }

    val unlockSummary = when {
        !xposed.active -> stringResource(R.string.passkey_unlock_inactive)
        !passkey.gmsChooserReady -> stringResource(R.string.passkey_unlock_gms_missing)
        missingLabels.isNotEmpty() -> stringResource(R.string.passkey_unlock_missing_scope, missingLabels.joinToString("、"))
        xposed.hasStaleTarget -> stringResource(R.string.passkey_unlock_reload)
        else -> stringResource(R.string.passkey_unlock_ready, activeScopeLabels.joinToString("、"))
    }

    Column {
        SmallTitle(text = stringResource(R.string.section_hyper_passkey), insideMargin = SectionTitleMargin)
        Card(modifier = Modifier.fillMaxWidth(), cornerRadius = 20.dp, insideMargin = CardMargin) {
            ManagerRow(
                title = stringResource(R.string.passkey_unlock_title),
                summary = unlockSummary,
                icon = MiuixIcons.Medium.Unlock,
                badge = BadgeEmerald,
                showChevron = !xposed.active || !passkey.gmsChooserReady || missingPackages.isNotEmpty(),
                onClick = when {
                    !xposed.active -> {
                        {
                            actions.refresh()
                            showMessage(refreshedMsg)
                        }
                    }
                    !passkey.gmsChooserReady -> {
                        {
                            if (!actions.openCredentialSettings()) showMessage(openFailedMsg)
                        }
                    }
                    missingPackages.isNotEmpty() -> {
                        {
                            actions.requestScope(missingPackages) { ok, reason ->
                                showMessage(
                                    if (ok) scopeApprovedMsg else String.format(scopeFailedPattern, reason ?: "-"),
                                )
                            }
                        }
                    }
                    else -> {
                        {
                            if (!actions.openCredentialSettings()) showMessage(openFailedMsg)
                        }
                    }
                },
            )
            ManagerRow(
                title = stringResource(R.string.passkey_provider_title),
                summary = providerSummary,
                icon = MiuixIcons.Medium.Lock,
                badge = BadgeBlue,
                showChevron = true,
                onClick = {
                    if (!actions.openCredentialSettings()) {
                        showMessage(openFailedMsg)
                    }
                },
            )
        }
    }
}

@Composable
private fun SupportedKeyboardsSection(
    status: DeviceStatus,
    xposed: XposedStatus,
    keyboardIcon: ImageVector,
    actions: ManagerActions,
    showMessage: (String) -> Unit,
) {
    val scopeApprovedMsg = stringResource(R.string.snackbar_scope_approved)
    val scopeFailedPattern = stringResource(R.string.snackbar_scope_failed)
    val openFailedMsg = stringResource(R.string.snackbar_open_failed)

    val installedKeyboards = remember(status.keyboards) { status.keyboards.filter { it.installed } }
    val uninstalledKeyboards = remember(status.keyboards) { status.keyboards.filter { !it.installed } }
    var showUninstalled by rememberSaveable { mutableStateOf(false) }
    val primaryList = if (installedKeyboards.isNotEmpty()) installedKeyboards else status.keyboards
    val collapsibleList = if (installedKeyboards.isNotEmpty()) uninstalledKeyboards else emptyList()

    Column {
        SmallTitle(text = stringResource(R.string.ime_supported), insideMargin = SectionTitleMargin)
        Card(modifier = Modifier.fillMaxWidth(), cornerRadius = 20.dp, insideMargin = CardMargin) {
            primaryList.forEach { keyboard ->
                KeyboardItemRow(
                    keyboard = keyboard,
                    defaultPackage = status.defaultKeyboard?.packageName,
                    xposed = xposed,
                    keyboardIcon = keyboardIcon,
                    actions = actions,
                    scopeApprovedMsg = scopeApprovedMsg,
                    scopeFailedPattern = scopeFailedPattern,
                    openFailedMsg = openFailedMsg,
                    showMessage = showMessage,
                )
            }
            if (collapsibleList.isNotEmpty()) {
                val previewNames = remember(collapsibleList) {
                    collapsibleList.joinToString("、") { it.app.label.removeSuffix("输入法") }
                }
                ExpandableToggleRow(
                    title = stringResource(R.string.ime_more_title),
                    summary = stringResource(R.string.ime_more_summary, collapsibleList.size, previewNames),
                    icon = keyboardIcon,
                    badge = null,
                    expanded = showUninstalled,
                    onToggle = { showUninstalled = !showUninstalled },
                )
                AnimatedVisibility(
                    visible = showUninstalled,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut(),
                ) {
                    Column {
                        collapsibleList.forEach { keyboard ->
                            KeyboardItemRow(
                                keyboard = keyboard,
                                defaultPackage = status.defaultKeyboard?.packageName,
                                xposed = xposed,
                                keyboardIcon = keyboardIcon,
                                actions = actions,
                                scopeApprovedMsg = scopeApprovedMsg,
                                scopeFailedPattern = scopeFailedPattern,
                                openFailedMsg = openFailedMsg,
                                showMessage = showMessage,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun KeyboardItemRow(
    keyboard: KeyboardStatus,
    defaultPackage: String?,
    xposed: XposedStatus,
    keyboardIcon: ImageVector,
    actions: ManagerActions,
    scopeApprovedMsg: String,
    scopeFailedPattern: String,
    openFailedMsg: String,
    showMessage: (String) -> Unit,
) {
    val pkg = keyboard.app.packageName
    val isCurrent = pkg == defaultPackage
    val installText = stringResource(
        when {
            isCurrent -> R.string.ime_current
            keyboard.installed -> R.string.ime_installed
            else -> R.string.ime_not_installed
        },
    )
    val scoped = xposed.isScoped(pkg)
    val summary = when {
        xposed.active && keyboard.installed -> {
            val scopeText = stringResource(if (scoped) R.string.ime_scoped else R.string.ime_unscoped)
            stringResource(R.string.ime_status_join, installText, scopeText)
        }
        keyboard.installed && isCurrent -> {
            stringResource(R.string.ime_status_join, installText, stringResource(R.string.ime_app_info_hint))
        }
        else -> installText
    }
    val needsScope = xposed.active && keyboard.installed && !scoped
    ManagerRow(
        title = keyboard.app.label,
        summary = summary,
        icon = keyboardIcon,
        badge = null,
        packageName = if (keyboard.installed) pkg else null,
        showChevron = keyboard.installed,
        onClick = when {
            needsScope -> {
                {
                    actions.requestScope(listOf(pkg)) { ok, reason ->
                        showMessage(
                            if (ok) scopeApprovedMsg else String.format(scopeFailedPattern, reason ?: "-"),
                        )
                    }
                }
            }
            keyboard.installed && !isCurrent -> {
                {
                    if (!actions.openInputMethodPicker()) showMessage(openFailedMsg)
                }
            }
            keyboard.installed -> {
                {
                    if (!actions.openAppDetails(pkg)) showMessage(openFailedMsg)
                }
            }
            else -> null
        },
    )
}

@Composable
private fun HookedProcessRow(
    status: DeviceStatus,
    target: HookedProcessInfo,
    keyboardIcon: ImageVector,
    actions: ManagerActions,
    openFailedMsg: String,
    showMessage: (String) -> Unit,
) {
    val matchedKeyboard = status.keyboards.firstOrNull {
        target.processName == it.app.packageName || target.processName.startsWith("${it.app.packageName}:")
    }
    val matchedSystemApp = listOf(
        HyperPasskeyHooks.SETTINGS_PACKAGE to stringResource(R.string.passkey_scope_settings),
        HyperPasskeyHooks.SECURITY_CENTER_PACKAGE to stringResource(R.string.passkey_scope_security),
        HyperPasskeyHooks.SCANNER_PACKAGE to stringResource(R.string.passkey_scope_scanner),
    ).firstOrNull { (appPkg, _) ->
        target.processName == appPkg || target.processName.startsWith("$appPkg:")
    }
    val title = when {
        target.processName == "system" || target.processName == "system_server" || target.processName == "android" ->
            stringResource(R.string.process_system)
        matchedSystemApp != null ->
            stringResource(R.string.process_keyboard, matchedSystemApp.second, target.processName)
        matchedKeyboard != null ->
            stringResource(R.string.process_keyboard, matchedKeyboard.app.label, target.processName)
        else -> target.processName
    }
    val stateLabel = stringResource(
        when (target.state) {
            HookedTarget.State.UP_TO_DATE -> R.string.process_state_up_to_date
            HookedTarget.State.STALE -> R.string.process_state_stale
            HookedTarget.State.RELOADING -> R.string.process_state_reloading
            HookedTarget.State.FAILED -> R.string.process_state_failed
        },
    )
    val pkg = matchedKeyboard?.app?.packageName ?: matchedSystemApp?.first
    ManagerRow(
        title = title,
        summary = stringResource(R.string.process_summary, target.pid, target.loadedVersionCode, stateLabel),
        icon = if (pkg != null) keyboardIcon else MiuixIcons.Medium.Tasks,
        badge = if (pkg == null) BadgeEmerald else null,
        packageName = pkg,
        showChevron = pkg != null,
        onClick = if (pkg != null) {
            {
                if (!actions.openAppDetails(pkg)) showMessage(openFailedMsg)
            }
        } else {
            null
        },
    )
}

private data class KeyColorPreset(val labelRes: Int, val color: Int)

private val KeyColorPresets = listOf(
    KeyColorPreset(R.string.color_red, 0xFFF44336.toInt()),
    KeyColorPreset(R.string.color_pink, 0xFFE91E63.toInt()),
    KeyColorPreset(R.string.color_purple, 0xFF9C27B0.toInt()),
    KeyColorPreset(R.string.color_deep_purple, 0xFF673AB7.toInt()),
    KeyColorPreset(R.string.color_indigo, 0xFF3F51B5.toInt()),
    KeyColorPreset(R.string.color_blue, 0xFF2196F3.toInt()),
    KeyColorPreset(R.string.color_cyan, 0xFF00BCD4.toInt()),
    KeyColorPreset(R.string.color_teal, 0xFF009688.toInt()),
    KeyColorPreset(R.string.color_green, 0xFF4FAF50.toInt()),
    KeyColorPreset(R.string.color_yellow, 0xFFFFEB3B.toInt()),
    KeyColorPreset(R.string.color_amber, 0xFFFFC107.toInt()),
    KeyColorPreset(R.string.color_orange, 0xFFFF9800.toInt()),
    KeyColorPreset(R.string.color_brown, 0xFF795548.toInt()),
    KeyColorPreset(R.string.color_blue_grey, 0xFF607D8F.toInt()),
    KeyColorPreset(R.string.color_sakura, 0xFFFF9CA8.toInt()),
)

@Composable
private fun SettingsSection(
    status: DeviceStatus,
    xposed: XposedStatus,
    preferences: UiPreferences,
    backdrop: Backdrop,
    keyboardIcon: ImageVector,
    actions: ManagerActions,
    onOpenTest: () -> Unit,
    showMessage: (String) -> Unit,
) {
    val colors = MiuixTheme.colorScheme
    val modeOptions = listOf(
        stringResource(R.string.pref_theme_system),
        stringResource(R.string.pref_theme_light),
        stringResource(R.string.pref_theme_dark),
    )
    var showColorDialog by rememberSaveable { mutableStateOf(false) }
    var showProcesses by rememberSaveable { mutableStateOf(false) }
    val activeColor = preferences.keyColor
    val presetIndex = KeyColorPresets.indexOfFirst { it.color == activeColor }
    val isCustomColor = activeColor != 0 && presetIndex < 0
    val customLabel = if (isCustomColor) {
        stringResource(R.string.pref_key_color_custom_hex, String.format("#%06X", activeColor and 0xFFFFFF))
    } else {
        stringResource(R.string.pref_key_color_custom)
    }
    val keyColorOptions = buildList {
        add(stringResource(R.string.pref_key_color_default))
        KeyColorPresets.forEach { add(stringResource(it.labelRes)) }
        add(customLabel)
    }
    val selectedColorIndex = when {
        activeColor == 0 -> 0
        presetIndex >= 0 -> presetIndex + 1
        else -> keyColorOptions.lastIndex
    }
    val swatchColor = if (activeColor != 0) Color(activeColor or -0x1000000) else colors.primary
    val iconHiddenMsg = stringResource(R.string.snackbar_icon_hidden)
    val iconShownMsg = stringResource(R.string.snackbar_icon_shown)
    val scopeApprovedMsg = stringResource(R.string.snackbar_scope_approved)
    val scopeFailedPattern = stringResource(R.string.snackbar_scope_failed)
    val scopeReadyMsg = stringResource(R.string.snackbar_scope_all_ready)
    val refreshedMsg = stringResource(R.string.snackbar_refreshed)
    val openFailedMsg = stringResource(R.string.snackbar_open_failed)

    val systemLabel = stringResource(R.string.status_scope_system)
    val settingsLabel = stringResource(R.string.passkey_scope_settings)
    val securityLabel = stringResource(R.string.passkey_scope_security)
    val scannerLabel = stringResource(R.string.passkey_scope_scanner)
    val missingAllScopes = remember(status, xposed) { allMissingScopePackages(status, xposed) }
    val missingAllLabels = remember(missingAllScopes, status, systemLabel, settingsLabel, securityLabel, scannerLabel) {
        missingAllScopes.map { pkg ->
            when (pkg) {
                "android" -> systemLabel
                HyperPasskeyHooks.SETTINGS_PACKAGE -> settingsLabel
                HyperPasskeyHooks.SECURITY_CENTER_PACKAGE -> securityLabel
                HyperPasskeyHooks.SCANNER_PACKAGE -> scannerLabel
                else -> status.keyboards.firstOrNull { it.app.packageName == pkg }?.app?.label ?: pkg
            }
        }
    }
    val scopeSummary = when {
        !xposed.active -> stringResource(R.string.status_scope_inactive)
        xposed.scope.isEmpty() -> stringResource(R.string.status_scope_empty)
        missingAllLabels.isNotEmpty() -> stringResource(R.string.status_scope_missing, missingAllLabels.joinToString("、"))
        else -> stringResource(R.string.status_scope_ready)
    }

    val targets = xposed.runningTargets
    val staleCount = remember(targets) { targets.count { it.state != HookedTarget.State.UP_TO_DATE } }
    val processesSummary = when {
        targets.isEmpty() -> stringResource(R.string.processes_summary_empty)
        staleCount > 0 -> stringResource(R.string.processes_summary_stale, targets.size, staleCount)
        else -> stringResource(R.string.processes_summary_ok, targets.size)
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Column {
            SmallTitle(text = stringResource(R.string.section_appearance), insideMargin = SectionTitleMargin)
            Card(modifier = Modifier.fillMaxWidth(), cornerRadius = 20.dp, insideMargin = CardMargin) {
                GlassDropdownPreference(
                    title = stringResource(R.string.pref_theme_mode),
                    items = modeOptions,
                    selectedIndex = preferences.themeMode,
                    onSelectedIndexChange = { actions.updateThemeMode(it) },
                    backdrop = backdrop,
                    insideMargin = RowMargin,
                    startAction = { SquircleIconBadge(MiuixIcons.Medium.Theme, BadgeAmber) },
                )
                SwitchRow(
                    title = stringResource(R.string.pref_monet),
                    summary = stringResource(R.string.pref_monet_summary),
                    icon = MiuixIcons.Medium.Background,
                    badge = BadgeViolet,
                    checked = preferences.monetEnabled,
                    onCheckedChange = { actions.updateMonet(it) },
                )
                AnimatedVisibility(
                    visible = preferences.monetEnabled,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut(),
                ) {
                    GlassDropdownPreference(
                        title = stringResource(R.string.pref_key_color),
                        items = keyColorOptions,
                        selectedIndex = selectedColorIndex,
                        onSelectedIndexChange = { idx ->
                            when (idx) {
                                0 -> actions.updateKeyColor(0)
                                keyColorOptions.lastIndex -> showColorDialog = true
                                else -> actions.updateKeyColor(KeyColorPresets[idx - 1].color)
                            }
                        },
                        backdrop = backdrop,
                        insideMargin = RowMargin,
                        startAction = {
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(swatchColor)
                                    .border(
                                        0.75.dp,
                                        colors.onSurface.copy(alpha = 0.14f),
                                        RoundedCornerShape(10.dp),
                                    )
                                    .clickable(enabled = isCustomColor) { showColorDialog = true },
                            )
                        },
                    )
                }
            }
        }

        Column {
            SmallTitle(text = stringResource(R.string.section_general), insideMargin = SectionTitleMargin)
            Card(modifier = Modifier.fillMaxWidth(), cornerRadius = 20.dp, insideMargin = CardMargin) {
                ManagerRow(
                    title = stringResource(R.string.status_scope),
                    summary = scopeSummary,
                    icon = MiuixIcons.Medium.Tune,
                    badge = BadgeIndigo,
                    showChevron = xposed.active && missingAllScopes.isNotEmpty(),
                    onClick = if (!xposed.active) {
                        {
                            actions.refresh()
                            showMessage(refreshedMsg)
                        }
                    } else {
                        {
                            if (missingAllScopes.isEmpty()) {
                                showMessage(scopeReadyMsg)
                            } else {
                                actions.requestScope(missingAllScopes) { ok, reason ->
                                    showMessage(
                                        if (ok) scopeApprovedMsg else String.format(scopeFailedPattern, reason ?: "-"),
                                    )
                                }
                            }
                        }
                    },
                )
                SwitchRow(
                    title = stringResource(R.string.pref_hide_icon),
                    summary = stringResource(R.string.pref_hide_icon_summary),
                    icon = MiuixIcons.Medium.Hide,
                    badge = BadgeSlate,
                    checked = preferences.launcherIconHidden,
                    onCheckedChange = { hidden ->
                        actions.updateLauncherIconHidden(hidden)
                        showMessage(if (hidden) iconHiddenMsg else iconShownMsg)
                    },
                )
            }
        }

        Column {
            SmallTitle(text = stringResource(R.string.section_tools), insideMargin = SectionTitleMargin)
            Card(modifier = Modifier.fillMaxWidth(), cornerRadius = 20.dp, insideMargin = CardMargin) {
                ManagerRow(
                    title = stringResource(R.string.tool_autofill_test),
                    summary = stringResource(R.string.tool_autofill_test_summary),
                    icon = keyboardIcon,
                    badge = BadgeSky,
                    showChevron = true,
                    onClick = onOpenTest,
                )
                if (targets.isNotEmpty()) {
                    ExpandableToggleRow(
                        title = stringResource(R.string.section_processes),
                        summary = processesSummary,
                        icon = MiuixIcons.Medium.Tasks,
                        badge = BadgeEmerald,
                        expanded = showProcesses,
                        onToggle = { showProcesses = !showProcesses },
                    )
                    AnimatedVisibility(
                        visible = showProcesses,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut(),
                    ) {
                        Column {
                            targets.forEach { target ->
                                HookedProcessRow(
                                    status = status,
                                    target = target,
                                    keyboardIcon = keyboardIcon,
                                    actions = actions,
                                    openFailedMsg = openFailedMsg,
                                    showMessage = showMessage,
                                )
                            }
                        }
                    }
                } else {
                    ManagerRow(
                        title = stringResource(R.string.section_processes),
                        summary = processesSummary,
                        icon = MiuixIcons.Medium.Tasks,
                        badge = BadgeEmerald,
                        onClick = {
                            actions.refresh()
                            showMessage(refreshedMsg)
                        },
                    )
                }
            }
        }
    }

    KeyColorPickerDialog(
        show = showColorDialog,
        initialColor = swatchColor,
        backdrop = backdrop,
        onDismiss = { showColorDialog = false },
        onConfirm = { picked ->
            actions.updateKeyColor(picked.toArgb())
            showColorDialog = false
        },
    )
}

@Composable
private fun KeyColorPickerDialog(
    show: Boolean,
    initialColor: Color,
    backdrop: Backdrop?,
    onDismiss: () -> Unit,
    onConfirm: (Color) -> Unit,
) {
    var draftColor by remember(show, initialColor) {
        mutableStateOf(initialColor.copy(alpha = 1f))
    }
    val hexText = remember(draftColor) {
        String.format("#%06X", draftColor.toArgb() and 0xFFFFFF)
    }

    GlassDialog(
        show = show,
        onDismissRequest = onDismiss,
        backdrop = backdrop,
        title = stringResource(R.string.key_color_dialog_title),
        summary = hexText,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            ColorPalette(
                color = draftColor,
                onColorChanged = { draftColor = it.copy(alpha = 1f) },
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(modifier = Modifier.height(20.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                GlassDialogButton(
                    onClick = onDismiss,
                    role = GlassDialogButtonRole.Normal,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        text = stringResource(R.string.action_cancel),
                        style = MiuixTheme.textStyles.body1.copy(fontWeight = FontWeight.Medium),
                    )
                }
                GlassDialogButton(
                    onClick = { onConfirm(draftColor) },
                    role = GlassDialogButtonRole.Primary,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        text = stringResource(R.string.action_confirm),
                        style = MiuixTheme.textStyles.body1.copy(fontWeight = FontWeight.Medium),
                    )
                }
            }
        }
    }
}

@Composable
private fun AutofillTestDialog(
    show: Boolean,
    backdrop: Backdrop?,
    onDismiss: () -> Unit,
) {
    val colors = MiuixTheme.colorScheme
    val usernameState = rememberTextFieldState()
    val passwordState = rememberTextFieldState()
    var passwordVisible by rememberSaveable { mutableStateOf(false) }
    val maskTransformation = remember {
        OutputTransformation {
            replace(0, length, "\u2022".repeat(length))
        }
    }

    GlassDialog(
        show = show,
        onDismissRequest = onDismiss,
        backdrop = backdrop,
        title = stringResource(R.string.test_dialog_title),
        summary = stringResource(R.string.test_dialog_summary),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            GlassTextField(
                state = usernameState,
                label = stringResource(R.string.test_hint_username),
                useLabelAsPlaceholder = true,
                lineLimits = TextFieldLineLimits.SingleLine,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Email,
                    imeAction = ImeAction.Next,
                ),
                leadingIcon = {
                    Icon(
                        imageVector = MiuixIcons.Contacts,
                        contentDescription = null,
                        tint = colors.onSurfaceVariantSummary,
                        modifier = Modifier
                            .padding(start = 16.dp, end = 10.dp)
                            .size(20.dp),
                    )
                },
                trailingIcon = if (usernameState.text.isNotEmpty()) {
                    {
                        IconButton(
                            onClick = { usernameState.clearText() },
                            modifier = Modifier.padding(end = 6.dp),
                        ) {
                            Icon(
                                imageVector = MiuixIcons.Clear,
                                contentDescription = null,
                                tint = colors.onSurfaceVariantSummary,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                } else {
                    null
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentType = ContentType.Username },
            )

            Spacer(modifier = Modifier.height(12.dp))

            GlassTextField(
                state = passwordState,
                label = stringResource(R.string.test_hint_password),
                useLabelAsPlaceholder = true,
                lineLimits = TextFieldLineLimits.SingleLine,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done,
                ),
                outputTransformation = if (passwordVisible) null else maskTransformation,
                leadingIcon = {
                    Icon(
                        imageVector = MiuixIcons.Lock,
                        contentDescription = null,
                        tint = colors.onSurfaceVariantSummary,
                        modifier = Modifier
                            .padding(start = 16.dp, end = 10.dp)
                            .size(20.dp),
                    )
                },
                trailingIcon = {
                    IconButton(
                        onClick = { passwordVisible = !passwordVisible },
                        modifier = Modifier.padding(end = 6.dp),
                    ) {
                        Icon(
                            imageVector = if (passwordVisible) MiuixIcons.Show else MiuixIcons.Hide,
                            contentDescription = null,
                            tint = colors.onSurfaceVariantSummary,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentType = ContentType.Password },
            )

            Spacer(modifier = Modifier.height(20.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                GlassDialogButton(
                    onClick = {
                        usernameState.clearText()
                        passwordState.clearText()
                    },
                    role = GlassDialogButtonRole.Normal,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        text = stringResource(R.string.action_clear),
                        style = MiuixTheme.textStyles.body1.copy(fontWeight = FontWeight.Medium),
                    )
                }
                GlassDialogButton(
                    onClick = onDismiss,
                    role = GlassDialogButtonRole.Primary,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        text = stringResource(R.string.action_close),
                        style = MiuixTheme.textStyles.body1.copy(fontWeight = FontWeight.Medium),
                    )
                }
            }
        }
    }
}

private val FirstScreenCardsHeight = 276.dp

@Composable
private fun HyperCeilerAboutPage(
    outerInsets: PaddingValues,
    metadata: AppMetadata,
    xposed: XposedStatus,
    dark: Boolean,
    playing: Boolean,
    actions: ManagerActions,
    showMessage: (String) -> Unit,
) {
    val layoutDirection = LocalLayoutDirection.current
    val listState = rememberLazyListState()
    val aboutBackdrop = rememberLayerBackdrop()
    val contentBackdrop = rememberLayerBackdrop()

    val scrollProgress by remember(listState) {
        derivedStateOf {
            when {
                listState.firstVisibleItemIndex > 0 -> 1f
                else -> {
                    val spacer = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == "logo_spacer" }
                    if (spacer != null && spacer.size > 0) {
                        (listState.firstVisibleItemScrollOffset.toFloat() / spacer.size).coerceIn(0f, 1f)
                    } else {
                        0f
                    }
                }
            }
        }
    }
    val showCompactTitle by remember { derivedStateOf { scrollProgress >= 0.48f } }
    val topBarScrolled by remember { derivedStateOf { scrollProgress >= 0.55f } }
    val titleAlpha by animateFloatAsState(
        targetValue = if (showCompactTitle) 1f else 0f,
        animationSpec = tween(durationMillis = 180),
    )

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = Color.Transparent,
        popupHost = {},
        topBar = {
            GlassTopAppBar(
                title = stringResource(R.string.manager_about),
                isContentScrolled = topBarScrolled,
                expandable = false,
                progressiveBlur = true,
                backdrop = contentBackdrop,
                titleAlpha = { titleAlpha },
                backgroundAlpha = { ((scrollProgress - 0.55f) / 0.35f).coerceIn(0f, 1f) },
            )
        },
    ) { pageInsets ->
        val topPadding = pageInsets.calculateTopPadding()
        val bottomInset = outerInsets.calculateBottomPadding()
        val contentPadding = PaddingValues(
            start = outerInsets.calculateStartPadding(layoutDirection) + 12.dp,
            end = outerInsets.calculateEndPadding(layoutDirection) + 12.dp,
            top = topPadding,
            bottom = bottomInset + 18.dp,
        )

        HyperOsAboutBackground(
            dark = dark,
            playing = playing,
            modifier = Modifier
                .fillMaxSize()
                .layerBackdrop(contentBackdrop),
            bgModifier = Modifier.layerBackdrop(aboutBackdrop),
            alpha = { (1f - scrollProgress).coerceIn(0f, 1f) },
        ) {
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val headerHeight = (maxHeight - topPadding - bottomInset - FirstScreenCardsHeight)
                    .coerceIn(280.dp, 430.dp)

                AboutVersionHeader(
                    metadata = metadata,
                    dark = dark,
                    aboutBackdrop = aboutBackdrop,
                    topPadding = topPadding,
                    headerHeight = headerHeight,
                    scrollProgress = { scrollProgress },
                )

                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .scrollEndHaptic()
                        .overScrollVertical(),
                    contentPadding = contentPadding,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    item(key = "logo_spacer") {
                        Spacer(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(headerHeight),
                        )
                    }
                    item(key = "device_info") {
                        MyDeviceOverviewSection(
                            metadata = metadata,
                            xposed = xposed,
                            dark = dark,
                        )
                    }
                    item(key = "author_card") {
                        AuthorCard(dark = dark, actions = actions, showMessage = showMessage)
                    }
                    item(key = "project_card") {
                        AboutProjectCard(
                            metadata = metadata,
                            dark = dark,
                            actions = actions,
                            showMessage = showMessage,
                        )
                    }
                    item(key = "credits_card") {
                        AboutCreditsCard(dark = dark, actions = actions, showMessage = showMessage)
                    }
                }
            }
        }
    }
}

@Composable
private fun AboutVersionHeader(
    metadata: AppMetadata,
    dark: Boolean,
    aboutBackdrop: Backdrop,
    topPadding: Dp,
    headerHeight: Dp,
    scrollProgress: () -> Float,
) {
    val context = LocalContext.current
    val isDebug = remember(context) {
        (context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0
    }
    val channelLabel = stringResource(
        if (isDebug) R.string.about_channel_debug else R.string.about_channel_release,
    )
    val logoBlend = remember(dark) {
        if (dark) {
            listOf(
                BlendColorEntry(Color(0xE6A1A1A1), BlurBlendMode.ColorDodge),
                BlendColorEntry(Color(0x4DE6E6E6), BlurBlendMode.LinearLight),
                BlendColorEntry(Color(0xFF1AF500), BlurBlendMode.Lab),
            )
        } else {
            listOf(
                BlendColorEntry(Color(0xCC4A4A4A), BlurBlendMode.ColorBurn),
                BlendColorEntry(Color(0xFF4F4F4F), BlurBlendMode.LinearLight),
                BlendColorEntry(Color(0xFF1AF200), BlurBlendMode.Lab),
            )
        }
    }
    val logoBlurColors = BlurDefaults.blurColors(blendColors = logoBlend)
    val iconBlurEnabled by remember { derivedStateOf { scrollProgress() < 0.62f } }
    val titleBlurEnabled by remember { derivedStateOf { scrollProgress() < 0.44f } }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = topPadding)
            .height(headerHeight),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Canvas(
                modifier = Modifier
                    .graphicsLayer {
                        val p = ((scrollProgress() - 0.34f) / 0.24f).coerceIn(0f, 1f)
                        alpha = 1f - p
                        val s = 1f - p * 0.08f
                        scaleX = s
                        scaleY = s
                    }
                    .size(90.dp)
                    .textureBlur(
                        backdrop = aboutBackdrop,
                        shape = RoundedCornerShape(24.dp),
                        blurRadius = 100f,
                        colors = logoBlurColors,
                        contentBlendMode = BlendMode.DstIn,
                        enabled = iconBlurEnabled,
                    ),
            ) {
                val w = size.width
                val cx = w * 0.5f
                val cy = size.height * 0.5f
                val ringRadius = w * 0.265f
                val strokePx = w * 0.155f
                drawRoundRect(
                    color = Color.White,
                    cornerRadius = CornerRadius(24.dp.toPx()),
                )
                drawArc(
                    color = Color.Black,
                    startAngle = 42f,
                    sweepAngle = 276f,
                    useCenter = false,
                    topLeft = Offset(cx - ringRadius, cy - ringRadius),
                    size = Size(ringRadius * 2f, ringRadius * 2f),
                    style = Stroke(width = strokePx, cap = StrokeCap.Round),
                    blendMode = BlendMode.Clear,
                )
                drawLine(
                    color = Color.Black,
                    start = Offset(cx, cy),
                    end = Offset(cx + ringRadius, cy),
                    strokeWidth = strokePx,
                    cap = StrokeCap.Round,
                    blendMode = BlendMode.Clear,
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            Text(
                text = stringResource(R.string.module_name),
                style = MiuixTheme.textStyles.headline1.copy(
                    fontSize = 38.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.5).sp,
                ),
                color = MiuixTheme.colorScheme.onSurface,
                modifier = Modifier
                    .graphicsLayer {
                        val p = ((scrollProgress() - 0.18f) / 0.22f).coerceIn(0f, 1f)
                        alpha = 1f - p
                        val s = 1f - p * 0.08f
                        scaleX = s
                        scaleY = s
                    }
                    .textureBlur(
                        backdrop = aboutBackdrop,
                        shape = RoundedCornerShape(14.dp),
                        blurRadius = 100f,
                        colors = logoBlurColors,
                        contentBlendMode = BlendMode.DstIn,
                        enabled = titleBlurEnabled,
                    )
                    .padding(horizontal = 10.dp, vertical = 2.dp),
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = stringResource(
                    R.string.about_version_line,
                    metadata.versionName,
                    metadata.versionCode,
                    channelLabel,
                ),
                style = MiuixTheme.textStyles.body2.copy(fontSize = 14.sp),
                color = if (dark) Color(0x99FFFFFF) else Color(0x99000000),
                modifier = Modifier.graphicsLayer {
                    val p = ((scrollProgress() - 0.05f) / 0.20f).coerceIn(0f, 1f)
                    alpha = 1f - p
                    val s = 1f - p * 0.06f
                    scaleX = s
                    scaleY = s
                },
            )
        }
    }
}

@Composable
private fun AboutGlassCard(
    dark: Boolean,
    modifier: Modifier = Modifier,
    insideMargin: PaddingValues = CardMargin,
    content: @Composable ColumnScope.() -> Unit,
) {
    val fill = if (dark) Color(0x1CFFFFFF) else Color(0xCCFFFFFF)
    Card(
        modifier = modifier,
        cornerRadius = 20.dp,
        insideMargin = insideMargin,
        colors = CardDefaults.defaultColors(color = fill),
        content = content,
    )
}

@Composable
private fun MyDeviceOverviewSection(
    metadata: AppMetadata,
    xposed: XposedStatus,
    dark: Boolean,
) {
    val frameworkTitle = if (xposed.active) xposed.frameworkName else stringResource(R.string.status_inactive)
    val frameworkSub = if (xposed.active) {
        "${xposed.frameworkVersion} · API ${xposed.apiVersion}"
    } else {
        stringResource(R.string.about_xposed_api_default)
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            MyDeviceBentoTile(
                label = stringResource(R.string.about_bento_device),
                value = metadata.deviceName,
                sub = metadata.marketName,
                dark = dark,
                modifier = Modifier.weight(1f),
            )
            MyDeviceBentoTile(
                label = stringResource(R.string.about_bento_framework),
                value = frameworkTitle,
                sub = frameworkSub,
                dark = dark,
                modifier = Modifier.weight(1f),
            )
        }

        AboutGlassCard(
            dark = dark,
            modifier = Modifier.fillMaxWidth(),
            insideMargin = PaddingValues(horizontal = 18.dp, vertical = 16.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    MyDeviceSpecCell(
                        value = stringResource(R.string.about_spec_android_val, metadata.androidRelease),
                        label = stringResource(R.string.about_spec_android_sub, metadata.sdkInt),
                        modifier = Modifier.weight(1f),
                    )
                    MyDeviceSpecCell(
                        value = stringResource(R.string.about_spec_min_val),
                        label = stringResource(R.string.about_spec_min_sub),
                        modifier = Modifier.weight(1f),
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    MyDeviceSpecCell(
                        value = metadata.osVersion,
                        label = stringResource(R.string.about_spec_os_sub),
                        modifier = Modifier.weight(1f),
                    )
                    MyDeviceSpecCell(
                        value = stringResource(R.string.about_spec_autofill_val),
                        label = stringResource(R.string.about_spec_autofill_sub),
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun MyDeviceBentoTile(
    label: String,
    value: String,
    sub: String,
    dark: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = MiuixTheme.colorScheme
    AboutGlassCard(
        dark = dark,
        modifier = modifier,
        insideMargin = PaddingValues(horizontal = 16.dp, vertical = 15.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = label,
                style = MiuixTheme.textStyles.body2.copy(fontSize = 12.sp),
                color = colors.onSurfaceVariantSummary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = value,
                style = MiuixTheme.textStyles.headline1.copy(fontSize = 19.sp, fontWeight = FontWeight.Bold),
                color = colors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp),
            )
            Text(
                text = sub,
                style = MiuixTheme.textStyles.body2.copy(fontSize = 12.sp),
                color = colors.onSurfaceVariantSummary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun MyDeviceSpecCell(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
) {
    val colors = MiuixTheme.colorScheme
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = value,
            style = MiuixTheme.textStyles.body1.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
            color = colors.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = label,
            style = MiuixTheme.textStyles.body2.copy(fontSize = 12.sp),
            color = colors.onSurfaceVariantSummary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun AuthorCard(
    dark: Boolean,
    actions: ManagerActions,
    showMessage: (String) -> Unit,
) {
    val colors = MiuixTheme.colorScheme
    val openFailedMsg = stringResource(R.string.snackbar_open_failed)
    val coolapkAction = stringResource(R.string.about_coolapk_action)

    AboutGlassCard(dark = dark, modifier = Modifier.fillMaxWidth()) {
        BasicComponent(
            title = stringResource(R.string.about_author_name),
            summary = stringResource(R.string.about_coolapk_summary),
            insideMargin = RowMargin,
            startAction = {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                colors = listOf(Color(0xFF2B74FF), Color(0xFF6AB2FF)),
                            ),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = MiuixIcons.Contacts,
                        contentDescription = null,
                        modifier = Modifier.size(22.dp),
                        tint = Color.White,
                    )
                }
            },
            endActions = {
                Text(
                    text = coolapkAction,
                    style = MiuixTheme.textStyles.body2,
                    color = colors.onSurfaceVariantActions,
                    modifier = Modifier
                        .padding(end = 8.dp)
                        .align(Alignment.CenterVertically),
                )
                Icon(
                    imageVector = MiuixIcons.Basic.ArrowRight,
                    contentDescription = null,
                    modifier = Modifier
                        .size(width = 10.dp, height = 16.dp)
                        .align(Alignment.CenterVertically),
                    tint = colors.onSurfaceVariantSummary,
                )
            },
            onClick = {
                if (!actions.openUrl(COOLAPK_URL)) showMessage(openFailedMsg)
            },
        )
    }
}

@Composable
private fun AboutProjectCard(
    metadata: AppMetadata,
    dark: Boolean,
    actions: ManagerActions,
    showMessage: (String) -> Unit,
) {
    val context = LocalContext.current
    val colors = MiuixTheme.colorScheme
    val copyLabel = stringResource(R.string.about_copy_package)
    val packageLabel = stringResource(R.string.about_package)
    val copiedMsg = stringResource(R.string.snackbar_package_copied)
    val openFailedMsg = stringResource(R.string.snackbar_open_failed)

    AboutGlassCard(dark = dark, modifier = Modifier.fillMaxWidth()) {
        AboutArrowRow(
            title = stringResource(R.string.about_github),
            summary = stringResource(R.string.about_github_summary),
            value = stringResource(R.string.about_github_action),
            onClick = {
                if (!actions.openUrl(GITHUB_URL)) showMessage(openFailedMsg)
            },
        )
        AboutArrowRow(
            title = stringResource(R.string.about_issues),
            summary = stringResource(R.string.about_issues_summary),
            value = stringResource(R.string.about_issues_action),
            onClick = {
                if (!actions.openUrl(ISSUES_URL)) showMessage(openFailedMsg)
            },
        )
        BasicComponent(
            title = packageLabel,
            summary = metadata.packageName,
            insideMargin = RowMargin,
            endActions = {
                Icon(
                    imageVector = MiuixIcons.Copy,
                    contentDescription = copyLabel,
                    modifier = Modifier
                        .size(19.dp)
                        .align(Alignment.CenterVertically),
                    tint = colors.onSurfaceVariantSummary,
                )
            },
            onClickLabel = copyLabel,
            onClick = {
                context.getSystemService(ClipboardManager::class.java)
                    ?.setPrimaryClip(ClipData.newPlainText(packageLabel, metadata.packageName))
                showMessage(copiedMsg)
            },
        )
    }
}

@Composable
private fun AboutCreditsCard(
    dark: Boolean,
    actions: ManagerActions,
    showMessage: (String) -> Unit,
) {
    val openFailedMsg = stringResource(R.string.snackbar_open_failed)
    val githubAction = stringResource(R.string.about_github_action)

    AboutGlassCard(dark = dark, modifier = Modifier.fillMaxWidth()) {
        AboutArrowRow(
            title = stringResource(R.string.about_license),
            value = stringResource(R.string.about_license_value),
            onClick = {
                if (!actions.openUrl(LICENSE_URL)) showMessage(openFailedMsg)
            },
        )
        AboutArrowRow(
            title = stringResource(R.string.about_credit_lsposed),
            summary = stringResource(R.string.about_credit_lsposed_summary),
            value = githubAction,
            onClick = {
                if (!actions.openUrl("https://github.com/libxposed")) showMessage(openFailedMsg)
            },
        )
        AboutArrowRow(
            title = stringResource(R.string.about_credit_miuix),
            summary = stringResource(R.string.about_credit_miuix_summary),
            value = githubAction,
            onClick = {
                if (!actions.openUrl("https://github.com/miuix-kotlin-multiplatform/miuix")) {
                    showMessage(openFailedMsg)
                }
            },
        )
    }
}

@Composable
private fun AboutArrowRow(
    title: String,
    summary: String? = null,
    value: String? = null,
    onClick: () -> Unit,
) {
    val colors = MiuixTheme.colorScheme
    BasicComponent(
        title = title,
        summary = summary,
        insideMargin = RowMargin,
        endActions = {
            if (value != null) {
                Text(
                    text = value,
                    style = MiuixTheme.textStyles.body2,
                    color = colors.onSurfaceVariantActions,
                    modifier = Modifier
                        .padding(end = 8.dp)
                        .align(Alignment.CenterVertically),
                )
            }
            Icon(
                imageVector = MiuixIcons.Basic.ArrowRight,
                contentDescription = null,
                modifier = Modifier
                    .size(width = 10.dp, height = 16.dp)
                    .align(Alignment.CenterVertically),
                tint = colors.onSurfaceVariantSummary,
            )
        },
        onClick = onClick,
    )
}

@Composable
private fun SwitchRow(
    title: String,
    summary: String,
    icon: ImageVector,
    badge: IconBadgeStyle? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    BasicComponent(
        title = title,
        summary = summary,
        insideMargin = RowMargin,
        startAction = { PackageIcon(null, icon, badge) },
        endActions = {
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                modifier = Modifier.align(Alignment.CenterVertically),
            )
        },
        onClick = { onCheckedChange(!checked) },
    )
}

@Composable
private fun ExpandableToggleRow(
    title: String,
    summary: String,
    icon: ImageVector,
    badge: IconBadgeStyle?,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 270f else 90f,
        animationSpec = tween(durationMillis = 200),
    )
    BasicComponent(
        title = title,
        summary = summary,
        insideMargin = RowMargin,
        startAction = { PackageIcon(null, icon, badge) },
        endActions = {
            Icon(
                imageVector = MiuixIcons.Basic.ArrowRight,
                contentDescription = null,
                modifier = Modifier
                    .size(width = 10.dp, height = 16.dp)
                    .align(Alignment.CenterVertically)
                    .graphicsLayer { rotationZ = rotation },
                tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
        },
        onClick = onToggle,
    )
}

@Composable
private fun ManagerRow(
    title: String,
    summary: String,
    icon: ImageVector,
    badge: IconBadgeStyle? = null,
    packageName: String? = null,
    showChevron: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    BasicComponent(
        title = title,
        summary = summary,
        insideMargin = RowMargin,
        startAction = { PackageIcon(packageName, icon, badge) },
        endActions = {
            if (showChevron) {
                Icon(
                    imageVector = MiuixIcons.Basic.ArrowRight,
                    contentDescription = null,
                    modifier = Modifier
                        .size(width = 10.dp, height = 16.dp)
                        .align(Alignment.CenterVertically),
                    tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
        },
        onClick = onClick,
    )
}

@Composable
private fun SquircleIconBadge(
    icon: ImageVector,
    badge: IconBadgeStyle?,
) {
    val colors = MiuixTheme.colorScheme
    val shape = RoundedCornerShape(10.dp)
    val bgModifier = if (badge != null) {
        Modifier.background(
            Brush.linearGradient(
                colors = listOf(badge.topColor, badge.bottomColor),
            ),
        )
    } else {
        Modifier.background(colors.onSurface.copy(alpha = 0.07f))
    }
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(shape)
            .then(bgModifier),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(19.dp),
            tint = badge?.tint ?: colors.onSurfaceVariantSummary,
        )
    }
}

@Composable
private fun PackageIcon(
    packageName: String?,
    fallback: ImageVector,
    badge: IconBadgeStyle? = null,
) {
    val context = LocalContext.current
    val bitmap = remember(context, packageName) {
        AppIconCache.get(context.packageManager, packageName)
    }
    if (bitmap != null) {
        val shape = RoundedCornerShape(10.dp)
        Image(
            bitmap = bitmap,
            contentDescription = null,
            modifier = Modifier
                .size(34.dp)
                .clip(shape)
                .border(
                    0.5.dp,
                    MiuixTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                    shape,
                ),
        )
    } else {
        SquircleIconBadge(icon = fallback, badge = badge)
    }
}
