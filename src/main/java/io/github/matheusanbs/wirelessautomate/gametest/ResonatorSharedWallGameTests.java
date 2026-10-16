package io.github.matheusanbs.wirelessautomate.gametest;

import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.block.RouterBlock;
import io.github.matheusanbs.wirelessautomate.block.RouterTier;
import io.github.matheusanbs.wirelessautomate.gametest.ResonatorTestSupport.Layout;
import io.github.matheusanbs.wirelessautomate.registry.ModBlocks;
import io.github.matheusanbs.wirelessautomate.resonator.ResonanceCoreBlockEntity;
import io.github.matheusanbs.wirelessautomate.resonator.ResonatorFormation;
import io.github.matheusanbs.wirelessautomate.resonator.ResonatorLayout;
import io.github.matheusanbs.wirelessautomate.resonator.ResonatorMembers;
import io.github.matheusanbs.wirelessautomate.resonator.ResonatorPartBlock;
import io.github.matheusanbs.wirelessautomate.resonator.ResonatorPorts;
import io.github.matheusanbs.wirelessautomate.resonator.ResonatorStatus;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Dois Ressonadores lado a lado dividindo uma parede (template {@code empty11}): o primeiro a montar fica com ela,
 * o segundo não monta até o primeiro desmontar.
 */
@GameTestHolder(WirelessAutomate.MODID)
@PrefixGameTestTemplate(false)
public final class ResonatorSharedWallGameTests {
    /** A caixa do primeiro vai de x 5 a 9; a do segundo, de x 1 a 5: a parede x = 5 é das duas. */
    private static final BlockPos FIRST_ORIGIN = new BlockPos(5, 1, 1);
    /** A Porta de Energia do primeiro, no centro da parede dividida. */
    private static final BlockPos SHARED_PORT = new BlockPos(5, 3, 3);
    private static final BlockPos SECOND_CORE = new BlockPos(1, 3, 3);

    private ResonatorSharedWallGameTests() {
    }

    @GameTest(template = "empty11")
    public static void paredeDivididaNaoMonta(GameTestHelper helper) {
        BlockPos first = ResonatorTestSupport.build(helper, FIRST_ORIGIN, Layout.VITRINE, RouterTier.BASIC);
        helper.startSequence().thenWaitUntil(() -> assertFormed(helper, first, true))
                .thenExecute(() -> buildSecond(helper))
                .thenWaitUntil(() -> assertSharedWall(helper)).thenExecute(() -> {
                    ResonanceCoreBlockEntity second = ResonatorTestSupport.core(helper, SECOND_CORE);
                    helper.assertTrue(!second.formed(), "o segundo montou com a parede do primeiro");
                    assertFormed(helper, first, true);
                    BlockPos owner = helper.absolutePos(first);
                    ResonatorLayout.Broken problem = second.lastProblem();
                    helper.assertTrue(problem.x() == owner.getX() && problem.y() == owner.getY()
                            && problem.z() == owner.getZ(), "a posição do problema é a do Núcleo dono: " + problem);
                    if (!(ResonatorStatus.line(second).getContents() instanceof TranslatableContents contents)) {
                        throw new GameTestAssertException("a linha do status não é traduzível");
                    }
                    helper.assertValueEqual(contents.getKey(),
                            "block.wirelessautomate.resonance_core.problem.shared_wall", "linha do status");
                    helper.assertValueEqual(String.valueOf(contents.getArgs()[0]),
                            owner.getX() + " " + owner.getY() + " " + owner.getZ(), "a posição na linha");
                    helper.assertTrue(owner.equals(ResonatorPorts.registered(helper.getLevel().dimension(),
                            helper.absolutePos(SHARED_PORT))), "a Porta da parede continua do primeiro");
                    helper.assertTrue(owner.equals(ResonatorMembers.owner(helper.getLevel().dimension(),
                            helper.absolutePos(SHARED_PORT))), "a parede continua do primeiro");
                    helper.assertTrue(helper.getBlockState(SHARED_PORT).getValue(ResonatorPartBlock.FACING)
                            == Direction.WEST, "a Porta da parede segue no quadro do primeiro");
                }).thenSucceed();
    }

    @GameTest(template = "empty11")
    public static void desmontarOPrimeiroLiberaOSegundo(GameTestHelper helper) {
        BlockPos first = ResonatorTestSupport.build(helper, FIRST_ORIGIN, Layout.VITRINE, RouterTier.BASIC);
        helper.startSequence().thenWaitUntil(() -> assertFormed(helper, first, true))
                .thenExecute(() -> buildSecond(helper))
                .thenWaitUntil(() -> assertSharedWall(helper))
                .thenExecute(() -> helper.getLevel().destroyBlock(helper.absolutePos(new BlockPos(9, 1, 1)), false))
                .thenWaitUntil(() -> assertFormed(helper, SECOND_CORE, true)).thenExecute(() -> {
                    assertFormed(helper, first, false);
                    BlockPos owner = helper.absolutePos(SECOND_CORE);
                    helper.assertTrue(owner.equals(ResonatorPorts.registered(helper.getLevel().dimension(),
                            helper.absolutePos(SHARED_PORT))), "a Porta da parede passou ao segundo");
                    helper.assertTrue(owner.equals(ResonatorMembers.owner(helper.getLevel().dimension(),
                            helper.absolutePos(SHARED_PORT))), "a parede passou ao segundo");
                    helper.assertTrue(helper.getBlockState(SHARED_PORT).getValue(ResonatorPartBlock.FACING)
                            == Direction.EAST, "a Porta da parede no quadro do segundo");
                }).thenSucceed();
    }

    /**
     * A carga do mundo: o primeiro sai dos índices (como no descarregamento) e volta pelo {@code onLoad}; mesmo com o
     * segundo revalidando antes dele, a parede continua do primeiro.
     */
    @GameTest(template = "empty11")
    public static void recarregarNaoTrocaODono(GameTestHelper helper) {
        BlockPos first = ResonatorTestSupport.build(helper, FIRST_ORIGIN, Layout.VITRINE, RouterTier.BASIC);
        helper.startSequence().thenWaitUntil(() -> assertFormed(helper, first, true))
                .thenExecute(() -> buildSecond(helper))
                .thenWaitUntil(() -> assertSharedWall(helper)).thenExecute(() -> {
                    ResonanceCoreBlockEntity a = ResonatorTestSupport.core(helper, first);
                    ResonanceCoreBlockEntity b = ResonatorTestSupport.core(helper, SECOND_CORE);
                    a.onChunkUnloaded();
                    helper.assertTrue(ResonatorMembers.owner(helper.getLevel().dimension(),
                            helper.absolutePos(SHARED_PORT)) == null, "o descarregamento solta o índice");
                    a.onLoad();
                    ResonatorFormation.revalidate(helper.getLevel(), b);
                    ResonatorFormation.revalidate(helper.getLevel(), a);
                    helper.assertTrue(!b.formed(), "o segundo montou na recarga");
                    helper.assertTrue(a.formed(), "o primeiro desmontou na recarga");
                    helper.assertTrue(helper.absolutePos(first).equals(ResonatorPorts.registered(
                            helper.getLevel().dimension(), helper.absolutePos(SHARED_PORT))), "a Porta trocou de dono");
                }).thenSucceed();
    }

    /** O segundo Ressonador: casca de x 1 a 5 (a parede x = 5 já está lá), Núcleo a oeste, Energia em cima, Cristais ao norte. */
    private static void buildSecond(GameTestHelper helper) {
        for (int x = 1; x < 5; x++) {
            for (int y = 1; y <= 5; y++) {
                for (int z = 1; z <= 5; z++) {
                    boolean inside = x > 1 && y > 1 && y < 5 && z > 1 && z < 5;
                    if (!inside) {
                        helper.setBlock(new BlockPos(x, y, z), ModBlocks.RESONATOR_CASING.get());
                    }
                }
            }
        }
        helper.setBlock(SECOND_CORE, ModBlocks.RESONANCE_CORE.get().defaultBlockState()
                .setValue(RouterBlock.TIER, RouterTier.BASIC).setValue(ResonatorPartBlock.FACING, Direction.WEST));
        part(helper, new BlockPos(3, 5, 3), ModBlocks.RESONATOR_ENERGY_PORT.get(), Direction.UP);
        part(helper, new BlockPos(3, 3, 1), ModBlocks.RESONATOR_CRYSTAL_PORT.get(), Direction.NORTH);
    }

    private static void part(GameTestHelper helper, BlockPos pos, Block block, Direction facing) {
        helper.setBlock(pos, block.defaultBlockState().setValue(ResonatorPartBlock.FACING, facing));
    }

    private static void assertSharedWall(GameTestHelper helper) {
        ResonanceCoreBlockEntity second = ResonatorTestSupport.core(helper, SECOND_CORE);
        helper.assertTrue(second != null && second.lastProblem() != null
                && second.lastProblem().problem() == ResonatorLayout.Problem.SHARED_WALL,
                "o segundo ainda não acusou a parede: " + (second == null ? null : second.lastProblem()));
    }

    private static void assertFormed(GameTestHelper helper, BlockPos core, boolean formed) {
        ResonanceCoreBlockEntity entity = ResonatorTestSupport.core(helper, core);
        helper.assertTrue(entity != null && entity.formed() == formed, (formed ? "não montou" : "não desmontou")
                + (entity == null ? "" : ": " + entity.lastProblem()));
    }
}
