package io.github.matheusanbs.wirelessautomate.resonator;

import io.github.matheusanbs.wirelessautomate.registry.ModBlocks;
import io.github.matheusanbs.wirelessautomate.storage.BulkEnergy;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

/**
 * Capabilities das Portas do Ressonador. As Portas não têm block entity: a capability acha o Núcleo pelo índice
 * {@link ResonatorPorts} (sem Núcleo montado e carregado, nenhuma). Porta de Energia: a de energia do NeoForge e a
 * {@link BulkEnergy} (para o roteador, em {@code long}), as duas só tirando. Porta de Cristais: a de itens do
 * NeoForge, para pôr cristais e redstone e tirar o Opaco. A visão da Porta de Energia guarda o índice dela (achado
 * uma vez, na criação da visão, que o cache de capability guarda); a montagem invalida as visões se a lista muda.
 */
public final class ResonatorCapabilities {
    private ResonatorCapabilities() {
    }

    public static void register(RegisterCapabilitiesEvent event) {
        event.registerBlock(Capabilities.EnergyStorage.BLOCK, (level, pos, state, entity, side) -> {
            ResonanceCoreBlockEntity core = ResonatorPorts.core(level, pos);
            return core == null ? null : new ResonatorPorts.CoreEnergyHandler(core, core.flow().indexOf(pos));
        }, ModBlocks.RESONATOR_ENERGY_PORT.get());
        event.registerBlock(BulkEnergy.BLOCK, (level, pos, state, entity, side) -> {
            ResonanceCoreBlockEntity core = ResonatorPorts.core(level, pos);
            return core == null ? null : new ResonatorPorts.CoreEnergy(core, core.flow().indexOf(pos));
        }, ModBlocks.RESONATOR_ENERGY_PORT.get());
        event.registerBlock(Capabilities.ItemHandler.BLOCK, (level, pos, state, entity, side) -> {
            ResonanceCoreBlockEntity core = ResonatorPorts.core(level, pos);
            return core == null ? null : new ResonatorPorts.CoreItems(core);
        }, ModBlocks.RESONATOR_CRYSTAL_PORT.get());
    }
}
