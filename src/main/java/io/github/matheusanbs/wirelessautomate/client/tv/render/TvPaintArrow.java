package io.github.matheusanbs.wirelessautomate.client.tv.render;

import net.minecraft.client.gui.GuiGraphics;

/**
 * A Seta: ▲ quando o dado passa da zona morta para cima, ▼ para baixo e ▬ dentro dela (ou sem número), com a ponta
 * em linhas de 1 px de desenho e a haste de um quarto da largura, como no mockup. {@code reverse} troca ▲ e ▼.
 */
final class TvPaintArrow {
    private TvPaintArrow() {
    }

    /** O sentido do dado: 1 sobe, -1 desce, 0 parado (dentro da zona morta, ou sem número). */
    static int sign(double value, double deadZone, boolean reverse) {
        if (Double.isNaN(value) || Math.abs(value) <= deadZone) {
            return 0;
        }
        return value > 0 != reverse ? 1 : -1;
    }

    static void paint(GuiGraphics g, int x, int y, int w, int h, int sign, int color) {
        if (color >>> 24 == 0) {
            return;
        }
        if (sign == 0) {
            int bar = Math.max(1, Math.round(h * 0.2f));
            int inset = Math.max(0, Math.round(w * 0.1f));
            int top = y + (h - bar) / 2;
            g.fill(x + inset, top, x + w - inset, top + bar, color);
            return;
        }
        int head = Math.max(1, Math.round(h * 0.65f));
        float half = w / 2f;
        float cx = x + half;
        for (int row = 0; row < head; row++) {
            int reach = Math.max(1, (int) Math.ceil(half * (row + 1) / head));
            int ry = sign > 0 ? y + row : y + h - 1 - row;
            g.fill(Math.round(cx - reach), ry, Math.round(cx + reach), ry + 1, color);
        }
        int stem = Math.max(1, Math.round(w * 0.3f));
        int sx = Math.round(cx - stem / 2f);
        int sy = sign > 0 ? y + head : y;
        g.fill(sx, sy, sx + stem, sy + (h - head), color);
    }
}
