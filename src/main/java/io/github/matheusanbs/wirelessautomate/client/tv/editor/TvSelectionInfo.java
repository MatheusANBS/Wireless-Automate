package io.github.matheusanbs.wirelessautomate.client.tv.editor;

import io.github.matheusanbs.wirelessautomate.client.GuiPaint;
import io.github.matheusanbs.wirelessautomate.client.GuiText;
import io.github.matheusanbs.wirelessautomate.tv.TvPiece;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/**
 * O resumo da escolha no topo da coluna da direita: o tipo (ou "N peças") e, na linha de baixo, o tamanho e onde está.
 * Os textos são refeitos só quando o rascunho ou a escolha mudam.
 */
final class TvSelectionInfo {
    private static final int LINE = 11;

    private final TvEditorDoc doc;
    private TvRect rect = TvRect.NONE;
    private Font font;
    private final GuiText.Fitted[] lines = new GuiText.Fitted[2];
    private int count;
    private int builtVersion = -1;
    private TvSelection builtSelection;

    TvSelectionInfo(TvEditorDoc doc) {
        this.doc = doc;
    }

    void layout(Font font, TvRect rect) {
        this.font = font;
        this.rect = rect;
        builtVersion = -1;
    }

    private void rebuild() {
        TvSelection selection = doc.selection();
        if (builtVersion == doc.model().version() && selection == builtSelection) {
            return;
        }
        builtVersion = doc.model().version();
        builtSelection = selection;
        int w = rect.w() - 12;
        List<TvPiece> pieces = doc.pieces();
        if (selection.isEmpty() || selection.last() >= pieces.size()) {
            lines[0] = GuiText.fit(font, TvEditorText.key("nothing"), w);
            count = 1;
            return;
        }
        int[] b = TvEditOps.bounds(TvPieceAccess.INSTANCE, pieces, selection);
        if (selection.size() == 1) {
            TvPiece p = pieces.get(selection.first());
            lines[0] = GuiText.fit(font, TvEditorText.typeName(p.type()), w);
        } else {
            lines[0] = GuiText.fit(font, TvEditorText.key("many", selection.size()), w);
        }
        lines[1] = GuiText.fit(font, TvEditorText.key("geometry", b[2] - b[0], b[3] - b[1], b[0], b[1]), w);
        count = 2;
    }

    void render(GuiGraphics g) {
        rebuild();
        GuiPaint.inset(g, rect.x(), rect.y(), rect.w(), rect.h());
        for (int i = 0; i < count; i++) {
            GuiText.draw(g, font, lines[i], rect.x() + 6, rect.y() + 3 + i * LINE, i == 0 ? GuiPaint.FG : GuiPaint.MUTED);
        }
    }
}
