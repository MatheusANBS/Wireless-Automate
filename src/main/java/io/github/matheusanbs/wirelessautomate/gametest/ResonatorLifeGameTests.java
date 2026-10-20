package io.github.matheusanbs.wirelessautomate.gametest;

import static io.github.matheusanbs.wirelessautomate.gametest.ResonatorTestSupport.assertInserted;
import static io.github.matheusanbs.wirelessautomate.gametest.ResonatorTestSupport.crystal;
import static io.github.matheusanbs.wirelessautomate.gametest.ResonatorTestSupport.formed;

import io.github.matheusanbs.wirelessautomate.Config;
import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.block.RouterTier;
import io.github.matheusanbs.wirelessautomate.registry.ModItems;
import io.github.matheusanbs.wirelessautomate.resonator.CrystalCharge;
import io.github.matheusanbs.wirelessautomate.resonator.ResonanceCoreBlockEntity;
import io.github.matheusanbs.wirelessautomate.resonator.ResonatorBatch;
import io.github.matheusanbs.wirelessautomate.resonator.SignalCrystalItem;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * A vida do cristal pelo tier do Núcleo ({@code crystalLifeMinutes}): a 100% de geração, o Afinado dura 3 min no
 * Ultimate e 20 min no Básico, e a recarga e a integridade seguem como antes. Os lotes rodam à mão, tirando a energia
 * a cada um para o buffer nunca encher (com o buffer cheio não há desgaste).
 */
@GameTestHolder(WirelessAutomate.MODID)
@PrefixGameTestTemplate(false)
public final class ResonatorLifeGameTests {
    private static final int BATCH = 20;

    private ResonatorLifeGameTests() {
    }

    private static void tune(ResonanceCoreBlockEntity core) {
        core.setStationForTest(98.0);
        core.setDial(98.0);
    }

    private static ItemStack tuned(RouterTier tier) {
        return new ItemStack(ModItems.TUNED_CRYSTALS.get(tier).get());
    }

    /** Lotes de 20 ticks até o cristal da câmara 0 ficar Opaco (no máximo {@code limit}); devolve quantos rodaram. */
    private static int batchesUntilDull(GameTestHelper helper, ResonanceCoreBlockEntity core, int limit) {
        int batches = 0;
        while (batches < limit && !SignalCrystalItem.isDull(core.crystals().get(0))) {
            ResonatorBatch.run(helper.getLevel(), core, BATCH);
            core.extractEnergy(Long.MAX_VALUE, false);
            batches++;
        }
        return batches;
    }

    @GameTest(template = "empty7", timeoutTicks = 300)
    public static void afinadoDuraTresMinutosNoUltimate(GameTestHelper helper) {
        formed(helper, RouterTier.ULTIMATE, core -> {
            tune(core);
            assertInserted(helper, core, tuned(RouterTier.ULTIMATE));
            helper.assertValueEqual(Config.crystalLifeTicks(RouterTier.ULTIMATE.ordinal()), 3_600L, "vida do Ultimate");
            // 3 min = 3.600 ticks = 180 lotes; um a menos ainda deixa carga.
            helper.assertValueEqual(batchesUntilDull(helper, core, 1_000), 180, "lotes até o Opaco");
            ItemStack stack = core.crystals().get(0);
            helper.assertValueEqual(SignalCrystalItem.integrity(stack), Config.crystalIntegrity(), "a vida gastou integridade");
            helper.assertValueEqual(core.addCatalyst(new ItemStack(ModItems.RESONANCE_DUST.get())), 1, "Pó que entrou");
            ResonatorBatch.run(helper.getLevel(), core, (int) Math.min(Config.rechargeTicks(), Integer.MAX_VALUE));
            helper.assertTrue(SignalCrystalItem.isFull(core.crystals().get(0)), "a recarga não encheu o Afinado");
            helper.assertValueEqual(SignalCrystalItem.integrity(core.crystals().get(0)), Config.crystalIntegrity() - 1,
                    "integridade depois da recarga");
            helper.assertValueEqual(core.redstone(), 0, "a recarga não gastou o Pó");
        });
    }

    @GameTest(template = "empty7", timeoutTicks = 300)
    public static void afinadoDuraVinteMinutosNoBasico(GameTestHelper helper) {
        formed(helper, RouterTier.BASIC, core -> {
            tune(core);
            assertInserted(helper, core, tuned(RouterTier.ULTIMATE));
            // 20 min = 24.000 ticks = 1.200 lotes: a vida do Lapidado na config, sem escala.
            helper.assertValueEqual(batchesUntilDull(helper, core, 2_000), 1_200, "lotes até o Opaco");
        });
    }

    @GameTest(template = "empty7", timeoutTicks = 300)
    public static void sinalNoAvancadoComoAntes(GameTestHelper helper) {
        formed(helper, RouterTier.ADVANCED, core -> {
            tune(core);
            assertInserted(helper, core, crystal(false, CrystalCharge.FULL));
            ResonatorBatch.run(helper.getLevel(), core, BATCH);
            helper.assertValueEqual(SignalCrystalItem.charge(core.crystals().get(0)),
                    CrystalCharge.drain(CrystalCharge.FULL, BATCH, Config.crystalLifeTicks(false)), "gasto de um lote");
        });
    }

    /** A vida do Ultimate em 6 min dobra a do Afinado: o gasto de um lote cai pela metade. */
    @GameTest(template = "empty7", timeoutTicks = 300, batch = "wirelessautomate_config_crystal_life")
    public static void vidaSegueAConfig(GameTestHelper helper) {
        List<? extends Integer> before = Config.CRYSTAL_LIFE_MINUTES.get();
        formed(helper, RouterTier.ULTIMATE, core -> {
            try {
                List<Integer> changed = new ArrayList<>(before);
                changed.set(RouterTier.ULTIMATE.ordinal(), 6);
                Config.CRYSTAL_LIFE_MINUTES.set(changed);
                tune(core);
                assertInserted(helper, core, tuned(RouterTier.ULTIMATE));
                ResonatorBatch.run(helper.getLevel(), core, BATCH);
                helper.assertValueEqual(SignalCrystalItem.charge(core.crystals().get(0)),
                        CrystalCharge.drain(CrystalCharge.FULL, BATCH, 7_200L), "gasto com 6 min");
            } finally {
                Config.CRYSTAL_LIFE_MINUTES.set(before);
            }
        });
    }
}
