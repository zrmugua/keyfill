package com.mugua.keyfill;

import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.service.autofill.InlinePresentation;
import android.widget.inline.InlinePresentationSpec;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

final class InlineThemeRenderer {
    static final String LIGHT_STYLE = "com.mugua.keyfill.style.light";
    static final String DARK_STYLE = "com.mugua.keyfill.style.dark";

    static void install(AutofillModule module, ClassLoader systemLoader) throws ReflectiveOperationException {
        String autofillPackage = "com.android.server.autofill.";
        Class<?> renderService = Class.forName(autofillPackage + "RemoteInlineSuggestionRenderService", false, systemLoader);
        Class<?> connector = Class.forName(autofillPackage + "ui.RemoteInlineSuggestionViewConnector", false, systemLoader);
        Class<?> remoteUi = Class.forName(autofillPackage + "ui.RemoteInlineSuggestionUi", false, systemLoader);
        Class<?> renderCallback = Class.forName("android.service.autofill.IInlineSuggestionUiCallback");
        Field presentationField = AutofillModule.accessibleField(connector, "mInlinePresentation");
        Map<Object, Integer> renderedModes = Collections.synchronizedMap(new WeakHashMap<>());

        module.hook(remoteUi.getDeclaredConstructor(connector, int.class, int.class, Handler.class))
                .intercept(chain -> {
                    Object result = chain.proceed();
                    Object connectorInstance = chain.getArg(0);
                    Object presentationValue = connectorInstance == null ? null : presentationField.get(connectorInstance);
                    if (presentationValue instanceof InlinePresentation presentation) {
                        InlinePresentationSpec spec = presentation.getInlinePresentationSpec();
                        Bundle style = spec == null ? null : spec.getStyle();
                        if (style != null && style.containsKey(LIGHT_STYLE) && style.containsKey(DARK_STYLE)) {
                            renderedModes.put(chain.getThisObject(), nightMode());
                        }
                    }
                    return result;
                });
        module.hook(remoteUi.getDeclaredMethod("match", int.class, int.class)).intercept(chain -> {
            Integer previous = renderedModes.get(chain.getThisObject());
            return previous != null && previous != nightMode() ? false : chain.proceed();
        });
        module.hook(renderService.getDeclaredMethod("renderSuggestion", renderCallback, InlinePresentation.class,
                int.class, int.class, IBinder.class, int.class, int.class, int.class)).intercept(chain -> {
                    Object arg = chain.getArg(1);
                    if (!(arg instanceof InlinePresentation presentation)) {
                        return chain.proceed();
                    }
                    InlinePresentationSpec original = presentation.getInlinePresentationSpec();
                    Bundle originalStyle = original == null ? null : original.getStyle();
                    Bundle style = originalStyle == null ? null : originalStyle.getBundle(
                            nightMode() == Configuration.UI_MODE_NIGHT_YES ? DARK_STYLE : LIGHT_STYLE);
                    if (style == null) {
                        return chain.proceed();
                    }
                    InlinePresentationSpec themed = new InlinePresentationSpec.Builder(
                            original.getMinSize(), original.getMaxSize()).setStyle(style).build();
                    Object[] args = chain.getArgs().toArray();
                    args[1] = new InlinePresentation(presentation.getSlice(), themed, presentation.isPinned());
                    return chain.proceed(args);
                });
    }

    private static int nightMode() {
        return Resources.getSystem().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
    }
}
