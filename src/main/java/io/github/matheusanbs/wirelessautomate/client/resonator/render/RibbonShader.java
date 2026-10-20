package io.github.matheusanbs.wirelessautomate.client.resonator.render;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import java.io.IOException;
import net.minecraft.client.renderer.ShaderInstance;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;

/** O core shader das fitas e dos brilhos, registrado pelo {@code ClientSetup}; {@code null} antes do registro ou se não compilou. */
public final class RibbonShader {
    private static ShaderInstance shader;

    private RibbonShader() {
    }

    /** Pede ao vanilla que compile o shader no carregamento dos recursos; se não compilar, fica {@code null} e a câmara cai no desenho antigo. */
    public static void register(RegisterShadersEvent event) {
        try {
            event.registerShader(new ShaderInstance(event.getResourceProvider(), WirelessAutomate.id("resonator_ribbon"),
                DefaultVertexFormat.POSITION_COLOR_TEX_LIGHTMAP), loaded -> shader = loaded);
        } catch (IOException | RuntimeException e) {
            shader = null;
            WirelessAutomate.LOGGER.warn("O shader resonator_ribbon não compilou; a câmara do Ressonador volta ao desenho antigo", e);
        }
    }

    public static ShaderInstance get() {
        return shader;
    }
}
