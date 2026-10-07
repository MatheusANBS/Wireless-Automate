package io.github.matheusanbs.wirelessautomate.gametest;

import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;

/**
 * Máquinas de teste dos GameTests que o vanilla não tem: um slot com pilha enorme (como uma gaveta
 * ou um barril com upgrade de pilha) e uma máquina com mais tanques que a janela de uma visita.
 * Presas a blocos vanilla sem capability, com o estado guardado por posição. Só existem com
 * {@code -Dwirelessautomate.gameTests=true} (a run {@code gameTestServer} liga).
 */
@EventBusSubscriber(modid = WirelessAutomate.MODID)
public final class TestMachines {
    /** Um slot só, que guarda até {@link BigSlot#LIMIT} itens e entrega no máximo uma pilha por extração. */
    public static final Block BIG_SLOT = Blocks.SPONGE;
    /** {@link ManyTanks#TANKS} tanques de fluido, só de saída. */
    public static final Block MANY_TANKS = Blocks.WET_SPONGE;

    private static final boolean ENABLED = Boolean.getBoolean("wirelessautomate.gameTests");
    private static final Map<BlockPos, BigSlot> BIG_SLOTS = new ConcurrentHashMap<>();
    private static final Map<BlockPos, ManyTanks> TANKS = new ConcurrentHashMap<>();

    public static boolean enabled() {
        return ENABLED;
    }

    @SubscribeEvent
    static void register(RegisterCapabilitiesEvent event) {
        if (!ENABLED) {
            return;
        }
        WirelessAutomate.LOGGER.info("GameTests: máquinas de teste ligadas em {} e {}", BIG_SLOT, MANY_TANKS);
        event.registerBlock(Capabilities.ItemHandler.BLOCK, (level, pos, state, be, side) -> bigSlot(pos), BIG_SLOT);
        event.registerBlock(Capabilities.FluidHandler.BLOCK, (level, pos, state, be, side) -> manyTanks(pos), MANY_TANKS);
    }

    /** O slot da posição absoluta {@code pos} (criado vazio na primeira consulta). */
    static BigSlot bigSlot(BlockPos pos) {
        return BIG_SLOTS.computeIfAbsent(pos.immutable(), key -> new BigSlot());
    }

    static ManyTanks manyTanks(BlockPos pos) {
        return TANKS.computeIfAbsent(pos.immutable(), key -> new ManyTanks());
    }

    /** Esquece o estado da posição (o mundo dos testes é reaproveitado entre lotes). */
    static void reset(BlockPos pos) {
        BIG_SLOTS.remove(pos.immutable());
        TANKS.remove(pos.immutable());
    }

    static final class BigSlot implements IItemHandler {
        static final int LIMIT = 1_000_000;
        private Item item = Items.AIR;
        private int count;

        void set(Item item, int count) {
            this.item = item;
            this.count = count;
        }

        int count() {
            return count;
        }

        @Override
        public int getSlots() {
            return 1;
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            return count <= 0 ? ItemStack.EMPTY : new ItemStack(item, count);
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            if (stack.isEmpty() || count > 0 && !stack.is(item)) {
                return stack;
            }
            int accepted = Math.min(stack.getCount(), LIMIT - count);
            if (accepted <= 0) {
                return stack;
            }
            if (!simulate) {
                item = stack.getItem();
                count += accepted;
            }
            return stack.copyWithCount(stack.getCount() - accepted);
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            int taken = Math.min(Math.min(amount, count), item.getDefaultMaxStackSize());
            if (taken <= 0) {
                return ItemStack.EMPTY;
            }
            ItemStack out = new ItemStack(item, taken);
            if (!simulate) {
                count -= taken;
            }
            return out;
        }

        @Override
        public int getSlotLimit(int slot) {
            return LIMIT;
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return true;
        }
    }

    static final class ManyTanks implements IFluidHandler {
        static final int TANKS = 20;
        static final int CAPACITY = 1_000;
        private final FluidStack[] tanks = new FluidStack[TANKS];

        ManyTanks() {
            Arrays.fill(tanks, FluidStack.EMPTY);
        }

        void set(int tank, FluidStack stack) {
            tanks[tank] = stack.copy();
        }

        int amount(int tank) {
            return tanks[tank].getAmount();
        }

        @Override
        public int getTanks() {
            return TANKS;
        }

        @Override
        public FluidStack getFluidInTank(int tank) {
            return tanks[tank];
        }

        @Override
        public int getTankCapacity(int tank) {
            return CAPACITY;
        }

        @Override
        public boolean isFluidValid(int tank, FluidStack stack) {
            return false;
        }

        @Override
        public int fill(FluidStack resource, FluidAction action) {
            return 0;
        }

        @Override
        public FluidStack drain(FluidStack resource, FluidAction action) {
            for (int tank = 0; tank < TANKS; tank++) {
                if (!tanks[tank].isEmpty() && FluidStack.isSameFluidSameComponents(tanks[tank], resource)) {
                    return drainTank(tank, resource.getAmount(), action);
                }
            }
            return FluidStack.EMPTY;
        }

        @Override
        public FluidStack drain(int maxDrain, FluidAction action) {
            for (int tank = 0; tank < TANKS; tank++) {
                if (!tanks[tank].isEmpty()) {
                    return drainTank(tank, maxDrain, action);
                }
            }
            return FluidStack.EMPTY;
        }

        private FluidStack drainTank(int tank, int max, FluidAction action) {
            int taken = Math.min(max, tanks[tank].getAmount());
            if (taken <= 0) {
                return FluidStack.EMPTY;
            }
            FluidStack out = tanks[tank].copyWithAmount(taken);
            if (action.execute()) {
                tanks[tank] = tanks[tank].getAmount() == taken ? FluidStack.EMPTY
                        : tanks[tank].copyWithAmount(tanks[tank].getAmount() - taken);
            }
            return out;
        }
    }

    private TestMachines() {
    }
}
