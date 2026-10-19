package io.github.matheusanbs.wirelessautomate.range;

import io.github.matheusanbs.wirelessautomate.block.RouterBlockEntity;
import io.github.matheusanbs.wirelessautomate.network.ResourceType;
import io.github.matheusanbs.wirelessautomate.packet.RenameRouterPayload;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Grupo de máquinas remotas do mesmo bloco: o perfil (a configuração das faces que vale para todas
 * as máquinas sem exceção) e os Cartões de Filtro, que ficam só aqui (decisão 9 da spec).
 *
 * <p>Os cartões seguem o layout dos {@code cards} do roteador, mas por {@link RemoteSide}:
 * {@code [tipo com cartão][lado remoto][slot]}, nunca nulos.
 */
public final class MachineGroup {
    private static final RemoteSide[] SIDES = RemoteSide.values();
    /** Tipos com slots de cartão, na ordem do roteador. */
    private static final ResourceType[] CARD_TYPES = Arrays.stream(ResourceType.values())
            .filter(ResourceType::cards).toArray(ResourceType[]::new);

    private final int id;
    private ResourceLocation block;
    /** Nome dado pelo jogador; vazio = a tela usa o nome do bloco. Nunca texto traduzido. */
    private String name;
    private final MachineConfig profile;
    private final ItemStack[][][] cards = new ItemStack[CARD_TYPES.length][SIDES.length][RouterBlockEntity.CARD_SLOTS];

    public MachineGroup(int id, ResourceLocation block, String name, MachineConfig profile) {
        this.id = id;
        this.block = block;
        this.name = name;
        this.profile = profile;
        for (ItemStack[][] byType : cards) {
            for (ItemStack[] bySide : byType) {
                Arrays.fill(bySide, ItemStack.EMPTY);
            }
        }
    }

    public int id() {
        return id;
    }

    /** Id do registro do bloco das máquinas do grupo. */
    public ResourceLocation block() {
        return block;
    }

    /** Passa o grupo para outro bloco (o Atualizar de um grupo inteiro trocado). Só pelo {@link RemoteMachines#regroup}. */
    void setBlock(ResourceLocation block) {
        this.block = block;
    }

    /** Nome do grupo; vazio = usar o nome do bloco. */
    public String name() {
        return name;
    }

    public void setName(String name) {
        this.name = name == null ? "" : RenameRouterPayload.clean(name);
    }

    /** O perfil do grupo. Mude pelos métodos por alvo do roteador, que avisam o motor. */
    public MachineConfig profile() {
        return profile;
    }

    /** Índice de {@code type} nos cartões, ou −1 se o tipo não tem slots de cartão. */
    public static int cardIndex(ResourceType type) {
        for (int i = 0; i < CARD_TYPES.length; i++) {
            if (CARD_TYPES[i] == type) {
                return i;
            }
        }
        return -1;
    }

    /** O cartão no slot; vazio se não houver ou se o tipo não tem slots. Não altere a pilha devolvida. */
    public ItemStack card(ResourceType type, RemoteSide side, int slot) {
        int index = cardIndex(type);
        if (index < 0 || slot < 0 || slot >= RouterBlockEntity.CARD_SLOTS) {
            return ItemStack.EMPTY;
        }
        return cards[index][side.ordinal()][slot];
    }

    /** Põe {@code stack} (guardada como está) no slot. Devolve {@code false} se o tipo ou o slot não vale. */
    public boolean setCard(ResourceType type, RemoteSide side, int slot, ItemStack stack) {
        int index = cardIndex(type);
        if (index < 0 || slot < 0 || slot >= RouterBlockEntity.CARD_SLOTS) {
            return false;
        }
        cards[index][side.ordinal()][slot] = stack;
        return true;
    }

    /** Tira todos os cartões do grupo (para soltar no mundo). */
    public List<ItemStack> removeAllCards() {
        List<ItemStack> removed = new ArrayList<>();
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
        return removed;
    }

    /** NBT com {@code id}, {@code block}, {@code name} (se não vazio), {@code profile} e {@code cards}. */
    public CompoundTag save(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("id", id);
        tag.putString("block", block.toString());
        if (!name.isEmpty()) {
            tag.putString("name", name);
        }
        CompoundTag profileTag = profile.save(registries);
        if (!profileTag.isEmpty()) {
            tag.put("profile", profileTag);
        }
        CompoundTag cardsTag = new CompoundTag();
        for (int t = 0; t < CARD_TYPES.length; t++) {
            CompoundTag typeTag = new CompoundTag();
            for (RemoteSide side : SIDES) {
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
        if (!cardsTag.isEmpty()) {
            tag.put("cards", cardsTag);
        }
        return tag;
    }

    /** Lê o formato de {@link #save}; {@code null} se o id do bloco não lê. */
    public static @Nullable MachineGroup load(CompoundTag tag, HolderLookup.Provider registries) {
        ResourceLocation block = ResourceLocation.tryParse(tag.getString("block"));
        if (block == null) {
            return null;
        }
        MachineGroup group = new MachineGroup(tag.getInt("id"), block, RenameRouterPayload.clean(tag.getString("name")),
                MachineConfig.load(tag.getCompound("profile"), registries));
        CompoundTag cardsTag = tag.getCompound("cards");
        for (int t = 0; t < CARD_TYPES.length; t++) {
            CompoundTag typeTag = cardsTag.getCompound(CARD_TYPES[t].key());
            for (RemoteSide side : SIDES) {
                ListTag list = typeTag.getList(side.key(), Tag.TAG_COMPOUND);
                ItemStack[] slots = group.cards[t][side.ordinal()];
                for (int i = 0; i < list.size(); i++) {
                    CompoundTag slotTag = list.getCompound(i);
                    int slot = slotTag.getByte("slot");
                    if (slot >= 0 && slot < slots.length) {
                        // Item de mod removido: a pilha não lê e o slot fica vazio.
                        ItemStack.parse(registries, slotTag).ifPresent(stack -> slots[slot] = stack);
                    }
                }
            }
        }
        return group;
    }
}
