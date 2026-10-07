package io.github.matheusanbs.wirelessautomate.bench;

import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

/**
 * Máquinas de teste para o cenário misto: o vanilla não tem bloco com energia, e o caldeirão troca
 * de bloco a cada balde (o que invalida a capability e remonta a rede inteira). Só existem com
 * {@code -Dwirelessautomate.bench=true} (o run {@code benchServer} liga), presas a blocos vanilla sem
 * capability: fonte infinita e ralo que aceita tudo, sem estado, então o custo medido é só o do mod.
 * O evento é do barramento do mod; o {@code @EventBusSubscriber} descobre o barramento pelo tipo.
 */
@EventBusSubscriber(modid = WirelessAutomate.MODID)
public final class BenchCapabilities {
    public static final Block FLUID_SOURCE = Blocks.LAPIS_BLOCK;
    public static final Block FLUID_SINK = Blocks.PRISMARINE_BRICKS;
    public static final Block ENERGY_SOURCE = Blocks.EMERALD_BLOCK;
    public static final Block ENERGY_SINK = Blocks.DIAMOND_BLOCK;

    private static final boolean ENABLED = Boolean.getBoolean("wirelessautomate.bench");

    public static boolean enabled() {
        return ENABLED;
    }

    @SubscribeEvent
    static void register(RegisterCapabilitiesEvent event) {
        if (!ENABLED) {
            return;
        }
        WirelessAutomate.LOGGER.info("Benchmark: máquinas de teste de fluido e energia ligadas");
        event.registerBlock(Capabilities.FluidHandler.BLOCK, (level, pos, state, be, side) -> InfiniteWater.INSTANCE, FLUID_SOURCE);
        event.registerBlock(Capabilities.FluidHandler.BLOCK, (level, pos, state, be, side) -> FluidVoid.INSTANCE, FLUID_SINK);
        event.registerBlock(Capabilities.EnergyStorage.BLOCK, (level, pos, state, be, side) -> InfiniteEnergy.INSTANCE, ENERGY_SOURCE);
        event.registerBlock(Capabilities.EnergyStorage.BLOCK, (level, pos, state, be, side) -> EnergyVoid.INSTANCE, ENERGY_SINK);
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
            return resource.is(Fluids.WATER) ? resource.copy() : FluidStack.EMPTY;
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
            return Math.max(0, toReceive);
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
