package io.github.matheusanbs.wirelessautomate.gametest;

import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.block.RouterBlock;
import io.github.matheusanbs.wirelessautomate.block.RouterBlockEntity;
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
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

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
        RouterBlockEntity router = GameTestCompat.getBlockEntity(helper, routerPos);
        router.setNetworkId(network);
        return router;
    }

    private static RouterBlockEntity chest(GameTestHelper helper, BlockPos pos, UUID network, PortMode mode) {
        RouterBlockEntity router = place(helper, pos, Blocks.CHEST.defaultBlockState(), network);
        router.setMode(ResourceType.ITEM, Direction.UP, mode);
        return router;
    }

    private static ChestBlockEntity chestAt(GameTestHelper helper, BlockPos pos) {
        return GameTestCompat.getBlockEntity(helper, pos);
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

    private static void assertDeepSleep(GameTestHelper helper, RouterBlockEntity router, String what) {
        int interval = interval(router);
        helper.assertTrue(interval >= DEEP_SLEEP, what + " acordou: próximo sono de " + interval + " ticks");
    }

    /**
     * #1: A e E (vazias) entregam em B. Com E dormindo fundo, A recebe itens e entrega em B: o aviso
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
                .thenWaitUntil(() -> GameTestCompat.assertValueEqual(helper, count(helper, B, Items.DIAMOND), 10, "diamantes em B"))
                .thenExecute(() -> assertDeepSleep(helper, e, "E vazia"))
                .thenSucceed();
    }

    /**
     * #1, motivo do sono: B está cheio; A tem itens e espera destino, E está vazia. Abrir espaço em B
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
                    GameTestCompat.assertValueEqual(helper, interval(a), 1, "próximo sono de A, que esperava destino");
                    assertDeepSleep(helper, e, "E vazia");
                })
                .thenWaitUntil(() -> GameTestCompat.assertValueEqual(helper, count(helper, B, Items.DIAMOND), 10, "diamantes em B"))
                .thenExecute(() -> helper.assertTrue(helper.getTick() - freedAt[0] < 10,
                        "A demorou " + (helper.getTick() - freedAt[0]) + " ticks para entregar"))
                .thenSucceed();
    }

    /**
     * Bônus do #2: um destino sem máquina (pedra, sem inventário) dorme sem contar como cheio; pôr um
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
                    GameTestCompat.assertValueEqual(helper, stats.destinationsFull(), 0, "destinos cheios");
                    GameTestCompat.assertValueEqual(helper, NodeProbe.fullDestinations(d, ResourceType.ITEM, helper.getTick()), 0,
                            "cheios no Tablet");
                })
                .thenIdle(10)
                .thenExecute(() -> {
                    helper.setBlock(B, Blocks.CHEST);
                    placedAt[0] = helper.getTick();
                })
                .thenWaitUntil(() -> GameTestCompat.assertValueEqual(helper, count(helper, B, Items.DIAMOND), 10, "diamantes em B"))
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
     * #4: uma remontagem sem destino novo (uma origem nova entrou na rede) não acorda a origem que
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

    /** #3: trocar o sinal de redstone num roteador sem face com modo de redstone não suja a rede. */
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

    /** #3 e remontagem por tipo: com uma face de itens por redstone, o sinal suja só os itens. */
    @GameTest(template = "empty")
    public static void redstoneModeDirtiesOnlyItsType(GameTestHelper helper) {
        UUID network = newNetwork(helper, "teste-redstone-com-modo");
        RouterBlockEntity router = chest(helper, A, network, PortMode.EXTRACT);
        router.setRedstone(ResourceType.ITEM, Direction.UP, RedstoneMode.HIGH);
        BlockPos beside = A.above().east();

        helper.startSequence()
                .thenWaitUntil(() -> waitRegistered(helper, router))
                .thenWaitUntil(() -> helper.assertFalse(NetworkManager.get().isDirty(network, ResourceType.ITEM),
                        "a rede ainda não remontou"))
                .thenExecute(() -> {
                    helper.setBlock(beside, Blocks.REDSTONE_BLOCK);
                    helper.assertTrue(NetworkManager.get().isDirty(network, ResourceType.ITEM), "itens não sujaram");
                    helper.assertFalse(NetworkManager.get().isDirty(network, ResourceType.FLUID), "fluidos sujaram");
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
     * #5: a fornalha acender troca só o estado do bloco, não o bloco: não conta como máquina trocada
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
                    GameTestCompat.assertValueEqual(helper, router.changeVersion(), version[0], "versão depois de acender a fornalha");
                    helper.setBlock(A, Blocks.BLAST_FURNACE.defaultBlockState());
                    helper.assertTrue(router.changeVersion() != version[0], "trocar a máquina não mudou a versão");
                })
                .thenSucceed();
    }
}
