package io.github.matheusanbs.wirelessautomate.gametest;

import static io.github.matheusanbs.wirelessautomate.gametest.ResonatorTestSupport.assertFormed;

import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.block.RouterTier;
import io.github.matheusanbs.wirelessautomate.gametest.ResonatorTestSupport.Layout;
import io.github.matheusanbs.wirelessautomate.gametest.TestEnergySinks.BulkSink;
import io.github.matheusanbs.wirelessautomate.gametest.TestEnergySinks.FeSink;
import io.github.matheusanbs.wirelessautomate.registry.ModItems;
import io.github.matheusanbs.wirelessautomate.resonator.ChamberView;
import io.github.matheusanbs.wirelessautomate.resonator.CrystalCharge;
import io.github.matheusanbs.wirelessautomate.resonator.ResonanceCoreBlockEntity;
import io.github.matheusanbs.wirelessautomate.resonator.ResonatorMembers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * A Porta de Energia ativa do Ressonador (design 1b): a cada tick ela empurra o buffer (até o máximo por tick) aos
 * vizinhos que não são membros e aceitam energia, primeiro em {@code long} (BulkEnergy) e depois em {@code int}
 * (IEnergyStorage, com teto de chamadas), sem perder energia e sem nunca chegar nos membros da Armilar.
 */
@GameTestHolder(WirelessAutomate.MODID)
@PrefixGameTestTemplate(false)
public final class ResonatorPushGameTests {
    private static final BlockPos ORIGIN = ResonatorTestSupport.ORIGIN;
    private static final BlockPos ENERGY_PORT = ResonatorTestSupport.ENERGY_PORT;
    /** O vizinho de fora da Porta de Energia (frente sul: a Porta olha para o leste). */
    private static final BlockPos OUTSIDE = ENERGY_PORT.east();
    /** Ticks para o primeiro empurrão e o primeiro cristal assentarem antes de medir. */
    private static final int WARM = 3;

    private ResonatorPushGameTests() {
    }

    private static void tuned(ResonanceCoreBlockEntity core) {
        core.setStationForTest(98.0);
        core.setDial(98.0);
    }

    /**
     * Um receptor ao lado da Porta recebe a geração de um tick em todos os ticks, sem puxar nada, e a vazão da Porta
     * acende com o que saiu empurrado.
     */
    @GameTest(template = "empty7", timeoutTicks = 200)
    public static void receptorAoLadoRecebeACadaTick(GameTestHelper helper) {
        BlockPos corePos = ResonatorTestSupport.build(helper, ORIGIN, Layout.ARMILAR, RouterTier.BASIC);
        BlockPos sinkAt = helper.absolutePos(OUTSIDE);
        long[] perTick = new long[1];
        long[] last = new long[1];
        int[] served = new int[1];
        int[] seen = new int[1];
        FeSink[] sink = new FeSink[1];
        int ticks = 30;
        helper.startSequence()
                .thenWaitUntil(() -> assertFormed(helper, corePos, true))
                .thenExecute(() -> {
                    ResonanceCoreBlockEntity core = ResonatorTestSupport.core(helper, corePos);
                    tuned(core);
                    helper.assertTrue(core.insertCrystal(ResonatorTestSupport.crystal(false, CrystalCharge.FULL)).isEmpty(),
                            "o cristal não entrou");
                    perTick[0] = core.generationPerTick();
                    helper.assertTrue(perTick[0] > 0, "sem geração");
                    TestEnergySinks.forget(sinkAt);
                    helper.setBlock(OUTSIDE, TestEnergySinks.FE_SINK.defaultBlockState());
                    sink[0] = TestEnergySinks.fe(sinkAt);
                })
                .thenIdle(WARM)
                // A primeira passada só fixa a base (ela pode cair no mesmo tick do passo anterior).
                .thenExecuteFor(ticks, () -> {
                    long delta = sink[0].received - last[0];
                    last[0] = sink[0].received;
                    if (seen[0]++ > 0 && delta == perTick[0]) {
                        served[0]++;
                    }
                })
                .thenExecute(() -> {
                    helper.assertValueEqual(served[0], ticks - 1, "ticks em que o receptor recebeu a geração de um tick");
                    byte[] flows = ChamberView.of(ResonatorTestSupport.core(helper, corePos), -1).flows();
                    helper.assertTrue(flows.length == 1 && flows[0] > 0,
                            "a faixa de vazão não acendeu com o empurrão: " + java.util.Arrays.toString(flows));
                    TestEnergySinks.forget(sinkAt);
                })
                .thenSucceed();
    }

    /**
     * Pela capability {@code long}, um tick entrega mais que {@link Integer#MAX_VALUE} de um Núcleo Ultimate cheio de
     * cristais, e numa chamada só.
     */
    @GameTest(template = "empty7", timeoutTicks = 200)
    public static void capabilityLongEntregaMaisQueIntNumTick(GameTestHelper helper) {
        BlockPos corePos = ResonatorTestSupport.build(helper, ORIGIN, Layout.ARMILAR, RouterTier.ULTIMATE);
        BlockPos sinkAt = helper.absolutePos(OUTSIDE);
        long[] perTick = new long[1];
        long[] last = new long[1];
        int[] lastCalls = new int[1];
        int[] served = new int[1];
        int[] seen = new int[1];
        BulkSink[] sink = new BulkSink[1];
        int ticks = 5;
        helper.startSequence()
                .thenWaitUntil(() -> assertFormed(helper, corePos, true))
                .thenExecute(() -> {
                    ResonanceCoreBlockEntity core = ResonatorTestSupport.core(helper, corePos);
                    tuned(core);
                    for (int i = 0; i < core.chambers(); i++) {
                        ItemStack crystal = new ItemStack(ModItems.TUNED_CRYSTALS.get(RouterTier.ULTIMATE).get());
                        helper.assertTrue(core.insertCrystal(crystal).isEmpty(), "o cristal " + i + " não entrou");
                    }
                    perTick[0] = core.generationPerTick();
                    helper.assertTrue(perTick[0] > Integer.MAX_VALUE, "a geração não passa do int: " + perTick[0]);
                    helper.assertTrue(perTick[0] <= core.maxOutput(), "a geração passa do máximo por tick");
                    TestEnergySinks.forget(sinkAt);
                    helper.setBlock(OUTSIDE, TestEnergySinks.BULK_SINK.defaultBlockState());
                    sink[0] = TestEnergySinks.bulk(sinkAt);
                })
                .thenIdle(WARM)
                .thenExecuteFor(ticks, () -> {
                    long delta = sink[0].received - last[0];
                    int calls = sink[0].calls - lastCalls[0];
                    last[0] = sink[0].received;
                    lastCalls[0] = sink[0].calls;
                    if (seen[0]++ > 0 && delta == perTick[0] && calls == 1) {
                        served[0]++;
                    }
                })
                .thenExecute(() -> {
                    helper.assertValueEqual(served[0], ticks - 1,
                            "ticks em que uma chamada long entregou a geração de um tick, acima do int");
                    TestEnergySinks.forget(sinkAt);
                })
                .thenSucceed();
    }

    /**
     * Com uma {@code IEnergyStorage} que aceita pouco por chamada, o empurrão faz no máximo
     * {@code MAX_FE_CALLS} chamadas por tick, e o que sobra fica no buffer: nada se perde.
     */
    @GameTest(template = "empty7", timeoutTicks = 200)
    public static void tetoDeChamadasFENaoPerdeEnergia(GameTestHelper helper) {
        BlockPos corePos = ResonatorTestSupport.build(helper, ORIGIN, Layout.ARMILAR, RouterTier.BASIC);
        BlockPos sinkAt = helper.absolutePos(OUTSIDE);
        long[] perTick = new long[1];
        long[] lastTotal = new long[1];
        long[] lastReceived = new long[1];
        int[] lastCalls = new int[1];
        int[] conserved = new int[1];
        int[] capped = new int[1];
        int[] seen = new int[1];
        FeSink[] sink = new FeSink[1];
        int ticks = 20;
        int maxCalls = 4;
        helper.startSequence()
                .thenWaitUntil(() -> assertFormed(helper, corePos, true))
                .thenExecute(() -> {
                    ResonanceCoreBlockEntity core = ResonatorTestSupport.core(helper, corePos);
                    tuned(core);
                    helper.assertTrue(core.insertCrystal(ResonatorTestSupport.crystal(false, CrystalCharge.FULL)).isEmpty(),
                            "o cristal não entrou");
                    perTick[0] = core.generationPerTick();
                    helper.assertTrue(perTick[0] >= 100, "geração pequena demais para o teste: " + perTick[0]);
                    TestEnergySinks.forget(sinkAt);
                    helper.setBlock(OUTSIDE, TestEnergySinks.FE_SINK.defaultBlockState());
                    sink[0] = TestEnergySinks.fe(sinkAt);
                    // Quatro chamadas levam 40% da geração de um tick; o resto sobe no buffer.
                    sink[0].perCall = (int) (perTick[0] / 10);
                })
                .thenIdle(WARM)
                .thenExecuteFor(ticks, () -> {
                    ResonanceCoreBlockEntity core = ResonatorTestSupport.core(helper, corePos);
                    long total = core.energy() + sink[0].received;
                    if (seen[0]++ > 0) {
                        if (total - lastTotal[0] == perTick[0]) {
                            conserved[0]++;
                        }
                        if (sink[0].calls - lastCalls[0] == maxCalls
                                && sink[0].received - lastReceived[0] == (long) maxCalls * sink[0].perCall) {
                            capped[0]++;
                        }
                    }
                    lastTotal[0] = total;
                    lastReceived[0] = sink[0].received;
                    lastCalls[0] = sink[0].calls;
                })
                .thenExecute(() -> {
                    helper.assertValueEqual(conserved[0], ticks - 1, "ticks em que buffer + recebido subiu a geração de um tick");
                    helper.assertValueEqual(capped[0], ticks - 1, "ticks com " + maxCalls + " chamadas de " + sink[0].perCall);
                    helper.assertTrue(ResonatorTestSupport.core(helper, corePos).energy() > 0, "o resto sumiu do buffer");
                    TestEnergySinks.forget(sinkAt);
                })
                .thenSucceed();
    }

    /**
     * Os blocos da Armilar ao lado da Porta não recebem nada, nem pelos dois caminhos, mesmo quando aceitariam (a
     * sonda os faz aceitar); o receptor de fora, no mesmo tempo, recebe.
     */
    @GameTest(template = "empty7", timeoutTicks = 200)
    public static void membroDaArmilarAoLadoNaoRecebe(GameTestHelper helper) {
        BlockPos portAt = helper.absolutePos(ENERGY_PORT);
        for (Direction face : Direction.values()) {
            TestEnergySinks.forget(portAt.relative(face));
            TestEnergySinks.probe(portAt.relative(face));
        }
        BlockPos corePos = ResonatorTestSupport.build(helper, ORIGIN, Layout.ARMILAR, RouterTier.BASIC);
        BlockPos outsideAt = helper.absolutePos(OUTSIDE);
        FeSink[] outside = new FeSink[1];
        helper.startSequence()
                .thenWaitUntil(() -> assertFormed(helper, corePos, true))
                .thenExecute(() -> {
                    ResonanceCoreBlockEntity core = ResonatorTestSupport.core(helper, corePos);
                    tuned(core);
                    helper.assertTrue(core.insertCrystal(ResonatorTestSupport.crystal(false, CrystalCharge.FULL)).isEmpty(),
                            "o cristal não entrou");
                    helper.setBlock(OUTSIDE, TestEnergySinks.FE_SINK.defaultBlockState());
                    outside[0] = TestEnergySinks.fe(outsideAt);
                })
                .thenIdle(10)
                .thenExecute(() -> {
                    helper.assertTrue(outside[0].received > 0, "o receptor de fora não recebeu: o empurrão não rodou");
                    int members = 0;
                    for (Direction face : Direction.values()) {
                        BlockPos at = portAt.relative(face);
                        if (ResonatorMembers.owner(helper.getLevel().dimension(), at) == null) {
                            continue;
                        }
                        members++;
                        helper.assertValueEqual(TestEnergySinks.fe(at).received, 0L, "membro " + face + " recebeu FE");
                        helper.assertValueEqual(TestEnergySinks.bulk(at).received, 0L, "membro " + face + " recebeu long");
                    }
                    helper.assertTrue(members >= 1, "a Porta não tem membro ao lado, o teste não prova nada");
                    for (Direction face : Direction.values()) {
                        TestEnergySinks.forget(portAt.relative(face));
                    }
                })
                .thenSucceed();
    }

    /**
     * Quando a face expõe as duas capabilities e a {@code long} recusa tudo, a de {@code int} recebe no mesmo tick.
     */
    @GameTest(template = "empty7", timeoutTicks = 200)
    public static void bulkRecusaERecuaParaFE(GameTestHelper helper) {
        BlockPos corePos = ResonatorTestSupport.build(helper, ORIGIN, Layout.ARMILAR, RouterTier.BASIC);
        BlockPos sinkAt = helper.absolutePos(OUTSIDE);
        long[] perTick = new long[1];
        long[] last = new long[1];
        int[] served = new int[1];
        int[] seen = new int[1];
        FeSink[] sink = new FeSink[1];
        int ticks = 10;
        helper.startSequence()
                .thenWaitUntil(() -> assertFormed(helper, corePos, true))
                .thenExecute(() -> {
                    ResonanceCoreBlockEntity core = ResonatorTestSupport.core(helper, corePos);
                    tuned(core);
                    helper.assertTrue(core.insertCrystal(ResonatorTestSupport.crystal(false, CrystalCharge.FULL)).isEmpty(),
                            "o cristal não entrou");
                    perTick[0] = core.generationPerTick();
                    TestEnergySinks.forget(sinkAt);
                    helper.setBlock(OUTSIDE, TestEnergySinks.BOTH_SINK.defaultBlockState());
                    sink[0] = TestEnergySinks.fe(sinkAt);
                })
                .thenIdle(WARM)
                .thenExecuteFor(ticks, () -> {
                    long delta = sink[0].received - last[0];
                    last[0] = sink[0].received;
                    if (seen[0]++ > 0 && delta == perTick[0]) {
                        served[0]++;
                    }
                })
                .thenExecute(() -> {
                    helper.assertValueEqual(served[0], ticks - 1, "ticks em que o FE recebeu depois da recusa da long");
                    TestEnergySinks.forget(sinkAt);
                })
                .thenSucceed();
    }

    /**
     * A posse muda sem a lista de Portas mudar: uma Armilar colada na Porta (o vizinho é membro, não recebe) é
     * desmontada, e um receptor ocupa o lugar da peça dela e passa a receber.
     */
    @GameTest(template = "empty11", timeoutTicks = 300)
    public static void vizinhoQueDeixaDeSerMembroPassaARecerber(GameTestHelper helper) {
        BlockPos second = new BlockPos(6, 1, 1);
        BlockPos secondCore = ResonatorTestSupport.build(helper, second, Layout.ARMILAR, RouterTier.BASIC);
        BlockPos corePos = ResonatorTestSupport.build(helper, ORIGIN, Layout.ARMILAR, RouterTier.BASIC);
        // A peça da segunda Armilar colada na Porta de Energia da primeira.
        BlockPos touching = OUTSIDE;
        BlockPos touchingAt = helper.absolutePos(touching);
        FeSink[] sink = new FeSink[1];
        TestEnergySinks.forget(touchingAt);
        TestEnergySinks.probe(touchingAt);
        helper.startSequence()
                .thenWaitUntil(() -> {
                    assertFormed(helper, corePos, true);
                    assertFormed(helper, secondCore, true);
                })
                .thenExecute(() -> {
                    ResonanceCoreBlockEntity core = ResonatorTestSupport.core(helper, corePos);
                    tuned(core);
                    helper.assertTrue(core.insertCrystal(ResonatorTestSupport.crystal(false, CrystalCharge.FULL)).isEmpty(),
                            "o cristal não entrou");
                    helper.assertTrue(ResonatorMembers.owner(helper.getLevel().dimension(), touchingAt) != null,
                            "a peça ao lado da Porta não é membro da segunda Armilar");
                    sink[0] = TestEnergySinks.fe(touchingAt);
                })
                .thenIdle(10)
                .thenExecute(() -> {
                    helper.assertValueEqual(sink[0].received, 0L, "o membro da segunda Armilar recebeu");
                    helper.getLevel().destroyBlock(helper.absolutePos(second.offset(2, 1, 2)), false);
                })
                .thenWaitUntil(() -> assertFormed(helper, secondCore, false))
                .thenExecute(() -> {
                    helper.assertTrue(ResonatorMembers.owner(helper.getLevel().dimension(), touchingAt) == null,
                            "a peça seguiu com dono depois da desmontagem");
                    TestEnergySinks.forget(touchingAt);
                    helper.setBlock(touching, TestEnergySinks.FE_SINK.defaultBlockState());
                    sink[0] = TestEnergySinks.fe(touchingAt);
                })
                .thenIdle(5)
                .thenExecute(() -> {
                    helper.assertTrue(sink[0].received > 0, "o receptor no lugar do ex-membro não recebeu");
                    TestEnergySinks.forget(touchingAt);
                })
                .thenSucceed();
    }
}
