package io.github.matheusanbs.wirelessautomate.client.tablet;

import io.github.matheusanbs.wirelessautomate.client.GuiPaint;
import io.github.matheusanbs.wirelessautomate.client.GuiText;
import io.github.matheusanbs.wirelessautomate.client.highlight.HighlightRenderer;
import io.github.matheusanbs.wirelessautomate.client.tablet.ListLayout.Hot;
import io.github.matheusanbs.wirelessautomate.client.tablet.ListLayout.Kind;
import io.github.matheusanbs.wirelessautomate.menu.TabletMachineRows;
import io.github.matheusanbs.wirelessautomate.menu.TabletRows.MachineLive;
import io.github.matheusanbs.wirelessautomate.menu.TabletRows.MachineStatus;
import io.github.matheusanbs.wirelessautomate.network.NodeIndex.NodeKey;
import io.github.matheusanbs.wirelessautomate.range.EditTarget;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import org.jetbrains.annotations.Nullable;

/**
 * A geometria e o desenho das linhas da visão por máquinas na Lista do Tablet (especificação, "A visão por
 * máquinas"): o cabeçalho do bloco (▸/▾, o ícone, "Fornalha · 23" e, à direita e apagado, "3 roteadores", ou o
 * "Mostrar menos" do bloco com todas à vista), a máquina como na árvore (a bolinha do estado, o nome, "via Forja" ou
 * "presa" com as coordenadas ou o estado, e à direita a vazão quando move, a marca "exceção" e o Olho ◉) e o "… mais
 * N · Mostrar todas". Os textos vêm prontos do {@link MachineRowTexts}; aqui só se compara largura e desenha.
 */
final class MachineRows {
    private static final int LINE1_Y = 2;
    private static final int LINE2_Y = 11;
    private static final int FIRST_H = 11;
    private static final int EYE_W = 10;
    /** O nome de uma máquina guarda ao menos isto antes da marca e da vazão (que vão na dica quando não cabem). */
    private static final int NAME_MIN = 28;
    /** A coluna do ▸/▾ e o recuo das máquinas. */
    private static final int CHEV_W = 8;

    private final TabletList list;
    final MachineRowTexts texts;

    MachineRows(TabletList list) {
        this.list = list;
        this.texts = new MachineRowTexts(list.texts);
    }

    private TabletMachineRows page() {
        return list.snapshot().machineRows();
    }

    /** Os textos em dia com a página; devolve as linhas. */
    List<TabletMachineRows.Row> rows() {
        TabletMachineRows page = page();
        texts.prepare(TabletList.font(), page);
        return page.rows();
    }

    /** O servidor cortou as linhas no teto: a última linha diz quantas a visão teria. */
    boolean limited() {
        TabletMachineRows page = page();
        return page.totalRows() > page.rows().size();
    }

    int rowCount() {
        return page().rows().size() + (limited() ? 1 : 0);
    }

    int height(int row) {
        List<TabletMachineRows.Row> rows = page().rows();
        return row < rows.size() && rows.get(row) instanceof TabletMachineRows.Machine ? ListLayout.NODE_ROW
                : ListLayout.HEADER_ROW;
    }

    /** Onde o conteúdo de uma linha começa: depois da caixa do Selecionar e da coluna do ▸/▾. */
    private int contentX(int rowX) {
        return rowX + 5 + (list.multi ? 12 : 0) + CHEV_W;
    }

    // ------------------------------------------------------------------ geometria

    /** As linhas a partir da rolagem, nas áreas do {@code layout}, até o fim da vista. */
    void arrange(Font font, ListLayout layout) {
        List<TabletMachineRows.Row> rows = rows();
        int x = layout.x0;
        int w = layout.listW;
        int y = layout.rowsTop;
        layout.drawnUntil = list.scroll;
        for (int r = list.scroll; r < rows.size(); r++) {
            int h = height(r);
            if (y + h > layout.rowsBottom) {
                break;
            }
            layout.drawnUntil = r + 1;
            layout.hots.add(new Hot(Kind.MACHINE, x, y, w, h, r));
            switch (rows.get(r)) {
                case TabletMachineRows.Machine machine -> layout.hots.add(new Hot(Kind.MACHINE_EYE,
                        x + w - 3 - EYE_W, y, EYE_W, FIRST_H, r));
                case TabletMachineRows.Block block -> {
                    if (list.multi) {
                        layout.hots.add(new Hot(Kind.MACHINE_OPEN, x + 14, y, CHEV_W + 2, h, r));
                    }
                    if (block.showAll()) {
                        int lw = Math.min(texts.lessW + 2, w / 2);
                        layout.hots.add(new Hot(Kind.MACHINE_FEW, x + w - 3 - lw, y, lw, h, r));
                    }
                }
                default -> {
                }
            }
            y += h;
        }
        layout.rowsEndY = y;
    }

    // ------------------------------------------------------------------ desenho

    void render(GuiGraphics g, Font font, ListLayout layout, int mx, int my) {
        List<TabletMachineRows.Row> rows = rows();
        for (Hot hot : layout.hots) {
            if (hot.kind() != Kind.MACHINE) {
                continue;
            }
            switch (rows.get(hot.arg())) {
                case TabletMachineRows.Block block -> renderBlock(g, font, layout, hot, block, mx, my);
                case TabletMachineRows.Machine machine -> renderMachine(g, font, hot, machine, mx, my);
                case TabletMachineRows.More more -> renderMore(g, font, hot, mx, my);
            }
        }
    }

    private void renderBlock(GuiGraphics g, Font font, ListLayout layout, Hot hot, TabletMachineRows.Block block,
            int mx, int my) {
        int r = hot.arg();
        boolean hovered = hot.contains(mx, my) || list.machineNav.isCursor(r);
        ListMarks.Box box = list.multi ? list.markRows.machineBox(r) : ListMarks.Box.NONE;
        g.fill(hot.x(), hot.y(), hot.x() + hot.w(), hot.y() + hot.h(), box == ListMarks.Box.ALL ? GuiPaint.ROW_SELECTED
                : hovered ? GuiPaint.ROW_HOVER : GuiPaint.INSET);
        g.fill(hot.x(), hot.y() + hot.h() - 1, hot.x() + hot.w(), hot.y() + hot.h(), GuiPaint.LINE);
        int color = hovered ? GuiPaint.FG : GuiPaint.MUTED;
        if (list.multi) {
            MarkRows.paint(g, hot.x() + 3, hot.y() + 2, box);
        }
        int x = hot.x() + (list.multi ? 12 : 0);
        if (block.collapsed()) {
            GuiPaint.arrowRight(g, x + 4, hot.y() + 3, color);
        } else {
            GuiPaint.arrowDown(g, x + 3, hot.y() + 4, color);
        }
        int iconX = x + 11;
        if (!texts.icons[r].isEmpty()) {
            g.pose().pushPose();
            g.pose().translate(iconX, hot.y() + 1, 0);
            g.pose().scale(0.625f, 0.625f, 1f);
            g.renderItem(texts.icons[r], 0, 0);
            g.pose().popPose();
        }
        int right = hot.x() + hot.w() - 3;
        Hot few = layout.hot(Kind.MACHINE_FEW, r);
        if (few != null) {
            GuiText.draw(g, font, texts.less, few.x() + 1, hot.y() + 2, few.w() - 1, GuiPaint.ACCENT);
            if (few.contains(mx, my)) {
                g.fill(few.x() + 1, hot.y() + 10, few.x() + few.w() - 1, hot.y() + 11, GuiPaint.ACCENT);
            }
            right = few.x() - 3;
        } else if (right - texts.secondW[r] - 4 - (iconX + 12) >= texts.nameW[r]) {
            // "N roteadores" só quando o nome cabe inteiro ao lado (o número vai também na dica)
            GuiPaint.textRight(g, font, texts.seconds[r], right, hot.y() + 2, GuiPaint.MUTED);
            right -= texts.secondW[r] + 4;
        }
        GuiText.draw(g, font, texts.names[r], iconX + 12, hot.y() + 2, right - iconX - 12, GuiPaint.FG);
    }

    private void renderMachine(GuiGraphics g, Font font, Hot hot, TabletMachineRows.Machine machine, int mx, int my) {
        int r = hot.arg();
        int x = hot.x();
        int y = hot.y();
        int w = hot.w();
        int h = hot.h();
        ListMarks.Box box = list.multi ? list.markRows.machineBox(r) : ListMarks.Box.NONE;
        if (isTarget(machine) || box == ListMarks.Box.ALL) {
            g.fill(x, y, x + w, y + h - 1, GuiPaint.ROW_SELECTED);
            g.fill(x, y, x + 2, y + h - 1, GuiPaint.ACCENT);
        } else if (hot.contains(mx, my) || list.machineNav.isCursor(r)) {
            g.fill(x, y, x + w, y + h - 1, GuiPaint.ROW_HOVER);
        }
        if (list.multi) {
            MarkRows.paint(g, x + 5, y + 5, box);
        }
        int tx = contentX(x);
        g.fill(tx, y + h - 1, x + w - 2, y + h, GuiPaint.LINE);
        MachineLive live = texts.lives[r];
        MachineStatus status = live == null ? MachineStatus.UNLOADED : live.status();
        GuiPaint.dot(g, tx, y + 7, TreeTexts.dotColor(status));
        NodeKey router = page().routers().get(machine.router()).key();
        boolean lit = HighlightRenderer.isLit(router.dimension(), machine.pos());
        int right = x + w - 3;
        int eyeX = right - EYE_W;
        GuiPaint.eye(g, eyeX + 2, y + LINE1_Y + 1, lit ? GuiPaint.HIGHLIGHT : GuiPaint.MUTED);
        int textX = tx + 8;
        int end = eyeX - 2;
        if (texts.rateW[r] > 0 && end - texts.rateW[r] - 4 - textX >= NAME_MIN) {
            end -= texts.rateW[r];
            GuiPaint.text(g, font, texts.rates[r], end, y + LINE1_Y, GuiPaint.MUTED);
            end -= 4;
        }
        if (machine.exception() && end - texts.badgeW - 4 - textX >= NAME_MIN) {
            end -= texts.badgeW;
            GuiPaint.text(g, font, texts.badge, end, y + LINE1_Y, GuiPaint.ACCENT);
            end -= 4;
        }
        boolean counts = status != MachineStatus.OUT_OF_RANGE && status != MachineStatus.OVER_LIMIT;
        GuiText.draw(g, font, texts.names[r], textX, y + LINE1_Y, end - textX, counts ? GuiPaint.FG : GuiPaint.MUTED);
        GuiText.draw(g, font, texts.seconds[r], textX, y + LINE2_Y, right - textX, GuiPaint.MUTED);
    }

    private void renderMore(GuiGraphics g, Font font, Hot hot, int mx, int my) {
        int r = hot.arg();
        int tx = contentX(hot.x());
        int right = hot.x() + hot.w() - 3;
        if (list.machineNav.isCursor(r)) {
            g.fill(hot.x(), hot.y(), hot.x() + hot.w(), hot.y() + hot.h() - 1, GuiPaint.ROW_HOVER);
        }
        GuiText.draw(g, font, texts.names[r], tx, hot.y() + LINE1_Y, right - tx, GuiPaint.ACCENT);
        if (hot.contains(mx, my)) {
            int uw = Math.min(texts.nameW[r], right - tx);
            g.fill(tx, hot.y() + LINE1_Y + 9, tx + uw, hot.y() + LINE1_Y + 10, GuiPaint.ACCENT);
        }
    }

    /** A máquina é o alvo do painel do roteador escolhido. */
    boolean isTarget(TabletMachineRows.Machine machine) {
        NodeKey router = page().routers().get(machine.router()).key();
        if (!router.equals(list.chosen)) {
            return false;
        }
        EditTarget target = list.treeRows.panelTarget();
        if (target == null) {
            return false;
        }
        return machine.attached() ? target.attached()
                : target.kind() == EditTarget.Kind.MACHINE && target.id() == machine.index();
    }

    /** A linha da máquina que é o alvo do painel, ou -1. */
    int targetLine() {
        List<TabletMachineRows.Row> rows = page().rows();
        for (int r = 0; r < rows.size(); r++) {
            if (rows.get(r) instanceof TabletMachineRows.Machine machine && isTarget(machine)) {
                return r;
            }
        }
        return -1;
    }

    /** A área do tipo na linha {@code row}, no quadro arranjado, ou {@code null}. */
    @Nullable Hot hot(Kind kind, int row) {
        return list.layoutHot(kind, row);
    }
}
