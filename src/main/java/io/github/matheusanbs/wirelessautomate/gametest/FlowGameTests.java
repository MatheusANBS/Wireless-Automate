package io.github.matheusanbs.wirelessautomate.gametest;

import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.block.RouterBlock;
import io.github.matheusanbs.wirelessautomate.block.RouterBlockEntity;
import io.github.matheusanbs.wirelessautomate.block.RouterTier;
import io.github.matheusanbs.wirelessautomate.network.FlowSampler;
import io.github.matheusanbs.wirelessautomate.network.FlowStats;
import io.github.matheusanbs.wirelessautomate.network.NetworkManager;
import io.github.matheusanbs.wirelessautomate.network.NetworkSavedData;
import io.github.matheusanbs.wirelessautomate.network.NodeIndex;
import io.github.matheusanbs.wirelessautomate.network.PortMode;
import io.github.matheusanbs.wirelessautomate.network.RedstoneMode;
import io.github.matheusanbs.wirelessautomate.network.ResourceType;
import io.github.matheusanbs.wirelessautomate.registry.ModBlocks;
import io.github.matheusanbs.wirelessautomate.storage.StorageBatteryBlockEntity;
import io.github.matheusanbs.wirelessautomate.storage.StorageChestBlockEntity;
import io.github.matheusanbs.wirelessautomate.storage.StorageKind;
import io.github.matheusanbs.wirelessautomate.storage.StorageTankBlockEntity;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Os contadores de entrega e recebimento da porta ({@code Port.gave} e {@code Port.got}): a origem
 * soma o que entregou, o destino o que recebeu, e os dois crescem do mesmo tanto. Cada teste cria a
 * própria rede e lê os contadores pela porta do próprio roteador, nunca pelo total do servidor; a leitura
 * de teste soma o que o amostrador já leu, então não depende de quando a amostra passou.
 * Máquinas em y=1 com o roteador em cima (facing=UP), então a face configurada é {@link Direction#UP}.
 *
 * <p>E o amostrador dos Fluxos ({@link FlowSampler}), que é do servidor inteiro e roda sempre: os testes
 * dele ficam no lote {@link #SAMPLER}, cada um conferindo só a própria rede ou o próprio nó. O que confere
 * que ninguém olha fica num lote só dele ({@link #IDLE}), longe dos testes do Tablet, que olham.
 */
@GameTestHolder(WirelessAutomate.MODID)
@PrefixGameTestTemplate(false)
public final class FlowGameTests {
    private static final BlockPos A = new BlockPos(0, 1, 0);
    private static final BlockPos B = new BlockPos(2, 1, 2);
    private static final BlockPos C = new BlockPos(2, 1, 0);
    /** Lote dos testes do amostrador. */
    private static final String SAMPLER = "wirelessautomate_flows";
    /** Lote do teste sem ninguém olhando. */
    private static final String IDLE = "wirelessautomate_flows_idle";

    private static UUID newNetwork(GameTestHelper helper, String name) {
        return NetworkSavedData.get(helper.getLevel().getServer()).create(UUID.randomUUID(), name).id();
    }

    /** Põe a máquina em {@code machine} e um roteador do tier em cima dela, na rede, no modo do tipo. */
    private static RouterBlockEntity place(GameTestHelper helper, BlockPos machine, BlockState machineState,
            RouterTier tier, UUID network, ResourceType type, PortMode mode) {
        helper.setBlock(machine, machineState);
        return router(helper, machine, tier, network, type, mode);
    }

    /** O roteador do tier em cima da máquina que já está em {@code machine}, na rede, no modo do tipo. */
    private static RouterBlockEntity router(GameTestHelper helper, BlockPos machine, RouterTier tier, UUID network,
            ResourceType type, PortMode mode) {
        BlockPos routerPos = machine.above();
        helper.setBlock(routerPos, ModBlocks.ROUTER.get().defaultBlockState()
                .setValue(RouterBlock.FACING, Direction.UP).setValue(RouterBlock.TIER, tier));
        RouterBlockEntity router = helper.getBlockEntity(routerPos);
        router.setNetworkId(network);
        router.setNetworkId(type, network);
        router.setMode(type, Direction.UP, mode);
        return router;
    }

    private static RouterBlockEntity chest(GameTestHelper helper, BlockPos pos, UUID network, PortMode mode) {
        return place(helper, pos, Blocks.CHEST.defaultBlockState(), RouterTier.BASIC, network, ResourceType.ITEM, mode);
    }

    private static ChestBlockEntity chestAt(GameTestHelper helper, BlockPos pos) {
        return helper.getBlockEntity(pos);
    }

    private static int count(GameTestHelper helper, BlockPos pos, Item item) {
        ChestBlockEntity chest = chestAt(helper, pos);
        int total = 0;
        for (int i = 0; i < chest.getContainerSize(); i++) {
            ItemStack stack = chest.getItem(i);
            if (stack.is(item)) {
                total += stack.getCount();
            }
        }
        return total;
    }

    private static void waitRegistered(GameTestHelper helper, RouterBlockEntity... routers) {
        for (RouterBlockEntity router : routers) {
            helper.assertTrue(NetworkManager.get().contains(router), "roteador não registrado");
        }
    }

    private static long gave(RouterBlockEntity router, ResourceType type) {
        return NetworkManager.get().gaveForTests(router, null, type, Direction.UP);
    }

    private static long got(RouterBlockEntity router, ResourceType type) {
        return NetworkManager.get().gotForTests(router, null, type, Direction.UP);
    }

    private static void assertGave(GameTestHelper helper, RouterBlockEntity router, ResourceType type, long expected,
            String what) {
        helper.assertValueEqual(gave(router, type), expected, "entregue por " + what);
    }

    private static void assertGot(GameTestHelper helper, RouterBlockEntity router, ResourceType type, long expected,
            String what) {
        helper.assertValueEqual(got(router, type), expected, "recebido por " + what);
    }

    /** Extrai → Insere: a origem entregou 15 e o destino recebeu 15; cada lado só soma o seu. */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void itemsExtractToInsertCountBothSides(GameTestHelper helper) {
        UUID network = newNetwork(helper, "teste-fluxo-itens");
        RouterBlockEntity source = chest(helper, A, network, PortMode.EXTRACT);
        RouterBlockEntity target = chest(helper, B, network, PortMode.INSERT);
        chestAt(helper, A).setItem(0, new ItemStack(Items.DIAMOND, 10));
        chestAt(helper, A).setItem(5, new ItemStack(Items.COBBLESTONE, 5));

        helper.startSequence()
                .thenWaitUntil(() -> waitRegistered(helper, source, target))
                .thenWaitUntil(() -> {
                    helper.assertValueEqual(count(helper, B, Items.DIAMOND) + count(helper, B, Items.COBBLESTONE), 15,
                            "itens no destino");
                    assertGave(helper, source, ResourceType.ITEM, 15, "a origem");
                    assertGot(helper, target, ResourceType.ITEM, 15, "o destino");
                })
                .thenExecute(() -> {
                    assertGot(helper, source, ResourceType.ITEM, 0, "a origem");
                    assertGave(helper, target, ResourceType.ITEM, 0, "o destino");
                })
                .thenSucceed();
    }

    /** Extrai → Armazém: a origem entrega e o Armazém recebe, sem entregar nada. */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void itemsExtractToStorageCountBothSides(GameTestHelper helper) {
        UUID network = newNetwork(helper, "teste-fluxo-extrai-armazem");
        RouterBlockEntity source = chest(helper, A, network, PortMode.EXTRACT);
        RouterBlockEntity storage = chest(helper, B, network, PortMode.BOTH);
        chestAt(helper, A).setItem(0, new ItemStack(Items.DIAMOND, 12));

        helper.startSequence()
                .thenWaitUntil(() -> waitRegistered(helper, source, storage))
                .thenWaitUntil(() -> {
                    helper.assertValueEqual(count(helper, B, Items.DIAMOND), 12, "itens no Armazém");
                    assertGave(helper, source, ResourceType.ITEM, 12, "a origem");
                    assertGot(helper, storage, ResourceType.ITEM, 12, "o Armazém");
                })
                .thenExecute(() -> assertGave(helper, storage, ResourceType.ITEM, 0, "o Armazém"))
                .thenSucceed();
    }

    /** Armazém → Insere: o Armazém entrega e o destino recebe, sem receber nada. */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void itemsStorageToInsertCountBothSides(GameTestHelper helper) {
        UUID network = newNetwork(helper, "teste-fluxo-armazem-insere");
        RouterBlockEntity storage = chest(helper, A, network, PortMode.BOTH);
        RouterBlockEntity target = chest(helper, B, network, PortMode.INSERT);
        chestAt(helper, A).setItem(0, new ItemStack(Items.EMERALD, 9));

        helper.startSequence()
                .thenWaitUntil(() -> waitRegistered(helper, storage, target))
                .thenWaitUntil(() -> {
                    helper.assertValueEqual(count(helper, B, Items.EMERALD), 9, "itens no destino");
                    assertGave(helper, storage, ResourceType.ITEM, 9, "o Armazém");
                    assertGot(helper, target, ResourceType.ITEM, 9, "o destino");
                })
                .thenExecute(() -> assertGot(helper, storage, ResourceType.ITEM, 0, "o Armazém"))
                .thenSucceed();
    }

    /**
     * Baú do mod → Baú do mod (o caminho por tipo, sem varrer slots): muito além de uma pilha, e os dois
     * contadores batem com o que o destino guarda.
     */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void bulkChestsCountBothSides(GameTestHelper helper) {
        UUID network = newNetwork(helper, "teste-fluxo-bau-do-mod");
        helper.setBlock(A, ModBlocks.STORAGE_CHEST.get().defaultBlockState().setValue(RouterBlock.TIER, RouterTier.ULTIMATE));
        helper.setBlock(B, ModBlocks.STORAGE_CHEST.get().defaultBlockState().setValue(RouterBlock.TIER, RouterTier.ULTIMATE));
        StorageChestBlockEntity from = helper.getBlockEntity(A);
        StorageChestBlockEntity to = helper.getBlockEntity(B);
        from.storage().insert(new ItemStack(Items.COBBLESTONE), 5_000, false);
        RouterBlockEntity source = router(helper, A, RouterTier.ULTIMATE, network,
                ResourceType.ITEM, PortMode.EXTRACT);
        RouterBlockEntity target = router(helper, B, RouterTier.ULTIMATE, network,
                ResourceType.ITEM, PortMode.INSERT);

        helper.startSequence()
                .thenWaitUntil(() -> waitRegistered(helper, source, target))
                .thenWaitUntil(() -> {
                    helper.assertValueEqual(to.storage().count(new ItemStack(Items.COBBLESTONE)), 5_000L, "no Baú de destino");
                    assertGave(helper, source, ResourceType.ITEM, 5_000, "o Baú de origem");
                    assertGot(helper, target, ResourceType.ITEM, 5_000, "o Baú de destino");
                })
                .thenSucceed();
    }

    /** Fluidos por handler comum (caldeirões): 1000 mB saem de um e entram no outro. */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void fluidsCountBothSides(GameTestHelper helper) {
        UUID network = newNetwork(helper, "teste-fluxo-fluidos");
        RouterBlockEntity source = place(helper, A,
                Blocks.WATER_CAULDRON.defaultBlockState().setValue(LayeredCauldronBlock.LEVEL, 3), RouterTier.BASIC,
                network, ResourceType.FLUID, PortMode.EXTRACT);
        RouterBlockEntity target = place(helper, B, Blocks.CAULDRON.defaultBlockState(), RouterTier.BASIC, network,
                ResourceType.FLUID, PortMode.INSERT);

        helper.startSequence()
                .thenWaitUntil(() -> waitRegistered(helper, source, target))
                .thenWaitUntil(() -> {
                    helper.assertBlockPresent(Blocks.WATER_CAULDRON, B);
                    assertGave(helper, source, ResourceType.FLUID, 1_000, "a origem");
                    assertGot(helper, target, ResourceType.FLUID, 1_000, "o destino");
                })
                .thenSucceed();
    }

    /** Fluidos entre Tanques do mod (o caminho em {@code long}): bem acima do teto de um {@code int}. */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void fluidTanksCountBothSides(GameTestHelper helper) {
        UUID network = newNetwork(helper, "teste-fluxo-tanques");
        long amount = 30_000_000_000L;
        helper.setBlock(A, ModBlocks.STORAGE.get(StorageKind.TANK).get().defaultBlockState()
                .setValue(RouterBlock.TIER, RouterTier.ULTIMATE));
        helper.setBlock(B, ModBlocks.STORAGE.get(StorageKind.TANK).get().defaultBlockState()
                .setValue(RouterBlock.TIER, RouterTier.ULTIMATE));
        StorageTankBlockEntity from = helper.getBlockEntity(A);
        StorageTankBlockEntity to = helper.getBlockEntity(B);
        from.storage().insert(new FluidStack(Fluids.WATER, 1), amount, false);
        RouterBlockEntity source = router(helper, A, RouterTier.ULTIMATE, network,
                ResourceType.FLUID, PortMode.EXTRACT);
        RouterBlockEntity target = router(helper, B, RouterTier.ULTIMATE, network,
                ResourceType.FLUID, PortMode.INSERT);

        helper.startSequence()
                .thenWaitUntil(() -> waitRegistered(helper, source, target))
                .thenWaitUntil(() -> {
                    helper.assertValueEqual(to.storage().count(new FluidStack(Fluids.WATER, 1)), amount, "no Tanque de destino");
                    assertGave(helper, source, ResourceType.FLUID, amount, "o Tanque de origem");
                    assertGot(helper, target, ResourceType.FLUID, amount, "o Tanque de destino");
                })
                .thenSucceed();
    }

    /**
     * Energia dividida entre dois destinos num passe só: cada um soma o que recebeu, e a origem soma o
     * total, que é o que as duas Baterias de destino guardam.
     */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void energySplitCountsEachDestination(GameTestHelper helper) {
        UUID network = newNetwork(helper, "teste-fluxo-energia");
        long amount = 1_000_000L;
        BlockState battery = ModBlocks.STORAGE.get(StorageKind.BATTERY).get().defaultBlockState()
                .setValue(RouterBlock.TIER, RouterTier.ULTIMATE);
        helper.setBlock(A, battery);
        helper.setBlock(B, battery);
        helper.setBlock(C, battery);
        StorageBatteryBlockEntity from = helper.getBlockEntity(A);
        StorageBatteryBlockEntity first = helper.getBlockEntity(B);
        StorageBatteryBlockEntity second = helper.getBlockEntity(C);
        from.store().insert(amount, false);
        RouterBlockEntity source = router(helper, A, RouterTier.ULTIMATE, network, ResourceType.ENERGY,
                PortMode.EXTRACT);
        RouterBlockEntity one = router(helper, B, RouterTier.ULTIMATE, network, ResourceType.ENERGY,
                PortMode.INSERT);
        RouterBlockEntity two = router(helper, C, RouterTier.ULTIMATE, network, ResourceType.ENERGY,
                PortMode.INSERT);

        helper.startSequence()
                .thenWaitUntil(() -> waitRegistered(helper, source, one, two))
                .thenWaitUntil(() -> {
                    helper.assertValueEqual(first.store().stored() + second.store().stored(), amount, "nos destinos");
                    assertGave(helper, source, ResourceType.ENERGY, amount, "a origem");
                    assertGot(helper, one, ResourceType.ENERGY, first.store().stored(), "o primeiro destino");
                    assertGot(helper, two, ResourceType.ENERGY, second.store().stored(), "o segundo destino");
                })
                .thenExecute(() -> {
                    helper.assertTrue(got(one, ResourceType.ENERGY) > 0 && got(two, ResourceType.ENERGY) > 0,
                            "a divisão não chegou aos dois destinos");
                    helper.assertValueEqual(got(one, ResourceType.ENERGY) + got(two, ResourceType.ENERGY),
                            gave(source, ResourceType.ENERGY), "soma recebida × entregue");
                })
                .thenSucceed();
    }

    /**
     * Destino que aceita na simulação e recusa de verdade, com a origem numa máquina de entrada e saída na
     * mesma face: a sobra não volta como entregue pela origem nem conta no destino que mentiu; o outro
     * destino, que recebe o resgate, soma o que resgatou, e a origem soma o mesmo.
     */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void rescuedLeftoverCountsForTheRescuingDestination(GameTestHelper helper) {
        if (!TestMachines.enabled()) {
            helper.succeed();
            return;
        }
        UUID network = newNetwork(helper, "teste-fluxo-resgate");
        BlockPos machine = helper.absolutePos(A);
        TestMachines.reset(machine);
        TestMachines.Furnace furnace = TestMachines.furnace(machine);
        furnace.slots[1] = new ItemStack(Items.STONE, 10);
        RouterBlockEntity source = place(helper, A, TestMachines.FURNACE.defaultBlockState(), RouterTier.BASIC, network,
                ResourceType.ITEM, PortMode.BOTH);
        RouterBlockEntity liar = place(helper, C, TestMachines.LYING_SINK.defaultBlockState(), RouterTier.BASIC, network,
                ResourceType.ITEM, PortMode.INSERT);
        RouterBlockEntity target = chest(helper, B, network, PortMode.INSERT);

        helper.startSequence()
                .thenWaitUntil(() -> waitRegistered(helper, source, liar, target))
                .thenWaitUntil(() -> {
                    helper.assertValueEqual(count(helper, B, Items.STONE), 10, "pedra no destino");
                    assertGave(helper, source, ResourceType.ITEM, 10, "a origem");
                    assertGot(helper, target, ResourceType.ITEM, 10, "o destino que resgatou");
                })
                .thenExecute(() -> assertGot(helper, liar, ResourceType.ITEM, 0, "o destino que mentiu"))
                .thenSucceed();
    }

    // ------------------------------------------------------------------ amostrador dos Fluxos

    private static FlowSampler flows() {
        return NetworkManager.get().flows();
    }

    private static long tick(GameTestHelper helper) {
        return helper.getLevel().getServer().getTickCount();
    }

    /**
     * Fechou uma volta inteira depois do tick: a volta que fecha logo depois pode ter passado pelo balde do nó
     * antes dele.
     */
    private static void sampledAfter(GameTestHelper helper, long tick) {
        helper.assertTrue(flows().report().sampledAt() >= tick + FlowSampler.SAMPLE_TICKS,
                "nenhuma volta inteira depois do tick " + tick);
    }

    private static FlowStats stats(GameTestHelper helper, UUID network, ResourceType type) {
        FlowStats stats = flows().report().get(network, type);
        helper.assertTrue(stats != null, "a rede não está nos Fluxos");
        return stats;
    }

    /**
     * Sem ninguém olhando, o amostrador roda do mesmo jeito: amostras a cada segundo, a rede nos Fluxos com o
     * que moveu, e a leitura de teste dos contadores da porta com tudo o que somaram.
     */
    @GameTest(template = "empty", batch = IDLE, timeoutTicks = 300)
    public static void samplerRunsWithoutWatchers(GameTestHelper helper) {
        UUID network = newNetwork(helper, "teste-fluxos-sem-olhar");
        RouterBlockEntity source = chest(helper, A, network, PortMode.EXTRACT);
        RouterBlockEntity target = chest(helper, B, network, PortMode.INSERT);
        chestAt(helper, A).setItem(0, new ItemStack(Items.DIAMOND, 16));
        long[] samples = {0};

        helper.startSequence()
                .thenWaitUntil(() -> waitRegistered(helper, source, target))
                .thenWaitUntil(() -> helper.assertValueEqual(count(helper, B, Items.DIAMOND), 16, "itens no destino"))
                .thenExecute(() -> samples[0] = flows().cost().samples())
                .thenIdle(45)
                .thenExecute(() -> {
                    helper.assertTrue(flows().cost().samples() >= samples[0] + 2,
                            "amostras sem ninguém olhando: " + (flows().cost().samples() - samples[0]));
                    helper.assertValueEqual(flows().cost().watchers(), 0, "jogadores olhando");
                    FlowStats stats = stats(helper, network, ResourceType.ITEM);
                    helper.assertValueEqual(stats.produced(), 16L, "entra");
                    helper.assertValueEqual(stats.consumed(), 16L, "sai");
                    assertGave(helper, source, ResourceType.ITEM, 16, "a origem");
                    assertGot(helper, target, ResourceType.ITEM, 16, "o destino");
                })
                .thenSucceed();
    }

    /**
     * O que a Extrai entregou entra e o que a Insere recebeu sai, e a amostra não muda o total da porta. A
     * origem só passa a Extrai depois de uma amostra, para a conta caber na janela.
     */
    @GameTest(template = "empty", batch = SAMPLER, timeoutTicks = 300)
    public static void samplerTotalsExtractToInsert(GameTestHelper helper) {
        UUID network = newNetwork(helper, "teste-fluxos-entra-sai");
        RouterBlockEntity source = chest(helper, A, network, PortMode.NONE);
        RouterBlockEntity target = chest(helper, B, network, PortMode.INSERT);
        chestAt(helper, A).setItem(0, new ItemStack(Items.DIAMOND, 16));
        long start = tick(helper);

        helper.startSequence()
                .thenWaitUntil(() -> waitRegistered(helper, source, target))
                .thenWaitUntil(() -> sampledAfter(helper, start))
                .thenExecute(() -> source.setMode(ResourceType.ITEM, Direction.UP, PortMode.EXTRACT))
                .thenWaitUntil(() -> {
                    FlowStats stats = stats(helper, network, ResourceType.ITEM);
                    helper.assertValueEqual(stats.produced(), 16L, "entra");
                    helper.assertValueEqual(stats.consumed(), 16L, "sai");
                })
                .thenExecute(() -> {
                    FlowStats stats = stats(helper, network, ResourceType.ITEM);
                    helper.assertValueEqual(stats.stored(), 0L, "o Armazém recebeu");
                    helper.assertValueEqual(stats.released(), 0L, "o Armazém deu");
                    helper.assertTrue(stats.seconds() > 0 && stats.producedPerSecond() > 0, "sem média por segundo");
                    helper.assertValueEqual(count(helper, B, Items.DIAMOND), 16, "itens no destino");
                    assertGave(helper, source, ResourceType.ITEM, 16, "a origem depois da amostra");
                    assertGot(helper, target, ResourceType.ITEM, 16, "o destino depois da amostra");
                })
                .thenSucceed();
    }

    /** Extrai → Armazém: entra o que a origem deu, o Armazém recebe o mesmo e não dá nada; o saldo enche. */
    @GameTest(template = "empty", batch = SAMPLER, timeoutTicks = 300)
    public static void samplerStorageFills(GameTestHelper helper) {
        UUID network = newNetwork(helper, "teste-fluxos-armazem");
        RouterBlockEntity source = chest(helper, A, network, PortMode.NONE);
        RouterBlockEntity storage = chest(helper, B, network, PortMode.BOTH);
        chestAt(helper, A).setItem(0, new ItemStack(Items.EMERALD, 16));
        long start = tick(helper);

        helper.startSequence()
                .thenWaitUntil(() -> waitRegistered(helper, source, storage))
                .thenWaitUntil(() -> sampledAfter(helper, start))
                .thenExecute(() -> source.setMode(ResourceType.ITEM, Direction.UP, PortMode.EXTRACT))
                .thenWaitUntil(() -> {
                    FlowStats stats = stats(helper, network, ResourceType.ITEM);
                    helper.assertValueEqual(stats.produced(), 16L, "entra");
                    helper.assertValueEqual(stats.stored(), 16L, "o Armazém recebeu");
                })
                .thenExecute(() -> {
                    FlowStats stats = stats(helper, network, ResourceType.ITEM);
                    helper.assertValueEqual(stats.released(), 0L, "o Armazém deu");
                    helper.assertValueEqual(stats.consumed(), 0L, "sai");
                    helper.assertTrue(stats.balancePerSecond() > 0, "o saldo do Armazém não está enchendo");
                })
                .thenSucceed();
    }

    /** Origem sem nada para dar: dorme vazia. */
    @GameTest(template = "empty", batch = SAMPLER, timeoutTicks = 300)
    public static void samplerCountsEmptySource(GameTestHelper helper) {
        UUID network = newNetwork(helper, "teste-fluxos-vazia");
        RouterBlockEntity source = chest(helper, A, network, PortMode.EXTRACT);
        RouterBlockEntity target = chest(helper, B, network, PortMode.INSERT);

        helper.startSequence()
                .thenWaitUntil(() -> waitRegistered(helper, source, target))
                .thenWaitUntil(() -> {
                    FlowStats.States states = stats(helper, network, ResourceType.ITEM).states();
                    helper.assertValueEqual(states.sourcesEmpty(), 1, "origens vazias");
                    helper.assertValueEqual(states.sourcesWaiting(), 0, "origens esperando destino");
                })
                .thenSucceed();
    }

    /** Destino lotado: a origem com o que dar espera destino. */
    @GameTest(template = "empty", batch = SAMPLER, timeoutTicks = 300)
    public static void samplerCountsWaitingSource(GameTestHelper helper) {
        UUID network = newNetwork(helper, "teste-fluxos-espera");
        RouterBlockEntity[] routers = fullDestination(helper, network, PortMode.EXTRACT);

        helper.startSequence()
                .thenWaitUntil(() -> waitRegistered(helper, routers))
                .thenWaitUntil(() -> {
                    FlowStats.States states = stats(helper, network, ResourceType.ITEM).states();
                    helper.assertValueEqual(states.sourcesWaiting(), 1, "origens esperando destino");
                    helper.assertValueEqual(states.sourcesEmpty(), 0, "origens vazias");
                })
                .thenSucceed();
    }

    /**
     * Armazém com o que dar e o destino lotado: não conta como esperando destino (só as Extrai esperam),
     * nem como vazio. O destino cheio aparece, para provar que a amostra já viu as duas portas.
     */
    @GameTest(template = "empty", batch = SAMPLER, timeoutTicks = 300)
    public static void samplerStorageNeverWaits(GameTestHelper helper) {
        UUID network = newNetwork(helper, "teste-fluxos-armazem-espera");
        RouterBlockEntity[] routers = fullDestination(helper, network, PortMode.BOTH);

        helper.startSequence()
                .thenWaitUntil(() -> waitRegistered(helper, routers))
                .thenWaitUntil(() -> helper.assertValueEqual(
                        stats(helper, network, ResourceType.ITEM).states().destinationsFull(), 1, "destinos cheios"))
                .thenExecute(() -> {
                    FlowStats.States states = stats(helper, network, ResourceType.ITEM).states();
                    helper.assertValueEqual(states.sourcesWaiting(), 0, "origens esperando destino");
                    helper.assertValueEqual(states.sourcesEmpty(), 0, "origens vazias");
                })
                .thenSucceed();
    }

    /** Destino lotado que recusou várias vezes: cheio. */
    @GameTest(template = "empty", batch = SAMPLER, timeoutTicks = 300)
    public static void samplerCountsFullDestination(GameTestHelper helper) {
        UUID network = newNetwork(helper, "teste-fluxos-cheio");
        RouterBlockEntity[] routers = fullDestination(helper, network, PortMode.EXTRACT);

        helper.startSequence()
                .thenWaitUntil(() -> waitRegistered(helper, routers))
                .thenWaitUntil(() -> helper.assertValueEqual(
                        stats(helper, network, ResourceType.ITEM).states().destinationsFull(), 1, "destinos cheios"))
                .thenSucceed();
    }

    /** Origem com diamantes ({@code sourceMode}) e destino lotado de pedra, na rede. */
    private static RouterBlockEntity[] fullDestination(GameTestHelper helper, UUID network, PortMode sourceMode) {
        RouterBlockEntity source = chest(helper, A, network, sourceMode);
        RouterBlockEntity target = chest(helper, B, network, PortMode.INSERT);
        chestAt(helper, A).setItem(0, new ItemStack(Items.DIAMOND, 8));
        ChestBlockEntity full = chestAt(helper, B);
        for (int i = 0; i < full.getContainerSize(); i++) {
            full.setItem(i, new ItemStack(Items.STONE, 64));
        }
        return new RouterBlockEntity[] {source, target};
    }

    /** Face com modo que o redstone desliga (só com sinal, e sem sinal): parada por redstone, na rede dela. */
    @GameTest(template = "empty", batch = SAMPLER, timeoutTicks = 300)
    public static void samplerCountsRedstoneStopped(GameTestHelper helper) {
        UUID network = newNetwork(helper, "teste-fluxos-redstone");
        RouterBlockEntity source = chest(helper, A, network, PortMode.EXTRACT);
        source.setRedstone(ResourceType.ITEM, Direction.UP, RedstoneMode.HIGH);

        helper.startSequence()
                .thenWaitUntil(() -> waitRegistered(helper, source))
                .thenWaitUntil(() -> helper.assertValueEqual(
                        stats(helper, network, ResourceType.ITEM).states().redstoneStopped(), 1, "paradas por redstone"))
                .thenSucceed();
    }

    /**
     * Faces com modo e sem rede contam por tipo, no servidor inteiro: as três do teste aparecem, e saem
     * quando voltam a Nenhum. A contagem é global, então o teste compara antes e depois.
     */
    @GameTest(template = "empty", batch = SAMPLER, timeoutTicks = 300)
    public static void samplerCountsFacesWithoutNetwork(GameTestHelper helper) {
        helper.setBlock(A, Blocks.CHEST.defaultBlockState());
        helper.setBlock(A.above(), ModBlocks.ROUTER.get().defaultBlockState()
                .setValue(RouterBlock.FACING, Direction.UP).setValue(RouterBlock.TIER, RouterTier.BASIC));
        RouterBlockEntity router = helper.getBlockEntity(A.above());
        Direction[] faces = {Direction.UP, Direction.NORTH, Direction.EAST};
        for (Direction face : faces) {
            router.setMode(ResourceType.ITEM, face, PortMode.EXTRACT);
        }
        long[] mark = {0, 0};

        helper.startSequence()
                .thenWaitUntil(() -> waitRegistered(helper, router))
                .thenExecute(() -> mark[1] = tick(helper))
                .thenWaitUntil(() -> sampledAfter(helper, mark[1]))
                .thenExecute(() -> {
                    int count = flows().report().withoutNetwork(ResourceType.ITEM);
                    helper.assertTrue(count >= faces.length, "faces sem rede: " + count);
                    helper.assertValueEqual(withoutNetwork(router), faces.length, "faces sem rede do nó");
                })
                .thenExecute(() -> {
                    for (Direction face : faces) {
                        router.setMode(ResourceType.ITEM, face, PortMode.NONE);
                    }
                    mark[1] = tick(helper);
                })
                .thenWaitUntil(() -> sampledAfter(helper, mark[1]))
                .thenExecute(() -> helper.assertValueEqual(withoutNetwork(router), 0, "faces sem rede do nó depois"))
                .thenSucceed();
    }

    /**
     * As faces de itens sem rede de um nó na última amostra. A contagem global não serve para comparar antes
     * e depois: outro teste do lote pode pôr faces sem rede no meio.
     */
    private static int withoutNetwork(RouterBlockEntity router) {
        NodeIndex.NodeKey key = NodeIndex.NodeKey.of(router.getLevel(), router.getBlockPos());
        for (FlowSampler.NodeWithoutNetwork node : flows().report().nodesWithout()) {
            if (node.node().equals(key)) {
                return node.counts().get(ResourceType.ITEM.ordinal());
            }
        }
        return 0;
    }

    private FlowGameTests() {
    }
}
