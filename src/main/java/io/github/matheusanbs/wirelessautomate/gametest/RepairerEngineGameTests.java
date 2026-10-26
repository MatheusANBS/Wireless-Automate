package io.github.matheusanbs.wirelessautomate.gametest;

import io.github.matheusanbs.wirelessautomate.Config;
import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.block.RouterTier;
import io.github.matheusanbs.wirelessautomate.registry.ModDataComponents;
import io.github.matheusanbs.wirelessautomate.registry.ModItems;
import io.github.matheusanbs.wirelessautomate.repairer.RepairMath;
import io.github.matheusanbs.wirelessautomate.repairer.RepairerBlockEntity;
import io.github.matheusanbs.wirelessautomate.repairer.RepairerCosts;
import io.github.matheusanbs.wirelessautomate.repairer.RepairerManager;
import io.github.matheusanbs.wirelessautomate.repairer.RepairerSlots;
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
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.capabilities.Capabilities;

/** O motor do Reparador: as oito linhas, o custo, o travar e destravar, o gerenciador e o NBT da linha. */
@GameTestHolder(WirelessAutomate.MODID)
@PrefixGameTestTemplate(false)
public final class RepairerEngineGameTests {
    private RepairerEngineGameTests() {
    }

    private static ItemStack full() {
        return ResonatorTestSupport.crystal(false, CrystalCharge.FULL);
    }

    private static BulkEnergy bulk(GameTestHelper helper) {
        return helper.getLevel().getCapability(BulkEnergy.BLOCK, helper.absolutePos(RepairerGameTests.A), Direction.NORTH);
    }

    private static IItemHandler handler(GameTestHelper helper) {
        return helper.getLevel().getCapability(Capabilities.ItemHandler.BLOCK, helper.absolutePos(RepairerGameTests.A), Direction.NORTH);
    }

    private static long later(GameTestHelper helper) {
        return helper.getLevel().getGameTime() + Config.repairTicks();
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void reparaUmCristalRachado(GameTestHelper helper) {
        RepairerGameTests.repairer(helper, repairer -> {
            ItemStack cracked = RepairerGameTests.cracked(true);
            long cost = RepairerCosts.of(cracked);
            repairer.items().set(0, cracked);
            repairer.items().set(RepairerSlots.FEED, full().copyWithCount(4));
            helper.assertValueEqual(bulk(helper).insert(cost, false), cost, "energia");
            helper.assertTrue(repairer.running(0), "a linha 0 não começou");
            helper.assertValueEqual(repairer.energy().stored(), 0L, "o custo não saiu do buffer");
            helper.assertTrue(repairer.items().get(RepairerSlots.FEED).isEmpty(), "o estoque não gastou 4");
            repairer.tick(later(helper));
            helper.assertTrue(repairer.items().get(0).isEmpty(), "a entrada não esvaziou");
            ItemStack out = repairer.items().get(RepairerSlots.OUTPUT_FIRST);
            helper.assertTrue(SignalCrystalItem.isFull(out), "a saída não está cheia");
            helper.assertTrue(out.get(ModDataComponents.CRYSTAL_INTEGRITY.get()) == null, "a integridade ficou");
            helper.assertValueEqual(SignalCrystalItem.integrity(out), Config.crystalIntegrity(), "integridade");
            helper.assertTrue(out.is(ModItems.CUT_SIGNAL_CRYSTAL.get()), "o tipo mudou");
            helper.assertTrue(!repairer.anyRunning(), "ainda há linha andando");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void semEnergiaNaoComeca(GameTestHelper helper) {
        RepairerGameTests.repairer(helper, repairer -> {
            ItemStack cracked = RepairerGameTests.cracked(true);
            long cost = RepairerCosts.of(cracked);
            repairer.items().set(0, cracked);
            repairer.items().set(RepairerSlots.FEED, full().copyWithCount(4));
            repairer.wake();
            helper.assertTrue(!repairer.running(0), "começou sem energia");
            bulk(helper).insert(cost - 1, false);
            helper.assertTrue(!repairer.running(0), "começou com energia a menos");
            bulk(helper).insert(1, false);
            helper.assertTrue(repairer.running(0), "a energia não acordou a linha");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void semCristaisCheiosNaoComeca(GameTestHelper helper) {
        RepairerGameTests.repairer(helper, repairer -> {
            ItemStack cracked = RepairerGameTests.cracked();
            repairer.items().set(0, cracked);
            repairer.items().set(RepairerSlots.FEED, full().copyWithCount(Config.recastCrystals() - 1));
            bulk(helper).insert(RepairerCosts.of(cracked), false);
            helper.assertTrue(!repairer.running(0), "começou com cristais a menos");
            helper.assertTrue(handler(helper).insertItem(RepairerSlots.FEED, full(), false).isEmpty(), "o cristal não entrou");
            helper.assertTrue(repairer.running(0), "o cristal que entrou não acordou a linha");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void oitoLinhasEmParalelo(GameTestHelper helper) {
        RepairerGameTests.repairer(helper, repairer -> {
            long total = 0;
            for (int i = 0; i < RepairMath.LINES; i++) {
                ItemStack cracked = RepairerGameTests.cracked(true);
                repairer.items().set(i, cracked);
                total += RepairerCosts.of(cracked);
            }
            repairer.items().set(RepairerSlots.FEED, full().copyWithCount(4 * RepairMath.LINES));
            bulk(helper).insert(total, false);
            for (int i = 0; i < RepairMath.LINES; i++) {
                helper.assertTrue(repairer.running(i), "a linha " + i + " não rodou");
            }
            helper.assertTrue(repairer.anyRunning(), "anyRunning");
            repairer.tick(later(helper));
            for (int i = 0; i < RepairMath.LINES; i++) {
                helper.assertTrue(SignalCrystalItem.isFull(repairer.items().get(RepairerSlots.OUTPUT_FIRST + i)), "sem saída " + i);
                helper.assertTrue(repairer.items().get(i).isEmpty(), "a entrada " + i + " não esvaziou");
            }
            helper.assertTrue(!repairer.anyRunning(), "sobrou linha andando");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void saidaCheiaTravaEDestravaAoEsvaziar(GameTestHelper helper) {
        RepairerGameTests.repairer(helper, repairer -> {
            for (int i = RepairerSlots.OUTPUT_FIRST; i < RepairerSlots.TOTAL; i++) {
                repairer.items().set(i, full());
            }
            ItemStack cracked = RepairerGameTests.cracked();
            repairer.items().set(0, cracked);
            repairer.items().set(RepairerSlots.FEED, full().copyWithCount(4));
            bulk(helper).insert(RepairerCosts.of(cracked), false);
            helper.assertTrue(repairer.running(0), "não começou");
            repairer.tick(later(helper));
            helper.assertTrue(repairer.lineBlocked(0), "a linha não travou");
            helper.assertTrue(!repairer.items().get(0).isEmpty(), "a entrada perdeu o cristal");
            helper.assertTrue(!RepairerManager.contains(repairer), "a linha travada ficou no gerenciador");
            helper.assertTrue(!handler(helper).extractItem(RepairerSlots.OUTPUT_FIRST, 64, false).isEmpty(), "nada saiu");
            helper.assertTrue(!repairer.lineBlocked(0) && repairer.lineEnd(0) == 0, "não destravou");
            helper.assertTrue(repairer.items().get(0).isEmpty(), "a entrada não esvaziou");
            helper.assertTrue(SignalCrystalItem.isFull(repairer.items().get(RepairerSlots.OUTPUT_FIRST)), "a saída não tem o cristal");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void paradoNaoEstaNoGerenciador(GameTestHelper helper) {
        RepairerGameTests.repairer(helper, repairer -> {
            helper.assertTrue(!RepairerManager.contains(repairer), "o Reparador parado está no gerenciador");
            ItemStack cracked = RepairerGameTests.cracked();
            repairer.items().set(0, cracked);
            repairer.items().set(RepairerSlots.FEED, full().copyWithCount(4));
            bulk(helper).insert(RepairerCosts.of(cracked), false);
            helper.assertTrue(RepairerManager.contains(repairer), "com linha trabalhando devia estar no gerenciador");
            repairer.tick(later(helper));
            helper.assertTrue(!RepairerManager.contains(repairer), "terminou e continuou no gerenciador");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void nbtGuardaALinhaEmAndamento(GameTestHelper helper) {
        RepairerGameTests.repairer(helper, repairer -> {
            for (int i = RepairerSlots.OUTPUT_FIRST + 1; i < RepairerSlots.TOTAL; i++) {
                repairer.items().set(i, full());
            }
            ItemStack cracked = RepairerGameTests.cracked();
            repairer.items().set(0, cracked);
            repairer.items().set(1, RepairerGameTests.cracked());
            repairer.items().set(RepairerSlots.FEED, full().copyWithCount(8));
            long cost = RepairerCosts.of(cracked);
            bulk(helper).insert(cost * 2 + 5, false);
            repairer.tick(later(helper));
            helper.assertTrue(repairer.running(1) || !repairer.items().get(1).isEmpty(), "a linha 1 sumiu");
            // A linha 0 terminou na saída 0; recoloca uma entrada para ter uma andando e outra travada.
            repairer.items().set(RepairerSlots.OUTPUT_FIRST, full());
            repairer.items().set(0, RepairerGameTests.cracked());
            repairer.items().set(RepairerSlots.FEED, full().copyWithCount(8));
            bulk(helper).insert(cost, false);
            helper.assertTrue(repairer.running(0), "a linha 0 devia andar");
            long left = repairer.lineEnd(0) - helper.getLevel().getGameTime();
            var registries = helper.getLevel().registryAccess();
            CompoundTag tag = repairer.saveWithoutMetadata(registries);
            helper.assertTrue(tag.getIntArray("lines")[0] > 0, "lines[0] devia ser positivo");
            repairer.items().clear();
            repairer.loadWithComponents(tag, registries);
            repairer.onLoad();
            helper.assertTrue(!repairer.items().get(0).isEmpty(), "a entrada não voltou");
            helper.assertTrue(repairer.running(0), "a linha não voltou");
            long back = repairer.lineEnd(0) - helper.getLevel().getGameTime();
            helper.assertTrue(Math.abs(back - left) <= 2, "o tempo que faltava mudou: " + left + " e " + back);
            helper.assertValueEqual(repairer.energy().stored(), tag.getLong("energy"), "energia");
            helper.assertValueEqual(repairer.items().get(RepairerSlots.FEED).getCount(), 4, "estoque");

            repairer.tick(later(helper));
            helper.assertTrue(repairer.lineBlocked(0), "sem saída a linha devia travar");
            CompoundTag blocked = repairer.saveWithoutMetadata(registries);
            helper.assertValueEqual(blocked.getIntArray("lines")[0], -1, "lines[0] travada");
            repairer.loadWithComponents(blocked, registries);
            repairer.onLoad();
            helper.assertTrue(repairer.lineBlocked(0), "a linha travada não voltou travada");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void custoPorTipo(GameTestHelper helper) {
        int factor = Config.repairCostFactor();
        helper.assertValueEqual(RepairerCosts.of(RepairerGameTests.cracked(false)),
                RepairMath.cost(Config.resonatorFePerCrystal(RouterTier.BASIC), factor), "Cristal de Sinal");
        helper.assertValueEqual(RepairerCosts.of(RepairerGameTests.cracked(true)),
                RepairMath.cost(Config.resonatorFePerCrystal(RouterTier.ADVANCED), factor), "Lapidado");
        ItemStack tuned = new ItemStack(ModItems.TUNED_CRYSTALS.get(RouterTier.ELITE).get());
        helper.assertValueEqual(RepairerCosts.of(tuned),
                RepairMath.cost(Config.resonatorFePerCrystal(RouterTier.ELITE), factor), "Afinado Elite");
        helper.assertValueEqual(RepairMath.costTier(false, RouterTier.ELITE.ordinal()), RouterTier.ELITE.ordinal(), "costTier");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void entradaDeLinhaOcupadaNaoSaiNemTroca(GameTestHelper helper) {
        RepairerGameTests.repairer(helper, repairer -> {
            ItemStack original = RepairerGameTests.cracked();
            long cost = RepairerCosts.of(original);
            repairer.items().set(0, original);
            repairer.items().set(RepairerSlots.FEED, full().copyWithCount(4));
            bulk(helper).insert(cost, false);
            helper.assertTrue(repairer.running(0), "não começou");
            var container = repairer.container();
            helper.assertTrue(container.removeItem(0, 1).isEmpty(), "removeItem tirou a entrada");
            helper.assertTrue(container.removeItemNoUpdate(0).isEmpty(), "removeItemNoUpdate tirou a entrada");
            container.setItem(0, RepairerGameTests.cracked(true));
            helper.assertTrue(!repairer.items().get(0).is(ModItems.CUT_SIGNAL_CRYSTAL.get()), "setItem trocou a entrada");
            helper.assertTrue(!container.getItem(0).isEmpty(), "getItem não devolve a pilha");
            repairer.tick(later(helper));
            helper.assertTrue(repairer.items().get(0).isEmpty(), "a entrada não esvaziou");
            ItemStack out = repairer.items().get(RepairerSlots.OUTPUT_FIRST);
            helper.assertTrue(SignalCrystalItem.isFull(out) && out.is(ModItems.SIGNAL_CRYSTAL.get()), "a saída não é o original");
            helper.assertValueEqual(repairer.energy().stored(), 0L, "o custo não foi o do original");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void linhaTravadaProtegeAEntrada(GameTestHelper helper) {
        RepairerGameTests.repairer(helper, repairer -> {
            for (int i = RepairerSlots.OUTPUT_FIRST; i < RepairerSlots.TOTAL; i++) {
                repairer.items().set(i, full());
            }
            ItemStack cracked = RepairerGameTests.cracked();
            repairer.items().set(0, cracked);
            repairer.items().set(RepairerSlots.FEED, full().copyWithCount(4));
            bulk(helper).insert(RepairerCosts.of(cracked), false);
            repairer.tick(later(helper));
            helper.assertTrue(repairer.lineBlocked(0), "não travou");
            helper.assertTrue(repairer.container().removeItem(0, 1).isEmpty(), "removeItem tirou");
            helper.assertTrue(repairer.container().removeItemNoUpdate(0).isEmpty(), "removeItemNoUpdate tirou");
            repairer.container().setItem(0, ItemStack.EMPTY);
            helper.assertTrue(!repairer.items().get(0).isEmpty(), "setItem esvaziou a entrada travada");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void linhaParadaDeixaTirarETrocar(GameTestHelper helper) {
        RepairerGameTests.repairer(helper, repairer -> {
            var container = repairer.container();
            container.setItem(0, RepairerGameTests.cracked());
            helper.assertTrue(!repairer.items().get(0).isEmpty(), "setItem não pôs");
            helper.assertTrue(!container.removeItem(0, 1).isEmpty(), "removeItem não tirou");
            container.setItem(1, RepairerGameTests.cracked());
            helper.assertTrue(!container.removeItemNoUpdate(1).isEmpty(), "removeItemNoUpdate não tirou");
            helper.assertTrue(repairer.items().get(0).isEmpty() && repairer.items().get(1).isEmpty(), "sobrou item");
        });
    }
}
