package io.github.matheusanbs.wirelessautomate.client.tv.editor;

import io.github.matheusanbs.wirelessautomate.client.GuiPaint;
import io.github.matheusanbs.wirelessautomate.client.GuiText;
import io.github.matheusanbs.wirelessautomate.tv.TvPiece;
import io.github.matheusanbs.wirelessautomate.tv.TvPieceType;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * As Camadas (decisão 9 da spec da etapa 2, na aba Camadas da coluna da esquerda, com a altura inteira dela): a lista das peças de cima para baixo (a última desenhada no topo), com o
 * símbolo do tipo, o nome e o olho que esconde; cada sequência de peças de um grupo ganha o cabeçalho "Grupo N", que
 * recolhe pela seta e escolhe o grupo inteiro pelo clique. Arrastar uma linha (ou um cabeçalho, com a sequência
 * dele) a leva para outra camada. As linhas e os textos medidos são refeitos só quando o rascunho, o que está
 * recolhido ou o tamanho mudam; por quadro, só o desenho.
 */
final class TvLayersPanel {
    static final int ROW_H = TvIcons.SIZE;
    private static final int EYE = TvIcons.SIZE;
    private static final int DRAG_START = 3;
    /** Onde o nome começa: o ícone de 16 px tem 2 px de folga em cada lado, que o nome ocupa. */
    private static final int TEXT_X = TvIcons.SIZE - 1;
    /** Quanto o olho tira da largura do nome (a folga dele também é aproveitada). */
    private static final int EYE_SPACE = EYE - 4;

    /** Uma linha: a peça (ou -1 no cabeçalho), o grupo, o texto medido, onde cair acima dela e o que ela leva. */
    private record Row(int piece, int group, boolean header, GuiText.Fitted label, Component count, int countWidth,
            int before, TvSelection block, TvPieceType type) {
    }

    private final TvEditorDoc doc;
    private final List<Row> rows = new ArrayList<>();
    private final IntOpenHashSet collapsed = new IntOpenHashSet();
    private TvRect rect = TvRect.NONE;
    private Font font;
    private GuiText.Fitted empty;
    private int builtVersion = -1;
    private boolean stale = true;
    private int scroll;
    private int pressed = -1;
    private double pressY;
    private boolean dragging;
    private int dropGap = -1;

    TvLayersPanel(TvEditorDoc doc) {
        this.doc = doc;
    }

    void layout(Font font, TvRect rect) {
        this.font = font;
        this.rect = rect;
        empty = GuiText.fit(font, TvEditorText.key("layers.empty"), rect.w() - 8);
        stale = true;
    }

    private int visible() {
        return Math.max(1, (rect.h() - 2) / ROW_H);
    }

    /** Refaz as linhas, só com o rascunho, o recolhido ou o tamanho mudados. */
    private void rebuild() {
        if (!stale && builtVersion == doc.model().version()) {
            return;
        }
        stale = false;
        builtVersion = doc.model().version();
        rows.clear();
        List<TvPiece> pieces = doc.pieces();
        int labelW = rect.w() - TEXT_X - EYE_SPACE - 3;
        int lastGroup = -1;
        for (int i = pieces.size() - 1; i >= 0; i--) {
            TvPiece p = pieces.get(i);
            int g = p.group();
            if (g != 0 && g != lastGroup) {
                int low = i;
                while (low > 0 && pieces.get(low - 1).group() == g) {
                    low--;
                }
                Component count = Component.literal(Integer.toString(i - low + 1));
                int countW = font.width(count);
                rows.add(new Row(-1, g, true, GuiText.fit(font, TvEditorText.key("group", g), labelW - countW - 4),
                        count, countW, i + 1, TvSelection.range(low, i + 1), p.type()));
            }
            lastGroup = g;
            if (g != 0 && collapsed.contains(g)) {
                continue;
            }
            int indent = g != 0 ? 6 : 0;
            rows.add(new Row(i, g, false, GuiText.fit(font, TvEditorText.label(p), labelW - indent), Component.empty(),
                    0, i + 1, TvSelection.of(i), p.type()));
        }
        scroll = Math.max(0, Math.min(scroll, rows.size() - visible()));
    }

    void render(GuiGraphics g, int mouseX, int mouseY) {
        rebuild();
        int x = rect.x();
        int y = rect.y();
        int h = rect.h();
        GuiPaint.inset(g, x, y, rect.w(), h);
        if (rows.isEmpty()) {
            GuiText.draw(g, font, empty, x + 4, y + 4, GuiPaint.MUTED);
            return;
        }
        int end = Math.min(rows.size(), scroll + visible());
        for (int r = scroll; r < end; r++) {
            renderRow(g, rows.get(r), x, y + 1 + (r - scroll) * ROW_H, mouseX, mouseY);
        }
        if (dragging && dropGap >= 0) {
            int lineY = y + 1 + (dropGap - scroll) * ROW_H;
            g.fill(x + 1, lineY - 1, x + rect.w() - 1, lineY + 1, GuiPaint.ACCENT);
        }
        if (rows.size() > visible()) {
            int track = h - 2;
            int thumb = Math.max(6, track * visible() / rows.size());
            int thumbY = y + 1 + (track - thumb) * scroll / Math.max(1, rows.size() - visible());
            g.fill(x + rect.w() - 3, thumbY, x + rect.w() - 1, thumbY + thumb, GuiPaint.BUTTON_BORDER);
        }
    }

    private void renderRow(GuiGraphics g, Row row, int x, int y, int mouseX, int mouseY) {
        int w = rect.w() - 4;
        boolean chosen = chosen(row);
        if (chosen) {
            g.fill(x + 1, y, x + w, y + ROW_H, GuiPaint.ROW_SELECTED);
            g.fill(x + 1, y, x + 3, y + ROW_H, GuiPaint.ACCENT);
        } else if (!dragging && mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + ROW_H) {
            g.fill(x + 1, y, x + w, y + ROW_H, GuiPaint.ROW_HOVER);
        }
        int textColor = GuiPaint.FG;
        if (row.header()) {
            TvIcons.draw(g, collapsed.contains(row.group()) ? TvIcons.Icon.SECTION_CLOSED : TvIcons.Icon.SECTION_OPEN, x,
                    y);
            GuiText.draw(g, font, row.label(), x + TEXT_X, y + 4, textColor);
            GuiPaint.text(g, font, row.count(), x + w - row.countWidth() - 2, y + 4, GuiPaint.MUTED);
            return;
        }
        int indent = row.group() != 0 ? 6 : 0;
        boolean hidden = doc.pieces().get(row.piece()).hidden();
        TvIcons.draw(g, TvIcons.of(row.type()), x + indent, y);
        GuiText.draw(g, font, row.label(), x + TEXT_X + indent, y + 4, hidden ? GuiPaint.MUTED : textColor);
        TvIcons.draw(g, hidden ? TvIcons.Icon.EYE_CLOSED : TvIcons.Icon.EYE, eyeX(), y);
    }

    private int eyeX() {
        return rect.x() + rect.w() - EYE - 1;
    }

    /** A linha está escolhida: a peça, ou o cabeçalho com todas as peças da sequência escolhidas. */
    private boolean chosen(Row row) {
        TvSelection selection = doc.selection();
        if (!row.header()) {
            return selection.contains(row.piece());
        }
        for (int k = 0; k < row.block().size(); k++) {
            if (!selection.contains(row.block().get(k))) {
                return false;
            }
        }
        return true;
    }

    /** A linha sob o mouse, ou -1. */
    private int rowAt(double mouseX, double mouseY) {
        int y = rect.y() + 1;
        if (mouseX < rect.x() || mouseX >= rect.right() || mouseY < y) {
            return -1;
        }
        int r = scroll + (int) ((mouseY - y) / ROW_H);
        return r < Math.min(rows.size(), scroll + visible()) ? r : -1;
    }

    boolean mouseClicked(double mouseX, double mouseY) {
        if (!rect.contains(mouseX, mouseY)) {
            return false;
        }
        rebuild();
        int r = rowAt(mouseX, mouseY);
        if (r < 0) {
            return true;
        }
        Row row = rows.get(r);
        if (row.header()) {
            if (mouseX < rect.x() + TvIcons.SIZE - 2) {
                if (!collapsed.remove(row.group())) {
                    collapsed.add(row.group());
                }
                stale = true;
                return true;
            }
            doc.select(TvEditOps.members(TvPieceAccess.INSTANCE, doc.pieces(), row.group()), 0);
        } else if (mouseX >= eyeX() - 2) {
            doc.toggleHidden(row.piece());
            return true;
        } else {
            doc.select(TvSelection.of(row.piece()), row.group());
        }
        pressed = r;
        pressY = mouseY;
        dragging = false;
        return true;
    }

    boolean mouseDragged(double mouseX, double mouseY) {
        if (pressed < 0) {
            return false;
        }
        if (!dragging && Math.abs(mouseY - pressY) >= DRAG_START) {
            dragging = true;
        }
        if (dragging) {
            int y = rect.y() + 1;
            int gap = scroll + (int) Math.round((mouseY - y) / ROW_H);
            dropGap = Math.max(scroll, Math.min(gap, Math.min(rows.size(), scroll + visible())));
        }
        return true;
    }

    boolean mouseReleased() {
        if (pressed < 0) {
            return false;
        }
        if (dragging && dropGap >= 0 && pressed < rows.size()) {
            Row moved = rows.get(pressed);
            int before = dropGap >= rows.size() ? 0 : rows.get(dropGap).before();
            doc.moveTo(moved.block(), before);
        }
        pressed = -1;
        dragging = false;
        dropGap = -1;
        return true;
    }

    boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (!rect.contains(mouseX, mouseY)) {
            return false;
        }
        scroll = Math.max(0, Math.min(rows.size() - visible(), scroll - (int) Math.signum(delta)));
        return true;
    }

    /** O centro da linha da peça {@code index} à vista, ou {@code null} (gancho do e2e). */
    int[] rowCenter(int index) {
        rebuild();
        for (int r = scroll; r < Math.min(rows.size(), scroll + visible()); r++) {
            if (!rows.get(r).header() && rows.get(r).piece() == index) {
                return new int[] {rect.x() + rect.w() / 2, rect.y() + 1 + (r - scroll) * ROW_H + ROW_H / 2};
            }
        }
        return null;
    }
}
