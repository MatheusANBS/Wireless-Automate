package io.github.matheusanbs.wirelessautomate.gametest;

import io.github.matheusanbs.wirelessautomate.Config;
import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.block.RouterBlock;
import io.github.matheusanbs.wirelessautomate.block.RouterBlockEntity;
import io.github.matheusanbs.wirelessautomate.block.RouterTier;
import io.github.matheusanbs.wirelessautomate.network.NetworkManager;
import io.github.matheusanbs.wirelessautomate.network.ResourceType;
import io.github.matheusanbs.wirelessautomate.registry.ModBlocks;
import io.github.matheusanbs.wirelessautomate.storage.StorageKind;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * Testes dentro do jogo. Rodam com {@code ./gradlew runGameTestServer} ou com {@code /test runall}
 * num mundo de dev. A estrutura {@code empty} fica em data/wirelessautomate/structures/empty.nbt.
 */
@GameTestHolder(WirelessAutomate.MODID)
@PrefixGameTestTemplate(false)
public final class RouterGameTests {
    private static final BlockPos MACHINE = new BlockPos(1, 1, 1);
    private static final BlockPos ROUTER = MACHINE.above();

    @GameTest(template = "empty")
    public static void routerRegistersWithManager(GameTestHelper helper) {
        helper.setBlock(MACHINE, Blocks.CHEST);
        helper.setBlock(ROUTER, ModBlocks.ROUTER.get().defaultBlockState().setValue(RouterBlock.FACING, Direction.UP));
        RouterBlockEntity node = GameTestCompat.getBlockEntity(helper, ROUTER);

        // onLoad de block entities recém-colocados roda no tick seguinte, por isso a espera.
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(NetworkManager.get().contains(node),
                        "roteador não entrou no gerenciador"))
                .thenExecute(() -> helper.setBlock(ROUTER, Blocks.AIR))
                .thenExecute(() -> helper.assertTrue(!NetworkManager.get().contains(node),
                        "roteador não saiu do gerenciador"))
                .thenSucceed();
    }

    @GameTest(template = "empty")
    public static void tierCoreJumpsToAnyHigherTier(GameTestHelper helper) {
        helper.setBlock(MACHINE, Blocks.FURNACE);
        helper.setBlock(ROUTER, ModBlocks.ROUTER.get().defaultBlockState().setValue(RouterBlock.FACING, Direction.UP));
        BlockPos router = helper.absolutePos(ROUTER);

        helper.assertTrue(RouterBlock.tryUpgrade(helper.getLevel(), router, RouterTier.ELITE), "Básico não pulou para o Elite");
        helper.assertBlockProperty(ROUTER, RouterBlock.TIER, RouterTier.ELITE);
        helper.assertFalse(RouterBlock.tryUpgrade(helper.getLevel(), router, RouterTier.ADVANCED), "desceu de tier");
        helper.assertFalse(RouterBlock.tryUpgrade(helper.getLevel(), router, RouterTier.ELITE), "reaplicou o mesmo tier");
        helper.assertBlockProperty(ROUTER, RouterBlock.TIER, RouterTier.ELITE);
        helper.assertTrue(RouterBlock.tryUpgrade(helper.getLevel(), router, RouterTier.ULTIMATE), "Elite não pulou para o Ultimate");
        helper.assertBlockProperty(ROUTER, RouterBlock.TIER, RouterTier.ULTIMATE);
        helper.succeed();
    }

    /** Sem o Allthemodium, a escada é Elite → Esmeralda → Ultimate; os tiers do ATM ficam de fora. */
    @GameTest(template = "empty")
    public static void withoutAllthemodiumEmeraldGoesToUltimate(GameTestHelper helper) {
        helper.setBlock(MACHINE, Blocks.FURNACE);
        helper.setBlock(ROUTER, ModBlocks.ROUTER.get().defaultBlockState().setValue(RouterBlock.FACING, Direction.UP)
                .setValue(RouterBlock.TIER, RouterTier.ELITE));
        BlockPos router = helper.absolutePos(ROUTER);

        GameTestCompat.assertValueEqual(helper, RouterTier.ELITE.next(), RouterTier.EMERALD, "depois do Elite");
        GameTestCompat.assertValueEqual(helper, RouterTier.EMERALD.next(), RouterTier.ULTIMATE, "depois da Esmeralda");
        GameTestCompat.assertValueEqual(helper, RouterTier.ULTIMATE.previous(), RouterTier.EMERALD, "antes do Ultimate");
        GameTestCompat.assertValueEqual(helper, RouterTier.VIBRANIUM.next(), RouterTier.ULTIMATE, "bloco que ficou no Vibranium");
        helper.assertFalse(RouterTier.ALLTHEMODIUM.loaded(), "Allthemodium carregado sem o mod");
        helper.assertTrue(RouterTier.EMERALD.loaded(), "Esmeralda não carregada");

        helper.assertTrue(RouterBlock.tryUpgrade(helper.getLevel(), router, RouterTier.EMERALD), "Elite não subiu");
        helper.assertFalse(RouterBlock.tryUpgrade(helper.getLevel(), router, RouterTier.ALLTHEMODIUM),
                "subiu para um tier do ATM sem o mod");
        helper.assertTrue(RouterBlock.tryUpgrade(helper.getLevel(), router, RouterTier.ULTIMATE),
                "Esmeralda não subiu para o Ultimate");
        helper.assertBlockProperty(ROUTER, RouterBlock.TIER, RouterTier.ULTIMATE);

        // Entre dimensões começa na Esmeralda; o Elite fica na dimensão dele.
        helper.assertTrue(Config.TIERS.get(RouterTier.EMERALD).crossDimension().get(), "Esmeralda sem entre dimensões");
        helper.assertFalse(Config.TIERS.get(RouterTier.ELITE).crossDimension().get(), "Elite entre dimensões");
        helper.succeed();
    }

    /**
     * Config de um mundo de antes da Esmeralda: os valores no padrão antigo passam para o novo uma vez, e o
     * que o dono mudou fica. Tudo síncrono, restaurado no finally.
     */
    @GameTest(template = "empty")
    public static void configMigratesOldDefaults(GameTestHelper helper) {
        var elite = Config.TIERS.get(RouterTier.ELITE).rates().get("itemsPerSecond");
        var advanced = Config.TIERS.get(RouterTier.ADVANCED).rates().get("itemsPerSecond");
        var basicRange = Config.TIERS.get(RouterTier.BASIC).range();
        var chest = Config.STORAGE_CAPACITY.get(StorageKind.CHEST).get(RouterTier.ELITE);
        try {
            elite.set(131_072L);
            advanced.set(9_999L);
            basicRange.set(128);
            chest.set(1_073_741_824L);
            Config.BALANCE_VERSION.set(0);
            helper.assertTrue(Config.migrateBalance() >= 3, "nada migrou");
            GameTestCompat.assertValueEqual(helper, elite.get(), 2_048L, "Elite itens/s");
            GameTestCompat.assertValueEqual(helper, advanced.get(), 9_999L, "valor do dono mudou");
            GameTestCompat.assertValueEqual(helper, basicRange.get(), 64, "alcance do Básico");
            GameTestCompat.assertValueEqual(helper, chest.get(), 2_097_152L, "Baú Elite");
            GameTestCompat.assertValueEqual(helper, Config.BALANCE_VERSION.get(), Config.CURRENT_BALANCE, "versão");
            elite.set(131_072L);
            GameTestCompat.assertValueEqual(helper, Config.migrateBalance(), 0, "migrou duas vezes");
        } finally {
            elite.set(ResourceType.ITEM.defaultRate(RouterTier.ELITE.ordinal()));
            advanced.set(ResourceType.ITEM.defaultRate(RouterTier.ADVANCED.ordinal()));
            basicRange.set(RouterTier.BASIC.defaultRange);
            chest.set(StorageKind.CHEST.defaultCapacity(RouterTier.ELITE));
            Config.BALANCE_VERSION.set(Config.CURRENT_BALANCE);
            Config.SPEC.save();
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void routerBreaksWithoutMachine(GameTestHelper helper) {
        helper.setBlock(MACHINE, Blocks.FURNACE);
        helper.setBlock(ROUTER, ModBlocks.ROUTER.get().defaultBlockState().setValue(RouterBlock.FACING, Direction.UP));
        helper.setBlock(MACHINE, Blocks.AIR);
        helper.assertBlockNotPresent(ModBlocks.ROUTER.get(), ROUTER);
        helper.succeed();
    }
}
