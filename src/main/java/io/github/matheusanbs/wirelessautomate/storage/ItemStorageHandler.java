package io.github.matheusanbs.wirelessautomate.storage;

import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandler;

/**
 * O Baú visto como um inventário comum, para funis, AE2, RS e outros mods: um slot por tipo, mais
 * um vazio no fim que aceita qualquer item. A extração segue o contrato do {@link IItemHandler}
 * (no máximo uma pilha por chamada); a pilha de um slot mostra a quantidade real, até
 * {@link Integer#MAX_VALUE}. O roteador do mod não passa por aqui: usa o {@link BulkItems}.
 */
public final class ItemStorageHandler implements IItemHandler {
    private final ItemStorage storage;

    public ItemStorageHandler(ItemStorage storage) {
        this.storage = storage;
    }

    @Override
    public int getSlots() {
        return storage.types() + 1;
    }

    @Override
    public ItemStack getStackInSlot(int slot) {
        if (slot < 0 || slot >= storage.types()) {
            return ItemStack.EMPTY;
        }
        return storage.key(slot).copyWithCount((int) Math.min(storage.count(slot), Integer.MAX_VALUE));
    }

    @Override
    public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
        if (stack.isEmpty() || !isItemValid(slot, stack)) {
            return stack;
        }
        int count = stack.getCount();
        int accepted = (int) storage.insert(stack, count, simulate);
        return accepted >= count ? ItemStack.EMPTY : stack.copyWithCount(count - accepted);
    }

    @Override
    public ItemStack extractItem(int slot, int amount, boolean simulate) {
        if (amount <= 0 || slot < 0 || slot >= storage.types()) {
            return ItemStack.EMPTY;
        }
        // A chave continua valendo depois da extração, mesmo se o tipo sair da lista.
        ItemStack key = storage.key(slot);
        long taken = storage.extract(key, Math.min(amount, key.getMaxStackSize()), simulate);
        return taken <= 0 ? ItemStack.EMPTY : key.copyWithCount((int) taken);
    }

    @Override
    public int getSlotLimit(int slot) {
        return Integer.MAX_VALUE;
    }

    /** Um slot de tipo só aceita o próprio tipo; o vazio do fim aceita qualquer um. */
    @Override
    public boolean isItemValid(int slot, ItemStack stack) {
        if (slot < 0 || slot > storage.types()) {
            return false;
        }
        return slot == storage.types() || ItemStack.isSameItemSameTags(storage.key(slot), stack);
    }
}
