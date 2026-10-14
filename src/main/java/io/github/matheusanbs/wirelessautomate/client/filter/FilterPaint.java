package io.github.matheusanbs.wirelessautomate.client.filter;

import io.github.matheusanbs.wirelessautomate.client.FlatButton;
import io.github.matheusanbs.wirelessautomate.client.GuiPaint;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

/** O desenho dos botões planos da tela de filtro e o texto quebrado com reticências na última linha. */
final class FilterPaint {
    private FilterPaint() {
    }

    private static Font font() {
        return Minecraft.getInstance().font;
    }

    static void paintTextButton(GuiGraphics g, FlatButton b, boolean hovered, Component text, boolean pressed) {
        Font font = font();
        int border = pressed ? GuiPaint.ACCENT : hovered ? GuiPaint.BUTTON_HOVER_BORDER : GuiPaint.BUTTON_BORDER;
        GuiPaint.box(g, b.getX(), b.getY(), b.getWidth(), b.getHeight(), b.active ? GuiPaint.BUTTON : GuiPaint.INSET,
                b.active ? border : GuiPaint.LINE);
        FormattedCharSequence label = GuiPaint.ellipsize(font, text, b.getWidth() - 4);
        g.drawString(font, label, b.getX() + (b.getWidth() - font.width(label)) / 2 + 1, b.getY() + (b.getHeight() - 8) / 2,
                b.active ? GuiPaint.FG : GuiPaint.DISABLED, false);
    }

    /** Botão principal: coral, com o texto em porcelana. */
    static void paintPrimary(GuiGraphics g, FlatButton b, boolean hovered, Component text) {
        if (!b.active) {
            paintTextButton(g, b, false, text, false);
            return;
        }
        Font font = font();
        int fill = hovered ? GuiPaint.ACCENT : GuiPaint.ACCENT_DEEP;
        GuiPaint.box(g, b.getX(), b.getY(), b.getWidth(), b.getHeight(), fill, fill);
        FormattedCharSequence label = GuiPaint.ellipsize(font, text, b.getWidth() - 4);
        g.drawString(font, label, b.getX() + (b.getWidth() - font.width(label)) / 2 + 1, b.getY() + (b.getHeight() - 8) / 2,
                GuiPaint.SELECTED_TEXT, false);
    }

    /** Remover e Limpar: neutros até o mouse chegar; armado (esperando confirmação), vermelhos. */
    static void paintDangerButton(GuiGraphics g, FlatButton b, boolean hovered, Component text, boolean armed) {
        if (!b.active) {
            paintTextButton(g, b, false, text, false);
            return;
        }
        int fill = armed ? GuiPaint.mix(GuiPaint.BUTTON, GuiPaint.DANGER, 0.3f) : GuiPaint.BUTTON;
        int border = armed || hovered ? GuiPaint.DANGER : GuiPaint.BUTTON_BORDER;
        GuiPaint.box(g, b.getX(), b.getY(), b.getWidth(), b.getHeight(), fill, border);
        GuiPaint.textCentered(g, font(), text, b.getX() + b.getWidth() / 2 + 1, b.getY() + (b.getHeight() - 8) / 2,
                armed || hovered ? GuiPaint.DANGER : GuiPaint.FG);
    }

    /** Parte de um controle segmentado (lista, abas): a escolhida fica cheia de grafite. */
    static void paintSegment(GuiGraphics g, FlatButton b, boolean hovered, Component text, boolean selected) {
        Font font = font();
        if (selected) {
            GuiPaint.box(g, b.getX(), b.getY(), b.getWidth(), b.getHeight(), GuiPaint.SELECTED, GuiPaint.SELECTED);
        } else {
            GuiPaint.box(g, b.getX(), b.getY(), b.getWidth(), b.getHeight(), GuiPaint.BUTTON,
                    hovered ? GuiPaint.BUTTON_HOVER_BORDER : GuiPaint.BUTTON_BORDER);
        }
        FormattedCharSequence label = GuiPaint.ellipsize(font, text, b.getWidth() - 4);
        g.drawString(font, label, b.getX() + (b.getWidth() - font.width(label)) / 2, b.getY() + (b.getHeight() - 8) / 2,
                selected ? GuiPaint.SELECTED_TEXT : GuiPaint.FG, false);
    }

    /** Uma opção de — / Sim / Não: a escolhida cheia (Não em vermelho). */
    static void paintTri(GuiGraphics g, FlatButton b, boolean hovered, Component text, boolean selected, boolean negative) {
        int color = negative ? GuiPaint.DANGER : GuiPaint.SELECTED;
        if (selected) {
            GuiPaint.box(g, b.getX(), b.getY(), b.getWidth(), b.getHeight(), color, color);
        } else {
            GuiPaint.box(g, b.getX(), b.getY(), b.getWidth(), b.getHeight(), GuiPaint.BUTTON,
                    hovered ? GuiPaint.BUTTON_HOVER_BORDER : GuiPaint.BUTTON_BORDER);
        }
        GuiPaint.textCentered(g, font(), text, b.getX() + b.getWidth() / 2 + 1, b.getY() + (b.getHeight() - 8) / 2 + 1,
                selected ? GuiPaint.SELECTED_TEXT : GuiPaint.MUTED);
    }

    /** Caixa de marcar com o texto ao lado, sem moldura. */
    static void paintCheck(GuiGraphics g, FlatButton b, boolean hovered, Component text, boolean on) {
        GuiPaint.checkbox(g, b.getX(), b.getY() + 1, on, GuiPaint.ACCENT);
        GuiPaint.text(g, font(), GuiPaint.ellipsize(font(), text, 64), b.getX() + 12, b.getY() + 2,
                on || hovered ? GuiPaint.FG : GuiPaint.MUTED);
    }

    /** Texto quebrado em linhas até {@code maxLines}; devolve quantas linhas usou. */
    static int wrapped(GuiGraphics g, Component text, int x, int y, int width, int maxLines, int color) {
        Font font = font();
        List<FormattedCharSequence> lines = font.split(text, width);
        int n = Math.min(lines.size(), maxLines);
        for (int i = 0; i < n; i++) {
            FormattedCharSequence line = lines.get(i);
            if (i == n - 1 && lines.size() > n) {
                // a última linha que cabe leva as reticências
                StringBuilder rest = new StringBuilder();
                for (int j = i; j < lines.size(); j++) {
                    lines.get(j).accept((index, style, codePoint) -> {
                        rest.appendCodePoint(codePoint);
                        return true;
                    });
                    rest.append(' ');
                }
                line = GuiPaint.ellipsize(font, Component.literal(rest.toString().strip()), width);
            }
            GuiPaint.text(g, font, line, x, y + i * 10, color);
        }
        return n;
    }
}
