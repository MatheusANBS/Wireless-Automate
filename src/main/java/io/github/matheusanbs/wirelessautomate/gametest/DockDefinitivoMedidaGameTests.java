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
 * As medições do "teste definitivo" ({@link DockDefinitivoMedida}): Infinite Smelting Factories do Mekanism Extras com
 * o Creative Upgrade, a 256×, o padrão de 4.096 e o pedido de 1.000.000 de lingotes, com a CPU de 16 coprocessadores ou
 * o Quantum Computer, e 1 ou 5 fábricas; e o padrão maior que a fábrica inteira (131.072, e 524.288, o maior que o ×2
 * do Pattern Modifier do ExtendedAE alcança a partir de 64). Na run da medição: {@code WA_DOCK_MEDIDA=definitivo
 * ./gradlew runGameTestServerAe2 -PdockMedida} (com {@code 1}, roda também as do Quantum Computer); a propriedade põe
 * o Mekanism e o Mekanism Extras na pasta mods. {@code WA_DOCK_MEDIDA_CASOS} (nomes separados por vírgula) escolhe
 * os casos. Sem o Extras, o gerador não cria nenhuma. Cada medição num lote só dela, no template {@code cubo11}. Não
 * afirmam nada: escrevem as linhas "MEDIDA doca-definitivo" no log.
 */
@GameTestHolder(DockQuantumMedidaGameTests.NAMESPACE)
@PrefixGameTestTemplate(false)
public final class DockDefinitivoMedidaGameTests {
    private static final String MODE = System.getenv("WA_DOCK_MEDIDA");
    private static final boolean ENABLED = "1".equals(MODE) || "definitivo".equals(MODE);
    private static final String CASES = System.getenv("WA_DOCK_MEDIDA_CASOS");
    /** Teto do pedido inteiro, em ticks (5 min de jogo). */
    private static final int JOB_LIMIT = 6000;
    private static final int PATTERN = 4096;
    private static final long MILLION = 1_000_000;

    private DockDefinitivoMedidaGameTests() {
    }

    @GameTestGenerator
    public static Collection<TestFunction> medidas() {
        List<TestFunction> tests = new ArrayList<>();
        if (!ENABLED || !Ae2DockGameTests.enabled() || !ModList.get().isLoaded("mekanism_extras")) {
            return tests;
        }
        add(tests, "aquece", helper -> job(helper, "aquece", 1, PATTERN, 200_000, false));
        add(tests, "um_cpu16", helper -> job(helper, "um_cpu16", 1, PATTERN, MILLION, false));
        add(tests, "cinco_cpu16", helper -> job(helper, "cinco_cpu16", 5, PATTERN, MILLION, false));
        add(tests, "um_q7", helper -> job(helper, "um_q7", 1, PATTERN, MILLION, true));
        add(tests, "cinco_q7", helper -> job(helper, "cinco_q7", 5, PATTERN, MILLION, true));
        add(tests, "grande_131072", helper -> job(helper, "grande_131072", 1, 131_072, 262_144, false));
        add(tests, "grande_524288", helper -> job(helper, "grande_524288", 1, 524_288, 524_288, false));
        return tests;
    }

    private static void add(List<TestFunction> tests, String name, Consumer<GameTestHelper> body) {
        if (CASES != null && !CASES.isBlank() && !List.of(CASES.split(",")).contains(name)) {
            return;
        }
        String id = "dockdefinitivo_" + name;
        tests.add(new TestFunction("wirelessautomate_" + id, id, DockQuantumMedidaGameTests.NAMESPACE + ":cubo11",
                JOB_LIMIT + 400, 0, true, body));
    }

    private static void job(GameTestHelper helper, String name, int factories, int perJob, long target,
            boolean quantum) {
        DockDefinitivoMedida medida = new DockDefinitivoMedida(helper, factories, perJob, target, quantum);
        Object[] plan = new Object[1];
        int[] step = {0};
        helper.startSequence()
                .thenWaitUntil(medida::registered)
                .thenExecute(medida::link)
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
