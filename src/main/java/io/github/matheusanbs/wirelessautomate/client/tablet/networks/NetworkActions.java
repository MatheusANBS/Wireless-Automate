package io.github.matheusanbs.wirelessautomate.client.tablet.networks;

import static io.github.matheusanbs.wirelessautomate.client.tablet.networks.NetworksText.tr;

import io.github.matheusanbs.wirelessautomate.client.access.AccessHost;
import io.github.matheusanbs.wirelessautomate.client.tablet.TabletActions;
import io.github.matheusanbs.wirelessautomate.menu.SuggestionsMenu;
import io.github.matheusanbs.wirelessautomate.menu.TabletMenu;
import io.github.matheusanbs.wirelessautomate.menu.TabletMenu.Action;
import io.github.matheusanbs.wirelessautomate.menu.TabletSnapshot;
import io.github.matheusanbs.wirelessautomate.menu.TabletSnapshot.AccessView;
import io.github.matheusanbs.wirelessautomate.menu.TabletSnapshot.GroupView;
import io.github.matheusanbs.wirelessautomate.menu.TabletSnapshot.NetworkView;
import io.github.matheusanbs.wirelessautomate.packet.NetworkSuggestPayload;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import org.jetbrains.annotations.Nullable;

/**
 * O que a aba Redes manda ao servidor, pelo {@link TabletActions}, sobre a rede ou o grupo escolhido: criar com nome
 * livre (e lembrar o que já existia, para escolher o novo quando chegar), renomear, acesso, Usar, pausar, apagar e mover
 * a rede de grupo. É a parte da anfitriã ({@link NetworksPanel}) que fala com o servidor; as peças da tela só a chamam.
 */
final class NetworkActions {
    private final TabletActions actions;
    private final Supplier<TabletSnapshot> snapshot;
    private final NetworksModel model;
    private final Supplier<@Nullable UUID> network;
    private final Supplier<@Nullable UUID> group;
    private final AccessHost accessHost;
    /** O que foi pedido e ainda não chegou: um grupo ({@code true}) ou uma rede; os ids que já existiam. */
    private @Nullable Boolean awaitingGroup;
    private final Set<UUID> known = new HashSet<>();

    NetworkActions(TabletActions actions, TabletMenu menu, Supplier<TabletSnapshot> snapshot, NetworksModel model,
            Supplier<@Nullable UUID> network, Supplier<@Nullable UUID> group) {
        this.actions = actions;
        this.snapshot = snapshot;
        this.model = model;
        this.network = network;
        this.group = group;
        this.accessHost = new Host(menu, actions, this::networkView);
    }

    private @Nullable NetworkView networkView() {
        UUID id = network.get();
        return id == null ? null : model.network(id);
    }

    private @Nullable GroupView groupView() {
        UUID id = group.get();
        return id == null ? null : model.group(id);
    }

    /** As ações do {@code AccessPanel}, para a rede escolhida. */
    AccessHost accessHost() {
        return accessHost;
    }

    // ------------------------------------------------------------------ criar

    void newNetwork() {
        List<String> taken = new ArrayList<>();
        for (NetworkView n : snapshot.get().networks()) {
            if (n.owned()) {
                taken.add(n.name());
            }
        }
        actions.create(Action.NETWORK_CREATE, DefaultNames.next(tr("nets.default_network").getString(), taken));
        await(false);
    }

    void newGroup() {
        List<String> taken = new ArrayList<>();
        for (GroupView g : snapshot.get().groups()) {
            if (g.manageable()) {
                taken.add(g.name());
            }
        }
        actions.create(Action.GROUP_CREATE, DefaultNames.next(tr("nets.default_group").getString(), taken));
        await(true);
    }

    private void await(boolean isGroup) {
        awaitingGroup = isGroup;
        known.clear();
        if (isGroup) {
            snapshot.get().groups().forEach(g -> known.add(g.id()));
        } else {
            snapshot.get().networks().forEach(n -> known.add(n.id()));
        }
    }

    /** Se o pedido pendente era um grupo (vale antes de {@link #takeCreated}). */
    boolean awaitingGroup() {
        return Boolean.TRUE.equals(awaitingGroup);
    }

    /** O que acabou de ser criado, se chegou no snapshot do modelo; a espera termina aí. */
    @Nullable UUID takeCreated() {
        if (awaitingGroup == null) {
            return null;
        }
        UUID created = model.created(awaitingGroup, known);
        if (created != null) {
            awaitingGroup = null;
        }
        return created;
    }

    // ------------------------------------------------------------------ a rede escolhida

    /** O nome digitado no título vai para a rede escolhida, ou para o grupo; vazio ou igual, nada sai. */
    void rename(String text) {
        String name = text.strip();
        if (name.isEmpty()) {
            return;
        }
        NetworkView view = networkView();
        GroupView chosen = groupView();
        if (view != null && !name.equals(view.name())) {
            actions.rename(Action.NETWORK_RENAME, view.id(), name);
        } else if (view == null && chosen != null && !name.equals(chosen.name())) {
            actions.rename(Action.GROUP_RENAME, chosen.id(), name);
        }
    }

    /** A cor escolhida no seletor ({@code rgb}) vai para a rede {@code id}. */
    void networkColor(UUID id, int rgb) {
        actions.networkColor(id, rgb);
    }

    void setPublic(boolean value) {
        NetworkView view = networkView();
        if (view != null && view.isPublic() != value) {
            actions.networkPublic(view.id(), value);
        }
    }

    void useNetwork() {
        UUID id = network.get();
        if (id != null) {
            actions.networkUse(id);
        }
    }

    /** Pausa ou retoma o grupo em que a rede escolhida aparece. */
    void pauseNetworkGroup() {
        UUID id = network.get();
        GroupView in = id == null ? null : model.groupOf(id);
        if (in != null) {
            actions.groupPause(in.id(), in.paused());
        }
    }

    void removeNetwork() {
        UUID id = network.get();
        if (id != null) {
            actions.remove(Action.NETWORK_REMOVE, id);
        }
    }

    /** Tira um convidado ({@code team} falso) ou um time da rede escolhida. */
    void removeAccess(UUID id, boolean team) {
        UUID chosen = network.get();
        if (chosen == null) {
            return;
        }
        if (team) {
            actions.team(chosen, id, "", false);
        } else {
            actions.member(chosen, id, "", false);
        }
    }

    /** Põe a rede no grupo {@code to} (o servidor a tira dos outros) ou, com {@code null}, a tira de todos. */
    void moveNetwork(UUID id, @Nullable UUID to) {
        actions.groupMove(id, to, model.groupsWith(id));
    }

    // ------------------------------------------------------------------ o grupo escolhido

    void pauseGroup() {
        GroupView chosen = groupView();
        if (chosen != null) {
            actions.groupPause(chosen.id(), chosen.paused());
        }
    }

    void removeGroup() {
        GroupView chosen = groupView();
        if (chosen != null) {
            actions.remove(Action.GROUP_REMOVE, chosen.id());
        }
    }

    /** As ações do acesso vão para a rede escolhida; sem rede escolhida, não há lista. */
    private record Host(TabletMenu tablet, TabletActions actions, Supplier<@Nullable NetworkView> network)
            implements AccessHost {
        @Override
        public @Nullable AccessView access() {
            NetworkView view = network.get();
            return view == null ? null : view.access();
        }

        @Override
        public SuggestionsMenu menu() {
            return tablet;
        }

        @Override
        public void member(@Nullable UUID id, String text, boolean add) {
            NetworkView view = network.get();
            if (view != null) {
                actions.member(view.id(), id, text, add);
            }
        }

        @Override
        public void team(@Nullable UUID id, String text, boolean add) {
            NetworkView view = network.get();
            if (view != null) {
                actions.team(view.id(), id, text, add);
            }
        }

        @Override
        public void suggest(NetworkSuggestPayload.Kind kind, String text) {
            NetworkView view = network.get();
            if (view != null) {
                actions.suggest(kind, text, view.id());
            }
        }
    }
}
