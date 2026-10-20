package io.github.matheusanbs.wirelessautomate.gametest;

import static io.github.matheusanbs.wirelessautomate.gametest.ResonatorTestSupport.ORIGIN;
import static io.github.matheusanbs.wirelessautomate.gametest.ResonatorTestSupport.assertFormed;
import static io.github.matheusanbs.wirelessautomate.gametest.ResonatorTestSupport.assertInserted;
import static io.github.matheusanbs.wirelessautomate.gametest.ResonatorTestSupport.crystal;
import static io.github.matheusanbs.wirelessautomate.gametest.ResonatorTestSupport.formed;

import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.block.RouterBlock;
import io.github.matheusanbs.wirelessautomate.block.RouterTier;
import io.github.matheusanbs.wirelessautomate.gametest.ResonatorTestSupport.Layout;
import io.github.matheusanbs.wirelessautomate.registry.ModBlocks;
import io.github.matheusanbs.wirelessautomate.resonator.CoreMath;
import io.github.matheusanbs.wirelessautomate.resonator.CrystalCharge;
import io.github.matheusanbs.wirelessautomate.resonator.ResonanceCoreBlockEntity;
import io.github.matheusanbs.wirelessautomate.resonator.ResonatorBatch;
import io.github.matheusanbs.wirelessautomate.resonator.ResonatorBlueprint;
import io.github.matheusanbs.wirelessautomate.resonator.ResonatorFormation;
import io.github.matheusanbs.wirelessautomate.resonator.ResonatorStatus;
import java.util.List;
import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Etapa 3 do Ressonador de Sinal: o modo automático do Sintonizador (o passo de 0,5 MHz, o teto por tier, a peça
 * que sai) e a estrutura que o Núcleo conhece ao montar. Os lotes rodam à mão dentro de um só {@code thenExecute},
 * para o lote do gerenciador não cair no meio da conta.
 */
@GameTestHolder(WirelessAutomate.MODID)
@PrefixGameTestTemplate(false)
public final class ResonatorTunerGameTests {
    private static final BlockPos TUNER = ORIGIN.offset(2, 4, 2);
    private static final BlockPos EDGE = ORIGIN.offset(2, 0, 0);

    private ResonatorTunerGameTests() {
    }

    /** A estação anda no máximo 0,04 MHz num passo da deriva e continua longe: o dial anda o passo inteiro. */
    @GameTest(template = "empty7")
    public static void sintonizadorAndaAteMeioMhzPorPasso(GameTestHelper helper) {
        formed(helper, RouterTier.ELITE, core -> {
            core.setStationForTest(98.0);
            core.setDial(90.0);
            helper.assertTrue(core.setAuto(true), "o automático não ligou com o Sintonizador");
            batches(helper, core, 20);
            assertDial(helper, core, 90.5);
            batches(helper, core, 20);
            assertDial(helper, core, 91.0);
        });
    }

    @GameTest(template = "empty7")
    public static void sintonizadorParaNoTeto(GameTestHelper helper) {
        formed(helper, RouterTier.ELITE, core -> {
            core.setStationForTest(98.0);
            core.setDial(98.0);
            helper.assertTrue(core.setAuto(true), "o automático não ligou");
            for (int i = 0; i < 3; i++) {
                assertInserted(helper, core, crystal(true, CrystalCharge.FULL));
            }
            assertNear(helper, core.efficiency(), 0.90, "eficiência no automático");
            assertNear(helper, core.rawEfficiency(), 1.0, "eficiência sem o teto");
            ResonatorBatch.run(helper.getLevel(), core, 20);
            // Três Lapidados comuns no Elite rendem 1/2 cada (2 quartos): 6 quartos.
            helper.assertValueEqual(core.energy(), CoreMath.perTick(10_000, 6, 0.9) * 20, "energia de um lote");
        });
    }

    @GameTest(template = "empty7")
    public static void manualNaoTemTeto(GameTestHelper helper) {
        formed(helper, RouterTier.ELITE, core -> {
            core.setStationForTest(98.0);
            core.setDial(98.0);
            helper.assertTrue(!core.auto(), "o Núcleo começou no automático");
            for (int i = 0; i < 3; i++) {
                assertInserted(helper, core, crystal(true, CrystalCharge.FULL));
            }
            assertNear(helper, core.efficiency(), 1.0, "eficiência no manual");
        });
    }

    @GameTest(template = "empty7")
    public static void semSintonizadorNaoLigaOAutomatico(GameTestHelper helper) {
        formed(helper, Layout.ENERGY_ON_TOP, RouterTier.BASIC, core -> {
            helper.assertTrue(!core.hasTuner(), "Sintonizador numa estrutura sem ele");
            helper.assertTrue(!core.setAuto(true), "o automático ligou sem Sintonizador");
            helper.assertTrue(!core.auto(), "o modo mudou");
        });
    }

    /**
     * A revalidação roda no mesmo tick da troca, sem deixar um lote no automático entre as duas; a espera depois
     * mostra que a revalidação agendada não religa nada.
     */
    @GameTest(template = "empty7")
    public static void pecaQueSaiVoltaAoManual(GameTestHelper helper) {
        BlockPos corePos = ResonatorTestSupport.build(helper, ORIGIN, Layout.VITRINE, RouterTier.BASIC);
        helper.startSequence().thenWaitUntil(() -> assertFormed(helper, corePos, true)).thenExecute(() -> {
            ResonanceCoreBlockEntity core = ResonatorTestSupport.core(helper, corePos);
            core.setStationForTest(98.0);
            core.setDial(94.0);
            helper.assertTrue(core.setAuto(true), "o automático não ligou");
            helper.setBlock(TUNER, ModBlocks.RESONATOR_CASING.get());
            ResonatorFormation.revalidate(helper.getLevel(), core);
            assertManualWithoutTuner(helper, core);
        }).thenIdle(2).thenExecute(() -> assertManualWithoutTuner(helper, ResonatorTestSupport.core(helper, corePos)))
                .thenSucceed();
    }

    private static void assertManualWithoutTuner(GameTestHelper helper, ResonanceCoreBlockEntity core) {
        helper.assertTrue(core.formed() && !core.hasTuner(), "não remontou sem o Sintonizador");
        helper.assertTrue(!core.auto(), "continuou no automático sem a peça");
        assertDial(helper, core, 94.0);
    }

    @GameTest(template = "empty7")
    public static void manualNaoMexeNoDial(GameTestHelper helper) {
        formed(helper, RouterTier.ELITE, core -> {
            core.setStationForTest(98.0);
            core.setDial(90.0);
            helper.assertTrue(!core.auto(), "o Núcleo começou no automático");
            batches(helper, core, 40);
            assertDial(helper, core, 90.0);
        });
    }

    @GameTest(template = "empty7")
    public static void statusEComandoMostramOModo(GameTestHelper helper) {
        formed(helper, RouterTier.BASIC, core -> {
            assertStatusMode(helper, core, "mode.manual");
            helper.assertTrue(core.setAuto(true), "o automático não ligou");
            assertStatusMode(helper, core, "mode.auto");
            BlockPos pos = core.getBlockPos();
            List<Component> lines = ResonatorTestSupport.commandLines(helper,
                    "wa resonator " + pos.getX() + " " + pos.getY() + " " + pos.getZ() + " info");
            TranslatableContents mode =
                    ResonatorTestSupport.line(lines, "command.wirelessautomate.resonator.info.mode");
            helper.assertTrue(mode != null, "sem a linha do modo: " + lines);
            assertKey(helper, mode.getArgs()[0], "block.wirelessautomate.resonance_core.mode.auto");
            assertKey(helper, mode.getArgs()[1], "command.wirelessautomate.resonator.yes");
        });
    }

    /** Um Núcleo salvo montado antes de o tempo existir ganha 0 na revalidação, sem reacender a câmara. */
    @GameTest(template = "empty7")
    public static void nucleoMontadoSemTempoGanhaZero(GameTestHelper helper) {
        formed(helper, RouterTier.BASIC, core -> {
            CompoundTag tag = core.saveWithoutMetadata(helper.getLevel().registryAccess());
            tag.remove("formed_at");
            core.loadWithComponents(tag, helper.getLevel().registryAccess());
            helper.assertValueEqual(core.formedAt(), -1L, "o tempo antes da revalidação");
            ResonatorFormation.revalidate(helper.getLevel(), core);
            helper.assertValueEqual(core.formedAt(), 0L, "o tempo depois da revalidação");
        });
    }

    private static void assertStatusMode(GameTestHelper helper, ResonanceCoreBlockEntity core, String mode) {
        Component line = ResonatorStatus.line(core);
        helper.assertTrue(line.getContents() instanceof TranslatableContents contents
                && contents.getArgs().length == 6, "linha do status: " + line);
        assertKey(helper, ((TranslatableContents) line.getContents()).getArgs()[5],
                "block.wirelessautomate.resonance_core." + mode);
    }

    private static void assertKey(GameTestHelper helper, Object arg, String key) {
        helper.assertTrue(arg instanceof Component component
                && component.getContents() instanceof TranslatableContents contents
                && contents.getKey().equals(key), "esperava " + key + ", veio " + arg);
    }

    @GameTest(template = "empty7")
    public static void estruturaConhecida(GameTestHelper helper) {
        BlockPos corePos = ResonatorTestSupport.build(helper, ORIGIN, Layout.VITRINE, RouterTier.BASIC);
        long[] first = new long[1];
        helper.startSequence().thenWaitUntil(() -> assertFormed(helper, corePos, true)).thenExecute(() -> {
            ResonanceCoreBlockEntity core = ResonatorTestSupport.core(helper, corePos);
            helper.assertTrue(core.hasTuner(), "sem o Sintonizador");
            helper.assertValueEqual(core.energyPorts(), 1, "Portas de Energia");
            helper.assertValueEqual(core.crystalPorts(), 1, "Portas de Cristais");
            helper.assertTrue(Objects.equals(core.layout(), ResonatorBlueprint.Layout.VITRINE),
                    "montagem: " + core.layout());
            helper.assertTrue(core.formedAt() >= 0, "sem o tempo da montagem");
            first[0] = core.formedAt();
            helper.getLevel().destroyBlock(helper.absolutePos(EDGE), false);
        }).thenWaitUntil(() -> assertFormed(helper, corePos, false))
                .thenExecute(() -> helper.setBlock(EDGE, ModBlocks.RESONATOR_CASING.get()))
                .thenWaitUntil(() -> assertFormed(helper, corePos, true)).thenExecute(() -> {
                    long again = ResonatorTestSupport.core(helper, corePos).formedAt();
                    helper.assertTrue(again > first[0], "remontar não mudou o tempo: " + first[0] + " → " + again);
                }).thenSucceed();
    }

    @GameTest(template = "empty")
    public static void cristaisSoLeitura(GameTestHelper helper) {
        BlockPos relative = new BlockPos(1, 1, 1);
        helper.setBlock(relative, ModBlocks.RESONANCE_CORE.get().defaultBlockState()
                .setValue(RouterBlock.TIER, RouterTier.BASIC));
        ResonanceCoreBlockEntity core = ResonatorTestSupport.core(helper, relative);
        boolean refused = false;
        try {
            core.crystals().set(0, ItemStack.EMPTY);
        } catch (UnsupportedOperationException expected) {
            refused = true;
        }
        helper.assertTrue(refused, "a lista dos cristais aceitou escrita");
        core.setCrystalAt(0, crystal(false, CrystalCharge.FULL));
        helper.assertTrue(!core.crystals().get(0).isEmpty(), "o setCrystalAt não pôs");
        core.setCrystalAt(0, ItemStack.EMPTY);
        helper.assertTrue(core.crystals().get(0).isEmpty(), "o setCrystalAt não tirou");
        helper.succeed();
    }

    @GameTest(template = "empty7")
    public static void modoSobreviveAoNbt(GameTestHelper helper) {
        formed(helper, RouterTier.ELITE, core -> {
            helper.assertTrue(core.setAuto(true), "o automático não ligou");
            CompoundTag tag = core.saveWithoutMetadata(helper.getLevel().registryAccess());
            ResonanceCoreBlockEntity copy = new ResonanceCoreBlockEntity(core.getBlockPos(), core.getBlockState());
            copy.loadWithComponents(tag, helper.getLevel().registryAccess());
            helper.assertTrue(copy.auto(), "o modo não voltou");
            helper.assertTrue(copy.hasTuner(), "o Sintonizador não voltou");
            helper.assertValueEqual(copy.energyPorts(), core.energyPorts(), "Portas de Energia");
            helper.assertValueEqual(copy.crystalPorts(), core.crystalPorts(), "Portas de Cristais");
            helper.assertTrue(Objects.equals(copy.layout(), core.layout()), "montagem: " + copy.layout());
            helper.assertValueEqual(copy.formedAt(), core.formedAt(), "tempo da montagem");
        });
    }

    private static void batches(GameTestHelper helper, ResonanceCoreBlockEntity core, int count) {
        for (int i = 0; i < count; i++) {
            ResonatorBatch.run(helper.getLevel(), core, 20);
        }
    }

    private static void assertDial(GameTestHelper helper, ResonanceCoreBlockEntity core, double expected) {
        assertNear(helper, core.dial(), expected, "dial");
    }

    private static void assertNear(GameTestHelper helper, double actual, double expected, String what) {
        helper.assertTrue(Math.abs(actual - expected) < 1e-9, what + ": " + actual + " em vez de " + expected);
    }
}
