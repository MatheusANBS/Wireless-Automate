package io.github.matheusanbs.wirelessautomate.gametest;

import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.block.RouterBlock;
import io.github.matheusanbs.wirelessautomate.block.RouterBlockEntity;
import io.github.matheusanbs.wirelessautomate.block.RouterTier;
import io.github.matheusanbs.wirelessautomate.network.NetworkManager;
import io.github.matheusanbs.wirelessautomate.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Testes dentro do jogo. Rodam com {@code ./gradlew runGameTestServer} ou com {@code /test runall}
 * num mundo de dev. A estrutura {@code empty} fica em data/wirelessautomate/structure/empty.nbt.
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
        RouterBlockEntity node = helper.getBlockEntity(ROUTER);

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
    public static void tierCoreUpgradesOneTierAtATime(GameTestHelper helper) {
        helper.setBlock(MACHINE, Blocks.FURNACE);
        helper.setBlock(ROUTER, ModBlocks.ROUTER.get().defaultBlockState().setValue(RouterBlock.FACING, Direction.UP));
        BlockPos router = helper.absolutePos(ROUTER);

        helper.assertTrue(!RouterBlock.tryUpgrade(helper.getLevel(), router, RouterTier.ELITE), "pulou um tier");
        helper.assertTrue(RouterBlock.tryUpgrade(helper.getLevel(), router, RouterTier.ADVANCED), "não subiu de tier");
        helper.assertBlockProperty(ROUTER, RouterBlock.TIER, RouterTier.ADVANCED);
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
