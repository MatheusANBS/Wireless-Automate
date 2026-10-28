package io.github.matheusanbs.wirelessautomate.gametest;

import static io.github.matheusanbs.wirelessautomate.gametest.ResonatorTestSupport.assertFormed;

import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.block.RouterBlock;
import io.github.matheusanbs.wirelessautomate.block.RouterBlockEntity;
import io.github.matheusanbs.wirelessautomate.block.RouterTier;
import io.github.matheusanbs.wirelessautomate.filter.Filter;
import io.github.matheusanbs.wirelessautomate.filter.FilterEntry;
import io.github.matheusanbs.wirelessautomate.gametest.ResonatorTestSupport.Layout;
import io.github.matheusanbs.wirelessautomate.network.NetworkManager;
import io.github.matheusanbs.wirelessautomate.network.NetworkSavedData;
import io.github.matheusanbs.wirelessautomate.network.PortMode;
import io.github.matheusanbs.wirelessautomate.network.ResourceType;
import io.github.matheusanbs.wirelessautomate.registry.ModBlocks;
import io.github.matheusanbs.wirelessautomate.resonator.CrystalCharge;
import io.github.matheusanbs.wirelessautomate.resonator.ResonanceCoreBlockEntity;
import io.github.matheusanbs.wirelessautomate.storage.StorageBatteryBlockEntity;
import io.github.matheusanbs.wirelessautomate.storage.StorageKind;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Medições do motor (não afirmam o comportamento, só registram no log as linhas "MEDIDA"): a energia que o
 * Ressonador entrega por tick e o atraso do sono com máquinas que mudam sem avisar.
 */
@GameTestHolder(WirelessAutomate.MODID)
@PrefixGameTestTemplate(false)
public final class MotorMedidaGameTests {
    private static final BlockPos ORIGIN = new BlockPos(1, 1, 1);
    private static final BlockPos TOP_ENERGY = ORIGIN.offset(2, 4, 2);
    private static final BlockPos CELL_A = new BlockPos(0, 1, 0);
    private static final BlockPos CELL_B = new BlockPos(4, 1, 0);
    /** O teto do sono da energia ({@code NetworkManager.SCALAR_MAX_SLEEP_TICKS}, 20) com folga de um tick por salto. */
    private static final long LIMIT = 25;

    private MotorMedidaGameTests() {
    }

    /** A energia que chega à Bateria a cada tick, com o Núcleo gerando pelo lote natural do gerenciador. */
    @GameTest(template = "empty7", batch = "wirelessautomate_medida", timeoutTicks = 400)
    public static void medidaPulsoDoRessonador(GameTestHelper helper) {
        UUID network = network(helper, "medida-pulso");
        BlockPos corePos = ResonatorTestSupport.build(helper, ORIGIN, Layout.ENERGY_ON_TOP, RouterTier.BASIC);
        BlockPos batteryPos = new BlockPos(0, 1, 0);
        helper.setBlock(batteryPos, ModBlocks.STORAGE.get(StorageKind.BATTERY).get().defaultBlockState()
                .setValue(RouterBlock.TIER, RouterTier.BASIC));
        RouterBlockEntity source = router(helper, TOP_ENERGY, Direction.UP, ResourceType.ENERGY, network, PortMode.EXTRACT);
        RouterBlockEntity target = router(helper, batteryPos, Direction.UP, ResourceType.ENERGY, network, PortMode.INSERT);
        List<Long> deltas = new ArrayList<>();
        long[] last = new long[1];
        helper.startSequence()
                .thenWaitUntil(() -> assertFormed(helper, corePos, true))
                .thenWaitUntil(() -> helper.assertTrue(NetworkManager.get().contains(target)
                        && NetworkManager.get().contains(source), "roteadores não registrados"))
                .thenExecute(() -> {
                    ResonanceCoreBlockEntity core = ResonatorTestSupport.core(helper, corePos);
                    core.setStationForTest(98.0);
                    core.setDial(98.0);
                    core.insertCrystal(ResonatorTestSupport.crystal(false, CrystalCharge.FULL));
                    WirelessAutomate.LOGGER.info("MEDIDA pulso: geração {} FE/t, buffer {}, máximo {}", core.generationPerTick(),
                            core.capacity(), core.maxOutput());
                })
                .thenExecuteFor(100, () -> {
                    StorageBatteryBlockEntity battery = helper.getBlockEntity(batteryPos);
                    long stored = battery.store().stored();
                    deltas.add(stored - last[0]);
                    last[0] = stored;
                })
                .thenExecute(() -> WirelessAutomate.LOGGER.info("MEDIDA pulso: FE entregues por tick em 100 ticks: {}", deltas))
                .thenSucceed();
    }

    /** Atraso para o destino que esvaziou sem avisar voltar a receber, depois de 150 ticks cheio. */
    @GameTest(template = "empty7", batch = "wirelessautomate_medida", timeoutTicks = 1500)
    public static void medidaDestinoSilencioso(GameTestHelper helper) {
        UUID network = network(helper, "medida-destino");
        helper.setBlock(CELL_A, TestMachines.SILENT_CELL);
        helper.setBlock(CELL_B, TestMachines.SILENT_CELL);
        TestMachines.reset(helper.absolutePos(CELL_A));
        TestMachines.reset(helper.absolutePos(CELL_B));
        TestMachines.SilentCell a = TestMachines.silentCell(helper.absolutePos(CELL_A));
        TestMachines.SilentCell b = TestMachines.silentCell(helper.absolutePos(CELL_B));
        RouterBlockEntity source = router(helper, CELL_A, Direction.UP, ResourceType.ENERGY, network, PortMode.EXTRACT);
        RouterBlockEntity target = router(helper, CELL_B, Direction.UP, ResourceType.ENERGY, network, PortMode.INSERT);
        List<Long> delays = new ArrayList<>();
        // state[0]: 0 enchendo, 1 cheio esperando, 2 drenado esperando voltar; state[1]: o tick da última mudança
        long[] state = new long[2];
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(NetworkManager.get().contains(target)
                        && NetworkManager.get().contains(source), "roteadores não registrados"))
                .thenWaitUntil(() -> {
                    a.setStored(1_000);
                    long tick = helper.getTick();
                    if (state[0] == 0 && b.stored() >= TestMachines.SilentCell.CAPACITY) {
                        state[0] = 1;
                        state[1] = tick;
                    }
                    if (state[0] == 1 && tick - state[1] >= 150) {
                        b.setStored(0);
                        state[0] = 2;
                        state[1] = tick;
                    }
                    if (state[0] == 2 && b.stored() > 0) {
                        delays.add(tick - state[1]);
                        state[0] = 0;
                    }
                    helper.assertTrue(delays.size() >= 3, "medindo: " + delays + " estado " + state[0]);
                })
                .thenExecute(() -> WirelessAutomate.LOGGER.info(
                        "MEDIDA destino silencioso: ticks até voltar a receber depois de 150 cheio (3 vezes): {}", delays))
                .thenExecute(() -> helper.assertTrue(delays.stream().allMatch(d -> d <= LIMIT),
                        "o destino de energia demorou além do teto do sono: " + delays))
                .thenSucceed();
    }

    /** Atraso para a origem vazia que ganhou energia sem avisar voltar a mover, depois de 150 ticks vazia. */
    @GameTest(template = "empty7", batch = "wirelessautomate_medida", timeoutTicks = 1500)
    public static void medidaOrigemSilenciosa(GameTestHelper helper) {
        UUID network = network(helper, "medida-origem");
        helper.setBlock(CELL_A, TestMachines.SILENT_CELL);
        helper.setBlock(CELL_B, TestMachines.SILENT_CELL);
        TestMachines.reset(helper.absolutePos(CELL_A));
        TestMachines.reset(helper.absolutePos(CELL_B));
        TestMachines.SilentCell a = TestMachines.silentCell(helper.absolutePos(CELL_A));
        TestMachines.SilentCell b = TestMachines.silentCell(helper.absolutePos(CELL_B));
        RouterBlockEntity source = router(helper, CELL_A, Direction.UP, ResourceType.ENERGY, network, PortMode.EXTRACT);
        RouterBlockEntity target = router(helper, CELL_B, Direction.UP, ResourceType.ENERGY, network, PortMode.INSERT);
        List<Long> delays = new ArrayList<>();
        long[] state = new long[2];
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(NetworkManager.get().contains(target)
                        && NetworkManager.get().contains(source), "roteadores não registrados"))
                .thenWaitUntil(() -> {
                    long tick = helper.getTick();
                    if (state[0] == 0) {
                        state[0] = 1;
                        state[1] = tick;
                    }
                    if (state[0] == 1 && tick - state[1] >= 150) {
                        b.setStored(0);
                        a.setStored(1_000);
                        state[0] = 2;
                        state[1] = tick;
                    }
                    if (state[0] == 2 && b.stored() > 0) {
                        delays.add(tick - state[1]);
                        state[0] = 3;
                    }
                    helper.assertTrue(state[0] == 3, "medindo: estado " + state[0]);
                })
                .thenExecute(() -> WirelessAutomate.LOGGER.info(
                        "MEDIDA origem silenciosa: ticks até mover depois de 150 vazia: {}", delays))
                .thenExecute(() -> helper.assertTrue(delays.stream().allMatch(d -> d <= LIMIT),
                        "a origem de energia demorou além do teto do sono: " + delays))
                .thenSucceed();
    }

    /**
     * Itens: a origem tem pedregulho, o destino D1 filtra só diamante (acordado, recusa pelo filtro) e o D2 está cheio
     * (dorme). Depois de 150 ticks o D2 ganha espaço (com aviso, como um baú); mede quantos ticks até o pedregulho
     * chegar. O controle é o mesmo sem o D1.
     */
    @GameTest(template = "empty7", batch = "wirelessautomate_medida", timeoutTicks = 800)
    public static void medidaItensComFiltroNoOutroDestino(GameTestHelper helper) {
        measureItems(helper, true, "com um destino que recusa pelo filtro");
    }

    @GameTest(template = "empty7", batch = "wirelessautomate_medida", timeoutTicks = 800)
    public static void medidaItensControleSemFiltro(GameTestHelper helper) {
        measureItems(helper, false, "controle, só o destino cheio");
    }

    private static void measureItems(GameTestHelper helper, boolean withFilteredDestination, String label) {
        UUID network = network(helper, "medida-itens-" + withFilteredDestination);
        BlockPos sourcePos = new BlockPos(0, 1, 0);
        BlockPos filteredPos = new BlockPos(3, 1, 0);
        BlockPos fullPos = new BlockPos(6, 1, 0);
        helper.setBlock(sourcePos, Blocks.CHEST);
        helper.setBlock(filteredPos, Blocks.CHEST);
        helper.setBlock(fullPos, Blocks.CHEST);
        ChestBlockEntity source = helper.getBlockEntity(sourcePos);
        ChestBlockEntity full = helper.getBlockEntity(fullPos);
        for (int i = 0; i < full.getContainerSize(); i++) {
            full.setItem(i, new ItemStack(Items.STONE, 64));
        }
        source.setItem(0, new ItemStack(Items.COBBLESTONE, 64));
        RouterBlockEntity sourceRouter = router(helper, sourcePos, Direction.UP, ResourceType.ITEM, network, PortMode.EXTRACT);
        RouterBlockEntity fullRouter = router(helper, fullPos, Direction.UP, ResourceType.ITEM, network, PortMode.INSERT);
        RouterBlockEntity filteredRouter = null;
        if (withFilteredDestination) {
            filteredRouter = router(helper, filteredPos, Direction.UP, ResourceType.ITEM, network, PortMode.INSERT);
            filteredRouter.setFilter(ResourceType.ITEM, Direction.UP, new Filter(Filter.ListMode.WHITELIST, false,
                    List.of(new FilterEntry.ItemEntry(new ItemStack(Items.DIAMOND), 0))));
        }
        List<Long> delay = new ArrayList<>();
        long[] state = new long[2];
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(NetworkManager.get().contains(sourceRouter)
                        && NetworkManager.get().contains(fullRouter), "roteadores não registrados"))
                .thenWaitUntil(() -> {
                    long tick = helper.getTick();
                    if (state[0] == 0) {
                        state[0] = 1;
                        state[1] = tick;
                    }
                    if (state[0] == 1 && tick - state[1] >= 150) {
                        full.setItem(0, ItemStack.EMPTY);
                        state[0] = 2;
                        state[1] = tick;
                    }
                    if (state[0] == 2 && full.getItem(0).is(Items.COBBLESTONE)) {
                        delay.add(tick - state[1]);
                        state[0] = 3;
                    }
                    helper.assertTrue(state[0] == 3, "medindo: estado " + state[0]);
                })
                .thenExecute(() -> WirelessAutomate.LOGGER.info(
                        "MEDIDA itens ({}): ticks até o pedregulho chegar depois de 150 com o destino cheio: {}", label, delay))
                .thenSucceed();
    }

    private static UUID network(GameTestHelper helper, String name) {
        return NetworkSavedData.get(helper.getLevel().getServer()).create(UUID.randomUUID(), name).id();
    }

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
}
