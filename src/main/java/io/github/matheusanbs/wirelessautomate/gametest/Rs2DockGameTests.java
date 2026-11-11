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
 * A Doca com o Refined Storage 2 de verdade. Rodam só na run {@code runGameTestServerRefined}, que tem o RS2 na pasta
 * mods e liga o namespace {@value #NAMESPACE}. Nenhum tipo do RS2 nas assinaturas: o que usa a API fica em
 * {@code Rs2TestSupport}.
 */
@GameTestHolder(Rs2DockGameTests.NAMESPACE)
@PrefixGameTestTemplate(false)
public final class Rs2DockGameTests {
    static final String NAMESPACE = "wirelessautomate_refined";

    private Rs2DockGameTests() {
    }

    private static Block rs2(String path) {
        return BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath("refinedstorage", path));
    }

    /** A run do RS2 carrega o RS2, com o Autocrafter no registro. */
    @GameTest(template = "empty")
    public static void rs2IsLoaded(GameTestHelper helper) {
        if (!Boolean.getBoolean("wirelessautomate.refinedTests")) {
            helper.succeed();
            return;
        }
        helper.assertTrue(ModList.get().isLoaded("refinedstorage"), "o RS2 não carregou na run do RS2");
        helper.assertTrue(rs2("autocrafter") != Blocks.AIR, "sem o Autocrafter no registro");
        helper.succeed();
    }
}
