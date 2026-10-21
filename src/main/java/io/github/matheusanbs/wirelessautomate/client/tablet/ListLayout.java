package io.github.matheusanbs.wirelessautomate.client.tablet;

import static io.github.matheusanbs.wirelessautomate.client.tablet.TabletText.tr;

import io.github.matheusanbs.wirelessautomate.client.FrameCache;
import io.github.matheusanbs.wirelessautomate.client.FrameClock;
import io.github.matheusanbs.wirelessautomate.client.NoticeBand;
import io.github.matheusanbs.wirelessautomate.client.highlight.HighlightRenderer;
import io.github.matheusanbs.wirelessautomate.client.router.NodePanel;
import io.github.matheusanbs.wirelessautomate.menu.TabletSnapshot;
import io.github.matheusanbs.wirelessautomate.packet.PanelNoticePayload;
import io.github.matheusanbs.wirelessautomate.packet.PanelNotices;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * A geometria da Lista do Tablet, refeita a cada quadro: a coluna da lista e a do painel, a busca com as
 * etiquetas, os filtros rápidos, a faixa do perfil, as linhas visíveis a partir da rolagem e os botões do painel,
 * cada um como uma área que responde ao mouse ({@link Hot}).
 */
final class ListLayout {
    static final int SEARCH_H = 14;
    static final int QUICK_H = 11;
    static final int BANNER_H = 12;
    static final int HEADER_ROW = 12;
    static final int NODE_ROW = 20;
    static final int LIMIT_ROW = 12;
    static final int BAR_H = 14;
    static final int GAP = 3;
    /** Largura mínima da coluna da lista e máxima do painel. */
    private static final int LIST_MIN = 128;
    private static final int PANEL_MAX = 260;
    private static final int LABEL_H = 10;
    private static final int SUGGEST_MAX_W = 230;
    /** Altura do cubo do painel: menor nas telas baixas. */

    /**
     * As áreas. Nas da árvore ({@link TreeRows}), o argumento é a linha da {@link ListTree}: a linha inteira
     * ({@code TREE}), o ▸/▾ de um nó ou de um grupo, o Olho de uma máquina e o "Mostrar menos" de um grupo. Nas da
     * visão por máquinas ({@link MachineRows}), a linha da página: a linha inteira ({@code MACHINE}), o Olho e o
     * "Mostrar menos" do bloco.
     */
    enum Kind { LABEL, VIEW, QUICK, TYPE_CHIP, CLEAR, FORGET, ROW, FULL_SCREEN, COPY,
        PASTE, UNDO, TREE, TREE_TOGGLE, TREE_EYE, TREE_FEW, MACHINE, MACHINE_EYE, MACHINE_FEW, MACHINE_OPEN;

        /** Fica por cima da linha onde está. */
        boolean onTop() {
            return this == TREE_TOGGLE || this == TREE_EYE || this == TREE_FEW
                    || this == MACHINE_EYE || this == MACHINE_FEW || this == MACHINE_OPEN;
        }
    }

    /** Uma área que responde ao mouse. */
    record Hot(Kind kind, int x, int y, int w, int h, int arg) {
        boolean contains(double mx, double my) {
            return mx >= x && mx < x + w && my >= y && my < y + h;
        }

        int[] center() {
            return new int[] {x + w / 2, y + h / 2};
        }
    }

    private final TabletList list;
    private final ViewBar viewBar;

    // geometria (absoluta), refeita a cada quadro
    int x0;
    int y0;
    int bottom;
    int listW;
    int panelX;
    int panelW;
    /** Altura do aviso sem painel no quadro atual: cresce com as linhas do texto ({@link NoticeBand}). */
    private int ownNoticeH = NoticeBand.LINE_H;
    int rowsTop;
    int rowsBottom;
    /** A primeira linha que não coube (as de antes, a partir da rolagem, estão nos {@link #hots}). */
    int drawnUntil;
    int rowsEndY;
    final List<Hot> hots = new ArrayList<>();
    /** O texto de cada filtro rápido do quadro, na ordem das áreas (inteiro, curto ou só o número). */
    final List<Component> quickLabels = new ArrayList<>();
    /** Os botões do perfil com o nome curto ("Copiar", "Colar") quando os dois inteiros não cabem. */
    private boolean copyShort;
    /** O layout do quadro: feito uma vez e reusado no desenho, por cima e na dica. */
    private final FrameCache arranged = new FrameCache();

    ListLayout(TabletList list) {
        this.list = list;
        this.viewBar = new ViewBar(list, this);
    }

    ViewBar viewBar() {
        return viewBar;
    }

    /** O retângulo da aba (de cima da busca até o fim da tela). */
    void layout(int x, int y, int w, int bottomY) {
        arranged.invalidate();
        x0 = x;
        y0 = y;
        bottom = bottomY;
        // o painel fica com pouco mais da metade (até PANEL_MAX); a lista, com o resto, nunca menos que LIST_MIN
        panelW = Math.min(PANEL_MAX, w * 55 / 100);
        listW = Math.max(LIST_MIN, w - panelW - 9);
        panelW = w - listW - 9;
        panelX = x0 + listW + 9;
    }

    int barY() {
        return bottom - BAR_H;
    }

    /** Onde as linhas de ação do modo Selecionar começam (duas linhas acima do fim). */
    int multiBarY() {
        return bottom - 2 * BAR_H - 2;
    }

    private int panelBodyTop() {
        return y0 + 24;
    }

    int copyRowY() {
        return bottom - BAR_H;
    }

    int ownNoticeY() {
        return bottom - ownNoticeH;
    }

    /**
     * Posições e áreas de clique refeitas agora: para um evento ou um gancho, que podem mudar o estado em
     * seguida (o próximo {@link #arrangeForFrame} refaz).
     */
    void arrange() {
        compute();
        arranged.invalidate();
    }

    /**
     * O layout do quadro, para o desenho, o que vem por cima e a dica: feito na primeira chamada do quadro e
     * reusado nas outras, enquanto o aviso é o mesmo. O que muda a lista (busca, snapshot, clique, rolagem)
     * chega por tick ou por evento, entre um quadro e outro.
     */
    void arrangeForFrame() {
        if (!arranged.reuse(FrameClock.frame(), PanelNotices.current())) {
            compute();
        }
    }

    private void compute() {
        Font font = TabletList.font();
        hots.clear();
        PanelNoticePayload notice = PanelNotices.current();
        ownNoticeH = notice == null ? NoticeBand.LINE_H
                : NoticeBand.height(font, notice, panelW, notice.undo() != PanelNoticePayload.Undo.NONE);
        int y = y0;
        arrangeSearch(font);
        y += SEARCH_H + GAP;
        int quickBottom = viewBar.arrange(font, y);
        if (quickBottom > y) {
            y = quickBottom + GAP;
        }
        if (list.profile().isPresent()) {
            int fw = font.width(forgetLabel(font)) + 6;
            hots.add(new Hot(Kind.FORGET, x0 + listW - fw - 2, y, fw, BANNER_H, 0));
            y += BANNER_H + GAP;
        }
        rowsTop = y;
        rowsBottom = (list.multi ? multiBarY() : barY()) - GAP;
        arrangeRows(font);
        if (list.search.filtering()) {
            Component clear = tr("list.clear");
            int cw = font.width(clear);
            Component count = list.texts.countText();
            // o Selecionar e, com alguma máquina acesa, o Apagar todas ficam à direita
            int buttonsW = 62 + (HighlightRenderer.count() > 0 && !list.multi ? SelectBar.clearLitWidth(font) + 2 : 0);
            int room = listW - buttonsW - cw - 8;
            int countW = Math.min(font.width(count), Math.max(0, room));
            // sem lugar nem para o Limpar ao lado dos botões, ele sai (o Esc na Lista também limpa a busca)
            if (!list.multi && room >= 0) {
                hots.add(new Hot(Kind.CLEAR, x0 + countW + 4, barY(), cw + 2, BAR_H, 0));
            }
        }
        arrangePanel(font);
        int reveal = revealLine();
        if (reveal >= 0 && scrollTo(reveal)) {
            compute();
        }
    }

    private void arrangeSearch(Font font) {
        List<ListSearch.Label> labels = list.search.labels;
        int x = x0 + 3;
        int room = listW - 6;
        int maxChip = labels.isEmpty() ? 0 : Math.max(24, (room - 40) / labels.size() - 2);
        for (int i = 0; i < labels.size(); i++) {
            int w = Math.min(font.width(list.texts.labelText(labels.get(i))) + 14, maxChip);
            hots.add(new Hot(Kind.LABEL, x, y0 + 2, w, LABEL_H, i));
            x += w + 2;
        }
        EditBox searchBox = list.search.box();
        if (searchBox != null) {
            int boxX = x + 1;
            int boxW = Math.max(20, x0 + listW - 4 - boxX);
            if (searchBox.getX() != boxX || searchBox.getWidth() != boxW) {
                searchBox.setX(boxX);
                searchBox.setWidth(boxW);
            }
            searchBox.setY(y0 + 3);
        }
        list.search.suggestions.place(x0, y0 + SEARCH_H + 1, Math.max(listW, Math.min(SUGGEST_MAX_W, panelX + panelW - x0)));
    }

    /** Altura de uma linha da lista. */
    private int rowHeight(ListTree.Line line) {
        return TreeRows.height(line, line.kind().tree() ? list.model.row(line) : null);
    }

    /** Linhas desenháveis: as da árvore (ou as das máquinas) e, se o servidor cortou o resultado, a do limite. */
    int rowCount() {
        if (list.machineView()) {
            return list.machineRows.rowCount();
        }
        TabletSnapshot s = list.snapshot();
        return list.model.tree().lines().size() + (s.matchingNodes() > s.nodes().size() ? 1 : 0);
    }

    private int height(int row) {
        if (list.machineView()) {
            return list.machineRows.height(row);
        }
        List<ListTree.Line> lines = list.model.tree().lines();
        return row < lines.size() ? rowHeight(lines.get(row)) : LIMIT_ROW;
    }

    int maxScroll() {
        int room = rowsBottom - rowsTop;
        int used = 0;
        int row = rowCount();
        while (row > 0 && used + height(row - 1) <= room) {
            used += height(row - 1);
            row--;
        }
        return row;
    }

    private void arrangeRows(Font font) {
        list.scroll = Math.max(0, Math.min(list.scroll, maxScroll()));
        if (list.machineView()) {
            list.machineRows.arrange(font, this);
            return;
        }
        List<ListTree.Line> rows = list.model.tree().lines();
        int y = rowsTop;
        drawnUntil = list.scroll;
        for (int r = list.scroll; r < rows.size(); r++) {
            ListTree.Line row = rows.get(r);
            int h = rowHeight(row);
            if (y + h > rowsBottom) {
                break;
            }
            drawnUntil = r + 1;
            switch (row.kind()) {
                case NODE -> {
                    hots.add(new Hot(Kind.ROW, x0, y, listW, h, row.node()));
                    list.treeRows.arrangeNode(hots, list.snapshot().nodes().get(row.node()), r, x0, y, h);
                }
                default -> list.treeRows.arrange(font, hots, row, r, x0, y, listW, h);
            }
            y += h;
        }
        rowsEndY = y;
    }

    private void arrangePanel(Font font) {
        if (panelW <= 0) {
            return;
        }
        if (list.chosen != null) {
            Component full = fullScreenLabel(font);
            int fw = font.width(full) + 8;
            hots.add(new Hot(Kind.FULL_SCREEN, panelX + panelW - fw, y0, fw, 12, 0));
        }
        NodePanel p = list.panel();
        if (p != null) {
            int top = panelBodyTop();
            p.layout(panelX, top, panelW, copyRowY() - 3 - top);
            boolean paste = list.profile().isPresent();
            copyShort = paste && font.width(tr("list.copy")) + font.width(tr("list.paste")) + 24 > panelW;
            int cw = font.width(copyLabel()) + 10;
            hots.add(new Hot(Kind.COPY, panelX, copyRowY(), cw, BAR_H, 0));
            if (paste) {
                int pw = font.width(pasteLabel()) + 10;
                hots.add(new Hot(Kind.PASTE, panelX + panelW - pw, copyRowY(), pw, BAR_H, 0));
            }
            return;
        }
        // sem painel, só o Desfazer do colar: os outros agem no nó do painel
        PanelNoticePayload notice = PanelNotices.current();
        if (notice != null && notice.undo() == PanelNoticePayload.Undo.UNDO_PASTE) {
            int uw = NoticeBand.undoWidth(font);
            hots.add(new Hot(Kind.UNDO, panelX + panelW - uw, ownNoticeY(), uw, NoticeBand.LINE_H, 0));
        }
    }

    // ------------------------------------------------------------------ rótulos que dependem da largura

    Component copyLabel() {
        return copyShort ? tr("list.copy.short") : tr("list.copy");
    }

    Component pasteLabel() {
        return copyShort ? tr("list.paste.short") : tr("list.paste");
    }

    Component fullScreenLabel(Font font) {
        Component full = tr("list.full_screen");
        return font.width(full) + 8 <= panelW / 2 ? full : tr("open");
    }

    /** "esquecer ✕", ou só o ✕ quando ele tiraria o lugar do nome do perfil. */
    Component forgetLabel(Font font) {
        Component full = tr("list.profile.forget");
        return font.width(full) + 6 <= listW / 3 ? full : Component.literal("✕");
    }

    // ------------------------------------------------------------------ áreas

    @Nullable Hot hot(Kind kind, int arg) {
        for (Hot hot : hots) {
            if (hot.kind() == kind && hot.arg() == arg) {
                return hot;
            }
        }
        return null;
    }

    @Nullable Hot hotAt(double mx, double my) {
        if (list.search.suggestions.contains(mx, my)) {
            return null;
        }
        Hot found = null;
        for (Hot hot : hots) {
            if (hot.contains(mx, my)) {
                // os botões de dentro de uma linha ficam por cima dela
                if (found == null || hot.kind().onTop()) {
                    found = hot;
                }
            }
        }
        return found;
    }

    /**
     * A linha a pôr na vista agora: a de {@link TabletList#revealKey}, se ela já está na lista, ou a que as setas
     * marcaram na árvore ({@link TreeNav#takeReveal}); -1 sem nenhuma.
     */
    private int revealLine() {
        if (list.machineView()) {
            return list.machineNav.takeReveal();
        }
        if (list.revealKey != null) {
            int row = list.model.tree().lineOfNode(list.model.indexOf(list.revealKey));
            if (row >= 0) {
                list.revealKey = null;
            }
            return row;
        }
        return list.treeNav.takeReveal();
    }

    /** Rola até a linha {@code row}; devolve se rolou. */
    private boolean scrollTo(int row) {
        int before = list.scroll;
        if (row < list.scroll) {
            list.scroll = row;
            return true;
        }
        int room = rowsBottom - rowsTop;
        while (list.scroll < row) {
            int used = 0;
            for (int r = list.scroll; r <= row; r++) {
                used += height(r);
            }
            if (used <= room) {
                break;
            }
            list.scroll++;
        }
        return list.scroll != before;
    }

    /** O "Limpar a busca" da lista vazia (com busca ou filtro), ou {@code null}. */
    int @Nullable [] emptyClearCenter() {
        TabletSnapshot s = list.snapshot();
        if (!list.listEmpty() || s.totalNodes() == 0 || !list.search.filtering()) {
            return null;
        }
        int cy = rowsTop + Math.max(10, (rowsBottom - rowsTop) / 2 - 12) + 26;
        return new int[] {x0 + listW / 2, cy};
    }
}
