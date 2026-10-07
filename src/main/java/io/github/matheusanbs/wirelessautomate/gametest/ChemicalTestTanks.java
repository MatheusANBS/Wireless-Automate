package io.github.matheusanbs.wirelessautomate.gametest;

import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.network.Chemicals;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

/**
 * Tanques de químico de teste para os {@link ChemicalGameTests}: os tanques do Mekanism vêm com
 * todas as faces desligadas na configuração de lados (o jogador liga com a ferramenta dele), então
 * os testes usam um bloco vanilla ({@link #BLOCK}) com um tanque de verdade da API do Mekanism
 * por posição, acessível por todas as faces. Só com {@code -Dwirelessautomate.chemicalTests=true}
 * (a run {@code gameTestServerChemicals} liga) e o Mekanism presente. Nenhum tipo do Mekanism aqui:
 * o NeoForge inspeciona esta classe; o que usa a API fica em {@link ChemicalTestSupport}.
 */
@EventBusSubscriber(modid = WirelessAutomate.MODID)
public final class ChemicalTestTanks {
    public static final Block BLOCK = Blocks.LODESTONE;
    static final long CAPACITY = 64_000;
    private static final boolean ENABLED = Boolean.getBoolean("wirelessautomate.chemicalTests");

    public static boolean enabled() {
        return ENABLED && Chemicals.LOADED;
    }

    @SubscribeEvent
    static void register(RegisterCapabilitiesEvent event) {
        if (!enabled()) {
            return;
        }
        WirelessAutomate.LOGGER.info("GameTests: tanques de químico de teste ligados em {}", BLOCK);
        ChemicalTestSupport.register(event);
    }

    private ChemicalTestTanks() {
    }
}
