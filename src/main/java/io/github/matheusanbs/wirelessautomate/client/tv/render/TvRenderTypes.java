package io.github.matheusanbs.wirelessautomate.client.tv.render;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import java.util.function.Function;
import net.minecraft.Util;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

/**
 * O tipo de desenho da imagem da Tela no mundo: o do texto do vanilla (shader, luz e profundidade), mas com a
 * mistura de alfa pré-multiplicado ({@code ONE, ONE_MINUS_SRC_ALPHA}). O redesenho pinta no framebuffer com a
 * mistura das telas do vanilla ({@code SRC_ALPHA, ONE_MINUS_SRC_ALPHA} na cor, {@code ONE, ONE_MINUS_SRC_ALPHA} no
 * alfa) sobre o transparente preto, e a textura sai com a cor já multiplicada pelo alfa; o {@code RenderType.text}
 * multiplicaria de novo e escureceria os fundos semitransparentes. Um por nome de textura, e os nomes são
 * reaproveitados pela {@link TvCanvases}.
 * <p>
 * Também o tipo de uma pixel art desenhada no redesenho ({@link #art}).
 */
final class TvRenderTypes extends RenderType {
    private static final TransparencyStateShard PREMULTIPLIED = new TransparencyStateShard(
            WirelessAutomate.MODID + "_tv_premultiplied", () -> {
                RenderSystem.enableBlend();
                RenderSystem.blendFunc(GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
            }, () -> {
                RenderSystem.disableBlend();
                RenderSystem.defaultBlendFunc();
            });
    private static final Function<ResourceLocation, RenderType> IMAGE = Util.memoize(id -> create(
            WirelessAutomate.MODID + "_tv_image", DefaultVertexFormat.POSITION_COLOR_TEX_LIGHTMAP,
            VertexFormat.Mode.QUADS, 256, false, true,
            CompositeState.builder()
                    .setShaderState(RENDERTYPE_TEXT_SHADER)
                    .setTextureState(new TextureStateShard(id, false, false))
                    .setTransparencyState(PREMULTIPLIED)
                    .setLightmapState(LIGHTMAP)
                    .createCompositeState(false)));

    private static final ShaderStateShard POSITION_TEX_COLOR = new ShaderStateShard(
            GameRenderer::getPositionTexColorShader);

    private TvRenderTypes(String name, VertexFormat format, VertexFormat.Mode mode, int bufferSize, boolean crumbling,
            boolean sort, Runnable setup, Runnable clear) {
        super(name, format, mode, bufferSize, crumbling, sort, setup, clear);
    }

    static RenderType image(ResourceLocation id) {
        return IMAGE.apply(id);
    }

    /**
     * Uma pixel art pintada na textura da Tela ({@link TvArtTextures}): a mesma mistura e o mesmo teste de profundidade
     * do {@code RenderType.gui()} que pinta o resto, com a cor do texel (sem pré-multiplicar) vezes a do vértice, a
     * opacidade da peça. Assim a arte sai na textura da Tela pré-multiplicada como o resto dela, e o texel
     * transparente é descartado pelo shader. Um por textura, guardado com ela.
     */
    static RenderType art(int texture) {
        return create(WirelessAutomate.MODID + "_tv_art", DefaultVertexFormat.POSITION_TEX_COLOR,
                VertexFormat.Mode.QUADS, 256, false, false,
                CompositeState.builder()
                        .setShaderState(POSITION_TEX_COLOR)
                        .setTextureState(new EmptyTextureStateShard(() -> RenderSystem.setShaderTexture(0, texture),
                                () -> {
                                }))
                        .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                        .setDepthTestState(LEQUAL_DEPTH_TEST)
                        .createCompositeState(false));
    }
}
