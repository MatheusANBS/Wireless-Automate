package io.github.matheusanbs.wirelessautomate.client.resonator.render;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import net.minecraft.client.renderer.RenderType;

/**
 * Os tipos de desenho da câmara: os sprites aditivos (o halo e a placa de Chladni), o billboard do Núcleo de plasma,
 * as fitas e o cristal facetado, este o único opaco. As texturas guardam a intensidade no alfa, com cor nos pixels
 * transparentes; o {@code eyes} soma ONE, ONE e acenderia o quadrado inteiro. Aqui a soma é SRC_ALPHA, ONE (a do
 * {@code lightning}), sem gravar profundidade e nas duas faces; o plasma e as fitas usam a mesma soma.
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

    private static final RenderType PLASMA = create(WirelessAutomate.MODID + "_chamber_plasma",
            DefaultVertexFormat.POSITION_COLOR_TEX_LIGHTMAP, VertexFormat.Mode.QUADS, 256, false, true,
            CompositeState.builder()
                    .setShaderState(new ShaderStateShard(PlasmaShader::get))
                    .setTransparencyState(LIGHTNING_TRANSPARENCY)
                    .setCullState(NO_CULL)
                    .setWriteMaskState(COLOR_WRITE)
                    .createCompositeState(false));

    private static final RenderType RIBBON = create(WirelessAutomate.MODID + "_chamber_ribbon",
            DefaultVertexFormat.POSITION_COLOR_TEX_LIGHTMAP, VertexFormat.Mode.QUADS, 256, false, true,
            CompositeState.builder()
                    .setShaderState(new ShaderStateShard(RibbonShader::get))
                    .setTransparencyState(LIGHTNING_TRANSPARENCY)
                    .setCullState(NO_CULL)
                    .setWriteMaskState(COLOR_WRITE)
                    .createCompositeState(false));

    private static final RenderType CRYSTAL = create(WirelessAutomate.MODID + "_chamber_crystal",
            DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.TRIANGLES, 1536, false, false,
            CompositeState.builder()
                    .setShaderState(new ShaderStateShard(CrystalShader::get))
                    .createCompositeState(false));

    private ChamberRenderTypes(String name, VertexFormat format, VertexFormat.Mode mode, int bufferSize,
            boolean crumbling, boolean sort, Runnable setup, Runnable clear) {
        super(name, format, mode, bufferSize, crumbling, sort, setup, clear);
    }

    /** Os sprites do atlas de blocos, somados pelo alfa (o alfa do vértice multiplica o da textura). */
    static RenderType glow() {
        return GLOW;
    }

    /** O billboard do Núcleo de plasma: aditivo, sem gravar profundidade, nas duas faces. */
    static RenderType plasma() {
        return PLASMA;
    }

    /** As fitas e os brilhos (o raio e os tentáculos): aditivo, sem gravar profundidade, nas duas faces. */
    static RenderType ribbon() {
        return RIBBON;
    }

    /** O cristal facetado: opaco, com profundidade e sem blend, só as faces de fora. */
    static RenderType crystal() {
        return CRYSTAL;
    }
}
