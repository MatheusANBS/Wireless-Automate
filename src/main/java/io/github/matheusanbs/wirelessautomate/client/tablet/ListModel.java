package io.github.matheusanbs.wirelessautomate.client.tablet;

import io.github.matheusanbs.wirelessautomate.client.tablet.ListTree.Kind;
import io.github.matheusanbs.wirelessautomate.client.tablet.ListTree.TreeRow;
import io.github.matheusanbs.wirelessautomate.menu.TabletRows;
import io.github.matheusanbs.wirelessautomate.menu.TabletSnapshot;
import io.github.matheusanbs.wirelessautomate.menu.TabletSnapshot.NodeView;
import io.github.matheusanbs.wirelessautomate.network.NodeIndex.NodeKey;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import org.jetbrains.annotations.Nullable;

/**
 * O modelo das linhas da Lista do Tablet: os nós (roteadores) do snapshot, com as linhas da árvore dos nós abertos
 * ({@link ListTree}) e a busca de um nó pela chave. As linhas são refeitas só quando a página muda, nunca por quadro. O desenho delas é do {@link ListRows} e do
 * {@link TreeRows}, a geometria do {@link ListLayout}.
 */
final class ListModel {
    private final Supplier<TabletSnapshot> snapshot;
    private @Nullable ListTree tree;
    private @Nullable List<NodeView> treeNodes;
    private @Nullable List<TabletRows.Row> treeRows;
    private boolean anyLinked;

    ListModel(Supplier<TabletSnapshot> snapshot) {
        this.snapshot = snapshot;
    }

    ListTree tree() {
        TabletSnapshot s = snapshot.get();
        List<NodeView> nodeViews = s.nodes();
        List<TabletRows.Row> rows = s.rows().rows();
        if (tree == null || treeNodes != nodeViews || treeRows != rows) {
            boolean[] openable = new boolean[nodeViews.size()];
            anyLinked = false;
            for (int i = 0; i < nodeViews.size(); i++) {
                openable[i] = nodeViews.get(i).linked() > 0;
                anyLinked |= openable[i];
            }
            List<TreeRow> treeRowList = new ArrayList<>(rows.size());
            for (TabletRows.Row row : rows) {
                treeRowList.add(switch (row) {
                    case TabletRows.Attached a -> new TreeRow(a.parent(), Kind.ATTACHED, false);
                    case TabletRows.Group g -> new TreeRow(g.parent(), Kind.GROUP, g.collapsed());
                    case TabletRows.Machine m -> new TreeRow(m.parent(), Kind.MACHINE, false);
                    case TabletRows.More m -> new TreeRow(m.parent(), Kind.MORE, false);
                });
            }
            tree = new ListTree(nodeViews.size(), treeRowList, openable);
            treeNodes = nodeViews;
            treeRows = rows;
        }
        return tree;
    }

    /** Algum nó da página tem máquinas vinculadas: as linhas dos nós guardam a coluna do ▸/▾. */
    boolean anyLinked() {
        tree();
        return anyLinked;
    }

    /** A linha do servidor da linha {@code line} da árvore. */
    TabletRows.Row row(ListTree.Line line) {
        return snapshot.get().rows().rows().get(line.row());
    }

    int indexOf(@Nullable NodeKey key) {
        List<NodeView> nodeViews = snapshot.get().nodes();
        for (int i = 0; i < nodeViews.size(); i++) {
            if (nodeViews.get(i).key().equals(key)) {
                return i;
            }
        }
        return -1;
    }

    @Nullable NodeView node(@Nullable NodeKey key) {
        int index = indexOf(key);
        return index < 0 ? null : snapshot.get().nodes().get(index);
    }
}
