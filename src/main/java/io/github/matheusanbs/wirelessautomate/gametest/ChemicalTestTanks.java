package io.github.matheusanbs.wirelessautomate.gametest;

import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.network.Chemicals;
import java.util.function.Supplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.RegisterEvent;

/**
 * Tanques de químico de teste para os {@link ChemicalGameTests}: os tanques do Mekanism vêm com
 * todas as faces desligadas na configuração de lados (o jogador liga com a ferramenta dele), então
 * os testes usam um bloco de teste ({@link #BLOCK}) com tanques de verdade da API do Mekanism
 * por posição, acessíveis por todas as faces. Só com {@code -Dwirelessautomate.chemicalTests=true}
 * (a run {@code gameTestServerChemicals} liga) e o Mekanism presente. Nenhum tipo do Mekanism aqui:
 * o Forge inspeciona esta classe; o que usa a API fica em {@link ChemicalTestSupport}.
 *
 * <p>Porte 1.20.1 (D3): no {@code main} eram blocos vanilla ({@code LODESTONE} e {@code CRYING_OBSIDIAN}) com a
 * capability presa pelo NeoForge; aqui são {@link TestCapabilityBlock}s (um block entity mínimo, com as
 * propriedades desses blocos), registrados só com a propriedade ligada. Por isso os campos viraram
 * {@link Supplier}.
 */
@Mod.EventBusSubscriber(modid = WirelessAutomate.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class ChemicalTestTanks {
    private static final TestCapabilityBlock.Group TANKS = new TestCapabilityBlock.Group("test_chemical")
            .add("tank", Blocks.LODESTONE, (cap, pos, side) -> ChemicalTestSupport.handler(cap, pos))
            .add("many_tanks", Blocks.CRYING_OBSIDIAN, (cap, pos, side) -> ChemicalTestSupport.manyHandler(cap, pos));

    public static final Supplier<Block> BLOCK = () -> TANKS.block("tank");
    /** Bloco com {@link #MANY_TANKS} tanques, mais que a janela de uma visita (16). */
    public static final Supplier<Block> MANY_TANKS_BLOCK = () -> TANKS.block("many_tanks");
    static final int MANY_TANKS = 20;
    static final long CAPACITY = 64_000;
    private static final boolean ENABLED = Boolean.getBoolean("wirelessautomate.chemicalTests");

    public static boolean enabled() {
        return ENABLED && Chemicals.LOADED;
    }

    @SubscribeEvent
    public static void register(RegisterEvent event) {
        if (!enabled()) {
            return;
        }
        if (event.getRegistryKey().equals(Registries.BLOCK)) {
            WirelessAutomate.LOGGER.info("GameTests: tanques de químico de teste ligados (tank, many_tanks)");
        }
        TANKS.register(event);
    }

    private ChemicalTestTanks() {
    }
}
