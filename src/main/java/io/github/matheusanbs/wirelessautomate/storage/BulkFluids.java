package io.github.matheusanbs.wirelessautomate.storage;

import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import net.minecraft.core.Direction;
import net.neoforged.neoforge.capabilities.BlockCapability;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;

/**
 * Fluidos por tipo e quantidade {@code long} (mB), sem o teto de {@link Integer#MAX_VALUE} mB por
 * chamada do {@link net.neoforged.neoforge.fluids.capability.IFluidHandler}. É a capability que o
 * roteador procura antes da de fluido do NeoForge: entre dois Tanques do mod, bilhões de mB passam
 * numa chamada. As chaves têm 1 mB e não podem ser alteradas.
 */
public interface BulkFluids {
    BlockCapability<BulkFluids, @Nullable Direction> BLOCK =
            BlockCapability.createSided(WirelessAutomate.id("bulk_fluids"), BulkFluids.class);

    int types();

    FluidStack key(int index);

    long count(int index);

    /** Quanto há do fluido com os mesmos componentes da chave. */
    long count(FluidStack key);

    /** Quanto há do fluido, com quaisquer componentes. */
    long countFluid(FluidStack key);

    long insert(FluidStack key, long amount, boolean simulate);

    long extract(FluidStack key, long amount, boolean simulate);
}
