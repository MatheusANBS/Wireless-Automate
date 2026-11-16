package io.github.matheusanbs.wirelessautomate.client.fx;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import java.io.IOException;
import java.util.function.Consumer;
import net.minecraft.client.renderer.ShaderInstance;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;

/**
 * Os três core shaders dos efeitos, registrados pelo {@code ClientSetup}: {@code fx} (tinta, com luz real e névoa
 * limitada), {@code fx_add} (aditivo, sem névoa e sem luz) e {@code fx_add_premul} (luz pré-multiplicada). Cada um é
 * {@code null} antes do registro ou se não compilou, e o tipo de desenho que depende dele some.
 */
public final class FxShaders {
    private static ShaderInstance ink;
    private static ShaderInstance add;
    private static ShaderInstance light;

    private FxShaders() {
    }

    /** Pede ao vanilla que compile os três shaders no carregamento dos recursos. */
    public static void register(RegisterShadersEvent event) {
        load(event, "fx", loaded -> ink = loaded);
        load(event, "fx_add", loaded -> add = loaded);
        load(event, "fx_add_premul", loaded -> light = loaded);
    }

    private static void load(RegisterShadersEvent event, String name, Consumer<ShaderInstance> sink) {
        try {
            event.registerShader(new ShaderInstance(event.getResourceProvider(), WirelessAutomate.id(name),
                    DefaultVertexFormat.NEW_ENTITY), sink);
        } catch (IOException | RuntimeException e) {
            sink.accept(null);
            WirelessAutomate.LOGGER.warn("O shader {} não compilou; os efeitos que dependem dele não são desenhados", name, e);
        }
    }

    /** A tinta: alfa, com a luz do mapa e a névoa limitada. */
    public static ShaderInstance ink() {
        return ink;
    }

    /** O aditivo sem pré-multiplicar, sem névoa e sem luz. */
    public static ShaderInstance add() {
        return add;
    }

    /** A luz pré-multiplicada: {@code rgb × a} com alfa 1. */
    public static ShaderInstance light() {
        return light;
    }

    /** Se os três compilaram. */
    public static boolean ready() {
        return ink != null && add != null && light != null;
    }
}
