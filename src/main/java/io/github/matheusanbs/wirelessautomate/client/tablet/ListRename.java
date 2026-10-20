package io.github.matheusanbs.wirelessautomate.client.tablet;

import io.github.matheusanbs.wirelessautomate.client.GuiPaint;
import io.github.matheusanbs.wirelessautomate.client.router.LabelEditor;
import io.github.matheusanbs.wirelessautomate.client.router.RangeRowButtons;
import io.github.matheusanbs.wirelessautomate.client.tablet.ListLayout.Hot;
import io.github.matheusanbs.wirelessautomate.menu.TabletSnapshot.NodeView;
import io.github.matheusanbs.wirelessautomate.network.NodeIndex.NodeKey;
import io.github.matheusanbs.wirelessautomate.packet.RenameRouterPayload;
import io.github.matheusanbs.wirelessautomate.range.EditTarget;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * Renomear na Lista do Tablet: o nome do roteador (primeira linha da linha dele) e o da máquina presa (linha da
 * presa na árvore), pelo ✎ que aparece com o mouse em cima ou por dois cliques no nome. A caixa
 * ({@link LabelEditor}) fica no lugar do nome, posta pelo desenho a cada quadro; Enter salva e Esc desiste. Só com o
 * nó escolhido e o painel editável: o servidor aplica ao nó escolhido do Tablet.
 */
final class ListRename {
    enum Kind { ROUTER, ATTACHED }

    private static final long DOUBLE_CLICK_MS = 400;
    private static final int PENCIL_W = 9;
    /** A altura da primeira linha de uma linha de duas, onde ficam o nome e o ✎. */
    private static final int FIRST_H = 11;

    private final TabletList list;
    private final LabelEditor editor;
    private @Nullable Kind kind;
    private @Nullable NodeKey node;
    private @Nullable Kind lastKind;
    private @Nullable NodeKey lastNode;
    private long lastAt;
    private boolean placed;

    ListRename(TabletList list) {
        this.list = list;
        this.editor = new LabelEditor((target, pos, name) -> commit(name));
    }

    LabelEditor editor() {
        return editor;
    }

    boolean editing(Kind what, NodeKey key) {
        return editor.editing() && what == kind && key.equals(node);
    }

    private boolean canEdit(NodeKey key) {
        return !list.multi && key.equals(list.chosen) && list.chosenPanel.canEdit();
    }

    // ------------------------------------------------------------------ geometria

    /** O retângulo do ✎ na primeira linha, encostado em {@code right}. */
    private static boolean onPencil(Hot hot, int right, double mx, double my) {
        return mx >= right - PENCIL_W && mx < right && my >= hot.y() && my < hot.y() + FIRST_H;
    }

    /** O ✎ da linha, só com o mouse nela, o nó escolhido, o painel editável e a caixa fechada. */
    void paintPencil(GuiGraphics g, Kind what, NodeKey key, Hot hot, int right, int mx, int my) {
        if (editor.editing() || !hot.contains(mx, my) || !canEdit(key)) {
            return;
        }
        int color = onPencil(hot, right, mx, my) ? GuiPaint.FG : GuiPaint.MUTED;
        RangeRowButtons.pencil(g, right - PENCIL_W + 1, hot.y() + 2, color);
    }

    // ------------------------------------------------------------------ cliques

    /** Um clique numa linha: o ✎ ou o segundo clique no nome abrem a caixa. {@code false} segue como clique comum. */
    boolean click(Kind what, NodeKey key, Hot hot, int nameX, int right, double mx, double my) {
        boolean first = my >= hot.y() && my < hot.y() + FIRST_H;
        boolean pencil = onPencil(hot, right, mx, my) && canEdit(key);
        boolean second = first && mx >= nameX && what == lastKind && key.equals(lastNode)
                && Util.getMillis() - lastAt <= DOUBLE_CLICK_MS;
        if (first && mx >= nameX && !second) {
            lastKind = what;
            lastNode = key;
            lastAt = Util.getMillis();
        } else {
            lastKind = null;
            lastNode = null;
        }
        if ((pencil || second) && canEdit(key)) {
            lastKind = null;
            lastNode = null;
            start(what, key);
            return true;
        }
        return false;
    }

    private void start(Kind what, NodeKey key) {
        NodeView view = null;
        for (NodeView n : list.snapshot().nodes()) {
            if (n.key().equals(key)) {
                view = n;
                break;
            }
        }
        if (view == null) {
            return;
        }
        kind = what;
        node = key;
        if (what == Kind.ROUTER) {
            editor.start(EditTarget.ATTACHED_TARGET, null, view.name(), TabletText.tr("router.default",
                    Component.translatable(view.tier().translationKey())));
        } else {
            editor.start(EditTarget.ATTACHED_TARGET, null, view.attachedLabel(), Component.literal(list.texts.machineName(view.machine())));
        }
    }

    private void commit(String name) {
        NodeKey key = node;
        if (key == null || !key.equals(list.chosen) || kind == null) {
            return;
        }
        if (kind == Kind.ROUTER) {
            list.actions.send(new RenameRouterPayload(list.actions.containerId(), name));
        } else {
            list.actions.send(LabelEditor.payload(list.actions.containerId(), EditTarget.ATTACHED_TARGET, null, name));
        }
    }

    // ------------------------------------------------------------------ desenho

    void beginFrame() {
        placed = false;
    }

    /** O desenho da linha em edição chama: a caixa vai no lugar do nome. */
    void place(int x, int y, int w) {
        editor.place(x - 3, y + 1, w + 3);
        placed = true;
    }

    /** A caixa no fim do quadro; a linha que saiu da vista salva, e o nó que deixou de ser o escolhido desiste. */
    void endFrame(GuiGraphics g, int mx, int my) {
        if (!editor.editing()) {
            return;
        }
        if (node == null || !node.equals(list.chosen)) {
            editor.finish(false);
        } else if (!placed) {
            editor.finish(true);
        } else {
            editor.render(g, mx, my);
        }
    }

    @Nullable Component tooltip(double mx, double my) {
        return editor.tooltip(mx, my);
    }
}
