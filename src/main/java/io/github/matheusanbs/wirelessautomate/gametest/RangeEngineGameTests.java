package io.github.matheusanbs.wirelessautomate.gametest;

import static io.github.matheusanbs.wirelessautomate.gametest.RangeTestSupport.*;

import io.github.matheusanbs.wirelessautomate.Config;
import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.block.RouterBlockEntity;
import io.github.matheusanbs.wirelessautomate.block.RouterTier;
import io.github.matheusanbs.wirelessautomate.network.NetworkManager;
import io.github.matheusanbs.wirelessautomate.network.NetworkSavedData;
import io.github.matheusanbs.wirelessautomate.network.PortMode;
import io.github.matheusanbs.wirelessautomate.network.ResourceType;
import io.github.matheusanbs.wirelessautomate.registry.ModItems;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Roteador de alcance, o motor: as portas das máquinas remotas, a frente de cada uma, o raio, o limite de
 * máquinas, o upgrade e a config do raio.
 */
@GameTestHolder(WirelessAutomate.MODID)
@PrefixGameTestTemplate(false)
public final class RangeEngineGameTests {
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void attachedToRemoteChest(GameTestHelper helper) {
        RouterBlockEntity router = rangeRouter(helper);
        helper.setBlock(REMOTE_CHEST, Blocks.CHEST);
        container(helper, CHEST).setItem(0, new ItemStack(Items.COBBLESTONE, 16));
        helper.startSequence()
                .thenWaitUntil(() -> waitLoaded(helper, router))
                .thenExecute(() -> {
                    router.setRangeUpgrade(rangeUpgrade());
                    router.setMode(ResourceType.ITEM, Direction.UP, PortMode.EXTRACT);
                    linkInNetwork(helper, router, REMOTE_CHEST);
                    profile(helper, router, REMOTE_CHEST, Direction.UP, PortMode.INSERT);
                })
                .thenWaitUntil(() -> helper.assertTrue(count(helper, REMOTE_CHEST) == 16, "os itens não chegaram ao baú remoto"))
                .thenExecute(() -> helper.assertTrue(count(helper, CHEST) == 0, "o baú preso não esvaziou"))
                .thenSucceed();
    }

    /**
     * A fornalha vanilla só dá o resultado por baixo ({@code WorldlyContainer}): "Baixo → Extrair" tira o
     * resultado de duas fornalhas com frentes diferentes. Os lados horizontais da fornalha são iguais
     * entre si, então quem prova que Trás segue a frente é {@link #backFollowsFront}.
     */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void remoteFurnaceOutput(GameTestHelper helper) {
        RouterBlockEntity router = routerWithMachines(helper);
        container(helper, FURNACE_A).setItem(2, new ItemStack(Items.IRON_INGOT, 5));
        container(helper, FURNACE_B).setItem(2, new ItemStack(Items.GOLD_INGOT, 7));
        helper.startSequence()
                .thenWaitUntil(() -> waitLoaded(helper, router))
                .thenExecute(() -> {
                    router.setRangeUpgrade(rangeUpgrade());
                    router.setMode(ResourceType.ITEM, Direction.UP, PortMode.INSERT);
                    linkInNetwork(helper, router, FURNACE_A);
                    linkInNetwork(helper, router, FURNACE_B);
                    profile(helper, router, FURNACE_A, Direction.DOWN, PortMode.EXTRACT);
                })
                .thenWaitUntil(() -> helper.assertTrue(count(helper, CHEST) == 12, "o resultado das fornalhas não chegou"))
                .thenExecute(() -> {
                    helper.assertTrue(container(helper, FURNACE_A).getItem(2).isEmpty()
                            && container(helper, FURNACE_B).getItem(2).isEmpty(), "sobrou resultado numa fornalha");
                    helper.assertTrue(remotePort(helper, router, FURNACE_A, Direction.DOWN)
                            && remotePort(helper, router, FURNACE_B, Direction.DOWN), "porta de baixo ausente");
                })
                .thenSucceed();
    }

    /**
     * "Trás → Extrair" numa máquina de teste ({@link TestMachines#BACK_SLOT}, abóbora esculpida) que só
     * tem inventário pela face de trás: com frente Leste, Trás é Oeste; com frente Sul, é Norte. Se o
     * lado não seguisse a frente, a consulta cairia numa face sem inventário e nada sairia.
     */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void backFollowsFront(GameTestHelper helper) {
        RouterBlockEntity router = rangeRouter(helper);
        BlockPos east = new BlockPos(0, 1, 0);
        BlockPos south = new BlockPos(2, 1, 2);
        helper.setBlock(east, TestMachines.BACK_SLOT.defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, Direction.EAST));
        helper.setBlock(south, TestMachines.BACK_SLOT.defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, Direction.SOUTH));
        TestMachines.reset(helper.absolutePos(east));
        TestMachines.reset(helper.absolutePos(south));
        TestMachines.bigSlot(helper.absolutePos(east)).set(Items.COBBLESTONE, 10);
        TestMachines.bigSlot(helper.absolutePos(south)).set(Items.DIRT, 10);
        helper.startSequence()
                .thenWaitUntil(() -> waitLoaded(helper, router))
                .thenExecute(() -> {
                    router.setRangeUpgrade(rangeUpgrade());
                    router.setMode(ResourceType.ITEM, Direction.UP, PortMode.INSERT);
                    linkInNetwork(helper, router, east);
                    linkInNetwork(helper, router, south);
                    // Trás, no quadro canônico (frente = Norte), é o Sul.
                    profile(helper, router, east, Direction.SOUTH, PortMode.EXTRACT);
                })
                .thenWaitUntil(() -> helper.assertTrue(count(helper, CHEST) == 20, "o lado de trás não deu os itens"))
                .thenExecute(() -> {
                    helper.assertTrue(remotePort(helper, router, east, Direction.WEST), "Trás da frente Leste não é Oeste");
                    helper.assertTrue(remotePort(helper, router, south, Direction.NORTH), "Trás da frente Sul não é Norte");
                    helper.assertFalse(remotePort(helper, router, east, Direction.EAST), "porta na frente");
                    TestMachines.reset(helper.absolutePos(east));
                    TestMachines.reset(helper.absolutePos(south));
                })
                .thenSucceed();
    }

    /** Barril no raio recebe; o de fora do raio (um bloco além, em cima) não vira porta e não recebe. */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void outOfRadiusDoesNotMove(GameTestHelper helper) {
        RouterBlockEntity router = rangeRouter(helper);
        int radius = Config.machineRadius(RouterTier.ADVANCED);
        BlockPos inside = new BlockPos(1, 2 + radius, 1);
        BlockPos outside = new BlockPos(1, 3 + radius, 1);
        helper.setBlock(inside, Blocks.BARREL);
        helper.setBlock(outside, Blocks.BARREL);
        container(helper, CHEST).setItem(0, new ItemStack(Items.COBBLESTONE, 64));
        helper.startSequence()
                .thenWaitUntil(() -> waitLoaded(helper, router))
                .thenExecute(() -> {
                    router.setRangeUpgrade(rangeUpgrade());
                    router.setMode(ResourceType.ITEM, Direction.UP, PortMode.EXTRACT);
                    linkInNetwork(helper, router, outside);
                    linkInNetwork(helper, router, inside);
                    profile(helper, router, inside, Direction.UP, PortMode.INSERT);
                })
                .thenWaitUntil(() -> helper.assertTrue(count(helper, inside) == 64, "o barril no raio não recebeu tudo"))
                .thenExecute(() -> {
                    helper.assertTrue(count(helper, outside) == 0, "o barril fora do raio recebeu");
                    helper.assertFalse(remotePort(helper, router, outside, Direction.UP), "porta fora do raio");
                })
                .thenSucceed();
    }

    /** Com o limite do Avançado em N máquinas, a máquina N+1 (na ordem de vínculo) não vira porta. */
    @GameTest(template = "empty", timeoutTicks = 300)
    public static void machineBeyondLimitDoesNotMove(GameTestHelper helper) {
        RouterBlockEntity router = rangeRouter(helper);
        int max = Config.maxMachines(RouterTier.ADVANCED);
        helper.assertTrue(max >= 1 && max + 1 <= 18, "teste pensado para até 17 máquinas, config com " + max);
        BlockPos[] barrels = new BlockPos[max + 1];
        for (int i = 0; i < barrels.length; i++) {
            barrels[i] = new BlockPos(i % 3, 4 + i / 9, i / 3 % 3);
            helper.setBlock(barrels[i], Blocks.BARREL);
        }
        Container chest = container(helper, CHEST);
        for (int i = 0; i < chest.getContainerSize(); i++) {
            chest.setItem(i, new ItemStack(Items.COBBLESTONE, 64));
        }
        helper.startSequence()
                .thenWaitUntil(() -> waitLoaded(helper, router))
                .thenExecute(() -> {
                    router.setRangeUpgrade(rangeUpgrade());
                    router.setMode(ResourceType.ITEM, Direction.UP, PortMode.EXTRACT);
                    for (BlockPos barrel : barrels) {
                        linkInNetwork(helper, router, barrel);
                    }
                    profile(helper, router, barrels[0], Direction.UP, PortMode.INSERT);
                })
                .thenWaitUntil(() -> {
                    for (int i = 0; i < max; i++) {
                        helper.assertTrue(count(helper, barrels[i]) > 0, "a máquina " + (i + 1) + " não recebeu");
                    }
                })
                .thenExecute(() -> {
                    helper.assertTrue(count(helper, barrels[max]) == 0, "a máquina além do limite recebeu");
                    helper.assertTrue(remotePort(helper, router, barrels[max - 1], Direction.UP), "a última no limite sem porta");
                    helper.assertFalse(remotePort(helper, router, barrels[max], Direction.UP), "porta além do limite");
                })
                .thenSucceed();
    }

    /** Sem o Upgrade de Alcance, as máquinas vinculadas ficam guardadas e não viram porta. */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void withoutUpgradeRemotesDoNotMove(GameTestHelper helper) {
        RouterBlockEntity router = rangeRouter(helper);
        helper.setBlock(REMOTE_CHEST, Blocks.CHEST);
        container(helper, CHEST).setItem(0, new ItemStack(Items.COBBLESTONE, 16));
        helper.startSequence()
                .thenWaitUntil(() -> waitLoaded(helper, router))
                .thenExecute(() -> {
                    router.setMode(ResourceType.ITEM, Direction.UP, PortMode.EXTRACT);
                    linkInNetwork(helper, router, REMOTE_CHEST);
                    profile(helper, router, REMOTE_CHEST, Direction.UP, PortMode.INSERT);
                })
                .thenExecuteAfter(40, () -> {
                    helper.assertTrue(count(helper, REMOTE_CHEST) == 0, "a remota recebeu sem o upgrade");
                    helper.assertTrue(count(helper, CHEST) == 16, "o baú preso perdeu itens");
                    helper.assertFalse(remotePort(helper, router, REMOTE_CHEST, Direction.UP), "porta remota sem o upgrade");
                })
                .thenSucceed();
    }

    /** Tirar o upgrade com as remotas movendo: as portas delas saem na remontagem e nada mais chega. */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void removingUpgradeStopsRemotes(GameTestHelper helper) {
        RouterBlockEntity router = rangeRouter(helper);
        helper.setBlock(REMOTE_CHEST, Blocks.CHEST);
        container(helper, CHEST).setItem(0, new ItemStack(Items.COBBLESTONE, 1));
        int[] before = new int[1];
        helper.startSequence()
                .thenWaitUntil(() -> waitLoaded(helper, router))
                .thenExecute(() -> {
                    router.setRangeUpgrade(rangeUpgrade());
                    router.setMode(ResourceType.ITEM, Direction.UP, PortMode.EXTRACT);
                    linkInNetwork(helper, router, REMOTE_CHEST);
                    profile(helper, router, REMOTE_CHEST, Direction.UP, PortMode.INSERT);
                })
                .thenWaitUntil(() -> helper.assertTrue(count(helper, REMOTE_CHEST) == 1, "a remota não recebeu com o upgrade"))
                .thenExecute(() -> {
                    helper.assertTrue(router.removeRangeUpgrade().is(ModItems.RANGE_UPGRADE.get()), "o upgrade não saiu");
                    container(helper, CHEST).setItem(0, new ItemStack(Items.COBBLESTONE, 16));
                    before[0] = count(helper, REMOTE_CHEST);
                })
                .thenExecuteAfter(40, () -> {
                    helper.assertTrue(count(helper, REMOTE_CHEST) == before[0], "a remota recebeu depois de tirar o upgrade");
                    helper.assertTrue(count(helper, CHEST) == 16, "o baú preso perdeu itens");
                    helper.assertFalse(remotePort(helper, router, REMOTE_CHEST, Direction.UP), "porta remota sem o upgrade");
                })
                .thenSucceed();
    }

    /** Uma máquina fora do raio vinculada primeiro não gasta vaga: as N de dentro do raio movem todas. */
    @GameTest(template = "empty", timeoutTicks = 300)
    public static void outOfRangeDoesNotTakeASlot(GameTestHelper helper) {
        RouterBlockEntity router = rangeRouter(helper);
        int radius = Config.machineRadius(RouterTier.ADVANCED);
        int max = Config.maxMachines(RouterTier.ADVANCED);
        helper.assertTrue(max >= 1 && max <= 18, "teste pensado para até 18 máquinas, config com " + max);
        BlockPos outside = new BlockPos(1, 3 + radius, 1);
        helper.setBlock(outside, Blocks.BARREL);
        BlockPos[] barrels = new BlockPos[max];
        for (int i = 0; i < barrels.length; i++) {
            barrels[i] = new BlockPos(i % 3, 4 + i / 9, i / 3 % 3);
            helper.setBlock(barrels[i], Blocks.BARREL);
        }
        Container chest = container(helper, CHEST);
        for (int i = 0; i < chest.getContainerSize(); i++) {
            chest.setItem(i, new ItemStack(Items.COBBLESTONE, 64));
        }
        helper.startSequence()
                .thenWaitUntil(() -> waitLoaded(helper, router))
                .thenExecute(() -> {
                    router.setRangeUpgrade(rangeUpgrade());
                    router.setMode(ResourceType.ITEM, Direction.UP, PortMode.EXTRACT);
                    linkInNetwork(helper, router, outside);
                    for (BlockPos barrel : barrels) {
                        linkInNetwork(helper, router, barrel);
                    }
                    profile(helper, router, outside, Direction.UP, PortMode.INSERT);
                })
                .thenWaitUntil(() -> {
                    for (int i = 0; i < max; i++) {
                        helper.assertTrue(count(helper, barrels[i]) > 0, "a máquina " + (i + 1) + " no raio não recebeu");
                    }
                })
                .thenExecute(() -> {
                    helper.assertTrue(count(helper, outside) == 0, "a máquina fora do raio recebeu");
                    helper.assertTrue(remotePort(helper, router, barrels[max - 1], Direction.UP), "a última no raio sem porta");
                    helper.assertFalse(remotePort(helper, router, outside, Direction.UP), "porta fora do raio");
                })
                .thenSucceed();
    }

    /** Desvincular tira as portas da máquina na remontagem e nada mais chega a ela. */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void unlinkRemovesPorts(GameTestHelper helper) {
        RouterBlockEntity router = rangeRouter(helper);
        helper.setBlock(REMOTE_CHEST, Blocks.CHEST);
        container(helper, CHEST).setItem(0, new ItemStack(Items.COBBLESTONE, 1));
        helper.startSequence()
                .thenWaitUntil(() -> waitLoaded(helper, router))
                .thenExecute(() -> {
                    router.setRangeUpgrade(rangeUpgrade());
                    router.setMode(ResourceType.ITEM, Direction.UP, PortMode.EXTRACT);
                    linkInNetwork(helper, router, REMOTE_CHEST);
                    profile(helper, router, REMOTE_CHEST, Direction.UP, PortMode.INSERT);
                })
                .thenWaitUntil(() -> helper.assertTrue(count(helper, REMOTE_CHEST) == 1, "a remota não recebeu"))
                .thenExecute(() -> {
                    helper.assertTrue(router.unlinkMachine(helper.absolutePos(REMOTE_CHEST)), "não desvinculou");
                    container(helper, CHEST).setItem(0, new ItemStack(Items.COBBLESTONE, 16));
                })
                .thenExecuteAfter(40, () -> {
                    helper.assertTrue(count(helper, REMOTE_CHEST) == 1, "a máquina desvinculada recebeu");
                    helper.assertTrue(count(helper, CHEST) == 16, "o baú preso perdeu itens");
                    helper.assertFalse(remotePort(helper, router, REMOTE_CHEST, Direction.UP), "porta da desvinculada");
                })
                .thenSucceed();
    }

    /**
     * Baixar o raio na config (num Elite, que nenhum outro teste usa com remotas) relê as portas na
     * remontagem: a remota a 4 blocos para de mover. O raio só é lido na remontagem, então o passo que
     * confere baixa a config, roda alguns ticks do motor à mão e restaura no {@code finally}, tudo
     * síncrono (a config nunca fica baixa entre ticks do jogo). Num lote próprio: a
     * recarga da config relê (e acorda) as portas de todos os roteadores com alcance, o que atrapalharia
     * os testes de sono que rodariam em paralelo.
     */
    @GameTest(template = "empty", timeoutTicks = 300, batch = "wirelessautomate_config")
    public static void configRadiusRereadsPorts(GameTestHelper helper) {
        RouterBlockEntity router = chestWithRouter(helper, RouterTier.ELITE);
        router.setNetworkId(NetworkSavedData.get(helper.getLevel().getServer()).create(UUID.randomUUID(), "raio").id());
        helper.setBlock(REMOTE_CHEST, Blocks.CHEST);
        container(helper, CHEST).setItem(0, new ItemStack(Items.COBBLESTONE, 1));
        var radius = Config.TIERS.get(RouterTier.ELITE).machineRadius();
        int[] original = new int[1];
        helper.startSequence()
                .thenWaitUntil(() -> waitLoaded(helper, router))
                .thenExecute(() -> {
                    router.setRangeUpgrade(rangeUpgrade());
                    router.setMode(ResourceType.ITEM, Direction.UP, PortMode.EXTRACT);
                    linkInNetwork(helper, router, REMOTE_CHEST);
                    profile(helper, router, REMOTE_CHEST, Direction.UP, PortMode.INSERT);
                })
                .thenWaitUntil(() -> helper.assertTrue(count(helper, REMOTE_CHEST) == 1, "a remota não recebeu"))
                .thenExecute(() -> {
                    original[0] = radius.get();
                    helper.assertTrue(original[0] >= 4, "o raio do Elite já não alcança a remota: " + original[0]);
                    container(helper, CHEST).setItem(0, new ItemStack(Items.COBBLESTONE, 16));
                    // A config baixa e volta no mesmo passo síncrono, para a restauração ser garantida pelo
                    // finally. O raio só é lido na remontagem: os ticks do motor rodam aqui, com ele baixo.
                    MinecraftServer server = helper.getLevel().getServer();
                    try {
                        radius.set(2);
                        NetworkManager.configChanged();
                        for (int tick = 0; tick < 5; tick++) {
                            NetworkManager.get().tick(server);
                        }
                        helper.assertFalse(remotePort(helper, router, REMOTE_CHEST, Direction.UP), "porta fora do raio novo");
                        helper.assertTrue(count(helper, REMOTE_CHEST) == 1, "a remota recebeu fora do raio novo");
                        helper.assertTrue(count(helper, CHEST) == 16, "o baú preso perdeu itens");
                    } finally {
                        radius.set(original[0]);
                        NetworkManager.configChanged();
                    }
                })
                .thenSucceed();
    }
}
