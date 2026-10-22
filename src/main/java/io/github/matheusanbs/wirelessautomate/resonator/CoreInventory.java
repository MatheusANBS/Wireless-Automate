package io.github.matheusanbs.wirelessautomate.resonator;

import io.github.matheusanbs.wirelessautomate.block.RouterTier;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Container;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * O Núcleo visto como {@link Container} pelo menu: as câmaras do tier, o slot do catalisador e, só no Ultimate, o do
 * Eco ({@link #ecoSlot}), sempre vazio: o Afinado Ultimate rachado posto nele vira Eco na hora. Tudo
 * passa pelos mutadores do Núcleo ({@code setCrystalAt}, {@code setCatalyst}), que gravam e mandam a câmara aos
 * clientes.
 *
 * <p>{@link #getItem} devolve sempre uma pilha nova: nenhuma pilha do Núcleo sai daqui, e uma mudança feita no lugar
 * na pilha devolvida não vale nada (o slot do catalisador do menu leva a inserção ao {@link #setItem}). O
 * {@link #setItem} não tem como recusar: o que não cabe (mais de um cristal numa câmara, item que não é cristal,
 * cristal rachado, catalisador que o tier não aceita (salvo o antigo já guardado, que só diminui) ou acima de {@link ResonanceCoreBlockEntity#MAX_REDSTONE}, o que
 * o Eco não queima) cai no mundo à frente do Núcleo, em vez de sumir.
 */
public final class CoreInventory implements Container {
    private final ResonanceCoreBlockEntity core;

    public CoreInventory(ResonanceCoreBlockEntity core) {
        this.core = core;
    }

    private boolean isCatalyst(int slot) {
        return slot == core.chambers();
    }

    /** O índice do slot do Eco, ou -1 fora do Ultimate. */
    public static int ecoSlot(RouterTier tier) {
        return tier == RouterTier.ULTIMATE ? CoreMath.chambers(tier.ordinal()) + 1 : -1;
    }

    private boolean isEco(int slot) {
        return slot >= 0 && slot == ecoSlot(core.tier());
    }

    private boolean inRange(int slot) {
        return slot >= 0 && slot < getContainerSize();
    }

    @Override
    public int getContainerSize() {
        return core.chambers() + (ecoSlot(core.tier()) < 0 ? 1 : 2);
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
        if (!inRange(slot) || isEco(slot)) {
            return ItemStack.EMPTY;
        }
        if (isCatalyst(slot)) {
            return core.catalyst().stack();
        }
        return core.crystals().get(slot).copy();
    }

    @Override
    public ItemStack removeItem(int slot, int count) {
        if (count <= 0 || !inRange(slot) || isEco(slot)) {
            return ItemStack.EMPTY;
        }
        if (isCatalyst(slot)) {
            int taken = Math.min(count, core.redstone());
            if (taken <= 0) {
                return ItemStack.EMPTY;
            }
            Item item = core.catalyst().item();
            core.setCatalyst(item, core.redstone() - taken);
            return new ItemStack(item, taken);
        }
        return removeItemNoUpdate(slot);
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        if (!inRange(slot) || isEco(slot)) {
            return ItemStack.EMPTY;
        }
        if (isCatalyst(slot)) {
            ItemStack taken = core.catalyst().stack();
            if (!taken.isEmpty()) {
                core.setCatalyst(taken.getItem(), 0);
            }
            return taken;
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
        if (isEco(slot)) {
            drop(stack.copyWithCount(stack.getCount() - core.eco().burn(stack, false)));
            return;
        }
        if (isCatalyst(slot)) {
            if (stack.isEmpty()) {
                core.setCatalyst(core.catalyst().item(), 0);
            } else if (!CoreCatalyst.accepts(core.tier(), stack)) {
                if (core.redstone() > 0 && stack.is(core.catalyst().item())) {
                    // O catalisador antigo que o tier já não aceita: a pilha recebida é o que sobrou dele no slot.
                    core.setCatalyst(stack.getItem(), Math.min(stack.getCount(), core.redstone()));
                } else {
                    drop(stack);
                }
            } else {
                int kept = Math.min(stack.getCount(), ResonanceCoreBlockEntity.MAX_REDSTONE);
                core.setCatalyst(stack.getItem(), kept);
                drop(stack.copyWithCount(stack.getCount() - kept));
            }
            return;
        }
        if (stack.isEmpty()) {
            core.setCrystalAt(slot, ItemStack.EMPTY);
        } else if (stack.getItem() instanceof SignalCrystalItem && !SignalCrystalItem.isCracked(stack)) {
            core.setCrystalAt(slot, stack.copyWithCount(1));
            drop(stack.copyWithCount(stack.getCount() - 1));
        } else {
            drop(stack);
        }
    }

    /** O limite das câmaras; o slot do catalisador do menu tem o dele. */
    @Override
    public int getMaxStackSize() {
        return 1;
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        if (isEco(slot)) {
            return core.eco().room(stack) > 0;
        }
        if (isCatalyst(slot)) {
            return core.catalyst().canAdd(core.tier(), stack);
        }
        return stack.getItem() instanceof SignalCrystalItem && !SignalCrystalItem.isCracked(stack);
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
        core.setCatalyst(core.catalyst().item(), 0);
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
