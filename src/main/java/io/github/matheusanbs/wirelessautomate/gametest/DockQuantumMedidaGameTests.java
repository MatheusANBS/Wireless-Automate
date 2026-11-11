package io.github.matheusanbs.wirelessautomate.gametest;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * As medições do uso intenso da Doca com o Quantum Computer do Advanced AE ({@link DockQuantumMedida}), na run
 * {@code runGameTestServerAe2} com o Mekanism junto: {@code WA_DOCK_MEDIDA=1 ./gradlew runGameTestServerAe2
 * -PdockMedida} (a propriedade põe o Mekanism na pasta mods dela e liga só o {@link #NAMESPACE}: os testes comuns do AE2
 * usam jogadores falsos, que quebram com o Mekanism). Sem a variável, sem o Mekanism ou sem o Advanced AE, o gerador não
 * cria nenhuma; sem a propriedade, o namespace nem é ligado. Cada medição num lote só dela, no template
 * {@code cubo11} (11×11×11: o Quantum Computer de 7×7×7 cabe nele). Não afirmam nada: escrevem as linhas "MEDIDA" no
 * log. O prazo passa dos 300 ticks dos outros testes porque o pedido inteiro pode durar minutos de jogo.
 */
@GameTestHolder(DockQuantumMedidaGameTests.NAMESPACE)
@PrefixGameTestTemplate(false)
public final class DockQuantumMedidaGameTests {
    /** O namespace só da medição: a run com {@code -PdockMedida} liga só ele, sem os testes comuns do AE2. */
    static final String NAMESPACE = "wirelessautomate_ae2medida";
    private static final boolean ENABLED = "1".equals(System.getenv("WA_DOCK_MEDIDA"));
    private static final int REPS = 3;
    /** Teto do pedido inteiro, em ticks (5 min de jogo). */
    private static final int JOB_LIMIT = 6000;
    /** As fábricas do dono: 8 Speed Upgrades (200 → 20 ticks) e 256× da Time Wand, o ritmo de 2.560× sem upgrades. */
    private static final int FAST = 2560;

    private DockQuantumMedidaGameTests() {
    }

    @GameTestGenerator
    public static Collection<TestFunction> medidas() {
        List<TestFunction> tests = new ArrayList<>();
        if (!ENABLED || !Ae2DockGameTests.enabled() || !ModList.get().isLoaded("mekanism")
                || !ModList.get().isLoaded("advanced_ae")) {
            return tests;
        }
        // Aquecimento (a JIT e as primeiras inserções na rede): a primeira medição da run sai com picos que as
        // seguintes não têm.
        tests.add(test("aquece", 1, helper -> job(helper, "aquece", FAST, true)));
        for (int rep = 1; rep <= REPS; rep++) {
            tests.add(test("q7_" + FAST, rep, helper -> job(helper, "q7_" + FAST, FAST, true)));
            tests.add(test("core_" + FAST, rep, helper -> job(helper, "core_" + FAST, FAST, false)));
        }
        tests.add(test("q7_256", 1, helper -> job(helper, "q7_256", 256, true)));
        return tests;
    }

    private static TestFunction test(String name, int rep, Consumer<GameTestHelper> body) {
        String id = "dockquantum_" + name + "_" + rep;
        return new TestFunction("wirelessautomate_" + id, id, NAMESPACE + ":cubo11",
                JOB_LIMIT + 400, 0, true, body);
    }

    /** O pedido inteiro pela CPU: do envio até o último lingote voltar à rede e o pedido acabar (ou o teto). */
    private static void job(GameTestHelper helper, String name, int speed, boolean fullComputer) {
        DockQuantumMedida medida = new DockQuantumMedida(helper, speed, fullComputer);
        Object[] plan = new Object[1];
        int[] step = {0};
        helper.startSequence()
                .thenWaitUntil(medida.scene::registered)
                .thenExecute(medida.scene::link)
                .thenWaitUntil(medida::ready)
                .thenExecute(medida::stock)
                .thenIdle(2)
                .thenExecute(() -> plan[0] = medida.plan())
                .thenWaitUntil(() -> helper.assertTrue(Ae2TestSupport.planned(plan[0]), "calculando"))
                .thenExecute(() -> medida.submit(plan[0]))
                .thenWaitUntil(() -> {
                    step[0]++;
                    medida.tick();
                    if (!medida.done() && step[0] < JOB_LIMIT) {
                        throw new GameTestAssertException("medindo");
                    }
                })
                .thenExecute(() -> medida.report(name))
                .thenSucceed();
    }
}
