package io.github.matheusanbs.wirelessautomate.client.tablet;

import static io.github.matheusanbs.wirelessautomate.client.tablet.TabletLayout.BODY_Y;
import static io.github.matheusanbs.wirelessautomate.client.tablet.TabletLayout.ROW_H;
import static io.github.matheusanbs.wirelessautomate.client.tablet.TabletLayout.SIDE_W;
import static io.github.matheusanbs.wirelessautomate.client.tablet.TabletLayout.X0;

import io.github.matheusanbs.wirelessautomate.client.GuiPaint;
import io.github.matheusanbs.wirelessautomate.client.GuiText;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;

/**
 * A lista lateral da aba Redes: o quadro, as linhas visíveis a partir da rolagem, a linha sob o mouse e a seta
 * de "tem mais embaixo". O conteúdo de cada linha é de quem a usa ({@link NetworkList}).
 */
final class SideList {
    private static final Component MORE = Component.literal("▼");
    private final TabletLayout layout;
    private int sideScroll;

    SideList(TabletLayout layout) {
        this.layout = layout;
    }

    int scroll() {
        return sideScroll;
    }

    int rows() {
        return (layout.sideBottom() - BODY_Y) / ROW_H;
    }

    int x() {
        return layout.left + X0;
    }

    /** O alto da linha {@code index} (contada de cima da lista, não da rolagem). */
    int rowY(int index) {
        return layout.top + BODY_Y + (index - sideScroll) * ROW_H;
    }

    /** A linha está na vista. */
    boolean shown(int index) {
        return index >= sideScroll && index < sideScroll + rows();
    }

    /** Linha sob o mouse, ou -1. */
    int rowAt(double mouseX, double mouseY, int count) {
        int x = x();
        int y = layout.top + BODY_Y;
        if (mouseX < x || mouseX >= x + SIDE_W || mouseY < y || mouseY >= y + rows() * ROW_H) {
            return -1;
        }
        int index = (int) ((mouseY - y) / ROW_H) + sideScroll;
        return index < count ? index : -1;
    }

    void mouseScrolled(int step, int count) {
        sideScroll = Mth.clamp(sideScroll + step, 0, Math.max(0, count - rows()));
    }

    /** Põe a linha na vista. */
    void reveal(int index, int count) {
        if (index < sideScroll) {
            sideScroll = index;
        } else if (index >= sideScroll + rows()) {
            sideScroll = index - rows() + 1;
        }
        mouseScrolled(0, count);
    }

    /** O fundo e o contorno; sem linhas, o texto {@code empty}. */
    void renderFrame(GuiGraphics g, Font font, int count, @Nullable Component empty) {
        int x = x();
        int y = layout.top + BODY_Y;
        g.fill(x, y, x + SIDE_W, layout.top + layout.sideBottom(), GuiPaint.INSET);
        GuiPaint.outline(g, x, y, SIDE_W, layout.sideBottom() - BODY_Y, GuiPaint.LINE);
        if (count == 0 && empty != null) {
            GuiText.draw(g, font, empty, x + 4, y + 5, SIDE_W - 8, GuiPaint.MUTED);
        }
    }

    /** A seta embaixo quando há linhas abaixo da vista. */
    void renderMore(GuiGraphics g, Font font, int count) {
        if (sideScroll + rows() < count) {
            GuiPaint.text(g, font, MORE, x() + SIDE_W - 9, layout.top + layout.sideBottom() - 10,
                    GuiPaint.MUTED);
        }
    }
}
