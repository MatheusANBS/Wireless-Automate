package io.github.matheusanbs.wirelessautomate.client.tv.render;

import com.mojang.blaze3d.vertex.VertexConsumer;
import io.github.matheusanbs.wirelessautomate.client.tv.image.TvImageReceiver;
import io.github.matheusanbs.wirelessautomate.tv.TvBackground;
import io.github.matheusanbs.wirelessautomate.tv.TvLayout;
import io.github.matheusanbs.wirelessautomate.tv.TvPartStyle;
import io.github.matheusanbs.wirelessautomate.tv.TvPicture;
import io.github.matheusanbs.wirelessautomate.tv.TvPiece;
import io.github.matheusanbs.wirelessautomate.tv.TvPixelArt;
import io.github.matheusanbs.wirelessautomate.tv.TvPixels;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderType;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;

/**
 * A pixel art e a imagem enviada na textura da Tela, só no redesenho: a peça Imagem e o fundo em mosaico. Cada uma é
 * um quadrilátero só com a textura ({@link TvArtTextures} ou {@link TvPictureTextures}): na Imagem, só a parte que cabe
 * na caixa; no mosaico, a Tela inteira, com a textura repetida pelas coordenadas além de 1. A opacidade vai na cor do
 * vértice. A imagem enviada que ainda não está pronta (não chegou, está subindo, não abre, ou o servidor desligou as
 * imagens) vira o quadro neutro no mesmo lugar, do tamanho que ela terá; a de outro jogador também, com a opção do
 * cliente "Mostrar imagens de outros jogadores" desligada ({@link #showOthers}), e essa nem é carregada. Um GIF
 * animado desenhado marca {@link #animated}, que o {@link TvPainter} devolve como {@code FLAG_GIF}. A pixel art
 * animada desenha o quadro do relógio ({@link TvPixelArt#frameAt}, o mesmo zero para todas as Telas) e guarda em
 * {@link #artNext} quando o próximo quadro de alguma arte do desenho começa, para o {@link TvTimed} redesenhar a Tela
 * à vista só então. Só a thread de render desenha.
 */
final class TvPaintImage {
    /** O quadro neutro: um cinza escuro com a borda mais clara, nas cores de nenhum tema. */
    private static final int NEUTRAL = 0xFF24272C;
    private static final int NEUTRAL_EDGE = 0xFF3C424B;
    /** Se o último desenho teve um GIF animado; o {@link TvPainter} zera antes de cada desenho. */
    static boolean animated;
    /**
     * O instante (ms, {@code Util.getMillis}) em que o quadro de alguma pixel art animada do último desenho muda, ou
     * {@link Long#MAX_VALUE} sem nenhuma; o {@link TvPainter} zera antes de cada desenho.
     */
    static long artNext = Long.MAX_VALUE;
    /** O relógio do desenho da vez, lido uma vez pelo {@link TvPainter}: todas as artes do desenho usam o mesmo. */
    static long nowMs;
    /** O quadro da última pixel art animada desenhada (gancho do e2e, {@link TvTimed#artFrameForTests}). */
    static int lastArtFrame = -1;
    /** A opção do cliente lida no tick ({@link TvCanvases}): falsa, as imagens de outros jogadores ficam neutras. */
    static boolean showOthers = true;

    private TvPaintImage() {
    }

    /** O que a peça ou o fundo apontam: o tamanho da fonte e a textura ({@code null}: o quadro neutro). */
    private record Source(int width, int height, @Nullable RenderType type) {
    }

    /** A arte ou a imagem do id no layout, ou {@code null} se ele não tem nenhuma das duas. */
    private static @Nullable Source source(TvLayout layout, Optional<UUID> id) {
        if (id.isEmpty()) {
            return null;
        }
        TvPixelArt art = layout.image(id.get());
        if (art != null) {
            if (!art.animated()) {
                return new Source(art.width(), art.height(), TvArtTextures.renderType(art));
            }
            artNext = Math.min(artNext, TvPixels.nextChange(nowMs, art.fps()));
            lastArtFrame = art.frameAt(nowMs);
            return new Source(art.width(), art.height(), TvArtTextures.renderType(art, lastArtFrame));
        }
        TvPicture picture = layout.picture(id.get());
        if (picture == null) {
            return null;
        }
        if (!showOthers && !mine(picture)) {
            return new Source(picture.width(), picture.height(), null);
        }
        animated |= picture.animated();
        return new Source(picture.width(), picture.height(), TvPictureTextures.renderType(picture));
    }

    /**
     * Se a imagem foi enviada por este jogador (pelo autor do layout ou, sem ele, pelo que veio com os bytes); sem
     * autor conhecido, conta como de outro.
     */
    static boolean mine(TvPicture picture) {
        UUID author = picture.author().orElseGet(() -> TvImageReceiver.author(picture.hash()));
        Minecraft minecraft = Minecraft.getInstance();
        return author != null && minecraft.player != null && author.equals(minecraft.player.getUUID());
    }

    /**
     * A Imagem na caixa da peça: na escala inteira, alinhada pelo {@code align} do estilo e pelo {@code valign} e
     * cortada pela caixa; com a escala 0, esticada até a caixa. Sem arte nem imagem no layout, nada.
     */
    static void piece(GuiGraphics g, TvPiece piece, TvLayout layout) {
        TvPartStyle part = piece.style().part();
        int opacity = piece.style().opacity();
        Source source = opacity <= 0 ? null : source(layout, part.image());
        if (source == null) {
            return;
        }
        int x = piece.x();
        int y = piece.y();
        int w = piece.width();
        int h = piece.height();
        if (part.scale() == 0) {
            draw(g, source.type, opacity, x, y, x + w, y + h, 0, 0, 1, 1);
            return;
        }
        int s = part.scale();
        int artW = source.width * s;
        int artH = source.height * s;
        // A ordem do TvAlign (esquerda, centro, direita) e a do VAlign (cima, meio, baixo) são 0, 1 e 2: a metade da
        // sobra vezes a posição.
        int ox = x + Math.floorDiv((w - artW) * piece.style().align().ordinal(), 2);
        int oy = y + Math.floorDiv((h - artH) * part.valign().ordinal(), 2);
        int x0 = Math.max(ox, x);
        int y0 = Math.max(oy, y);
        int x1 = Math.min(ox + artW, x + w);
        int y1 = Math.min(oy + artH, y + h);
        if (x0 < x1 && y0 < y1) {
            draw(g, source.type, opacity, x0, y0, x1, y1, (x0 - ox) / (float) artW, (y0 - oy) / (float) artH,
                    (x1 - ox) / (float) artW, (y1 - oy) / (float) artH);
        }
    }

    /** O mosaico do fundo: a arte ou a imagem repetida a partir do canto de cima à esquerda, cortada pela Tela. */
    static void tiled(GuiGraphics g, TvLayout layout, int width, int height) {
        TvBackground background = layout.background();
        Source source = background.opacity() <= 0 ? null : source(layout, background.image());
        if (source == null) {
            return;
        }
        int s = background.scale();
        draw(g, source.type, background.opacity(), 0, 0, width, height, 0, 0, width / (float) (source.width * s),
                height / (float) (source.height * s));
    }

    private static void draw(GuiGraphics g, @Nullable RenderType type, int opacity, int x0, int y0, int x1, int y1,
            float u0, float v0, float u1, float v1) {
        if (type == null) {
            neutral(g, opacity, x0, y0, x1, y1);
        } else {
            quad(g, type, opacity, x0, y0, x1, y1, u0, v0, u1, v1);
        }
    }

    /** O quadro neutro no lugar de uma imagem que não está pronta: o fundo e uma borda de 1 px. */
    private static void neutral(GuiGraphics g, int opacity, int x0, int y0, int x1, int y1) {
        g.fill(x0, y0, x1, y1, TvPaintMath.withOpacity(NEUTRAL_EDGE, opacity));
        if (x1 - x0 > 2 && y1 - y0 > 2) {
            g.fill(x0 + 1, y0 + 1, x1 - 1, y1 - 1, TvPaintMath.withOpacity(NEUTRAL, opacity));
        }
    }

    /**
     * Um quadrilátero com a textura de ({@code u0}, {@code v0}) a ({@code u1}, {@code v1}), na ordem de vértices do
     * {@code GuiGraphics.fill} (a mesma face para o descarte de faces). A textura tem o alfa pré-multiplicado
     * ({@link TvRenderTypes#art}), então a cor do vértice é a opacidade nos quatro canais: o alfa do
     * {@link TvPaintMath#withOpacity} repetido no vermelho, no verde e no azul. O lote antes e o deste quadrilátero vão
     * ao GL na hora, para a ordem das peças valer.
     */
    private static void quad(GuiGraphics g, RenderType type, int opacity, int x0, int y0, int x1, int y1, float u0,
            float v0, float u1, float v1) {
        int alpha = TvPaintMath.withOpacity(0xFFFFFFFF, opacity) >>> 24;
        int color = alpha << 24 | alpha << 16 | alpha << 8 | alpha;
        g.flush();
        Matrix4f matrix = g.pose().last().pose();
        VertexConsumer buffer = g.bufferSource().getBuffer(type);
        buffer.addVertex(matrix, x1, y1, 0).setUv(u1, v1).setColor(color);
        buffer.addVertex(matrix, x1, y0, 0).setUv(u1, v0).setColor(color);
        buffer.addVertex(matrix, x0, y0, 0).setUv(u0, v0).setColor(color);
        buffer.addVertex(matrix, x0, y1, 0).setUv(u0, v1).setColor(color);
        g.flush();
    }
}
