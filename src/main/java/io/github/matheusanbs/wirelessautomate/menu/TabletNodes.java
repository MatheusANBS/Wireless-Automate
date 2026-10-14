package io.github.matheusanbs.wirelessautomate.menu;

import io.github.matheusanbs.wirelessautomate.block.RouterBlockEntity;
import io.github.matheusanbs.wirelessautomate.menu.TabletSnapshot.NodeStatus;
import io.github.matheusanbs.wirelessautomate.network.LoadedTypes;
import io.github.matheusanbs.wirelessautomate.network.NetworkSavedData;
import io.github.matheusanbs.wirelessautomate.network.NodeIndex;
import io.github.matheusanbs.wirelessautomate.network.NodeIndex.NodeKey;
import io.github.matheusanbs.wirelessautomate.network.NodeProbe;
import io.github.matheusanbs.wirelessautomate.network.ResourceType;
import io.github.matheusanbs.wirelessautomate.network.WaNetwork;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

/**
 * As regras do Tablet sobre um nó do índice: quem o vê, o estado que a Lista mostra, as redes das faces e o
 * roteador carregado (sem carregar chunk).
 */
final class TabletNodes {
    static final ResourceType[] TRANSFER_TYPES = ResourceType.values();

    private TabletNodes() {
    }

    /** O jogador vê o nó: operador, quem o colocou, ou alguma face numa rede dele. */
    static boolean visible(NodeIndex.Entry entry, NetworkSavedData data, boolean operator, UUID me) {
        if (operator || me.equals(entry.placedBy())) {
            return true;
        }
        for (ResourceType type : LoadedTypes.LIST) {
            for (UUID id : entry.networks(type)) {
                WaNetwork network = data.network(id);
                if (network != null && network.owner().equals(me)) {
                    return true;
                }
            }
        }
        return false;
    }

    static NodeStatus status(NodeIndex.Entry entry, boolean loaded, int full, boolean moved, NetworkSavedData data) {
        if (!loaded) {
            return NodeStatus.UNLOADED;
        }
        boolean anyNetwork = false;
        boolean anyRunning = false;
        for (ResourceType type : LoadedTypes.LIST) {
            for (UUID id : entry.networks(type)) {
                if (data.network(id) != null) {
                    anyNetwork = true;
                    anyRunning |= !data.isPaused(id);
                }
            }
        }
        if (!anyNetwork) {
            return NodeStatus.NO_NETWORK;
        }
        if (!anyRunning) {
            return NodeStatus.PAUSED;
        }
        if (full > 0) {
            return NodeStatus.FULL;
        }
        return moved ? NodeStatus.ACTIVE : NodeStatus.IDLE;
    }

    /** Alguma face do nó está numa rede que existe. */
    static boolean hasExistingNetwork(NodeIndex.Entry entry, NetworkSavedData data) {
        for (ResourceType type : LoadedTypes.LIST) {
            for (UUID id : entry.networks(type)) {
                if (data.network(id) != null) {
                    return true;
                }
            }
        }
        return false;
    }

    /** As redes das faces do nó nos tipos carregados, sem repetir. */
    static Set<UUID> distinctNetworks(NodeIndex.Entry entry) {
        Set<UUID> ids = null;
        for (ResourceType type : LoadedTypes.LIST) {
            for (UUID id : entry.networks(type)) {
                if (ids == null) {
                    ids = new HashSet<>();
                }
                ids.add(id);
            }
        }
        return ids == null ? Set.of() : ids;
    }

    /** Destinos cheios do nó, nos tipos que o Tablet mostra. */
    static int fullDestinations(RouterBlockEntity router, long now) {
        int full = 0;
        for (ResourceType type : TRANSFER_TYPES) {
            full += NodeProbe.fullDestinations(router, type, now);
        }
        return full;
    }

    /**
     * O roteador carregado da entrada. Se o chunk está carregado e não há roteador ali (região
     * apagada, bloco trocado sem {@code onRemove}), a entrada velha sai do índice.
     */
    static @Nullable RouterBlockEntity loadedRouter(MinecraftServer server, NodeIndex index, NodeIndex.Entry entry) {
        RouterBlockEntity router = entry.router();
        if (router != null) {
            return router;
        }
        ServerLevel level = server.getLevel(entry.key().dimension());
        if (level != null && level.isLoaded(entry.key().pos())) {
            if (level.getBlockEntity(entry.key().pos()) instanceof RouterBlockEntity found) {
                NodeIndex.track(found);
                return found;
            }
            index.remove(entry.key());
        }
        return null;
    }

    /** O roteador do nó, se o jogador o vê, está carregado e ao alcance; senão {@code null}. */
    static @Nullable RouterBlockEntity reachableRouter(ServerPlayer viewer, NodeKey key) {
        MinecraftServer server = viewer.server;
        NodeIndex index = NodeIndex.get(server);
        NodeIndex.Entry entry = index.entry(key);
        if (entry == null || !visible(entry, NetworkSavedData.get(server), viewer.hasPermissions(2), viewer.getUUID())) {
            return null;
        }
        RouterBlockEntity router = loadedRouter(server, index, entry);
        return router != null && TabletMenu.inReach(viewer, router) ? router : null;
    }
}
