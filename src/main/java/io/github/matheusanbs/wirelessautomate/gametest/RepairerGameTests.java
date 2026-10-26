package io.github.matheusanbs.wirelessautomate.gametest;

import io.github.matheusanbs.wirelessautomate.Config;
import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.block.RouterTier;
import io.github.matheusanbs.wirelessautomate.registry.ModBlocks;
import io.github.matheusanbs.wirelessautomate.repairer.RepairMath;
import io.github.matheusanbs.wirelessautomate.repairer.RepairerBlockEntity;
import io.github.matheusanbs.wirelessautomate.repairer.RepairerSlots;
import io.github.matheusanbs.wirelessautomate.resonator.CrystalCharge;
import io.github.matheusanbs.wirelessautomate.resonator.SignalCrystalItem;
import io.github.matheusanbs.wirelessautomate.storage.BulkEnergy;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** O Reparador de Cristais: o bloco, os slots e o NBT. */
@GameTestHolder(WirelessAutomate.MODID)
@PrefixGameTestTemplate(false)
public final class RepairerGameTests {
    static final BlockPos A = new BlockPos(1, 1, 1);

    private RepairerGameTests() {
    }

    /** Põe o Reparador e espera o `onLoad`, que só roda no tick seguinte. */
    static void repairer(GameTestHelper helper, java.util.function.Consumer<RepairerBlockEntity> body) {
        helper.setBlock(A, ModBlocks.CRYSTAL_REPAIRER.get().defaultBlockState());
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(
                        helper.getBlockEntity(A) instanceof RepairerBlockEntity, "sem block entity"))
                .thenExecute(() -> body.accept((RepairerBlockEntity) helper.getBlockEntity(A)))
                .thenSucceed();
    }

    private static ItemStack cracked() {
        ItemStack stack = ResonatorTestSupport.crystal(false, 0);
        SignalCrystalItem.setIntegrity(stack, 0);
        return stack;
    }

    private static IItemHandler items(GameTestHelper helper) {
        return helper.getLevel().getCapability(Capabilities.ItemHandler.BLOCK, helper.absolutePos(A), Direction.NORTH);
    }

    @GameTest(template = "empty")
    public static void itensEntramNosSlotsCertos(GameTestHelper helper) {
        repairer(helper, repairer -> {
            IItemHandler handler = items(helper);
            helper.assertTrue(handler != null, "sem handler de itens");
            helper.assertTrue(handler.insertItem(0, cracked(), false).isEmpty(), "o rachado não entrou");
            helper.assertTrue(SignalCrystalItem.isCracked(repairer.items().get(0)), "o slot 0 não tem o rachado");
            ItemStack full = ResonatorTestSupport.crystal(false, CrystalCharge.FULL);
            helper.assertValueEqual(handler.insertItem(1, full, false).getCount(), 1, "o Sinal cheio entrou na entrada");
            helper.assertValueEqual(handler.insertItem(1, ResonatorTestSupport.crystal(true, CrystalCharge.FULL), false).getCount(), 1,
                    "o Lapidado cheio entrou na entrada");
            helper.assertValueEqual(handler.insertItem(1, ResonatorTestSupport.crystal(false, 0), false).getCount(), 1,
                    "o Opaco entrou na entrada");
            helper.assertTrue(repairer.items().get(1).isEmpty(), "a entrada 1 devia estar vazia");
            helper.assertTrue(handler.insertItem(RepairerSlots.FEED, full.copyWithCount(64), false).isEmpty(), "o estoque não aceitou 64");
            helper.assertValueEqual(handler.insertItem(RepairerSlots.FEED, full.copyWithCount(1), false).getCount(), 1, "o 65º entrou");
            helper.assertValueEqual(repairer.items().get(RepairerSlots.FEED).getCount(), 64, "o estoque");
            ItemStack cut = ResonatorTestSupport.crystal(true, CrystalCharge.FULL);
            helper.assertValueEqual(handler.insertItem(RepairerSlots.FEED, cut, false).getCount(), 1, "o Lapidado entrou no estoque");
            for (int i = RepairerSlots.OUTPUT_FIRST; i < RepairerSlots.TOTAL; i++) {
                helper.assertValueEqual(handler.insertItem(i, cracked(), false).getCount(), 1, "entrou na saída " + i);
                helper.assertTrue(repairer.items().get(i).isEmpty(), "a saída " + i + " tem item");
            }
        });
    }

    @GameTest(template = "empty")
    public static void entradaTemLimiteDeUm(GameTestHelper helper) {
        repairer(helper, repairer -> {
            IItemHandler handler = items(helper);
            helper.assertValueEqual(handler.getSlotLimit(0), 1, "limite da entrada");
            helper.assertValueEqual(handler.getSlotLimit(RepairerSlots.FEED), 64, "limite do estoque");
            helper.assertTrue(handler.insertItem(0, cracked(), false).isEmpty(), "a primeira não entrou");
            helper.assertValueEqual(handler.insertItem(0, cracked(), false).getCount(), 1, "a segunda entrou no slot cheio");
            helper.assertValueEqual(repairer.items().get(0).getCount(), 1, "o slot passou de 1");
        });
    }

    @GameTest(template = "empty")
    public static void soAsSaidasSaem(GameTestHelper helper) {
        repairer(helper, repairer -> {
            IItemHandler handler = items(helper);
            ItemStack full = ResonatorTestSupport.crystal(false, CrystalCharge.FULL);
            repairer.items().set(0, cracked());
            repairer.items().set(RepairerSlots.FEED, full.copyWithCount(5));
            repairer.items().set(RepairerSlots.OUTPUT_FIRST, full.copyWithCount(3));
            helper.assertTrue(handler.extractItem(0, 1, false).isEmpty(), "saiu da entrada");
            helper.assertTrue(handler.extractItem(RepairerSlots.FEED, 1, false).isEmpty(), "saiu do estoque");
            helper.assertTrue(!repairer.items().get(0).isEmpty(), "a entrada foi esvaziada");
            helper.assertValueEqual(handler.extractItem(RepairerSlots.OUTPUT_FIRST, 2, true).getCount(), 2, "simulado");
            helper.assertValueEqual(repairer.items().get(RepairerSlots.OUTPUT_FIRST).getCount(), 3, "simulado tirou");
            helper.assertValueEqual(handler.extractItem(RepairerSlots.OUTPUT_FIRST, 2, false).getCount(), 2, "saída");
            helper.assertValueEqual(repairer.items().get(RepairerSlots.OUTPUT_FIRST).getCount(), 1, "sobrou");
        });
    }

    @GameTest(template = "empty")
    public static void energiaSoEntra(GameTestHelper helper) {
        repairer(helper, repairer -> {
            IEnergyStorage fe = helper.getLevel().getCapability(Capabilities.EnergyStorage.BLOCK, helper.absolutePos(A), Direction.NORTH);
            BulkEnergy bulk = helper.getLevel().getCapability(BulkEnergy.BLOCK, helper.absolutePos(A), Direction.NORTH);
            helper.assertTrue(fe != null && bulk != null, "faltam capabilities de energia");
            helper.assertTrue(fe.canReceive() && !fe.canExtract(), "canReceive/canExtract");
            helper.assertTrue(repairer.energy().capacity() > 1500, "a capacidade é pequena demais para o teste");
            helper.assertValueEqual(fe.receiveEnergy(1000, false), 1000, "FE recebido");
            helper.assertValueEqual(repairer.energy().stored(), 1000L, "guardado");
            helper.assertValueEqual(fe.extractEnergy(500, false), 0, "FE devolvido");
            helper.assertValueEqual(bulk.insert(500, false), 500L, "bulk guardou");
            helper.assertValueEqual(bulk.extract(500, false), 0L, "bulk devolveu");
            helper.assertValueEqual(repairer.energy().stored(), 1500L, "guardado depois");
        });
    }

    @GameTest(template = "empty")
    public static void capacidadeDoBufferEhOFatorVezesOUltimateVezesOito(GameTestHelper helper) {
        repairer(helper, repairer -> {
            long expected = RepairMath.bufferCapacity(
                    RepairMath.cost(Config.resonatorFePerCrystal(RouterTier.ULTIMATE), Config.repairCostFactor()), RepairMath.LINES);
            helper.assertValueEqual(repairer.energy().capacity(), expected, "capacidade");
        });
    }

    @GameTest(template = "empty")
    public static void nbtGuardaSlotsEEnergia(GameTestHelper helper) {
        repairer(helper, repairer -> {
            helper.assertValueEqual(repairer.container().getContainerSize(), RepairerSlots.TOTAL, "getContainerSize");
            repairer.items().set(2, cracked());
            repairer.items().set(RepairerSlots.OUTPUT_FIRST + 3, ResonatorTestSupport.crystal(true, CrystalCharge.FULL));
            repairer.energy().insert(777, false);
            CompoundTag tag = repairer.saveWithFullMetadata(helper.getLevel().registryAccess());
            repairer.items().clear();
            repairer.energy().set(0);
            repairer.loadWithComponents(tag, helper.getLevel().registryAccess());
            helper.assertTrue(SignalCrystalItem.isCracked(repairer.items().get(2)), "o rachado não voltou");
            helper.assertTrue(SignalCrystalItem.isFull(repairer.items().get(RepairerSlots.OUTPUT_FIRST + 3)), "a saída não voltou");
            helper.assertTrue(repairer.items().get(0).isEmpty(), "um slot vazio ganhou item");
            helper.assertValueEqual(repairer.energy().stored(), 777L, "energia");
        });
    }

    @GameTest(template = "empty")
    public static void temDezesseteSlotsVazios(GameTestHelper helper) {
        repairer(helper, repairer -> {
            helper.assertValueEqual(repairer.items().size(), RepairerSlots.TOTAL, "slots");
            for (int i = 0; i < RepairerSlots.TOTAL; i++) {
                helper.assertTrue(repairer.items().get(i).isEmpty(), "o slot " + i + " começa vazio");
            }
        });
    }
}
