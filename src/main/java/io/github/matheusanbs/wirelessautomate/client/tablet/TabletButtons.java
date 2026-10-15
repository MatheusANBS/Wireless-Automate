package io.github.matheusanbs.wirelessautomate.client.tablet;

import io.github.matheusanbs.wirelessautomate.client.FlatButton;
import io.github.matheusanbs.wirelessautomate.client.GuiPaint;
import io.github.matheusanbs.wirelessautomate.client.GuiText;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/** O desenho dos botões fixos do Tablet ({@link FlatButton}): aba, texto, opção, alternância, principal e perigo. */
final class TabletButtons {
    private TabletButtons() {
    }

    private static Font font() {
        return Minecraft.getInstance().font;
    }

    static void paintTab(GuiGraphics g, FlatButton b, boolean hovered, boolean chosen) {
        GuiPaint.tab(g, b.getX(), b.getY(), b.getWidth(), b.getHeight(), chosen, hovered);
        GuiPaint.textCentered(g, font(), b.getMessage(), b.getX() + b.getWidth() / 2 + 1, b.getY() + 4,
                GuiPaint.tabText(chosen, hovered));
    }

    static void paintText(GuiGraphics g, FlatButton b, boolean hovered, Component text) {
        int border = hovered ? GuiPaint.BUTTON_HOVER_BORDER : GuiPaint.BUTTON_BORDER;
        GuiPaint.box(g, b.getX(), b.getY(), b.getWidth(), b.getHeight(), b.active ? GuiPaint.BUTTON : GuiPaint.INSET,
                b.active ? border : GuiPaint.LINE);
        GuiPaint.textCentered(g, font(), text, b.getX() + b.getWidth() / 2 + 1, b.getY() + (b.getHeight() - 8) / 2,
                b.active ? GuiPaint.FG : GuiPaint.DISABLED);
    }

    /** Botão que troca entre opções: o valor atual e a seta. */
    static void paintCycle(GuiGraphics g, FlatButton b, boolean hovered, Component value) {
        int border = hovered ? GuiPaint.BUTTON_HOVER_BORDER : GuiPaint.BUTTON_BORDER;
        GuiPaint.box(g, b.getX(), b.getY(), b.getWidth(), b.getHeight(), GuiPaint.BUTTON, border);
        GuiText.draw(g, font(), value, b.getX() + 4, b.getY() + 3, b.getWidth() - 14, GuiPaint.FG);
        GuiPaint.arrowDown(g, b.getX() + b.getWidth() - 9, b.getY() + 6, hovered ? GuiPaint.FG : GuiPaint.MUTED);
    }

    static void paintToggle(GuiGraphics g, FlatButton b, boolean hovered, Component text, boolean on) {
        int fill = on ? GuiPaint.mix(GuiPaint.BUTTON, GuiPaint.ACCENT, 0.3f) : GuiPaint.BUTTON;
        int border = on ? GuiPaint.ACCENT : hovered ? GuiPaint.BUTTON_HOVER_BORDER : GuiPaint.BUTTON_BORDER;
        GuiPaint.box(g, b.getX(), b.getY(), b.getWidth(), b.getHeight(), fill, border);
        GuiPaint.textCentered(g, font(), text, b.getX() + b.getWidth() / 2 + 1, b.getY() + 3, GuiPaint.FG);
    }

    static void paintSegment(GuiGraphics g, FlatButton b, boolean hovered, Component text, boolean on) {
        paintToggle(g, b, hovered, text, on);
    }

    /** Botão principal: coral, com o texto em porcelana. */
    static void paintPrimary(GuiGraphics g, FlatButton b, boolean hovered, Component text) {
        if (!b.active) {
            paintText(g, b, hovered, text);
            return;
        }
        int fill = hovered ? GuiPaint.ACCENT : GuiPaint.ACCENT_DEEP;
        GuiPaint.box(g, b.getX(), b.getY(), b.getWidth(), b.getHeight(), fill, fill);
        GuiPaint.textCentered(g, font(), text, b.getX() + b.getWidth() / 2 + 1, b.getY() + (b.getHeight() - 8) / 2,
                GuiPaint.SELECTED_TEXT);
    }

    static void paintDanger(GuiGraphics g, FlatButton b, boolean hovered, Component text, boolean armed) {
        int fill = armed ? GuiPaint.mix(GuiPaint.BUTTON, GuiPaint.DANGER, 0.3f) : GuiPaint.BUTTON;
        int border = armed || hovered ? GuiPaint.DANGER : GuiPaint.BUTTON_BORDER;
        GuiPaint.box(g, b.getX(), b.getY(), b.getWidth(), b.getHeight(), fill, border);
        GuiPaint.textCentered(g, font(), text, b.getX() + b.getWidth() / 2 + 1, b.getY() + 3,
                armed || hovered ? GuiPaint.DANGER : GuiPaint.FG);
    }

    /** Botão de texto que abrevia pelo {@link GuiText} quando o texto não cabe (o inteiro vai na dica). */
    static void paintClipped(GuiGraphics g, FlatButton b, boolean hovered, Component text) {
        Font font = font();
        int border = hovered && b.active ? GuiPaint.BUTTON_HOVER_BORDER : GuiPaint.BUTTON_BORDER;
        GuiPaint.box(g, b.getX(), b.getY(), b.getWidth(), b.getHeight(), b.active ? GuiPaint.BUTTON : GuiPaint.INSET,
                b.active ? border : GuiPaint.LINE);
        int room = b.getWidth() - 8;
        int textW = Math.min(font.width(text), room);
        GuiText.draw(g, font, text, b.getX() + 4 + (room - textW + 1) / 2, b.getY() + (b.getHeight() - 8) / 2, room,
                b.active ? GuiPaint.FG : GuiPaint.DISABLED);
    }

    /** O "Nova rede" ou "Novo grupo" recolhível: a seta e o rótulo. */
    static void paintMore(GuiGraphics g, FlatButton b, boolean hovered, Component label, boolean open) {
        int color = hovered || open ? GuiPaint.FG : GuiPaint.MUTED;
        if (open) {
            GuiPaint.arrowDown(g, b.getX() + 1, b.getY() + 3, color);
        } else {
            GuiPaint.arrowRight(g, b.getX() + 2, b.getY() + 2, color);
        }
        GuiPaint.text(g, font(), label, b.getX() + 10, b.getY() + 1, color);
    }
}
