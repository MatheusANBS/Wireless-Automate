package io.github.matheusanbs.wirelessautomate.client.tablet;

import io.github.matheusanbs.wirelessautomate.client.GuiPaint;
import io.github.matheusanbs.wirelessautomate.client.GuiText;
import io.github.matheusanbs.wirelessautomate.client.highlight.HighlightRenderer;
import io.github.matheusanbs.wirelessautomate.client.tablet.ListLayout.Hot;
import io.github.matheusanbs.wirelessautomate.client.tablet.ListLayout.Kind;
import io.github.matheusanbs.wirelessautomate.client.tablet.ListTree.Line;
import io.github.matheusanbs.wirelessautomate.menu.TabletRows;
import io.github.matheusanbs.wirelessautomate.menu.TabletRows.MachineLive;
import io.github.matheusanbs.wirelessautomate.menu.TabletRows.MachineStatus;
import io.github.matheusanbs.wirelessautomate.menu.TabletSnapshot.NodeView;
import io.github.matheusanbs.wirelessautomate.packet.TabletPanelPayload;
import io.github.matheusanbs.wirelessautomate.range.EditTarget;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/**
 * O desenho e as áreas de clique das linhas da árvore na Lista do Tablet (especificação, "A árvore na Lista"):
 * o ▸/▾ na frente do nó com máquinas vinculadas; a presa ("Máquina presa", o bloco e o lado do roteador); o
 * grupo ("Fornalha ×12", as exceções e o "Mostrar menos"), com o ▸/▾ que o recolhe; a máquina (o nome, as
 * coordenadas e o estado em palavras, e à direita a marca "exceção", a vazão na cor do tipo e o Olho ◉); e o
 * "… mais N · Mostrar todas". Os textos vêm prontos do {@link TreeTexts}; aqui só se compara largura e desenha.
 */
final class TreeRows {
    /** A coluna do ▸/▾ dos nós, quando algum nó da página tem máquinas. */
    static final int CHEV_W = 8;
    /** O recuo de cada nível da árvore. */
    static final int INDENT = 8;
    static final int EYE_W = 10;
    private static final int LINE1_Y = 2;
    private static final int LINE2_Y = 11;
    /** A primeira linha de uma linha de duas: onde ficam o ▸/▾ e o Olho. */
    private static final int FIRST_H = 11;
    /** O nome de uma máquina guarda ao menos isto antes da marca e da vazão (que vão na dica quando não cabem). */
    private static final int NAME_MIN = 28;

    private final TabletList list;
    final TreeTexts texts;

    TreeRows(TabletList list) {
        this.list = list;
        this.texts = new TreeTexts(list.texts);
    }

    // ------------------------------------------------------------------ geometria

    /** Altura de uma linha da lista: o cabeçalho e o "mais N" numa linha, o grupo numa ou duas, o resto em duas. */
    static int height(Line<ResourceLocation> line, @Nullable TabletRows.Row row) {
        return switch (line.kind()) {
            case HEADER, MORE -> ListLayout.HEADER_ROW;
            case GROUP -> row instanceof TabletRows.Group group && (group.exceptions() > 0 || group.showAll())
                    ? ListLayout.NODE_ROW : ListLayout.HEADER_ROW;
            case NODE, ATTACHED, MACHINE -> ListLayout.NODE_ROW;
        };
    }

    /** A largura da coluna do ▸/▾: só quando algum nó da página tem máquinas vinculadas. */
    int chevronWidth() {
        return list.model.anyLinked() ? CHEV_W : 0;
    }

    /** Onde o conteúdo de uma linha começa: depois da caixa do Selecionar e da coluna do ▸/▾. */
    int contentX(int rowX) {
        return rowX + 5 + (list.multi ? 12 : 0) + chevronWidth();
    }

    /** O ▸/▾ de um nó com máquinas vinculadas. */
    void arrangeNode(List<Hot> hots, NodeView node, int line, int x, int y, int h) {
        if (node.linked() > 0) {
            hots.add(new Hot(Kind.TREE_TOGGLE, x + 3 + (list.multi ? 12 : 0), y, CHEV_W + 2, h, line));
        }
    }

    /** A linha da árvore e, dentro dela, o ▸/▾ do grupo, o "Mostrar menos" e o Olho da máquina. */
    void arrange(Font font, List<Hot> hots, Line<ResourceLocation> line, int index, int x, int y, int w, int h) {
        texts.prepare(font, list.snapshot());
        hots.add(new Hot(Kind.TREE, x, y, w, h, index));
        int tx = contentX(x) + line.depth() * INDENT;
        TabletRows.Row row = list.model.row(line);
        switch (row) {
            case TabletRows.Group group -> {
                hots.add(new Hot(Kind.TREE_TOGGLE, tx - 2, y, CHEV_W + 2, Math.min(h, FIRST_H + 1), index));
                if (group.showAll()) {
                    int r = line.row();
                    int lx = tx + 8 + (texts.secondW[r] > 0 ? texts.secondW[r] + texts.separatorW : 0);
                    hots.add(new Hot(Kind.TREE_FEW, lx, y + LINE2_Y - 1, Math.min(texts.lessW, x + w - 3 - lx),
                            10, index));
                }
            }
            case TabletRows.Machine machine -> hots.add(new Hot(Kind.TREE_EYE, x + w - 3 - EYE_W, y, EYE_W, FIRST_H,
                    index));
            default -> {
            }
        }
    }

    // ------------------------------------------------------------------ desenho

    /** O ▸/▾ na frente do nó, em {@code x}: aberto, ▾. */
    void renderNodeChevron(GuiGraphics g, int x, int y, NodeView node, int line, boolean hovered) {
        if (node.linked() <= 0) {
            return;
        }
        int color = hovered ? GuiPaint.FG : GuiPaint.MUTED;
        if (list.model.tree().open(list.model.tree().lines().get(line).node())) {
            GuiPaint.arrowDown(g, x, y + 8, color);
        } else {
            GuiPaint.arrowRight(g, x + 1, y + 7, color);
        }
    }

    void render(GuiGraphics g, Font font, Hot hot, Line<ResourceLocation> line, int mx, int my) {
        texts.prepare(font, list.snapshot());
        TabletRows.Row row = list.model.row(line);
        int x = hot.x();
        int y = hot.y();
        int w = hot.w();
        int h = hot.h();
        int tx = contentX(x) + line.depth() * INDENT;
        ListMarks.Box box = list.multi && !(row instanceof TabletRows.More) ? list.markRows.treeBox(hot.arg())
                : ListMarks.Box.NONE;
        if (isTarget(line, row) || box == ListMarks.Box.ALL) {
            g.fill(x, y, x + w, y + h - 1, GuiPaint.ROW_SELECTED);
            g.fill(x, y, x + 2, y + h - 1, GuiPaint.ACCENT);
        } else if (hot.contains(mx, my) || list.treeNav.isCursor(hot.arg())) {
            g.fill(x, y, x + w, y + h - 1, GuiPaint.ROW_HOVER);
        }
        if (list.multi && !(row instanceof TabletRows.More)) {
            MarkRows.paint(g, tx - 12, y + (h > ListLayout.HEADER_ROW ? 5 : 2), box);
        }
        g.fill(tx, y + h - 1, x + w - 2, y + h, GuiPaint.LINE);
        int r = line.row();
        int right = x + w - 3;
        switch (row) {
            case TabletRows.Attached attached -> {
                GuiText.draw(g, font, texts.names[r], tx + 8, y + LINE1_Y, right - tx - 8, GuiPaint.FG);
                GuiText.draw(g, font, texts.seconds[r], tx + 8, y + LINE2_Y, right - tx - 8, GuiPaint.MUTED);
            }
            case TabletRows.Group group -> renderGroup(g, font, hot, group, r, tx, right, mx, my);
            case TabletRows.Machine machine -> renderMachine(g, font, hot, line, machine, r, tx, right);
            case TabletRows.More more -> {
                GuiText.draw(g, font, texts.names[r], tx + 8, y + LINE1_Y, right - tx - 8, GuiPaint.ACCENT);
                if (hot.contains(mx, my)) {
                    int uw = Math.min(texts.nameW[r], right - tx - 8);
                    g.fill(tx + 8, y + LINE1_Y + 9, tx + 8 + uw, y + LINE1_Y + 10, GuiPaint.ACCENT);
                }
            }
        }
    }

    private void renderGroup(GuiGraphics g, Font font, Hot hot, TabletRows.Group group, int r, int tx, int right,
            int mx, int my) {
        int y = hot.y();
        Hot toggle = hotOf(Kind.TREE_TOGGLE, hot.arg());
        int color = toggle != null && toggle.contains(mx, my) ? GuiPaint.FG : GuiPaint.MUTED;
        if (group.collapsed()) {
            GuiPaint.arrowRight(g, tx + 1, y + 3, color);
        } else {
            GuiPaint.arrowDown(g, tx, y + 4, color);
        }
        GuiText.draw(g, font, texts.names[r], tx + 8, y + LINE1_Y, right - tx - 8, GuiPaint.FG);
        int sx = tx + 8;
        if (texts.secondW[r] > 0) {
            GuiText.draw(g, font, texts.seconds[r], sx, y + LINE2_Y, right - sx, GuiPaint.MUTED);
            sx += texts.secondW[r];
            if (group.showAll()) {
                GuiPaint.text(g, font, TreeTexts.SEPARATOR, sx, y + LINE2_Y, GuiPaint.MUTED);
                sx += texts.separatorW;
            }
        }
        Hot less = hotOf(Kind.TREE_FEW, hot.arg());
        if (group.showAll() && less != null && less.w() > 0) {
            GuiText.draw(g, font, texts.less, less.x(), y + LINE2_Y, less.w(), GuiPaint.ACCENT);
            if (less.contains(mx, my)) {
                g.fill(less.x(), y + LINE2_Y + 9, less.x() + less.w(), y + LINE2_Y + 10, GuiPaint.ACCENT);
            }
        }
    }

    private void renderMachine(GuiGraphics g, Font font, Hot hot, Line<ResourceLocation> line,
            TabletRows.Machine machine, int r, int tx, int right) {
        int y = hot.y();
        MachineLive live = texts.lives[r];
        MachineStatus status = live == null ? MachineStatus.UNLOADED : live.status();
        GuiPaint.dot(g, tx, y + 7, TreeTexts.dotColor(status));
        NodeView node = list.snapshot().nodes().get(line.node());
        boolean lit = HighlightRenderer.isLit(node.key().dimension(), machine.pos());
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
        int room = right - textX;
        int stateColor = TreeTexts.stateColor(status);
        if (texts.secondW[r] + texts.separatorW + texts.stateW[r] <= room) {
            GuiPaint.text(g, font, texts.seconds[r], textX, y + LINE2_Y, GuiPaint.MUTED);
            int sx = textX + texts.secondW[r];
            GuiPaint.text(g, font, TreeTexts.SEPARATOR, sx, y + LINE2_Y, GuiPaint.MUTED);
            GuiPaint.text(g, font, texts.states[r], sx + texts.separatorW, y + LINE2_Y, stateColor);
        } else {
            // sem lugar para tudo, o estado diz mais que as coordenadas (que ficam na dica)
            GuiText.draw(g, font, texts.states[r], textX, y + LINE2_Y, room, stateColor);
        }
    }

    private @Nullable Hot hotOf(Kind kind, int line) {
        return list.layoutHot(kind, line);
    }

    // ------------------------------------------------------------------ o alvo do painel

    /** O alvo do painel do nó escolhido, ou {@code null} sem painel. */
    @Nullable EditTarget panelTarget() {
        TabletPanelPayload payload = list.panelPayload();
        return payload == null || payload.snapshot().isEmpty() ? null : payload.snapshot().get().target();
    }

    /** A linha é o alvo do painel do nó escolhido. */
    boolean isTarget(Line<ResourceLocation> line, TabletRows.Row row) {
        NodeView node = list.snapshot().nodes().get(line.node());
        if (!node.key().equals(list.chosen)) {
            return false;
        }
        EditTarget target = panelTarget();
        if (target == null) {
            return false;
        }
        return switch (row) {
            case TabletRows.Attached attached -> target.attached();
            case TabletRows.Group group -> target.kind() == EditTarget.Kind.GROUP && target.id() == group.id();
            case TabletRows.Machine machine -> target.kind() == EditTarget.Kind.MACHINE
                    && target.id() == machine.index();
            case TabletRows.More more -> false;
        };
    }
}
