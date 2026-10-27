package io.github.matheusanbs.wirelessautomate.client.tv.render;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.tv.TvArea;
import io.github.matheusanbs.wirelessautomate.tv.TvBlock;
import io.github.matheusanbs.wirelessautomate.tv.TvBlockEntity;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * A Tela de Sinal no mundo. Na Tela 1 × 1 o modelo do bloco já é a moldura com o vidro, e aqui fica só a imagem, no
 * retângulo do vidro, logo atrás dele; na maior ({@code EXPANDED}) o modelo some e aqui fica a moldura inteira
 * ({@link TvFrameMesh}, em {@code entitySolid} com o atlas do modelo) e a imagem no retângulo de dentro. A imagem é
 * a textura da {@link TvCanvases}, desenhada só quando muda; aqui ela é um quadrilátero com luz cheia e alfa
 * pré-multiplicado ({@link TvRenderTypes}), na escala uniforme que cabe no retângulo e centrada (a sobra mostra o
 * miolo apagado). Por quadro: nada alocado, a moldura e um quadrilátero.
 */
public final class TvRenderer implements BlockEntityRenderer<TvBlockEntity> {
    private static final int VIEW_DISTANCE = 64;
    private static final ResourceLocation ATLAS = WirelessAutomate.id("textures/block/signal_screen/signal_screen.png");

    public TvRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(TvBlockEntity tv, float partialTick, PoseStack poseStack, MultiBufferSource buffer, int light,
            int overlay) {
        BlockState state = tv.getBlockState();
        if (!state.hasProperty(TvBlock.FACING)) {
            return;
        }
        Direction facing = state.getValue(TvBlock.FACING);
        TvArea area = tv.area();
        float block = TvFrame.UNITS_PER_BLOCK;
        float w = area.width() * block;
        float h = area.height() * block;
        TvQuads.begin(facing, (area.anchorCol() + 1) * block, -area.anchorRow() * block);
        PoseStack.Pose pose = poseStack.last();
        if (state.getValue(TvBlock.EXPANDED)) {
            TvFrameMesh.render(buffer.getBuffer(RenderType.entitySolid(ATLAS)), pose, w, h, overlay, light);
        }
        Level level = tv.getLevel();
        if (tv.layout().pieces().isEmpty() || level == null) {
            return;
        }
        RenderType image = TvCanvases.image(tv, level.getGameTime());
        if (image == null) {
            return;
        }
        float innerW = w - 2 * TvFrame.BAND;
        float innerH = h - TvFrame.BAND - TvFrame.BAND_BOTTOM;
        float scale = Math.min(innerW / area.pixelWidth(), innerH / area.pixelHeight());
        float imageW = area.pixelWidth() * scale;
        float imageH = area.pixelHeight() * scale;
        float x0 = TvFrame.BAND + (innerW - imageW) / 2;
        float y0 = TvFrame.BAND_BOTTOM + (innerH - imageH) / 2;
        TvQuads.front(x0, y0, x0 + imageW, y0 + imageH, TvFrame.IMAGE_Z, 0, 1, 1, 0);
        TvQuads.text(buffer.getBuffer(image), pose, LightTexture.FULL_BRIGHT);
    }

    @Override
    public int getViewDistance() {
        return VIEW_DISTANCE;
    }

    /**
     * A Tela maior vai na lista global do jogo, conferida só pela caixa da área inteira: na lista da seção do bloco
     * colocado, ela sumiria inteira quando essa seção sai da vista, mesmo com o resto da área à vista. O valor é lido
     * quando a seção é montada, e o {@code EXPANDED} mudando refaz a seção.
     */
    @Override
    public boolean shouldRenderOffScreen(TvBlockEntity tv) {
        BlockState state = tv.getBlockState();
        return state.hasProperty(TvBlock.EXPANDED) && state.getValue(TvBlock.EXPANDED);
    }

    @Override
    public AABB getRenderBoundingBox(TvBlockEntity tv) {
        return tv.renderBox();
    }
}
