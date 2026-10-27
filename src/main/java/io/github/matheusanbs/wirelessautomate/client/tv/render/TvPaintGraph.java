package io.github.matheusanbs.wirelessautomate.client.tv.render;

import io.github.matheusanbs.wirelessautomate.tv.TvGraphHistory;
import io.github.matheusanbs.wirelessautomate.tv.TvPartStyle;
import io.github.matheusanbs.wirelessautomate.tv.TvPiece;
import net.minecraft.client.gui.GuiGraphics;

/**
 * O Gráfico: o histórico da ligação ({@link TvGraphHistory}, um número por segundo) na janela de 1 a 10 minutos, da
 * esquerda (o mais velho) para a direita (agora), em linha (uma faixa de 1 px de desenho por coluna, ligada à
 * anterior) ou em colunas, com a área sob a linha opcional. Enquanto a história é mais curta que a janela, ela ocupa
 * só o lado direito; um segundo sem número deixa um vão. A escala é a do histórico da janela ou a fixa da peça.
 */
final class TvPaintGraph {
    private static final int AREA_ALPHA = 0x30;
    private static final int COLUMN_GAP_MIN_WIDTH = 3;

    private TvPaintGraph() {
    }

    static void paint(GuiGraphics g, TvPiece piece, TvPartStyle part, TvGraphHistory history, int color) {
        int w = piece.width();
        int h = piece.height();
        if (color >>> 24 == 0 || w < 2 || h < 1) {
            return;
        }
        int seconds = TvGraphHistory.seconds(part.window());
        double[] scale = history.scale(seconds, part.min(), part.max());
        if (part.mode() == TvPartStyle.GraphMode.COLUMNS) {
            columns(g, piece, history, seconds, scale, color);
        } else {
            line(g, piece, part, history, seconds, scale, color);
        }
    }

    /** A linha de y (em pixels, 0 em cima) de um número na escala. */
    private static int rowOf(TvPiece piece, double value, double[] scale) {
        double t = Math.max(0, Math.min(1, (value - scale[0]) / (scale[1] - scale[0])));
        return piece.y() + (int) Math.round((piece.height() - 1) * (1 - t));
    }

    private static void line(GuiGraphics g, TvPiece piece, TvPartStyle part, TvGraphHistory history, int seconds,
            double[] scale, int color) {
        int w = piece.width();
        int bottom = piece.y() + piece.height();
        int areaColor = areaColor(color);
        int previous = Integer.MIN_VALUE;
        for (int col = 0; col < w; col++) {
            double age = (seconds - 1) * (1.0 - col / (double) (w - 1));
            double value = sample(history, age);
            if (Double.isNaN(value)) {
                previous = Integer.MIN_VALUE;
                continue;
            }
            int row = rowOf(piece, value, scale);
            int x = piece.x() + col;
            if (part.area()) {
                g.fill(x, row + 1, x + 1, bottom, areaColor);
            }
            int from = previous == Integer.MIN_VALUE ? row : Math.min(previous, row);
            int to = previous == Integer.MIN_VALUE ? row : Math.max(previous, row);
            g.fill(x, from, x + 1, to + 1, color);
            previous = row;
        }
    }

    private static void columns(GuiGraphics g, TvPiece piece, TvGraphHistory history, int seconds, double[] scale,
            int color) {
        int w = piece.width();
        int bars = Math.max(1, Math.min(seconds, w / 2));
        double bucket = seconds / (double) bars;
        int bottom = piece.y() + piece.height();
        for (int bar = 0; bar < bars; bar++) {
            int x0 = piece.x() + bar * w / bars;
            int x1 = piece.x() + (bar + 1) * w / bars;
            double sum = 0;
            int n = 0;
            double newest = (bars - 1 - bar) * bucket;
            for (int s = 0; s < Math.ceil(bucket); s++) {
                double v = history.ago((int) (newest + s));
                if (!Double.isNaN(v)) {
                    sum += v;
                    n++;
                }
            }
            if (n == 0) {
                continue;
            }
            int row = rowOf(piece, sum / n, scale);
            int gap = x1 - x0 >= COLUMN_GAP_MIN_WIDTH ? 1 : 0;
            g.fill(x0, row, x1 - gap, bottom, color);
        }
    }

    /** O número {@code age} segundos atrás (fracionário: interpola entre dois segundos); {@code NaN} fora do que há. */
    private static double sample(TvGraphHistory history, double age) {
        int lo = (int) Math.floor(age);
        double a = history.ago(lo);
        if (lo == age) {
            return a;
        }
        double b = history.ago(lo + 1);
        if (Double.isNaN(a) || Double.isNaN(b)) {
            return Double.isNaN(a) ? Double.NaN : a;
        }
        double t = age - lo;
        return a * (1 - t) + b * t;
    }

    private static int areaColor(int color) {
        return (color >>> 24) * AREA_ALPHA / 255 << 24 | color & 0xFFFFFF;
    }
}
