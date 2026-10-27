package io.github.matheusanbs.wirelessautomate.client.tv.render;

import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexSorting;
import io.github.matheusanbs.wirelessautomate.tv.TvArea;
import io.github.matheusanbs.wirelessautomate.tv.TvBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.neoforged.neoforge.client.ClientHooks;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.lwjgl.opengl.EXTTextureFilterAnisotropic;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GLCapabilities;

/**
 * A imagem de uma Tela: um framebuffer próprio ({@link TextureTarget}, sem profundidade, com mipmaps e o filtro
 * anisotrópico) do tamanho da área vezes os pixels por bloco que a {@link TvCanvases} escolheu pelo {@link TvBudget},
 * registrado no {@code TextureManager} com um nome próprio para o BER o usar como textura. Criado sem nada de GL quando o BER pede; o framebuffer nasce no primeiro
 * redesenho, que só acontece no começo do quadro ({@link TvCanvases#onFrame}), porque criar ou limpar um framebuffer
 * troca o que está ligado. Só na thread de render.
 */
final class TvCanvas {
    private static final int MAG_FILTER = GL11.GL_LINEAR;
    private static final float ANISOTROPY = 8f;
    /** O anisotrópico que a placa aceita, até {@link #ANISOTROPY}; 0 sem suporte, -1 antes de perguntar. */
    private static float anisotropy = -1;
    final int slot;
    final ResourceLocation id;
    final RenderType renderType;
    TvBlockEntity tv;
    private @Nullable TextureTarget target;
    /** A revisão do layout com que foi desenhada; -1 nunca desenhou. */
    int revision = -1;
    /** Os pixels por bloco da textura atual (0 sem textura) e os do próximo redesenho (0 ainda sem escolha). */
    int ppb;
    int wantedPpb;
    /** A memória de vídeo da textura atual, com os mipmaps. */
    long bytes;
    /** A última vez que foi desenhada, alguma regra que bate mandava piscar (a {@link TvCanvases} a redesenha). */
    boolean blinking;
    /** O último tick do mundo em que o BER a pediu. */
    long lastWanted;

    TvCanvas(int slot, ResourceLocation id, TvBlockEntity tv) {
        this.slot = slot;
        this.id = id;
        this.renderType = TvRenderTypes.image(id);
        this.tv = tv;
    }

    boolean painted() {
        return revision >= 0 && target != null;
    }

    /**
     * Redesenha o layout. Deixa ligado o framebuffer desta imagem e a projeção dela: quem chama guarda e devolve os
     * do jogo depois de todos os redesenhos do quadro. A névoa fica desligada só durante o desenho (o texto está em
     * z −11000 e um mod que deixe a névoa ligada o apagaria). Desenha com o {@link #wantedPpb}, no teto da área atual
     * (a área pode ter mudado depois da escolha), e atualiza {@link #ppb} e {@link #bytes}.
     */
    void paint(Minecraft minecraft, char decimalSep, char groupSep) {
        TvArea area = tv.area();
        int effective = Math.min(Math.max(1, wantedPpb),
                TvBudget.maxPpb(area.width(), area.height(), RenderSystem.maxSupportedTextureSize()));
        int width = area.width() * effective;
        int height = area.height() * effective;
        if (target == null) {
            target = new TextureTarget(width, height, false, Minecraft.ON_OSX);
            minecraft.getTextureManager().register(id, new Texture(target));
        } else if (target.width != width || target.height != height) {
            target.resize(width, height, Minecraft.ON_OSX);
        }
        target.setClearColor(0, 0, 0, 0);
        target.clear(Minecraft.ON_OSX);
        target.bindWrite(true);
        float far = ClientHooks.getGuiFarPlane();
        RenderSystem.setProjectionMatrix(new Matrix4f().setOrtho(0, width, height, 0, 1000, far),
                VertexSorting.ORTHOGRAPHIC_Z);
        Matrix4fStack modelView = RenderSystem.getModelViewStack();
        modelView.pushMatrix();
        modelView.translation(0, 0, 10000 - far);
        RenderSystem.applyModelViewMatrix();
        float fogStart = RenderSystem.getShaderFogStart();
        RenderSystem.setShaderFogStart(Float.MAX_VALUE);
        GuiGraphics graphics = new GuiGraphics(minecraft, minecraft.renderBuffers().bufferSource());
        float scale = effective / (float) TvArea.PIXELS_PER_BLOCK;
        graphics.pose().scale(scale, scale, 1);
        long gameTime = minecraft.level == null ? 0 : minecraft.level.getGameTime();
        blinking = TvPainter.paint(graphics, minecraft.font, tv.layout(), area, tv.getBlockPos().asLong(),
                tv.revision(), decimalSep, groupSep, TvPaintMath.blinkOff(gameTime), scale, height);
        graphics.flush();
        RenderSystem.setShaderFogStart(fogStart);
        mipmaps(target.getColorTextureId());
        modelView.popMatrix();
        RenderSystem.applyModelViewMatrix();
        revision = tv.revision();
        ppb = effective;
        bytes = TvBudget.bytes(area.width(), area.height(), effective);
    }

    /**
     * Gera os níveis de mipmap da imagem (o GL aloca os que faltam) e põe o filtro: linear entre os níveis de longe,
     * para a Tela não cintilar, o {@link #MAG_FILTER} de perto e o anisotrópico, para a Tela vista de lado não
     * borrar. O {@code TextureTarget} só cria o nível 0 e põe {@code NEAREST} ao criar ou refazer o framebuffer; por
     * isso o filtro vai a cada redesenho, e a {@link Texture} não deixa o tipo de render trocá-lo.
     */
    private static void mipmaps(int texture) {
        GlStateManager._bindTexture(texture);
        GL30.glGenerateMipmap(GL11.GL_TEXTURE_2D);
        GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR_MIPMAP_LINEAR);
        GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, MAG_FILTER);
        float level = anisotropy();
        if (level > 1) {
            GlStateManager._texParameter(GL11.GL_TEXTURE_2D,
                    EXTTextureFilterAnisotropic.GL_TEXTURE_MAX_ANISOTROPY_EXT, level);
        }
    }

    /**
     * O anisotrópico até {@link #ANISOTROPY}, perguntado à placa uma vez: pelo GL 4.6 ou pelas extensões ARB e EXT,
     * que usam as mesmas constantes. Sem nenhuma, 0 (segue sem).
     */
    private static float anisotropy() {
        if (anisotropy < 0) {
            GLCapabilities caps = GL.getCapabilities();
            anisotropy = caps.OpenGL46 || caps.GL_ARB_texture_filter_anisotropic
                    || caps.GL_EXT_texture_filter_anisotropic
                    ? Math.min(ANISOTROPY, GL11.glGetFloat(EXTTextureFilterAnisotropic.GL_MAX_TEXTURE_MAX_ANISOTROPY_EXT))
                    : 0;
        }
        return anisotropy;
    }

    /** Solta o framebuffer e o nome no {@code TextureManager}. */
    void release(Minecraft minecraft) {
        if (target != null) {
            minecraft.getTextureManager().release(id);
            target.destroyBuffers();
            target = null;
        }
        revision = -1;
        blinking = false;
        ppb = 0;
        bytes = 0;
    }

    /**
     * A textura que o {@code TextureManager} conhece: o id é o da cor do framebuffer, lido na hora (muda quando ele
     * é refeito). Não apaga nada no {@code close}: quem apaga é o {@code destroyBuffers} do framebuffer.
     */
    private static final class Texture extends AbstractTexture {
        private final TextureTarget target;

        Texture(TextureTarget target) {
            this.target = target;
        }

        @Override
        public int getId() {
            return target.getColorTextureId();
        }

        @Override
        public void load(ResourceManager resourceManager) {
        }

        /** O filtro é o do {@link #mipmaps}: o {@code RenderType.text} pediria {@code NEAREST} sem mipmap. */
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
}
