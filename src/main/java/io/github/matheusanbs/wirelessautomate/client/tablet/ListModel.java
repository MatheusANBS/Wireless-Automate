package io.github.matheusanbs.wirelessautomate.client.tablet;

import io.github.matheusanbs.wirelessautomate.menu.TabletSnapshot;
import io.github.matheusanbs.wirelessautomate.menu.TabletSnapshot.NodeView;
import io.github.matheusanbs.wirelessautomate.network.NodeIndex.NodeKey;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/**
 * O modelo das linhas da Lista do Tablet: os nós do snapshot agrupados pela máquina ({@link ListGroups}), os
 * grupos recolhidos e a busca de um nó pela chave. As linhas são refeitas só quando os nós ou os grupos
 * recolhidos mudam, nunca por quadro. O desenho delas é do {@link ListRows}, a geometria do {@link ListLayout}.
 */
final class ListModel {
    private final Supplier<TabletSnapshot> snapshot;
    private final Set<ResourceLocation> collapsed = new HashSet<>();
    private @Nullable ListGroups<ResourceLocation> groups;
    private @Nullable List<NodeView> groupsFor;
    private Set<ResourceLocation> groupsCollapsed = Set.of();

    ListModel(Supplier<TabletSnapshot> snapshot) {
        this.snapshot = snapshot;
    }

    ListGroups<ResourceLocation> groups() {
        List<NodeView> nodeViews = snapshot.get().nodes();
        if (groups == null || groupsFor != nodeViews || !groupsCollapsed.equals(collapsed)) {
            groups = new ListGroups<>(nodeViews.stream().map(NodeView::machine).toList(), Set.copyOf(collapsed));
            groupsFor = nodeViews;
            groupsCollapsed = Set.copyOf(collapsed);
        }
        return groups;
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
        collapsed.add(group);
    }

    void expand(ResourceLocation group) {
        collapsed.remove(group);
    }

    /** Recolhe o grupo aberto ou abre o recolhido (o clique no cabeçalho). */
    void toggle(ResourceLocation group) {
        if (!collapsed.remove(group)) {
            collapsed.add(group);
        }
    }
}
