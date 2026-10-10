package io.github.matheusanbs.wirelessautomate.gametest;

import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.templates.FluidTank;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.registries.RegisterEvent;

/**
 * Máquinas de teste dos GameTests que o vanilla não tem: um slot com pilha enorme (como uma gaveta
 * ou um barril com upgrade de pilha), uma máquina com mais tanques que a janela de uma visita e um
 * tanque que entrega a própria pilha interna e a encolhe ao drenar (como os do Mekanism).
 * Com o estado guardado por posição. Só existem com {@code -Dwirelessautomate.gameTests=true} (a run
 * {@code gameTestServer} liga).
 *
 * <p>Porte 1.20.1 (D3): no {@code main} eram blocos vanilla com a capability presa pelo NeoForge; aqui são
 * {@link TestCapabilityBlock}s (um block entity mínimo, com as propriedades do bloco vanilla de antes),
 * registrados só com a propriedade ligada. Por isso os campos viraram {@link Supplier}.
 */
@Mod.EventBusSubscriber(modid = WirelessAutomate.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class TestMachines {
    private static final TestCapabilityBlock.Group MACHINES = new TestCapabilityBlock.Group("test_machine")
            .add("big_slot", Blocks.SPONGE, (cap, pos, side) -> cap == ForgeCapabilities.ITEM_HANDLER ? bigSlot(pos) : null)
            .add("many_tanks", Blocks.WET_SPONGE,
                    (cap, pos, side) -> cap == ForgeCapabilities.FLUID_HANDLER ? manyTanks(pos) : null)
            .add("live_tank", Blocks.SLIME_BLOCK, (cap, pos, side) -> cap == ForgeCapabilities.FLUID_HANDLER ? liveTank(pos) : null)
            .add("naive_slots", Blocks.CLAY, (cap, pos, side) -> cap == ForgeCapabilities.ITEM_HANDLER ? naiveSlots(pos) : null)
            .add("switch_tank", Blocks.IRON_BLOCK,
                    (cap, pos, side) -> cap == ForgeCapabilities.FLUID_HANDLER && switchedOn(pos) ? switchTank(pos) : null);

    /** Um slot só, que guarda até {@link BigSlot#LIMIT} itens e entrega no máximo uma pilha por extração. */
    public static final Supplier<Block> BIG_SLOT = () -> MACHINES.block("big_slot");
    /** {@link ManyTanks#TANKS} tanques de fluido, só de saída. */
    public static final Supplier<Block> MANY_TANKS = () -> MACHINES.block("many_tanks");
    /** Um tanque de saída que devolve a pilha interna em {@code getFluidInTank} ({@link LiveTank}). */
    public static final Supplier<Block> LIVE_TANK = () -> MACHINES.block("live_tank");
    /** {@link NaiveSlots#SLOTS} slots com limite 64 que não limitam a pilha recebida ({@link NaiveSlots}). */
    public static final Supplier<Block> NAIVE_SLOTS = () -> MACHINES.block("naive_slots");
    /**
     * Porte 1.20.1: um tanque de {@link #SWITCH_TANK_CAPACITY} mB que só oferece a capability de fluido quando
     * ligado ({@link #switchOn}), sem trocar de block entity: o caso de uma máquina cujo lado passa de "nenhum" para
     * entrada (a configuração de lados do Mekanism), que o cache negativo do roteador precisa ver.
     */
    public static final Supplier<Block> SWITCH_TANK = () -> MACHINES.block("switch_tank");
    static final int SWITCH_TANK_CAPACITY = 1_000;

    private static final boolean ENABLED = Boolean.getBoolean("wirelessautomate.gameTests");
    private static final Map<BlockPos, BigSlot> BIG_SLOTS = new ConcurrentHashMap<>();
    private static final Map<BlockPos, ManyTanks> TANKS = new ConcurrentHashMap<>();
    private static final Map<BlockPos, LiveTank> LIVE_TANKS = new ConcurrentHashMap<>();
    private static final Map<BlockPos, NaiveSlots> NAIVE = new ConcurrentHashMap<>();
    private static final Map<BlockPos, FluidTank> SWITCH_TANKS = new ConcurrentHashMap<>();
    private static final Set<BlockPos> SWITCHED_ON = ConcurrentHashMap.newKeySet();

    public static boolean enabled() {
        return ENABLED;
    }

    @SubscribeEvent
    public static void register(RegisterEvent event) {
        if (!ENABLED) {
            return;
        }
        if (event.getRegistryKey().equals(Registries.BLOCK)) {
            WirelessAutomate.LOGGER.info("GameTests: máquinas de teste ligadas (big_slot, many_tanks, live_tank, naive_slots, switch_tank)");
        }
        MACHINES.register(event);
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

    static FluidTank switchTank(BlockPos pos) {
        return SWITCH_TANKS.computeIfAbsent(pos.immutable(), key -> new FluidTank(SWITCH_TANK_CAPACITY));
    }

    /** Liga a capability do tanque de {@code pos} sem avisar ninguém (o mod que não chama {@code neighborChanged}). */
    static void switchOnSilently(BlockPos pos) {
        SWITCHED_ON.add(pos.immutable());
    }

    static boolean switchedOn(BlockPos pos) {
        return SWITCHED_ON.contains(pos);
    }

    /**
     * Liga a capability do tanque de {@code pos} (posição absoluta) sem trocar de block entity e avisa como uma
     * máquina real: o Mekanism 10.4, ao mudar o lado ({@code TileComponentConfig.sideChanged}), invalida a capability
     * do lado e chama {@code WorldUtils.notifyNeighborOfChange}, que dá {@code onNeighborChange} e
     * {@code neighborChanged} ao vizinho daquele lado. Aqui não há {@code LazyOptional} velho para invalidar (o lado
     * não oferecia nada), então só o aviso aos vizinhos ({@code updateNeighborsAt}, o {@code neighborChanged}).
     */
    static void switchOn(ServerLevel level, BlockPos pos) {
        SWITCHED_ON.add(pos.immutable());
        level.updateNeighborsAt(pos, level.getBlockState(pos).getBlock());
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
        SWITCH_TANKS.remove(pos.immutable());
        SWITCHED_ON.remove(pos.immutable());
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
            int taken = Math.min(Math.min(amount, count), item.getMaxStackSize());
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
            if (stack.isEmpty() || !in.isEmpty() && (!ItemStack.isSameItemSameTags(in, stack) || in.getCount() >= 64)) {
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
                if (!tanks[tank].isEmpty() && tanks[tank].isFluidEqual(resource)) {
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
            FluidStack out = new FluidStack(tanks[tank], taken);
            if (action.execute()) {
                tanks[tank] = tanks[tank].getAmount() == taken ? FluidStack.EMPTY
                        : new FluidStack(tanks[tank], tanks[tank].getAmount() - taken);
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
            return stored.isFluidEqual(resource) ? drain(resource.getAmount(), action) : FluidStack.EMPTY;
        }

        @Override
        public FluidStack drain(int maxDrain, FluidAction action) {
            int taken = Math.min(maxDrain, stored.getAmount());
            if (taken <= 0) {
                return FluidStack.EMPTY;
            }
            FluidStack out = new FluidStack(stored, taken);
            if (action.execute()) {
                stored.shrink(taken);
            }
            return out;
        }
    }

    private TestMachines() {
    }
}
