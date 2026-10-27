package io.github.matheusanbs.wirelessautomate.client.tv;

import io.github.matheusanbs.wirelessautomate.client.GuiPaint;
import io.github.matheusanbs.wirelessautomate.client.GuiText;
import io.github.matheusanbs.wirelessautomate.client.tv.TvScreenLayout.Rect;
import io.github.matheusanbs.wirelessautomate.client.tv.editor.TvIcons;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * Os botões desenhados da tela da Tela de Sinal (sem widget: a tela acha o clique pelo retângulo). Planos como os
 * das outras telas do mod: o comum com a borda coral sob o mouse, o escolhido aceso, o principal na cor de destaque e
 * o desligado afundado. O texto é medido uma vez, pelo {@link #label}.
 */
final class TvButtons {
    private TvButtons() {
    }

    static void plain(GuiGraphics g, Font font, Rect r, int left, int top, boolean hovered,
            Label text, boolean centered) {
        int x = left + r.x();
        int y = top + r.y();
        GuiPaint.box(g, x, y, r.w(), r.h(), GuiPaint.BUTTON,
                hovered ? GuiPaint.BUTTON_HOVER_BORDER : GuiPaint.BUTTON_BORDER);
        label(g, font, r, x, y, text, centered, GuiPaint.FG);
    }

    /**
     * Um botão de escolha com o ícone à esquerda: o escolhido leva a borda coral e um véu coral sobre o botão do tema
     * (o {@code SELECTED} é porcelana nos dois temas e apagaria o ícone no escuro).
     */
    static void choice(GuiGraphics g, Font font, Rect r, int left, int top, boolean hovered, boolean selected,
            TvIcons.Icon icon, Label text) {
        int x = left + r.x();
        int y = top + r.y();
        GuiPaint.box(g, x, y, r.w(), r.h(), GuiPaint.BUTTON,
                selected ? GuiPaint.ACCENT : hovered ? GuiPaint.BUTTON_HOVER_BORDER : GuiPaint.BUTTON_BORDER);
        if (selected) {
            g.fill(x + 1, y + 1, x + r.w() - 1, y + r.h() - 1, GuiPaint.alpha(GuiPaint.ACCENT, 0x40));
        }
        TvIcons.draw(g, icon, x + TvIcons.PAD, y + (r.h() - TvIcons.SIZE) / 2);
        GuiText.draw(g, font, text.text(), x + TvIcons.LABEL_OFFSET, y + (r.h() - 8) / 2, GuiPaint.FG);
    }

    /** O botão principal (Aplicar), na cor {@code color}; desligado, afundado e apagado. */
    static void primary(GuiGraphics g, Font font, Rect r, int left, int top, boolean hovered, boolean active,
            int color, Label text) {
        int x = left + r.x();
        int y = top + r.y();
        if (active) {
            int fill = hovered ? GuiPaint.mix(color, GuiPaint.BEVEL_LIGHT, 0.15f) : color;
            GuiPaint.box(g, x, y, r.w(), r.h(), fill, GuiPaint.mix(color, GuiPaint.RING, 0.3f));
        } else {
            GuiPaint.box(g, x, y, r.w(), r.h(), GuiPaint.INSET, GuiPaint.LINE);
        }
        label(g, font, r, x, y, text, true, active ? GuiPaint.DARK_TEXT : GuiPaint.DISABLED);
    }

    /** Um ícone fixo (o passo ‹ ou ›) no meio de um botão quadrado. */
    static void symbol(GuiGraphics g, int x, int y, int size, boolean hovered, TvIcons.Icon icon) {
        GuiPaint.box(g, x, y, size, size, GuiPaint.BUTTON,
                hovered ? GuiPaint.BUTTON_HOVER_BORDER : GuiPaint.BUTTON_BORDER);
        TvIcons.draw(g, icon, x + (size - TvIcons.SIZE) / 2, y + (size - TvIcons.SIZE) / 2);
    }

    private static void label(GuiGraphics g, Font font, Rect r, int x, int y, Label text, boolean centered,
            int color) {
        int textX = centered ? x + (r.w() - text.width()) / 2 : x + 4;
        GuiText.draw(g, font, text.text(), textX, y + (r.h() - 8) / 2, color);
    }

    /** O texto de um botão, abreviado em {@code maxWidth} e com a largura que ocupa, medidos uma vez. */
    record Label(GuiText.Fitted text, int width) {
    }

    static Label label(Font font, Component text, int maxWidth) {
        GuiText.Fitted fitted = GuiText.fit(font, text, maxWidth);
        return new Label(fitted, Math.min(maxWidth, font.width(fitted.shown())));
    }
}
