package io.github.matheusanbs.wirelessautomate.block;

import io.github.matheusanbs.wirelessautomate.filter.Filter;
import io.github.matheusanbs.wirelessautomate.filter.FilterSet;
import io.github.matheusanbs.wirelessautomate.item.FilterCardItem;
import io.github.matheusanbs.wirelessautomate.network.NetworkManager;
import io.github.matheusanbs.wirelessautomate.network.ResourceType;
import io.github.matheusanbs.wirelessautomate.range.MachineGroup;
import io.github.matheusanbs.wirelessautomate.range.RemoteMachine;
import io.github.matheusanbs.wirelessautomate.range.RemoteMachines;
import io.github.matheusanbs.wirelessautomate.range.RemoteSide;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.minecraft.world.item.ItemStack;

/**
 * O filtro de uma face de máquina de um {@link RouterBlockEntity}: o embutido da configuração que vale e os
 * Cartões de Filtro do grupo naquele lado (os cartões ficam só no grupo, também os da presa).
 */
final class RouterCards {
    private static final int CARD_SLOTS = RouterBlockEntity.CARD_SLOTS;
    /** Máscara ({@link NetworkManager#typeBit}) dos tipos com cartões. */
    static final int CARD_TYPES_MASK = cardTypesMask();

    private RouterCards() {
    }

    private static int cardTypesMask() {
        int mask = 0;
        for (ResourceType type : Arrays.stream(ResourceType.values()).filter(ResourceType::cards).toList()) {
            mask |= NetworkManager.typeBit(type);
        }
        return mask;
    }

    /** Ver {@link RouterBlockEntity#filterSet(RemoteMachine, ResourceType, RemoteSide)}. */
    static FilterSet remoteFilterSet(RemoteMachines remotes, RemoteMachine machine, ResourceType type, RemoteSide side) {
        return groupFilterSet(remotes.groupOf(machine), remotes.effective(machine).face(type, side).filter(), type,
                side);
    }

    /** O filtro {@code embedded} e os dos cartões válidos do grupo no lado, na ordem dos slots; sem cartões não aloca. */
    static FilterSet groupFilterSet(MachineGroup group, Filter embedded, ResourceType type, RemoteSide side) {
        if (MachineGroup.cardIndex(type) < 0) {
            return embedded.asSet();
        }
        List<Filter> fromCards = null;
        for (int slot = 0; slot < CARD_SLOTS; slot++) {
            ItemStack stack = group.card(type, side, slot);
            if (RouterBlockEntity.acceptsCard(type, stack)) {
                if (fromCards == null) {
                    fromCards = new ArrayList<>(CARD_SLOTS);
                }
                fromCards.add(FilterCardItem.contents(stack).filter());
            }
        }
        return fromCards == null ? embedded.asSet() : FilterSet.of(embedded, fromCards);
    }
}
