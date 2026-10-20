package io.github.matheusanbs.wirelessautomate.client.tablet;

import io.github.matheusanbs.wirelessautomate.menu.TabletSnapshot;
import io.github.matheusanbs.wirelessautomate.network.NodeIndex.NodeKey;
import io.github.matheusanbs.wirelessautomate.packet.TabletProfilePayload;
import io.github.matheusanbs.wirelessautomate.packet.TabletProfilePayload.Action;
import java.util.List;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/**
 * As ações do perfil na Lista do Tablet: copiar do nó escolhido, colar nele, nos marcados ou num grupo, desfazer
 * e esquecer, mandadas pelo {@link TabletActions}, e quantos nós o "Colar nas N" atinge.
 */
final class ListProfileActions {
    private final TabletList list;

    ListProfileActions(TabletList list) {
        this.list = list;
    }

    private void send(Action action, @Nullable NodeKey key, @Nullable ResourceLocation machine, List<NodeKey> keys) {
        list.actions.profile(action, key, machine, keys);
    }

    void copy() {
        if (list.chosen != null && list.chosenPanel.canEdit()) {
            send(Action.COPY, list.chosen, null, List.of());
        }
    }

    void paste() {
        if (list.chosen != null && list.profile().isPresent() && list.chosenPanel.canEdit()) {
            send(Action.PASTE_ONE, list.chosen, null, List.of());
        }
    }

    void undo() {
        send(Action.UNDO, null, null, List.of());
    }

    void forget() {
        send(Action.FORGET, null, null, List.of());
    }

    /** Cola nos marcados: os roteadores (na presa) e as máquinas, os grupos e os blocos. */
    void pasteSelected() {
        if (list.profile().isPresent() && !list.marks.isEmpty()) {
            list.actions.profile(Action.PASTE_SELECTED, null, null, list.marks.nodeList(TabletProfilePayload.MAX_KEYS),
                    list.marks.payload());
        }
    }
}
