package io.github.matheusanbs.wirelessautomate.client.tablet;

import static io.github.matheusanbs.wirelessautomate.client.tablet.ListLayout.LIMIT_ROW;
import static io.github.matheusanbs.wirelessautomate.client.tablet.TabletText.tr;

import io.github.matheusanbs.wirelessautomate.client.GuiPaint;
import io.github.matheusanbs.wirelessautomate.client.GuiText;
import io.github.matheusanbs.wirelessautomate.client.router.FaceNet;
import io.github.matheusanbs.wirelessautomate.client.tablet.ListLayout.Hot;
import io.github.matheusanbs.wirelessautomate.client.tablet.ListLayout.Kind;
import io.github.matheusanbs.wirelessautomate.menu.TabletSnapshot;
import io.github.matheusanbs.wirelessautomate.menu.TabletSnapshot.NodeView;
import io.github.matheusanbs.wirelessautomate.menu.TabletSnapshot.ProfileSummary;
import io.github.matheusanbs.wirelessautomate.network.PortMode;
import io.github.matheusanbs.wirelessautomate.range.RemoteSide;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * As linhas da Lista do Tablet: a linha de cada roteador (ícone do tier com o estado, nome, pílula do tier, a presa
 * na segunda linha e os quadradinhos das faces), a linha do limite, a barra de rolagem e a lista vazia.
 */
final class ListRows {
    /** Abaixo desta largura da lista, os quadradinhos das faces somem. */
    private static final int CHIPS_MIN_LIST = 150;
    private static final int CHIP = 4;
    /** O ícone do item, no tamanho natural dele. */
    private static final int ICON_W = 16;

    private final TabletList list;
    private final ListLayout layout;

    ListRows(TabletList list, ListLayout layout) {
        this.list = list;
        this.layout = layout;
    }

    void render(GuiGraphics g, Font font, int mx, int my) {
        TabletSnapshot s = list.snapshot();
        List<NodeView> nodes = s.nodes();
        if (list.listEmpty()) {
            renderEmpty(g, font);
            return;
        }
        if (list.machineView()) {
            renderMachines(g, font, mx, my);
            return;
        }
        boolean chips = layout.listW >= CHIPS_MIN_LIST;
        ProfileSummary profile = s.profile().orElse(null);
        List<ListTree.Line> rows = list.model.tree().lines();
        for (Hot hot : layout.hots) {
            switch (hot.kind()) {
                case ROW -> renderNode(g, font, hot, nodes.get(hot.arg()), profile, chips, mx, my);
                case TREE -> list.treeRows.render(g, font, hot, rows.get(hot.arg()), mx, my);
                default -> {
                }
            }
        }
        // o resultado cortado pelo servidor: a última linha diz quantos casaram
        if (s.matchingNodes() > nodes.size() && layout.drawnUntil >= rows.size()
                && layout.rowsEndY + LIMIT_ROW <= layout.rowsBottom) {
            GuiText.draw(g, font, tr("list.limit", nodes.size(), s.matchingNodes()), layout.x0 + 4, layout.rowsEndY + 2,
                    layout.listW - 8, GuiPaint.MUTED);
        }
        renderScrollbar(g);
    }

    /** A visão por máquinas: as linhas, a do limite (o servidor cortou no teto) e a rolagem. */
    private void renderMachines(GuiGraphics g, Font font, int mx, int my) {
        list.machineRows.render(g, font, layout, mx, my);
        var page = list.snapshot().machineRows();
        if (list.machineRows.limited() && layout.drawnUntil >= page.rows().size()
                && layout.rowsEndY + LIMIT_ROW <= layout.rowsBottom) {
            GuiText.draw(g, font, tr("list.limit", page.rows().size(), page.totalRows()), layout.x0 + 4,
                    layout.rowsEndY + 2, layout.listW - 8, GuiPaint.MUTED);
        }
        renderScrollbar(g);
    }

    /** A barra de rolagem fina na borda direita das linhas, só quando elas não cabem. */
    private void renderScrollbar(GuiGraphics g) {
        int max = layout.maxScroll();
        if (max <= 0) {
            return;
        }
        int track = layout.rowsBottom - layout.rowsTop;
        int rows = layout.rowCount();
        int thumb = Math.max(8, track * (rows - max) / rows);
        int top = layout.rowsTop + (track - thumb) * list.scroll / max;
        int x = layout.x0 + layout.listW - 2;
        g.fill(x, layout.rowsTop, x + 2, layout.rowsBottom, GuiPaint.LINE);
        g.fill(x, top, x + 2, top + thumb, GuiPaint.MUTED);
    }

    private void renderEmpty(GuiGraphics g, Font font) {
        TabletSnapshot s = list.snapshot();
        int listW = layout.listW;
        int x0 = layout.x0;
        int cy = layout.rowsTop + Math.max(10, (layout.rowsBottom - layout.rowsTop) / 2 - 12);
        if (s.totalNodes() == 0) {
            GuiText.wrapCentered(g, font, tr("list.empty"), x0 + listW / 2, cy, listW - 8, 2, GuiPaint.MUTED);
            return;
        }
        String text = list.search.text;
        Component why = text.isBlank() ? tr("list.no_match") : tr("list.no_match.text", text.strip());
        GuiText.wrapCentered(g, font, why, x0 + listW / 2, cy, listW - 8, 2, GuiPaint.MUTED);
        int[] clear = layout.emptyClearCenter();
        if (clear != null) {
            Component label = tr("list.clear_search");
            int w = font.width(label);
            GuiPaint.text(g, font, label, clear[0] - w / 2, clear[1] - 4, GuiPaint.ACCENT);
            g.fill(clear[0] - w / 2, clear[1] + 4, clear[0] - w / 2 + w - 1, clear[1] + 5, GuiPaint.ACCENT);
        }
    }

    /**
     * A linha do nó. Escolhido e aberto na árvore, o alvo do painel é uma das linhas de baixo, que leva a marca: o
     * nó fica só com o fundo.
     */
    private void renderNode(GuiGraphics g, Font font, Hot hot, NodeView node, @Nullable ProfileSummary profile,
            boolean chips, int mx, int my) {
        boolean isChosen = node.key().equals(list.chosen);
        boolean picked = list.selected.contains(node.key());
        int x = hot.x();
        int y = hot.y();
        int line = list.model.tree().lineOfNode(hot.arg());
        if (isChosen || picked) {
            g.fill(x, y, x + hot.w(), y + hot.h() - 1, GuiPaint.ROW_SELECTED);
            if (picked || !list.model.tree().open(hot.arg())) {
                g.fill(x, y, x + 2, y + hot.h() - 1, GuiPaint.ACCENT);
            }
        } else if (hot.contains(mx, my)) {
            g.fill(x, y, x + hot.w(), y + hot.h() - 1, GuiPaint.ROW_HOVER);
        }
        g.fill(x + 2, y + hot.h() - 1, x + hot.w() - 2, y + hot.h(), GuiPaint.LINE);
        int cx = x + 5;
        if (list.multi) {
            MarkRows.paint(g, cx, y + 5, list.markRows.node(node.key()));
            cx += 12;
        }
        int chevron = list.treeRows.chevronWidth();
        if (chevron > 0) {
            Hot toggle = layout.hot(Kind.TREE_TOGGLE, line);
            list.treeRows.renderNodeChevron(g, cx, y, node, line, toggle != null && toggle.contains(mx, my));
            cx += chevron;
        }
        g.renderItem(RouterIcons.of(node.tier()), cx, y + 2);
        // o estado vai na pontinha do ícone, para a linha não perder largura
        GuiPaint.dot(g, cx + 11, y + 12, TabletText.statusColor(node.status()));
        int textX = cx + ICON_W + 3;
        int right = x + hot.w() - 3;
        if (chips) {
            right -= 3 * CHIP + 2 + 4;
            renderChips(g, right + 4, y + 5, node);
        }
        ListTexts.TierPill pill = list.texts.pill(font, node.tier());
        ListTexts.NodeLine texts = list.texts.nodeLine(font, hot.arg(), node, profile, right - textX, pill.width());
        if (texts.pill()) {
            GuiPaint.outline(g, right - pill.width(), y + 1, pill.width(), 10, pill.color());
            GuiPaint.text(g, font, pill.name(), right - pill.width() + ListTexts.PILL_PAD + 1, y + 2, pill.color());
        }
        GuiText.draw(g, font, texts.name(), textX, y + 2, GuiPaint.FG);
        GuiText.draw(g, font, texts.state(), textX, y + 11, GuiPaint.MUTED);
    }

    /** Os seis quadradinhos das faces na aba do painel: Cima, Frente, Esquerda / Baixo, Trás, Direita. */
    private void renderChips(GuiGraphics g, int x, int y, NodeView node) {
        for (int i = 0; i < FaceNet.CHIP_ORDER.size(); i++) {
            RemoteSide side = FaceNet.CHIP_ORDER.get(i);
            int cx = x + (i % 3) * (CHIP + 1);
            int cy = y + (i / 3) * (CHIP + 1);
            PortMode mode = node.faceMode(list.chipType, side);
            if (mode == PortMode.NONE) {
                GuiPaint.outline(g, cx, cy, CHIP, CHIP, GuiPaint.LINE);
            } else {
                g.fill(cx, cy, cx + CHIP, cy + CHIP, GuiPaint.modeColor(mode));
            }
        }
    }
}
