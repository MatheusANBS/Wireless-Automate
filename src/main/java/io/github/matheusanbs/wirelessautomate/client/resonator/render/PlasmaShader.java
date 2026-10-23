package io.github.matheusanbs.wirelessautomate.client.resonator.render;

import com.mojang.blaze3d.shaders.Uniform;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import java.io.IOException;
import net.minecraft.client.renderer.ShaderInstance;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;

/** O core shader do Núcleo de plasma, registrado pelo {@code ClientSetup}; {@code null} antes do registro ou se não compilou. */
public final class PlasmaShader {
    private static ShaderInstance shader;

    private PlasmaShader() {
    }

    /** Pede ao vanilla que compile o shader no carregamento dos recursos; se não compilar, fica {@code null} e a câmara cai no desenho antigo. */
    public static void register(RegisterShadersEvent event) {
        try {
            event.registerShader(new ShaderInstance(event.getResourceProvider(), WirelessAutomate.id("resonator_plasma"),
                DefaultVertexFormat.POSITION_COLOR_TEX_LIGHTMAP), loaded -> shader = loaded);
        } catch (IOException | RuntimeException e) {
            shader = null;
            WirelessAutomate.LOGGER.warn("O shader resonator_plasma não compilou; a câmara do Ressonador volta ao desenho antigo", e);
        }
    }

    /** Escreve o uniform {@code ViewRot} (9 floats, coluna-maior); sem o shader ou sem o uniform, não faz nada. */
    public static void setViewRotation(float[] matrix) {
        if (shader == null) {
            return;
        }
        Uniform uniform = shader.getUniform("ViewRot");
        if (uniform != null) {
            uniform.set(matrix);
        }
    }

    public static ShaderInstance get() {
        return shader;
    }
}
