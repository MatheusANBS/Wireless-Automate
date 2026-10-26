package io.github.matheusanbs.wirelessautomate.repairer;

import io.github.matheusanbs.wirelessautomate.registry.ModBlockEntities;
import io.github.matheusanbs.wirelessautomate.storage.BulkEnergy;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

/** As capabilities do Reparador: itens por slot, e energia só de entrada (a do NeoForge e a {@link BulkEnergy} do roteador). */
public final class RepairerCapabilities {
    public static void register(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, ModBlockEntities.CRYSTAL_REPAIRER.get(),
                (repairer, side) -> repairer.itemView());
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, ModBlockEntities.CRYSTAL_REPAIRER.get(),
                (repairer, side) -> repairer.energyView());
        event.registerBlockEntity(BulkEnergy.BLOCK, ModBlockEntities.CRYSTAL_REPAIRER.get(),
                (repairer, side) -> repairer.bulkEnergyView());
    }

    private RepairerCapabilities() {
    }
}
