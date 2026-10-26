package io.github.matheusanbs.wirelessautomate.repairer;

import io.github.matheusanbs.wirelessautomate.Config;
import io.github.matheusanbs.wirelessautomate.block.RouterTier;
import io.github.matheusanbs.wirelessautomate.registry.ModBlockEntities;
import io.github.matheusanbs.wirelessautomate.storage.ScalarStore;
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
    private final ScalarStore energy = new ScalarStore(this::markUnsaved, RepairerBlockEntity::bufferCapacity);
    private final RepairerItems itemView = new RepairerItems(this);
    private final RepairerEnergy energyView = new RepairerEnergy(this, energy);
    private final RepairerBulkEnergy bulkEnergyView = new RepairerBulkEnergy(this, energy);

    public RepairerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.CRYSTAL_REPAIRER.get(), pos, state);
    }

    public NonNullList<ItemStack> items() {
        return items;
    }

    public RepairerContainer container() {
        return container;
    }

    public ScalarStore energy() {
        return energy;
    }

    public RepairerItems itemView() {
        return itemView;
    }

    public RepairerEnergy energyView() {
        return energyView;
    }

    public RepairerBulkEnergy bulkEnergyView() {
        return bulkEnergyView;
    }

    /** O reparo mais caro (o Afinado Ultimate) vezes as oito linhas, pela config. */
    private static long bufferCapacity() {
        long highest = RepairMath.cost(Config.resonatorFePerCrystal(RouterTier.ULTIMATE), Config.repairCostFactor());
        return RepairMath.bufferCapacity(highest, RepairMath.LINES);
    }

    /** A energia mudou: marca o chunk para gravar, sem avisar os vizinhos. */
    private void markUnsaved() {
        if (level != null) {
            level.blockEntityChanged(worldPosition);
        }
    }

    /** Chamado depois de uma entrada de item ou de energia; quando houver motor, começa os reparos que já podem começar. */
    public void wake() {
    }

    /** O conteúdo mudou: marca o chunk para gravar e acorda o motor. */
    public void itemsChanged() {
        setChanged();
        wake();
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
        tag.putLong("energy", energy.stored());
    }

    /** Esvazia os slots e põe os gravados; um índice fora da lista é ignorado. */
    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        items.clear();
        energy.set(Math.max(0, tag.getLong("energy")));
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
