package io.github.matheusanbs.wirelessautomate.gametest;

import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.block.RouterBlock;
import io.github.matheusanbs.wirelessautomate.block.RouterBlockEntity;
import io.github.matheusanbs.wirelessautomate.block.RouterTier;
import io.github.matheusanbs.wirelessautomate.network.FlowSampler;
import io.github.matheusanbs.wirelessautomate.network.FlowStats;
import io.github.matheusanbs.wirelessautomate.network.NetworkManager;
import io.github.matheusanbs.wirelessautomate.network.NetworkSavedData;
import io.github.matheusanbs.wirelessautomate.network.PortMode;
import io.github.matheusanbs.wirelessautomate.network.ResourceType;
import io.github.matheusanbs.wirelessautomate.registry.ModBlocks;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.items.IItemHandler;

/**
 * O amostrador dos Fluxos pulando os nós sem mudança: os estados saem iguais pulando e lendo tudo, e o que muda
 * depois (itens postos numa origem vazia) aparece. Num lote só dele, porque desliga o pulo por um tempo.
 */
@GameTestHolder(WirelessAutomate.MODID)
@PrefixGameTestTemplate(false)
public final class FlowSkipGameTests {
    private static final String BATCH = "wirelessautomate_flows_skip";
    private static final BlockPos EMPTY_SOURCE = new BlockPos(0, 1, 0);
    private static final BlockPos EMPTY_TARGET = new BlockPos(2, 1, 0);
    private static final BlockPos WAITING_SOURCE = new BlockPos(0, 1, 2);
    private static final BlockPos FULL_TARGET = new BlockPos(2, 1, 2);

    private static RouterBlockEntity chest(GameTestHelper helper, BlockPos pos, UUID network, PortMode mode) {
        helper.setBlock(pos, Blocks.CHEST);
        helper.setBlock(pos.above(), ModBlocks.ROUTER.get().defaultBlockState()
                .setValue(RouterBlock.FACING, Direction.UP).setValue(RouterBlock.TIER, RouterTier.BASIC));
        RouterBlockEntity router = helper.getBlockEntity(pos.above());
        router.setNetworkId(network);
        router.setMode(ResourceType.ITEM, Direction.UP, mode);
        return router;
    }

    private static FlowSampler flows() {
        return NetworkManager.get().flows();
    }

    private static FlowStats.States states(GameTestHelper helper, UUID network) {
        FlowStats stats = flows().report().get(network, ResourceType.ITEM);
        helper.assertTrue(stats != null, "a rede não está nos Fluxos");
        return stats.states();
    }

    /** As duas redes no estado parado: uma origem vazia, e uma origem esperando um destino cheio. */
    private static void assertIdle(GameTestHelper helper, UUID empty, UUID waiting, String when) {
        FlowStats.States a = states(helper, empty);
        helper.assertTrue(a.sourcesEmpty() == 1 && a.sourcesWaiting() == 0 && a.sourcesMoving() == 0
                && a.destinationsFull() == 0, when + ": estados da origem vazia " + a);
        FlowStats.States b = states(helper, waiting);
        helper.assertTrue(b.sourcesWaiting() == 1 && b.destinationsFull() == 1 && b.sourcesEmpty() == 0
                && b.sourcesMoving() == 0, when + ": estados da origem esperando " + b);
    }

    @GameTest(template = "empty", batch = BATCH, timeoutTicks = 300)
    public static void skippedNodesKeepTheSameFlows(GameTestHelper helper) {
        NetworkSavedData data = NetworkSavedData.get(helper.getLevel().getServer());
        UUID empty = data.create(UUID.randomUUID(), "teste-fluxos-pulo-vazia").id();
        UUID waiting = data.create(UUID.randomUUID(), "teste-fluxos-pulo-espera").id();
        RouterBlockEntity[] routers = {
                chest(helper, EMPTY_SOURCE, empty, PortMode.EXTRACT),
                chest(helper, EMPTY_TARGET, empty, PortMode.INSERT),
                chest(helper, WAITING_SOURCE, waiting, PortMode.EXTRACT),
                chest(helper, FULL_TARGET, waiting, PortMode.INSERT)};
        ((ChestBlockEntity) helper.getBlockEntity(WAITING_SOURCE)).setItem(0, new ItemStack(Items.DIAMOND, 8));
        ChestBlockEntity full = helper.getBlockEntity(FULL_TARGET);
        for (int i = 0; i < full.getContainerSize(); i++) {
            full.setItem(i, new ItemStack(Items.STONE, 64));
        }
        long[] mark = {0, 0};
        helper.startSequence()
                .thenWaitUntil(() -> {
                    for (RouterBlockEntity router : routers) {
                        helper.assertTrue(NetworkManager.get().contains(router), "roteador não registrado");
                    }
                })
                .thenWaitUntil(() -> assertIdle(helper, empty, waiting, "parado"))
                .thenExecute(() -> mark[0] = flows().skippedForTests())
                // Duas voltas e meia pulando: os mesmos estados, e alguma leitura pulada.
                .thenIdle(50)
                .thenExecute(() -> {
                    helper.assertTrue(flows().skippedForTests() > mark[0], "nenhum nó foi pulado");
                    assertIdle(helper, empty, waiting, "pulando");
                    flows().skipUnchangedForTests(false);
                    mark[1] = flows().report().sampledAt();
                })
                .thenWaitUntil(() -> helper.assertTrue(flows().report().sampledAt() >= mark[1] + 2L * FlowSampler.SAMPLE_TICKS,
                        "sem duas voltas lendo tudo"))
                .thenExecute(() -> {
                    try {
                        assertIdle(helper, empty, waiting, "lendo tudo");
                    } finally {
                        flows().skipUnchangedForTests(true);
                    }
                    IItemHandler handler = helper.getLevel().getCapability(Capabilities.ItemHandler.BLOCK,
                            helper.absolutePos(EMPTY_SOURCE), Direction.UP);
                    helper.assertTrue(handler != null, "sem IItemHandler na origem vazia");
                    handler.insertItem(0, new ItemStack(Items.EMERALD, 5), false);
                })
                .thenWaitUntil(() -> {
                    FlowStats stats = flows().report().get(empty, ResourceType.ITEM);
                    helper.assertTrue(stats != null && stats.produced() == 5 && stats.consumed() == 5,
                            "o que a origem vazia moveu depois não apareceu");
                })
                .thenSucceed();
    }
}
