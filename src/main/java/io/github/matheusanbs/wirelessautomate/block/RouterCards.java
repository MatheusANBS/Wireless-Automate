package io.github.matheusanbs.wirelessautomate.block;

import io.github.matheusanbs.wirelessautomate.filter.Filter;
import io.github.matheusanbs.wirelessautomate.filter.FilterSet;
import io.github.matheusanbs.wirelessautomate.item.FilterCardItem;
import io.github.matheusanbs.wirelessautomate.network.NetworkManager;
import io.github.matheusanbs.wirelessautomate.network.RelativeSide;
import io.github.matheusanbs.wirelessautomate.network.ResourceType;
import io.github.matheusanbs.wirelessautomate.range.MachineGroup;
import io.github.matheusanbs.wirelessautomate.range.RemoteMachine;
import io.github.matheusanbs.wirelessautomate.range.RemoteMachines;
import io.github.matheusanbs.wirelessautomate.range.RemoteSide;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;

/**
 * Os slots de Cartão de Filtro da máquina presa de um {@link RouterBlockEntity}: {@link RouterBlockEntity#CARD_SLOTS}
 * por face, para cada tipo com cartões, guardados por lado relativo como a configuração; o filtro de uma face
 * (embutido e cartões) e o NBT. Não avisa ninguém: quem muda é o roteador.
 */
final class RouterCards {
    private static final RelativeSide[] SIDES = RelativeSide.values();
    private static final int CARD_SLOTS = RouterBlockEntity.CARD_SLOTS;
    /** Tipos com slots de cartão, na ordem de {@link #cards}. */
    private static final ResourceType[] CARD_TYPES = Arrays.stream(ResourceType.values()).filter(ResourceType::cards)
            .toArray(ResourceType[]::new);
    /** Máscara ({@link NetworkManager#typeBit}) dos tipos com cartões. */
    static final int CARD_TYPES_MASK = cardTypesMask();

    /** [tipo de {@link #CARD_TYPES}][lado relativo][slot], nunca nulos. */
    private final ItemStack[][][] cards = new ItemStack[CARD_TYPES.length][SIDES.length][CARD_SLOTS];

    RouterCards() {
        clear();
    }

    private static int cardTypesMask() {
        int mask = 0;
        for (ResourceType type : CARD_TYPES) {
            mask |= NetworkManager.typeBit(type);
        }
        return mask;
    }

    private static int cardIndex(ResourceType type) {
        for (int i = 0; i < CARD_TYPES.length; i++) {
            if (CARD_TYPES[i] == type) {
                return i;
            }
        }
        return -1;
    }

    /** O cartão no slot; vazio se não houver ou se o tipo não tem slots. */
    ItemStack get(ResourceType type, RelativeSide side, int slot) {
        int index = cardIndex(type);
        if (index < 0 || slot < 0 || slot >= CARD_SLOTS) {
            return ItemStack.EMPTY;
        }
        return cards[index][side.ordinal()][slot];
    }

    /** Põe {@code stack} no slot. Devolve se o slot existe (e a pilha foi guardada). */
    boolean set(ResourceType type, RelativeSide side, int slot, ItemStack stack) {
        int index = cardIndex(type);
        if (index < 0 || slot < 0 || slot >= CARD_SLOTS) {
            return false;
        }
        cards[index][side.ordinal()][slot] = stack;
        return true;
    }

    /** Cartões nos slots do lado para o tipo. */
    int count(ResourceType type, RelativeSide side) {
        int index = cardIndex(type);
        if (index < 0) {
            return 0;
        }
        int count = 0;
        for (ItemStack stack : cards[index][side.ordinal()]) {
            if (!stack.isEmpty()) {
                count++;
            }
        }
        return count;
    }

    /** O filtro embutido do lado e os dos cartões válidos, na ordem dos slots; sem cartões não aloca. */
    FilterSet filterSet(ResourceType type, RelativeSide side, Filter embedded) {
        int index = cardIndex(type);
        if (index < 0) {
            return embedded.asSet();
        }
        List<Filter> fromCards = null;
        for (ItemStack stack : cards[index][side.ordinal()]) {
            if (RouterBlockEntity.acceptsCard(type, stack)) {
                if (fromCards == null) {
                    fromCards = new ArrayList<>(CARD_SLOTS);
                }
                fromCards.add(FilterCardItem.contents(stack).filter());
            }
        }
        return fromCards == null ? embedded.asSet() : FilterSet.of(embedded, fromCards);
    }

    /** Ver {@link RouterBlockEntity#filterSet(RemoteMachine, ResourceType, RemoteSide)}. */
    static FilterSet remoteFilterSet(RemoteMachines remotes, RemoteMachine machine, ResourceType type, RemoteSide side) {
        Filter embedded = remotes.effective(machine).face(type, side).filter();
        if (MachineGroup.cardIndex(type) < 0) {
            return embedded.asSet();
        }
        MachineGroup group = remotes.groupOf(machine);
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

    /** O giro: os cartões do lado relativo {@code i} vão para {@code target[i]}, em todos os tipos. */
    void rotate(int[] target) {
        for (ItemStack[][] byType : cards) {
            ItemStack[][] old = byType.clone();
            for (int i = 0; i < old.length; i++) {
                byType[target[i]] = old[i];
            }
        }
    }

    /** Tira todos os cartões da presa, deixando os slots vazios, e os junta em {@code removed}. */
    void removeAll(NonNullList<ItemStack> removed) {
        for (ItemStack[][] byType : cards) {
            for (ItemStack[] bySide : byType) {
                for (int i = 0; i < bySide.length; i++) {
                    if (!bySide[i].isEmpty()) {
                        removed.add(bySide[i]);
                        bySide[i] = ItemStack.EMPTY;
                    }
                }
            }
        }
    }

    void clear() {
        for (ItemStack[][] byType : cards) {
            for (ItemStack[] bySide : byType) {
                Arrays.fill(bySide, ItemStack.EMPTY);
            }
        }
    }

    /** Os cartões por tipo e lado, com o slot em cada pilha (chave {@code cards}); vazio sem cartões. */
    CompoundTag save(HolderLookup.Provider registries) {
        CompoundTag cardsTag = new CompoundTag();
        for (int t = 0; t < CARD_TYPES.length; t++) {
            CompoundTag typeTag = new CompoundTag();
            for (RelativeSide side : SIDES) {
                ListTag list = new ListTag();
                ItemStack[] slots = cards[t][side.ordinal()];
                for (int slot = 0; slot < slots.length; slot++) {
                    if (!slots[slot].isEmpty()) {
                        CompoundTag slotTag = new CompoundTag();
                        slotTag.putByte("slot", (byte) slot);
                        list.add(slots[slot].save(registries, slotTag));
                    }
                }
                if (!list.isEmpty()) {
                    typeTag.put(side.key(), list);
                }
            }
            if (!typeTag.isEmpty()) {
                cardsTag.put(CARD_TYPES[t].key(), typeTag);
            }
        }
        return cardsTag;
    }

    /** Esvazia os slots e lê os cartões de {@code cardsTag}. */
    void load(CompoundTag cardsTag, HolderLookup.Provider registries) {
        clear();
        for (int t = 0; t < CARD_TYPES.length; t++) {
            CompoundTag typeTag = cardsTag.getCompound(CARD_TYPES[t].key());
            for (RelativeSide side : SIDES) {
                ListTag list = typeTag.getList(side.key(), Tag.TAG_COMPOUND);
                for (int i = 0; i < list.size(); i++) {
                    CompoundTag slotTag = list.getCompound(i);
                    int slot = slotTag.getByte("slot");
                    ItemStack[] slots = cards[t][side.ordinal()];
                    if (slot >= 0 && slot < CARD_SLOTS) {
                        // Item de mod removido: a pilha não lê e o slot fica vazio.
                        ItemStack.parse(registries, slotTag).ifPresent(stack -> slots[slot] = stack);
                    }
                }
            }
        }
    }
}
