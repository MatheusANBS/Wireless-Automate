package io.github.matheusanbs.wirelessautomate.client.tablet;

import static io.github.matheusanbs.wirelessautomate.client.tablet.TabletLayout.BODY_Y;
import static io.github.matheusanbs.wirelessautomate.client.tablet.TabletLayout.ROW_H;
import static io.github.matheusanbs.wirelessautomate.client.tablet.TabletLayout.SIDE_W;
import static io.github.matheusanbs.wirelessautomate.client.tablet.TabletLayout.X0;

import io.github.matheusanbs.wirelessautomate.client.GuiPaint;
import io.github.matheusanbs.wirelessautomate.client.GuiText;
import java.util.function.IntFunction;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;

/**
 * A lista lateral das abas Redes e Grupos: linhas com o olho na cor, o nome e uma marca à direita, a escolhida
 * em destaque, e a rolagem (uma só, dividida pelas duas abas).
 */
final class SideList {
    /** Uma linha: nome, cor do olho, se é a escolhida e a marca à direita (ou nenhuma). */
    record Row(Component name, int color, boolean selected, @Nullable Component mark) {
    }

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

    /** Linha da lista lateral (redes ou grupos) sob o mouse, ou -1. */
    int rowAt(double mouseX, double mouseY, int count) {
        int x = layout.left + X0;
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

    void render(GuiGraphics g, Font font, int mouseX, int mouseY, int count, IntFunction<Row> rowOf, Component empty) {
        int x = layout.left + X0;
        int y = layout.top + BODY_Y;
        g.fill(x, y, x + SIDE_W, layout.top + layout.sideBottom(), GuiPaint.INSET);
        GuiPaint.outline(g, x, y, SIDE_W, layout.sideBottom() - BODY_Y, GuiPaint.LINE);
        if (count == 0) {
            GuiText.draw(g, font, empty, x + 4, y + 5, SIDE_W - 8, GuiPaint.MUTED);
        }
        int hovered = rowAt(mouseX, mouseY, count);
        for (int i = 0; i < rows() && i + sideScroll < count; i++) {
            int index = i + sideScroll;
            Row row = rowOf.apply(index);
            int ry = y + i * ROW_H;
            if (row.selected) {
                g.fill(x + 1, ry + 1, x + SIDE_W - 1, ry + ROW_H, GuiPaint.ROW_SELECTED);
                g.fill(x + 1, ry + 1, x + 2, ry + ROW_H, GuiPaint.ACCENT);
            } else if (index == hovered) {
                g.fill(x + 1, ry + 1, x + SIDE_W - 1, ry + ROW_H, GuiPaint.ROW_HOVER);
            }
            GuiPaint.eye(g, x + 5, ry + 5, row.color);
            int right = x + SIDE_W - 4;
            if (row.mark != null) {
                GuiPaint.textRight(g, font, row.mark, right, ry + 4, GuiPaint.MUTED);
                right -= font.width(row.mark) + 3;
            }
            GuiText.draw(g, font, row.name, x + 14, ry + 4, right - (x + 14), GuiPaint.FG);
        }
        if (sideScroll + rows() < count) {
            GuiPaint.text(g, font, Component.literal("▼"), x + SIDE_W - 9, layout.top + layout.sideBottom() - 10, GuiPaint.MUTED);
        }
    }
}
