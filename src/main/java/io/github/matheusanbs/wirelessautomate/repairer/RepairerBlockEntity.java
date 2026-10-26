package io.github.matheusanbs.wirelessautomate.repairer;

import io.github.matheusanbs.wirelessautomate.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** O Reparador de Cristais: os 17 slots ({@link RepairerSlots}) e o NBT {@code items}. Não faz tick. */
public class RepairerBlockEntity extends BlockEntity {
    private final NonNullList<ItemStack> items = NonNullList.withSize(RepairerSlots.TOTAL, ItemStack.EMPTY);
    private final RepairerContainer container = new RepairerContainer(this);

    public RepairerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.CRYSTAL_REPAIRER.get(), pos, state);
    }

    public NonNullList<ItemStack> items() {
        return items;
    }

    public RepairerContainer container() {
        return container;
    }

    /** O conteúdo mudou: marca o chunk para gravar. */
    public void itemsChanged() {
        setChanged();
    }

    /** Grava só os slots ocupados, cada um com o índice em {@code slot}. */
    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ListTag list = new ListTag();
        for (int i = 0; i < items.size(); i++) {
            ItemStack stack = items.get(i);
            if (!stack.isEmpty()) {
                CompoundTag entry = new CompoundTag();
                entry.putByte("slot", (byte) i);
                list.add(stack.save(registries, entry));
            }
        }
        tag.put("items", list);
    }

    /** Esvazia os slots e põe os gravados; um índice fora da lista é ignorado. */
    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        items.clear();
        ListTag list = tag.getList("items", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            int slot = entry.getByte("slot") & 0xFF;
            if (slot < items.size()) {
                items.set(slot, ItemStack.parse(registries, entry).orElse(ItemStack.EMPTY));
            }
        }
    }
}
