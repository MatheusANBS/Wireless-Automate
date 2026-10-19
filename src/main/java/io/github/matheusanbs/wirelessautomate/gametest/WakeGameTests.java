package io.github.matheusanbs.wirelessautomate.gametest;

import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.block.RouterBlock;
import io.github.matheusanbs.wirelessautomate.block.RouterBlockEntity;
import io.github.matheusanbs.wirelessautomate.block.RouterTier;
import io.github.matheusanbs.wirelessautomate.filter.Filter;
import io.github.matheusanbs.wirelessautomate.filter.FilterEntry;
import io.github.matheusanbs.wirelessautomate.network.NetworkManager;
import io.github.matheusanbs.wirelessautomate.network.NetworkSavedData;
import io.github.matheusanbs.wirelessautomate.network.NetworkStats;
import io.github.matheusanbs.wirelessautomate.network.NodeProbe;
import io.github.matheusanbs.wirelessautomate.network.PortMode;
import io.github.matheusanbs.wirelessautomate.network.RedstoneMode;
import io.github.matheusanbs.wirelessautomate.network.ResourceType;
import io.github.matheusanbs.wirelessautomate.registry.ModBlocks;
import io.github.matheusanbs.wirelessautomate.storage.StorageChestBlockEntity;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Sono, despertar e remontagem: o que acorda uma origem dormindo, o que suja a rede e o que conta
 * como máquina trocada. Máquinas em y=1 com o roteador em cima (facing=UP), então a face configurada
 * é {@link Direction#UP}. Os baús ficam afastados para não virarem baú duplo.
 */
@GameTestHolder(WirelessAutomate.MODID)
@PrefixGameTestTemplate(false)
public final class WakeGameTests {
    private static final BlockPos A = new BlockPos(0, 1, 0);
    private static final BlockPos B = new BlockPos(0, 1, 2);
    private static final BlockPos E = new BlockPos(2, 1, 2);
    private static final BlockPos C = new BlockPos(2, 1, 0);
    /** Uma origem que já dormiu várias vezes seguidas tem o próximo sono pelo menos assim (ticks). */
    private static final int DEEP_SLEEP = 32;

    private static UUID newNetwork(GameTestHelper helper, String name) {
        return NetworkSavedData.get(helper.getLevel().getServer()).create(UUID.randomUUID(), name).id();
    }

    private static RouterBlockEntity place(GameTestHelper helper, BlockPos machine, BlockState machineState, UUID network) {
        helper.setBlock(machine, machineState);
        BlockPos routerPos = machine.above();
        helper.setBlock(routerPos, ModBlocks.ROUTER.get().defaultBlockState().setValue(RouterBlock.FACING, Direction.UP));
        RouterBlockEntity router = helper.getBlockEntity(routerPos);
        router.setNetworkId(network);
        return router;
    }

    private static RouterBlockEntity chest(GameTestHelper helper, BlockPos pos, UUID network, PortMode mode) {
        RouterBlockEntity router = place(helper, pos, Blocks.CHEST.defaultBlockState(), network);
        router.setMode(ResourceType.ITEM, Direction.UP, mode);
        return router;
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

    private static int interval(RouterBlockEntity router) {
        return NetworkManager.get().sourceSleepInterval(router, ResourceType.ITEM, Direction.UP);
    }

    private static int destinationInterval(RouterBlockEntity router) {
        return NetworkManager.get().destinationSleepInterval(router, ResourceType.ITEM, Direction.UP);
    }

    private static void assertDeepSleep(GameTestHelper helper, RouterBlockEntity router, String what) {
        int interval = interval(router);
        helper.assertTrue(interval >= DEEP_SLEEP, what + " acordou: próximo sono de " + interval + " ticks");
    }

    /**
     * A e E (vazias) entregam em B. Com E dormindo fundo, A recebe itens e entrega em B: o aviso
     * de B (a nossa inserção) não pode acordar E, que continua vazia.
     */
    @GameTest(template = "empty", timeoutTicks = 400)
    public static void deliveryDoesNotWakeEmptySourceOfAnotherMachine(GameTestHelper helper) {
        UUID network = newNetwork(helper, "teste-sono-entrega");
        RouterBlockEntity a = chest(helper, A, network, PortMode.EXTRACT);
        RouterBlockEntity b = chest(helper, B, network, PortMode.INSERT);
        RouterBlockEntity e = chest(helper, E, network, PortMode.EXTRACT);

        helper.startSequence()
                .thenWaitUntil(() -> waitRegistered(helper, a, b, e))
                .thenWaitUntil(() -> assertDeepSleep(helper, e, "E"))
                .thenExecute(() -> chestAt(helper, A).setItem(0, new ItemStack(Items.DIAMOND, 10)))
                .thenWaitUntil(() -> helper.assertValueEqual(count(helper, B, Items.DIAMOND), 10, "diamantes em B"))
                .thenExecute(() -> assertDeepSleep(helper, e, "E vazia"))
                .thenSucceed();
    }

    /**
     * Motivo do sono: B está cheio; A tem itens e espera destino, E está vazia. Abrir espaço em B
     * por fora acorda A na hora (e A entrega), mas não E. O destino D é um baú que recusa diamantes
     * pelo filtro: recusa de filtro não faz dormir, então D fica acordado e E dorme por estar vazia, e
     * não por todos os destinos dormirem.
     */
    @GameTest(template = "empty", timeoutTicks = 400)
    public static void destinationChangeWakesOnlySourcesWaitingForIt(GameTestHelper helper) {
        UUID network = newNetwork(helper, "teste-sono-motivo");
        RouterBlockEntity a = chest(helper, A, network, PortMode.EXTRACT);
        RouterBlockEntity b = chest(helper, B, network, PortMode.INSERT);
        RouterBlockEntity e = chest(helper, E, network, PortMode.EXTRACT);
        RouterBlockEntity d = chest(helper, C, network, PortMode.INSERT);
        d.setFilter(ResourceType.ITEM, Direction.UP, new Filter(Filter.ListMode.BLACKLIST, false,
                List.of(new FilterEntry.ItemEntry(new ItemStack(Items.DIAMOND), 0))));
        chestAt(helper, A).setItem(0, new ItemStack(Items.DIAMOND, 10));
        ChestBlockEntity full = chestAt(helper, B);
        for (int i = 0; i < full.getContainerSize(); i++) {
            full.setItem(i, new ItemStack(Items.DIRT, 64));
        }
        long[] freedAt = {0};

        helper.startSequence()
                .thenWaitUntil(() -> waitRegistered(helper, a, b, e, d))
                .thenWaitUntil(() -> {
                    assertDeepSleep(helper, a, "A");
                    assertDeepSleep(helper, e, "E");
                })
                .thenExecute(() -> {
                    chestAt(helper, B).setItem(5, ItemStack.EMPTY);
                    freedAt[0] = helper.getTick();
                    helper.assertValueEqual(interval(a), 1, "próximo sono de A, que esperava destino");
                    assertDeepSleep(helper, e, "E vazia");
                })
                .thenWaitUntil(() -> helper.assertValueEqual(count(helper, B, Items.DIAMOND), 10, "diamantes em B"))
                .thenExecute(() -> helper.assertTrue(helper.getTick() - freedAt[0] < 10,
                        "A demorou " + (helper.getTick() - freedAt[0]) + " ticks para entregar"))
                .thenSucceed();
    }

    /**
     * Um destino sem máquina (pedra, sem inventário) dorme sem contar como cheio; pôr um
     * baú no lugar invalida a capability, o listener acorda o destino e a origem que esperava, e a
     * entrega sai logo, sem esperar o teto do sono.
     */
    @GameTest(template = "empty", timeoutTicks = 300)
    public static void destinationWithoutMachineSleepsUntilMachineAppears(GameTestHelper helper) {
        UUID network = newNetwork(helper, "teste-destino-sem-maquina");
        RouterBlockEntity a = chest(helper, A, network, PortMode.EXTRACT);
        RouterBlockEntity d = place(helper, B, Blocks.STONE.defaultBlockState(), network);
        d.setMode(ResourceType.ITEM, Direction.UP, PortMode.INSERT);
        chestAt(helper, A).setItem(0, new ItemStack(Items.DIAMOND, 10));
        long[] placedAt = {0};

        helper.startSequence()
                .thenWaitUntil(() -> waitRegistered(helper, a, d))
                .thenWaitUntil(() -> {
                    NetworkStats stats = stats(helper, network);
                    helper.assertTrue(stats != null && stats.destinationsSleeping() == 1, "o destino sem máquina não dormiu");
                    helper.assertValueEqual(stats.destinationsFull(), 0, "destinos cheios");
                    helper.assertValueEqual(NodeProbe.fullDestinations(d, ResourceType.ITEM, helper.getTick()), 0,
                            "cheios no Tablet");
                })
                .thenIdle(10)
                .thenExecute(() -> {
                    helper.setBlock(B, Blocks.CHEST);
                    placedAt[0] = helper.getTick();
                })
                .thenWaitUntil(() -> helper.assertValueEqual(count(helper, B, Items.DIAMOND), 10, "diamantes em B"))
                .thenExecute(() -> helper.assertTrue(helper.getTick() - placedAt[0] < 10,
                        "a entrega demorou " + (helper.getTick() - placedAt[0]) + " ticks"))
                .thenSucceed();
    }

    private static NetworkStats stats(GameTestHelper helper, UUID network) {
        for (NetworkStats stats : NetworkManager.get().stats(helper.getLevel().getServer())) {
            if (stats.id().equals(network)) {
                return stats;
            }
        }
        return null;
    }

    /**
     * Uma remontagem sem destino novo (uma origem nova entrou na rede) não acorda a origem que
     * dorme nem zera o backoff dela.
     */
    @GameTest(template = "empty", timeoutTicks = 400)
    public static void rebuildWithoutNewDestinationKeepsSourcesAsleep(GameTestHelper helper) {
        UUID network = newNetwork(helper, "teste-sono-remontagem");
        RouterBlockEntity a = chest(helper, A, network, PortMode.EXTRACT);
        RouterBlockEntity b = chest(helper, B, network, PortMode.INSERT);
        RouterBlockEntity c = chest(helper, C, network, PortMode.NONE);

        helper.startSequence()
                .thenWaitUntil(() -> waitRegistered(helper, a, b, c))
                .thenWaitUntil(() -> assertDeepSleep(helper, a, "A"))
                .thenExecute(() -> {
                    c.setMode(ResourceType.ITEM, Direction.UP, PortMode.EXTRACT);
                    helper.assertTrue(NetworkManager.get().isDirty(network, ResourceType.ITEM), "a rede não sujou");
                })
                .thenWaitUntil(() -> helper.assertFalse(NetworkManager.get().isDirty(network, ResourceType.ITEM),
                        "a rede não remontou"))
                .thenExecute(() -> assertDeepSleep(helper, a, "A"))
                .thenSucceed();
    }

    /** Trocar o sinal de redstone num roteador sem face com modo de redstone não suja a rede. */
    @GameTest(template = "empty")
    public static void redstoneWithoutRedstoneModeDoesNotDirtyNetwork(GameTestHelper helper) {
        UUID network = newNetwork(helper, "teste-redstone-sem-modo");
        RouterBlockEntity router = chest(helper, A, network, PortMode.EXTRACT);
        BlockPos beside = A.above().east();

        helper.startSequence()
                .thenWaitUntil(() -> waitRegistered(helper, router))
                .thenWaitUntil(() -> helper.assertFalse(NetworkManager.get().isDirty(network, ResourceType.ITEM),
                        "a rede ainda não remontou"))
                .thenExecute(() -> {
                    helper.setBlock(beside, Blocks.REDSTONE_BLOCK);
                    helper.assertTrue(router.powered(), "o roteador não leu o sinal");
                    for (ResourceType type : ResourceType.values()) {
                        helper.assertFalse(NetworkManager.get().isDirty(network, type), "sujou " + type);
                    }
                    helper.setBlock(beside, Blocks.AIR);
                    helper.assertFalse(router.powered(), "o roteador não leu o fim do sinal");
                    helper.assertFalse(NetworkManager.get().isDirty(network, ResourceType.ITEM), "sujou ao desligar");
                })
                .thenSucceed();
    }

    /** Com uma face de itens por redstone, o sinal só trava e destrava a porta: nada suja nem remonta. */
    @GameTest(template = "empty")
    public static void redstoneSignalLatchesWithoutRebuild(GameTestHelper helper) {
        UUID network = newNetwork(helper, "teste-redstone-com-modo");
        RouterBlockEntity router = chest(helper, A, network, PortMode.EXTRACT);
        router.setRedstone(ResourceType.ITEM, Direction.UP, RedstoneMode.HIGH);
        BlockPos beside = A.above().east();

        helper.startSequence()
                .thenWaitUntil(() -> waitRegistered(helper, router))
                .thenWaitUntil(() -> helper.assertFalse(NetworkManager.get().isDirty(network, ResourceType.ITEM),
                        "a rede ainda não remontou"))
                .thenExecute(() -> {
                    helper.assertTrue(NetworkManager.get().redstoneOffForTests(router, null, ResourceType.ITEM,
                            Direction.UP), "sem sinal, a face não travou");
                    helper.setBlock(beside, Blocks.REDSTONE_BLOCK);
                    helper.assertFalse(NetworkManager.get().isDirty(network, ResourceType.ITEM), "itens sujaram");
                    helper.assertFalse(NetworkManager.get().isDirty(network, ResourceType.FLUID), "fluidos sujaram");
                    helper.assertFalse(NetworkManager.get().redstoneOffForTests(router, null, ResourceType.ITEM,
                            Direction.UP), "com sinal, a face continuou travada");
                })
                .thenSucceed();
    }

    /** Remontagem por tipo: mudar a aba Itens não suja Fluidos (nem Energia) da mesma rede. */
    @GameTest(template = "empty")
    public static void itemTabChangeDoesNotRebuildFluids(GameTestHelper helper) {
        UUID network = newNetwork(helper, "teste-remontagem-por-tipo");
        RouterBlockEntity router = place(helper, A, Blocks.CHEST.defaultBlockState(), network);
        router.setMode(ResourceType.FLUID, Direction.UP, PortMode.INSERT);

        helper.startSequence()
                .thenWaitUntil(() -> waitRegistered(helper, router))
                .thenWaitUntil(() -> {
                    helper.assertFalse(NetworkManager.get().isDirty(network, ResourceType.ITEM), "itens sujos");
                    helper.assertFalse(NetworkManager.get().isDirty(network, ResourceType.FLUID), "fluidos sujos");
                })
                .thenExecute(() -> {
                    router.setMode(ResourceType.ITEM, Direction.UP, PortMode.EXTRACT);
                    helper.assertTrue(NetworkManager.get().isDirty(network, ResourceType.ITEM), "itens não sujaram");
                    helper.assertFalse(NetworkManager.get().isDirty(network, ResourceType.FLUID), "fluidos sujaram");
                    helper.assertFalse(NetworkManager.get().isDirty(network, ResourceType.ENERGY), "energia sujou");
                })
                .thenSucceed();
    }

    /**
     * A fornalha acender troca só o estado do bloco, não o bloco: não conta como máquina trocada
     * (a versão da tela não muda). Trocar a fornalha por um alto-forno conta.
     */
    @GameTest(template = "empty")
    public static void machineStateChangeIsNotMachineChange(GameTestHelper helper) {
        UUID network = newNetwork(helper, "teste-maquina-estado");
        RouterBlockEntity router = place(helper, A, Blocks.FURNACE.defaultBlockState(), network);
        int[] version = {0};

        helper.startSequence()
                .thenWaitUntil(() -> waitRegistered(helper, router))
                .thenExecute(() -> {
                    version[0] = router.changeVersion();
                    helper.setBlock(A, Blocks.FURNACE.defaultBlockState().setValue(AbstractFurnaceBlock.LIT, true));
                    helper.assertValueEqual(router.changeVersion(), version[0], "versão depois de acender a fornalha");
                    helper.setBlock(A, Blocks.BLAST_FURNACE.defaultBlockState());
                    helper.assertTrue(router.changeVersion() != version[0], "trocar a máquina não mudou a versão");
                })
                .thenSucceed();
    }

    /**
     * Máquina que recusa pelo tipo (a Salvaging Table do Apotheosis com o que não se desmonta): o
     * baú de origem tem dez pilhas de pedregulho antes dos diamantes. Recusar o pedregulho não pode
     * pôr a máquina para dormir como cheia; antes, cada pilha recusada dobrava o sono e os diamantes
     * levavam mais de 400 ticks para chegar.
     */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void machineRefusingItemTypeDoesNotSleep(GameTestHelper helper) {
        UUID network = newNetwork(helper, "teste-recusa-tipo");
        TestMachines.reset(helper.absolutePos(B));
        RouterBlockEntity a = chest(helper, A, network, PortMode.EXTRACT);
        RouterBlockEntity b = place(helper, B, TestMachines.TYPED_SLOT.defaultBlockState(), network);
        b.setMode(ResourceType.ITEM, Direction.UP, PortMode.INSERT);
        ChestBlockEntity source = chestAt(helper, A);
        for (int i = 0; i < 10; i++) {
            source.setItem(i, new ItemStack(Items.COBBLESTONE, 64));
        }
        source.setItem(10, new ItemStack(Items.DIAMOND, 5));
        TestMachines.TypedSlot machine = TestMachines.typedSlot(helper.absolutePos(B));
        long[] start = {0};

        helper.startSequence()
                .thenWaitUntil(() -> waitRegistered(helper, a, b))
                .thenExecute(() -> start[0] = helper.getTick())
                .thenWaitUntil(() -> helper.assertValueEqual(machine.consumed(), 5, "diamantes consumidos"))
                .thenExecute(() -> helper.assertTrue(helper.getTick() - start[0] < 40,
                        "os diamantes levaram " + (helper.getTick() - start[0]) + " ticks"))
                .thenExecute(() -> helper.assertValueEqual(destinationInterval(b), 1, "sono de destino da máquina"))
                .thenExecute(() -> helper.assertValueEqual(count(helper, A, Items.COBBLESTONE), 640, "pedregulho na origem"))
                .thenSucceed();
    }

    /** O mesmo com o Baú do mod de origem (visita por tipos), o caso relatado com o Apotheosis. */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void machineRefusingItemTypeDoesNotSleepFromStorageChest(GameTestHelper helper) {
        UUID network = newNetwork(helper, "teste-recusa-tipo-bau");
        TestMachines.reset(helper.absolutePos(B));
        helper.setBlock(A, ModBlocks.STORAGE_CHEST.get().defaultBlockState().setValue(RouterBlock.TIER, RouterTier.ELITE));
        StorageChestBlockEntity storage = helper.getBlockEntity(A);
        Item[] junk = {Items.COBBLESTONE, Items.DIRT, Items.STONE, Items.SAND, Items.GRAVEL, Items.OAK_LOG,
                Items.ANDESITE, Items.DIORITE, Items.GRANITE, Items.TUFF};
        for (Item item : junk) {
            storage.storage().insert(new ItemStack(item), 1_000, false);
        }
        storage.storage().insert(new ItemStack(Items.DIAMOND), 5, false);
        BlockPos routerPos = A.above();
        helper.setBlock(routerPos, ModBlocks.ROUTER.get().defaultBlockState().setValue(RouterBlock.FACING, Direction.UP));
        RouterBlockEntity a = helper.getBlockEntity(routerPos);
        a.setNetworkId(network);
        a.setMode(ResourceType.ITEM, Direction.UP, PortMode.EXTRACT);
        RouterBlockEntity b = place(helper, B, TestMachines.TYPED_SLOT.defaultBlockState(), network);
        b.setMode(ResourceType.ITEM, Direction.UP, PortMode.INSERT);
        TestMachines.TypedSlot machine = TestMachines.typedSlot(helper.absolutePos(B));
        long[] start = {0};

        helper.startSequence()
                .thenWaitUntil(() -> waitRegistered(helper, a, b))
                .thenExecute(() -> start[0] = helper.getTick())
                .thenWaitUntil(() -> helper.assertValueEqual(machine.consumed(), 5, "diamantes consumidos"))
                .thenExecute(() -> helper.assertTrue(helper.getTick() - start[0] < 40,
                        "os diamantes levaram " + (helper.getTick() - start[0]) + " ticks"))
                .thenExecute(() -> helper.assertValueEqual(destinationInterval(b), 1, "sono de destino da máquina"))
                .thenSucceed();
    }

    /**
     * A mesma máquina cheia (recusa o diamante, que ela diz ser válido) continua dormindo como cheia,
     * cada vez mais fundo; a origem espera por ela.
     */
    @GameTest(template = "empty", timeoutTicks = 400)
    public static void fullMachineStillSleeps(GameTestHelper helper) {
        UUID network = newNetwork(helper, "teste-maquina-cheia");
        TestMachines.reset(helper.absolutePos(B));
        TestMachines.TypedSlot machine = TestMachines.typedSlot(helper.absolutePos(B));
        machine.setFull(true);
        RouterBlockEntity a = chest(helper, A, network, PortMode.EXTRACT);
        RouterBlockEntity b = place(helper, B, TestMachines.TYPED_SLOT.defaultBlockState(), network);
        b.setMode(ResourceType.ITEM, Direction.UP, PortMode.INSERT);
        chestAt(helper, A).setItem(0, new ItemStack(Items.DIAMOND, 5));

        helper.startSequence()
                .thenWaitUntil(() -> waitRegistered(helper, a, b))
                .thenWaitUntil(() -> helper.assertTrue(destinationInterval(b) >= DEEP_SLEEP,
                        "a máquina cheia não dormiu fundo: próximo sono de " + destinationInterval(b) + " ticks"))
                .thenExecute(() -> helper.assertValueEqual(machine.consumed(), 0, "diamantes consumidos"))
                .thenSucceed();
    }
}
