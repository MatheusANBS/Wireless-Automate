package io.github.matheusanbs.wirelessautomate.menu;

import io.github.matheusanbs.wirelessautomate.block.RouterBlockEntity;
import io.github.matheusanbs.wirelessautomate.command.WaCommand;
import io.github.matheusanbs.wirelessautomate.network.KnownPlayers;
import io.github.matheusanbs.wirelessautomate.network.LoadedTypes;
import io.github.matheusanbs.wirelessautomate.network.NetworkSavedData;
import io.github.matheusanbs.wirelessautomate.network.NodeIndex;
import io.github.matheusanbs.wirelessautomate.network.NodeIndex.NodeKey;
import io.github.matheusanbs.wirelessautomate.network.ResourceType;
import io.github.matheusanbs.wirelessautomate.network.TeamAccess;
import io.github.matheusanbs.wirelessautomate.network.WaGroup;
import io.github.matheusanbs.wirelessautomate.network.WaNetwork;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.StringUtil;
import org.jetbrains.annotations.Nullable;

/**
 * As ações do Tablet sobre redes, grupos e nós (servidor): criar, renomear, pintar, publicar, apagar e usar
 * redes; criar, renomear, apagar, montar e pausar grupos; mover nós de rede e abrir a tela de um nó. Cada uma
 * é validada aqui e deixa o aviso do cabeçalho ({@link #notice()}), que pede uma remontagem ao menu.
 */
final class TabletMenuActions {
    private final TabletMenu menu;
    private final ServerPlayer viewer;
    private Component notice = Component.empty();
    private int noticeId;

    TabletMenuActions(TabletMenu menu, ServerPlayer viewer) {
        this.menu = menu;
        this.viewer = viewer;
    }

    Component notice() {
        return notice;
    }

    int noticeId() {
        return noticeId;
    }

    private void notice(String key, Object... args) {
        notice = Component.translatable("gui.wirelessautomate.tablet.notice." + key, args);
        noticeId++;
        menu.requestRebuild();
    }

    /** Ver {@link TabletMenu#moveNodes}. Mover a rede organiza a base: vale à distância, sem o {@code inReach} do colar. */
    int moveNodes(List<NodeKey> keys, TabletMarks marks, Optional<ResourceType> type, Optional<UUID> network,
            @Nullable TabletMachines view) {
        if (keys.isEmpty() && marks.isEmpty() || keys.size() > TabletMenu.MAX_MOVE
                || type.filter(t -> !LoadedTypes.contains(t)).isPresent()) {
            return -1;
        }
        if (!TabletPlayers.takeBulk(viewer.getUUID(), viewer.server.getTickCount())) {
            notice("busy");
            return -1;
        }
        MinecraftServer server = viewer.server;
        NetworkSavedData data = NetworkSavedData.get(server);
        WaNetwork target = network.map(data::network).orElse(null);
        if (network.isPresent() && (target == null || !target.canUse(viewer))) {
            notice("move.refused");
            return -1;
        }
        NodeIndex index = NodeIndex.get(server);
        boolean operator = viewer.hasPermissions(2);
        int moved = 0;
        int unloaded = 0;
        int kept = 0;
        java.util.function.Predicate<UUID> canLeave = from -> {
            WaNetwork current = data.network(from);
            return current == null || current.canEdit(viewer);
        };
        Component name = target == null ? Component.translatable("gui.wirelessautomate.tablet.network.none")
                : target.displayName();
        if (!marks.isEmpty()) {
            MarkedMove.Result result = MarkedMove.move(server, MarkedTargets.collect(viewer, keys, marks, view),
                    type.map(List::of).orElseGet(() -> LoadedTypes.LIST), target == null ? null : target.id(), canLeave);
            Component parts = MarkedNotice.parts(result.routers(), result.groups(), result.machines());
            Component what = parts == null ? Component.literal("0") : parts;
            String suffix = result.kept() > 0 ? ".kept" : "";
            if (result.unloaded() > 0) {
                notice("moved.unloaded" + suffix, what, name, result.unloaded(), result.kept());
            } else {
                notice("moved" + suffix, what, name, result.kept());
            }
            return result.routers() + result.groups() + result.machines();
        }
        for (NodeKey key : new HashSet<>(keys)) {
            NodeIndex.Entry entry = index.entry(key);
            if (entry == null || !TabletNodes.visible(entry, data, operator, viewer.getUUID())) {
                continue;
            }
            RouterBlockEntity router = TabletNodes.loadedRouter(server, index, entry);
            if (router == null) {
                unloaded++;
                continue;
            }
            UUID id = target == null ? null : target.id();
            kept += router.setNetworkAllFaces(type.map(List::of).orElseGet(() -> LoadedTypes.LIST), id, canLeave);
            moved++;
        }
        String suffix = kept > 0 ? ".kept" : "";
        if (unloaded > 0) {
            notice("moved.unloaded" + suffix, moved, name, unloaded, kept);
        } else {
            notice("moved" + suffix, moved, name, kept);
        }
        return moved;
    }

    /** Ver {@link TabletMenu#openNode}. */
    boolean openNode(NodeKey key) {
        MinecraftServer server = viewer.server;
        NodeIndex index = NodeIndex.get(server);
        NodeIndex.Entry entry = index.entry(key);
        if (entry == null || !TabletNodes.visible(entry, NetworkSavedData.get(server), viewer.hasPermissions(2),
                viewer.getUUID())) {
            return false;
        }
        RouterBlockEntity router = TabletNodes.loadedRouter(server, index, entry);
        if (router == null) {
            notice("open.unloaded");
            return false;
        }
        if (!TabletMenu.inReach(viewer, router)) {
            notice("open.range");
            return false;
        }
        RemoteRouterMenu.open(viewer, router);
        return true;
    }

    /** Ver {@link TabletMenu#apply}; o menu pede a remontagem depois. */
    boolean apply(TabletMenu.Action action, Optional<UUID> target, Optional<UUID> other, String text, int value) {
        NetworkSavedData data = NetworkSavedData.get(viewer.server);
        return switch (action) {
            case NETWORK_CREATE, NETWORK_RENAME, NETWORK_COLOR, NETWORK_PUBLIC, NETWORK_REMOVE, NETWORK_USE ->
                    applyNetwork(action, data, target, text, value);
            case NETWORK_MEMBER_ADD, NETWORK_MEMBER_REMOVE, NETWORK_TEAM_ADD, NETWORK_TEAM_REMOVE ->
                    applyAccess(action, data, target, other, text);
            case GROUP_CREATE, GROUP_RENAME, GROUP_REMOVE, GROUP_ADD_NETWORK, GROUP_REMOVE_NETWORK, GROUP_PAUSE ->
                    applyGroup(action, data, target, other, text, value);
        };
    }

    private boolean applyNetwork(TabletMenu.Action action, NetworkSavedData data, Optional<UUID> target, String text,
            int value) {
        UUID me = viewer.getUUID();
        return switch (action) {
            case NETWORK_CREATE -> {
                String name = cleanName(text);
                if (name == null || data.byName(me, name) != null) {
                    yield refuse("name");
                }
                if (data.networkLimitReached(viewer)) {
                    yield refuse("limit");
                }
                data.create(me, name);
                notice("network.created", name);
                yield true;
            }
            case NETWORK_RENAME -> {
                WaNetwork network = managedNetwork(data, target);
                String name = cleanName(text);
                if (network == null || name == null) {
                    yield refuse(network == null ? "permission" : "name");
                }
                WaNetwork same = data.byName(network.owner(), name);
                if (same != null && !same.id().equals(network.id())) {
                    yield refuse("name");
                }
                yield data.update(network.withName(name));
            }
            case NETWORK_COLOR -> {
                WaNetwork network = managedNetwork(data, target);
                yield network == null ? refuse("permission") : data.update(network.withColor(value));
            }
            case NETWORK_PUBLIC -> {
                WaNetwork network = managedNetwork(data, target);
                if (network == null || (value != 0 && value != 1)) {
                    yield refuse("permission");
                }
                yield data.update(network.withPublic(value == 1));
            }
            case NETWORK_REMOVE -> {
                WaNetwork network = managedNetwork(data, target);
                if (network == null) {
                    yield refuse("permission");
                }
                data.remove(network.id());
                notice("network.removed", network.name());
                yield true;
            }
            case NETWORK_USE -> {
                WaNetwork network = target.map(data::network).orElse(null);
                if (network == null || !network.canUse(viewer)) {
                    yield refuse("permission");
                }
                data.setActiveNetwork(me, network.id());
                notice("network.active", network.displayName());
                yield true;
            }
            default -> throw new IllegalArgumentException(action.name());
        };
    }

    /** Convidar e tirar jogadores e times de uma rede: só quem gerencia a rede. */
    private boolean applyAccess(TabletMenu.Action action, NetworkSavedData data, Optional<UUID> target,
            Optional<UUID> other, String text) {
        WaNetwork network = managedNetwork(data, target);
        if (network == null) {
            return refuse("permission");
        }
        MinecraftServer server = viewer.server;
        return switch (action) {
            case NETWORK_MEMBER_ADD -> {
                UUID player = TabletAccess.resolvePlayer(server, other, text);
                if (player == null) {
                    yield refuse("known");
                }
                if (player.equals(network.owner())) {
                    yield refuse("owner");
                }
                if (network.members().contains(player)) {
                    yield refuse("exists");
                }
                if (data.memberLimitReached(viewer, network)) {
                    yield refuse("limit");
                }
                notice("access.member.added", KnownPlayers.name(server, player), network.displayName());
                yield data.update(network.withMember(player));
            }
            case NETWORK_MEMBER_REMOVE -> {
                UUID player = other.orElse(null);
                if (player == null || !network.members().contains(player)) {
                    yield refuse("known");
                }
                notice("access.member.removed", KnownPlayers.name(server, player), network.displayName());
                yield data.update(network.withoutMember(player));
            }
            case NETWORK_TEAM_ADD -> {
                UUID team = TabletAccess.resolveTeam(other, text);
                if (team == null) {
                    yield refuse("team");
                }
                if (network.teams().contains(team)) {
                    yield refuse("exists");
                }
                if (data.teamLimitReached(viewer, network)) {
                    yield refuse("limit");
                }
                TeamAccess.TeamInfo info = TeamAccess.info(team);
                notice("access.team.added", info == null ? "" : info.name(), network.displayName());
                yield data.update(network.withTeam(team));
            }
            case NETWORK_TEAM_REMOVE -> {
                UUID team = other.orElse(null);
                if (team == null || !network.teams().contains(team)) {
                    yield refuse("team");
                }
                TeamAccess.TeamInfo info = TeamAccess.info(team);
                notice("access.team.removed", info == null ? "" : info.name(), network.displayName());
                yield data.update(network.withoutTeam(team));
            }
            default -> throw new IllegalArgumentException(action.name());
        };
    }

    private boolean applyGroup(TabletMenu.Action action, NetworkSavedData data, Optional<UUID> target,
            Optional<UUID> other, String text, int value) {
        UUID me = viewer.getUUID();
        return switch (action) {
            case GROUP_CREATE -> {
                String name = cleanName(text);
                if (name == null) {
                    yield refuse("name");
                }
                if (data.groupLimitReached(viewer)) {
                    yield refuse("limit");
                }
                data.createGroup(me, name);
                notice("group.created", name);
                yield true;
            }
            case GROUP_RENAME -> {
                WaGroup group = managedGroup(data, target);
                String name = cleanName(text);
                if (group == null || name == null) {
                    yield refuse(group == null ? "permission" : "name");
                }
                yield data.updateGroup(group.withName(name));
            }
            case GROUP_REMOVE -> {
                WaGroup group = managedGroup(data, target);
                if (group == null) {
                    yield refuse("permission");
                }
                data.removeGroup(group.id());
                notice("group.removed", group.name());
                yield true;
            }
            case GROUP_ADD_NETWORK, GROUP_REMOVE_NETWORK -> {
                WaGroup group = managedGroup(data, target);
                // Só redes que o jogador gerencia: pausar o grupo não pode parar a rede de outro.
                WaNetwork network = managedNetwork(data, other);
                if (group == null || network == null) {
                    yield refuse("permission");
                }
                yield data.updateGroup(action == TabletMenu.Action.GROUP_ADD_NETWORK
                        ? group.withNetwork(network.id()) : group.withoutNetwork(network.id()));
            }
            case GROUP_PAUSE -> {
                WaGroup group = managedGroup(data, target);
                if (group == null || (value != 0 && value != 1)) {
                    yield refuse("permission");
                }
                boolean changed = data.updateGroup(group.withPaused(value == 1));
                notice(value == 1 ? "group.paused" : "group.resumed", group.name());
                yield changed;
            }
            default -> throw new IllegalArgumentException(action.name());
        };
    }

    private boolean refuse(String reason) {
        notice("refused." + reason);
        return false;
    }

    private @Nullable WaNetwork managedNetwork(NetworkSavedData data, Optional<UUID> id) {
        WaNetwork network = id.map(data::network).orElse(null);
        return network != null && network.canManage(viewer) ? network : null;
    }

    private @Nullable WaGroup managedGroup(NetworkSavedData data, Optional<UUID> id) {
        WaGroup group = id.map(data::group).orElse(null);
        return group != null && group.canManage(viewer) ? group : null;
    }

    /** Nome sem caracteres inválidos e sem espaços nas pontas, de 1 a {@link WaCommand#MAX_NAME_LENGTH}; senão {@code null}. */
    static @Nullable String cleanName(String text) {
        String name = StringUtil.filterText(text).strip();
        return name.isEmpty() || name.length() > WaCommand.MAX_NAME_LENGTH ? null : name;
    }
}
