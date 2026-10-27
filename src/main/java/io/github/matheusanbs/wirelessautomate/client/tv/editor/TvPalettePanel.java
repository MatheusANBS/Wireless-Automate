package io.github.matheusanbs.wirelessautomate.client.tv.editor;

import io.github.matheusanbs.wirelessautomate.client.GuiPaint;
import io.github.matheusanbs.wirelessautomate.client.GuiText;
import io.github.matheusanbs.wirelessautomate.tv.TvPieceType;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * A aba Peças da coluna da esquerda: Valor, Texto e Forma; o clique insere uma nova no primeiro lugar livre e a
 * escolhe. Os textos são medidos no {@link #layout}.
 */
final class TvPalettePanel {
    private static final List<TvPieceType> TYPES = List.of(TvPieceType.VALUE, TvPieceType.TEXT, TvPieceType.SHAPE);

    private final TvEditorDoc doc;
    private TvEditorLayout layout;
    private Font font;
    private final GuiText.Fitted[] names = new GuiText.Fitted[TYPES.size()];
    private final Component[] glyphs = new Component[TYPES.size()];
    private String defaultText = "";

    TvPalettePanel(TvEditorDoc doc) {
        this.doc = doc;
    }

    void layout(Font font, TvEditorLayout layout) {
        this.font = font;
        this.layout = layout;
        for (int i = 0; i < names.length; i++) {
            names[i] = GuiText.fit(font, TvEditorText.typeName(TYPES.get(i)), layout.pieceButtons()[i].w() - 20);
            glyphs[i] = Component.literal(TvEditorText.glyph(TYPES.get(i)));
        }
        defaultText = TvEditorText.key("default_text").getString();
    }

    void render(GuiGraphics g, int mouseX, int mouseY) {
        for (int i = 0; i < TYPES.size(); i++) {
            TvRect r = layout.pieceButtons()[i];
            GuiPaint.box(g, r.x(), r.y(), r.w(), r.h(), GuiPaint.BUTTON,
                    r.contains(mouseX, mouseY) ? GuiPaint.BUTTON_HOVER_BORDER : GuiPaint.BUTTON_BORDER);
            GuiPaint.text(g, font, glyphs[i], r.x() + 5, r.y() + 3, GuiPaint.ACCENT);
            GuiText.draw(g, font, names[i], r.x() + 16, r.y() + 3, GuiPaint.FG);
        }
    }

    boolean mouseClicked(double mouseX, double mouseY) {
        for (int i = 0; i < TYPES.size(); i++) {
            if (layout.pieceButtons()[i].contains(mouseX, mouseY)) {
                doc.insert(TYPES.get(i), defaultText);
                return true;
            }
        }
        return false;
    }

    /** O centro do botão de um tipo de peça (gancho do e2e). */
    int[] center(TvPieceType type) {
        return layout.pieceButtons()[TYPES.indexOf(type)].center();
    }
}
