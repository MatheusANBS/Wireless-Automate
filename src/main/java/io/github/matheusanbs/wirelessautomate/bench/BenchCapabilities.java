package io.github.matheusanbs.wirelessautomate.bench;

import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.gametest.TestCapabilityBlock;
import java.util.function.Supplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.registries.RegisterEvent;

/**
 * Máquinas de teste para o cenário misto: o vanilla não tem bloco com energia, e o caldeirão troca
 * de bloco a cada balde (o que invalida a capability e remonta a rede inteira). Também a origem de
 * 1 slot com uma pilha enorme (gaveta, bin) e o ralo de itens do cenário {@code bigstack}. Só existem com
 * {@code -Dwirelessautomate.bench=true} (o run {@code benchServer} liga), presas a blocos vanilla sem
 * capability: fonte infinita e ralo que aceita tudo, sem estado, então o custo medido é só o do mod.
 * O evento é do barramento do mod.
 *
 * <p>Porte 1.20.1 (D3): no {@code main} eram blocos vanilla com a capability presa pelo NeoForge; aqui são
 * {@link TestCapabilityBlock}s (um block entity mínimo com as propriedades do bloco vanilla de antes),
 * registrados só com a propriedade ligada. Por isso os campos viraram {@link Supplier}.
 */
@Mod.EventBusSubscriber(modid = WirelessAutomate.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class BenchCapabilities {
    private static final TestCapabilityBlock.Group MACHINES = new TestCapabilityBlock.Group("bench_machine")
            .add("fluid_source", Blocks.LAPIS_BLOCK, (cap, pos, side) -> cap == ForgeCapabilities.FLUID_HANDLER ? InfiniteWater.INSTANCE : null)
            .add("fluid_sink", Blocks.PRISMARINE_BRICKS, (cap, pos, side) -> cap == ForgeCapabilities.FLUID_HANDLER ? FluidVoid.INSTANCE : null)
            .add("energy_source", Blocks.EMERALD_BLOCK, (cap, pos, side) -> cap == ForgeCapabilities.ENERGY ? InfiniteEnergy.INSTANCE : null)
            .add("energy_sink", Blocks.DIAMOND_BLOCK, (cap, pos, side) -> cap == ForgeCapabilities.ENERGY ? EnergyVoid.INSTANCE : null)
            .add("stack_source", Blocks.GOLD_BLOCK, (cap, pos, side) -> cap == ForgeCapabilities.ITEM_HANDLER ? BigStack.INSTANCE : null)
            .add("item_sink", Blocks.IRON_BLOCK, (cap, pos, side) -> cap == ForgeCapabilities.ITEM_HANDLER ? ItemVoid.INSTANCE : null);

    public static final Supplier<Block> FLUID_SOURCE = () -> MACHINES.block("fluid_source");
    public static final Supplier<Block> FLUID_SINK = () -> MACHINES.block("fluid_sink");
    public static final Supplier<Block> ENERGY_SOURCE = () -> MACHINES.block("energy_source");
    public static final Supplier<Block> ENERGY_SINK = () -> MACHINES.block("energy_sink");
    public static final Supplier<Block> STACK_SOURCE = () -> MACHINES.block("stack_source");
    public static final Supplier<Block> ITEM_SINK = () -> MACHINES.block("item_sink");

    private static final boolean ENABLED = Boolean.getBoolean("wirelessautomate.bench");

    /**
     * O que os ralos receberam de verdade (fora as simulações), somado por tipo: a contagem neutra do
     * benchmark comparativo, a mesma para qualquer mod que entregue neles. Só a thread do servidor escreve.
     */
    private static long itemsReceived;
    private static long fluidReceived;
    private static long energyReceived;

    static long itemsReceived() {
        return itemsReceived;
    }

    static long fluidReceived() {
        return fluidReceived;
    }

    static long energyReceived() {
        return energyReceived;
    }

    public static boolean enabled() {
        return ENABLED;
    }

    @SubscribeEvent
    public static void register(RegisterEvent event) {
        if (!ENABLED) {
            return;
        }
        if (event.getRegistryKey().equals(Registries.BLOCK)) {
            WirelessAutomate.LOGGER.info("Benchmark: máquinas de teste de fluido e energia ligadas");
        }
        MACHINES.register(event);
    }

    /**
     * Um slot com uma pilha de {@link #COUNT} pedregulhos que nunca acaba, como uma gaveta ou um bin:
     * cada extração dá no máximo uma pilha normal (64), como esses mods fazem. Não aceita nada.
     */
    private enum BigStack implements IItemHandler {
        INSTANCE;

        static final int COUNT = 1_000_000;

        @Override
        public int getSlots() {
            return 1;
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            return new ItemStack(Items.COBBLESTONE, COUNT);
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            return stack;
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            int count = Math.min(amount, Items.COBBLESTONE.getMaxStackSize());
            return count <= 0 ? ItemStack.EMPTY : new ItemStack(Items.COBBLESTONE, count);
        }

        @Override
        public int getSlotLimit(int slot) {
            return Integer.MAX_VALUE;
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return false;
        }
    }

    /** Um slot vazio que aceita qualquer item e descarta. */
    private enum ItemVoid implements IItemHandler {
        INSTANCE;

        @Override
        public int getSlots() {
            return 1;
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            return ItemStack.EMPTY;
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            if (!simulate) {
                itemsReceived += stack.getCount();
            }
            return ItemStack.EMPTY;
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            return ItemStack.EMPTY;
        }

        @Override
        public int getSlotLimit(int slot) {
            return Integer.MAX_VALUE;
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return true;
        }
    }

    /** Um tanque de água que nunca acaba e não aceita nada. */
    private enum InfiniteWater implements IFluidHandler {
        INSTANCE;

        private static final int AMOUNT = 1_000_000_000;

        @Override
        public int getTanks() {
            return 1;
        }

        @Override
        public FluidStack getFluidInTank(int tank) {
            return new FluidStack(Fluids.WATER, AMOUNT);
        }

        @Override
        public int getTankCapacity(int tank) {
            return AMOUNT;
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
            return resource.getFluid() == Fluids.WATER ? resource.copy() : FluidStack.EMPTY;
        }

        @Override
        public FluidStack drain(int maxDrain, FluidAction action) {
            return maxDrain <= 0 ? FluidStack.EMPTY : new FluidStack(Fluids.WATER, maxDrain);
        }
    }

    /** Um tanque vazio que aceita qualquer quantidade e descarta. */
    private enum FluidVoid implements IFluidHandler {
        INSTANCE;

        @Override
        public int getTanks() {
            return 1;
        }

        @Override
        public FluidStack getFluidInTank(int tank) {
            return FluidStack.EMPTY;
        }

        @Override
        public int getTankCapacity(int tank) {
            return Integer.MAX_VALUE;
        }

        @Override
        public boolean isFluidValid(int tank, FluidStack stack) {
            return true;
        }

        @Override
        public int fill(FluidStack resource, FluidAction action) {
            if (action.execute()) {
                fluidReceived += resource.getAmount();
            }
            return resource.getAmount();
        }

        @Override
        public FluidStack drain(FluidStack resource, FluidAction action) {
            return FluidStack.EMPTY;
        }

        @Override
        public FluidStack drain(int maxDrain, FluidAction action) {
            return FluidStack.EMPTY;
        }
    }

    private enum InfiniteEnergy implements IEnergyStorage {
        INSTANCE;

        @Override
        public int receiveEnergy(int toReceive, boolean simulate) {
            return 0;
        }

        @Override
        public int extractEnergy(int toExtract, boolean simulate) {
            return Math.max(0, toExtract);
        }

        @Override
        public int getEnergyStored() {
            return Integer.MAX_VALUE;
        }

        @Override
        public int getMaxEnergyStored() {
            return Integer.MAX_VALUE;
        }

        @Override
        public boolean canExtract() {
            return true;
        }

        @Override
        public boolean canReceive() {
            return false;
        }
    }

    private enum EnergyVoid implements IEnergyStorage {
        INSTANCE;

        @Override
        public int receiveEnergy(int toReceive, boolean simulate) {
            int accepted = Math.max(0, toReceive);
            if (!simulate) {
                energyReceived += accepted;
            }
            return accepted;
        }

        @Override
        public int extractEnergy(int toExtract, boolean simulate) {
            return 0;
        }

        @Override
        public int getEnergyStored() {
            return 0;
        }

        @Override
        public int getMaxEnergyStored() {
            return Integer.MAX_VALUE;
        }

        @Override
        public boolean canExtract() {
            return false;
        }

        @Override
        public boolean canReceive() {
            return true;
        }
    }

    private BenchCapabilities() {
    }
}
