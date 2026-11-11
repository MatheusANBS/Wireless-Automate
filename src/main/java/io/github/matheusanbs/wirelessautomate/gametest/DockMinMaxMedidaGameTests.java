package io.github.matheusanbs.wirelessautomate.gametest;

import io.github.matheusanbs.wirelessautomate.block.RouterTier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * As medições do uso intenso da Doca ({@link DockMinMaxMedida}), na run {@code runGameTestServerChemicals}: só existem com a
 * variável de ambiente {@code WA_DOCK_MEDIDA=1} (sem ela, o gerador não cria nenhuma), cada caso {@link #REPS} vezes,
 * cada repetição num lote só dela, no template {@code empty11} (a cena inteira cabe nele, e o jogo limpa a área entre
 * os lotes). Não afirmam nada: escrevem as linhas "MEDIDA doca-minmax" no log. O prazo passa dos 300 ticks dos outros
 * testes porque cada medição dura de 40 s a 5 min de jogo.
 */
@GameTestHolder(ChemicalGameTests.NAMESPACE)
@PrefixGameTestTemplate(false)
public final class DockMinMaxMedidaGameTests {
    private static final boolean ENABLED = "1".equals(System.getenv("WA_DOCK_MEDIDA"));
    private static final int REPS = 3;
    /** Ticks de aquecimento antes de medir (as fábricas enchem e a volta entra em regime). */
    private static final int WARM = 100;
    private static final int MEASURE = 800;
    /** O pedido inteiro do dono: 100.000 minérios, 391 execuções de 256. */
    private static final int JOB_ORDERS = 391;
    /** Teto do pedido inteiro, em ticks (5 min de jogo). */
    private static final int JOB_LIMIT = 6000;

    private DockMinMaxMedidaGameTests() {
    }

    @GameTestGenerator
    public static Collection<TestFunction> medidas() {
        List<TestFunction> tests = new ArrayList<>();
        if (!ENABLED) {
            return tests;
        }
        for (int rep = 1; rep <= REPS; rep++) {
            for (int speed : new int[] {8, 32, 128, 256, 512}) {
                tests.add(test("c" + speed, rep, helper -> control(helper, "c" + speed, speed)));
                tests.add(test("u" + speed, rep, helper -> run(helper, "u" + speed, RouterTier.ULTIMATE, speed)));
            }
            tests.add(test("job256x4", rep, JOB_LIMIT, helper -> job(helper, "job256x4", 256, 4)));
            tests.add(test("job256x1", rep, JOB_LIMIT, helper -> job(helper, "job256x1", 256, 1)));
            tests.add(test("b256", rep, helper -> run(helper, "b256", RouterTier.BASIC, 256)));
            tests.add(test("a256", rep, helper -> run(helper, "a256", RouterTier.ADVANCED, 256)));
            tests.add(test("e256", rep, helper -> run(helper, "e256", RouterTier.ELITE, 256)));
        }
        // As fábricas do dono com 8 Speed Upgrades (200 → 20 ticks por operação) e 256× da Time Wand: o mesmo ritmo de
        // 2.560× sem upgrades. Uma repetição só (12.800 ticks de fábrica por tick do servidor).
        int fast = 2560;
        tests.add(test("c" + fast, 1, helper -> control(helper, "c" + fast, fast)));
        tests.add(test("u" + fast + "x32", 1, helper -> run(helper, "u" + fast + "x32", RouterTier.ULTIMATE, fast, 32)));
        for (int pushes : new int[] {1, 4, 7, 8, 12, 32}) {
            tests.add(test("job" + fast + "x" + pushes, 1, JOB_LIMIT,
                    helper -> job(helper, "job" + fast + "x" + pushes, fast, pushes)));
        }
        return tests;
    }

    private static TestFunction test(String name, int rep, Consumer<GameTestHelper> body) {
        return test(name, rep, WARM + MEASURE, body);
    }

    private static TestFunction test(String name, int rep, int ticks, Consumer<GameTestHelper> body) {
        String id = "dockmedida_" + name + "_" + rep;
        return new TestFunction("wirelessautomate_" + id, id, ChemicalGameTests.NAMESPACE + ":empty11", ticks + 100, 0,
                true, helper -> {
                    if (!ChemicalTestTanks.enabled()) {
                        helper.succeed();
                        return;
                    }
                    body.accept(helper);
                });
    }

    /** Uma janela fixa: {@link #WARM} ticks de aquecimento e {@link #MEASURE} medidos, com pedidos sem fim. */
    private static void run(GameTestHelper helper, String name, RouterTier tier, int speed) {
        run(helper, name, tier, speed, DockMinMaxMedida.PUSHES);
    }

    private static void run(GameTestHelper helper, String name, RouterTier tier, int speed, int pushes) {
        DockMinMaxMedida scene = new DockMinMaxMedida(helper, tier, speed);
        scene.pushes = pushes;
        int[] step = {0};
        helper.startSequence()
                .thenWaitUntil(scene::registered)
                .thenExecute(scene::link)
                .thenWaitUntil(() -> {
                    step[0]++;
                    if (step[0] == WARM) {
                        scene.startMeasure();
                    }
                    scene.tick(step[0] > WARM);
                    if (step[0] < WARM + MEASURE) {
                        throw new GameTestAssertException("medindo");
                    }
                })
                .thenExecute(() -> scene.report(name))
                .thenSucceed();
    }

    /**
     * O pedido inteiro: a CPU tem {@link #JOB_ORDERS} execuções e empurra {@code pushes} a cada 3 ticks; mede do
     * primeiro empurrão até o último lingote voltar (ou o teto).
     */
    private static void job(GameTestHelper helper, String name, int speed, int pushes) {
        DockMinMaxMedida scene = new DockMinMaxMedida(helper, RouterTier.ULTIMATE, speed);
        scene.pushes = pushes;
        int[] step = {0};
        helper.startSequence()
                .thenWaitUntil(scene::registered)
                .thenExecute(() -> {
                    scene.link();
                    scene.jobLeft = JOB_ORDERS;
                    scene.startMeasure();
                })
                .thenWaitUntil(() -> {
                    step[0]++;
                    scene.tick(true);
                    if (scene.returnedSince() < (long) JOB_ORDERS * 256 && step[0] < JOB_LIMIT) {
                        throw new GameTestAssertException("medindo");
                    }
                })
                .thenExecute(() -> scene.report(name))
                .thenSucceed();
    }

    /** O controle sem a Doca ({@link DockMinMaxMedida#idealTick}): a entrada cheia e a saída vazia a cada tick. */
    private static void control(GameTestHelper helper, String name, int speed) {
        DockMinMaxMedida scene = new DockMinMaxMedida(helper, RouterTier.ULTIMATE, speed);
        int[] step = {0};
        helper.startSequence()
                .thenExecute(() -> {
                    for (BlockPos rel : scene.factories) {
                        ChemicalTestSupport.mekanismSides(helper.getLevel().getBlockEntity(helper.absolutePos(rel)));
                    }
                })
                .thenWaitUntil(() -> {
                    step[0]++;
                    scene.idealTick(step[0] > WARM);
                    if (step[0] < WARM + MEASURE) {
                        throw new GameTestAssertException("medindo");
                    }
                })
                .thenExecute(() -> scene.reportIdeal(name))
                .thenSucceed();
    }
}
