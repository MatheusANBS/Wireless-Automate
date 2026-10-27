package io.github.matheusanbs.wirelessautomate.client.tv.render;

import io.github.matheusanbs.wirelessautomate.tv.TvPartStyle;
import io.github.matheusanbs.wirelessautomate.tv.TvPiece;
import net.minecraft.client.gui.GuiGraphics;

/**
 * A Barra: a trilha (com os cantos da peça) e o cheio por cima, deitado ou em pé, crescendo para a direita ou para
 * cima (invertido: para a esquerda ou para baixo). As regras pintam o cheio. Nada é alocado.
 */
final class TvPaintBar {
    private TvPaintBar() {
    }

    static void paint(GuiGraphics g, TvPiece piece, TvPartStyle part, int radius, double fraction, int fill,
            int track) {
        int x = piece.x();
        int y = piece.y();
        int w = piece.width();
        int h = piece.height();
        if (track >>> 24 != 0) {
            TvPaintBox.fillRounded(g, x, y, w, h, Math.min(radius, Math.min(w, h) / 2), track);
        }
        if (fraction <= 0 || fill >>> 24 == 0) {
            return;
        }
        boolean vertical = part.orientation() == TvPartStyle.Orientation.VERTICAL;
        int length = Math.max(1, Math.round((float) ((vertical ? h : w) * fraction)));
        int fx = x;
        int fy = y;
        int fw = w;
        int fh = h;
        if (vertical) {
            fh = length;
            fy = part.reverse() ? y : y + h - length;
        } else {
            fw = length;
            fx = part.reverse() ? x + w - length : x;
        }
        TvPaintBox.fillRounded(g, fx, fy, fw, fh, Math.min(radius, Math.min(fw, fh) / 2), fill);
    }
}
