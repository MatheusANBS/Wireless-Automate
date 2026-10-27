package io.github.matheusanbs.wirelessautomate.client.tv.render;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import java.util.function.Function;
import net.minecraft.Util;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

/**
 * O tipo de desenho da imagem da Tela no mundo: o do texto do vanilla (shader, luz e profundidade), mas com a
 * mistura de alfa pré-multiplicado ({@code ONE, ONE_MINUS_SRC_ALPHA}). O redesenho pinta no framebuffer com a
 * mistura das telas do vanilla ({@code SRC_ALPHA, ONE_MINUS_SRC_ALPHA} na cor, {@code ONE, ONE_MINUS_SRC_ALPHA} no
 * alfa) sobre o transparente preto, e a textura sai com a cor já multiplicada pelo alfa; o {@code RenderType.text}
 * multiplicaria de novo e escureceria os fundos semitransparentes. Um por nome de textura, e os nomes são
 * reaproveitados pela {@link TvCanvases}.
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

    private TvRenderTypes(String name, VertexFormat format, VertexFormat.Mode mode, int bufferSize, boolean crumbling,
            boolean sort, Runnable setup, Runnable clear) {
        super(name, format, mode, bufferSize, crumbling, sort, setup, clear);
    }

    static RenderType image(ResourceLocation id) {
        return IMAGE.apply(id);
    }
}
