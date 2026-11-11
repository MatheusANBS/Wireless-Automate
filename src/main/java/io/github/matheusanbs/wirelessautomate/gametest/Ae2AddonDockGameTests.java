package io.github.matheusanbs.wirelessautomate.gametest;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * A Doca com os addons do AE2 do ATM10, na run {@code runGameTestServerAe2}: o Extended Pattern Provider do ExtendedAE
 * (a lógica do provedor do AE2), o Advanced Pattern Provider do Advanced AE (lógica própria, que tenta o
 * {@code ICraftingMachine} antes de tudo) e a CPU quântica do Advanced AE (um Quantum Core sozinho já forma o
 * multibloco, ligado à rede por cima e por baixo), que recebe o resultado pela inserção na rede por um mixin no
 * {@code CraftingService.insertIntoCpus}. Cada teste é um pedido de verdade pela CPU ({@link Ae2DockGameTests#orderEndsInNetwork}), com o retorno do provedor
 * vazio o tempo todo. Sem o addon na pasta mods, o teste passa sem fazer nada. Os blocos são lidos pelo id: nenhum
 * tipo dos addons no código.
 */
@GameTestHolder(Ae2DockGameTests.NAMESPACE)
@PrefixGameTestTemplate(false)
public final class Ae2AddonDockGameTests {
    private Ae2AddonDockGameTests() {
    }

    private static boolean enabled(String mod) {
        return Ae2DockGameTests.enabled() && ModList.get().isLoaded(mod);
    }

    private static Block block(String id) {
        return BuiltInRegistries.BLOCK.get(ResourceLocation.parse(id));
    }

    /** A run do AE2 carrega os dois addons, com os blocos que os testes usam. */
    @GameTest(template = "empty")
    public static void addonsAreLoaded(GameTestHelper helper) {
        if (!Ae2DockGameTests.enabled()) {
            helper.succeed();
            return;
        }
        helper.assertTrue(ModList.get().isLoaded("extendedae"), "o ExtendedAE não carregou na run do AE2");
        helper.assertTrue(ModList.get().isLoaded("advanced_ae"), "o Advanced AE não carregou na run do AE2");
        for (String id : new String[] {"extendedae:ex_pattern_provider", "advanced_ae:adv_pattern_provider",
                "advanced_ae:quantum_core"}) {
            helper.assertTrue(block(id) != Blocks.AIR, "sem " + id + " no registro");
        }
        helper.succeed();
    }

    /** O Extended Pattern Provider (36 padrões, a lógica do AE2) entrega à Doca e o resultado vai para a rede. */
    @GameTest(template = "empty", timeoutTicks = 300)
    public static void extendedProviderDelivers(GameTestHelper helper) {
        if (!enabled("extendedae")) {
            helper.succeed();
            return;
        }
        Ae2DockGameTests.orderEndsInNetwork(helper, block("extendedae:ex_pattern_provider"),
                Ae2DockGameTests.ae2("1k_crafting_storage"), 3, "CraftingCPU");
    }

    /** O Advanced Pattern Provider (lógica própria) entrega à Doca e o resultado vai para a rede. */
    @GameTest(template = "empty", timeoutTicks = 300)
    public static void advancedProviderDelivers(GameTestHelper helper) {
        if (!enabled("advanced_ae")) {
            helper.succeed();
            return;
        }
        Ae2DockGameTests.orderEndsInNetwork(helper, block("advanced_ae:adv_pattern_provider"),
                Ae2DockGameTests.ae2("1k_crafting_storage"), 3, "CraftingCPU");
    }

    /**
     * A CPU quântica do Advanced AE (um Quantum Core, a única CPU da rede) conta o resultado que a Doca insere direto na
     * rede, e o pedido termina. O Quantum Core sozinho só se liga à rede por cima e por baixo: fica em cima da energia,
     * e uma ME Interface liga o provedor a ela.
     */
    @GameTest(template = "empty", timeoutTicks = 300)
    public static void quantumCpuCountsTheDirectReturn(GameTestHelper helper) {
        if (!enabled("advanced_ae")) {
            helper.succeed();
            return;
        }
        Ae2DockGameTests.orderEndsInNetwork(helper, Ae2DockGameTests.ae2("pattern_provider"),
                Ae2DockGameTests.ae2("interface"), 3, "AdvCraftingCPU");
        helper.setBlock(Ae2DockGameTests.ENERGY.above(), block("advanced_ae:quantum_core"));
    }
}
