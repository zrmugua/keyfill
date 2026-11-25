package com.mugua.keyfill

import android.content.ActivityNotFoundException
import android.content.ComponentName
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

internal data class AppIdentity(val label: String, val packageName: String)

internal data class KeyboardStatus(val app: AppIdentity, val installed: Boolean)

internal data class AppMetadata(
    val packageName: String,
    val versionName: String,
    val versionCode: Long,
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
    fun requestScope(packages: List<String>, onResult: (Boolean, String?) -> Unit)
    fun openUrl(url: String): Boolean
}

class MainActivity : ComponentActivity(), ManagerActions {
    private var deviceStatus by mutableStateOf(DeviceStatus(null, null, emptyList()))

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        XposedServiceBridge.ensureRegistered()
        deviceStatus = readDeviceStatus()
        val info = packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0L))
        val metadata = AppMetadata(
            packageName = packageName,
            versionName = info.versionName.orEmpty(),
            versionCode = info.longVersionCode,
        )
        setContent {
            ManagerApp(
                status = deviceStatus,
                xposed = XposedServiceBridge.status,
                metadata = metadata,
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

    override fun requestScope(packages: List<String>, onResult: (Boolean, String?) -> Unit) {
        XposedServiceBridge.requestScope(packages, onResult)
    }

    override fun openUrl(url: String): Boolean {
        return launchFirstAvailable(listOf(Intent(Intent.ACTION_VIEW, Uri.parse(url))))
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
        val keyboards = listOf(
            AppIdentity(getString(R.string.ime_wechat), KeyboardTarget.WETYPE.packageName),
            AppIdentity(getString(R.string.ime_xiaoai), KeyboardTarget.XIAO_AI.packageName),
        ).map { KeyboardStatus(it, findApp(it.packageName) != null) }

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
}
