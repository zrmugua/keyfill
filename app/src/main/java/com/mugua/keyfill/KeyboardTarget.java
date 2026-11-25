package com.mugua.keyfill;

import android.content.ComponentName;

import androidx.annotation.StringRes;

enum KeyboardTarget {
    WETYPE("com.tencent.wetype", ".plugin.hld.WxHldService", ":hld",
            "com.tencent.wetype.plugin.hld.candidate.ImeCandidateView", R.string.ime_wechat),
    XIAO_AI("com.xiaomi.type", "com.mi.ime.MiInputMethodService", "",
            "mb.u0", R.string.ime_xiaoai);

    final String packageName;
    final String serviceId;
    final String processName;
    final String containerClass;
    @StringRes
    final int labelRes;

    KeyboardTarget(String packageName, String serviceName, String processSuffix,
            String containerClass, @StringRes int labelRes) {
        this.packageName = packageName;
        serviceId = ComponentName.createRelative(packageName, serviceName).flattenToShortString();
        processName = packageName + processSuffix;
        this.containerClass = containerClass;
        this.labelRes = labelRes;
    }
}
