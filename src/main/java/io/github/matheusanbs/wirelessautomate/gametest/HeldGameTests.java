package io.github.matheusanbs.wirelessautomate.gametest;

import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.block.RouterBlock;
import io.github.matheusanbs.wirelessautomate.block.RouterBlockEntity;
import io.github.matheusanbs.wirelessautomate.block.RouterTier;
import io.github.matheusanbs.wirelessautomate.network.FlowSampler;
import io.github.matheusanbs.wirelessautomate.network.NetworkManager;
import io.github.matheusanbs.wirelessautomate.network.NetworkSavedData;
import io.github.matheusanbs.wirelessautomate.network.PortMode;
import io.github.matheusanbs.wirelessautomate.network.ResourceType;
import io.github.matheusanbs.wirelessautomate.registry.ModBlocks;
import io.github.matheusanbs.wirelessautomate.storage.StorageBatteryBlockEntity;
import io.github.matheusanbs.wirelessautomate.storage.StorageChestBlockEntity;
import io.github.matheusanbs.wirelessautomate.storage.StorageKind;
import io.github.matheusanbs.wirelessautomate.storage.StorageTankBlockEntity;
import io.github.matheusanbs.wirelessautomate.tv.TvBinding;
import io.github.matheusanbs.wirelessautomate.tv.TvBlock;
import io.github.matheusanbs.wirelessautomate.tv.TvBlockEntity;
import io.github.matheusanbs.wirelessautomate.tv.TvFormat;
import io.github.matheusanbs.wirelessautomate.tv.TvLayout;
import io.github.matheusanbs.wirelessautomate.tv.TvManager;
import io.github.matheusanbs.wirelessautomate.tv.TvMetric;
import io.github.matheusanbs.wirelessautomate.tv.TvPiece;
import io.github.matheusanbs.wirelessautomate.tv.TvPieceType;
import io.github.matheusanbs.wirelessautomate.tv.TvStyle;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * O guardado na rede (métricas {@code held}, {@code held_capacity} e {@code held_fill}): o {@link FlowSampler} soma o
 * conteúdo e a capacidade das máquinas em Armazém da rede × tipo pedida, e só delas; sem pedido, não lê máquina
 * nenhuma. O lote do teste sem pedido é só dele, porque a contagem de leituras é do servidor inteiro.
 */
@GameTestHolder(WirelessAutomate.MODID)
@PrefixGameTestTemplate(false)
public final class HeldGameTests {
    private static final BlockPos BATTERY = new BlockPos(0, 1, 0);
    private static final BlockPos FULL = new BlockPos(2, 1, 0);
    private static final BlockPos CHEST = new BlockPos(0, 1, 2);
    private static final BlockPos TANK = new BlockPos(2, 1, 2);
    private static final BlockPos TV = new BlockPos(4, 1, 0);
    private static final String BATCH = "wirelessautomate_held";
    private static final String IDLE = "wirelessautomate_held_idle";

    private static long at;

    private HeldGameTests() {
    }

    private static FlowSampler flows() {
        return NetworkManager.get().flows();
    }

    private static long tick(GameTestHelper helper) {
        return helper.getLevel().getServer().getTickCount();
    }

    private static UUID network(GameTestHelper helper, UUID owner) {
        return NetworkSavedData.get(helper.getLevel().getServer()).create(owner, "teste-guardado").id();
    }

    /** A máquina do mod em {@code pos} com um roteador em cima, na rede, no modo do tipo. */
    private static RouterBlockEntity place(GameTestHelper helper, BlockPos pos, BlockState machine, UUID network,
            ResourceType type, PortMode mode) {
        helper.setBlock(pos, machine);
        helper.setBlock(pos.above(), ModBlocks.ROUTER.get().defaultBlockState()
                .setValue(RouterBlock.FACING, Direction.UP).setValue(RouterBlock.TIER, RouterTier.BASIC));
        RouterBlockEntity router = helper.getBlockEntity(pos.above());
        router.setNetworkId(network);
        router.setNetworkId(type, network);
        router.setMode(type, Direction.UP, mode);
        return router;
    }

    private static BlockState battery() {
        return ModBlocks.STORAGE.get(StorageKind.BATTERY).get().defaultBlockState();
    }

    private static void registered(GameTestHelper helper, RouterBlockEntity... routers) {
        for (RouterBlockEntity router : routers) {
            helper.assertTrue(NetworkManager.get().contains(router), "roteador não registrado");
        }
    }

    private static FlowSampler.Held held(GameTestHelper helper, UUID network, ResourceType type) {
        flows().requestHeld(network, type, tick(helper));
        FlowSampler.Held held = flows().held(network, type);
        helper.assertTrue(held != null, "o guardado de " + type.key() + " ainda não foi publicado");
        return held;
    }

    /**
     * Uma Bateria, um Baú e um Tanque em Armazém somam o que guardam e o que cabe; uma Bateria cheia em Insere, na
     * mesma rede, não entra na conta.
     */
    @GameTest(template = "empty", batch = BATCH, timeoutTicks = 300)
    public static void somaAsMaquinasEmArmazemEIgnoraAsOutras(GameTestHelper helper) {
        UUID network = network(helper, UUID.randomUUID());
        RouterBlockEntity inBattery = place(helper, BATTERY, battery(), network, ResourceType.ENERGY, PortMode.BOTH);
        RouterBlockEntity other = place(helper, FULL, battery(), network, ResourceType.ENERGY, PortMode.INSERT);
        RouterBlockEntity inChest = place(helper, CHEST, ModBlocks.STORAGE_CHEST.get().defaultBlockState(), network,
                ResourceType.ITEM, PortMode.BOTH);
        RouterBlockEntity inTank = place(helper, TANK, ModBlocks.STORAGE.get(StorageKind.TANK).get()
                .defaultBlockState(), network, ResourceType.FLUID, PortMode.BOTH);
        StorageBatteryBlockEntity battery = helper.getBlockEntity(BATTERY);
        StorageBatteryBlockEntity full = helper.getBlockEntity(FULL);
        StorageChestBlockEntity chest = helper.getBlockEntity(CHEST);
        StorageTankBlockEntity tank = helper.getBlockEntity(TANK);
        battery.store().insert(5_000, false);
        full.store().insert(full.capacity(), false);
        chest.storage().insert(new ItemStack(Items.DIAMOND), 100, false);
        chest.storage().insert(new ItemStack(Items.GOLD_INGOT), 28, false);
        tank.storage().insert(new FluidStack(Fluids.WATER, 1), 3_000, false);

        helper.startSequence()
                .thenWaitUntil(() -> registered(helper, inBattery, other, inChest, inTank))
                .thenWaitUntil(() -> {
                    FlowSampler.Held energy = held(helper, network, ResourceType.ENERGY);
                    helper.assertValueEqual(energy.stored(), 5_000L, "energia guardada só no Armazém");
                    helper.assertValueEqual(energy.capacity(), battery.capacity(), "capacidade só do Armazém");
                    FlowSampler.Held items = held(helper, network, ResourceType.ITEM);
                    helper.assertValueEqual(items.stored(), 128L, "itens no Baú");
                    helper.assertValueEqual(items.capacity(), chest.capacity(), "capacidade do Baú");
                    FlowSampler.Held fluids = held(helper, network, ResourceType.FLUID);
                    helper.assertValueEqual(fluids.stored(), 3_000L, "fluido no Tanque");
                    helper.assertValueEqual(fluids.capacity(), tank.capacity(), "capacidade do Tanque");
                })
                .thenExecute(() -> {
                    FlowSampler.Held energy = flows().held(network, ResourceType.ENERGY);
                    helper.assertValueEqual(TvMetric.HELD.readHeld(energy), 5_000.0, "HELD");
                    helper.assertValueEqual(TvMetric.HELD_CAPACITY.readHeld(energy), (double) battery.capacity(),
                            "HELD_CAPACITY");
                    helper.assertValueEqual(TvMetric.HELD_FILL.readHeld(energy), 5_000.0 / battery.capacity(),
                            "HELD_FILL");
                })
                .thenSucceed();
    }

    /** Uma Bateria cheia em Armazém dá a fração 1 e a capacidade dela. */
    @GameTest(template = "empty", batch = BATCH, timeoutTicks = 300)
    public static void bateriaCheiaDaFracaoUm(GameTestHelper helper) {
        UUID network = network(helper, UUID.randomUUID());
        RouterBlockEntity router = place(helper, BATTERY, battery(), network, ResourceType.ENERGY, PortMode.BOTH);
        StorageBatteryBlockEntity battery = helper.getBlockEntity(BATTERY);
        battery.store().insert(battery.capacity(), false);

        helper.startSequence()
                .thenWaitUntil(() -> registered(helper, router))
                .thenWaitUntil(() -> {
                    FlowSampler.Held held = held(helper, network, ResourceType.ENERGY);
                    helper.assertValueEqual(held.stored(), battery.capacity(), "guardado");
                    helper.assertValueEqual(held.capacity(), battery.capacity(), "capacidade");
                    helper.assertValueEqual(TvMetric.HELD_FILL.readHeld(held), 1.0, "cheia");
                })
                .thenSucceed();
    }

    /** Uma rede sem nenhuma máquina em Armazém dá zero guardado, não traço. */
    @GameTest(template = "empty", batch = BATCH, timeoutTicks = 300)
    public static void semArmazemDaZero(GameTestHelper helper) {
        UUID network = network(helper, UUID.randomUUID());
        RouterBlockEntity router = place(helper, FULL, battery(), network, ResourceType.ENERGY, PortMode.INSERT);

        helper.startSequence()
                .thenWaitUntil(() -> registered(helper, router))
                .thenWaitUntil(() -> {
                    FlowSampler.Held held = held(helper, network, ResourceType.ENERGY);
                    helper.assertValueEqual(held.stored(), 0L, "guardado");
                    helper.assertValueEqual(held.capacity(), 0L, "capacidade");
                    helper.assertValueEqual(TvMetric.HELD_FILL.readHeld(held), 0.0, "enchimento");
                })
                .thenSucceed();
    }

    /** Uma Tela com as três métricas pede ao amostrador pela própria visita e passa a mostrar os números. */
    @GameTest(template = "empty", batch = BATCH, timeoutTicks = 300)
    public static void aTelaPedeEOsNumerosChegam(GameTestHelper helper) {
        UUID owner = UUID.randomUUID();
        UUID network = network(helper, owner);
        RouterBlockEntity router = place(helper, BATTERY, battery(), network, ResourceType.ENERGY, PortMode.BOTH);
        StorageBatteryBlockEntity battery = helper.getBlockEntity(BATTERY);
        battery.store().insert(2_000, false);
        TvBlockEntity tv = new TvBlockEntity(helper.absolutePos(TV),
                ModBlocks.SIGNAL_SCREEN.get().defaultBlockState().setValue(TvBlock.FACING, Direction.NORTH));
        tv.setOwner(owner);
        tv.setLayout(TvLayout.of(List.of(value(0, network, TvMetric.HELD), value(12, network, TvMetric.HELD_CAPACITY),
                value(24, network, TvMetric.HELD_FILL))));
        ServerPlayer player = TestPlayers.menuOpener(helper);
        player.moveTo(Vec3.atCenterOf(helper.absolutePos(TV)).add(0, 0, 10));

        helper.startSequence()
                .thenWaitUntil(() -> registered(helper, router))
                .thenWaitUntil(() -> {
                    TvManager.sampleForTests(tv, new FlowSampler.Report(++at, List.of(),
                            FlowSampler.Report.EMPTY.withoutNetwork(), List.of()), List.of(player));
                    double[] values = tv.feed().values();
                    helper.assertValueEqual(values[0], 2_000.0, "guardado");
                    helper.assertValueEqual(values[1], (double) battery.capacity(), "capacidade");
                    helper.assertValueEqual(values[2], 2_000.0 / battery.capacity(), "enchimento");
                })
                .thenSucceed();
    }

    private static TvPiece value(int x, UUID network, TvMetric metric) {
        return new TvPiece(TvPieceType.VALUE, x, 0, 10, 10, 0, "",
                Optional.of(new TvBinding(network, ResourceType.ENERGY, metric)), TvFormat.DEFAULT, TvStyle.DEFAULT);
    }

    /** Sem Tela pedindo, o amostrador gira várias voltas com o Armazém na rede e não lê máquina nenhuma. */
    @GameTest(template = "empty", batch = IDLE, timeoutTicks = 300)
    public static void semPedidoNadaELido(GameTestHelper helper) {
        UUID network = network(helper, UUID.randomUUID());
        RouterBlockEntity router = place(helper, BATTERY, battery(), network, ResourceType.ENERGY, PortMode.BOTH);
        StorageBatteryBlockEntity battery = helper.getBlockEntity(BATTERY);
        battery.store().insert(1_000, false);
        long[] reads = {0};
        long[] samples = {0};

        helper.startSequence()
                .thenWaitUntil(() -> registered(helper, router))
                .thenExecute(() -> {
                    reads[0] = flows().heldReadsForTests();
                    samples[0] = flows().cost().samples();
                })
                .thenIdle(50)
                .thenExecute(() -> {
                    helper.assertTrue(flows().cost().samples() >= samples[0] + 2, "o amostrador não girou");
                    helper.assertValueEqual(flows().heldReadsForTests(), reads[0], "máquinas lidas sem pedido");
                    helper.assertTrue(flows().held(network, ResourceType.ENERGY) == null, "guardado sem pedido");
                })
                .thenSucceed();
    }
}
