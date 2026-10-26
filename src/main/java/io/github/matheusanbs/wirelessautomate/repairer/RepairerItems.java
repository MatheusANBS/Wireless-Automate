package io.github.matheusanbs.wirelessautomate.repairer;

import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;

/**
 * O Reparador como {@link IItemHandler} de 17 slots: as entradas aceitam um cristal rachado cada, o estoque
 * aceita Cristais de Sinal cheios até 64 e só as saídas deixam tirar.
 */
public final class RepairerItems implements IItemHandler {
    private final RepairerBlockEntity repairer;

    RepairerItems(RepairerBlockEntity repairer) {
        this.repairer = repairer;
    }

    @Override
    public int getSlots() {
        return RepairerSlots.TOTAL;
    }

    @Override
    public ItemStack getStackInSlot(int slot) {
        return repairer.items().get(slot);
    }

    @Override
    public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
        if (stack.isEmpty() || !isItemValid(slot, stack)) {
            return stack;
        }
        ItemStack current = repairer.items().get(slot);
        if (RepairerSlots.isInput(slot)) {
            if (!current.isEmpty()) {
                return stack;
            }
            if (!simulate) {
                repairer.items().set(slot, stack.copyWithCount(1));
                repairer.itemsChanged();
            }
            return stack.copyWithCount(stack.getCount() - 1);
        }
        if (!current.isEmpty() && !ItemStack.isSameItemSameComponents(current, stack)) {
            return stack;
        }
        int moved = Math.min(getSlotLimit(slot) - current.getCount(), stack.getCount());
        if (moved <= 0) {
            return stack;
        }
        if (!simulate) {
            if (current.isEmpty()) {
                repairer.items().set(slot, stack.copyWithCount(moved));
            } else {
                current.grow(moved);
            }
            repairer.itemsChanged();
        }
        return stack.copyWithCount(stack.getCount() - moved);
    }

    @Override
    public ItemStack extractItem(int slot, int amount, boolean simulate) {
        if (!RepairerSlots.isOutput(slot) || amount <= 0) {
            return ItemStack.EMPTY;
        }
        ItemStack current = repairer.items().get(slot);
        if (current.isEmpty()) {
            return ItemStack.EMPTY;
        }
        int taken = Math.min(amount, current.getCount());
        ItemStack out = current.copyWithCount(taken);
        if (!simulate) {
            current.shrink(taken);
            repairer.itemsChanged();
        }
        return out;
    }

    @Override
    public int getSlotLimit(int slot) {
        return RepairerSlots.isInput(slot) ? 1 : 64;
    }

    @Override
    public boolean isItemValid(int slot, ItemStack stack) {
        if (RepairerSlots.isInput(slot)) {
            return RepairerSlots.acceptsCracked(stack);
        }
        return slot == RepairerSlots.FEED && RepairerSlots.acceptsFeed(stack);
    }
}
