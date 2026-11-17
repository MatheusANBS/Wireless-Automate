package io.github.matheusanbs.wirelessautomate.client.fx;

import io.github.matheusanbs.wirelessautomate.client.FrameClock;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

/**
 * Se um shaderpack do Iris está em uso, e se o Iris desenha a passada de sombra agora. A detecção serve ao desenho
 * tardio dos efeitos (com pacote, os desenhos vão por cima da imagem final) e a regra é uma: nada se desenha na passada
 * de sombra. Por reflexão, para o mod não depender do Iris; sem Iris, as duas respostas são sempre falsas.
 * {@link #active()} vale por um quadro.
 */
public final class ShaderPackCheck {
    private static final MethodHandle IN_USE = find("isShaderPackInUse");
    private static final MethodHandle IN_SHADOW = find("isRenderingShadowPass");
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

    /**
     * Se o Iris está desenhando a passada de sombra agora; sem Iris, sempre falso. Nada de efeito nessa passada. A
     * resposta não é guardada por quadro: a passada de sombra e a do mundo se alternam no mesmo quadro.
     */
    public static boolean shadowPass() {
        if (IN_SHADOW == null) {
            return false;
        }
        try {
            return (boolean) IN_SHADOW.invoke();
        } catch (Throwable e) {
            return false;
        }
    }

    private static MethodHandle find(String method) {
        try {
            Class<?> api = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
            Object instance = api.getMethod("getInstance").invoke(null);
            return MethodHandles.publicLookup()
                    .findVirtual(api, method, MethodType.methodType(boolean.class))
                    .bindTo(instance);
        } catch (ReflectiveOperationException | LinkageError e) {
            return null;
        }
    }
}
