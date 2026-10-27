package io.github.matheusanbs.wirelessautomate.client.tv.render;

import com.mojang.blaze3d.pipeline.TextureTarget;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.server.packs.resources.ResourceManager;

/**
 * A textura que o {@code TextureManager} conhece para um framebuffer da Tela: o id é o da cor do framebuffer, lido na
 * hora (muda quando ele é refeito). Não apaga nada no {@code close}: quem apaga é o {@code destroyBuffers} do
 * framebuffer. O filtro é o de quem desenhou: o {@code RenderType.text} pediria {@code NEAREST} sem mipmap.
 */
final class TvTargetTexture extends AbstractTexture {
    private final TextureTarget target;

    TvTargetTexture(TextureTarget target) {
        this.target = target;
    }

    @Override
    public int getId() {
        return target.getColorTextureId();
    }

    @Override
    public void load(ResourceManager resourceManager) {
    }

    @Override
    public void setFilter(boolean blur, boolean mipmap) {
    }

    @Override
    public void releaseId() {
    }

    @Override
    public void close() {
    }
}
