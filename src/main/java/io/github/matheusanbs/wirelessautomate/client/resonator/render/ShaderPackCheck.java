package io.github.matheusanbs.wirelessautomate.client.resonator.render;

import io.github.matheusanbs.wirelessautomate.client.FrameClock;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

/**
 * Se um shaderpack do Iris está em uso: com ele, o core shader do plasma pode sair errado, e a câmara volta ao desenho
 * de hoje. Por reflexão, para o mod não depender do Iris; a resposta vale por um quadro.
 */
public final class ShaderPackCheck {
    private static final MethodHandle IN_USE = find();
    private static long frame = -1;
    private static boolean active;

    private ShaderPackCheck() {
    }

    public static boolean active() {
        if (IN_USE == null) {
            return false;
        }
        long now = FrameClock.frame();
        if (now != frame) {
            frame = now;
            try {
                active = (boolean) IN_USE.invoke();
            } catch (Throwable e) {
                active = false;
            }
        }
        return active;
    }

    private static MethodHandle find() {
        try {
            Class<?> api = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
            Object instance = api.getMethod("getInstance").invoke(null);
            return MethodHandles.publicLookup()
                    .findVirtual(api, "isShaderPackInUse", MethodType.methodType(boolean.class))
                    .bindTo(instance);
        } catch (ReflectiveOperationException | LinkageError e) {
            return null;
        }
    }
}
