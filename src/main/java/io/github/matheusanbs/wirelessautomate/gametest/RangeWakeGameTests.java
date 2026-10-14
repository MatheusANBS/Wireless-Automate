package io.github.matheusanbs.wirelessautomate.gametest;

import static io.github.matheusanbs.wirelessautomate.gametest.RangeTestSupport.*;

import io.github.matheusanbs.wirelessautomate.Config;
import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.block.RouterBlock;
import io.github.matheusanbs.wirelessautomate.block.RouterBlockEntity;
import io.github.matheusanbs.wirelessautomate.block.RouterTier;
import io.github.matheusanbs.wirelessautomate.network.NetworkManager;
import io.github.matheusanbs.wirelessautomate.network.PortMode;
import io.github.matheusanbs.wirelessautomate.network.ResourceType;
import io.github.matheusanbs.wirelessautomate.range.RemoteIndex;
import io.github.matheusanbs.wirelessautomate.range.RemoteMachines;
import io.github.matheusanbs.wirelessautomate.registry.ModBlocks;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.items.IItemHandler;

/**
 * Roteador de alcance, o acordar: o {@code setChanged} de uma remota pelo mixin, a carga de chunk, a frente relida
 * e o índice das remotas ({@code RemoteIndex}).
 */
@GameTestHolder(WirelessAutomate.MODID)
@PrefixGameTestTemplate(false)
public final class RangeWakeGameTests {
    /** Um sono bem maior que os 20 ticks em que o aviso tem de chegar. */
    private static final int LONG_SLEEP = 300;

    /** O {@code IItemHandler} da máquina em {@code relative} pela face de cima; mexer nele chama {@code setChanged}. */
    private static IItemHandler handler(GameTestHelper helper, BlockPos relative) {
        IItemHandler handler = helper.getLevel().getCapability(Capabilities.ItemHandler.BLOCK,
                helper.absolutePos(relative), Direction.UP);
        helper.assertTrue(handler != null, "sem IItemHandler em " + relative);
        return handler;
    }

    private static boolean sleep(GameTestHelper helper, RouterBlockEntity router, BlockPos relativeOrNull) {
        return NetworkManager.get().sleepForTests(router, relativeOrNull == null ? null : helper.absolutePos(relativeOrNull),
                ResourceType.ITEM, Direction.UP, helper.getLevel().getServer().getTickCount(), LONG_SLEEP);
    }

    /**
     * Uma origem que espera destino: barril em {@code relative}, com terra e um roteador Básico em
     * cima, na rede, extraindo. O destino dela fica cheio de terra ({@link #fillButOne}), então ela
     * oferece, é recusada por espaço e dorme esperando destino.
     */
    private static RouterBlockEntity waitingFeeder(GameTestHelper helper, BlockPos relative, UUID network) {
        helper.setBlock(relative, Blocks.BARREL.defaultBlockState().setValue(BarrelBlock.FACING, Direction.UP));
        helper.setBlock(relative.above(), ModBlocks.ROUTER.get().defaultBlockState().setValue(RouterBlock.FACING, Direction.UP));
        RouterBlockEntity router = helper.getBlockEntity(relative.above());
        router.setNetworkId(network);
        router.setMode(ResourceType.ITEM, Direction.UP, PortMode.EXTRACT);
        container(helper, relative).setItem(0, new ItemStack(Items.DIRT, 8));
        return router;
    }

    /** Enche o baú de terra, menos o slot 0, que fica com 1 pedregulho: só aceita mais pedregulho. */
    private static void fillButOne(GameTestHelper helper, BlockPos relative) {
        Container container = container(helper, relative);
        container.setItem(0, new ItemStack(Items.COBBLESTONE, 1));
        for (int i = 1; i < container.getContainerSize(); i++) {
            container.setItem(i, new ItemStack(Items.DIRT, 64));
        }
    }

    /**
     * Origem remota vazia e dormindo um sono longo: pôr um item no baú remoto pelo {@code IItemHandler}
     * (que chama {@code setChanged}) acorda a porta pelo mixin, e o item chega à presa em menos de 20
     * ticks.
     */
    @GameTest(template = "empty", timeoutTicks = 300)
    public static void remoteSourceWakesOnSetChanged(GameTestHelper helper) {
        RouterBlockEntity router = rangeRouter(helper);
        helper.setBlock(REMOTE_CHEST, Blocks.CHEST);
        long[] insertedAt = {0};
        helper.startSequence()
                .thenWaitUntil(() -> waitLoaded(helper, router))
                .thenExecute(() -> {
                    router.setRangeUpgrade(rangeUpgrade());
                    router.setMode(ResourceType.ITEM, Direction.UP, PortMode.INSERT);
                    linkInNetwork(helper, router, REMOTE_CHEST);
                    profile(helper, router, REMOTE_CHEST, Direction.UP, PortMode.EXTRACT);
                })
                .thenWaitUntil(() -> helper.assertTrue(remotePort(helper, router, REMOTE_CHEST, Direction.UP), "porta da remota"))
                .thenIdle(5)
                .thenExecute(() -> {
                    helper.assertTrue(RemoteIndex.get().isIndexed(router, helper.absolutePos(REMOTE_CHEST)), "remota fora do índice");
                    helper.assertTrue(sleep(helper, router, REMOTE_CHEST), "a porta da remota sumiu");
                    handler(helper, REMOTE_CHEST).insertItem(0, new ItemStack(Items.COBBLESTONE, 5), false);
                    insertedAt[0] = helper.getTick();
                })
                .thenWaitUntil(() -> helper.assertTrue(count(helper, CHEST) == 5, "o item não seguiu para a presa"))
                .thenExecute(() -> helper.assertTrue(helper.getTick() - insertedAt[0] < 20,
                        "a remota demorou " + (helper.getTick() - insertedAt[0]) + " ticks para acordar"))
                .thenSucceed();
    }

    /**
     * Destino remoto cheio, dormindo, e a origem presa esperando por ele: tirar uma pilha do baú remoto
     * pelo {@code IItemHandler} acorda o destino e a origem que espera, e a entrega sai em menos de 20
     * ticks.
     */
    @GameTest(template = "empty", timeoutTicks = 400)
    public static void fullRemoteDestinationWakesWhenEmptied(GameTestHelper helper) {
        RouterBlockEntity router = rangeRouter(helper);
        helper.setBlock(REMOTE_CHEST, Blocks.CHEST);
        Container full = container(helper, REMOTE_CHEST);
        for (int i = 0; i < full.getContainerSize(); i++) {
            full.setItem(i, new ItemStack(Items.DIRT, 64));
        }
        container(helper, CHEST).setItem(0, new ItemStack(Items.COBBLESTONE, 10));
        long[] freedAt = {0};
        helper.startSequence()
                .thenWaitUntil(() -> waitLoaded(helper, router))
                .thenExecute(() -> {
                    router.setRangeUpgrade(rangeUpgrade());
                    router.setMode(ResourceType.ITEM, Direction.UP, PortMode.EXTRACT);
                    linkInNetwork(helper, router, REMOTE_CHEST);
                    profile(helper, router, REMOTE_CHEST, Direction.UP, PortMode.INSERT);
                })
                .thenWaitUntil(() -> helper.assertTrue(remotePort(helper, router, REMOTE_CHEST, Direction.UP), "porta da remota"))
                // A origem tenta, o destino cheio recusa e ela passa a dormir esperando destino.
                .thenIdle(40)
                .thenExecute(() -> {
                    helper.assertTrue(countOf(helper, CHEST, Items.COBBLESTONE) == 10, "a presa entregou num destino cheio");
                    helper.assertTrue(sleep(helper, router, null), "a porta da presa sumiu");
                    helper.assertTrue(sleep(helper, router, REMOTE_CHEST), "a porta da remota sumiu");
                    helper.assertTrue(handler(helper, REMOTE_CHEST).extractItem(3, 64, false).getCount() == 64, "não tirou a pilha");
                    freedAt[0] = helper.getTick();
                })
                .thenWaitUntil(() -> helper.assertTrue(countOf(helper, REMOTE_CHEST, Items.COBBLESTONE) == 10,
                        "a entrega no destino remoto não saiu"))
                .thenExecute(() -> helper.assertTrue(helper.getTick() - freedAt[0] < 20,
                        "o destino remoto demorou " + (helper.getTick() - freedAt[0]) + " ticks para acordar"))
                .thenSucceed();
    }

    /**
     * A origem remota de um roteador entregando na presa do mesmo roteador é uma entrega
     * (compara a máquina, não o nó). O barril, que espera a presa (cheia de terra) abrir espaço, está num
     * sono longo; a entrega de pedregulho da remota na presa não pode acordá-lo (o tick em que ele
     * acorda não muda).
     */
    @GameTest(template = "empty", timeoutTicks = 500)
    public static void remoteDeliveryIntoAttachedDoesNotWakeFeeders(GameTestHelper helper) {
        RouterBlockEntity router = rangeRouter(helper);
        RouterBlockEntity feeder = waitingFeeder(helper, new BlockPos(0, 1, 0), router.networkId(ResourceType.ITEM));
        fillButOne(helper, CHEST);
        helper.setBlock(REMOTE_CHEST, Blocks.CHEST);
        long[] feederWake = {0};
        helper.startSequence()
                .thenWaitUntil(() -> {
                    waitLoaded(helper, router);
                    waitLoaded(helper, feeder);
                })
                .thenExecute(() -> {
                    router.setRangeUpgrade(rangeUpgrade());
                    router.setMode(ResourceType.ITEM, Direction.UP, PortMode.INSERT);
                    linkInNetwork(helper, router, REMOTE_CHEST);
                    profile(helper, router, REMOTE_CHEST, Direction.UP, PortMode.EXTRACT);
                })
                .thenWaitUntil(() -> helper.assertTrue(remotePort(helper, router, REMOTE_CHEST, Direction.UP), "porta da remota"))
                .thenWaitUntil(() -> helper.assertTrue(NetworkManager.get().waitsDestination(feeder, null, ResourceType.ITEM,
                        Direction.UP), "o barril não espera destino"))
                .thenExecute(() -> {
                    helper.assertTrue(sleep(helper, feeder, null), "a porta do barril sumiu");
                    feederWake[0] = NetworkManager.get().sourceWakeAt(feeder, null, ResourceType.ITEM, Direction.UP);
                    handler(helper, REMOTE_CHEST).insertItem(0, new ItemStack(Items.COBBLESTONE, 5), false);
                })
                .thenWaitUntil(() -> helper.assertTrue(countOf(helper, CHEST, Items.COBBLESTONE) == 6, "a remota não entregou na presa"))
                .thenExecute(() -> helper.assertValueEqual(NetworkManager.get().sourceWakeAt(feeder, null, ResourceType.ITEM,
                        Direction.UP), feederWake[0], "a entrega da remota na presa acordou o barril; o tick do despertar"))
                .thenSucceed();
    }

    /**
     * A mesma regra numa máquina remota ({@code wakeMachine}, pelo mixin): a presa entregando
     * pedregulho na remota não acorda o barril que espera a remota (cheia de terra) abrir espaço.
     */
    @GameTest(template = "empty", timeoutTicks = 500)
    public static void deliveryIntoRemoteDoesNotWakeFeeders(GameTestHelper helper) {
        RouterBlockEntity router = rangeRouter(helper);
        RouterBlockEntity feeder = waitingFeeder(helper, new BlockPos(0, 1, 0), router.networkId(ResourceType.ITEM));
        helper.setBlock(REMOTE_CHEST, Blocks.CHEST);
        fillButOne(helper, REMOTE_CHEST);
        long[] feederWake = {0};
        helper.startSequence()
                .thenWaitUntil(() -> {
                    waitLoaded(helper, router);
                    waitLoaded(helper, feeder);
                })
                .thenExecute(() -> {
                    router.setRangeUpgrade(rangeUpgrade());
                    router.setMode(ResourceType.ITEM, Direction.UP, PortMode.EXTRACT);
                    linkInNetwork(helper, router, REMOTE_CHEST);
                    profile(helper, router, REMOTE_CHEST, Direction.UP, PortMode.INSERT);
                })
                .thenWaitUntil(() -> helper.assertTrue(remotePort(helper, router, REMOTE_CHEST, Direction.UP), "porta da remota"))
                .thenWaitUntil(() -> helper.assertTrue(NetworkManager.get().waitsDestination(feeder, null, ResourceType.ITEM,
                        Direction.UP), "o barril não espera destino"))
                .thenExecute(() -> {
                    helper.assertTrue(sleep(helper, feeder, null), "a porta do barril sumiu");
                    feederWake[0] = NetworkManager.get().sourceWakeAt(feeder, null, ResourceType.ITEM, Direction.UP);
                    handler(helper, CHEST).insertItem(0, new ItemStack(Items.COBBLESTONE, 5), false);
                })
                .thenWaitUntil(() -> helper.assertTrue(countOf(helper, REMOTE_CHEST, Items.COBBLESTONE) == 6,
                        "a presa não entregou na remota"))
                .thenExecute(() -> helper.assertValueEqual(NetworkManager.get().sourceWakeAt(feeder, null, ResourceType.ITEM,
                        Direction.UP), feederWake[0], "a entrega da presa na remota acordou o barril; o tick do despertar"))
                .thenSucceed();
    }

    /**
     * A carga do chunk (o método que o {@code ChunkEvent.Load} chama) acorda a porta
     * dormindo de uma remota do chunk. Aqui a porta já tinha cache de capability; o caso sem cache usa
     * o mesmo caminho (acordar a porta, e a visita seguinte cria o cache), mas não é isolado no teste.
     */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void chunkLoadWakesRemote(GameTestHelper helper) {
        RouterBlockEntity router = rangeRouter(helper);
        helper.setBlock(REMOTE_CHEST, Blocks.CHEST);
        helper.startSequence()
                .thenWaitUntil(() -> waitLoaded(helper, router))
                .thenExecute(() -> {
                    router.setRangeUpgrade(rangeUpgrade());
                    router.setMode(ResourceType.ITEM, Direction.UP, PortMode.INSERT);
                    linkInNetwork(helper, router, REMOTE_CHEST);
                    profile(helper, router, REMOTE_CHEST, Direction.UP, PortMode.EXTRACT);
                })
                .thenWaitUntil(() -> helper.assertTrue(remotePort(helper, router, REMOTE_CHEST, Direction.UP), "porta da remota"))
                .thenExecute(() -> {
                    BlockPos remote = helper.absolutePos(REMOTE_CHEST);
                    long now = helper.getLevel().getServer().getTickCount();
                    helper.assertTrue(sleep(helper, router, REMOTE_CHEST), "a porta da remota sumiu");
                    helper.assertFalse(NetworkManager.get().sourceAwake(router, remote, ResourceType.ITEM, Direction.UP, now),
                            "a porta não dormiu");
                    RemoteIndex.get().chunkLoaded(helper.getLevel(), helper.getLevel().getChunk(remote));
                    helper.assertTrue(NetworkManager.get().sourceAwake(router, remote, ResourceType.ITEM, Direction.UP, now),
                            "a carga do chunk não acordou a porta");
                })
                .thenSucceed();
    }

    /**
     * Pela carga do chunk: a máquina de teste sem block entity ({@link TestMachines#BACK_SLOT})
     * girada no mundo não avisa nada; a carga do chunk relê a frente e "Trás" passa para o lado novo.
     */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void chunkLoadRereadsFront(GameTestHelper helper) {
        RouterBlockEntity router = rangeRouter(helper);
        BlockPos machine = new BlockPos(0, 1, 0);
        helper.setBlock(machine, TestMachines.BACK_SLOT.defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, Direction.EAST));
        TestMachines.reset(helper.absolutePos(machine));
        helper.startSequence()
                .thenWaitUntil(() -> waitLoaded(helper, router))
                .thenExecute(() -> {
                    router.setRangeUpgrade(rangeUpgrade());
                    router.setMode(ResourceType.ITEM, Direction.UP, PortMode.INSERT);
                    linkInNetwork(helper, router, machine);
                    profile(helper, router, machine, Direction.SOUTH, PortMode.EXTRACT);
                })
                .thenWaitUntil(() -> helper.assertTrue(remotePort(helper, router, machine, Direction.WEST), "Trás da frente Leste"))
                .thenExecute(() -> {
                    helper.setBlock(machine, TestMachines.BACK_SLOT.defaultBlockState()
                            .setValue(HorizontalDirectionalBlock.FACING, Direction.SOUTH));
                    BlockPos absolute = helper.absolutePos(machine);
                    RemoteIndex.get().chunkLoaded(helper.getLevel(), helper.getLevel().getChunk(absolute));
                    RemoteMachines remotes = router.remoteMachines();
                    helper.assertValueEqual(remotes.machine(remotes.index(absolute)).front(), Direction.SOUTH.ordinal(), "frente relida");
                })
                .thenWaitUntil(() -> helper.assertTrue(remotePort(helper, router, machine, Direction.NORTH), "Trás da frente Sul"))
                .thenExecute(() -> {
                    helper.assertFalse(remotePort(helper, router, machine, Direction.WEST), "porta no lado velho");
                    TestMachines.reset(helper.absolutePos(machine));
                })
                .thenSucceed();
    }

    /**
     * A máquina de teste sem block entity ({@link TestMachines#BACK_SLOT}) girada por
     * {@code setBlock} não chama {@code setChanged} nem invalida a capability, então a frente fica a velha.
     * Uma remontagem vinda de outra coisa (o modo de outra face do roteador) relê a frente e "Trás"
     * passa para o lado novo.
     */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void rebuildRereadsFront(GameTestHelper helper) {
        RouterBlockEntity router = rangeRouter(helper);
        BlockPos machine = new BlockPos(0, 1, 0);
        helper.setBlock(machine, TestMachines.BACK_SLOT.defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, Direction.EAST));
        TestMachines.reset(helper.absolutePos(machine));
        helper.startSequence()
                .thenWaitUntil(() -> waitLoaded(helper, router))
                .thenExecute(() -> {
                    router.setRangeUpgrade(rangeUpgrade());
                    router.setMode(ResourceType.ITEM, Direction.UP, PortMode.INSERT);
                    linkInNetwork(helper, router, machine);
                    profile(helper, router, machine, Direction.SOUTH, PortMode.EXTRACT);
                })
                .thenWaitUntil(() -> helper.assertTrue(remotePort(helper, router, machine, Direction.WEST), "Trás da frente Leste"))
                .thenExecute(() -> {
                    BlockPos absolute = helper.absolutePos(machine);
                    helper.setBlock(machine, TestMachines.BACK_SLOT.defaultBlockState()
                            .setValue(HorizontalDirectionalBlock.FACING, Direction.SOUTH));
                    RemoteMachines remotes = router.remoteMachines();
                    helper.assertValueEqual(remotes.machine(remotes.index(absolute)).front(), Direction.EAST.ordinal(),
                            "a frente mudou sem aviso nem remontagem");
                    router.setMode(ResourceType.ITEM, Direction.NORTH, PortMode.INSERT);
                })
                .thenWaitUntil(() -> helper.assertTrue(remotePort(helper, router, machine, Direction.NORTH), "Trás da frente Sul"))
                .thenExecute(() -> {
                    BlockPos absolute = helper.absolutePos(machine);
                    RemoteMachines remotes = router.remoteMachines();
                    helper.assertValueEqual(remotes.machine(remotes.index(absolute)).front(), Direction.SOUTH.ordinal(), "frente relida");
                    helper.assertFalse(remotePort(helper, router, machine, Direction.WEST), "porta no lado velho");
                    TestMachines.reset(helper.absolutePos(machine));
                })
                .thenSucceed();
    }

    /**
     * Pelo mixin: uma fornalha remota girada (o block entity fica; a capability não é
     * invalidada) relê a frente no {@code setChanged} seguinte.
     */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void setChangedRereadsFront(GameTestHelper helper) {
        RouterBlockEntity router = rangeRouter(helper);
        BlockPos furnace = new BlockPos(0, 1, 0);
        helper.setBlock(furnace, Blocks.FURNACE.defaultBlockState().setValue(AbstractFurnaceBlock.FACING, Direction.EAST));
        helper.startSequence()
                .thenWaitUntil(() -> waitLoaded(helper, router))
                .thenExecute(() -> {
                    router.setRangeUpgrade(rangeUpgrade());
                    router.setMode(ResourceType.ITEM, Direction.UP, PortMode.EXTRACT);
                    linkInNetwork(helper, router, furnace);
                    profile(helper, router, furnace, Direction.UP, PortMode.INSERT);
                })
                .thenWaitUntil(() -> helper.assertTrue(remotePort(helper, router, furnace, Direction.UP), "porta da fornalha"))
                .thenExecute(() -> {
                    BlockPos absolute = helper.absolutePos(furnace);
                    RemoteMachines remotes = router.remoteMachines();
                    helper.setBlock(furnace, Blocks.FURNACE.defaultBlockState().setValue(AbstractFurnaceBlock.FACING, Direction.SOUTH));
                    helper.assertValueEqual(remotes.machine(remotes.index(absolute)).front(), Direction.EAST.ordinal(),
                            "frente antes do setChanged");
                    handler(helper, furnace).insertItem(0, new ItemStack(Items.IRON_ORE, 1), false);
                    helper.assertValueEqual(remotes.machine(remotes.index(absolute)).front(), Direction.SOUTH.ordinal(),
                            "frente depois do setChanged");
                })
                .thenSucceed();
    }

    /** Sem o upgrade, a posição da máquina vinculada não fica no índice do mixin; com ele, fica; tirando, sai. */
    @GameTest(template = "empty")
    public static void indexFollowsUpgrade(GameTestHelper helper) {
        RouterBlockEntity router = rangeRouter(helper);
        helper.setBlock(REMOTE_CHEST, Blocks.CHEST);
        helper.startSequence()
                .thenWaitUntil(() -> waitLoaded(helper, router))
                .thenExecute(() -> {
                    BlockPos remote = helper.absolutePos(REMOTE_CHEST);
                    link(helper, router, REMOTE_CHEST);
                    helper.assertFalse(RemoteIndex.get().isIndexed(router, remote), "no índice sem o upgrade");
                    router.setRangeUpgrade(rangeUpgrade());
                    helper.assertTrue(RemoteIndex.get().isIndexed(router, remote), "fora do índice com o upgrade");
                    router.removeRangeUpgrade();
                    helper.assertFalse(RemoteIndex.get().isIndexed(router, remote), "no índice depois de tirar o upgrade");
                    router.setRangeUpgrade(rangeUpgrade());
                    router.unlinkMachine(remote);
                    helper.assertFalse(RemoteIndex.get().isIndexed(router, remote), "no índice depois de desvincular");
                })
                .thenSucceed();
    }

    /**
     * Só as máquinas ativas entram no índice: a de fora do raio não entra; com o limite do Avançado
     * cheio, a seguinte não entra até uma das primeiras ser desvinculada.
     */
    @GameTest(template = "empty")
    public static void onlyActiveMachinesIndexed(GameTestHelper helper) {
        RouterBlockEntity router = rangeRouter(helper);
        int radius = Config.machineRadius(RouterTier.ADVANCED);
        int max = Config.maxMachines(RouterTier.ADVANCED);
        BlockPos outside = new BlockPos(1, 3 + radius, 1);
        helper.setBlock(outside, Blocks.BARREL);
        BlockPos[] inside = new BlockPos[max + 1];
        int n = 0;
        for (int y = 3; y <= 5 && n < inside.length; y++) {
            for (int x = 0; x <= 2 && n < inside.length; x += 2) {
                for (int z = 0; z <= 2 && n < inside.length; z += 2) {
                    inside[n] = new BlockPos(x, y, z);
                    helper.setBlock(inside[n++], Blocks.BARREL);
                }
            }
        }
        int placed = n;
        helper.startSequence()
                .thenWaitUntil(() -> waitLoaded(helper, router))
                .thenExecute(() -> {
                    helper.assertTrue(placed == max + 1 && radius >= 3, "a config padrão mudou: limite " + max + ", raio " + radius);
                    router.setRangeUpgrade(rangeUpgrade());
                    link(helper, router, outside);
                    for (BlockPos pos : inside) {
                        link(helper, router, pos);
                    }
                    RemoteIndex index = RemoteIndex.get();
                    helper.assertFalse(index.isIndexed(router, helper.absolutePos(outside)), "fora do raio no índice");
                    for (int i = 0; i < max; i++) {
                        helper.assertTrue(index.isIndexed(router, helper.absolutePos(inside[i])), "ativa fora do índice: " + i);
                    }
                    BlockPos extra = helper.absolutePos(inside[max]);
                    helper.assertFalse(index.isIndexed(router, extra), "além do limite no índice");
                    router.unlinkMachine(helper.absolutePos(inside[0]));
                    helper.assertTrue(index.isIndexed(router, extra), "a seguinte não entrou ao abrir a vaga");
                })
                .thenSucceed();
    }

    /** Quebrar o roteador ({@code destroyBlock}) tira as máquinas dele do índice. */
    @GameTest(template = "empty")
    public static void breakingRouterRemovesFromIndex(GameTestHelper helper) {
        RouterBlockEntity router = rangeRouter(helper);
        helper.setBlock(REMOTE_CHEST, Blocks.CHEST);
        helper.startSequence()
                .thenWaitUntil(() -> waitLoaded(helper, router))
                .thenExecute(() -> {
                    router.setRangeUpgrade(rangeUpgrade());
                    link(helper, router, REMOTE_CHEST);
                    helper.assertTrue(RemoteIndex.get().isIndexed(router, helper.absolutePos(REMOTE_CHEST)), "fora do índice");
                    helper.destroyBlock(ROUTER);
                    helper.assertBlockNotPresent(ModBlocks.ROUTER.get(), ROUTER);
                    helper.assertFalse(RemoteIndex.get().isIndexed(router, helper.absolutePos(REMOTE_CHEST)),
                            "o roteador quebrado ficou no índice");
                })
                .thenSucceed();
    }
}
