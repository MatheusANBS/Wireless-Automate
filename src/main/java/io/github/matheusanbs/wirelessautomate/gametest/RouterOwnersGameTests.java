package io.github.matheusanbs.wirelessautomate.gametest;

import static io.github.matheusanbs.wirelessautomate.gametest.RangeTestSupport.*;

import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.block.RouterBlock;
import io.github.matheusanbs.wirelessautomate.block.RouterBlockEntity;
import io.github.matheusanbs.wirelessautomate.block.RouterTier;
import io.github.matheusanbs.wirelessautomate.range.RouterOwners;
import io.github.matheusanbs.wirelessautomate.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * O índice de donos dos roteadores ({@link RouterOwners}): a máquina vinculada (ativa, fora do raio ou de um roteador
 * sem o upgrade), a presa de um roteador encostado e a saída do roteador que quebra.
 */
@GameTestHolder(WirelessAutomate.MODID)
@PrefixGameTestTemplate(false)
public final class RouterOwnersGameTests {
    private RouterOwnersGameTests() {
    }

    private static final BlockPos CHEST_A = new BlockPos(1, 1, 1);
    private static final BlockPos CHEST_B = new BlockPos(5, 1, 1);
    private static final BlockPos FURNACE = new BlockPos(3, 1, 4);
    /** Bem acima do raio de qualquer tier do teste (o {@code linkMachine} não confere o raio). */
    private static final BlockPos FAR_FURNACE = new BlockPos(3, 40, 4);

    /** Baú em {@code chest} e roteador Avançado preso nele por cima. */
    private static RouterBlockEntity routerOn(GameTestHelper helper, BlockPos chest) {
        return chestRouter(helper, chest, Direction.UP);
    }

    private static RouterBlockEntity chestRouter(GameTestHelper helper, BlockPos chest, Direction facing) {
        helper.setBlock(chest, Blocks.CHEST);
        BlockPos at = chest.above();
        helper.setBlock(at, ModBlocks.ROUTER.get().defaultBlockState()
                .setValue(RouterBlock.FACING, facing).setValue(RouterBlock.TIER, RouterTier.ADVANCED));
        return helper.getBlockEntity(at);
    }

    private static void eq(GameTestHelper helper, Object actual, Object expected, String what) {
        helper.assertTrue(java.util.Objects.equals(actual, expected), what + ": esperado " + expected + ", veio " + actual);
    }

    private static RouterBlockEntity other(GameTestHelper helper, RouterBlockEntity chosen, BlockPos furnace) {
        return RouterOwners.otherRouter(helper.getLevel(), chosen, helper.absolutePos(furnace));
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void linkedMachineBelongsToTheRouterUntilUnlinked(GameTestHelper helper) {
        RouterBlockEntity a = routerOn(helper, CHEST_A);
        RouterBlockEntity b = routerOn(helper, CHEST_B);
        helper.setBlock(FURNACE, Blocks.FURNACE);
        helper.startSequence()
                .thenWaitUntil(() -> {
                    waitLoaded(helper, a);
                    waitLoaded(helper, b);
                })
                .thenExecute(() -> {
                    a.setRangeUpgrade(rangeUpgrade());
                    link(helper, a, FURNACE);
                    eq(helper, other(helper, b, FURNACE), a, "outro dono para B");
                    eq(helper, other(helper, a, FURNACE), null, "outro dono para A");
                    helper.assertTrue(a.unlinkMachine(helper.absolutePos(FURNACE)), "não desvinculou");
                    eq(helper, other(helper, b, FURNACE), null, "depois de desvincular");
                    helper.assertTrue(RouterOwners.get().at(helper.getLevel(), helper.absolutePos(FURNACE)).isEmpty(),
                            "o índice guardou a desvinculada");
                })
                .thenSucceed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void inactiveMachinesStillBelong(GameTestHelper helper) {
        RouterBlockEntity a = routerOn(helper, CHEST_A);
        RouterBlockEntity b = routerOn(helper, CHEST_B);
        helper.setBlock(FAR_FURNACE, Blocks.FURNACE);
        helper.setBlock(FURNACE, Blocks.FURNACE);
        helper.startSequence()
                .thenWaitUntil(() -> {
                    waitLoaded(helper, a);
                    waitLoaded(helper, b);
                })
                .thenExecute(() -> {
                    a.setRangeUpgrade(rangeUpgrade());
                    // fora do raio de A: inativa para o motor, mas vinculada
                    link(helper, a, FAR_FURNACE);
                    eq(helper, other(helper, b, FAR_FURNACE), a, "fora do raio");
                    // A sem o upgrade com a remota guardada
                    link(helper, a, FURNACE);
                    a.setRangeUpgrade(ItemStack.EMPTY);
                    helper.assertFalse(a.rangeActive(), "o upgrade ainda vale");
                    eq(helper, other(helper, b, FURNACE), a, "sem o upgrade");
                    eq(helper, other(helper, b, FAR_FURNACE), a, "fora do raio, sem o upgrade");
                })
                .thenSucceed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void attachedMachineBelongsToTheTouchingRouter(GameTestHelper helper) {
        // A de frente para a fornalha, sem vínculo: roteador no ar com a presa em (3, 1, 4)
        helper.setBlock(FURNACE, Blocks.FURNACE);
        helper.setBlock(FURNACE.above(), ModBlocks.ROUTER.get().defaultBlockState()
                .setValue(RouterBlock.FACING, Direction.UP).setValue(RouterBlock.TIER, RouterTier.ADVANCED));
        RouterBlockEntity a = helper.getBlockEntity(FURNACE.above());
        RouterBlockEntity b = routerOn(helper, CHEST_B);
        helper.startSequence()
                .thenWaitUntil(() -> {
                    waitLoaded(helper, a);
                    waitLoaded(helper, b);
                })
                .thenExecute(() -> {
                    eq(helper, a.machinePos(), helper.absolutePos(FURNACE), "presa de A");
                    eq(helper, other(helper, b, FURNACE), a, "outro dono para B");
                    eq(helper, other(helper, a, FURNACE), null, "outro dono para A");
                })
                .thenSucceed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void brokenRouterLeavesTheIndex(GameTestHelper helper) {
        RouterBlockEntity a = routerOn(helper, CHEST_A);
        RouterBlockEntity b = routerOn(helper, CHEST_B);
        helper.setBlock(FURNACE, Blocks.FURNACE);
        helper.startSequence()
                .thenWaitUntil(() -> {
                    waitLoaded(helper, a);
                    waitLoaded(helper, b);
                })
                .thenExecute(() -> {
                    a.setRangeUpgrade(rangeUpgrade());
                    link(helper, a, FURNACE);
                    eq(helper, other(helper, b, FURNACE), a, "antes de quebrar");
                    helper.setBlock(CHEST_A.above(), Blocks.AIR);
                })
                .thenExecuteAfter(1, () -> {
                    eq(helper, other(helper, b, FURNACE), null, "depois de quebrar");
                    helper.assertFalse(
                            RouterOwners.get().at(helper.getLevel(), helper.absolutePos(FURNACE)).contains(a),
                            "A continua no índice");
                })
                .thenSucceed();
    }
}
