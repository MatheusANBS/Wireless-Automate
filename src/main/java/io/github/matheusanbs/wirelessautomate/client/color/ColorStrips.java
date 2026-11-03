package io.github.matheusanbs.wirelessautomate.client.color;

import io.github.matheusanbs.wirelessautomate.client.GuiPaint;
import net.minecraft.client.gui.GuiGraphics;

/** O desenho das faixas de matiz e de alfa e do xadrez do {@link ColorPopup}: só {@code fill}, sem alocar. */
final class ColorStrips {
    private ColorStrips() {
    }

    /** A faixa de matiz: seis degradês de uma cor cheia à próxima, com a marca em {@code hue}. */
    static void hue(GuiGraphics g, int left, int top, int strip, int h, float hue, boolean framed) {
        if (framed) {
            GuiPaint.outline(g, left - 1, top - 1, strip + 2, h + 2, GuiPaint.FRAME);
        }
        for (int k = 0; k < 6; k++) {
            int from = top + Math.round(k * h / 6f);
            int to = top + Math.round((k + 1) * h / 6f);
            g.fillGradient(left, from, left + strip, to, 0xFF000000 | ColorHsv.toRgb(k / 6f, 1, 1),
                    0xFF000000 | ColorHsv.toRgb((k + 1) / 6f, 1, 1));
        }
        mark(g, left, top + Math.round(hue * (h - 1)), strip, framed);
    }

    /** A faixa de alfa: o xadrez com a cor {@code rgb} do opaco (em cima) ao transparente, com a marca em {@code a}. */
    static void alpha(GuiGraphics g, int left, int top, int strip, int h, int rgb, int a, boolean framed) {
        if (framed) {
            GuiPaint.outline(g, left - 1, top - 1, strip + 2, h + 2, GuiPaint.FRAME);
        }
        checker(g, left, top, strip, h);
        g.fillGradient(left, top, left + strip, top + h, rgb, rgb & 0xFFFFFF);
        mark(g, left, top + Math.round((1 - a / 255f) * (h - 1)), strip, framed);
    }

    private static void mark(GuiGraphics g, int left, int at, int strip, boolean framed) {
        if (framed) {
            g.fill(left - 2, at, left + strip + 2, at + 1, GuiPaint.FG);
        } else {
            g.fill(left - 1, at, left + strip + 1, at + 1, 0xFFFFFFFF);
            g.fill(left - 1, at + 1, left + strip + 1, at + 2, 0xFF000000);
        }
    }

    static void checker(GuiGraphics g, int left, int top, int w, int h) {
        g.fill(left, top, left + w, top + h, GuiPaint.BEVEL_LIGHT);
        for (int row = 0; row * 4 < h; row++) {
            for (int col = row & 1; col * 4 < w; col += 2) {
                g.fill(left + col * 4, top + row * 4, Math.min(left + w, left + col * 4 + 4),
                        Math.min(top + h, top + row * 4 + 4), GuiPaint.INSET);
            }
        }
    }
}
