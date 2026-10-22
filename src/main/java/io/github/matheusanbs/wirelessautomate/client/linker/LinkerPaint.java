package io.github.matheusanbs.wirelessautomate.client.linker;

import io.github.matheusanbs.wirelessautomate.client.FlatButton;
import io.github.matheusanbs.wirelessautomate.client.GuiPaint;
import io.github.matheusanbs.wirelessautomate.client.GuiText;
import io.github.matheusanbs.wirelessautomate.menu.LinkerSnapshot;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/** O desenho dos botões do Vinculador: Vincular e Desvincular no cabeçalho, texto e o botão da área. */
final class LinkerPaint {
    private LinkerPaint() {
    }

    private static Font font() {
        return Minecraft.getInstance().font;
    }

    static void paintChoice(GuiGraphics g, FlatButton b, boolean hovered, Component text, boolean selected) {
        if (selected) {
            GuiPaint.box(g, b.getX(), b.getY(), b.getWidth(), b.getHeight(), GuiPaint.SELECTED, GuiPaint.SELECTED);
        } else {
            GuiPaint.box(g, b.getX(), b.getY(), b.getWidth(), b.getHeight(), GuiPaint.BUTTON,
                    hovered ? GuiPaint.BUTTON_HOVER_BORDER : GuiPaint.BUTTON_BORDER);
        }
        GuiPaint.textCentered(g, font(), text, b.getX() + b.getWidth() / 2 + 1, b.getY() + (b.getHeight() - 8) / 2,
                selected ? GuiPaint.SELECTED_TEXT : GuiPaint.FG);
    }

    static void paintText(GuiGraphics g, FlatButton b, boolean hovered, Component text, int color) {
        int border = hovered ? GuiPaint.BUTTON_HOVER_BORDER : GuiPaint.BUTTON_BORDER;
        GuiPaint.box(g, b.getX(), b.getY(), b.getWidth(), b.getHeight(), b.active ? GuiPaint.BUTTON : GuiPaint.INSET,
                b.active ? border : GuiPaint.LINE);
        GuiPaint.textCentered(g, font(), text, b.getX() + b.getWidth() / 2 + 1, b.getY() + (b.getHeight() - 8) / 2,
                b.active ? color : GuiPaint.DISABLED);
    }

    /** Vincular: botão principal na cor de destaque (no desvincular, a do perigo). */
    static void paintLink(GuiGraphics g, FlatButton b, boolean hovered, LinkerSnapshot s) {
        Font font = font();
        int color = s.unlink() ? GuiPaint.DANGER : GuiPaint.ACCENT;
        if (b.active) {
            int fill = hovered ? GuiPaint.mix(color, GuiPaint.BEVEL_LIGHT, 0.15f) : color;
            GuiPaint.box(g, b.getX(), b.getY(), b.getWidth(), b.getHeight(), fill, GuiPaint.mix(color, GuiPaint.RING, 0.3f));
        } else {
            GuiPaint.box(g, b.getX(), b.getY(), b.getWidth(), b.getHeight(), GuiPaint.INSET, GuiPaint.LINE);
        }
        Component text = LinkerText.linkLabel(s);
        int textW = Math.min(font.width(text), b.getWidth() - 6);
        GuiText.draw(g, font, text, b.getX() + (b.getWidth() - textW) / 2, b.getY() + 4, textW,
                b.active ? GuiPaint.DARK_TEXT : GuiPaint.DISABLED);
    }
}
