package io.github.matheusanbs.wirelessautomate.storage;

import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import net.minecraft.core.Direction;
import net.neoforged.neoforge.capabilities.BlockCapability;
import org.jetbrains.annotations.Nullable;

/**
 * Source em {@code long}, sem o teto de {@link Integer#MAX_VALUE} por chamada da capability do Ars. É a
 * que o roteador procura antes da do Ars: entre dois Tanques de Source, bilhões passam numa chamada.
 * Não usa tipos do Ars.
 */
public interface BulkSource {
    BlockCapability<BulkSource, @Nullable Direction> BLOCK =
            BlockCapability.createSided(WirelessAutomate.id("bulk_source"), BulkSource.class);

    /** Guarda até {@code amount}; devolve quanto coube. */
    long insert(long amount, boolean simulate);

    /** Tira até {@code amount}; devolve quanto saiu. */
    long extract(long amount, boolean simulate);
}
