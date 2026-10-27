package io.github.matheusanbs.wirelessautomate.client.tv.editor;

import io.github.matheusanbs.wirelessautomate.client.GuiPaint;
import io.github.matheusanbs.wirelessautomate.client.GuiText;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * Os botões desenhados do editor (sem widget: a parte acha o clique pelo retângulo), planos como os das outras telas
 * do mod: o comum com a borda coral sob o mouse, o desligado afundado e apagado, e o principal na cor de destaque. O
 * texto é medido uma vez, pelo {@link #label}.
 */
final class TvEditorPaint {
    private TvEditorPaint() {
    }

    /** O texto de um botão, abreviado em {@code maxWidth} e com a largura que ocupa, medidos uma vez. */
    record Label(GuiText.Fitted text, int width) {
    }

    static Label label(Font font, Component text, int maxWidth) {
        GuiText.Fitted fitted = GuiText.fit(font, text, maxWidth);
        return new Label(fitted, Math.min(maxWidth, font.width(fitted.shown())));
    }

    static void button(GuiGraphics g, Font font, TvRect r, Label label, boolean hovered, boolean enabled) {
        button(g, font, r.x(), r.y(), r.w(), r.h(), label, hovered, enabled);
    }

    /** O botão comum num retângulo dado por inteiros (os controles da coluna de propriedades). */
    static void button(GuiGraphics g, Font font, int x, int y, int w, int h, Label label, boolean hovered,
            boolean enabled) {
        if (enabled) {
            GuiPaint.box(g, x, y, w, h, GuiPaint.BUTTON, hovered ? GuiPaint.BUTTON_HOVER_BORDER : GuiPaint.BUTTON_BORDER);
        } else {
            GuiPaint.box(g, x, y, w, h, GuiPaint.INSET, GuiPaint.LINE);
        }
        GuiText.draw(g, font, label.text(), x + (w - label.width()) / 2, y + (h - 8) / 2,
                enabled ? GuiPaint.FG : GuiPaint.DISABLED);
    }

    /** O botão comum com um ícone de 16 px no meio; desligado, afundado e com o ícone apagado. */
    static void iconButton(GuiGraphics g, TvRect r, TvIcons.Icon icon, boolean hovered, boolean enabled) {
        if (enabled) {
            GuiPaint.box(g, r.x(), r.y(), r.w(), r.h(), GuiPaint.BUTTON,
                    hovered ? GuiPaint.BUTTON_HOVER_BORDER : GuiPaint.BUTTON_BORDER);
        } else {
            GuiPaint.box(g, r.x(), r.y(), r.w(), r.h(), GuiPaint.INSET, GuiPaint.LINE);
        }
        TvIcons.draw(g, icon, r.x() + (r.w() - TvIcons.SIZE) / 2, r.y() + (r.h() - TvIcons.SIZE) / 2, enabled);
    }

    /** O botão comum com o ícone à esquerda e o texto ao lado. */
    static void iconButton(GuiGraphics g, Font font, TvRect r, TvIcons.Icon icon, Label label, boolean hovered) {
        GuiPaint.box(g, r.x(), r.y(), r.w(), r.h(), GuiPaint.BUTTON,
                hovered ? GuiPaint.BUTTON_HOVER_BORDER : GuiPaint.BUTTON_BORDER);
        TvIcons.draw(g, icon, r.x() + 1, r.y() + (r.h() - TvIcons.SIZE) / 2);
        GuiText.draw(g, font, label.text(), r.x() + TvIcons.SIZE + 1, r.y() + (r.h() - 8) / 2, GuiPaint.FG);
    }

    /** O botão principal na cor {@code color}; desligado, afundado e apagado. */
    static void primary(GuiGraphics g, Font font, TvRect r, Label label, boolean hovered, boolean active, int color) {
        if (active) {
            int fill = hovered ? GuiPaint.mix(color, GuiPaint.BEVEL_LIGHT, 0.15f) : color;
            GuiPaint.box(g, r.x(), r.y(), r.w(), r.h(), fill, GuiPaint.mix(color, GuiPaint.RING, 0.3f));
        } else {
            GuiPaint.box(g, r.x(), r.y(), r.w(), r.h(), GuiPaint.INSET, GuiPaint.LINE);
        }
        centered(g, font, r, label, active ? GuiPaint.DARK_TEXT : GuiPaint.DISABLED);
    }

    static void centered(GuiGraphics g, Font font, TvRect r, Label label, int color) {
        GuiText.draw(g, font, label.text(), r.x() + (r.w() - label.width()) / 2, r.y() + (r.h() - 8) / 2, color);
    }
}
