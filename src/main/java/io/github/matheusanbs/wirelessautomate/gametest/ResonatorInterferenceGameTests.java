package io.github.matheusanbs.wirelessautomate.gametest;

import io.github.matheusanbs.wirelessautomate.Config;
import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.block.RouterTier;
import io.github.matheusanbs.wirelessautomate.gametest.ResonatorTestSupport.Layout;
import io.github.matheusanbs.wirelessautomate.resonator.CoreInterference;
import io.github.matheusanbs.wirelessautomate.resonator.CoreMath;
import io.github.matheusanbs.wirelessautomate.resonator.CrystalCharge;
import io.github.matheusanbs.wirelessautomate.resonator.ResonanceCoreBlockEntity;
import io.github.matheusanbs.wirelessautomate.resonator.ResonatorFormation;
import io.github.matheusanbs.wirelessautomate.resonator.ResonatorInterference;
import io.github.matheusanbs.wirelessautomate.resonator.ResonatorManager;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * A interferência entre dois Ressonadores no template {@code empty11}, com os Núcleos a 6 blocos. A run de GameTest
 * nasce com ela desligada (os testes da grade ficam a menos de 32 blocos uns dos outros); cada teste a liga com um
 * raio de no máximo 6, que não alcança o Núcleo de outro teste (a grade deixa 5 blocos entre os templates), e
 * devolve a config e o desligado no mesmo tick.
 */
@GameTestHolder(WirelessAutomate.MODID)
@PrefixGameTestTemplate(false)
public final class ResonatorInterferenceGameTests {
    private static final String BATCH = "wirelessautomate_config_interference";
    private static final String KEY = "command.wirelessautomate.resonator.info.interference";
    private static final BlockPos FIRST_ORIGIN = new BlockPos(0, 1, 1);
    private static final BlockPos SECOND_ORIGIN = new BlockPos(6, 1, 1);
    /** A distância de Chebyshev entre os dois Núcleos. */
    private static final int APART = 6;

    private ResonatorInterferenceGameTests() {
    }

    @GameTest(template = "empty11", timeoutTicks = 200, batch = BATCH)
    public static void semVizinhoNaoPerde(GameTestHelper helper) {
        BlockPos first = ResonatorTestSupport.build(helper, FIRST_ORIGIN, Layout.VITRINE, RouterTier.BASIC);
        helper.startSequence().thenWaitUntil(() -> ResonatorTestSupport.assertFormed(helper, first, true))
                .thenExecute(() -> withInterference(APART, 0.15, 0.6, () -> {
                    CoreInterference interference = ResonatorTestSupport.core(helper, first).interference();
                    helper.assertValueEqual(interference.lossPermille(), 0, "perda sem vizinho");
                    helper.assertValueEqual(interference.nearest(), ResonatorInterference.NONE, "vizinho");
                    helper.assertTrue(ResonatorTestSupport.line(info(helper, helper.absolutePos(first)), KEY + ".none") != null,
                            "o comando não disse nenhuma");
                })).thenSucceed();
    }

    /** Um vizinho no raio tira 15% dos dois, depois do teto do Sintonizador, e o comando mostra a perda e a distância. */
    @GameTest(template = "empty11", timeoutTicks = 200, batch = BATCH)
    public static void vizinhoNoRaioTiraQuinze(GameTestHelper helper) {
        pair(helper, (first, second) -> withInterference(APART, 0.15, 0.6, () -> {
            assertLoss(helper, first, 150, APART);
            assertLoss(helper, second, 150, APART);
            first.setStationForTest(98.0);
            first.setDial(98.0);
            ResonatorTestSupport.assertInserted(helper, first, ResonatorTestSupport.crystal(false, CrystalCharge.FULL));
            long fe = Config.resonatorFePerCrystal(RouterTier.BASIC);
            helper.assertValueEqual(first.generationPerTick(), CoreMath.perTick(fe, 4, first.efficiency() * 0.85),
                    "geração com a perda");
            TranslatableContents line = ResonatorTestSupport.line(info(helper, first.getBlockPos()), KEY);
            helper.assertTrue(line != null, "o comando não mostrou a interferência");
            helper.assertValueEqual(String.valueOf(line.getArgs()[0]), "15", "perda no comando");
            helper.assertValueEqual(line.getArgs()[1], APART, "distância no comando");
        }));
    }

    @GameTest(template = "empty11", timeoutTicks = 200, batch = BATCH)
    public static void perdaParaNoTeto(GameTestHelper helper) {
        pair(helper, (first, second) -> withInterference(APART, 0.7, 0.6, () -> assertLoss(helper, first, 600, APART)));
    }

    @GameTest(template = "empty11", timeoutTicks = 200, batch = BATCH)
    public static void foraDoRaioNaoPerde(GameTestHelper helper) {
        pair(helper, (first, second) -> withInterference(APART - 1, 0.15, 0.6,
                () -> assertLoss(helper, first, 0, ResonatorInterference.NONE)));
    }

    @GameTest(template = "empty11", timeoutTicks = 200, batch = BATCH)
    public static void raioZeroDesliga(GameTestHelper helper) {
        pair(helper, (first, second) -> withInterference(0, 0.15, 0.6, () -> {
            assertLoss(helper, first, 0, ResonatorInterference.NONE);
            assertLoss(helper, second, 0, ResonatorInterference.NONE);
        }));
    }

    @GameTest(template = "empty11", timeoutTicks = 200, batch = BATCH)
    public static void desmontarOVizinhoDevolve(GameTestHelper helper) {
        pair(helper, (first, second) -> withInterference(APART, 0.15, 0.6, () -> {
            assertLoss(helper, first, 150, APART);
            ResonatorFormation.disassemble(helper.getLevel(), second);
            assertLoss(helper, first, 0, ResonatorInterference.NONE);
        }));
    }

    /** O vizinho descarregado não conta; ao carregar de novo, a conta é refeita. */
    @GameTest(template = "empty11", timeoutTicks = 200, batch = BATCH)
    public static void descarregarERecarregarRefaz(GameTestHelper helper) {
        pair(helper, (first, second) -> withInterference(APART, 0.15, 0.6, () -> {
            second.onChunkUnloaded();
            assertLoss(helper, first, 0, ResonatorInterference.NONE);
            second.onLoad();
            assertLoss(helper, first, 150, APART);
            assertLoss(helper, second, 150, APART);
        }));
    }

    private interface PairBody {
        void run(ResonanceCoreBlockEntity first, ResonanceCoreBlockEntity second);
    }

    /** Monta os dois Ressonadores, espera os dois montarem e roda {@code body} num só tick. */
    private static void pair(GameTestHelper helper, PairBody body) {
        BlockPos first = ResonatorTestSupport.build(helper, FIRST_ORIGIN, Layout.VITRINE, RouterTier.BASIC);
        BlockPos second = ResonatorTestSupport.build(helper, SECOND_ORIGIN, Layout.VITRINE, RouterTier.BASIC);
        helper.startSequence().thenWaitUntil(() -> {
            ResonatorTestSupport.assertFormed(helper, first, true);
            ResonatorTestSupport.assertFormed(helper, second, true);
        }).thenExecute(() -> body.run(ResonatorTestSupport.core(helper, first), ResonatorTestSupport.core(helper, second)))
                .thenSucceed();
    }

    /** Liga a interferência com os valores dados, roda {@code body} e devolve a config e o desligado. */
    private static void withInterference(int radius, double penalty, double max, Runnable body) {
        int radiusBefore = Config.INTERFERENCE_RADIUS.get();
        double penaltyBefore = Config.INTERFERENCE_PENALTY.get();
        double maxBefore = Config.INTERFERENCE_MAX.get();
        try {
            Config.INTERFERENCE_RADIUS.set(radius);
            Config.INTERFERENCE_PENALTY.set(penalty);
            Config.INTERFERENCE_MAX.set(max);
            ResonatorManager.setInterferenceForTests(true);
            body.run();
        } finally {
            Config.INTERFERENCE_RADIUS.set(radiusBefore);
            Config.INTERFERENCE_PENALTY.set(penaltyBefore);
            Config.INTERFERENCE_MAX.set(maxBefore);
            ResonatorManager.setInterferenceForTests(false);
        }
    }

    private static void assertLoss(GameTestHelper helper, ResonanceCoreBlockEntity core, int permille, int nearest) {
        helper.assertValueEqual(core.interference().lossPermille(), permille, "perda em milésimos");
        helper.assertValueEqual(core.interference().nearest(), nearest, "distância do vizinho");
    }

    /** O {@code /wa resonator <pos> info} do Núcleo na posição absoluta dada. */
    private static List<Component> info(GameTestHelper helper, BlockPos absolute) {
        return ResonatorTestSupport.commandLines(helper,
                "wa resonator " + absolute.getX() + " " + absolute.getY() + " " + absolute.getZ() + " info");
    }
}
