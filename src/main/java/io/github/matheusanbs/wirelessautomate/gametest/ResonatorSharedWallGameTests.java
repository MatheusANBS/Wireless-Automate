package io.github.matheusanbs.wirelessautomate.gametest;

import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.block.RouterTier;
import io.github.matheusanbs.wirelessautomate.gametest.ResonatorTestSupport.Layout;
import io.github.matheusanbs.wirelessautomate.resonator.ResonanceCoreBlockEntity;
import io.github.matheusanbs.wirelessautomate.resonator.ResonatorCasingBlock;
import io.github.matheusanbs.wirelessautomate.resonator.ResonatorFormation;
import io.github.matheusanbs.wirelessautomate.resonator.ResonatorLayout;
import io.github.matheusanbs.wirelessautomate.resonator.ResonatorLayout.Face;
import io.github.matheusanbs.wirelessautomate.resonator.ResonatorMembers;
import io.github.matheusanbs.wirelessautomate.resonator.ResonatorStatus;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Duas Armilares de costas uma para a outra (template {@code empty11}), dividindo a fileira de trás do chão: o primeiro
 * a montar fica com ela, o segundo não monta até o primeiro desmontar. O {@code empty11} tem 7 de fundo, então o par
 * fica ao longo de x (frente leste e frente oeste) em vez de z.
 */
@GameTestHolder(WirelessAutomate.MODID)
@PrefixGameTestTemplate(false)
public final class ResonatorSharedWallGameTests {
    /** A primeira, de frente para o leste: a caixa vai de x 5 a 9, e a fileira de trás é x = 5. */
    private static final BlockPos FIRST_ORIGIN = new BlockPos(5, 1, 1);
    /** A segunda, de frente para o oeste, a oeste da primeira: a caixa vai de x 1 a 5, e a fileira de trás também é x = 5. */
    private static final BlockPos SECOND_ORIGIN = new BlockPos(1, 1, 1);
    private static final BlockPos SECOND_CORE = ResonatorTestSupport.coreOf(SECOND_ORIGIN, Face.WEST);
    /** A Laje do meio da fileira dividida. */
    private static final BlockPos SHARED = new BlockPos(5, 1, 3);

    private ResonatorSharedWallGameTests() {
    }

    @GameTest(template = "empty11")
    public static void paredeDivididaNaoMonta(GameTestHelper helper) {
        BlockPos first = ResonatorTestSupport.build(helper, FIRST_ORIGIN, Face.EAST, Layout.SEM_SINTONIZADOR,
                RouterTier.BASIC);
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
                    helper.assertTrue(owner.equals(ResonatorMembers.owner(helper.getLevel().dimension(),
                            helper.absolutePos(SHARED))), "a parede continua do primeiro");
                    helper.assertTrue(helper.getBlockState(SHARED).getValue(ResonatorCasingBlock.FACING)
                            == Direction.EAST, "a Laje da parede segue no quadro do primeiro");
                }).thenSucceed();
    }

    @GameTest(template = "empty11")
    public static void desmontarOPrimeiroLiberaOSegundo(GameTestHelper helper) {
        BlockPos first = ResonatorTestSupport.build(helper, FIRST_ORIGIN, Face.EAST, Layout.SEM_SINTONIZADOR,
                RouterTier.BASIC);
        helper.startSequence().thenWaitUntil(() -> assertFormed(helper, first, true))
                .thenExecute(() -> buildSecond(helper))
                .thenWaitUntil(() -> assertSharedWall(helper))
                .thenExecute(() -> helper.getLevel().destroyBlock(helper.absolutePos(new BlockPos(9, 1, 1)), false))
                .thenWaitUntil(() -> assertFormed(helper, SECOND_CORE, true)).thenExecute(() -> {
                    assertFormed(helper, first, false);
                    BlockPos owner = helper.absolutePos(SECOND_CORE);
                    helper.assertTrue(owner.equals(ResonatorMembers.owner(helper.getLevel().dimension(),
                            helper.absolutePos(SHARED))), "a parede passou ao segundo");
                    helper.assertTrue(helper.getBlockState(SHARED).getValue(ResonatorCasingBlock.FACING)
                            == Direction.WEST, "a Laje da parede no quadro do segundo");
                }).thenSucceed();
    }

    /**
     * A carga do mundo: o primeiro sai dos índices (como no descarregamento) e volta pelo {@code onLoad}; mesmo com o
     * segundo revalidando antes dele, a parede continua do primeiro.
     */
    @GameTest(template = "empty11")
    public static void recarregarNaoTrocaODono(GameTestHelper helper) {
        BlockPos first = ResonatorTestSupport.build(helper, FIRST_ORIGIN, Face.EAST, Layout.SEM_SINTONIZADOR,
                RouterTier.BASIC);
        helper.startSequence().thenWaitUntil(() -> assertFormed(helper, first, true))
                .thenExecute(() -> buildSecond(helper))
                .thenWaitUntil(() -> assertSharedWall(helper)).thenExecute(() -> {
                    ResonanceCoreBlockEntity a = ResonatorTestSupport.core(helper, first);
                    ResonanceCoreBlockEntity b = ResonatorTestSupport.core(helper, SECOND_CORE);
                    a.onChunkUnloaded();
                    helper.assertTrue(ResonatorMembers.owner(helper.getLevel().dimension(),
                            helper.absolutePos(SHARED)) == null, "o descarregamento solta o índice");
                    a.onLoad();
                    ResonatorFormation.revalidate(helper.getLevel(), b);
                    ResonatorFormation.revalidate(helper.getLevel(), a);
                    helper.assertTrue(!b.formed(), "o segundo montou na recarga");
                    helper.assertTrue(a.formed(), "o primeiro desmontou na recarga");
                    helper.assertTrue(helper.absolutePos(first).equals(ResonatorMembers.owner(
                            helper.getLevel().dimension(), helper.absolutePos(SHARED))), "a parede trocou de dono");
                }).thenSucceed();
    }

    /** A segunda Armilar, sem Sintonizador, sem pôr de novo a fileira dividida (x = 5): ela já é do primeiro. */
    private static void buildSecond(GameTestHelper helper) {
        ResonatorTestSupport.build(helper, SECOND_ORIGIN, Face.WEST, Layout.SEM_SINTONIZADOR, RouterTier.BASIC,
                pos -> pos.getX() == FIRST_ORIGIN.getX());
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
