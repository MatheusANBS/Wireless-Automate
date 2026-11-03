package io.github.matheusanbs.wirelessautomate.client.tablet.networks;

import io.github.matheusanbs.wirelessautomate.menu.TabletSnapshot;
import io.github.matheusanbs.wirelessautomate.menu.TabletSnapshot.GroupView;
import io.github.matheusanbs.wirelessautomate.menu.TabletSnapshot.NetworkView;
import io.github.matheusanbs.wirelessautomate.registry.ModItems;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

/**
 * O que a aba Redes monta de cada snapshot, uma vez: as redes e os grupos pelo id, a árvore da coluna
 * ({@link NetworkTree}), o grupo em que cada rede aparece e o {@link ItemStack} do ícone de cada grupo (o escolhido,
 * senão a máquina de reserva, senão o Tablet).
 */
final class NetworksModel {
    private @Nullable TabletSnapshot snapshot;
    private final Map<UUID, NetworkView> networks = new HashMap<>();
    private final Map<UUID, GroupView> groups = new HashMap<>();
    private final Map<UUID, ItemStack> icons = new HashMap<>();
    private final Map<UUID, GroupView> groupOf = new HashMap<>();
    private List<NetworkTree.Row> rows = List.of();

    void build(TabletSnapshot s) {
        snapshot = s;
        networks.clear();
        groups.clear();
        groupOf.clear();
        Map<UUID, ItemStack> oldIcons = new HashMap<>(icons);
        icons.clear();
        List<UUID> ids = new ArrayList<>(s.networks().size());
        for (NetworkView n : s.networks()) {
            networks.put(n.id(), n);
            ids.add(n.id());
        }
        List<NetworkTree.GroupIn> in = new ArrayList<>(s.groups().size());
        for (GroupView g : s.groups()) {
            groups.put(g.id(), g);
            ItemStack old = oldIcons.get(g.id());
            Item item = iconItem(g);
            icons.put(g.id(), old != null && old.is(item) ? old : new ItemStack(item));
            in.add(new NetworkTree.GroupIn(g.id(), g.networks()));
        }
        rows = NetworkTree.build(in, ids);
        for (NetworkTree.Row row : rows) {
            if (row.kind() == NetworkTree.Kind.NETWORK && row.group() != null) {
                groupOf.put(row.id(), groups.get(row.group()));
            }
        }
    }

    /** O ícone do grupo: o item escolhido, senão o da primeira máquina das redes dele, senão o Tablet. */
    private static Item iconItem(GroupView g) {
        Item chosen = item(g.icon(), false);
        if (chosen != Items.AIR) {
            return chosen;
        }
        Item machine = item(g.machine(), true);
        return machine != Items.AIR ? machine : ModItems.NETWORK_TABLET.get();
    }

    private static Item item(Optional<ResourceLocation> id, boolean block) {
        if (id.isEmpty()) {
            return Items.AIR;
        }
        return block ? BuiltInRegistries.BLOCK.get(id.get()).asItem() : BuiltInRegistries.ITEM.get(id.get());
    }

    @Nullable TabletSnapshot snapshot() {
        return snapshot;
    }

    List<NetworkTree.Row> rows() {
        return rows;
    }

    Map<UUID, NetworkView> networks() {
        return networks;
    }

    Map<UUID, GroupView> groups() {
        return groups;
    }

    Map<UUID, ItemStack> icons() {
        return icons;
    }

    @Nullable NetworkView network(UUID id) {
        return networks.get(id);
    }

    @Nullable GroupView group(UUID id) {
        return groups.get(id);
    }

    /** O grupo em que a rede aparece na coluna, ou {@code null} ("Sem grupo"). */
    @Nullable GroupView groupOf(UUID network) {
        return groupOf.get(network);
    }

    ItemStack icon(UUID group) {
        return icons.getOrDefault(group, ItemStack.EMPTY);
    }

    /** Todos os grupos em que a rede está (num mundo antigo, mais de um). */
    List<UUID> groupsWith(UUID network) {
        List<UUID> out = new ArrayList<>();
        if (snapshot != null) {
            for (GroupView g : snapshot.groups()) {
                if (g.networks().contains(network)) {
                    out.add(g.id());
                }
            }
        }
        return out;
    }

    /** A primeira rede da coluna, ou {@code null} sem redes. */
    @Nullable UUID firstNetwork() {
        for (NetworkTree.Row row : rows) {
            if (row.kind() == NetworkTree.Kind.NETWORK) {
                return row.id();
            }
        }
        return null;
    }

    /** O grupo (ou a rede) que não estava em {@code known}, ou {@code null} se ainda não chegou. */
    @Nullable UUID created(boolean group, Set<UUID> known) {
        if (snapshot == null) {
            return null;
        }
        if (group) {
            for (GroupView g : snapshot.groups()) {
                if (!known.contains(g.id())) {
                    return g.id();
                }
            }
        } else {
            for (NetworkView n : snapshot.networks()) {
                if (!known.contains(n.id())) {
                    return n.id();
                }
            }
        }
        return null;
    }
}
