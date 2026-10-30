package io.github.matheusanbs.wirelessautomate.menu;

import java.util.List;
import net.minecraft.core.NonNullList;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.StackedContents;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.ItemStack;

/**
 * A grade 3×3 da Mesa de Trabalho do Baú vista como {@link CraftingContainer}. No servidor as pilhas são as do
 * {@code ChestUpgrades.grid()} do Baú, divididas por todos os menus abertos nele, e toda mudança chama
 * {@code onChange} (o {@code changed()} dos upgrades: salva o Baú e recalcula o resultado de todos os menus). No
 * cliente é uma lista local, que a sincronização normal dos slots preenche, e {@code onChange} não faz nada.
 *
 * <p>Um craft tira de vários slots: entre {@link #beginBatch()} e {@link #endBatch()} os avisos se juntam num só.
 */
final class ChestCraftGrid implements CraftingContainer {
    static final int SIZE = 3;

    private final NonNullList<ItemStack> items;
    private final Runnable onChange;
    private int batch;
    private boolean pending;

    ChestCraftGrid(NonNullList<ItemStack> items, Runnable onChange) {
        this.items = items;
        this.onChange = onChange;
    }

    void beginBatch() {
        batch++;
    }

    void endBatch() {
        if (--batch == 0 && pending) {
            pending = false;
            onChange.run();
        }
    }

    private void changed() {
        if (batch > 0) {
            pending = true;
        } else {
            onChange.run();
        }
    }

    @Override
    public int getWidth() {
        return SIZE;
    }

    @Override
    public int getHeight() {
        return SIZE;
    }

    @Override
    public List<ItemStack> getItems() {
        return List.copyOf(items);
    }

    @Override
    public int getContainerSize() {
        return items.size();
    }

    @Override
    public boolean isEmpty() {
        for (ItemStack stack : items) {
            if (!stack.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public ItemStack getItem(int slot) {
        return slot >= 0 && slot < items.size() ? items.get(slot) : ItemStack.EMPTY;
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        ItemStack taken = ContainerHelper.removeItem(items, slot, amount);
        if (!taken.isEmpty()) {
            changed();
        }
        return taken;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        ItemStack taken = ContainerHelper.takeItem(items, slot);
        if (!taken.isEmpty()) {
            changed();
        }
        return taken;
    }

    /** Sem aviso: o {@code Slot.set} chama {@link #setChanged()} logo depois, e um aviso basta. */
    @Override
    public void setItem(int slot, ItemStack stack) {
        items.set(slot, stack);
    }

    /** Também a pilha que só cresceu (o {@code moveItemStackTo} faz {@code grow} e {@code setChanged}). */
    @Override
    public void setChanged() {
        changed();
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }

    @Override
    public void clearContent() {
        items.replaceAll(stack -> ItemStack.EMPTY);
        changed();
    }

    /** Como o {@code TransientCraftingContainer}. */
    @Override
    public void fillStackedContents(StackedContents contents) {
        for (ItemStack stack : items) {
            contents.accountSimpleStack(stack);
        }
    }
}
