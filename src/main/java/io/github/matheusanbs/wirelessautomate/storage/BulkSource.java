package io.github.matheusanbs.wirelessautomate.storage;

import net.minecraftforge.common.capabilities.Capability;

/**
 * Source em {@code long}, sem o teto de {@link Integer#MAX_VALUE} por chamada da capability do Ars. É a
 * que o roteador procura antes da do Ars: entre dois Tanques de Source, bilhões passam numa chamada.
 * Não usa tipos do Ars.
 */
public interface BulkSource {
    /**
     * A capability (no Forge 1.20.1, pelo tipo; registrada no {@code RegisterCapabilitiesEvent}). Criada no
     * {@link StorageCapabilities}: assim esta interface não depende do Forge para carregar (o JUnit do
     * {@code ScalarStore} roda sem ele).
     */
    Capability<BulkSource> BLOCK = StorageCapabilities.SOURCE;

    /** Guarda até {@code amount}; devolve quanto coube. */
    long insert(long amount, boolean simulate);

    /** Tira até {@code amount}; devolve quanto saiu. */
    long extract(long amount, boolean simulate);
}
