package com.mugua.keyfill

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.autofill.AutofillManager
import android.view.inputmethod.InputMethodManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import java.util.concurrent.ConcurrentHashMap

internal object AppIconCache {
    private val cache = ConcurrentHashMap<String, ImageBitmap>()

    fun preload(pm: PackageManager, packageName: String?) {
        if (packageName.isNullOrBlank() || cache.containsKey(packageName)) return
        try {
            cache[packageName] = pm.getApplicationIcon(packageName).toBitmap(96, 96).asImageBitmap()
        } catch (_: PackageManager.NameNotFoundException) {
            // Package is not installed.
        }
    }

    fun get(pm: PackageManager, packageName: String?): ImageBitmap? {
        if (packageName.isNullOrBlank()) return null
        return cache[packageName] ?: run {
            preload(pm, packageName)
            cache[packageName]
        }
    }
}

internal data class AppIdentity(val label: String, val packageName: String)

internal data class KeyboardStatus(val app: AppIdentity, val installed: Boolean)

internal data class AppMetadata(
    val packageName: String,
    val versionName: String,
    val versionCode: Long,
    val deviceName: String,
    val marketName: String,
    val androidRelease: String,
    val sdkInt: Int,
    val osVersion: String,
)

internal data class UiPreferences(
    val themeMode: Int = 0, // 0: System, 1: Light, 2: Dark
    val monetEnabled: Boolean = false,
    val keyColor: Int = 0, // 0: System wallpaper, non-zero: ARGB seed color
    val launcherIconHidden: Boolean = false,
)

internal data class DeviceStatus(
    val autofillProvider: AppIdentity?,
    val defaultKeyboard: AppIdentity?,
    val keyboards: List<KeyboardStatus>,
) {
    val hasSupportedKeyboard: Boolean
        get() = keyboards.any { it.app.packageName == defaultKeyboard?.packageName }
}

internal interface ManagerActions {
    fun refresh(): Boolean
    fun openAutofillSettings(): Boolean
    fun openInputMethodPicker(): Boolean
    fun openInputMethodSettings(): Boolean
    fun openAppDetails(targetPackage: String): Boolean
    fun requestScope(packages: List<String>, onResult: (Boolean, String?) -> Unit)
    fun openUrl(url: String): Boolean
    fun updateThemeMode(mode: Int)
    fun updateMonet(enabled: Boolean)
    fun updateKeyColor(color: Int)
    fun updateLauncherIconHidden(hidden: Boolean)
}

class MainActivity : ComponentActivity(), ManagerActions {
    private var deviceStatus by mutableStateOf(DeviceStatus(null, null, emptyList()))
    private var uiPrefs by mutableStateOf(UiPreferences())

    private val prefs by lazy { getSharedPreferences("keyfill_prefs", Context.MODE_PRIVATE) }
    private val launcherAlias by lazy { ComponentName(this, "$packageName.LauncherAlias") }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        XposedServiceBridge.ensureRegistered()
        deviceStatus = readDeviceStatus()
        uiPrefs = readPreferences()
        val info = packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0L))
        val globalName = Settings.Global.getString(contentResolver, Settings.Global.DEVICE_NAME).orEmpty().trim()
        val market = sysProp("ro.product.marketname").ifBlank { sysProp("ro.avium.device.codename") }
        val devName = globalName
            .ifBlank { market }
            .ifBlank { sysProp("persist.sys.device_name") }
            .ifBlank { sysProp("bluetooth.device.default_name") }
            .ifBlank { "${android.os.Build.BRAND} ${android.os.Build.MODEL}".trim() }
        val hyperOsInc = sysProp("ro.mi.os.version.incremental")
        val miuiVer = sysProp("ro.miui.ui.version.name")
        val cleanDisplay = android.os.Build.DISPLAY.replace("release-keys", "").trim()
        val osVer = when {
            hyperOsInc.startsWith("OS") -> "HyperOS ${hyperOsInc.removePrefix("OS")}"
            hyperOsInc.isNotBlank() -> hyperOsInc
            miuiVer.isNotBlank() -> "MIUI ${sysProp("ro.build.version.incremental").ifBlank { miuiVer }}"
            cleanDisplay.isNotBlank() -> cleanDisplay
            else -> sysProp("ro.build.version.incremental").ifBlank { "Android ${android.os.Build.VERSION.RELEASE}" }
        }
        val metadata = AppMetadata(
            packageName = packageName,
            versionName = info.versionName.orEmpty(),
            versionCode = info.longVersionCode,
            deviceName = devName,
            marketName = "${android.os.Build.MODEL} (${android.os.Build.DEVICE})",
            androidRelease = android.os.Build.VERSION.RELEASE,
            sdkInt = android.os.Build.VERSION.SDK_INT,
            osVersion = osVer,
        )
        setContent {
            ManagerApp(
                status = deviceStatus,
                xposed = XposedServiceBridge.status,
                metadata = metadata,
                preferences = uiPrefs,
                actions = this,
            )
        }
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            refresh()
        }
    }

    override fun refresh(): Boolean {
        deviceStatus = readDeviceStatus()
        uiPrefs = readPreferences()
        XposedServiceBridge.refresh()
        return true
    }

    override fun openAutofillSettings(): Boolean {
        val serviceIntent = Intent("android.service.autofill.AutofillService")
        val targetPackage = deviceStatus.autofillProvider?.packageName
            ?: packageManager.queryIntentServices(serviceIntent, PackageManager.ResolveInfoFlags.of(0L))
                .firstOrNull()?.serviceInfo?.packageName
        val candidates = buildList {
            if (targetPackage != null) {
                add(Intent(Settings.ACTION_REQUEST_SET_AUTOFILL_SERVICE, Uri.parse("package:$targetPackage")))
            }
            add(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
            add(Intent(Settings.ACTION_SETTINGS))
        }
        return launchFirstAvailable(candidates)
    }

    override fun openInputMethodPicker(): Boolean {
        val imm = getSystemService(InputMethodManager::class.java)
        if (imm != null) {
            imm.showInputMethodPicker()
            return true
        }
        return openInputMethodSettings()
    }

    override fun openInputMethodSettings(): Boolean {
        return launchFirstAvailable(
            listOf(
                Intent(Settings.ACTION_INPUT_METHOD_SETTINGS),
                Intent(Settings.ACTION_SETTINGS),
            ),
        )
    }

    override fun openAppDetails(targetPackage: String): Boolean {
        return launchFirstAvailable(
            listOf(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$targetPackage")),
            ),
        )
    }

    override fun requestScope(packages: List<String>, onResult: (Boolean, String?) -> Unit) {
        XposedServiceBridge.requestScope(packages, onResult)
    }

    override fun openUrl(url: String): Boolean {
        return launchFirstAvailable(listOf(Intent(Intent.ACTION_VIEW, Uri.parse(url))))
    }

    override fun updateThemeMode(mode: Int) {
        val clamped = mode.coerceIn(0, 2)
        prefs.edit().putInt(KEY_THEME_MODE, clamped).apply()
        uiPrefs = uiPrefs.copy(themeMode = clamped)
    }

    override fun updateMonet(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_MONET, enabled).apply()
        uiPrefs = uiPrefs.copy(monetEnabled = enabled)
    }

    override fun updateKeyColor(color: Int) {
        val normalized = if (color == 0) 0 else (color or -0x1000000)
        prefs.edit().putInt(KEY_COLOR, normalized).apply()
        uiPrefs = uiPrefs.copy(keyColor = normalized)
    }

    override fun updateLauncherIconHidden(hidden: Boolean) {
        val state = if (hidden) {
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        } else {
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        }
        packageManager.setComponentEnabledSetting(launcherAlias, state, PackageManager.DONT_KILL_APP)
        uiPrefs = uiPrefs.copy(launcherIconHidden = hidden)
    }

    private fun readPreferences(): UiPreferences {
        val componentState = packageManager.getComponentEnabledSetting(launcherAlias)
        val hidden = componentState == PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        val rawColor = prefs.getInt(KEY_COLOR, 0)
        return UiPreferences(
            themeMode = prefs.getInt(KEY_THEME_MODE, 0).coerceIn(0, 2),
            monetEnabled = prefs.getBoolean(KEY_MONET, false),
            keyColor = if (rawColor == 0) 0 else (rawColor or -0x1000000),
            launcherIconHidden = hidden,
        )
    }

    private fun launchFirstAvailable(intents: List<Intent>): Boolean {
        for (intent in intents) {
            try {
                startActivity(intent)
                return true
            } catch (_: ActivityNotFoundException) {
                // Try the next fallback destination.
            } catch (_: SecurityException) {
                // Try the next fallback destination.
            }
        }
        return false
    }

    private fun readDeviceStatus(): DeviceStatus {
        val keyboards = KeyboardTarget.values().map { target ->
            val identity = AppIdentity(getString(target.labelRes), target.packageName)
            KeyboardStatus(identity, findApp(target.packageName) != null)
        }

        val defaultPackage = ComponentName.unflattenFromString(
            Settings.Secure.getString(contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD).orEmpty(),
        )?.packageName
        val defaultKeyboard = defaultPackage?.let { name ->
            keyboards.firstOrNull { it.app.packageName == name }?.app
                ?: AppIdentity(appLabel(name) ?: getString(R.string.ime_generic), name)
        }

        val autofill = getSystemService(AutofillManager::class.java)
        val autofillPackage = autofill?.autofillServiceComponentName?.packageName
        val provider = autofillPackage?.takeIf { autofill.isEnabled }?.let { name ->
            val label = when (name) {
                "com.onepassword.android" -> "1Password"
                "com.x8bit.bitwarden" -> "Bitwarden"
                else -> appLabel(name) ?: getString(R.string.autofill_generic)
            }
            AppIdentity(label, name)
        }
        for (keyboard in keyboards) {
            if (keyboard.installed) {
                AppIconCache.preload(packageManager, keyboard.app.packageName)
            }
        }
        AppIconCache.preload(packageManager, defaultKeyboard?.packageName)
        AppIconCache.preload(packageManager, provider?.packageName)
        return DeviceStatus(provider, defaultKeyboard, keyboards)
    }

    private fun appLabel(name: String): String? = findApp(name)?.let { app ->
        packageManager.getApplicationLabel(app).toString().takeIf { it.isNotBlank() && it != name }
    }

    private fun findApp(name: String): ApplicationInfo? = try {
        packageManager.getApplicationInfo(name, PackageManager.ApplicationInfoFlags.of(0L))
    } catch (_: PackageManager.NameNotFoundException) {
        null
    }

    private fun sysProp(key: String): String = try {
        val cls = Class.forName("android.os.SystemProperties")
        (cls.getMethod("get", String::class.java, String::class.java).invoke(null, key, "") as? String).orEmpty()
    } catch (_: ReflectiveOperationException) {
        ""
    }

    private companion object {
        const val KEY_THEME_MODE = "theme_mode"
        const val KEY_MONET = "monet_enabled"
        const val KEY_COLOR = "key_color"
    }
}
