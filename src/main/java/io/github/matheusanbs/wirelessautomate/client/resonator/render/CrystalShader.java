package io.github.matheusanbs.wirelessautomate.client.resonator.render;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import java.io.IOException;
import net.minecraft.client.renderer.ShaderInstance;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;

/** O core shader do cristal facetado da câmara, registrado pelo {@code ClientSetup}; {@code null} antes do registro ou se não compilou. */
public final class CrystalShader {
    private static ShaderInstance shader;

    private CrystalShader() {
    }

    /** Pede ao vanilla que compile o shader no carregamento dos recursos. */
    public static void register(RegisterShadersEvent event) throws IOException {
        event.registerShader(new ShaderInstance(event.getResourceProvider(), WirelessAutomate.id("resonator_crystal"),
                DefaultVertexFormat.NEW_ENTITY), loaded -> shader = loaded);
    }

    public static ShaderInstance get() {
        return shader;
    }
}
