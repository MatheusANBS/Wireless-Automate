package io.github.matheusanbs.wirelessautomate.storage;

import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.CapabilityToken;
import net.minecraftforge.common.capabilities.RegisterCapabilitiesEvent;

/**
 * Capabilities dos armazenamentos do mod. Baú: a de itens do NeoForge (para todo mundo) e a
 * {@link BulkItems} (para o roteador, sem o teto de uma pilha por chamada). Tanque: a de fluido do
 * NeoForge e a {@link BulkFluids} (para o roteador, em {@code long}). Bateria: a de energia do
 * NeoForge e a {@link BulkEnergy} (para o roteador, em {@code long}, sem o teto do {@code int}). Tanque
 * Químico: a do Mekanism, só com ele ({@code compat/mekanism}, pela ponte {@code Chemicals}). Tanque de
 * Source: a {@link BulkSource} (para o roteador, em {@code long}) e a do Ars, só com ele ({@code compat/arsnouveau},
 * pela ponte {@code Sources}).
 *
 * <p>Porte 1.20.1 (D3): aqui só se registram os tipos das capabilities próprias ({@code Bulk*}); quem expõe
 * cada uma é o {@code getCapability} do block entity ({@link StorageBlockEntity#exposed} em cada tipo). As do
 * Mekanism são registradas por ele, e a Source do Ars 4.12 não tem capability (D5).
 */
public final class StorageCapabilities {
    // Porte 1.20.1: as capabilities próprias, lidas pelo BLOCK de cada interface. Ficam aqui, e não nas
    // interfaces, para elas carregarem sem o Forge (o verificador da JVM carregaria o CapabilityToken).
    static final Capability<BulkItems> ITEMS = CapabilityManager.get(new CapabilityToken<>() {
    });
    static final Capability<BulkFluids> FLUIDS = CapabilityManager.get(new CapabilityToken<>() {
    });
    static final Capability<BulkEnergy> ENERGY = CapabilityManager.get(new CapabilityToken<>() {
    });
    static final Capability<BulkSource> SOURCE = CapabilityManager.get(new CapabilityToken<>() {
    });

    public static void register(RegisterCapabilitiesEvent event) {
        event.register(BulkItems.class);
        event.register(BulkFluids.class);
        event.register(BulkEnergy.class);
        event.register(BulkSource.class);
    }

    private StorageCapabilities() {
    }
}
