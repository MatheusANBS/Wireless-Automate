package io.github.matheusanbs.wirelessautomate.client.tablet;

import io.github.matheusanbs.wirelessautomate.network.NodeIndex.NodeKey;
import io.github.matheusanbs.wirelessautomate.packet.TabletProfilePayload;
import io.github.matheusanbs.wirelessautomate.packet.TabletProfilePayload.Action;
import java.util.List;
import org.jetbrains.annotations.Nullable;

/**
 * As ações do perfil na Lista do Tablet: copiar do nó escolhido, colar nele ou nos marcados, desfazer
 * e esquecer, mandadas pelo {@link TabletActions}.
 */
final class ListProfileActions {
    private final TabletList list;

    ListProfileActions(TabletList list) {
        this.list = list;
    }

    private void send(Action action, @Nullable NodeKey key, List<NodeKey> keys) {
        list.actions.profile(action, key, keys);
    }

    void copy() {
        if (list.chosen != null && list.chosenPanel.canEdit()) {
            send(Action.COPY, list.chosen, List.of());
        }
    }

    void paste() {
        if (list.chosen != null && list.profile().isPresent() && list.chosenPanel.canEdit()) {
            send(Action.PASTE_ONE, list.chosen, List.of());
        }
    }

    void undo() {
        send(Action.UNDO, null, List.of());
    }

    void forget() {
        send(Action.FORGET, null, List.of());
    }

    /** Cola nos marcados: os roteadores (na presa) e as máquinas, os grupos e os blocos. */
    void pasteSelected() {
        if (list.profile().isPresent() && !list.marks.isEmpty()) {
            list.actions.profile(Action.PASTE_SELECTED, null, list.marks.nodeList(TabletProfilePayload.MAX_KEYS),
                    list.marks.payload());
        }
    }
}
