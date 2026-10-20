package io.github.matheusanbs.wirelessautomate.gametest;

import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.block.RouterBlock;
import io.github.matheusanbs.wirelessautomate.block.RouterBlockEntity;
import io.github.matheusanbs.wirelessautomate.block.RouterTier;
import io.github.matheusanbs.wirelessautomate.gametest.ResonatorTestSupport.Layout;
import io.github.matheusanbs.wirelessautomate.network.NetworkManager;
import io.github.matheusanbs.wirelessautomate.network.NetworkSavedData;
import io.github.matheusanbs.wirelessautomate.network.PortMode;
import io.github.matheusanbs.wirelessautomate.network.ResourceType;
import io.github.matheusanbs.wirelessautomate.registry.ModBlocks;
import io.github.matheusanbs.wirelessautomate.registry.ModItems;
import io.github.matheusanbs.wirelessautomate.resonator.CrystalCharge;
import io.github.matheusanbs.wirelessautomate.resonator.ResonanceCoreBlockEntity;
import io.github.matheusanbs.wirelessautomate.resonator.ResonatorBatch;
import io.github.matheusanbs.wirelessautomate.resonator.ResonatorPartBlock;
import io.github.matheusanbs.wirelessautomate.resonator.SignalCrystalItem;
import io.github.matheusanbs.wirelessautomate.storage.BulkEnergy;
import io.github.matheusanbs.wirelessautomate.storage.StorageBatteryBlockEntity;
import io.github.matheusanbs.wirelessautomate.storage.StorageKind;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.items.IItemHandler;

/**
 * Etapa 2 do Ressonador de Sinal: as Portas de Energia e de Cristais, com a capability pelo índice das Portas.
 * A Energia só tira; a de Cristais aceita cristal numa câmara vazia e redstone até 64, e só devolve o Opaco com o
 * Núcleo sem redstone.
 */
@GameTestHolder(WirelessAutomate.MODID)
@PrefixGameTestTemplate(false)
public final class ResonatorPortGameTests {
    private static final BlockPos ORIGIN = new BlockPos(1, 1, 1);
    /** As Portas da Vitrine, relativas ao teste. */
    private static final BlockPos VITRINE_ENERGY = ORIGIN.offset(0, 2, 2);
    private static final BlockPos CRYSTAL_PORT = ORIGIN.offset(2, 2, 0);
    /** A Porta de Energia do layout {@link Layout#ENERGY_ON_TOP}. */
    private static final BlockPos TOP_ENERGY = ORIGIN.offset(2, 4, 2);
    /** Um sono bem maior que o teto do backoff ({@code NetworkManager.MAX_SLEEP_TICKS}, 100 ticks). */
    private static final int LONG_SLEEP = 1_000;

    private ResonatorPortGameTests() {
    }

    @GameTest(template = "empty7")
    public static void portaDeEnergiaSoTira(GameTestHelper helper) {
        formed(helper, Layout.VITRINE, RouterTier.BASIC, core -> {
            tuned(core);
            helper.assertTrue(core.insertCrystal(crystal(false, CrystalCharge.FULL)).isEmpty(), "o cristal não entrou");
            ResonatorBatch.run(helper.getLevel(), core, 20);
            long before = core.energy();
            helper.assertTrue(before > 1_000, "o lote não gerou o bastante: " + before);
            BlockPos port = helper.absolutePos(VITRINE_ENERGY);
            IEnergyStorage energy = helper.getLevel().getCapability(Capabilities.EnergyStorage.BLOCK, port, Direction.WEST);
            helper.assertTrue(energy != null, "a Porta de Energia sem capability");
            helper.assertValueEqual(energy.receiveEnergy(100, false), 0, "a Porta não recebe");
            helper.assertTrue(!energy.canReceive(), "canReceive");
            helper.assertTrue(energy.canExtract(), "canExtract");
            helper.assertValueEqual(energy.extractEnergy(1_000, false), 1_000, "energia tirada");
            helper.assertValueEqual(core.energy(), before - 1_000, "o Núcleo perdeu o que saiu");
            helper.assertValueEqual(energy.getEnergyStored(), (int) core.energy(), "guardado");
            helper.assertValueEqual(energy.getMaxEnergyStored(), (int) core.capacity(), "capacidade");
            BulkEnergy bulk = helper.getLevel().getCapability(BulkEnergy.BLOCK, port, Direction.WEST);
            helper.assertTrue(bulk != null, "a Porta de Energia sem BulkEnergy");
            helper.assertValueEqual(bulk.insert(100L, false), 0L, "o BulkEnergy não recebe");
            long rest = core.energy();
            helper.assertValueEqual(bulk.extract(Long.MAX_VALUE, true), rest, "simulado em long");
            helper.assertValueEqual(bulk.extract(Long.MAX_VALUE, false), rest, "tirado em long");
            helper.assertValueEqual(core.energy(), 0L, "o Núcleo vazio");
        });
    }

    @GameTest(template = "empty7")
    public static void portaSemEstruturaNaoTemCapability(GameTestHelper helper) {
        BlockPos loose = new BlockPos(0, 1, 0);
        helper.setBlock(loose, ModBlocks.RESONATOR_ENERGY_PORT.get().defaultBlockState()
                .setValue(ResonatorPartBlock.FACING, Direction.NORTH));
        BlockPos looseCrystals = new BlockPos(6, 1, 0);
        helper.setBlock(looseCrystals, ModBlocks.RESONATOR_CRYSTAL_PORT.get().defaultBlockState()
                .setValue(ResonatorPartBlock.FACING, Direction.NORTH));
        helper.assertTrue(energy(helper, loose) == null, "Porta de Energia solta com capability");
        helper.assertTrue(helper.getLevel().getCapability(Capabilities.ItemHandler.BLOCK, helper.absolutePos(looseCrystals),
                Direction.NORTH) == null, "Porta de Cristais solta com capability");
        BlockPos corePos = ResonatorTestSupport.build(helper, ORIGIN, Layout.VITRINE, RouterTier.BASIC);
        helper.startSequence().thenWaitUntil(() -> assertFormed(helper, corePos, true))
                .thenExecute(() -> {
                    helper.assertTrue(energy(helper, VITRINE_ENERGY) != null, "a Porta montada sem capability");
                    helper.assertTrue(items(helper) != null, "a Porta de Cristais montada sem capability");
                    helper.getLevel().destroyBlock(helper.absolutePos(ORIGIN.offset(2, 0, 0)), false);
                })
                .thenWaitUntil(() -> assertFormed(helper, corePos, false))
                .thenExecute(() -> {
                    helper.assertTrue(energy(helper, VITRINE_ENERGY) == null, "Porta desmontada com capability");
                    helper.assertTrue(items(helper) == null, "Porta de Cristais desmontada com capability");
                })
                .thenSucceed();
    }

    @GameTest(template = "empty7")
    public static void portaDeCristaisAceitaCristalERedstone(GameTestHelper helper) {
        formed(helper, Layout.VITRINE, RouterTier.ELITE, core -> {
            IItemHandler items = items(helper);
            helper.assertTrue(items != null, "a Porta de Cristais sem capability");
            int chambers = core.chambers();
            helper.assertValueEqual(items.getSlots(), chambers + 1, "câmaras + redstone");
            helper.assertValueEqual(items.getSlotLimit(0), 1, "limite da câmara");
            helper.assertValueEqual(items.getSlotLimit(chambers), 64, "limite da redstone");
            ItemStack two = new ItemStack(ModItems.SIGNAL_CRYSTAL.get(), 2);
            helper.assertValueEqual(items.insertItem(0, two, true).getCount(), 1, "simulado: uma unidade por câmara");
            helper.assertTrue(core.crystals().get(0).isEmpty(), "a simulação não pôs");
            helper.assertValueEqual(items.insertItem(0, new ItemStack(ModItems.SIGNAL_CRYSTAL.get()), false).getCount(), 0,
                    "o primeiro cristal entra");
            helper.assertValueEqual(items.insertItem(0, new ItemStack(ModItems.SIGNAL_CRYSTAL.get()), false).getCount(), 1,
                    "câmara ocupada recusa");
            helper.assertValueEqual(items.insertItem(1, new ItemStack(ModItems.CUT_SIGNAL_CRYSTAL.get()), false).getCount(), 0,
                    "o segundo entra na próxima");
            helper.assertTrue(core.crystals().get(0).is(ModItems.SIGNAL_CRYSTAL.get()), "o cristal na câmara 0");
            helper.assertTrue(core.crystals().get(1).is(ModItems.CUT_SIGNAL_CRYSTAL.get()), "o lapidado na câmara 1");
            helper.assertValueEqual(items.insertItem(2, new ItemStack(Items.STONE), false).getCount(), 1, "pedra recusada");
            helper.assertTrue(!items.isItemValid(2, new ItemStack(Items.STONE)), "pedra não vale");
            helper.assertValueEqual(items.insertItem(chambers, new ItemStack(ModItems.SIGNAL_CRYSTAL.get()), false).getCount(),
                    1, "cristal no slot da redstone");
            helper.assertValueEqual(items.insertItem(2, new ItemStack(Items.REDSTONE), false).getCount(), 1,
                    "redstone numa câmara");
            // O Elite recarrega só com o Pó de Ressonância: a redstone fica de fora.
            helper.assertValueEqual(items.insertItem(chambers, new ItemStack(Items.REDSTONE, 5), false).getCount(), 5,
                    "redstone no Elite");
            helper.assertValueEqual(items.insertItem(chambers, new ItemStack(ModItems.RESONANCE_DUST.get(), 70), false)
                    .getCount(), 6, "70 de Pó: entram 64");
            helper.assertValueEqual(core.redstone(), 64, "Pó no Núcleo");
            helper.assertValueEqual(items.getStackInSlot(chambers).getCount(), 64, "o Pó no slot");
            helper.assertTrue(items.extractItem(chambers, 64, false).isEmpty(), "o catalisador nunca sai");
            helper.assertValueEqual(core.redstone(), 64, "o Pó ficou");
        });
    }

    @GameTest(template = "empty7")
    public static void opacoSoSaiSemRedstone(GameTestHelper helper) {
        formed(helper, Layout.VITRINE, RouterTier.ADVANCED, core -> {
            IItemHandler items = items(helper);
            helper.assertTrue(items != null, "a Porta de Cristais sem capability");
            helper.assertValueEqual(items.insertItem(0, crystal(false, 0), false).getCount(), 0, "o Opaco entra");
            helper.assertValueEqual(items.insertItem(1, crystal(false, CrystalCharge.FULL), false).getCount(), 0,
                    "o cheio entra");
            helper.assertValueEqual(items.insertItem(core.chambers(), new ItemStack(Items.REDSTONE), false).getCount(), 0,
                    "1 de redstone");
            helper.assertTrue(items.extractItem(0, 1, false).isEmpty(), "com redstone o Opaco fica");
            core.setRedstoneForTest(0);
            helper.assertTrue(items.extractItem(1, 1, false).isEmpty(), "o cristal com carga nunca sai");
            ItemStack simulated = items.extractItem(0, 1, true);
            helper.assertTrue(SignalCrystalItem.isDull(simulated), "simulado devolve o Opaco");
            helper.assertTrue(!core.crystals().get(0).isEmpty(), "a simulação não tira");
            ItemStack taken = items.extractItem(0, 1, false);
            helper.assertTrue(SignalCrystalItem.isDull(taken), "sem redstone o Opaco sai: " + taken);
            helper.assertTrue(core.crystals().get(0).isEmpty(), "a câmara fica vazia");
            helper.assertTrue(!core.crystals().get(1).isEmpty(), "o cheio continua");
        });
    }

    /**
     * O aviso da Porta de Energia: o roteador dela dorme num sono bem maior que o teto do backoff, o lote dá a
     * primeira energia ao Núcleo e o aviso acorda o roteador na hora (pelo {@code onNeighborChange}, com ele em cima
     * da Porta); a Bateria do outro roteador recebe energia em poucos ticks.
     */
    @GameTest(template = "empty7", timeoutTicks = 200)
    public static void roteadorTiraEnergiaDaPorta(GameTestHelper helper) {
        UUID network = network(helper, "teste-porta-de-energia");
        BlockPos corePos = ResonatorTestSupport.build(helper, ORIGIN, Layout.ENERGY_ON_TOP, RouterTier.BASIC);
        BlockPos batteryPos = new BlockPos(0, 1, 0);
        helper.setBlock(batteryPos, ModBlocks.STORAGE.get(StorageKind.BATTERY).get().defaultBlockState()
                .setValue(RouterBlock.TIER, RouterTier.BASIC));
        RouterBlockEntity source = router(helper, TOP_ENERGY, Direction.UP, ResourceType.ENERGY, network, PortMode.EXTRACT);
        RouterBlockEntity target = router(helper, batteryPos, Direction.UP, ResourceType.ENERGY, network, PortMode.INSERT);
        long[] woke = new long[1];
        helper.startSequence()
                .thenWaitUntil(() -> assertFormed(helper, corePos, true))
                .thenWaitUntil(() -> helper.assertTrue(NetworkManager.get().contains(target), "destino não registrado"))
                .thenWaitUntil(() -> assertSleeps(helper, source, ResourceType.ENERGY, Direction.UP))
                .thenExecute(() -> {
                    ResonanceCoreBlockEntity core = ResonatorTestSupport.core(helper, corePos);
                    tuned(core);
                    helper.assertTrue(core.insertCrystal(crystal(false, CrystalCharge.FULL)).isEmpty(),
                            "o cristal não entrou");
                    assertSleeps(helper, source, ResourceType.ENERGY, Direction.UP);
                    ResonatorBatch.run(helper.getLevel(), core, 20);
                    helper.assertTrue(core.energy() > 0, "o lote não gerou");
                    assertAwake(helper, source, ResourceType.ENERGY, Direction.UP, "o aviso não acordou o roteador da Porta");
                    woke[0] = helper.getTick();
                })
                .thenWaitUntil(() -> {
                    StorageBatteryBlockEntity battery = helper.getBlockEntity(batteryPos);
                    helper.assertTrue(battery.store().stored() > 0, "a Bateria ainda sem energia");
                })
                .thenExecute(() -> helper.assertTrue(helper.getTick() - woke[0] <= 20,
                        "a energia levou " + (helper.getTick() - woke[0]) + " ticks: veio pelo backoff"))
                .thenSucceed();
    }

    /**
     * O aviso da Porta de Cristais no lote: um cristal que fica Opaco com o Núcleo sem redstone acorda na hora o
     * roteador que tira dela, e o Opaco chega ao baú em poucos ticks.
     */
    @GameTest(template = "empty7", timeoutTicks = 200)
    public static void opacoNovoAcordaQuemTiraDaPortaDeCristais(GameTestHelper helper) {
        UUID network = network(helper, "teste-porta-de-cristais-opaco");
        BlockPos corePos = ResonatorTestSupport.build(helper, ORIGIN, Layout.VITRINE, RouterTier.BASIC);
        BlockPos chestPos = new BlockPos(0, 1, 0);
        helper.setBlock(chestPos, Blocks.CHEST);
        RouterBlockEntity source = router(helper, CRYSTAL_PORT, Direction.NORTH, ResourceType.ITEM, network, PortMode.EXTRACT);
        RouterBlockEntity target = router(helper, chestPos, Direction.UP, ResourceType.ITEM, network, PortMode.INSERT);
        long[] woke = new long[1];
        helper.startSequence()
                .thenWaitUntil(() -> assertFormed(helper, corePos, true))
                .thenWaitUntil(() -> helper.assertTrue(NetworkManager.get().contains(target), "destino não registrado"))
                .thenWaitUntil(() -> assertSleeps(helper, source, ResourceType.ITEM, Direction.NORTH))
                .thenExecute(() -> {
                    ResonanceCoreBlockEntity core = ResonatorTestSupport.core(helper, corePos);
                    tuned(core);
                    helper.assertTrue(core.insertCrystal(crystal(false, 1)).isEmpty(), "o cristal não entrou");
                    assertSleeps(helper, source, ResourceType.ITEM, Direction.NORTH);
                    ResonatorBatch.run(helper.getLevel(), core, 20);
                    helper.assertTrue(SignalCrystalItem.isDull(core.crystals().get(0)), "o cristal não ficou Opaco");
                    assertAwake(helper, source, ResourceType.ITEM, Direction.NORTH, "o Opaco novo não acordou o roteador");
                    woke[0] = helper.getTick();
                })
                .thenWaitUntil(() -> {
                    ChestBlockEntity chest = helper.getBlockEntity(chestPos);
                    helper.assertTrue(SignalCrystalItem.isDull(chest.getItem(0)), "o Opaco ainda não chegou ao baú");
                })
                .thenExecute(() -> helper.assertTrue(helper.getTick() - woke[0] <= 20,
                        "o Opaco levou " + (helper.getTick() - woke[0]) + " ticks: veio pelo backoff"))
                .thenSucceed();
    }

    /** Esvaziar uma câmara (pela mão ou por outra Porta) acorda na hora quem põe cristais pela Porta de Cristais. */
    @GameTest(template = "empty7")
    public static void camaraVaziaAcordaQuemPoeNaPortaDeCristais(GameTestHelper helper) {
        UUID network = network(helper, "teste-porta-de-cristais-camara");
        BlockPos corePos = ResonatorTestSupport.build(helper, ORIGIN, Layout.VITRINE, RouterTier.BASIC);
        BlockPos chestPos = new BlockPos(0, 1, 0);
        helper.setBlock(chestPos, Blocks.CHEST);
        RouterBlockEntity feeder = router(helper, chestPos, Direction.UP, ResourceType.ITEM, network, PortMode.EXTRACT);
        RouterBlockEntity target = router(helper, CRYSTAL_PORT, Direction.NORTH, ResourceType.ITEM, network, PortMode.INSERT);
        helper.startSequence()
                .thenWaitUntil(() -> assertFormed(helper, corePos, true))
                .thenWaitUntil(() -> helper.assertTrue(NetworkManager.get().contains(feeder), "origem não registrada"))
                .thenWaitUntil(() -> assertSleeps(helper, target, ResourceType.ITEM, Direction.NORTH))
                .thenExecute(() -> {
                    ResonanceCoreBlockEntity core = ResonatorTestSupport.core(helper, corePos);
                    helper.assertTrue(core.insertCrystal(crystal(false, CrystalCharge.FULL)).isEmpty(),
                            "o cristal não entrou");
                    assertSleeps(helper, target, ResourceType.ITEM, Direction.NORTH);
                    helper.assertTrue(!core.takeLastCrystal().isEmpty(), "não tirou o cristal");
                    assertAwake(helper, target, ResourceType.ITEM, Direction.NORTH, "a câmara vazia não acordou a Porta");
                })
                .thenSucceed();
    }

    private static UUID network(GameTestHelper helper, String name) {
        return NetworkSavedData.get(helper.getLevel().getServer()).create(UUID.randomUUID(), name).id();
    }

    /** Põe a porta do roteador num sono bem maior que o teto do backoff; falha se a porta ainda não existe. */
    private static void assertSleeps(GameTestHelper helper, RouterBlockEntity router, ResourceType type, Direction face) {
        long now = helper.getLevel().getServer().getTickCount();
        helper.assertTrue(NetworkManager.get().sleepForTests(router, null, type, face, now, LONG_SLEEP),
                "a porta do roteador ainda não existe");
        helper.assertTrue(!NetworkManager.get().sourceAwake(router, null, type, face, now), "a porta não dormiu");
    }

    private static void assertAwake(GameTestHelper helper, RouterBlockEntity router, ResourceType type, Direction face,
            String message) {
        long now = helper.getLevel().getServer().getTickCount();
        helper.assertTrue(NetworkManager.get().sourceAwake(router, null, type, face, now), message);
    }

    /** Roteador preso à face {@code face} de {@code machine}, de frente para fora dela, com essa face no modo dado. */
    private static RouterBlockEntity router(GameTestHelper helper, BlockPos machine, Direction face, ResourceType type,
            UUID network, PortMode mode) {
        BlockPos routerPos = machine.relative(face);
        helper.setBlock(routerPos, ModBlocks.ROUTER.get().defaultBlockState()
                .setValue(RouterBlock.FACING, face).setValue(RouterBlock.TIER, RouterTier.BASIC));
        RouterBlockEntity router = helper.getBlockEntity(routerPos);
        router.setNetworkId(type, network);
        router.setMode(type, face, mode);
        return router;
    }

    /** Monta o layout no tier, espera montar e roda {@code body} num só tick (sem o lote do gerenciador no meio). */
    private static void formed(GameTestHelper helper, Layout layout, RouterTier tier,
            Consumer<ResonanceCoreBlockEntity> body) {
        BlockPos corePos = ResonatorTestSupport.build(helper, ORIGIN, layout, tier);
        helper.startSequence().thenWaitUntil(() -> assertFormed(helper, corePos, true))
                .thenExecute(() -> body.accept(ResonatorTestSupport.core(helper, corePos))).thenSucceed();
    }

    private static IEnergyStorage energy(GameTestHelper helper, BlockPos port) {
        return helper.getLevel().getCapability(Capabilities.EnergyStorage.BLOCK, helper.absolutePos(port), Direction.WEST);
    }

    private static IItemHandler items(GameTestHelper helper) {
        return helper.getLevel().getCapability(Capabilities.ItemHandler.BLOCK, helper.absolutePos(CRYSTAL_PORT),
                Direction.NORTH);
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

    private static void assertFormed(GameTestHelper helper, BlockPos corePos, boolean formed) {
        ResonanceCoreBlockEntity core = ResonatorTestSupport.core(helper, corePos);
        helper.assertTrue(core != null && core.formed() == formed, formed ? "não montou" : "não desmontou");
    }
}
