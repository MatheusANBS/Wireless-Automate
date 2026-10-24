package io.github.matheusanbs.wirelessautomate.client.resonator.render;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import net.minecraft.client.renderer.RenderType;

/**
 * Os tipos de desenho da câmara: os sprites aditivos (o halo e as faíscas do desenho antigo), o billboard do Núcleo de plasma
 * (pré-multiplicado), as fitas e o cristal facetado, este o único opaco. As texturas guardam a intensidade no alfa,
 * com cor nos pixels transparentes; o {@code eyes} soma ONE, ONE e acenderia o quadrado inteiro. Aqui a soma é
 * SRC_ALPHA, ONE (a do {@code lightning}), sem gravar profundidade e nas duas faces; as fitas usam a mesma soma.
 */
final class ChamberRenderTypes extends RenderType {
    /**
     * A mistura do plasma, com alfa pré-multiplicado: {@code ONE, ONE_MINUS_SRC_ALPHA}. Com alfa 1 a cor cobre o que
     * está atrás (o corpo da esfera esconde o cristal de trás); com alfa 0 ela soma (o halo), numa passada só.
     */
    private static final TransparencyStateShard PREMULTIPLIED = new TransparencyStateShard(
            WirelessAutomate.MODID + "_premultiplied", () -> {
                RenderSystem.enableBlend();
                RenderSystem.blendFunc(GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
            }, () -> {
                RenderSystem.disableBlend();
                RenderSystem.defaultBlendFunc();
            });
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
                    .setTransparencyState(PREMULTIPLIED)
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

    /**
     * O billboard do Núcleo de plasma: alfa pré-multiplicado (o corpo cobre, o halo soma), sem gravar profundidade,
     * nas duas faces. Vem depois dos cristais, então cobre só os que estão atrás do plano dele.
     */
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
