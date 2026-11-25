package com.mugua.keyfill;

import android.content.ComponentName;

enum KeyboardTarget {
    WETYPE("com.tencent.wetype", ".plugin.hld.WxHldService", ":hld",
            "com.tencent.wetype.plugin.hld.candidate.ImeCandidateView"),
    XIAO_AI("com.xiaomi.type", "com.mi.ime.MiInputMethodService", "", "mb.u0");

    final String packageName;
    final String serviceId;
    final String processName;
    final String containerClass;

    KeyboardTarget(String packageName, String serviceName, String processSuffix, String containerClass) {
        this.packageName = packageName;
        serviceId = ComponentName.createRelative(packageName, serviceName).flattenToShortString();
        processName = packageName + processSuffix;
        this.containerClass = containerClass;
    }
}
