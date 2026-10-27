package io.github.matheusanbs.wirelessautomate.client.tv.render;

import io.github.matheusanbs.wirelessautomate.tv.TvPiece;
import io.github.matheusanbs.wirelessautomate.tv.TvStyle;
import net.minecraft.client.gui.GuiGraphics;

/**
 * A caixa de uma peça: o fundo e a borda, com os cantos cortados em degraus de 1 px de desenho
 * ({@link TvPaintMath#inset}). Sem raio, o fundo é um retângulo e a borda quatro; com raio, uma faixa por linha só
 * nas pontas, e o miolo de uma vez. Nada é alocado.
 */
final class TvPaintBox {
    private TvPaintBox() {
    }

    static void paint(GuiGraphics g, TvPiece piece, TvStyle style, int background, int border) {
        int x0 = piece.x();
        int y0 = piece.y();
        int w = piece.width();
        int h = piece.height();
        int r = Math.min(style.radius(), Math.min(w, h) / 2);
        int b = Math.min(style.borderWidth(), Math.min(w, h) / 2);
        if (background >>> 24 != 0) {
            fillRounded(g, x0, y0, w, h, r, background);
        }
        if (b > 0 && border >>> 24 != 0) {
            if (r == 0) {
                int x1 = x0 + w;
                int y1 = y0 + h;
                g.fill(x0, y0, x1, y0 + b, border);
                g.fill(x0, y1 - b, x1, y1, border);
                g.fill(x0, y0 + b, x0 + b, y1 - b, border);
                g.fill(x1 - b, y0 + b, x1, y1 - b, border);
            } else {
                ring(g, x0, y0, w, h, r, b, border);
            }
        }
    }

    static void fillRounded(GuiGraphics g, int x0, int y0, int w, int h, int r, int color) {
        if (r == 0) {
            g.fill(x0, y0, x0 + w, y0 + h, color);
            return;
        }
        for (int i = 0; i < r; i++) {
            int in = TvPaintMath.inset(r, i);
            g.fill(x0 + in, y0 + i, x0 + w - in, y0 + i + 1, color);
            g.fill(x0 + in, y0 + h - 1 - i, x0 + w - in, y0 + h - i, color);
        }
        g.fill(x0, y0 + r, x0 + w, y0 + h - r, color);
    }

    /**
     * A borda de espessura {@code b} de um retângulo de raio {@code r}: o contorno de fora menos o de dentro (raio
     * {@code r - b}, no mínimo 0), linha a linha nas pontas de cima e de baixo e duas faixas retas no meio.
     */
    private static void ring(GuiGraphics g, int x0, int y0, int w, int h, int r, int b, int color) {
        int k = Math.max(r, b);
        int inner = Math.max(r - b, 0);
        for (int i = 0; i < k; i++) {
            int out = TvPaintMath.inset(r, i);
            if (i < b) {
                g.fill(x0 + out, y0 + i, x0 + w - out, y0 + i + 1, color);
                g.fill(x0 + out, y0 + h - 1 - i, x0 + w - out, y0 + h - i, color);
            } else {
                int in = b + TvPaintMath.inset(inner, i - b);
                g.fill(x0 + out, y0 + i, x0 + in, y0 + i + 1, color);
                g.fill(x0 + w - in, y0 + i, x0 + w - out, y0 + i + 1, color);
                g.fill(x0 + out, y0 + h - 1 - i, x0 + in, y0 + h - i, color);
                g.fill(x0 + w - in, y0 + h - 1 - i, x0 + w - out, y0 + h - i, color);
            }
        }
        g.fill(x0, y0 + k, x0 + b, y0 + h - k, color);
        g.fill(x0 + w - b, y0 + k, x0 + w, y0 + h - k, color);
    }
}
