package io.github.matheusanbs.wirelessautomate.gametest;

import io.github.matheusanbs.wirelessautomate.Config;
import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.block.RouterTier;
import io.github.matheusanbs.wirelessautomate.registry.ModItems;
import io.github.matheusanbs.wirelessautomate.repairer.RepairerBlock;
import io.github.matheusanbs.wirelessautomate.repairer.RepairerBlockEntity;
import io.github.matheusanbs.wirelessautomate.repairer.RepairerCosts;
import io.github.matheusanbs.wirelessautomate.repairer.RepairerSlots;
import io.github.matheusanbs.wirelessautomate.repairer.RepairerView;
import io.github.matheusanbs.wirelessautomate.resonator.CrystalCharge;
import io.github.matheusanbs.wirelessautomate.resonator.SignalCrystalItem;
import io.github.matheusanbs.wirelessautomate.storage.BulkEnergy;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** A animação do Reparador no servidor: o blockstate {@code lit}, a {@link RepairerView} e o update tag. */
@GameTestHolder(WirelessAutomate.MODID)
@PrefixGameTestTemplate(false)
public final class RepairerSyncGameTests {
    private RepairerSyncGameTests() {
    }

    private static ItemStack full() {
        return ResonatorTestSupport.crystal(false, CrystalCharge.FULL);
    }

    private static BulkEnergy bulk(GameTestHelper helper) {
        return helper.getLevel().getCapability(BulkEnergy.BLOCK, helper.absolutePos(RepairerGameTests.A), Direction.NORTH);
    }

    private static boolean lit(GameTestHelper helper) {
        return helper.getBlockState(RepairerGameTests.A).getValue(RepairerBlock.LIT);
    }

    private static long later(GameTestHelper helper) {
        return helper.getLevel().getGameTime() + Config.repairTicks();
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void acendeComEnergiaEApagaSemEla(GameTestHelper helper) {
        RepairerGameTests.repairer(helper, repairer -> {
            helper.assertTrue(!lit(helper), "devia nascer apagado");
            bulk(helper).insert(100, false);
            helper.assertTrue(lit(helper), "a energia não acendeu");
            repairer.energy().extract(100, false);
            helper.assertTrue(!lit(helper), "sem energia devia apagar");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void continuaAcesoComLinhaAndandoSemEnergia(GameTestHelper helper) {
        RepairerGameTests.repairer(helper, repairer -> {
            ItemStack cracked = RepairerGameTests.cracked();
            repairer.items().set(0, cracked);
            repairer.items().set(RepairerSlots.FEED, full().copyWithCount(4));
            bulk(helper).insert(RepairerCosts.of(cracked), false);
            helper.assertTrue(repairer.running(0), "a linha não andou");
            helper.assertValueEqual(repairer.energy().stored(), 0L, "buffer");
            helper.assertTrue(lit(helper), "com a linha andando devia estar aceso");
            repairer.tick(later(helper));
            helper.assertTrue(!repairer.anyRunning(), "a linha não terminou");
            helper.assertTrue(!lit(helper), "terminou sem energia e devia apagar");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void viewSoMudaNoInicioENoFim(GameTestHelper helper) {
        RepairerGameTests.repairer(helper, repairer -> {
            ItemStack cracked = RepairerGameTests.cracked(true);
            repairer.items().set(0, cracked);
            repairer.items().set(RepairerSlots.FEED, full().copyWithCount(8));
            int before = repairer.syncCount();
            bulk(helper).insert(RepairerCosts.of(cracked), false);
            helper.assertTrue(repairer.running(0), "a linha não começou");
            helper.assertValueEqual(repairer.syncCount(), before + 1, "o início do reparo");
            RepairerView view = RepairerView.of(repairer);
            helper.assertValueEqual((int) view.kind(0), (int) RepairerView.CUT, "o tipo");
            helper.assertValueEqual(view.end(0), repairer.lineEnd(0), "o fim");
            bulk(helper).insert(50, false);
            repairer.items().set(RepairerSlots.FEED, full().copyWithCount(5));
            repairer.itemsChanged();
            helper.assertValueEqual(repairer.syncCount(), before + 1, "energia e estoque não mudam a view");
            repairer.tick(later(helper));
            helper.assertValueEqual(repairer.syncCount(), before + 2, "o fim do reparo");
            RepairerView done = RepairerView.of(repairer);
            helper.assertValueEqual((int) done.kind(0), (int) RepairerView.NONE, "o tipo no fim");
            helper.assertValueEqual(done.end(0), 0L, "o fim zerado");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void updateTagNaoApagaOsSlots(GameTestHelper helper) {
        RepairerGameTests.repairer(helper, repairer -> {
            repairer.items().set(0, RepairerGameTests.cracked());
            var registries = helper.getLevel().registryAccess();
            repairer.handleUpdateTag(repairer.getUpdateTag(registries), registries);
            helper.assertTrue(!repairer.items().get(0).isEmpty(), "o update tag apagou o slot 0");
            helper.assertValueEqual((int) repairer.clientView().kind(0), (int) RepairerView.SIGNAL, "a view recebida");
            CompoundTag disk = repairer.saveWithoutMetadata(registries);
            RepairerView kept = repairer.clientView();
            repairer.handleUpdateTag(disk, registries);
            helper.assertTrue(repairer.clientView() == kept, "o tag do disco trocou a view");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void tipoDeCadaRachado(GameTestHelper helper) {
        ItemStack tuned = new ItemStack(ModItems.TUNED_CRYSTALS.get(RouterTier.ELITE).get());
        SignalCrystalItem.setCharge(tuned, 0);
        SignalCrystalItem.setIntegrity(tuned, 0);
        helper.assertValueEqual((int) RepairerView.kindOf(RepairerGameTests.cracked(false)), (int) RepairerView.SIGNAL, "Sinal");
        helper.assertValueEqual((int) RepairerView.kindOf(RepairerGameTests.cracked(true)), (int) RepairerView.CUT, "Lapidado");
        helper.assertValueEqual((int) RepairerView.kindOf(tuned), RepairerView.TUNED + RouterTier.ELITE.ordinal(), "Afinado Elite");
        helper.assertValueEqual((int) RepairerView.kindOf(full()), (int) RepairerView.NONE, "cheio");
        helper.assertValueEqual((int) RepairerView.kindOf(ItemStack.EMPTY), (int) RepairerView.NONE, "vazio");
        helper.succeed();
    }
}
