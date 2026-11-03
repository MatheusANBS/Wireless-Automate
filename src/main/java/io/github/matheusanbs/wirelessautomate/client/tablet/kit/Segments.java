package io.github.matheusanbs.wirelessautomate.client.tablet.kit;

import io.github.matheusanbs.wirelessautomate.client.GuiPaint;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * A escolha entre opções do Tablet (mockup {@code segmentos}): o escolhido claro e emoldurado em {@code FG}, os outros
 * planos e apagados. As larguras são medidas uma vez ({@link #measure}, ou na primeira chamada de {@link #render}), e
 * a geometria sai do {@code SegmentLayout}.
 */
public final class Segments {
    private static final int H = 18;

    private final Component[] labels;
    private int[] widths;
    private int total;

    public Segments(Component... labels) {
        this.labels = labels;
    }

    /** Mede os rótulos; a anfitriã chama quando o texto ou o idioma muda. */
    public void measure(Font font) {
        int[] texts = new int[labels.length];
        for (int i = 0; i < texts.length; i++) {
            texts[i] = font.width(labels[i]);
        }
        widths = SegmentLayout.widths(texts, false);
        total = 0;
        for (int w : widths) {
            total += w;
        }
        total -= Math.max(0, widths.length - 1);
    }

    /** Os não escolhidos primeiro e o escolhido por último, para a moldura dele não ser coberta pelo vizinho. */
    public void render(GuiGraphics g, Font font, int x, int y, int chosen, int mx, int my) {
        if (widths == null) {
            measure(font);
        }
        int[] lefts = SegmentLayout.lefts(x, widths);
        for (int pass = 0; pass < 2; pass++) {
            for (int i = 0; i < widths.length; i++) {
                if ((i == chosen) == (pass == 1)) {
                    part(g, font, lefts[i], y, widths[i], labels[i], i == chosen);
                }
            }
        }
    }

    private static void part(GuiGraphics g, Font font, int x, int y, int w, Component label, boolean on) {
        GuiPaint.outline(g, x, y, w, H, on ? GuiPaint.FG : GuiPaint.FRAME);
        g.fill(x + 1, y + 1, x + w - 1, y + H - 1, on ? GuiPaint.BEVEL_LIGHT : GuiPaint.FACE);
        GuiPaint.text(g, font, label, x + 6, y + 6, on ? GuiPaint.FG : GuiPaint.MUTED);
    }

    /** O índice sob o mouse, ou −1. */
    public int clicked(int x, int y, double mx, double my) {
        if (widths == null || my < y || my >= y + H) {
            return -1;
        }
        return SegmentLayout.at(x, widths, mx);
    }

    public int width() {
        return total;
    }

    /** O centro da parte {@code index} na tela (para o e2e clicar). */
    public int[] center(int x, int y, int index) {
        int[] lefts = SegmentLayout.lefts(x, widths);
        return new int[] {lefts[index] + widths[index] / 2, y + H / 2};
    }
}
