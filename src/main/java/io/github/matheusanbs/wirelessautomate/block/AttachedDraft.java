package io.github.matheusanbs.wirelessautomate.block;

import io.github.matheusanbs.wirelessautomate.network.FaceConfig;
import io.github.matheusanbs.wirelessautomate.network.PortMode;
import io.github.matheusanbs.wirelessautomate.network.RedstoneMode;
import io.github.matheusanbs.wirelessautomate.network.RelativeSide;
import io.github.matheusanbs.wirelessautomate.network.ResourceType;
import io.github.matheusanbs.wirelessautomate.range.MachineConfig;
import io.github.matheusanbs.wirelessautomate.range.MachineGroup;
import io.github.matheusanbs.wirelessautomate.range.RemoteSide;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * A máquina presa de um mundo antigo, lida das chaves {@code faces} e {@code cards} do roteador (por
 * {@link RelativeSide}, no quadro do roteador), até virar membro de um grupo ({@link RouterAttached#sync}).
 * Enquanto existe, vale como a configuração da presa (leitura, redes e redstone, o "todas as faces numa rede"),
 * e volta a ser gravada nas mesmas chaves, para nada se perder com a máquina ausente ou o chunk dela descarregado.
 */
final class AttachedDraft {
    private static final ResourceType[] TYPES = ResourceType.values();
    private static final RelativeSide[] SIDES = RelativeSide.values();
    private static final ResourceType[] CARD_TYPES = Arrays.stream(TYPES).filter(ResourceType::cards)
            .toArray(ResourceType[]::new);
    private static final int CARD_SLOTS = RouterBlockEntity.CARD_SLOTS;

    /** [tipo][lado relativo], nunca nulo. */
    private final FaceConfig[][] faces = new FaceConfig[TYPES.length][SIDES.length];
    /** [tipo de {@link #CARD_TYPES}][lado relativo][slot], nunca nulo. */
    private final ItemStack[][][] cards = new ItemStack[CARD_TYPES.length][SIDES.length][CARD_SLOTS];

    private AttachedDraft() {
        for (FaceConfig[] row : faces) {
            for (int i = 0; i < row.length; i++) {
                row[i] = new FaceConfig();
            }
        }
        for (ItemStack[][] byType : cards) {
            for (ItemStack[] bySide : byType) {
                Arrays.fill(bySide, ItemStack.EMPTY);
            }
        }
    }

    /**
     * Lê as chaves antigas de {@code tag} e põe a rede antiga da aba ({@code legacy}, por
     * {@link ResourceType#ordinal()}) nas faces ainda sem rede, como a migração de sempre. {@code null} se tudo
     * ficou no padrão e sem cartões (nada a converter).
     */
    static @Nullable AttachedDraft load(CompoundTag tag, HolderLookup.Provider registries, UUID[] legacy) {
        if (!tag.contains("faces", Tag.TAG_COMPOUND) && !tag.contains("cards", Tag.TAG_COMPOUND) && allNull(legacy)) {
            return null;
        }
        AttachedDraft draft = new AttachedDraft();
        CompoundTag facesTag = tag.getCompound("faces");
        for (ResourceType type : TYPES) {
            CompoundTag typeTag = facesTag.getCompound(type.key());
            UUID network = legacy[type.ordinal()];
            for (RelativeSide side : SIDES) {
                FaceConfig config = draft.faces[type.ordinal()][side.ordinal()];
                if (typeTag.contains(side.key(), CompoundTag.TAG_COMPOUND)) {
                    config.copyFrom(FaceConfig.load(typeTag.getCompound(side.key()), registries));
                }
                if (network != null && config.network() == null) {
                    config.setNetwork(network);
                }
            }
        }
        CompoundTag cardsTag = tag.getCompound("cards");
        for (int t = 0; t < CARD_TYPES.length; t++) {
            CompoundTag typeTag = cardsTag.getCompound(CARD_TYPES[t].key());
            for (RelativeSide side : SIDES) {
                ListTag list = typeTag.getList(side.key(), Tag.TAG_COMPOUND);
                ItemStack[] slots = draft.cards[t][side.ordinal()];
                for (int i = 0; i < list.size(); i++) {
                    CompoundTag slotTag = list.getCompound(i);
                    int slot = slotTag.getByte("slot");
                    if (slot >= 0 && slot < CARD_SLOTS) {
                        // Item de mod removido: a pilha não lê e o slot fica vazio.
                        ItemStack.parse(registries, slotTag).ifPresent(stack -> slots[slot] = stack);
                    }
                }
            }
        }
        return draft.isEmpty() ? null : draft;
    }

    private static boolean allNull(UUID[] legacy) {
        for (UUID network : legacy) {
            if (network != null) {
                return false;
            }
        }
        return true;
    }

    private boolean isEmpty() {
        for (FaceConfig[] row : faces) {
            for (FaceConfig config : row) {
                if (!config.isDefault()) {
                    return false;
                }
            }
        }
        return !hasCards();
    }

    boolean hasCards() {
        for (ItemStack[][] byType : cards) {
            for (ItemStack[] bySide : byType) {
                for (ItemStack stack : bySide) {
                    if (!stack.isEmpty()) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /** A face do lado relativo {@code side}. Só leitura para quem está fora; mude por {@link #setNetwork}. */
    FaceConfig face(ResourceType type, RelativeSide side) {
        return faces[type.ordinal()][side.ordinal()];
    }

    /** O cartão do lado relativo {@code side} no slot; vazio se o tipo não tem cartões. */
    ItemStack card(ResourceType type, RelativeSide side, int slot) {
        for (int t = 0; t < CARD_TYPES.length; t++) {
            if (CARD_TYPES[t] == type) {
                return slot >= 0 && slot < CARD_SLOTS ? cards[t][side.ordinal()][slot] : ItemStack.EMPTY;
            }
        }
        return ItemStack.EMPTY;
    }

    /** Põe as seis faces do tipo na rede, menos as que o {@code canLeave} segura (contadas em {@code kept}). */
    boolean setNetwork(ResourceType type, @Nullable UUID network, Predicate<UUID> canLeave, int[] kept) {
        boolean changed = false;
        for (FaceConfig config : faces[type.ordinal()]) {
            changed |= RouterFaces.moveFace(config, network, canLeave, kept);
        }
        return changed;
    }

    /** Junta em {@code found} as redes das faces do tipo. */
    void addNetworks(Set<UUID> found, ResourceType type) {
        for (FaceConfig config : faces[type.ordinal()]) {
            if (config.network() != null) {
                found.add(config.network());
            }
        }
    }

    /** Alguma face do tipo tem modo e controle por redstone. */
    boolean usesRedstone(ResourceType type) {
        for (FaceConfig config : faces[type.ordinal()]) {
            if (config.redstone() != RedstoneMode.IGNORE && config.mode() != PortMode.NONE) {
                return true;
            }
        }
        return false;
    }

    /** O lado da máquina (frente {@code front}) onde fica o lado relativo {@code side} do roteador. */
    private static RemoteSide remote(RelativeSide side, Direction facing, int spin, int front) {
        return RemoteSide.fromAbsolute(front, side.toAbsolute(facing, spin).ordinal());
    }

    /** A configuração do rascunho nos lados da máquina, com o roteador em {@code facing} + {@code spin}. */
    MachineConfig toConfig(Direction facing, int spin, int front) {
        MachineConfig config = new MachineConfig();
        for (ResourceType type : TYPES) {
            for (RelativeSide side : SIDES) {
                config.face(type, remote(side, facing, spin, front)).copyFrom(faces[type.ordinal()][side.ordinal()]);
            }
        }
        return config;
    }

    /** Os cartões do rascunho, convertidos como em {@link #toConfig}, são os mesmos do grupo, slot a slot. */
    boolean sameCards(MachineGroup group, Direction facing, int spin, int front) {
        for (int t = 0; t < CARD_TYPES.length; t++) {
            for (RelativeSide side : SIDES) {
                RemoteSide to = remote(side, facing, spin, front);
                for (int slot = 0; slot < CARD_SLOTS; slot++) {
                    if (!ItemStack.matches(cards[t][side.ordinal()][slot], group.card(CARD_TYPES[t], to, slot))) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    /** Põe os cartões do rascunho no grupo, convertidos como em {@link #toConfig}. */
    void cardsInto(MachineGroup group, Direction facing, int spin, int front) {
        for (int t = 0; t < CARD_TYPES.length; t++) {
            for (RelativeSide side : SIDES) {
                RemoteSide to = remote(side, facing, spin, front);
                for (int slot = 0; slot < CARD_SLOTS; slot++) {
                    group.setCard(CARD_TYPES[t], to, slot, cards[t][side.ordinal()][slot]);
                }
            }
        }
    }

    /** Grava o rascunho nas chaves antigas {@code faces} e {@code cards}, sem os padrões. */
    void save(CompoundTag tag, HolderLookup.Provider registries) {
        CompoundTag facesTag = new CompoundTag();
        for (ResourceType type : TYPES) {
            CompoundTag typeTag = new CompoundTag();
            for (RelativeSide side : SIDES) {
                FaceConfig config = faces[type.ordinal()][side.ordinal()];
                if (!config.isDefault()) {
                    typeTag.put(side.key(), config.save(registries));
                }
            }
            if (!typeTag.isEmpty()) {
                facesTag.put(type.key(), typeTag);
            }
        }
        if (!facesTag.isEmpty()) {
            tag.put("faces", facesTag);
        }
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
        if (!cardsTag.isEmpty()) {
            tag.put("cards", cardsTag);
        }
    }

    /** Tira todos os cartões do rascunho (para soltar no mundo quando o roteador sai). */
    void removeAllCards(List<ItemStack> removed) {
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
}
