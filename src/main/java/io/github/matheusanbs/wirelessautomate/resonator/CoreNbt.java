package io.github.matheusanbs.wirelessautomate.resonator;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;

/** As listas do NBT do Núcleo: os membros da casca ({@code members}) e as câmaras ({@code crystals}). */
final class CoreNbt {
    private CoreNbt() {
    }

    static void saveMembers(CompoundTag tag, List<BlockPos> members) {
        long[] packed = new long[members.size()];
        for (int i = 0; i < packed.length; i++) {
            packed[i] = members.get(i).asLong();
        }
        tag.putLongArray("members", packed);
    }

    static List<BlockPos> loadMembers(CompoundTag tag) {
        List<BlockPos> loaded = new ArrayList<>();
        for (long packed : tag.getLongArray("members")) {
            loaded.add(BlockPos.of(packed));
        }
        return List.copyOf(loaded);
    }

    /** Grava só as câmaras ocupadas, cada uma com o índice em {@code slot}. */
    static void saveCrystals(CompoundTag tag, List<ItemStack> crystals, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (int i = 0; i < crystals.size(); i++) {
            ItemStack stack = crystals.get(i);
            if (!stack.isEmpty()) {
                CompoundTag entry = new CompoundTag();
                entry.putByte("slot", (byte) i);
                list.add(stack.save(registries, entry));
            }
        }
        tag.put("crystals", list);
    }

    /** Esvazia {@code crystals} e põe as gravadas; um índice fora da lista é ignorado. */
    static void loadCrystals(CompoundTag tag, List<ItemStack> crystals, HolderLookup.Provider registries) {
        for (int i = 0; i < crystals.size(); i++) {
            crystals.set(i, ItemStack.EMPTY);
        }
        ListTag list = tag.getList("crystals", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            int slot = entry.getByte("slot") & 0xFF;
            if (slot < crystals.size()) {
                crystals.set(slot, ItemStack.parse(registries, entry).orElse(ItemStack.EMPTY));
            }
        }
    }
}
