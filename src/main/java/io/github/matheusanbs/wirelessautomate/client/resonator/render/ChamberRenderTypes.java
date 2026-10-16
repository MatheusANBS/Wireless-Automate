package io.github.matheusanbs.wirelessautomate.client.resonator.render;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import net.minecraft.client.renderer.RenderType;

/**
 * O tipo de desenho dos sprites aditivos da câmara (o halo e a placa de Chladni). As texturas guardam a intensidade
 * no alfa, com cor nos pixels transparentes; o {@code eyes} soma ONE, ONE e acenderia o quadrado inteiro. Aqui a
 * soma é SRC_ALPHA, ONE (a do {@code lightning}), sem gravar profundidade e nas duas faces.
 */
final class ChamberRenderTypes extends RenderType {
    private static final RenderType GLOW = create(WirelessAutomate.MODID + "_chamber_glow",
            DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS, 1536, false, true,
            CompositeState.builder()
                    .setShaderState(RENDERTYPE_EYES_SHADER)
                    .setTextureState(new TextureStateShard(ChamberLens.ATLAS, false, false))
                    .setTransparencyState(LIGHTNING_TRANSPARENCY)
                    .setCullState(NO_CULL)
                    .setWriteMaskState(COLOR_WRITE)
                    .createCompositeState(false));

    private ChamberRenderTypes(String name, VertexFormat format, VertexFormat.Mode mode, int bufferSize,
            boolean crumbling, boolean sort, Runnable setup, Runnable clear) {
        super(name, format, mode, bufferSize, crumbling, sort, setup, clear);
    }

    /** Os sprites do atlas de blocos, somados pelo alfa (o alfa do vértice multiplica o da textura). */
    static RenderType glow() {
        return GLOW;
    }
}
