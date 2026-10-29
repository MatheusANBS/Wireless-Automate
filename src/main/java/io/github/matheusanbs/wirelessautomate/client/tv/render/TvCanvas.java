package io.github.matheusanbs.wirelessautomate.client.tv.render;

import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import io.github.matheusanbs.wirelessautomate.tv.TvArea;
import io.github.matheusanbs.wirelessautomate.tv.TvBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.opengl.EXTTextureFilterAnisotropic;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GLCapabilities;

/**
 * A imagem de uma Tela: um framebuffer próprio ({@link TextureTarget}, com profundidade só se a Tela tem Ícone, com mipmaps e o filtro
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
    /** A última vez que foi desenhada, tinha um Gráfico (redesenha a cada segundo) ou um número do relógio. */
    boolean graphing;
    boolean clocking;
    /** A última vez que foi desenhada, tinha um Ícone que se mexe (redesenha a ~10 Hz). */
    boolean iconing;
    /** A última vez que foi desenhada, tinha um GIF animado (redesenha quando o quadro dele muda). */
    boolean gifing;
    /**
     * O instante (ms) em que a pixel art animada do último desenho troca de quadro, ou {@link Long#MAX_VALUE} sem
     * nenhuma (o {@link TvTimed} a redesenha então, se está à vista).
     */
    long artNext = Long.MAX_VALUE;
    /** As texturas animadas do último desenho, que o Sodium quer marcadas a cada quadro (vazio sem o Sodium). */
    TextureAtlasSprite[] sprites = TvPaintIcon.NO_SPRITES;
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
     * Redesenha o layout pelo {@link TvTargets}. Deixa ligado o framebuffer desta imagem e a projeção dela: quem chama
     * guarda e devolve os do jogo depois de todos os redesenhos do quadro. Desenha com o {@link #wantedPpb}, no teto da
     * área atual (a área pode ter mudado depois da escolha), e atualiza {@link #ppb} e {@link #bytes}.
     */
    void paint(Minecraft minecraft, char decimalSep, char groupSep) {
        TvArea area = tv.area();
        int effective = Math.min(Math.max(1, wantedPpb),
                TvBudget.maxPpb(area.width(), area.height(), RenderSystem.maxSupportedTextureSize()));
        int width = area.width() * effective;
        int height = area.height() * effective;
        TextureTarget before = target;
        target = TvTargets.ensure(target, width, height, TvPaintIcon.has(tv.layout()));
        if (target != before) {
            minecraft.getTextureManager().register(id, new TvTargetTexture(target));
        }
        float scale = effective / (float) TvArea.PIXELS_PER_BLOCK;
        long gameTime = minecraft.level == null ? 0 : minecraft.level.getGameTime();
        GuiGraphics graphics = TvTargets.begin(minecraft, target, width, height, scale);
        TvPaintIcon.beginCollect();
        int flags = TvPainter.paint(graphics, minecraft.font, tv.layout(), tv.layout(), area,
                tv.getBlockPos().asLong(), tv.revision(), decimalSep, groupSep, TvPaintMath.blinkOff(gameTime), scale,
                height, false);
        blinking = (flags & TvPainter.FLAG_BLINK) != 0;
        graphing = (flags & TvPainter.FLAG_GRAPH) != 0;
        clocking = (flags & TvPainter.FLAG_CLOCK) != 0;
        iconing = (flags & TvPainter.FLAG_ICON) != 0;
        gifing = (flags & TvPainter.FLAG_GIF) != 0;
        artNext = (flags & TvPainter.FLAG_ART) != 0 ? TvPaintImage.artNext : Long.MAX_VALUE;
        sprites = iconing ? TvPaintIcon.collected() : TvPaintIcon.NO_SPRITES;
        TvTargets.end(graphics);
        mipmaps(target.getColorTextureId());
        revision = tv.revision();
        ppb = effective;
        bytes = TvBudget.bytes(area.width(), area.height(), effective);
    }

    /**
     * Gera os níveis de mipmap da imagem (o GL aloca os que faltam) e põe o filtro: linear entre os níveis de longe,
     * para a Tela não cintilar, o {@link #MAG_FILTER} de perto e o anisotrópico, para a Tela vista de lado não
     * borrar. O {@code TextureTarget} só cria o nível 0 e põe {@code NEAREST} ao criar ou refazer o framebuffer; por
     * isso o filtro vai a cada redesenho, e a {@link TvTargetTexture} não deixa o tipo de render trocá-lo.
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
        graphing = false;
        clocking = false;
        iconing = false;
        gifing = false;
        artNext = Long.MAX_VALUE;
        sprites = TvPaintIcon.NO_SPRITES;
        ppb = 0;
        bytes = 0;
    }
}
