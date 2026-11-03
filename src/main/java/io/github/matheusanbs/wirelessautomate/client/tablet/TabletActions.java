package io.github.matheusanbs.wirelessautomate.client.tablet;

import io.github.matheusanbs.wirelessautomate.menu.TabletMarks;
import io.github.matheusanbs.wirelessautomate.menu.TabletMenu;
import io.github.matheusanbs.wirelessautomate.menu.TabletMenu.Action;
import io.github.matheusanbs.wirelessautomate.menu.TabletSnapshot.Query;
import io.github.matheusanbs.wirelessautomate.network.NodeIndex.NodeKey;
import io.github.matheusanbs.wirelessautomate.network.ResourceType;
import io.github.matheusanbs.wirelessautomate.packet.ClearIdleNetworksPayload;
import io.github.matheusanbs.wirelessautomate.packet.NetworkSuggestPayload;
import io.github.matheusanbs.wirelessautomate.packet.TabletActionPayload;
import io.github.matheusanbs.wirelessautomate.packet.TabletMachinesPayload;
import io.github.matheusanbs.wirelessautomate.packet.TabletMoveNodesPayload;
import io.github.matheusanbs.wirelessautomate.packet.TabletOpenNodePayload;
import io.github.matheusanbs.wirelessautomate.packet.TabletProfilePayload;
import io.github.matheusanbs.wirelessautomate.packet.TabletQueryPayload;
import io.github.matheusanbs.wirelessautomate.packet.TabletSelectNodePayload;
import io.github.matheusanbs.wirelessautomate.packet.TabletTabPayload;
import io.github.matheusanbs.wirelessautomate.packet.TabletTreePayload;
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
public final class TabletActions {
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

    void tree(TabletTreePayload.Action action, NodeKey node, int group) {
        send(new TabletTreePayload(menu.containerId, action, node, group));
    }

    /** Troca a visão da Lista ou abre, recolhe, mostra todas ou poucas máquinas do bloco. */
    void machines(TabletMachinesPayload.Action action, Optional<ResourceLocation> block) {
        send(new TabletMachinesPayload(menu.containerId, action, block));
    }

    void openNode(NodeKey key) {
        send(new TabletOpenNodePayload(menu.containerId, key));
    }

    void moveNodes(List<NodeKey> keys, TabletMarks marks, Optional<ResourceType> type, Optional<UUID> network) {
        send(new TabletMoveNodesPayload(menu.containerId, List.copyOf(keys), marks, type, network));
    }

    void profile(TabletProfilePayload.Action action, @Nullable NodeKey key, List<NodeKey> keys) {
        profile(action, key, keys, TabletMarks.EMPTY);
    }

    void profile(TabletProfilePayload.Action action, @Nullable NodeKey key, List<NodeKey> keys, TabletMarks marks) {
        send(new TabletProfilePayload(menu.containerId, action, Optional.ofNullable(key), keys, marks));
    }

    void clearIdleNetworks(boolean undo) {
        send(new ClearIdleNetworksPayload(menu.containerId, undo));
    }

    public void networkPublic(UUID network, boolean value) {
        send(new TabletActionPayload(menu.containerId, Action.NETWORK_PUBLIC, Optional.of(network),
                Optional.empty(), "", value ? 1 : 0));
    }

    public void networkUse(UUID network) {
        send(new TabletActionPayload(menu.containerId, Action.NETWORK_USE, network));
    }

    public void networkColor(UUID network, int color) {
        send(new TabletActionPayload(menu.containerId, Action.NETWORK_COLOR, Optional.of(network),
                Optional.empty(), "", color));
    }

    public void groupPause(UUID group, boolean paused) {
        send(new TabletActionPayload(menu.containerId, Action.GROUP_PAUSE, Optional.of(group),
                Optional.empty(), "", paused ? 0 : 1));
    }

    /** Põe a rede no grupo (o servidor a tira dos outros) ou, com {@code group} nulo, a tira de todos em que está. */
    public void groupMove(UUID network, @Nullable UUID group, List<UUID> currentGroups) {
        if (group != null) {
            send(new TabletActionPayload(menu.containerId, Action.GROUP_ADD_NETWORK, Optional.of(group),
                    Optional.of(network), "", 0));
            return;
        }
        for (UUID current : currentGroups) {
            send(new TabletActionPayload(menu.containerId, Action.GROUP_REMOVE_NETWORK, Optional.of(current),
                    Optional.of(network), "", 0));
        }
    }

    /** O ícone do grupo; {@code null} tira (volta à primeira máquina). */
    public void groupIcon(UUID group, @Nullable ResourceLocation icon) {
        send(new TabletActionPayload(menu.containerId, Action.GROUP_ICON, Optional.of(group), Optional.empty(),
                icon == null ? "" : icon.toString(), 0));
    }

    /** Convida ({@code add}) ou tira um jogador da rede: pelo id da sugestão ou, sem id, pelo nome digitado. */
    public void member(UUID network, @Nullable UUID player, String text, boolean add) {
        send(new TabletActionPayload(menu.containerId, add ? Action.NETWORK_MEMBER_ADD : Action.NETWORK_MEMBER_REMOVE,
                Optional.of(network), Optional.ofNullable(player), text, 0));
    }

    /** Adiciona ou tira um time do FTB Teams da rede: pelo id da sugestão ou, sem id, pelo nome digitado. */
    public void team(UUID network, @Nullable UUID team, String text, boolean add) {
        send(new TabletActionPayload(menu.containerId, add ? Action.NETWORK_TEAM_ADD : Action.NETWORK_TEAM_REMOVE,
                Optional.of(network), Optional.ofNullable(team), text, 0));
    }

    /** Pede as sugestões do campo de convidado ou de time para o texto digitado. */
    public void suggest(NetworkSuggestPayload.Kind kind, String text, UUID network) {
        send(new NetworkSuggestPayload(menu.containerId, kind, text, network));
    }

    /** Remover a rede ou o grupo (já confirmado). */
    public void remove(Action action, UUID id) {
        send(new TabletActionPayload(menu.containerId, action, id));
    }

    public void create(Action action, String name) {
        send(new TabletActionPayload(menu.containerId, action, Optional.empty(), Optional.empty(), name, 0));
    }

    public void rename(Action action, UUID id, String name) {
        send(new TabletActionPayload(menu.containerId, action, Optional.of(id), Optional.empty(), name, 0));
    }
}
