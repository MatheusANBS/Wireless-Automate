package io.github.matheusanbs.wirelessautomate.client.tablet;

import io.github.matheusanbs.wirelessautomate.menu.TabletMenu;
import io.github.matheusanbs.wirelessautomate.menu.TabletMenu.Action;
import io.github.matheusanbs.wirelessautomate.menu.TabletSnapshot.Query;
import io.github.matheusanbs.wirelessautomate.network.NodeIndex.NodeKey;
import io.github.matheusanbs.wirelessautomate.network.ResourceType;
import io.github.matheusanbs.wirelessautomate.packet.ClearIdleNetworksPayload;
import io.github.matheusanbs.wirelessautomate.packet.TabletActionPayload;
import io.github.matheusanbs.wirelessautomate.packet.TabletMoveNodesPayload;
import io.github.matheusanbs.wirelessautomate.packet.TabletOpenNodePayload;
import io.github.matheusanbs.wirelessautomate.packet.TabletProfilePayload;
import io.github.matheusanbs.wirelessautomate.packet.TabletQueryPayload;
import io.github.matheusanbs.wirelessautomate.packet.TabletSelectNodePayload;
import io.github.matheusanbs.wirelessautomate.packet.TabletTabPayload;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

/**
 * Tudo o que a tela do Tablet manda ao servidor, pelo menu dela. Na prévia (captura de desenvolvimento, sem
 * servidor) nada sai.
 */
final class TabletActions {
    private final TabletMenu menu;
    private final boolean preview;

    TabletActions(TabletMenu menu, boolean preview) {
        this.menu = menu;
        this.preview = preview;
    }

    int containerId() {
        return menu.containerId;
    }

    void send(CustomPacketPayload payload) {
        if (!preview) {
            PacketDistributor.sendToServer(payload);
        }
    }

    void tab(int tab) {
        send(new TabletTabPayload(menu.containerId, tab));
    }

    void query(Query query) {
        send(new TabletQueryPayload(menu.containerId, query));
    }

    void selectNode(@Nullable NodeKey key) {
        send(new TabletSelectNodePayload(menu.containerId, Optional.ofNullable(key)));
    }

    void openNode(NodeKey key) {
        send(new TabletOpenNodePayload(menu.containerId, key));
    }

    void moveNodes(List<NodeKey> keys, Optional<ResourceType> type, Optional<UUID> network) {
        send(new TabletMoveNodesPayload(menu.containerId, List.copyOf(keys), type, network));
    }

    void profile(TabletProfilePayload.Action action, @Nullable NodeKey key, @Nullable ResourceLocation machine,
            List<NodeKey> keys) {
        send(new TabletProfilePayload(menu.containerId, action, Optional.ofNullable(key), Optional.ofNullable(machine), keys));
    }

    void clearIdleNetworks(boolean undo) {
        send(new ClearIdleNetworksPayload(menu.containerId, undo));
    }

    void networkPublic(UUID network, boolean value) {
        send(new TabletActionPayload(menu.containerId, Action.NETWORK_PUBLIC, Optional.of(network),
                Optional.empty(), "", value ? 1 : 0));
    }

    void networkUse(UUID network) {
        send(new TabletActionPayload(menu.containerId, Action.NETWORK_USE, network));
    }

    void networkColor(UUID network, int color) {
        send(new TabletActionPayload(menu.containerId, Action.NETWORK_COLOR, Optional.of(network),
                Optional.empty(), "", color));
    }

    void groupPause(UUID group, boolean paused) {
        send(new TabletActionPayload(menu.containerId, Action.GROUP_PAUSE, Optional.of(group),
                Optional.empty(), "", paused ? 0 : 1));
    }

    /** Marca ou desmarca a rede no grupo. */
    void groupNetwork(UUID group, UUID network, boolean inGroup) {
        Action action = inGroup ? Action.GROUP_REMOVE_NETWORK : Action.GROUP_ADD_NETWORK;
        send(new TabletActionPayload(menu.containerId, action, Optional.of(group), Optional.of(network), "", 0));
    }

    /** Remover a rede ou o grupo (já confirmado). */
    void remove(Action action, UUID id) {
        send(new TabletActionPayload(menu.containerId, action, id));
    }

    void create(Action action, String name) {
        send(new TabletActionPayload(menu.containerId, action, Optional.empty(), Optional.empty(), name, 0));
    }

    void rename(Action action, UUID id, String name) {
        send(new TabletActionPayload(menu.containerId, action, Optional.of(id), Optional.empty(), name, 0));
    }
}
