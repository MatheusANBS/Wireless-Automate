package io.github.matheusanbs.wirelessautomate.resonator;

import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * O Núcleo visto como {@link Container} pelo menu: as câmaras do tier e, por último, o slot da redstone. Tudo passa
 * pelos mutadores do Núcleo ({@code setCrystalAt}, {@code setRedstone}), que gravam e mandam a câmara aos clientes.
 *
 * <p>A redstone é um inteiro no Núcleo, então o slot dela mostra sempre a mesma pilha, ajustada à contagem: o vanilla
 * muda essa pilha no lugar ({@code grow} ao juntar redstone, {@code shrink} no Shift + clique) e só depois chama
 * {@link #setChanged()}, que leva ao Núcleo a diferença em relação ao último valor mostrado. Pela diferença, e não
 * pelo valor, a redstone gasta pelo lote no meio do caminho não volta. As câmaras dão uma cópia, para nenhuma mudança
 * no lugar escapar do {@code setCrystalAt}.
 */
public final class CoreInventory implements Container {
    private final ResonanceCoreBlockEntity core;
    private final ItemStack redstoneView = new ItemStack(Items.REDSTONE);
    /** A contagem da {@link #redstoneView} quando ela saiu pela última vez. */
    private int shownRedstone = -1;

    public CoreInventory(ResonanceCoreBlockEntity core) {
        this.core = core;
    }

    private boolean isRedstone(int slot) {
        return slot == core.chambers();
    }

    @Override
    public int getContainerSize() {
        return core.chambers() + 1;
    }

    @Override
    public boolean isEmpty() {
        if (core.redstone() > 0) {
            return false;
        }
        for (int i = 0; i < core.chambers(); i++) {
            if (!core.crystals().get(i).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public ItemStack getItem(int slot) {
        if (slot < 0 || slot > core.chambers()) {
            return ItemStack.EMPTY;
        }
        if (isRedstone(slot)) {
            pushRedstone();
            int count = core.redstone();
            if (count <= 0) {
                shownRedstone = -1;
                return ItemStack.EMPTY;
            }
            redstoneView.setCount(count);
            shownRedstone = count;
            return redstoneView;
        }
        return core.crystals().get(slot).copy();
    }

    @Override
    public ItemStack removeItem(int slot, int count) {
        if (count <= 0 || slot < 0 || slot > core.chambers()) {
            return ItemStack.EMPTY;
        }
        if (isRedstone(slot)) {
            pushRedstone();
            int taken = Math.min(count, core.redstone());
            if (taken <= 0) {
                return ItemStack.EMPTY;
            }
            core.setRedstone(core.redstone() - taken);
            return new ItemStack(Items.REDSTONE, taken);
        }
        return removeItemNoUpdate(slot);
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        if (slot < 0 || slot > core.chambers()) {
            return ItemStack.EMPTY;
        }
        if (isRedstone(slot)) {
            pushRedstone();
            int taken = core.redstone();
            if (taken <= 0) {
                return ItemStack.EMPTY;
            }
            core.setRedstone(0);
            return new ItemStack(Items.REDSTONE, taken);
        }
        ItemStack stack = core.crystals().get(slot).copy();
        if (!stack.isEmpty()) {
            core.setCrystalAt(slot, ItemStack.EMPTY);
        }
        return stack;
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        if (slot < 0 || slot > core.chambers()) {
            return;
        }
        if (isRedstone(slot)) {
            shownRedstone = -1;
            core.setRedstone(stack.is(Items.REDSTONE) ? stack.getCount() : 0);
        } else {
            core.setCrystalAt(slot, stack);
        }
    }

    @Override
    public int getMaxStackSize() {
        return ResonanceCoreBlockEntity.MAX_REDSTONE;
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return isRedstone(slot) ? stack.is(Items.REDSTONE) : stack.getItem() instanceof SignalCrystalItem;
    }

    @Override
    public boolean stillValid(Player player) {
        return !core.isRemoved() && core.formed() && Container.stillValidBlockEntity(core, player);
    }

    @Override
    public void setChanged() {
        pushRedstone();
        core.setChanged();
    }

    @Override
    public void clearContent() {
        shownRedstone = -1;
        for (int i = 0; i < core.chambers(); i++) {
            core.setCrystalAt(i, ItemStack.EMPTY);
        }
        core.setRedstone(0);
    }

    /** Leva ao Núcleo o que o vanilla mudou no lugar na pilha da redstone desde que ela saiu. */
    private void pushRedstone() {
        if (shownRedstone < 0) {
            return;
        }
        int delta = redstoneView.getCount() - shownRedstone;
        shownRedstone = redstoneView.getCount();
        if (delta != 0) {
            core.setRedstone(core.redstone() + delta);
        }
    }
}
