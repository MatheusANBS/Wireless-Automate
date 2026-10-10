package io.github.matheusanbs.wirelessautomate.storage;

import net.minecraftforge.common.capabilities.Capability;

/**
 * Energia em {@code long}, sem o teto de {@link Integer#MAX_VALUE} FE por chamada do
 * {@link net.minecraftforge.energy.IEnergyStorage}. É a capability que o roteador procura antes
 * da de energia do Forge: entre duas Baterias do mod, bilhões de FE passam numa chamada.
 */
public interface BulkEnergy {
    /**
     * A capability (no Forge 1.20.1, pelo tipo; registrada no {@code RegisterCapabilitiesEvent}). Criada no
     * {@link StorageCapabilities}: assim esta interface não depende do Forge para carregar (o JUnit do
     * {@code ScalarStore} roda sem ele).
     */
    Capability<BulkEnergy> BLOCK = StorageCapabilities.ENERGY;

    /** Guarda até {@code amount}; devolve quanto coube. */
    long insert(long amount, boolean simulate);

    /** Tira até {@code amount}; devolve quanto saiu. */
    long extract(long amount, boolean simulate);
}
