package com.mugua.keyfill;

import android.app.Activity;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.View;
import android.widget.CompoundButton;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Clean-room HyperOS Passkey (Credential Manager) compatibility hooks.
 *
 * Restores standard third-party passkey provider selection on Xiaomi HyperOS (Android 14+)
 * using pure reflection without native libraries or static-final field mutation.
 */
final class HyperPasskeyHooks {
    static final String SETTINGS_PACKAGE = "com.android.settings";
    static final String SECURITY_CENTER_PACKAGE = "com.miui.securitycenter";
    static final String SCANNER_PACKAGE = "com.xiaomi.scanner";
    static final String GMS_PACKAGE = "com.google.android.gms";
    static final String MIUI_PASSWORDS_PACKAGE = "com.miui.passwords";
    static final String MIUI_CONTENT_CATCHER_PACKAGE = "com.miui.contentcatcher";

    static final String GMS_CHOOSER_COMPONENT =
            "com.google.android.gms/.identitycredentials.ui.CredentialChooserActivity";
    static final String GMS_HYBRID_SERVICE =
            "com.google.android.gms/.auth.api.credentials.credman.service.RemoteService";

    private static final int PROVIDER_FILTER_USER_PROVIDERS_ONLY = 2;

    private HyperPasskeyHooks() {
    }

    static boolean isHyperOsPasskeySupported() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            return false;
        }
        return !readSystemProperty("ro.mi.os.version.name").isEmpty()
                || !readSystemProperty("ro.miui.ui.version.name").isEmpty();
    }

    static void installSystemServer(AutofillModule module, ClassLoader systemLoader) {
        if (!isHyperOsPasskeySupported()) {
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            try {
                hookIntentFactory(module, systemLoader);
                AutofillModule.log("HyperOS passkey selector hook ready");
            } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
                AutofillModule.log("Passkey selector hook unavailable: " + failure.getClass().getSimpleName());
            }
        }
        try {
            hookRequestSession(module, systemLoader);
            AutofillModule.log("HyperOS passkey hybrid-service hook ready");
        } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
            AutofillModule.log("Passkey hybrid hook unavailable: " + failure.getClass().getSimpleName());
        }
    }

    static void installPackage(AutofillModule module, String packageName, ClassLoader classLoader) {
        if (!isHyperOsPasskeySupported()) {
            return;
        }
        switch (packageName) {
            case SETTINGS_PACKAGE -> installSettingsHooks(module, classLoader);
            case SECURITY_CENTER_PACKAGE -> installSecurityCenterHooks(module, classLoader);
            case SCANNER_PACKAGE -> installScannerHooks(module, classLoader);
            default -> {
            }
        }
    }

    private static void hookIntentFactory(AutofillModule module, ClassLoader systemLoader)
            throws ReflectiveOperationException {
        Class<?> factoryClass = Class.forName("android.credentials.selection.IntentFactory", false, systemLoader);
        Class<?> builderClass = Class.forName(
                "android.credentials.selection.IntentCreationResult$Builder", false, systemLoader);
        Class<?> statusEnumClass = Class.forName(
                "android.credentials.selection.IntentCreationResult$OemUiUsageStatus", false, systemLoader);
        Method setOemPkg = builderClass.getMethod("setOemUiPackageName", String.class);
        Method setOemStatus = builderClass.getMethod("setOemUiUsageStatus", statusEnumClass);
        Object statusSuccess = findEnumConstant(statusEnumClass, "SUCCESS");
        if (statusSuccess == null) {
            throw new NoSuchFieldException("OemUiUsageStatus.SUCCESS not found");
        }

        deoptimizeByPrefix(module, factoryClass, "createCredentialSelectorIntent");

        int hooked = 0;
        for (Method method : factoryClass.getDeclaredMethods()) {
            if (!"getOemOverrideComponentName".equals(method.getName())) {
                continue;
            }
            Class<?>[] params = method.getParameterTypes();
            if (params.length < 2 || params[0] != Context.class || params[1] != builderClass) {
                continue;
            }
            method.setAccessible(true);
            module.deoptimize(method);
            module.hook(method).intercept(chain -> {
                Object existing = chain.proceed();
                if (existing instanceof ComponentName existingCn
                        && GMS_PACKAGE.equals(existingCn.getPackageName())) {
                    return existing;
                }
                Object ctxObj = chain.getArg(0);
                Object builderObj = chain.getArg(1);
                if (ctxObj instanceof Context context && builderObj != null) {
                    ComponentName gmsChooser = resolveEnabledGmsChooser(context);
                    if (gmsChooser != null) {
                        try {
                            setOemPkg.invoke(builderObj, gmsChooser.getPackageName());
                            setOemStatus.invoke(builderObj, statusSuccess);
                            return gmsChooser;
                        } catch (ReflectiveOperationException | RuntimeException ignored) {
                            // Fall back to the original component if reflective builder update fails.
                        }
                    }
                }
                return existing;
            });
            hooked++;
        }
        if (hooked == 0) {
            throw new NoSuchMethodException("getOemOverrideComponentName not found");
        }
    }

    private static void hookRequestSession(AutofillModule module, ClassLoader systemLoader)
            throws ReflectiveOperationException {
        Class<?> sessionClass = Class.forName("com.android.server.credentials.RequestSession", false, systemLoader);
        Field hybridField = AutofillModule.accessibleField(sessionClass, "mHybridService");
        Field contextField = AutofillModule.accessibleField(sessionClass, "mContext");

        deoptimizeByName(module, systemLoader, "com.android.server.credentials.ProviderGetSession", "createNewSession");
        deoptimizeByName(module, systemLoader, "com.android.server.credentials.ProviderCreateSession", "createNewSession");

        int hooked = 0;
        for (Constructor<?> ctor : sessionClass.getDeclaredConstructors()) {
            ctor.setAccessible(true);
            module.hook(ctor).intercept(chain -> {
                Object result = chain.proceed();
                Object self = chain.getThisObject();
                if (self != null) {
                    try {
                        Object ctxObj = contextField.get(self);
                        if (ctxObj instanceof Context context && isGmsHybridServiceAvailable(context)) {
                            Object current = hybridField.get(self);
                            if (current == null
                                    || current.toString().isEmpty()
                                    || isXiaomiCredentialString(current.toString())) {
                                hybridField.set(self, GMS_HYBRID_SERVICE);
                            }
                        }
                    } catch (ReflectiveOperationException | RuntimeException failure) {
                        AutofillModule.log("RequestSession mHybridService update skipped: "
                                + failure.getClass().getSimpleName());
                    }
                }
                return result;
            });
            hooked++;
        }
        if (hooked == 0) {
            throw new NoSuchMethodException("RequestSession constructors not found");
        }
    }

    private static void installSettingsHooks(AutofillModule module, ClassLoader classLoader) {
        try {
            hookCombinedProviderFilter(module, classLoader);
            AutofillModule.log("Settings credential provider filter hook ready");
        } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
            AutofillModule.log("Settings filter hook unavailable: " + failure.getClass().getSimpleName());
        }
        try {
            hookDefaultCombinedPicker(module, classLoader);
            AutofillModule.log("Settings default picker hook ready");
        } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
            AutofillModule.log("Settings picker hook unavailable: " + failure.getClass().getSimpleName());
        }
        try {
            hookProviderSettingsClick(module, classLoader);
            AutofillModule.log("Settings provider details click hook ready");
        } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
            AutofillModule.log("Settings provider click hook unavailable: " + failure.getClass().getSimpleName());
        }
        try {
            hookCombiPreferenceSwitchFallback(module, classLoader);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
            AutofillModule.log("Settings switch fallback skipped: " + failure.getClass().getSimpleName());
        }
    }

    private static void hookCombinedProviderFilter(AutofillModule module, ClassLoader classLoader)
            throws ReflectiveOperationException {
        Class<?> controllerClass = Class.forName(
                "com.android.settings.applications.credentials.DefaultCombinedPreferenceController",
                false, classLoader);
        Class<?> credManClass = Class.forName("android.credentials.CredentialManager", false, classLoader);
        Method target = controllerClass.getDeclaredMethod("getCombinedProviderInfos", credManClass, int.class);
        Method getServices = credManClass.getMethod("getCredentialProviderServices", int.class, int.class);
        target.setAccessible(true);
        module.deoptimize(target);
        deoptimizeByName(module, classLoader,
                "com.android.settings.applications.credentials.DefaultCombinedPreferenceController", "getAllProviders");
        deoptimizeByName(module, classLoader,
                "com.android.settings.applications.credentials.DefaultCombinedPicker", "getAllProviders");
        deoptimizeByName(module, classLoader,
                "com.android.settings.applications.credentials.CredentialManagerPreferenceController", "update");
        deoptimizeByName(module, classLoader,
                "com.android.settings.applications.credentials.CredentialManagerPreferenceController", "updateFromExternal");
        module.hook(target).intercept(chain -> {
            Object credMan = chain.getArg(0);
            Object userIdObj = chain.getArg(1);
            if (credMan != null && userIdObj instanceof Integer userId) {
                try {
                    Object list = getServices.invoke(credMan, userId, PROVIDER_FILTER_USER_PROVIDERS_ONLY);
                    if (list instanceof List<?>) {
                        return list;
                    }
                } catch (ReflectiveOperationException | RuntimeException ignored) {
                    // Fall back to original implementation if framework method differs.
                }
            }
            return chain.proceed();
        });
    }

    private static void hookDefaultCombinedPicker(AutofillModule module, ClassLoader classLoader)
            throws ReflectiveOperationException {
        Class<?> pickerClass = Class.forName(
                "com.android.settings.applications.credentials.DefaultCombinedPicker", false, classLoader);
        Class<?> combinedInfoClass = Class.forName(
                "com.android.settings.applications.credentials.CombinedProviderInfo", false, classLoader);
        Method setDefaultKey = pickerClass.getDeclaredMethod("setDefaultKey", String.class);
        Method getUser = findMethodInHierarchy(pickerClass, "getUser");
        Method getAllProviders = findMethodInHierarchy(pickerClass, "getAllProviders", int.class);
        Method setProviders = findMethodInHierarchy(pickerClass, "setProviders", String.class, List.class);
        Method getActivity = findMethodInHierarchy(pickerClass, "getActivity");
        if (getUser == null || getAllProviders == null || setProviders == null) {
            throw new NoSuchMethodException("DefaultCombinedPicker helper methods not found");
        }
        Method getAppInfo = combinedInfoClass.getMethod("getApplicationInfo");
        Method getCredInfos = combinedInfoClass.getMethod("getCredentialProviderInfos");
        Method getAutofillInfo = combinedInfoClass.getMethod("getAutofillServiceInfo");
        Method getCredServiceInfo = Class.forName("android.credentials.CredentialProviderInfo", false, classLoader)
                .getMethod("getServiceInfo");
        Method getAutofillServiceInfo = Class.forName("android.service.autofill.AutofillServiceInfo", false, classLoader)
                .getMethod("getServiceInfo");

        setDefaultKey.setAccessible(true);
        getUser.setAccessible(true);
        getAllProviders.setAccessible(true);
        setProviders.setAccessible(true);
        module.deoptimize(setDefaultKey);

        module.hook(setDefaultKey).intercept(chain -> {
            Object picker = chain.getThisObject();
            String key = chain.getArg(0) instanceof String s ? s : null;
            if (picker == null) {
                return chain.proceed();
            }
            try {
                int userId = (Integer) getUser.invoke(picker);
                Object providersObj = getAllProviders.invoke(picker, userId);
                if (!(providersObj instanceof List<?> providers)) {
                    return chain.proceed();
                }
                Object matched = null;
                for (Object item : providers) {
                    if (item == null) {
                        continue;
                    }
                    Object appInfo = getAppInfo.invoke(item);
                    if (appInfo instanceof ApplicationInfo ai && ai.packageName != null && ai.packageName.equals(key)) {
                        matched = item;
                        break;
                    }
                }
                if (matched == null) {
                    setProviders.invoke(picker, null, Collections.emptyList());
                } else {
                    List<String> credComponents = new ArrayList<>();
                    Object credListObj = getCredInfos.invoke(matched);
                    if (credListObj instanceof List<?> credInfos) {
                        for (Object credInfo : credInfos) {
                            if (credInfo == null) {
                                continue;
                            }
                            Object si = getCredServiceInfo.invoke(credInfo);
                            if (si instanceof ServiceInfo serviceInfo) {
                                credComponents.add(flattenServiceComponent(serviceInfo));
                            }
                        }
                    }
                    String autofillComponent = null;
                    Object afObj = getAutofillInfo.invoke(matched);
                    if (afObj != null) {
                        Object si = getAutofillServiceInfo.invoke(afObj);
                        if (si instanceof ServiceInfo serviceInfo) {
                            autofillComponent = flattenServiceComponent(serviceInfo);
                        }
                    }
                    setProviders.invoke(picker, autofillComponent, credComponents);
                }
                if (getActivity != null) {
                    Object activityObj = getActivity.invoke(picker);
                    if (activityObj instanceof Activity activity) {
                        Intent intent = activity.getIntent();
                        String requestedPkg = intent == null ? null : intent.getStringExtra("package_name");
                        if (requestedPkg != null) {
                            activity.setResult(key != null && key.startsWith(requestedPkg)
                                    ? Activity.RESULT_OK : Activity.RESULT_CANCELED);
                            activity.finish();
                        }
                    }
                }
                return true;
            } catch (ReflectiveOperationException | RuntimeException failure) {
                AutofillModule.log("setDefaultKey fallback to original: " + failure.getClass().getSimpleName());
                return chain.proceed();
            }
        });
    }

    private static void hookProviderSettingsClick(AutofillModule module, ClassLoader classLoader)
            throws ReflectiveOperationException {
        Class<?> controllerClass = Class.forName(
                "com.android.settings.applications.credentials.CredentialManagerPreferenceController",
                false, classLoader);
        Class<?> listenerInterface = Class.forName(
                "com.android.settings.applications.credentials.CredentialManagerPreferenceController$CombiPreference$OnCombiPreferenceClickListener",
                false, classLoader);
        Class<?> combinedInfoClass = Class.forName(
                "com.android.settings.applications.credentials.CombinedProviderInfo", false, classLoader);
        Method launchSettings = findLaunchSettingsMethod(combinedInfoClass);
        Method getUser = findMethodInHierarchy(controllerClass, "getUser");
        Field contextField = findFieldInHierarchy(controllerClass, "mContext");
        if (launchSettings == null || getUser == null || contextField == null) {
            return;
        }
        launchSettings.setAccessible(true);
        getUser.setAccessible(true);
        contextField.setAccessible(true);

        List<Class<?>> candidates = new ArrayList<>();
        Collections.addAll(candidates, controllerClass.getDeclaredClasses());
        for (int index = 1; index <= 8; index++) {
            try {
                Class<?> anon = Class.forName(controllerClass.getName() + "$" + index, false, classLoader);
                if (!candidates.contains(anon)) {
                    candidates.add(anon);
                }
            } catch (ClassNotFoundException ignored) {
                break;
            }
        }

        int hooked = 0;
        for (Class<?> inner : candidates) {
            if (!listenerInterface.isAssignableFrom(inner) || inner.isInterface()) {
                continue;
            }
            Method onLeftSideClicked;
            try {
                onLeftSideClicked = inner.getDeclaredMethod("onLeftSideClicked");
            } catch (NoSuchMethodException ignored) {
                continue;
            }
            Field outerField = findFieldOfType(inner, controllerClass);
            Field pkgField = findNamedOrTypedField(inner, "val$packageName", String.class);
            Field actField = findNamedOrTypedField(inner, "val$settingsActivity", CharSequence.class);
            if (outerField == null || pkgField == null || actField == null) {
                continue;
            }
            onLeftSideClicked.setAccessible(true);
            module.deoptimize(onLeftSideClicked);
            module.hook(onLeftSideClicked).intercept(chain -> {
                Object listener = chain.getThisObject();
                if (listener != null) {
                    try {
                        Object outer = outerField.get(listener);
                        if (outer != null) {
                            Object ctx = contextField.get(outer);
                            CharSequence pkg = (CharSequence) pkgField.get(listener);
                            CharSequence settingsAct = (CharSequence) actField.get(listener);
                            int userId = (Integer) getUser.invoke(outer);
                            if (ctx instanceof Context context && !TextUtils.isEmpty(pkg)) {
                                launchSettings.invoke(null, context, pkg, settingsAct, userId);
                                return null;
                            }
                        }
                    } catch (ReflectiveOperationException | RuntimeException ignored) {
                        // Fall through to original behavior.
                    }
                }
                return chain.proceed();
            });
            hooked++;
        }
        if (hooked == 0) {
            throw new NoSuchMethodException("OnCombiPreferenceClickListener implementation not found");
        }
    }

    private static void hookCombiPreferenceSwitchFallback(AutofillModule module, ClassLoader classLoader)
            throws ReflectiveOperationException {
        Class<?> combiClass = Class.forName(
                "com.android.settings.applications.credentials.CredentialManagerPreferenceController$CombiPreference",
                false, classLoader);
        Class<?> holderClass = Class.forName("androidx.preference.PreferenceViewHolder", false, classLoader);
        Method onBind = combiClass.getDeclaredMethod("onBindViewHolder", holderClass);
        Field switchField = AutofillModule.accessibleField(combiClass, "mSwitch");
        Field checkedField = AutofillModule.accessibleField(combiClass, "mChecked");
        Field listenerField = AutofillModule.accessibleField(combiClass, "mListener");
        Field itemViewField = findFieldInHierarchy(holderClass, "itemView");
        Class<?> idClass = Class.forName("com.android.settingslib.R$id", false, classLoader);
        int switchWidgetId = AutofillModule.accessibleField(idClass, "switchWidget").getInt(null);
        Method updateDesc = null;
        try {
            updateDesc = combiClass.getDeclaredMethod("maybeUpdateContentDescription");
            updateDesc.setAccessible(true);
        } catch (NoSuchMethodException ignored) {
        }
        Method finalUpdateDesc = updateDesc;

        onBind.setAccessible(true);
        module.hook(onBind).intercept(chain -> {
            Object result = chain.proceed();
            Object pref = chain.getThisObject();
            Object holder = chain.getArg(0);
            if (pref == null || holder == null || itemViewField == null) {
                return result;
            }
            try {
                if (switchField.get(pref) != null) {
                    return result;
                }
                Object itemViewObj = itemViewField.get(holder);
                if (!(itemViewObj instanceof View itemView)) {
                    return result;
                }
                View candidate = itemView.findViewById(switchWidgetId);
                if (candidate instanceof CompoundButton switchView) {
                    switchView.setChecked(checkedField.getBoolean(pref));
                    Object listener = listenerField.get(pref);
                    if (listener instanceof CompoundButton.OnCheckedChangeListener checkedListener) {
                        switchView.setOnCheckedChangeListener(checkedListener);
                    }
                    switchField.set(pref, switchView);
                    if (finalUpdateDesc != null) {
                        finalUpdateDesc.invoke(pref);
                    }
                }
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                // Leave original binding untouched.
            }
            return result;
        });
    }

    private static void installSecurityCenterHooks(AutofillModule module, ClassLoader classLoader) {
        int hooked = 0;
        try {
            Method putString = Settings.Secure.class.getDeclaredMethod(
                    "putString", ContentResolver.class, String.class, String.class);
            module.hook(putString).intercept(chain -> shouldBlockSecurityCenterWrite(chain.getArg(1), chain.getArg(2))
                    ? Boolean.TRUE : chain.proceed());
            hooked++;
        } catch (NoSuchMethodException | RuntimeException ignored) {
        }
        try {
            Method putStringForUser = Settings.Secure.class.getDeclaredMethod(
                    "putStringForUser", ContentResolver.class, String.class, String.class, int.class);
            module.hook(putStringForUser).intercept(chain -> shouldBlockSecurityCenterWrite(chain.getArg(1), chain.getArg(2))
                    ? Boolean.TRUE : chain.proceed());
            hooked++;
        } catch (NoSuchMethodException | RuntimeException ignored) {
        }
        AutofillModule.log("SecurityCenter credential guard ready (" + hooked + ")");
    }

    private static boolean shouldBlockSecurityCenterWrite(Object keyObj, Object valueObj) {
        if (!(keyObj instanceof String key)) {
            return false;
        }
        if (!"credential_service".equals(key)
                && !"credential_service_primary".equals(key)
                && !"autofill_service".equals(key)) {
            return false;
        }
        String value = valueObj instanceof String s ? s : "";
        if (isXiaomiCredentialString(value)) {
            AutofillModule.log("Blocked SecurityCenter reset of " + key);
            return true;
        }
        return false;
    }

    private static void installScannerHooks(AutofillModule module, ClassLoader classLoader) {
        try {
            Class<?> miFidoClass = Class.forName(
                    "com.xiaomi.scanner.module.code.utils.bean.MiFiDoBean", false, classLoader);
            Method getAppPkg = miFidoClass.getDeclaredMethod("getAppPackageName");
            getAppPkg.setAccessible(true);
            module.hook(getAppPkg).intercept(chain -> "");
            AutofillModule.log("Xiaomi Scanner FIDO QR passthrough ready");
        } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
            AutofillModule.log("Scanner FIDO hook unavailable: " + failure.getClass().getSimpleName());
        }
    }

    private static ComponentName resolveEnabledGmsChooser(Context context) {
        ComponentName component = ComponentName.unflattenFromString(GMS_CHOOSER_COMPONENT);
        if (component == null) {
            return null;
        }
        PackageManager pm = context.getPackageManager();
        ActivityInfo info = null;
        try {
            info = pm.getActivityInfo(component, PackageManager.ComponentInfoFlags.of(PackageManager.MATCH_SYSTEM_ONLY));
        } catch (PackageManager.NameNotFoundException ignored) {
            try {
                info = pm.getActivityInfo(component, PackageManager.ComponentInfoFlags.of(0L));
            } catch (PackageManager.NameNotFoundException ignoredAgain) {
                return null;
            }
        }
        if (info == null || !info.exported) {
            return null;
        }
        return isComponentEnabled(pm, component, info.enabled) ? component : null;
    }

    private static boolean isGmsHybridServiceAvailable(Context context) {
        ComponentName component = ComponentName.unflattenFromString(GMS_HYBRID_SERVICE);
        if (component == null) {
            return false;
        }
        PackageManager pm = context.getPackageManager();
        try {
            ServiceInfo info = pm.getServiceInfo(component, PackageManager.ComponentInfoFlags.of(0L));
            return isComponentEnabled(pm, component, info.enabled);
        } catch (PackageManager.NameNotFoundException ignored) {
            return false;
        }
    }

    private static boolean isComponentEnabled(PackageManager pm, ComponentName component, boolean manifestEnabled) {
        int state = pm.getComponentEnabledSetting(component);
        if (state == PackageManager.COMPONENT_ENABLED_STATE_ENABLED) {
            return true;
        }
        if (state == PackageManager.COMPONENT_ENABLED_STATE_DISABLED
                || state == PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER
                || state == PackageManager.COMPONENT_ENABLED_STATE_DISABLED_UNTIL_USED) {
            return false;
        }
        return manifestEnabled;
    }

    private static boolean isXiaomiCredentialString(String value) {
        return value.contains(MIUI_PASSWORDS_PACKAGE) || value.contains(MIUI_CONTENT_CATCHER_PACKAGE);
    }

    private static String flattenServiceComponent(ServiceInfo serviceInfo) {
        return new ComponentName(serviceInfo.packageName, serviceInfo.name).flattenToString();
    }

    private static Object findEnumConstant(Class<?> enumClass, String name) {
        Object[] constants = enumClass.getEnumConstants();
        if (constants == null) {
            return null;
        }
        for (Object constant : constants) {
            if (constant instanceof Enum<?> e && name.equals(e.name())) {
                return constant;
            }
        }
        return null;
    }

    private static void deoptimizeByPrefix(AutofillModule module, Class<?> clazz, String prefix) {
        for (Method method : clazz.getDeclaredMethods()) {
            if (method.getName().startsWith(prefix)) {
                try {
                    module.deoptimize(method);
                } catch (RuntimeException ignored) {
                }
            }
        }
    }

    private static void deoptimizeByName(
            AutofillModule module, ClassLoader loader, String className, String methodName) {
        try {
            Class<?> clazz = Class.forName(className, false, loader);
            for (Method method : clazz.getDeclaredMethods()) {
                if (methodName.equals(method.getName())) {
                    module.deoptimize(method);
                }
            }
        } catch (ReflectiveOperationException | RuntimeException ignored) {
        }
    }

    private static Method findLaunchSettingsMethod(Class<?> combinedInfoClass) {
        for (Method method : combinedInfoClass.getDeclaredMethods()) {
            if (!"launchSettingsActivityIntent".equals(method.getName())
                    || !Modifier.isStatic(method.getModifiers())) {
                continue;
            }
            Class<?>[] params = method.getParameterTypes();
            if (params.length == 4
                    && params[0] == Context.class
                    && params[1] == CharSequence.class
                    && params[2] == CharSequence.class
                    && params[3] == int.class) {
                return method;
            }
        }
        return null;
    }

    private static Method findMethodInHierarchy(Class<?> type, String name, Class<?>... params) {
        Class<?> current = type;
        while (current != null && current != Object.class) {
            try {
                Method m = current.getDeclaredMethod(name, params);
                m.setAccessible(true);
                return m;
            } catch (NoSuchMethodException ignored) {
                current = current.getSuperclass();
            }
        }
        return null;
    }

    private static Field findFieldInHierarchy(Class<?> type, String name) {
        Class<?> current = type;
        while (current != null && current != Object.class) {
            try {
                Field f = current.getDeclaredField(name);
                f.setAccessible(true);
                return f;
            } catch (NoSuchFieldException ignored) {
                current = current.getSuperclass();
            }
        }
        return null;
    }

    private static Field findFieldOfType(Class<?> owner, Class<?> fieldType) {
        for (Field field : owner.getDeclaredFields()) {
            if (field.getType() == fieldType) {
                field.setAccessible(true);
                return field;
            }
        }
        return null;
    }

    private static Field findNamedOrTypedField(Class<?> owner, String preferredName, Class<?> fieldType) {
        try {
            Field exact = owner.getDeclaredField(preferredName);
            exact.setAccessible(true);
            return exact;
        } catch (NoSuchFieldException ignored) {
            for (Field field : owner.getDeclaredFields()) {
                if (field.getType() == fieldType) {
                    field.setAccessible(true);
                    return field;
                }
            }
            return null;
        }
    }

    private static String readSystemProperty(String key) {
        try {
            Class<?> props = Class.forName("android.os.SystemProperties");
            Object val = props.getMethod("get", String.class, String.class).invoke(null, key, "");
            return val instanceof String s ? s : "";
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return "";
        }
    }
}
