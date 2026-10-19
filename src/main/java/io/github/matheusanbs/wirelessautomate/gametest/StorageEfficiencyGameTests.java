package io.github.matheusanbs.wirelessautomate.gametest;

import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.block.RouterBlock;
import io.github.matheusanbs.wirelessautomate.block.RouterBlockEntity;
import io.github.matheusanbs.wirelessautomate.block.RouterTier;
import io.github.matheusanbs.wirelessautomate.network.NetworkManager;
import io.github.matheusanbs.wirelessautomate.network.NetworkSavedData;
import io.github.matheusanbs.wirelessautomate.network.PortMode;
import io.github.matheusanbs.wirelessautomate.network.ResourceType;
import io.github.matheusanbs.wirelessautomate.registry.ModBlocks;
import io.github.matheusanbs.wirelessautomate.storage.ItemStorage;
import io.github.matheusanbs.wirelessautomate.storage.StorageBatteryBlockEntity;
import io.github.matheusanbs.wirelessautomate.storage.StorageChestBlockEntity;
import io.github.matheusanbs.wirelessautomate.storage.StorageKind;
import io.github.matheusanbs.wirelessautomate.storage.StorageNeighbors;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ComparatorBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.ComparatorBlockEntity;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * O pacote S da auditoria de eficiência nos armazenamentos do mod: o aviso aos vizinhos juntado num
 * por tick ({@link StorageNeighbors}), o estoque sem componentes em O(1), o NBT das chaves guardado, a
 * pilha do slot e o {@code StorageSavedData} que não cresce sem limite. Cada teste cria a própria rede.
 */
@GameTestHolder(WirelessAutomate.MODID)
@PrefixGameTestTemplate(false)
public final class StorageEfficiencyGameTests {
    private static final BlockPos A = new BlockPos(0, 1, 0);
    private static final BlockPos B = new BlockPos(2, 1, 2);
    /** O comparador a leste de {@link #A}, lendo dele (a frente do comparador aponta para a entrada). */
    private static final BlockPos COMPARATOR = new BlockPos(1, 1, 0);

    private static StorageChestBlockEntity chest(GameTestHelper helper, BlockPos pos, RouterTier tier) {
        helper.setBlock(pos, ModBlocks.STORAGE_CHEST.get().defaultBlockState().setValue(RouterBlock.TIER, tier));
        return helper.getBlockEntity(pos);
    }

    private static void comparator(GameTestHelper helper) {
        helper.setBlock(COMPARATOR.below(), Blocks.STONE);
        helper.setBlock(COMPARATOR, Blocks.COMPARATOR.defaultBlockState().setValue(ComparatorBlock.FACING, Direction.WEST));
    }

    private static int comparatorOutput(GameTestHelper helper) {
        ComparatorBlockEntity comparator = helper.getBlockEntity(COMPARATOR);
        return comparator.getOutputSignal();
    }

    private static RouterBlockEntity router(GameTestHelper helper, BlockPos machine, UUID network, PortMode mode) {
        BlockPos routerPos = machine.above();
        helper.setBlock(routerPos, ModBlocks.ROUTER.get().defaultBlockState()
                .setValue(RouterBlock.FACING, Direction.UP).setValue(RouterBlock.TIER, RouterTier.ELITE));
        RouterBlockEntity router = helper.getBlockEntity(routerPos);
        router.setNetworkId(network);
        router.setNetworkId(ResourceType.ITEM, network);
        router.setMode(ResourceType.ITEM, Direction.UP, mode);
        return router;
    }

    // ------------------------------------------------------------------ S1: um aviso aos vizinhos por tick

    /**
     * Cem inserções no mesmo tick entram uma vez só na fila do aviso, e o comparador segue o conteúdo: sobe
     * depois das inserções e volta a zero quando o Baú esvazia.
     */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void comparatorFollowsTheBatchedNotice(GameTestHelper helper) {
        StorageChestBlockEntity entity = chest(helper, A, RouterTier.BASIC);
        ItemStorage storage = entity.storage();
        comparator(helper);
        helper.startSequence()
                .thenIdle(2)
                .thenExecute(() -> {
                    helper.assertValueEqual(comparatorOutput(helper), 0, "comparador do Baú vazio");
                    for (int i = 0; i < 100; i++) {
                        storage.insert(new ItemStack(Items.COBBLESTONE), 160, false);
                    }
                    helper.assertValueEqual(StorageNeighbors.queued(entity), 1, "na fila uma vez só");
                })
                .thenWaitUntil(() -> {
                    helper.assertValueEqual(StorageNeighbors.queued(entity), 0, "a fila foi avisada");
                    helper.assertTrue(entity.signal() > 0, "sinal do Baú");
                    helper.assertValueEqual(comparatorOutput(helper), entity.signal(), "comparador");
                })
                .thenExecute(() -> storage.extract(new ItemStack(Items.COBBLESTONE), Long.MAX_VALUE, false))
                .thenWaitUntil(() -> helper.assertValueEqual(comparatorOutput(helper), 0, "comparador depois de esvaziar"))
                .thenSucceed();
    }

    /** A Bateria também avisa pelo tick: o comparador segue a energia guardada. */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void batteryComparatorFollowsTheBatchedNotice(GameTestHelper helper) {
        helper.setBlock(A, ModBlocks.STORAGE.get(StorageKind.BATTERY).get().defaultBlockState().setValue(RouterBlock.TIER, RouterTier.BASIC));
        StorageBatteryBlockEntity battery = helper.getBlockEntity(A);
        comparator(helper);
        helper.startSequence()
                .thenIdle(2)
                .thenExecute(() -> {
                    for (int i = 0; i < 50; i++) {
                        battery.store().insert(battery.capacity() / 100, false);
                    }
                    helper.assertValueEqual(StorageNeighbors.queued(battery), 1, "na fila uma vez só");
                })
                .thenWaitUntil(() -> {
                    helper.assertTrue(battery.signal() > 0, "sinal da Bateria");
                    helper.assertValueEqual(comparatorOutput(helper), battery.signal(), "comparador");
                })
                .thenSucceed();
    }

    /**
     * O roteador preso num Baú vazio dorme; o aviso do tick o acorda quando o Baú recebe itens por fora
     * (como um funil), e ele leva tudo para o baú vanilla.
     */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void batchedNoticeWakesTheAttachedRouter(GameTestHelper helper) {
        UUID network = NetworkSavedData.get(helper.getLevel().getServer()).create(UUID.randomUUID(), "teste-aviso").id();
        StorageChestBlockEntity entity = chest(helper, A, RouterTier.BASIC);
        helper.setBlock(B, Blocks.CHEST);
        RouterBlockEntity source = router(helper, A, network, PortMode.EXTRACT);
        RouterBlockEntity target = router(helper, B, network, PortMode.INSERT);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(NetworkManager.get().contains(source)
                        && NetworkManager.get().contains(target), "roteadores não registrados"))
                .thenIdle(60)
                .thenExecute(() -> entity.handler().insertItem(0, new ItemStack(Items.COBBLESTONE, 64), false))
                .thenWaitUntil(() -> {
                    ChestBlockEntity vanilla = helper.getBlockEntity(B);
                    helper.assertValueEqual(vanilla.getItem(0).getCount(), 64, "pedregulho no baú vanilla");
                })
                .thenSucceed();
    }
}
