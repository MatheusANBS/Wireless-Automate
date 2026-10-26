package io.github.matheusanbs.wirelessautomate.repairer;

import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/** Os 17 slots do Reparador como {@link Container}, para o menu e para largar o conteúdo ao quebrar. */
public final class RepairerContainer implements Container {
    private final RepairerBlockEntity repairer;

    RepairerContainer(RepairerBlockEntity repairer) {
        this.repairer = repairer;
    }

    @Override
    public int getContainerSize() {
        return RepairerSlots.TOTAL;
    }

    @Override
    public boolean isEmpty() {
        for (ItemStack stack : repairer.items()) {
            if (!stack.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public ItemStack getItem(int slot) {
        return repairer.items().get(slot);
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        ItemStack removed = ContainerHelper.removeItem(repairer.items(), slot, amount);
        if (!removed.isEmpty()) {
            repairer.itemsChanged();
        }
        return removed;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        return ContainerHelper.takeItem(repairer.items(), slot);
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        repairer.items().set(slot, stack);
        repairer.itemsChanged();
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        if (RepairerSlots.isInput(slot)) {
            return RepairerSlots.acceptsCracked(stack);
        }
        return slot == RepairerSlots.FEED && RepairerSlots.acceptsFeed(stack);
    }

    @Override
    public int getMaxStackSize() {
        return 64;
    }

    @Override
    public boolean stillValid(Player player) {
        return Container.stillValidBlockEntity(repairer, player);
    }

    @Override
    public void setChanged() {
        repairer.itemsChanged();
    }

    @Override
    public void clearContent() {
        repairer.items().clear();
        repairer.itemsChanged();
    }
}
