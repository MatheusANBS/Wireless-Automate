package io.github.matheusanbs.wirelessautomate.gametest;

import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.registry.ModBlocks;
import io.github.matheusanbs.wirelessautomate.tv.TvArea;
import io.github.matheusanbs.wirelessautomate.tv.TvAreaCheck;
import io.github.matheusanbs.wirelessautomate.tv.TvAreaCheck.Kind;
import io.github.matheusanbs.wirelessautomate.tv.TvBlock;
import io.github.matheusanbs.wirelessautomate.tv.TvBlockEntity;
import io.github.matheusanbs.wirelessautomate.tv.TvLoaded;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** A validação da área da Tela na parede e o conjunto das Telas carregadas. Tela virada para o sul: a direita é o leste. */
@GameTestHolder(WirelessAutomate.MODID)
@PrefixGameTestTemplate(false)
public final class TvAreaGameTests {
    private static final Direction FACING = Direction.SOUTH;
    /** O bloco colocado, no canto de baixo à esquerda da área 4 × 3 (x 1 a 4, y 1 a 3, parede em z = 2). */
    private static final BlockPos A = new BlockPos(1, 1, 3);
    private static final TvArea BIG = TvArea.grown(4, 3, TvArea.Grow.UP_RIGHT);

    private TvAreaGameTests() {
    }

    private static void wall(GameTestHelper helper) {
        for (int x = 1; x <= 5; x++) {
            for (int y = 1; y <= 3; y++) {
                helper.setBlock(new BlockPos(x, y, 2), Blocks.STONE);
            }
        }
    }

    private static TvBlockEntity screenAt(GameTestHelper helper, BlockPos pos) {
        helper.setBlock(pos, ModBlocks.SIGNAL_SCREEN.get().defaultBlockState().setValue(TvBlock.FACING, FACING));
        return (TvBlockEntity) helper.getBlockEntity(pos);
    }

    private static TvAreaCheck.Problem check(GameTestHelper helper, TvArea area, int max, List<TvBlockEntity> others) {
        return TvAreaCheck.check(helper.getLevel(), helper.absolutePos(A), FACING, area, max, max, others);
    }

    private static void expect(GameTestHelper helper, TvAreaCheck.Problem got, Kind kind, BlockPos relative) {
        helper.assertTrue(got.kind() == kind, "esperava " + kind + " e veio " + got);
        if (relative != null) {
            helper.assertTrue(helper.absolutePos(relative).equals(got.pos()), "posição " + got.pos());
        }
    }

    @GameTest(template = "empty7")
    public static void areaLivreContraParede(GameTestHelper helper) {
        wall(helper);
        screenAt(helper, A);
        expect(helper, check(helper, BIG, 32, List.of()), Kind.NONE, null);
        helper.succeed();
    }

    @GameTest(template = "empty7")
    public static void faltaParede(GameTestHelper helper) {
        wall(helper);
        helper.setBlock(new BlockPos(3, 2, 2), Blocks.AIR);
        screenAt(helper, A);
        expect(helper, check(helper, BIG, 32, List.of()), Kind.NO_WALL, new BlockPos(3, 2, 3));
        helper.succeed();
    }

    @GameTest(template = "empty7")
    public static void tochaNaFrenteBloqueia(GameTestHelper helper) {
        wall(helper);
        helper.setBlock(new BlockPos(2, 0, 3), Blocks.DIRT);
        helper.setBlock(new BlockPos(2, 1, 3), Blocks.SHORT_GRASS);
        screenAt(helper, A);
        expect(helper, check(helper, BIG, 32, List.of()), Kind.NONE, null);
        helper.setBlock(new BlockPos(2, 1, 3), Blocks.TORCH);
        expect(helper, check(helper, BIG, 32, List.of()), Kind.BLOCKED, new BlockPos(2, 1, 3));
        helper.succeed();
    }

    @GameTest(template = "empty7")
    public static void outraTelaSobrepoe(GameTestHelper helper) {
        wall(helper);
        screenAt(helper, A);
        BlockPos otherPos = new BlockPos(5, 1, 3);
        TvBlockEntity other = screenAt(helper, otherPos);
        other.setArea(TvArea.grown(2, 1, TvArea.Grow.UP_LEFT));
        expect(helper, check(helper, BIG, 32, List.of(other)), Kind.OVERLAP, otherPos);
        other.setArea(TvArea.SINGLE);
        expect(helper, check(helper, BIG, 32, List.of(other)), Kind.NONE, null);
        helper.succeed();
    }

    @GameTest(template = "empty7")
    public static void tamanhoAcimaDoTeto(GameTestHelper helper) {
        wall(helper);
        screenAt(helper, A);
        expect(helper, check(helper, TvArea.grown(5, 1, TvArea.Grow.UP_RIGHT), 4, List.of()), Kind.TOO_BIG, null);
        helper.succeed();
    }

    @GameTest(template = "empty7")
    public static void umPorUmSempreLivre(GameTestHelper helper) {
        helper.setBlock(new BlockPos(1, 1, 2), Blocks.STONE);
        screenAt(helper, A);
        expect(helper, check(helper, TvArea.SINGLE, 32, List.of()), Kind.NONE, null);
        helper.succeed();
    }

    @GameTest(template = "empty7", timeoutTicks = 100)
    public static void entraESaiDoTvLoaded(GameTestHelper helper) {
        wall(helper);
        TvBlockEntity tv = screenAt(helper, A);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(TvLoaded.of(helper.getLevel()).contains(tv), "não entrou"))
                .thenExecute(() -> helper.setBlock(A, Blocks.AIR))
                .thenWaitUntil(() -> helper.assertFalse(TvLoaded.of(helper.getLevel()).contains(tv), "não saiu"))
                .thenSucceed();
    }
}
