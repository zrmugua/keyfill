package com.mugua.keyfill

import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.libxposed.service.HookedTarget
import io.github.libxposed.service.XposedService
import io.github.libxposed.service.XposedServiceHelper
import java.util.concurrent.atomic.AtomicBoolean

internal data class HookedProcessInfo(
    val processName: String,
    val pid: Int,
    val state: HookedTarget.State,
    val loadedVersionCode: Long,
)

internal data class XposedStatus(
    val active: Boolean = false,
    val frameworkName: String = "",
    val frameworkVersion: String = "",
    val apiVersion: Int = 0,
    val scope: Set<String> = emptySet(),
    val runningTargets: List<HookedProcessInfo> = emptyList(),
) {
    val hasSystemScope: Boolean
        get() = "android" in scope || "system" in scope

    val hasStaleTarget: Boolean
        get() = runningTargets.any { it.state == HookedTarget.State.STALE }

    fun isScoped(packageName: String): Boolean = packageName in scope
}

internal object XposedServiceBridge {
    private val initialized = AtomicBoolean(false)
    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile
    private var liveService: XposedService? = null

    var status by mutableStateOf(XposedStatus())
        private set

    fun ensureRegistered() {
        if (!initialized.compareAndSet(false, true)) {
            refresh()
            return
        }
        XposedServiceHelper.registerListener(object : XposedServiceHelper.OnServiceListener {
            override fun onServiceBind(service: XposedService) {
                liveService = service
                updateFromService(service)
            }

            override fun onServiceDied(service: XposedService) {
                if (liveService === service) {
                    liveService = null
                    mainHandler.post { status = XposedStatus() }
                }
            }
        })
    }

    fun refresh() {
        val service = liveService
        if (service == null) {
            mainHandler.post { status = XposedStatus() }
        } else {
            updateFromService(service)
        }
    }

    fun requestScope(packages: List<String>, onResult: (Boolean, String?) -> Unit) {
        val service = liveService
        if (service == null || packages.isEmpty()) {
            mainHandler.post { onResult(false, null) }
            return
        }
        try {
            service.requestScope(packages, object : XposedService.OnScopeEventListener {
                override fun onScopeRequestApproved(approved: MutableList<String>) {
                    updateFromService(service)
                    mainHandler.post { onResult(true, null) }
                }

                override fun onScopeRequestFailed(message: String) {
                    updateFromService(service)
                    mainHandler.post { onResult(false, message) }
                }
            })
        } catch (failure: RuntimeException) {
            mainHandler.post { onResult(false, failure.message) }
        }
    }

    private fun updateFromService(service: XposedService) {
        val snapshot = try {
            val apiVersion = service.apiVersion
            val frameworkName = service.frameworkName
            val frameworkVersion = service.frameworkVersion
            val scope = service.scope.toSet()
            val targets = if (apiVersion >= XposedService.API_102) {
                runCatching {
                    service.runningTargets.map { target ->
                        HookedProcessInfo(
                            processName = target.processName,
                            pid = target.pid,
                            state = target.state,
                            loadedVersionCode = target.loadedVersionCode,
                        )
                    }
                }.getOrDefault(emptyList())
            } else {
                emptyList()
            }
            XposedStatus(
                active = true,
                frameworkName = frameworkName,
                frameworkVersion = frameworkVersion,
                apiVersion = apiVersion,
                scope = scope,
                runningTargets = targets,
            )
        } catch (_: RuntimeException) {
            XposedStatus()
        }
        mainHandler.post { status = snapshot }
    }
}
