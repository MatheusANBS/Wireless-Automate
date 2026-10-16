package io.github.matheusanbs.wirelessautomate.resonator;

import net.minecraft.core.BlockPos;
import net.minecraft.world.Container;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

/**
 * O Núcleo visto como {@link Container} pelo menu: as câmaras do tier e, por último, o slot da redstone. Tudo passa
 * pelos mutadores do Núcleo ({@code setCrystalAt}, {@code setRedstone}), que gravam e mandam a câmara aos clientes.
 *
 * <p>{@link #getItem} devolve sempre uma pilha nova: nenhuma pilha do Núcleo sai daqui, e uma mudança feita no lugar
 * na pilha devolvida não vale nada (o slot da redstone do menu leva a inserção ao {@link #setItem}). O
 * {@link #setItem} não tem como recusar: o que não cabe (mais de um cristal numa câmara, item que não é cristal, redstone
 * com componentes ou acima de {@link ResonanceCoreBlockEntity#MAX_REDSTONE}) cai no mundo à frente do Núcleo, em vez
 * de sumir.
 */
public final class CoreInventory implements Container {
    private final ResonanceCoreBlockEntity core;

    public CoreInventory(ResonanceCoreBlockEntity core) {
        this.core = core;
    }

    /** Só a redstone comum: os componentes de uma redstone renomeada não cabem no inteiro do Núcleo. */
    public static boolean isPlainRedstone(ItemStack stack) {
        return stack.is(Items.REDSTONE) && stack.getComponentsPatch().isEmpty();
    }

    private boolean isRedstone(int slot) {
        return slot == core.chambers();
    }

    private boolean inRange(int slot) {
        return slot >= 0 && slot <= core.chambers();
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
        if (!inRange(slot)) {
            return ItemStack.EMPTY;
        }
        if (isRedstone(slot)) {
            return core.redstone() > 0 ? new ItemStack(Items.REDSTONE, core.redstone()) : ItemStack.EMPTY;
        }
        return core.crystals().get(slot).copy();
    }

    @Override
    public ItemStack removeItem(int slot, int count) {
        if (count <= 0 || !inRange(slot)) {
            return ItemStack.EMPTY;
        }
        if (isRedstone(slot)) {
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
        if (!inRange(slot)) {
            return ItemStack.EMPTY;
        }
        if (isRedstone(slot)) {
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
        if (!inRange(slot)) {
            drop(stack);
            return;
        }
        if (isRedstone(slot)) {
            if (stack.isEmpty()) {
                core.setRedstone(0);
            } else if (!isPlainRedstone(stack)) {
                drop(stack);
            } else {
                int kept = Math.min(stack.getCount(), ResonanceCoreBlockEntity.MAX_REDSTONE);
                core.setRedstone(kept);
                drop(stack.copyWithCount(stack.getCount() - kept));
            }
            return;
        }
        if (stack.isEmpty()) {
            core.setCrystalAt(slot, ItemStack.EMPTY);
        } else if (stack.getItem() instanceof SignalCrystalItem) {
            core.setCrystalAt(slot, stack.copyWithCount(1));
            drop(stack.copyWithCount(stack.getCount() - 1));
        } else {
            drop(stack);
        }
    }

    /** O limite das câmaras; o slot da redstone do menu tem o dele. */
    @Override
    public int getMaxStackSize() {
        return 1;
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return isRedstone(slot) ? isPlainRedstone(stack) : stack.getItem() instanceof SignalCrystalItem;
    }

    @Override
    public boolean stillValid(Player player) {
        return !core.isRemoved() && core.formed() && Container.stillValidBlockEntity(core, player);
    }

    @Override
    public void setChanged() {
        core.setChanged();
    }

    @Override
    public void clearContent() {
        for (int i = 0; i < core.chambers(); i++) {
            core.setCrystalAt(i, ItemStack.EMPTY);
        }
        core.setRedstone(0);
    }

    /** Joga no mundo, à frente do Núcleo (para fora da casca), o que um {@code setItem} não pôde guardar. */
    private void drop(ItemStack stack) {
        Level level = core.getLevel();
        if (stack.isEmpty() || level == null || level.isClientSide) {
            return;
        }
        BlockPos front = core.getBlockPos().relative(core.getBlockState().getValue(ResonatorPartBlock.FACING));
        Containers.dropItemStack(level, front.getX() + 0.5, front.getY() + 0.5, front.getZ() + 0.5, stack.copy());
    }
}
