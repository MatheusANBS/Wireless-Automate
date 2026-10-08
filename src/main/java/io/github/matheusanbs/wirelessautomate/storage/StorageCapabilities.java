package io.github.matheusanbs.wirelessautomate.storage;

import io.github.matheusanbs.wirelessautomate.network.Chemicals;
import io.github.matheusanbs.wirelessautomate.network.Sources;
import io.github.matheusanbs.wirelessautomate.registry.ModBlockEntities;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

/**
 * Capabilities dos armazenamentos do mod. Baú: a de itens do NeoForge (para todo mundo) e a
 * {@link BulkItems} (para o roteador, sem o teto de uma pilha por chamada). Tanque: a de fluido do
 * NeoForge e a {@link BulkFluids} (para o roteador, em {@code long}). Bateria: a de energia do
 * NeoForge e a {@link BulkEnergy} (para o roteador, em {@code long}, sem o teto do {@code int}). Tanque
 * Químico: a do Mekanism, só com ele ({@code compat/mekanism}, pela ponte {@code Chemicals}). Tanque de
 * Source: a {@link BulkSource} (para o roteador, em {@code long}) e a do Ars, só com ele ({@code compat/arsnouveau},
 * pela ponte {@code Sources}).
 */
public final class StorageCapabilities {
    public static void register(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, ModBlockEntities.CHEST.get(),
                (chest, side) -> chest.handler());
        event.registerBlockEntity(BulkItems.BLOCK, ModBlockEntities.CHEST.get(),
                (chest, side) -> chest.storage());
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, ModBlockEntities.TANK.get(),
                (tank, side) -> tank.handler());
        event.registerBlockEntity(BulkFluids.BLOCK, ModBlockEntities.TANK.get(),
                (tank, side) -> tank.storage());
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, ModBlockEntities.BATTERY.get(),
                (battery, side) -> battery.handler());
        event.registerBlockEntity(BulkEnergy.BLOCK, ModBlockEntities.BATTERY.get(),
                (battery, side) -> battery.store());
        event.registerBlockEntity(BulkSource.BLOCK, ModBlockEntities.SOURCE_TANK.get(),
                (tank, side) -> tank.store());
        Chemicals.registerStorage(event);
        Sources.registerStorage(event);
    }

    private StorageCapabilities() {
    }
}
