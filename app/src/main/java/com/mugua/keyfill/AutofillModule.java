package com.mugua.keyfill;

import android.inputmethodservice.InputMethodService;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InlineSuggestionsRequest;
import android.view.inputmethod.InlineSuggestionsResponse;
import android.view.inputmethod.InputMethodInfo;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

import io.github.libxposed.api.XposedModule;

public final class AutofillModule extends XposedModule {
    private static final String TAG = "KeyFill";

    private KeyboardTarget target;
    private Class<?> keyboardLifecycleType;
    private KeyboardInlineUi keyboardUi;

    @Override
    public void onModuleLoaded(ModuleLoadedParam param) {
        for (KeyboardTarget keyboard : KeyboardTarget.values()) {
            if (keyboard.processName.equals(param.getProcessName())) {
                target = keyboard;
                break;
            }
        }
    }

    @Override
    public void onSystemServerStarting(SystemServerStartingParam param) {
        try {
            Field inlineEnabled = accessibleField(InputMethodInfo.class, "mInlineSuggestionsEnabled");
            for (Constructor<?> constructor : InputMethodInfo.class.getDeclaredConstructors()) {
                hook(constructor).intercept(chain -> {
                    Object result = chain.proceed();
                    InputMethodInfo ime = (InputMethodInfo) chain.getThisObject();
                    for (KeyboardTarget keyboard : KeyboardTarget.values()) {
                        if (keyboard.serviceId.equals(ime.getId())) {
                            inlineEnabled.setBoolean(ime, true);
                            break;
                        }
                    }
                    return result;
                });
            }
            log("Keyboard capability hook ready");
        } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
            log("Inline capability hook unavailable: " + failure.getClass().getSimpleName());
        }
        try {
            InlineThemeRenderer.install(this, param.getClassLoader());
            log("Inline theme hook ready");
        } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
            log("Inline theme hook unavailable: " + failure.getClass().getSimpleName());
        }
        HyperPasskeyHooks.installSystemServer(this, param.getClassLoader());
    }

    @Override
    public void onPackageReady(PackageReadyParam param) {
        if (param.isFirstPackage()) {
            HyperPasskeyHooks.installPackage(this, param.getPackageName(), param.getClassLoader());
        }
        if (target == null || !target.packageName.equals(param.getPackageName())) {
            return;
        }
        try {
            hookKeyboard();
            if (target == KeyboardTarget.BAIDU) {
                BaiduToolbar.installHooks(this, param.getClassLoader());
            } else if (target == KeyboardTarget.IFLYTEK) {
                IflytekToolbar.installHooks(this, param.getClassLoader());
            }
            log("Keyboard inline hooks ready for " + target.packageName);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
            log("Keyboard hooks unavailable: " + failure.getClass().getSimpleName());
        }
    }

    private void hookKeyboard() throws ReflectiveOperationException {
        Field sessionController = accessibleField(InputMethodService.class,
                "mInlineSuggestionSessionController");
        Class<?> controllerType = sessionController.getType();
        Field requestSupplier = accessibleField(controllerType, "mRequestSupplier");
        Field responseConsumer = accessibleField(controllerType, "mResponseConsumer");
        Class<?> activityThread = Class.forName("android.app.ActivityThread");
        Class<?> serviceData = Class.forName("android.app.ActivityThread$CreateServiceData");
        Field serviceToken = accessibleField(serviceData, "token");
        Field services = accessibleField(activityThread, "mServices");
        hook(activityThread.getDeclaredMethod("handleCreateService", serviceData))
                .intercept(chain -> {
                    Object result = chain.proceed();
                    try {
                        Map<?, ?> liveServices = (Map<?, ?>) services.get(chain.getThisObject());
                        Object created = liveServices.get(serviceToken.get(chain.getArg(0)));
                        if (created instanceof InputMethodService service) {
                            Object controller = sessionController.get(service);
                            KeyboardInlineUi ui = new KeyboardInlineUi(service, target);
                            Function<Bundle, InlineSuggestionsRequest> request = extras -> ui.createRequest();
                            Consumer<InlineSuggestionsResponse> response = ui::receive;
                            requestSupplier.set(controller, request);
                            responseConsumer.set(controller, response);
                            keyboardUi = ui;
                            log("Inline callbacks registered for " + service.getClass().getName());
                            if (keyboardLifecycleType != service.getClass()) {
                                hookLifecycle(service.getClass());
                                keyboardLifecycleType = service.getClass();
                            }
                        }
                    } catch (ReflectiveOperationException | RuntimeException failure) {
                        log("Keyboard service initialization failed: " + failure.getClass().getSimpleName());
                    }
                    return result;
                });
    }

    private void hookLifecycle(Class<?> serviceClass) throws NoSuchMethodException {
        hook(serviceClass.getMethod("onStartInput", EditorInfo.class, boolean.class))
                .intercept(chain -> {
                    if (keyboardUi != null) {
                        keyboardUi.clear();
                    }
                    return chain.proceed();
                });
        hook(serviceClass.getMethod("onStartInputView", EditorInfo.class, boolean.class))
                .intercept(chain -> {
                    Object result = chain.proceed();
                    if (keyboardUi != null) {
                        keyboardUi.onKeyboardShown();
                    }
                    return result;
                });
        hook(serviceClass.getMethod("setInputView", View.class))
                .intercept(chain -> {
                    Object result = chain.proceed();
                    if (keyboardUi != null) {
                        keyboardUi.onInputViewReplaced();
                    }
                    return result;
                });
        hook(serviceClass.getMethod("onFinishInputView", boolean.class))
                .intercept(chain -> {
                    if (keyboardUi != null) {
                        keyboardUi.onKeyboardHidden();
                    }
                    return chain.proceed();
                });
        hook(serviceClass.getMethod("onDestroy")).intercept(chain -> {
            if (keyboardUi != null) {
                keyboardUi.onKeyboardHidden();
                keyboardUi = null;
            }
            return chain.proceed();
        });
    }

    static Field accessibleField(Class<?> type, String name) throws NoSuchFieldException {
        Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    static void log(String event) {
        Log.i(TAG, event);
    }
}
