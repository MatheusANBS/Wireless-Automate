package io.github.matheusanbs.wirelessautomate.storage;

import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import net.minecraft.core.Direction;
import net.neoforged.neoforge.capabilities.BlockCapability;
import org.jetbrains.annotations.Nullable;

/**
 * Energia em {@code long}, sem o teto de {@link Integer#MAX_VALUE} FE por chamada do
 * {@link net.neoforged.neoforge.energy.IEnergyStorage}. É a capability que o roteador procura antes
 * da de energia do NeoForge: entre duas Baterias do mod, bilhões de FE passam numa chamada.
 */
public interface BulkEnergy {
    BlockCapability<BulkEnergy, @Nullable Direction> BLOCK =
            BlockCapability.createSided(WirelessAutomate.id("bulk_energy"), BulkEnergy.class);

    /** Guarda até {@code amount}; devolve quanto coube. */
    long insert(long amount, boolean simulate);

    /** Tira até {@code amount}; devolve quanto saiu. */
    long extract(long amount, boolean simulate);
}
