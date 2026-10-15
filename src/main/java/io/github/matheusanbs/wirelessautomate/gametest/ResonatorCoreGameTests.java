package io.github.matheusanbs.wirelessautomate.gametest;

import io.github.matheusanbs.wirelessautomate.Config;
import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.block.RouterTier;
import io.github.matheusanbs.wirelessautomate.gametest.ResonatorTestSupport.Layout;
import io.github.matheusanbs.wirelessautomate.registry.ModItems;
import io.github.matheusanbs.wirelessautomate.resonator.CrystalCharge;
import io.github.matheusanbs.wirelessautomate.resonator.ResonanceCoreBlockEntity;
import io.github.matheusanbs.wirelessautomate.resonator.ResonatorBatch;
import io.github.matheusanbs.wirelessautomate.resonator.SignalCrystalItem;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Etapa 2 do Ressonador de Sinal: o lote do Núcleo, com a geração pela sintonia, o desgaste dos cristais e a
 * recarga com redstone. Cada teste roda o lote à mão dentro de um só {@code thenExecute}, para o lote do
 * gerenciador não cair no meio da conta.
 */
@GameTestHolder(WirelessAutomate.MODID)
@PrefixGameTestTemplate(false)
public final class ResonatorCoreGameTests {
    private static final BlockPos ORIGIN = new BlockPos(1, 1, 1);

    private ResonatorCoreGameTests() {
    }

    @GameTest(template = "empty7")
    public static void emSintoniaGeraOMaximoDosCristais(GameTestHelper helper) {
        formed(helper, RouterTier.ELITE, core -> {
            core.setStationForTest(98.0);
            core.setDial(98.0);
            for (int i = 0; i < 3; i++) {
                assertInserted(helper, core, crystal(false, CrystalCharge.FULL));
            }
            ResonatorBatch.run(helper.getLevel(), core, 20);
            helper.assertValueEqual(core.energy(), 3L * 4_096 * 20, "energia de um lote");
        });
    }

    @GameTest(template = "empty7")
    public static void foraDaEstacaoNaoGeraNemGasta(GameTestHelper helper) {
        formed(helper, RouterTier.ELITE, core -> {
            core.setStationForTest(98.0);
            core.setDial(90.0);
            assertInserted(helper, core, crystal(false, CrystalCharge.FULL));
            assertInserted(helper, core, crystal(true, CrystalCharge.FULL));
            ResonatorBatch.run(helper.getLevel(), core, 20);
            helper.assertValueEqual(core.energy(), 0L, "energia fora da estação");
            helper.assertValueEqual(SignalCrystalItem.charge(core.crystals().get(0)), CrystalCharge.FULL, "carga do cristal");
            helper.assertValueEqual(SignalCrystalItem.charge(core.crystals().get(1)), CrystalCharge.FULL, "carga do lapidado");
        });
    }

    @GameTest(template = "empty7")
    public static void lapidadoRendeODobro(GameTestHelper helper) {
        formed(helper, RouterTier.BASIC, core -> {
            tuned(core);
            assertInserted(helper, core, crystal(true, CrystalCharge.FULL));
            ResonatorBatch.run(helper.getLevel(), core, 20);
            helper.assertValueEqual(core.energy(), 64L * 2 * 20, "energia do lapidado");
        });
    }

    @GameTest(template = "empty7")
    public static void bufferCheioParaAGeracao(GameTestHelper helper) {
        formed(helper, RouterTier.BASIC, core -> {
            tuned(core);
            assertInserted(helper, core, crystal(true, CrystalCharge.FULL));
            for (int i = 0; i < 100 && core.energy() < core.capacity(); i++) {
                ResonatorBatch.run(helper.getLevel(), core, 20);
            }
            helper.assertValueEqual(core.energy(), core.capacity(), "buffer cheio");
            int before = SignalCrystalItem.charge(core.crystals().get(0));
            ResonatorBatch.run(helper.getLevel(), core, 20);
            helper.assertValueEqual(SignalCrystalItem.charge(core.crystals().get(0)), before,
                    "a carga não muda com o buffer cheio");
            helper.assertValueEqual(core.energy(), core.capacity(), "energia não passa da capacidade");
        });
    }

    @GameTest(template = "empty7", batch = "wirelessautomate_config")
    public static void cristalGastaNaVidaDaConfig(GameTestHelper helper) {
        formed(helper, RouterTier.BASIC, core -> {
            long before = Config.CRYSTAL_LIFE_TICKS.get();
            try {
                Config.CRYSTAL_LIFE_TICKS.set(40L);
                tuned(core);
                assertInserted(helper, core, crystal(false, CrystalCharge.FULL));
                ResonatorBatch.run(helper.getLevel(), core, 20);
                helper.assertValueEqual(SignalCrystalItem.charge(core.crystals().get(0)), CrystalCharge.FULL / 2,
                        "meia vida depois de um lote");
                ResonatorBatch.run(helper.getLevel(), core, 20);
                helper.assertTrue(SignalCrystalItem.isDull(core.crystals().get(0)), "o cristal fica Opaco");
            } finally {
                Config.CRYSTAL_LIFE_TICKS.set(before);
            }
        });
    }

    @GameTest(template = "empty7", batch = "wirelessautomate_config")
    public static void redstoneRecarregaOOpaco(GameTestHelper helper) {
        formed(helper, RouterTier.BASIC, core -> {
            long before = Config.RECHARGE_TICKS.get();
            try {
                Config.RECHARGE_TICKS.set(40L);
                assertInserted(helper, core, crystal(false, 0));
                helper.assertValueEqual(core.addRedstone(3), 3, "redstone que entrou");
                ResonatorBatch.run(helper.getLevel(), core, 20);
                helper.assertTrue(SignalCrystalItem.isDull(core.crystals().get(0)), "ainda Opaco no meio da recarga");
                ResonatorBatch.run(helper.getLevel(), core, 20);
                helper.assertTrue(SignalCrystalItem.isFull(core.crystals().get(0)), "o cristal fica cheio");
                helper.assertValueEqual(core.redstone(), 2, "sobra de redstone");
            } finally {
                Config.RECHARGE_TICKS.set(before);
            }
        });
    }

    @GameTest(template = "empty7")
    public static void semRedstoneOOpacoFica(GameTestHelper helper) {
        formed(helper, RouterTier.BASIC, core -> {
            assertInserted(helper, core, crystal(false, 0));
            for (int i = 0; i < 60; i++) {
                ResonatorBatch.run(helper.getLevel(), core, 20);
            }
            helper.assertTrue(SignalCrystalItem.isDull(core.crystals().get(0)), "o cristal continua Opaco");
            helper.assertValueEqual(core.redstone(), 0, "sem redstone");
        });
    }

    @GameTest(template = "empty7")
    public static void redstoneTemTetoDe64(GameTestHelper helper) {
        formed(helper, RouterTier.BASIC, core -> {
            helper.assertValueEqual(core.addRedstone(50), 50, "a primeira leva entra inteira");
            helper.assertValueEqual(core.addRedstone(50), 14, "só até 64");
            helper.assertValueEqual(core.redstone(), 64, "redstone no teto");
        });
    }

    @GameTest(template = "empty7")
    public static void camarasPeloTier(GameTestHelper helper) {
        formed(helper, RouterTier.BASIC, core -> {
            assertInserted(helper, core, crystal(false, CrystalCharge.FULL));
            ItemStack sobra = core.insertCrystal(crystal(false, CrystalCharge.FULL));
            helper.assertValueEqual(sobra.getCount(), 1, "o Básico só tem uma câmara");
            ItemStack outro = core.insertCrystal(new ItemStack(ModItems.RESONATOR_CRYSTAL_PORT.get()));
            helper.assertValueEqual(outro.getCount(), 1, "só cristais entram");
            ItemStack tirado = core.takeLastCrystal();
            helper.assertTrue(tirado.is(ModItems.SIGNAL_CRYSTAL.get()), "tira o cristal");
            helper.assertTrue(core.crystals().get(0).isEmpty(), "a câmara fica vazia");
        });
    }

    @GameTest(template = "empty7")
    public static void extrairEnergia(GameTestHelper helper) {
        formed(helper, RouterTier.BASIC, core -> {
            tuned(core);
            assertInserted(helper, core, crystal(false, CrystalCharge.FULL));
            ResonatorBatch.run(helper.getLevel(), core, 20);
            helper.assertValueEqual(core.extractEnergy(1_000L, true), 1_000L, "simulado");
            helper.assertValueEqual(core.energy(), 1_280L, "a simulação não tira");
            helper.assertValueEqual(core.extractEnergy(5_000L, false), 1_280L, "só o que tem");
            helper.assertValueEqual(core.energy(), 0L, "vazio");
        });
    }

    @GameTest(template = "empty7")
    public static void gerenciadorRodaOsLotes(GameTestHelper helper) {
        BlockPos corePos = ResonatorTestSupport.build(helper, ORIGIN, Layout.VITRINE, RouterTier.BASIC);
        helper.startSequence().thenWaitUntil(() -> assertFormed(helper, corePos, true)).thenExecute(() -> {
            ResonanceCoreBlockEntity core = ResonatorTestSupport.core(helper, corePos);
            tuned(core);
            assertInserted(helper, core, crystal(false, CrystalCharge.FULL));
        }).thenWaitUntil(() -> helper.assertTrue(ResonatorTestSupport.core(helper, corePos).energy() > 0,
                "o gerenciador ainda não rodou o lote")).thenSucceed();
    }

    @GameTest(template = "empty7")
    public static void desmontadoNaoGera(GameTestHelper helper) {
        BlockPos corePos = ResonatorTestSupport.build(helper, ORIGIN, Layout.VITRINE, RouterTier.BASIC);
        helper.startSequence().thenWaitUntil(() -> assertFormed(helper, corePos, true))
                .thenExecute(() -> helper.getLevel().destroyBlock(helper.absolutePos(ORIGIN.offset(2, 0, 0)), false))
                .thenWaitUntil(() -> assertFormed(helper, corePos, false)).thenExecute(() -> {
                    ResonanceCoreBlockEntity core = ResonatorTestSupport.core(helper, corePos);
                    tuned(core);
                    assertInserted(helper, core, crystal(false, CrystalCharge.FULL));
                    long before = core.energy();
                    ResonatorBatch.run(helper.getLevel(), core, 20);
                    helper.assertValueEqual(core.energy(), before, "desmontado não gera");
                    helper.assertValueEqual(SignalCrystalItem.charge(core.crystals().get(0)), CrystalCharge.FULL,
                            "desmontado não gasta");
                }).thenSucceed();
    }

    /** Monta a Vitrine no tier, espera montar e roda {@code body} num só tick. */
    private static void formed(GameTestHelper helper, RouterTier tier, Consumer<ResonanceCoreBlockEntity> body) {
        BlockPos corePos = ResonatorTestSupport.build(helper, ORIGIN, Layout.VITRINE, tier);
        helper.startSequence().thenWaitUntil(() -> assertFormed(helper, corePos, true))
                .thenExecute(() -> body.accept(ResonatorTestSupport.core(helper, corePos))).thenSucceed();
    }

    private static void tuned(ResonanceCoreBlockEntity core) {
        core.setStationForTest(98.0);
        core.setDial(98.0);
    }

    private static ItemStack crystal(boolean cut, int charge) {
        ItemStack stack = new ItemStack(cut ? ModItems.CUT_SIGNAL_CRYSTAL.get() : ModItems.SIGNAL_CRYSTAL.get());
        SignalCrystalItem.setCharge(stack, charge);
        return stack;
    }

    private static void assertInserted(GameTestHelper helper, ResonanceCoreBlockEntity core, ItemStack stack) {
        helper.assertTrue(core.insertCrystal(stack).isEmpty(), "o cristal não entrou");
    }

    private static void assertFormed(GameTestHelper helper, BlockPos corePos, boolean formed) {
        ResonanceCoreBlockEntity core = ResonatorTestSupport.core(helper, corePos);
        helper.assertTrue(core != null && core.formed() == formed, formed ? "não montou" : "não desmontou");
    }
}
