package io.github.matheusanbs.wirelessautomate.client.tablet;

import io.github.matheusanbs.wirelessautomate.menu.TabletMenu;
import io.github.matheusanbs.wirelessautomate.menu.TabletSnapshot;
import io.github.matheusanbs.wirelessautomate.menu.TabletSnapshot.ProfileSummary;
import io.github.matheusanbs.wirelessautomate.network.NodeIndex.NodeKey;
import io.github.matheusanbs.wirelessautomate.packet.TabletProfilePayload;
import io.github.matheusanbs.wirelessautomate.packet.TabletProfilePayload.Action;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
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

    void pasteGroup(ResourceLocation machine) {
        send(Action.PASTE_GROUP, null, machine, List.of());
    }

    void pasteSelected() {
        if (list.profile().isPresent() && !list.selected.isEmpty()) {
            List<NodeKey> keys = new ArrayList<>(list.selected);
            send(Action.PASTE_SELECTED, null, null,
                    List.copyOf(keys.size() > TabletProfilePayload.MAX_KEYS ? keys.subList(0, TabletProfilePayload.MAX_KEYS)
                            : keys));
        }
    }

    /** Quantos nós do grupo o "Colar nas N" atinge: os da consulta, menos a origem do perfil. */
    int pasteCount(ResourceLocation machine) {
        ProfileSummary summary = list.profile().orElse(null);
        if (summary == null) {
            return 0;
        }
        TabletSnapshot s = list.snapshot();
        int count = s.groupCount(machine);
        if (s.originGroup().equals(Optional.of(machine))) {
            count--;
        }
        return Math.min(count, TabletMenu.MAX_MOVE);
    }
}
