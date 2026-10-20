package io.github.matheusanbs.wirelessautomate.gametest;

import static io.github.matheusanbs.wirelessautomate.gametest.ResonatorTestSupport.ORIGIN;
import static io.github.matheusanbs.wirelessautomate.gametest.ResonatorTestSupport.assertFormed;
import static io.github.matheusanbs.wirelessautomate.gametest.ResonatorTestSupport.build;
import static io.github.matheusanbs.wirelessautomate.gametest.ResonatorTestSupport.core;
import static io.github.matheusanbs.wirelessautomate.gametest.ResonatorTestSupport.crystal;
import static io.github.matheusanbs.wirelessautomate.gametest.ResonatorTestSupport.formed;

import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.block.RouterTier;
import io.github.matheusanbs.wirelessautomate.gametest.ResonatorTestSupport.Layout;
import io.github.matheusanbs.wirelessautomate.registry.ModBlocks;
import io.github.matheusanbs.wirelessautomate.resonator.ChamberView;
import io.github.matheusanbs.wirelessautomate.resonator.CrystalCharge;
import io.github.matheusanbs.wirelessautomate.resonator.ResonanceCoreBlockEntity;
import io.github.matheusanbs.wirelessautomate.resonator.ResonatorBatch;
import io.github.matheusanbs.wirelessautomate.resonator.ResonatorPartBlock;
import io.github.matheusanbs.wirelessautomate.storage.BulkEnergy;
import java.util.Arrays;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * A vazão real de cada Porta de Energia do Ressonador e o buffer cheio, que vão à câmara pela {@link ChamberView}:
 * só a extração de verdade conta, a faixa se apaga no lote sem vazão e uma vazão estável não manda update. Cada
 * teste roda num só {@code thenExecute}, para o lote do gerenciador não cair no meio das contas.
 */
@GameTestHolder(WirelessAutomate.MODID)
@PrefixGameTestTemplate(false)
public final class ResonatorFlowGameTests {
    private static final BlockPos VITRINE_ENERGY = ORIGIN.offset(0, 2, 2);
    /** Uma segunda Porta de Energia na mesma face: x e z iguais, y maior, então vem depois na ordem. */
    private static final BlockPos SECOND_ENERGY = ORIGIN.offset(0, 3, 2);
    private static final int BATCH = 20;

    private ResonatorFlowGameTests() {
    }

    @GameTest(template = "empty7", timeoutTicks = 200)
    public static void vazaoRealAcendeAFaixa(GameTestHelper helper) {
        formed(helper, Layout.VITRINE, RouterTier.BASIC, core -> {
            generating(helper, core);
            BulkEnergy port = bulk(helper, VITRINE_ENERGY);
            helper.assertTrue(port.extract(1_000, false) == 1_000, "a Porta não deu a energia");
            batch(helper, core);
            byte[] flows = ChamberView.of(core, -1).flows();
            helper.assertTrue(flows.length == 1 && flows[0] > 0, "a faixa não acendeu: " + Arrays.toString(flows));
        });
    }

    @GameTest(template = "empty7", timeoutTicks = 200)
    public static void simulacaoNaoConta(GameTestHelper helper) {
        formed(helper, Layout.VITRINE, RouterTier.BASIC, core -> {
            generating(helper, core);
            BulkEnergy port = bulk(helper, VITRINE_ENERGY);
            helper.assertTrue(port.extract(1_000, true) == 1_000, "a simulação não viu a energia");
            batch(helper, core);
            helper.assertTrue(ChamberView.of(core, -1).flows()[0] == 0, "a extração simulada contou");
        });
    }

    @GameTest(template = "empty7", timeoutTicks = 200)
    public static void semVazaoApaga(GameTestHelper helper) {
        formed(helper, Layout.VITRINE, RouterTier.BASIC, core -> {
            generating(helper, core);
            bulk(helper, VITRINE_ENERGY).extract(1_000, false);
            batch(helper, core);
            helper.assertTrue(ChamberView.of(core, -1).flows()[0] > 0, "a faixa não acendeu");
            batch(helper, core);
            helper.assertTrue(ChamberView.of(core, -1).flows()[0] == 0, "a faixa não apagou sem vazão");
        });
    }

    @GameTest(template = "empty7", timeoutTicks = 300)
    public static void duasPortasSeparadas(GameTestHelper helper) {
        BlockPos corePos = build(helper, ORIGIN, Layout.VITRINE, RouterTier.BASIC);
        helper.setBlock(SECOND_ENERGY, ModBlocks.RESONATOR_ENERGY_PORT.get().defaultBlockState()
                .setValue(ResonatorPartBlock.FACING, Direction.WEST));
        helper.startSequence().thenWaitUntil(() -> assertFormed(helper, corePos, true)).thenExecute(() -> {
            ResonanceCoreBlockEntity core = core(helper, corePos);
            generating(helper, core);
            helper.assertTrue(ChamberView.of(core, -1).flows().length == 2, "duas Portas, uma faixa por Porta");
            bulk(helper, VITRINE_ENERGY).extract(1_000, false);
            batch(helper, core);
            byte[] flows = ChamberView.of(core, -1).flows();
            helper.assertTrue(flows[0] > 0 && flows[1] == 0, "só a primeira Porta devia acender: "
                    + Arrays.toString(flows));
            bulk(helper, SECOND_ENERGY).extract(1_000, false);
            batch(helper, core);
            flows = ChamberView.of(core, -1).flows();
            helper.assertTrue(flows[0] == 0 && flows[1] > 0, "só a segunda Porta devia acender: "
                    + Arrays.toString(flows));
        }).thenSucceed();
    }

    @GameTest(template = "empty7", timeoutTicks = 200)
    public static void cheioAparece(GameTestHelper helper) {
        formed(helper, Layout.VITRINE, RouterTier.BASIC, core -> {
            generating(helper, core);
            helper.assertTrue(!ChamberView.of(core, -1).full(), "cheio com o buffer longe do teto");
            for (int i = 0; i < 100 && core.energy() < core.capacity(); i++) {
                batch(helper, core);
            }
            helper.assertTrue(core.energy() >= core.capacity(), "o buffer não encheu");
            helper.assertTrue(ChamberView.of(core, -1).full(), "o buffer cheio não apareceu na vista");
            bulk(helper, VITRINE_ENERGY).extract(Long.MAX_VALUE, false);
            batch(helper, core);
            helper.assertTrue(!ChamberView.of(core, -1).full(), "o cheio não apagou depois de tirar");
        });
    }

    @GameTest(template = "empty7", timeoutTicks = 200)
    public static void vistaEstavelNaoReenvia(GameTestHelper helper) {
        formed(helper, Layout.VITRINE, RouterTier.BASIC, core -> {
            generating(helper, core);
            BulkEnergy port = bulk(helper, VITRINE_ENERGY);
            port.extract(100, false);
            batch(helper, core);
            int sent = core.syncCount();
            ChamberView first = ChamberView.of(core, -1);
            for (int i = 0; i < 2; i++) {
                port.extract(100, false);
                batch(helper, core);
            }
            helper.assertTrue(ChamberView.of(core, -1).equals(first), "a vista mudou com a vazão estável");
            helper.assertValueEqual(core.syncCount(), sent, "update tag mandado sem a vista mudar");
        });
    }

    @GameTest(template = "empty7")
    public static void faixasEFullVaoEVoltamDoNbt(GameTestHelper helper) {
        formed(helper, Layout.VITRINE, RouterTier.BASIC, core -> {
            generating(helper, core);
            bulk(helper, VITRINE_ENERGY).extract(1_000, false);
            batch(helper, core);
            ChamberView view = ChamberView.of(core, -1);
            CompoundTag tag = new CompoundTag();
            view.save(tag);
            helper.assertTrue(view.equals(ChamberView.load(tag)), "a vista com a vazão não voltou igual do NBT");
        });
    }

    /** Tuna, põe um cristal cheio e roda um lote: o Núcleo gera e o buffer tem o que tirar. */
    private static void generating(GameTestHelper helper, ResonanceCoreBlockEntity core) {
        core.setStationForTest(98.0);
        core.setDial(98.0);
        helper.assertTrue(core.insertCrystal(crystal(false, CrystalCharge.FULL)).isEmpty(), "o cristal não entrou");
        batch(helper, core);
        helper.assertTrue(core.energy() > 1_000, "o lote não gerou o bastante: " + core.energy());
    }

    private static void batch(GameTestHelper helper, ResonanceCoreBlockEntity core) {
        ResonatorBatch.run(helper.getLevel(), core, BATCH);
    }

    private static BulkEnergy bulk(GameTestHelper helper, BlockPos relative) {
        BulkEnergy energy = helper.getLevel().getCapability(BulkEnergy.BLOCK, helper.absolutePos(relative),
                Direction.WEST);
        helper.assertTrue(energy != null, "a Porta de Energia sem BulkEnergy");
        return energy;
    }
}
