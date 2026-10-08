package io.github.matheusanbs.wirelessautomate.storage;

import io.github.matheusanbs.wirelessautomate.registry.ModBlockEntities;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

/** Capabilities do Baú: a de itens do NeoForge (para todo mundo) e a {@link BulkItems} (para o roteador). */
public final class StorageCapabilities {
    public static void register(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, ModBlockEntities.CHEST.get(),
                (chest, side) -> chest.handler());
        event.registerBlockEntity(BulkItems.BLOCK, ModBlockEntities.CHEST.get(),
                (chest, side) -> chest.storage());
    }

    private StorageCapabilities() {
    }
}
