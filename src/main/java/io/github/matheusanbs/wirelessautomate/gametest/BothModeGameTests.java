package io.github.matheusanbs.wirelessautomate.gametest;

import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.block.RouterBlock;
import io.github.matheusanbs.wirelessautomate.block.RouterBlockEntity;
import io.github.matheusanbs.wirelessautomate.network.NetworkManager;
import io.github.matheusanbs.wirelessautomate.network.NetworkSavedData;
import io.github.matheusanbs.wirelessautomate.network.PortMode;
import io.github.matheusanbs.wirelessautomate.network.ResourceType;
import io.github.matheusanbs.wirelessautomate.registry.ModBlocks;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * Modo Ambos: uma face Ambos não entrega para outra face Ambos; extrai para faces que só inserem e
 * recebe de faces que só extraem. Baús em y=1 com o roteador em cima (facing=UP).
 */
@GameTestHolder(WirelessAutomate.MODID)
@PrefixGameTestTemplate(false)
public final class BothModeGameTests {
    private static final BlockPos A = new BlockPos(0, 1, 0);
    private static final BlockPos B = new BlockPos(2, 1, 2);
    private static final BlockPos C = new BlockPos(2, 1, 0);

    private static UUID newNetwork(GameTestHelper helper, String name) {
        return NetworkSavedData.get(helper.getLevel().getServer()).create(UUID.randomUUID(), name).id();
    }

    private static RouterBlockEntity chest(GameTestHelper helper, BlockPos pos, UUID network, PortMode mode) {
        helper.setBlock(pos, Blocks.CHEST);
        BlockPos routerPos = pos.above();
        helper.setBlock(routerPos, ModBlocks.ROUTER.get().defaultBlockState().setValue(RouterBlock.FACING, Direction.UP));
        RouterBlockEntity router = GameTestCompat.getBlockEntity(helper, routerPos);
        router.setNetworkId(network);
        router.setMode(ResourceType.ITEM, Direction.UP, mode);
        return router;
    }

    private static int diamonds(GameTestHelper helper, BlockPos pos) {
        ChestBlockEntity chest = GameTestCompat.getBlockEntity(helper, pos);
        int total = 0;
        for (int i = 0; i < chest.getContainerSize(); i++) {
            ItemStack stack = chest.getItem(i);
            if (stack.is(Items.DIAMOND)) {
                total += stack.getCount();
            }
        }
        return total;
    }

    private static void assertDiamonds(GameTestHelper helper, BlockPos pos, int expected) {
        GameTestCompat.assertValueEqual(helper, diamonds(helper, pos), expected, "diamantes em " + pos.toShortString());
    }

    private static void waitRegistered(GameTestHelper helper, RouterBlockEntity... routers) {
        for (RouterBlockEntity router : routers) {
            helper.assertTrue(NetworkManager.get().contains(router), "roteador não registrado");
        }
    }

    @GameTest(template = "empty")
    public static void bothDoesNotFeedBoth(GameTestHelper helper) {
        UUID network = newNetwork(helper, "teste-ambos-ambos");
        RouterBlockEntity a = chest(helper, A, network, PortMode.BOTH);
        RouterBlockEntity b = chest(helper, B, network, PortMode.BOTH);
        ((ChestBlockEntity) GameTestCompat.getBlockEntity(helper, A)).setItem(0, new ItemStack(Items.DIAMOND, 10));

        helper.startSequence()
                .thenWaitUntil(() -> waitRegistered(helper, a, b))
                .thenExecuteFor(20, () -> {
                    assertDiamonds(helper, A, 10);
                    assertDiamonds(helper, B, 0);
                })
                .thenSucceed();
    }

    @GameTest(template = "empty")
    public static void bothFeedsInsert(GameTestHelper helper) {
        UUID network = newNetwork(helper, "teste-ambos-insere");
        chest(helper, A, network, PortMode.BOTH);
        chest(helper, B, network, PortMode.INSERT);
        ((ChestBlockEntity) GameTestCompat.getBlockEntity(helper, A)).setItem(0, new ItemStack(Items.DIAMOND, 10));

        helper.succeedWhen(() -> {
            assertDiamonds(helper, A, 0);
            assertDiamonds(helper, B, 10);
        });
    }

    @GameTest(template = "empty")
    public static void extractFeedsBoth(GameTestHelper helper) {
        UUID network = newNetwork(helper, "teste-extrai-ambos");
        chest(helper, A, network, PortMode.EXTRACT);
        chest(helper, B, network, PortMode.BOTH);
        ((ChestBlockEntity) GameTestCompat.getBlockEntity(helper, A)).setItem(0, new ItemStack(Items.DIAMOND, 10));

        helper.succeedWhen(() -> {
            assertDiamonds(helper, A, 0);
            assertDiamonds(helper, B, 10);
        });
    }

    @GameTest(template = "empty")
    public static void bothPassesFromExtractToInsert(GameTestHelper helper) {
        // Extrai → (Ambos ou Insere) e Ambos → Insere: no fim tudo para no baú que só insere.
        UUID network = newNetwork(helper, "teste-ambos-meio");
        chest(helper, A, network, PortMode.EXTRACT);
        chest(helper, B, network, PortMode.BOTH);
        chest(helper, C, network, PortMode.INSERT);
        ((ChestBlockEntity) GameTestCompat.getBlockEntity(helper, A)).setItem(0, new ItemStack(Items.DIAMOND, 20));

        helper.onEachTick(() -> GameTestCompat.assertValueEqual(helper, 
                diamonds(helper, A) + diamonds(helper, B) + diamonds(helper, C), 20, "diamantes"));
        helper.succeedWhen(() -> {
            assertDiamonds(helper, A, 0);
            assertDiamonds(helper, B, 0);
            assertDiamonds(helper, C, 20);
        });
    }
}
