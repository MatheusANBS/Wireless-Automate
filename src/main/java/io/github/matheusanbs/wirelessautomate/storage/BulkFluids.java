package io.github.matheusanbs.wirelessautomate.storage;

import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.fluids.FluidStack;

/**
 * Fluidos por tipo e quantidade {@code long} (mB), sem o teto de {@link Integer#MAX_VALUE} mB por
 * chamada do {@link net.minecraftforge.fluids.capability.IFluidHandler}. É a capability que o
 * roteador procura antes da de fluido do Forge: entre dois Tanques do mod, bilhões de mB passam
 * numa chamada. As chaves têm 1 mB e não podem ser alteradas.
 */
public interface BulkFluids {
    /**
     * A capability (no Forge 1.20.1, pelo tipo; registrada no {@code RegisterCapabilitiesEvent}). Criada no
     * {@link StorageCapabilities}: assim esta interface não depende do Forge para carregar (o JUnit do
     * {@code ScalarStore} roda sem ele).
     */
    Capability<BulkFluids> BLOCK = StorageCapabilities.FLUIDS;

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
