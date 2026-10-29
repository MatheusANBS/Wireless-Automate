package io.github.matheusanbs.wirelessautomate.client.tv.render;

import com.mojang.blaze3d.vertex.VertexConsumer;
import io.github.matheusanbs.wirelessautomate.tv.TvBackground;
import io.github.matheusanbs.wirelessautomate.tv.TvLayout;
import io.github.matheusanbs.wirelessautomate.tv.TvPartStyle;
import io.github.matheusanbs.wirelessautomate.tv.TvPiece;
import io.github.matheusanbs.wirelessautomate.tv.TvPixelArt;
import net.minecraft.client.gui.GuiGraphics;
import org.joml.Matrix4f;

/**
 * A pixel art na textura da Tela, só no redesenho: a peça Imagem e o fundo em mosaico. Cada uma é um quadrilátero só
 * com a textura da arte ({@link TvArtTextures}, {@code NEAREST}): na Imagem, só a parte que cabe na caixa; no
 * mosaico, a Tela inteira, com a textura repetida pelas coordenadas além de 1. A opacidade vai na cor do vértice. Só a
 * thread de render desenha.
 */
final class TvPaintImage {
    private TvPaintImage() {
    }

    /**
     * A Imagem na caixa da peça: na escala inteira, alinhada pelo {@code align} do estilo e pelo {@code valign} e
     * cortada pela caixa; com a escala 0, esticada até a caixa. Sem arte no layout, nada.
     */
    static void piece(GuiGraphics g, TvPiece piece, TvLayout layout) {
        TvPartStyle part = piece.style().part();
        TvPixelArt art = part.image().isPresent() ? layout.image(part.image().get()) : null;
        int opacity = piece.style().opacity();
        if (art == null || opacity <= 0) {
            return;
        }
        int x = piece.x();
        int y = piece.y();
        int w = piece.width();
        int h = piece.height();
        if (part.scale() == 0) {
            quad(g, art, opacity, x, y, x + w, y + h, 0, 0, 1, 1);
            return;
        }
        int s = part.scale();
        int artW = art.width() * s;
        int artH = art.height() * s;
        // A ordem do TvAlign (esquerda, centro, direita) e a do VAlign (cima, meio, baixo) são 0, 1 e 2: a metade da
        // sobra vezes a posição.
        int ox = x + Math.floorDiv((w - artW) * piece.style().align().ordinal(), 2);
        int oy = y + Math.floorDiv((h - artH) * part.valign().ordinal(), 2);
        int x0 = Math.max(ox, x);
        int y0 = Math.max(oy, y);
        int x1 = Math.min(ox + artW, x + w);
        int y1 = Math.min(oy + artH, y + h);
        if (x0 < x1 && y0 < y1) {
            quad(g, art, opacity, x0, y0, x1, y1, (x0 - ox) / (float) artW, (y0 - oy) / (float) artH,
                    (x1 - ox) / (float) artW, (y1 - oy) / (float) artH);
        }
    }

    /** O mosaico do fundo: a arte repetida a partir do canto de cima à esquerda, cortada pela Tela. */
    static void tiled(GuiGraphics g, TvLayout layout, int width, int height) {
        TvBackground background = layout.background();
        TvPixelArt art = background.image().isPresent() ? layout.image(background.image().get()) : null;
        if (art == null || background.opacity() <= 0) {
            return;
        }
        int s = background.scale();
        quad(g, art, background.opacity(), 0, 0, width, height, 0, 0, width / (float) (art.width() * s),
                height / (float) (art.height() * s));
    }

    /**
     * Um quadrilátero com a textura da arte de ({@code u0}, {@code v0}) a ({@code u1}, {@code v1}), na ordem de
     * vértices do {@code GuiGraphics.fill} (a mesma face para o descarte de faces). A cor do vértice é o branco com o
     * alfa do {@link TvPaintMath#withOpacity}, o mesmo que a opacidade dá a uma cor opaca. O lote antes e o deste
     * quadrilátero vão ao GL na hora, para a ordem das peças valer.
     */
    private static void quad(GuiGraphics g, TvPixelArt art, int opacity, int x0, int y0, int x1, int y1, float u0,
            float v0, float u1, float v1) {
        int color = TvPaintMath.withOpacity(0xFFFFFFFF, opacity);
        g.flush();
        Matrix4f matrix = g.pose().last().pose();
        VertexConsumer buffer = g.bufferSource().getBuffer(TvArtTextures.renderType(art));
        buffer.addVertex(matrix, x1, y1, 0).setUv(u1, v1).setColor(color);
        buffer.addVertex(matrix, x1, y0, 0).setUv(u1, v0).setColor(color);
        buffer.addVertex(matrix, x0, y0, 0).setUv(u0, v0).setColor(color);
        buffer.addVertex(matrix, x0, y1, 0).setUv(u0, v1).setColor(color);
        g.flush();
    }
}
