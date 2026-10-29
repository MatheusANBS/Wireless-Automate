package io.github.matheusanbs.wirelessautomate.client.tv.editor;

import io.github.matheusanbs.wirelessautomate.client.GuiPaint;
import io.github.matheusanbs.wirelessautomate.tv.TvLayout;
import io.github.matheusanbs.wirelessautomate.tv.TvPiece;
import io.github.matheusanbs.wirelessautomate.tv.TvPieceType;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.client.gui.GuiGraphics;

/**
 * O contorno tracejado da miniatura nas Imagens que ainda não têm arte (a que nasceu pela paleta de Peças antes de
 * escolher a arte, ou a cuja arte ainda não chegou): a caixa vazia, desenhada na GUI por cima da imagem do rascunho,
 * que não pinta nada nela. A lista é refeita com as caixas da {@link TvEditorCanvas} (quando o rascunho muda); por
 * quadro, só os traços.
 */
final class TvBlankImages {
    private int[] indices = new int[0];

    void refresh(TvLayout layout) {
        List<TvPiece> pieces = layout.pieces();
        int[] found = new int[pieces.size()];
        int count = 0;
        for (int i = 0; i < pieces.size(); i++) {
            TvPiece piece = pieces.get(i);
            Optional<UUID> art = piece.style().part().image();
            if (piece.type() == TvPieceType.IMAGE && !piece.hidden()
                    && (art.isEmpty() || layout.image(art.get()) == null)) {
                found[count++] = i;
            }
        }
        indices = Arrays.copyOf(found, count);
    }

    void render(GuiGraphics g, TvCanvasView view, List<TvPiece> pieces) {
        int color = GuiPaint.alpha(GuiPaint.MUTED, 0xC0);
        for (int i : indices) {
            if (i < pieces.size()) {
                TvPiece p = pieces.get(i);
                int x0 = view.screenX(p.x());
                int y0 = view.screenY(p.y());
                TvEditorPaint.dashed(g, x0, y0, Math.max(2, view.screenX(p.x() + p.width()) - x0),
                        Math.max(2, view.screenY(p.y() + p.height()) - y0), color);
            }
        }
    }
}
