package io.github.matheusanbs.wirelessautomate.client.tablet;

import io.github.matheusanbs.wirelessautomate.client.tablet.ListTree.Kind;
import io.github.matheusanbs.wirelessautomate.client.tablet.ListTree.TreeRow;
import io.github.matheusanbs.wirelessautomate.menu.TabletRows;
import io.github.matheusanbs.wirelessautomate.menu.TabletSnapshot;
import io.github.matheusanbs.wirelessautomate.menu.TabletSnapshot.NodeView;
import io.github.matheusanbs.wirelessautomate.network.NodeIndex.NodeKey;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/**
 * O modelo das linhas da Lista do Tablet: os nós do snapshot agrupados pela máquina, com as linhas da árvore dos
 * nós abertos ({@link ListTree}), os grupos de nós recolhidos e a busca de um nó pela chave. As linhas são refeitas
 * só quando a página ou os grupos recolhidos mudam, nunca por quadro. O desenho delas é do {@link ListRows} e do
 * {@link TreeRows}, a geometria do {@link ListLayout}.
 */
final class ListModel {
    private final Supplier<TabletSnapshot> snapshot;
    private final Set<ResourceLocation> collapsed = new HashSet<>();
    private @Nullable ListTree<ResourceLocation> tree;
    private @Nullable List<NodeView> treeNodes;
    private @Nullable List<TabletRows.Row> treeRows;
    /** Sobe a cada mudança dos grupos recolhidos: a árvore compara o número, sem percorrer o conjunto. */
    private int collapsedVersion;
    private int treeCollapsed = -1;
    private boolean anyLinked;

    ListModel(Supplier<TabletSnapshot> snapshot) {
        this.snapshot = snapshot;
    }

    ListTree<ResourceLocation> tree() {
        TabletSnapshot s = snapshot.get();
        List<NodeView> nodeViews = s.nodes();
        List<TabletRows.Row> rows = s.rows().rows();
        if (tree == null || treeNodes != nodeViews || treeRows != rows || treeCollapsed != collapsedVersion) {
            List<ResourceLocation> machines = new ArrayList<>(nodeViews.size());
            anyLinked = false;
            for (NodeView node : nodeViews) {
                machines.add(node.machine());
                anyLinked |= node.linked() > 0;
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
            tree = new ListTree<>(machines, treeRowList, Set.copyOf(collapsed));
            treeNodes = nodeViews;
            treeRows = rows;
            treeCollapsed = collapsedVersion;
        }
        return tree;
    }

    /** Algum nó da página tem máquinas vinculadas: as linhas dos nós guardam a coluna do ▸/▾. */
    boolean anyLinked() {
        tree();
        return anyLinked;
    }

    /** A linha do servidor da linha {@code line} da árvore. */
    TabletRows.Row row(ListTree.Line<ResourceLocation> line) {
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

    /** O grupo da máquina está recolhido. */
    boolean collapsed(ResourceLocation group) {
        return collapsed.contains(group);
    }

    void collapse(ResourceLocation group) {
        if (collapsed.add(group)) {
            collapsedVersion++;
        }
    }

    void expand(ResourceLocation group) {
        if (collapsed.remove(group)) {
            collapsedVersion++;
        }
    }

    /** Recolhe o grupo aberto ou abre o recolhido (o clique no cabeçalho). */
    void toggle(ResourceLocation group) {
        if (!collapsed.remove(group)) {
            collapsed.add(group);
        }
        collapsedVersion++;
    }
}
