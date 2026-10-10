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
 * ou um barril com upgrade de pilha), uma máquina com mais tanques que a janela de uma visita e um
 * tanque que entrega a própria pilha interna e a encolhe ao drenar (como os do Mekanism).
 * Presas a blocos vanilla sem capability, com o estado guardado por posição. Só existem com
 * {@code -Dwirelessautomate.gameTests=true} (a run {@code gameTestServer} liga).
 */
@EventBusSubscriber(modid = WirelessAutomate.MODID)
public final class TestMachines {
    /** Um slot só, que guarda até {@link BigSlot#LIMIT} itens e entrega no máximo uma pilha por extração. */
    public static final Block BIG_SLOT = Blocks.SPONGE;
    /** {@link ManyTanks#TANKS} tanques de fluido, só de saída. */
    public static final Block MANY_TANKS = Blocks.WET_SPONGE;
    /** Um tanque de saída que devolve a pilha interna em {@code getFluidInTank} ({@link LiveTank}). */
    public static final Block LIVE_TANK = Blocks.SLIME_BLOCK;
    /** {@link NaiveSlots#SLOTS} slots com limite 64 que não limitam a pilha recebida ({@link NaiveSlots}). */
    public static final Block NAIVE_SLOTS = Blocks.CLAY;

    private static final boolean ENABLED = Boolean.getBoolean("wirelessautomate.gameTests");
    private static final Map<BlockPos, BigSlot> BIG_SLOTS = new ConcurrentHashMap<>();
    private static final Map<BlockPos, ManyTanks> TANKS = new ConcurrentHashMap<>();
    private static final Map<BlockPos, LiveTank> LIVE_TANKS = new ConcurrentHashMap<>();
    private static final Map<BlockPos, NaiveSlots> NAIVE = new ConcurrentHashMap<>();

    public static boolean enabled() {
        return ENABLED;
    }

    @SubscribeEvent
    static void register(RegisterCapabilitiesEvent event) {
        if (!ENABLED) {
            return;
        }
        WirelessAutomate.LOGGER.info("GameTests: máquinas de teste ligadas em {}, {} e {}", BIG_SLOT, MANY_TANKS, LIVE_TANK);
        event.registerBlock(Capabilities.ItemHandler.BLOCK, (level, pos, state, be, side) -> bigSlot(pos), BIG_SLOT);
        event.registerBlock(Capabilities.FluidHandler.BLOCK, (level, pos, state, be, side) -> manyTanks(pos), MANY_TANKS);
        event.registerBlock(Capabilities.FluidHandler.BLOCK, (level, pos, state, be, side) -> liveTank(pos), LIVE_TANK);
        event.registerBlock(Capabilities.ItemHandler.BLOCK, (level, pos, state, be, side) -> naiveSlots(pos), NAIVE_SLOTS);
    }

    /** O slot da posição absoluta {@code pos} (criado vazio na primeira consulta). */
    static BigSlot bigSlot(BlockPos pos) {
        return BIG_SLOTS.computeIfAbsent(pos.immutable(), key -> new BigSlot());
    }

    static ManyTanks manyTanks(BlockPos pos) {
        return TANKS.computeIfAbsent(pos.immutable(), key -> new ManyTanks());
    }

    static NaiveSlots naiveSlots(BlockPos pos) {
        return NAIVE.computeIfAbsent(pos.immutable(), key -> new NaiveSlots());
    }

    static LiveTank liveTank(BlockPos pos) {
        return LIVE_TANKS.computeIfAbsent(pos.immutable(), key -> new LiveTank());
    }

    /** Esquece o estado da posição (o mundo dos testes é reaproveitado entre lotes). */
    static void reset(BlockPos pos) {
        BIG_SLOTS.remove(pos.immutable());
        TANKS.remove(pos.immutable());
        LIVE_TANKS.remove(pos.immutable());
        NAIVE.remove(pos.immutable());
    }

    static final class BigSlot implements IItemHandler {
        static final int LIMIT = 1_000_000;
        private Item item = Items.AIR;
        private int count;
        /** Chamadas de inserção de verdade que entraram algo. */
        private int inserts;

        int inserts() {
            return inserts;
        }

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
                inserts++;
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

    /**
     * Um inventário mal feito: declara limite 64 por slot, mas o {@code insertItem} aceita a pilha
     * inteira sem limitar (como uma máquina que confia em quem chama). Anota a maior pilha recebida
     * numa chamada, para o teste conferir que o roteador nunca manda mais que o tamanho do item.
     */
    static final class NaiveSlots implements IItemHandler {
        static final int SLOTS = 4;
        private final ItemStack[] stacks = new ItemStack[SLOTS];
        private int largestCall;

        NaiveSlots() {
            Arrays.fill(stacks, ItemStack.EMPTY);
        }

        int largestCall() {
            return largestCall;
        }

        int total() {
            int total = 0;
            for (ItemStack stack : stacks) {
                total += stack.getCount();
            }
            return total;
        }

        int largestSlot() {
            int largest = 0;
            for (ItemStack stack : stacks) {
                largest = Math.max(largest, stack.getCount());
            }
            return largest;
        }

        @Override
        public int getSlots() {
            return SLOTS;
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            return stacks[slot];
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            largestCall = Math.max(largestCall, stack.getCount());
            ItemStack in = stacks[slot];
            if (stack.isEmpty() || !in.isEmpty() && (!ItemStack.isSameItemSameComponents(in, stack) || in.getCount() >= 64)) {
                return stack;
            }
            if (!simulate) {
                stacks[slot] = stack.copyWithCount(in.getCount() + stack.getCount());
            }
            return ItemStack.EMPTY;
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            return ItemStack.EMPTY;
        }

        @Override
        public int getSlotLimit(int slot) {
            return 64;
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

    /**
     * Um tanque só, de saída, como o {@code BasicFluidTank} do Mekanism: {@code getFluidInTank} devolve
     * a pilha guardada (não uma cópia) e o dreno a encolhe no lugar, então quem guardou a referência
     * a vê zerar quando o tanque esvazia.
     */
    static final class LiveTank implements IFluidHandler {
        static final int CAPACITY = 1_000;
        private FluidStack stored = FluidStack.EMPTY;

        void set(FluidStack stack) {
            stored = stack.copy();
        }

        int amount() {
            return stored.getAmount();
        }

        @Override
        public int getTanks() {
            return 1;
        }

        @Override
        public FluidStack getFluidInTank(int tank) {
            return stored;
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
            return FluidStack.isSameFluidSameComponents(stored, resource) ? drain(resource.getAmount(), action) : FluidStack.EMPTY;
        }

        @Override
        public FluidStack drain(int maxDrain, FluidAction action) {
            int taken = Math.min(maxDrain, stored.getAmount());
            if (taken <= 0) {
                return FluidStack.EMPTY;
            }
            FluidStack out = stored.copyWithAmount(taken);
            if (action.execute()) {
                stored.shrink(taken);
            }
            return out;
        }
    }

    private TestMachines() {
    }
}
