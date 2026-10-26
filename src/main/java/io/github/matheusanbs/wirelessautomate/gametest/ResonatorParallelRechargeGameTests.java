package io.github.matheusanbs.wirelessautomate.gametest;

import static io.github.matheusanbs.wirelessautomate.gametest.ResonatorTestSupport.assertInserted;
import static io.github.matheusanbs.wirelessautomate.gametest.ResonatorTestSupport.crystal;
import static io.github.matheusanbs.wirelessautomate.gametest.ResonatorTestSupport.formed;

import io.github.matheusanbs.wirelessautomate.Config;
import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.block.RouterTier;
import io.github.matheusanbs.wirelessautomate.resonator.ResonanceCoreBlockEntity;
import io.github.matheusanbs.wirelessautomate.resonator.ResonatorBatch;
import io.github.matheusanbs.wirelessautomate.resonator.SignalCrystalItem;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * A recarga paralela do Núcleo (spec {@code 2026-10-26-reparador-de-cristais-design.md}): todos os Opacos que o
 * catalisador paga recarregam no mesmo ciclo. O Avançado tem 2 câmaras; a recarga é forçada por um lote do tamanho
 * dela, sem mudar a config.
 */
@GameTestHolder(WirelessAutomate.MODID)
@PrefixGameTestTemplate(false)
public final class ResonatorParallelRechargeGameTests {
    private ResonatorParallelRechargeGameTests() {
    }

    /** Um lote que completa a recarga em curso de uma vez. */
    private static void recharge(GameTestHelper helper, ResonanceCoreBlockEntity core) {
        ResonatorBatch.run(helper.getLevel(), core, (int) Math.min(Config.rechargeTicks(), Integer.MAX_VALUE));
    }

    /** Põe um Opaco e racha a pilha da câmara no lugar (rachado não entra pelos caminhos normais). */
    private static void crackedAt(GameTestHelper helper, ResonanceCoreBlockEntity core, int chamber) {
        helper.assertTrue(core.insertCrystalAt(chamber, crystal(true, 0)), "o Opaco não entrou");
        SignalCrystalItem.setIntegrity(core.crystals().get(chamber), 0);
        helper.assertTrue(SignalCrystalItem.isCracked(core.crystals().get(chamber)), "não rachou");
    }

    @GameTest(template = "empty7")
    public static void recarregaTodosOsOpacosDeUmaVez(GameTestHelper helper) {
        formed(helper, RouterTier.ADVANCED, core -> {
            assertInserted(helper, core, crystal(false, 0));
            assertInserted(helper, core, crystal(true, 0));
            helper.assertValueEqual(core.addCatalyst(new ItemStack(Items.REDSTONE, 2)), 2, "redstone que entrou");
            recharge(helper, core);
            for (int i = 0; i < 2; i++) {
                ItemStack stack = core.crystals().get(i);
                helper.assertTrue(SignalCrystalItem.isFull(stack), "a câmara " + i + " não encheu");
                helper.assertValueEqual(SignalCrystalItem.integrity(stack), Config.crystalIntegrity() - 1,
                        "integridade da câmara " + i);
            }
            helper.assertValueEqual(core.redstone(), 0, "um catalisador por cristal");
            helper.assertValueEqual(core.rechargeProgress(), 0, "o cronômetro não reiniciou");
        });
    }

    @GameTest(template = "empty7")
    public static void catalisadorLimitaQuantosRecarregam(GameTestHelper helper) {
        formed(helper, RouterTier.ADVANCED, core -> {
            assertInserted(helper, core, crystal(false, 0));
            assertInserted(helper, core, crystal(true, 0));
            helper.assertValueEqual(core.addCatalyst(new ItemStack(Items.REDSTONE, 1)), 1, "redstone que entrou");
            recharge(helper, core);
            helper.assertTrue(SignalCrystalItem.isFull(core.crystals().get(0)), "o primeiro Opaco não encheu");
            helper.assertTrue(SignalCrystalItem.isDull(core.crystals().get(1)), "o segundo encheu sem catalisador");
            helper.assertValueEqual(core.redstone(), 0, "o catalisador não foi gasto");
        });
    }

    @GameTest(template = "empty7")
    public static void rachadoFicaDeForaEOutroRecarrega(GameTestHelper helper) {
        formed(helper, RouterTier.ADVANCED, core -> {
            crackedAt(helper, core, 0);
            helper.assertTrue(core.insertCrystalAt(1, crystal(false, 0)), "o Opaco não entrou");
            helper.assertValueEqual(core.addCatalyst(new ItemStack(Items.REDSTONE, 2)), 2, "redstone que entrou");
            recharge(helper, core);
            helper.assertTrue(SignalCrystalItem.isCracked(core.crystals().get(0)), "o rachado mudou");
            helper.assertTrue(SignalCrystalItem.isFull(core.crystals().get(1)), "o Opaco não encheu");
            helper.assertValueEqual(core.redstone(), 1, "só o Opaco gasta catalisador");
        });
    }

    @GameTest(template = "empty7")
    public static void semCatalisadorNinguemRecarrega(GameTestHelper helper) {
        formed(helper, RouterTier.ADVANCED, core -> {
            assertInserted(helper, core, crystal(false, 0));
            assertInserted(helper, core, crystal(true, 0));
            recharge(helper, core);
            helper.assertTrue(SignalCrystalItem.isDull(core.crystals().get(0)), "encheu sem catalisador");
            helper.assertTrue(SignalCrystalItem.isDull(core.crystals().get(1)), "encheu sem catalisador");
            helper.assertValueEqual(core.rechargeProgress(), 0, "o cronômetro andou sem catalisador");
        });
    }
}
