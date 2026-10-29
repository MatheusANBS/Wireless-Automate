package io.github.matheusanbs.wirelessautomate.client.tv.editor;

import io.github.matheusanbs.wirelessautomate.client.GuiPaint;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.FormattedCharSequence;

/**
 * O aviso no lugar do editor (da Tela ou de pixel art) numa janela abaixo do mínimo: o pedido de uma escala menor,
 * quebrado em linhas centradas, medidas só quando a janela muda.
 */
final class TvTooSmall {
    private List<FormattedCharSequence> lines = List.of();
    private int[] widths = new int[0];
    private Font font;
    private TvRect box = TvRect.NONE;

    void layout(Font font, TvRect box) {
        this.font = font;
        this.box = box;
        lines = font.split(TvEditorText.key("too_small"), Math.max(40, box.w() - 24));
        widths = new int[lines.size()];
        for (int i = 0; i < widths.length; i++) {
            widths[i] = font.width(lines.get(i));
        }
    }

    void render(GuiGraphics g) {
        int lineY = box.y() + (box.h() - lines.size() * 10) / 2;
        for (int i = 0; i < lines.size(); i++) {
            GuiPaint.text(g, font, lines.get(i), box.x() + (box.w() - widths[i]) / 2, lineY + i * 10, GuiPaint.MUTED);
        }
    }
}
