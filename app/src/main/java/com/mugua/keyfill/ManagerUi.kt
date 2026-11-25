package com.mugua.keyfill

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.pm.PackageManager
import android.graphics.drawable.GradientDrawable
import android.text.InputType
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.graphics.drawable.toBitmap
import androidx.core.view.WindowCompat
import io.github.libxposed.service.HookedTarget
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.SnackbarHost
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.basic.rememberTopAppBarState
import top.yukonga.miuix.kmp.blur.Backdrop
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.glass.GlassDropdownPreference
import top.yukonga.miuix.kmp.glass.GlassNavigationBar
import top.yukonga.miuix.kmp.glass.GlassNavigationItem
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.ArrowRight
import top.yukonga.miuix.kmp.icon.extended.Contacts
import top.yukonga.miuix.kmp.icon.extended.Copy
import top.yukonga.miuix.kmp.icon.extended.Hide
import top.yukonga.miuix.kmp.icon.extended.Home
import top.yukonga.miuix.kmp.icon.extended.Info
import top.yukonga.miuix.kmp.icon.extended.Layers
import top.yukonga.miuix.kmp.icon.extended.Link
import top.yukonga.miuix.kmp.icon.extended.Lock
import top.yukonga.miuix.kmp.icon.extended.Notes
import top.yukonga.miuix.kmp.icon.extended.Refresh
import top.yukonga.miuix.kmp.icon.extended.Settings
import top.yukonga.miuix.kmp.icon.extended.Tasks
import top.yukonga.miuix.kmp.icon.extended.Theme
import top.yukonga.miuix.kmp.icon.extended.Tune
import top.yukonga.miuix.kmp.material.GlassEdgeStrokes
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController
import top.yukonga.miuix.kmp.theme.darkColorScheme
import top.yukonga.miuix.kmp.theme.lightColorScheme
import top.yukonga.miuix.kmp.utils.PagerNavigationSpringSpec
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic
import top.yukonga.miuix.kmp.utils.springAnimateToPage

private val CardMargin = PaddingValues(0.dp)
private val RowMargin = PaddingValues(horizontal = 16.dp, vertical = 15.dp)
private val SectionTitleMargin = PaddingValues(horizontal = 16.dp, vertical = 10.dp)

private const val COOLAPK_URL = "https://www.coolapk.com/u/9898174"
private const val GITHUB_URL = "https://github.com/zrmugua/keyfill"

private enum class HealthState {
    WORKING,
    RELOAD_NEEDED,
    SETUP_NEEDED,
    INACTIVE,
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
    val themeController = remember(schemeMode, systemDark) {
        ThemeController(
            colorSchemeMode = schemeMode,
            lightColors = lightColorScheme(primary = Color(0xFF1B5EF2)),
            darkColors = darkColorScheme(primary = Color(0xFF6DA8FF)),
            isDark = dark,
        )
    }

    MiuixTheme(controller = themeController) {
        val colors = MiuixTheme.colorScheme
        val pagerState = rememberPagerState(initialPage = 0, pageCount = { 3 })
        val scope = rememberCoroutineScope()
        val snackbarHostState = remember { SnackbarHostState() }
        var showTestDialog by rememberSaveable { mutableStateOf(false) }
        val activeTab = pagerState.targetPage

        BackHandler(enabled = activeTab != 0) {
            scope.launch { pagerState.springAnimateToPage(0) }
        }

        val pageState = rememberSaveableStateHolder()
        val backdrop = rememberLayerBackdrop()
        val homeLabel = stringResource(R.string.manager_home)
        val settingsLabel = stringResource(R.string.manager_settings)
        val aboutLabel = stringResource(R.string.manager_about)
        val refreshLabel = stringResource(R.string.action_refresh)
        val refreshedMessage = stringResource(R.string.snackbar_refreshed)
        val tabs = remember(homeLabel, settingsLabel, aboutLabel) {
            listOf(
                GlassNavigationItem(icon = MiuixIcons.Home, label = homeLabel),
                GlassNavigationItem(icon = MiuixIcons.Tune, label = settingsLabel),
                GlassNavigationItem(icon = MiuixIcons.Info, label = aboutLabel),
            )
        }
        val showMessage: (String) -> Unit = { message ->
            scope.launch { snackbarHostState.showSnackbar(message) }
        }

        Scaffold(
            modifier = Modifier.fillMaxSize(),
            contentWindowInsets = WindowInsets.safeDrawing,
            snackbarHost = { SnackbarHost(state = snackbarHostState) },
            bottomBar = {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .windowInsetsPadding(
                            WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal),
                        )
                        .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    GlassNavigationBar(
                        items = tabs,
                        selectedIndex = activeTab,
                        onSelect = { target ->
                            if (target != pagerState.targetPage) {
                                scope.launch { pagerState.springAnimateToPage(target) }
                            }
                        },
                        backdrop = backdrop,
                        defaultWindowInsetsPadding = false,
                        selectedColor = colors.primary,
                        height = 60.dp,
                        stroke = GlassEdgeStrokes.forTheme(
                            dark,
                            GlassEdgeStrokes.SmallLight,
                            GlassEdgeStrokes.SmallDark,
                        ),
                        modifier = Modifier.widthIn(max = 304.dp).fillMaxWidth(),
                    )
                }
            },
        ) { outerInsets ->
            val layoutDirection = LocalLayoutDirection.current
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .layerBackdrop(backdrop)
                    .background(colors.surface),
            ) {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),
                    beyondViewportPageCount = 1,
                    flingBehavior = PagerDefaults.flingBehavior(
                        state = pagerState,
                        snapAnimationSpec = PagerNavigationSpringSpec,
                    ),
                ) { pageIndex ->
                    pageState.SaveableStateProvider(pageIndex) {
                        val scrollBehavior = MiuixScrollBehavior(rememberTopAppBarState())
                        val pageTitle = when (pageIndex) {
                            0 -> stringResource(R.string.module_name)
                            1 -> settingsLabel
                            else -> aboutLabel
                        }
                        Scaffold(
                            modifier = Modifier.fillMaxSize(),
                            containerColor = Color.Transparent,
                            popupHost = {},
                            topBar = {
                                TopAppBar(
                                    title = pageTitle,
                                    color = colors.surface,
                                    scrollBehavior = scrollBehavior,
                                    actions = {
                                        if (pageIndex == 0) {
                                            IconButton(
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
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .scrollEndHaptic()
                                    .overScrollVertical()
                                    .nestedScroll(scrollBehavior.nestedScrollConnection),
                                contentPadding = contentPadding,
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                when (pageIndex) {
                                    0 -> {
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
                                            EnvironmentSection(
                                                status = status,
                                                xposed = xposed,
                                                actions = actions,
                                                showMessage = showMessage,
                                            )
                                        }
                                        item {
                                            SupportedKeyboardsSection(
                                                status = status,
                                                xposed = xposed,
                                                actions = actions,
                                                onOpenTest = { showTestDialog = true },
                                                showMessage = showMessage,
                                            )
                                        }
                                        if (xposed.runningTargets.isNotEmpty()) {
                                            item {
                                                HookedProcessesSection(
                                                    status = status,
                                                    targets = xposed.runningTargets,
                                                    actions = actions,
                                                    showMessage = showMessage,
                                                )
                                            }
                                        }
                                    }
                                    1 -> {
                                        item {
                                            SettingsSection(
                                                preferences = preferences,
                                                backdrop = backdrop,
                                                actions = actions,
                                                onOpenTest = { showTestDialog = true },
                                                showMessage = showMessage,
                                            )
                                        }
                                    }
                                    else -> {
                                        item { AboutHeroCard(metadata = metadata, dark = dark) }
                                        item {
                                            AboutDetailsSection(
                                                status = status,
                                                metadata = metadata,
                                                xposed = xposed,
                                                actions = actions,
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

            AutofillTestDialog(
                show = showTestDialog,
                dark = dark,
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
        colors = CardDefaults.defaultColors(color = container),
        insideMargin = CardMargin,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 128.dp)
                .clickable {
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

@Composable
private fun EnvironmentSection(
    status: DeviceStatus,
    xposed: XposedStatus,
    actions: ManagerActions,
    showMessage: (String) -> Unit,
) {
    val unset = stringResource(R.string.status_not_configured)
    val keyboardIcon = ImageVector.vectorResource(R.drawable.ic_keyboard)
    val systemScopeLabel = stringResource(R.string.status_scope_system)
    val scopeApprovedMsg = stringResource(R.string.snackbar_scope_approved)
    val scopeFailedPattern = stringResource(R.string.snackbar_scope_failed)
    val scopeReadyMsg = stringResource(R.string.snackbar_scope_all_ready)
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

    val frameworkSummary = if (xposed.active) {
        stringResource(R.string.status_framework_meta, xposed.frameworkName, xposed.frameworkVersion, xposed.apiVersion)
    } else {
        stringResource(R.string.status_framework_disconnected)
    }

    val scopeSummary = when {
        !xposed.active -> stringResource(R.string.status_scope_inactive)
        xposed.scope.isEmpty() -> stringResource(R.string.status_scope_empty)
        missingLabels.isNotEmpty() -> stringResource(R.string.status_scope_missing, missingLabels.joinToString("、"))
        else -> stringResource(R.string.status_scope_ready)
    }

    Column {
        SmallTitle(text = stringResource(R.string.section_environment), insideMargin = SectionTitleMargin)
        Card(modifier = Modifier.fillMaxWidth(), insideMargin = CardMargin) {
            ManagerRow(
                title = stringResource(R.string.status_framework),
                summary = frameworkSummary,
                icon = MiuixIcons.Layers,
                onClick = {
                    actions.refresh()
                    showMessage(refreshedMsg)
                },
            )
            ManagerRow(
                title = stringResource(R.string.status_scope),
                summary = scopeSummary,
                icon = MiuixIcons.Settings,
                showChevron = xposed.active && missingScopes.isNotEmpty(),
                onClick = if (!xposed.active) {
                    {
                        actions.refresh()
                        showMessage(refreshedMsg)
                    }
                } else {
                    {
                        if (missingScopes.isEmpty()) {
                            showMessage(scopeReadyMsg)
                        } else {
                            actions.requestScope(missingScopes) { ok, reason ->
                                showMessage(
                                    if (ok) scopeApprovedMsg else String.format(scopeFailedPattern, reason ?: "-"),
                                )
                            }
                        }
                    }
                },
            )
            ManagerRow(
                title = stringResource(R.string.status_autofill),
                summary = status.autofillProvider?.label ?: unset,
                icon = MiuixIcons.Lock,
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
                packageName = status.defaultKeyboard?.packageName,
                showChevron = true,
                onClick = {
                    if (!actions.openInputMethodPicker()) {
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
    actions: ManagerActions,
    onOpenTest: () -> Unit,
    showMessage: (String) -> Unit,
) {
    val keyboardIcon = ImageVector.vectorResource(R.drawable.ic_keyboard)
    val scopeApprovedMsg = stringResource(R.string.snackbar_scope_approved)
    val scopeFailedPattern = stringResource(R.string.snackbar_scope_failed)
    val openFailedMsg = stringResource(R.string.snackbar_open_failed)

    Column {
        SmallTitle(text = stringResource(R.string.ime_supported), insideMargin = SectionTitleMargin)
        Card(modifier = Modifier.fillMaxWidth(), insideMargin = CardMargin) {
            status.keyboards.forEach { keyboard ->
                val pkg = keyboard.app.packageName
                val isCurrent = pkg == status.defaultKeyboard?.packageName
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
                    packageName = pkg,
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
        }

        SmallTitle(text = stringResource(R.string.section_tools), insideMargin = SectionTitleMargin)
        Card(modifier = Modifier.fillMaxWidth(), insideMargin = CardMargin) {
            ManagerRow(
                title = stringResource(R.string.tool_autofill_test),
                summary = stringResource(R.string.tool_autofill_test_summary),
                icon = MiuixIcons.Lock,
                showChevron = true,
                onClick = onOpenTest,
            )
        }
    }
}

@Composable
private fun HookedProcessesSection(
    status: DeviceStatus,
    targets: List<HookedProcessInfo>,
    actions: ManagerActions,
    showMessage: (String) -> Unit,
) {
    val keyboardIcon = ImageVector.vectorResource(R.drawable.ic_keyboard)
    val openFailedMsg = stringResource(R.string.snackbar_open_failed)

    Column {
        SmallTitle(text = stringResource(R.string.section_processes), insideMargin = SectionTitleMargin)
        Card(modifier = Modifier.fillMaxWidth(), insideMargin = CardMargin) {
            targets.forEach { target ->
                val matchedKeyboard = status.keyboards.firstOrNull {
                    target.processName == it.app.packageName || target.processName.startsWith("${it.app.packageName}:")
                }
                val title = when {
                    target.processName == "system" || target.processName == "system_server" || target.processName == "android" ->
                        stringResource(R.string.process_system)
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
                val pkg = matchedKeyboard?.app?.packageName
                ManagerRow(
                    title = title,
                    summary = stringResource(R.string.process_summary, target.pid, target.loadedVersionCode, stateLabel),
                    icon = if (pkg != null) keyboardIcon else MiuixIcons.Tasks,
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
        }
    }
}

@Composable
private fun SettingsSection(
    preferences: UiPreferences,
    backdrop: Backdrop,
    actions: ManagerActions,
    onOpenTest: () -> Unit,
    showMessage: (String) -> Unit,
) {
    val modeOptions = listOf(
        stringResource(R.string.pref_theme_system),
        stringResource(R.string.pref_theme_light),
        stringResource(R.string.pref_theme_dark),
    )
    val iconHiddenMsg = stringResource(R.string.snackbar_icon_hidden)
    val iconShownMsg = stringResource(R.string.snackbar_icon_shown)

    Column {
        SmallTitle(text = stringResource(R.string.section_appearance), insideMargin = SectionTitleMargin)
        Card(modifier = Modifier.fillMaxWidth(), insideMargin = CardMargin) {
            GlassDropdownPreference(
                title = stringResource(R.string.pref_theme_mode),
                items = modeOptions,
                selectedIndex = preferences.themeMode,
                onSelectedIndexChange = { actions.updateThemeMode(it) },
                backdrop = backdrop,
                insideMargin = RowMargin,
                startAction = { PackageIcon(null, MiuixIcons.Theme) },
            )
            SwitchRow(
                title = stringResource(R.string.pref_monet),
                summary = stringResource(R.string.pref_monet_summary),
                icon = MiuixIcons.Tune,
                checked = preferences.monetEnabled,
                onCheckedChange = { actions.updateMonet(it) },
            )
        }

        SmallTitle(text = stringResource(R.string.section_tools), insideMargin = SectionTitleMargin)
        Card(modifier = Modifier.fillMaxWidth(), insideMargin = CardMargin) {
            SwitchRow(
                title = stringResource(R.string.pref_hide_icon),
                summary = stringResource(R.string.pref_hide_icon_summary),
                icon = MiuixIcons.Hide,
                checked = preferences.launcherIconHidden,
                onCheckedChange = { hidden ->
                    actions.updateLauncherIconHidden(hidden)
                    showMessage(if (hidden) iconHiddenMsg else iconShownMsg)
                },
            )
            ManagerRow(
                title = stringResource(R.string.tool_autofill_test),
                summary = stringResource(R.string.tool_autofill_test_summary),
                icon = MiuixIcons.Lock,
                showChevron = true,
                onClick = onOpenTest,
            )
        }
    }
}

@Composable
private fun AutofillTestDialog(
    show: Boolean,
    dark: Boolean,
    onDismiss: () -> Unit,
) {
    val usernameHint = stringResource(R.string.test_hint_username)
    val passwordHint = stringResource(R.string.test_hint_password)
    val textColor = MiuixTheme.colorScheme.onSurface.toArgb()
    val hintColor = MiuixTheme.colorScheme.onSurfaceVariantSummary.toArgb()
    val fieldBg = (if (dark) Color(0xFF232C3B) else Color(0xFFF0F4FA)).toArgb()

    OverlayDialog(
        show = show,
        title = stringResource(R.string.test_dialog_title),
        summary = stringResource(R.string.test_dialog_summary),
        onDismissRequest = onDismiss,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            AndroidView(
                modifier = Modifier.fillMaxWidth(),
                factory = { ctx ->
                    val density = ctx.resources.displayMetrics.density
                    fun dpPx(v: Int): Int = (v * density).toInt()
                    fun buildInput(hintText: String, autofillHint: String, inputType: Int): EditText {
                        return EditText(ctx).apply {
                            hint = hintText
                            this.inputType = inputType
                            isSingleLine = true
                            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                            setPadding(dpPx(14), dpPx(13), dpPx(14), dpPx(13))
                            importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_YES
                            setAutofillHints(autofillHint)
                        }
                    }
                    LinearLayout(ctx).apply {
                        orientation = LinearLayout.VERTICAL
                        importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_YES
                        val userField = buildInput(
                            usernameHint,
                            View.AUTOFILL_HINT_USERNAME,
                            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS,
                        )
                        val passField = buildInput(
                            passwordHint,
                            View.AUTOFILL_HINT_PASSWORD,
                            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD,
                        )
                        addView(
                            userField,
                            LinearLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.WRAP_CONTENT,
                            ).apply { bottomMargin = dpPx(10) },
                        )
                        addView(
                            passField,
                            LinearLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.WRAP_CONTENT,
                            ),
                        )
                    }
                },
                update = { container ->
                    val density = container.resources.displayMetrics.density
                    for (i in 0 until container.childCount) {
                        val child = container.getChildAt(i) as? EditText ?: continue
                        child.setTextColor(textColor)
                        child.setHintTextColor(hintColor)
                        child.background = GradientDrawable().apply {
                            cornerRadius = 14f * density
                            setColor(fieldBg)
                        }
                    }
                },
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = stringResource(R.string.action_close),
                    style = MiuixTheme.textStyles.body1.copy(fontWeight = FontWeight.Medium),
                )
            }
        }
    }
}

@Composable
private fun AboutHeroCard(metadata: AppMetadata, dark: Boolean) {
    val accent = MiuixTheme.colorScheme.primary
    val colors = MiuixTheme.colorScheme
    val heroColor = accent.copy(alpha = if (dark) 0.16f else 0.10f)
    val markRes = if (dark) R.drawable.ic_about_mark_dark else R.drawable.ic_launcher_foreground

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.defaultColors(color = heroColor),
        insideMargin = CardMargin,
    ) {
        Box(
            modifier = Modifier.fillMaxWidth().heightIn(min = 296.dp),
            contentAlignment = Alignment.TopCenter,
        ) {
            Canvas(Modifier.matchParentSize()) {
                val center = Offset(size.width / 2, 92.dp.toPx())
                drawCircle(accent.copy(alpha = 0.14f), 67.dp.toPx(), center, style = Stroke(1.dp.toPx()))
                drawCircle(accent.copy(alpha = 0.09f), 92.dp.toPx(), center, style = Stroke(1.dp.toPx()))
                drawCircle(accent.copy(alpha = 0.05f), 112.dp.toPx(), center, style = Stroke(1.dp.toPx()))
            }
            Column(
                modifier = Modifier.padding(start = 18.dp, end = 18.dp, top = 24.dp, bottom = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Image(
                    painter = painterResource(markRes),
                    contentDescription = null,
                    modifier = Modifier.size(134.dp),
                )
                Text(
                    text = stringResource(R.string.module_name),
                    style = MiuixTheme.textStyles.headline1.copy(fontSize = 24.sp, fontWeight = FontWeight.Bold),
                    textAlign = TextAlign.Center,
                    color = colors.onSurface,
                )
                Text(
                    text = stringResource(R.string.about_version, metadata.versionName, metadata.versionCode),
                    modifier = Modifier.padding(top = 5.dp),
                    style = MiuixTheme.textStyles.body2.copy(fontWeight = FontWeight.Medium),
                    color = colors.onSurfaceVariantSummary,
                )
                Text(
                    text = stringResource(R.string.about_autofill_description),
                    modifier = Modifier.widthIn(max = 286.dp).padding(top = 18.dp),
                    style = MiuixTheme.textStyles.body2,
                    color = colors.onSurfaceVariantSummary,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun AboutDetailsSection(
    status: DeviceStatus,
    metadata: AppMetadata,
    xposed: XposedStatus,
    actions: ManagerActions,
    showMessage: (String) -> Unit,
) {
    val context = LocalContext.current
    val copyLabel = stringResource(R.string.about_copy_package)
    val packageLabel = stringResource(R.string.about_package)
    val copiedMsg = stringResource(R.string.snackbar_package_copied)
    val openFailedMsg = stringResource(R.string.snackbar_open_failed)
    val apiSummary = if (xposed.active) {
        stringResource(R.string.status_framework_meta, xposed.frameworkName, xposed.frameworkVersion, xposed.apiVersion)
    } else {
        stringResource(R.string.about_xposed_api_default)
    }
    val supportedSummary = remember(status.keyboards) {
        status.keyboards.joinToString(" · ") { it.app.label }
    }

    Column {
        SmallTitle(text = stringResource(R.string.section_links), insideMargin = SectionTitleMargin)
        Card(modifier = Modifier.fillMaxWidth(), insideMargin = CardMargin) {
            ManagerRow(
                title = stringResource(R.string.about_coolapk),
                summary = stringResource(R.string.about_coolapk_value),
                icon = MiuixIcons.Contacts,
                showChevron = true,
                onClick = {
                    if (!actions.openUrl(COOLAPK_URL)) {
                        showMessage(openFailedMsg)
                    }
                },
            )
            ManagerRow(
                title = stringResource(R.string.about_github),
                summary = stringResource(R.string.about_github_value),
                icon = MiuixIcons.Link,
                showChevron = true,
                onClick = {
                    if (!actions.openUrl(GITHUB_URL)) {
                        showMessage(openFailedMsg)
                    }
                },
            )
        }

        SmallTitle(text = stringResource(R.string.about_details), insideMargin = SectionTitleMargin)
        Card(modifier = Modifier.fillMaxWidth(), insideMargin = CardMargin) {
            ManagerRow(
                title = stringResource(R.string.about_xposed_api),
                summary = apiSummary,
                icon = MiuixIcons.Layers,
            )
            BasicComponent(
                title = packageLabel,
                summary = metadata.packageName,
                insideMargin = RowMargin,
                startAction = { PackageIcon(null, MiuixIcons.Info) },
                endActions = {
                    Icon(
                        imageVector = MiuixIcons.Copy,
                        contentDescription = copyLabel,
                        modifier = Modifier.size(20.dp),
                        tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                },
                onClickLabel = copyLabel,
                onClick = {
                    context.getSystemService(ClipboardManager::class.java)
                        ?.setPrimaryClip(ClipData.newPlainText(packageLabel, metadata.packageName))
                    showMessage(copiedMsg)
                },
            )
            ManagerRow(
                title = stringResource(R.string.about_targets),
                summary = supportedSummary,
                icon = ImageVector.vectorResource(R.drawable.ic_keyboard),
            )
            ManagerRow(
                title = stringResource(R.string.about_providers),
                summary = stringResource(R.string.about_providers_summary),
                icon = MiuixIcons.Lock,
            )
            ManagerRow(
                title = stringResource(R.string.about_requirement),
                summary = stringResource(R.string.about_requirement_summary),
                icon = MiuixIcons.Settings,
            )
            ManagerRow(
                title = stringResource(R.string.about_license),
                summary = stringResource(R.string.about_license_value),
                icon = MiuixIcons.Notes,
            )
        }

        SmallTitle(text = stringResource(R.string.about_credits), insideMargin = SectionTitleMargin)
        Card(modifier = Modifier.fillMaxWidth(), insideMargin = CardMargin) {
            ManagerRow(
                title = stringResource(R.string.about_credit_lsposed),
                summary = stringResource(R.string.about_credit_lsposed_summary),
                icon = MiuixIcons.Link,
                showChevron = true,
                onClick = {
                    if (!actions.openUrl("https://github.com/libxposed")) {
                        showMessage(openFailedMsg)
                    }
                },
            )
            ManagerRow(
                title = stringResource(R.string.about_credit_miuix),
                summary = stringResource(R.string.about_credit_miuix_summary),
                icon = MiuixIcons.Link,
                showChevron = true,
                onClick = {
                    if (!actions.openUrl("https://github.com/miuix-kotlin-multiplatform/miuix")) {
                        showMessage(openFailedMsg)
                    }
                },
            )
        }
    }
}

@Composable
private fun SwitchRow(
    title: String,
    summary: String,
    icon: ImageVector,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    BasicComponent(
        title = title,
        summary = summary,
        insideMargin = RowMargin,
        startAction = { PackageIcon(null, icon) },
        endActions = {
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
            )
        },
        onClick = { onCheckedChange(!checked) },
    )
}

@Composable
private fun ManagerRow(
    title: String,
    summary: String,
    icon: ImageVector,
    packageName: String? = null,
    showChevron: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    BasicComponent(
        title = title,
        summary = summary,
        insideMargin = RowMargin,
        startAction = { PackageIcon(packageName, icon) },
        endActions = {
            if (showChevron) {
                Icon(
                    imageVector = MiuixIcons.Basic.ArrowRight,
                    contentDescription = null,
                    modifier = Modifier.size(15.dp),
                    tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
        },
        onClick = onClick,
    )
}

@Composable
private fun PackageIcon(packageName: String?, fallback: ImageVector) {
    val context = LocalContext.current
    val bitmap = remember(context, packageName) {
        if (packageName == null) {
            null
        } else {
            try {
                context.packageManager.getApplicationIcon(packageName).toBitmap(96, 96).asImageBitmap()
            } catch (_: PackageManager.NameNotFoundException) {
                null
            }
        }
    }
    if (bitmap != null) {
        Image(bitmap, contentDescription = null, modifier = Modifier.size(26.dp))
    } else {
        Icon(
            imageVector = fallback,
            contentDescription = null,
            modifier = Modifier.size(26.dp),
            tint = MiuixTheme.colorScheme.onSurface,
        )
    }
}
