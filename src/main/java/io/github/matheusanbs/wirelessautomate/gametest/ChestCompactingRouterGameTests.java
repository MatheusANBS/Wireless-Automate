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
import io.github.matheusanbs.wirelessautomate.storage.StorageChestBlockEntity;
import io.github.matheusanbs.wirelessautomate.storage.upgrade.CompactingMode;
import io.github.matheusanbs.wirelessautomate.storage.upgrade.CompactingRules;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.items.IItemHandler;

/**
 * A Compactação em Descompactar num Baú de origem de um roteador: o que a conversão cria no fim do tick chega ao
 * destino logo, com a porta da origem já dormindo e com entrada contínua, como com a forma base inserida direto.
 * Baús Ultimate em y=1 com roteadores Ultimate em cima; cada teste cria a própria rede.
 */
@GameTestHolder(WirelessAutomate.MODID)
@PrefixGameTestTemplate(false)
public final class ChestCompactingRouterGameTests {
    private static final BlockPos A = new BlockPos(0, 1, 0);
    private static final BlockPos B = new BlockPos(2, 1, 2);
    private static final CompactingRules DECOMPRESS = CompactingRules.DEFAULT.withMode(CompactingMode.DECOMPRESS);
    /** Ticks parados antes da inserção: a porta da origem já dorme pelo backoff. */
    private static final int IDLE = 60;
    /** O teto de ticks entre a inserção e a chegada de tudo ao destino. */
    private static final int MAX_DELAY = 5;

    private static UUID newNetwork(GameTestHelper helper, String name) {
        return NetworkSavedData.get(helper.getLevel().getServer()).create(UUID.randomUUID(), name).id();
    }

    private static RouterBlockEntity router(GameTestHelper helper, BlockPos machine, UUID network, PortMode mode) {
        BlockPos routerPos = machine.above();
        helper.setBlock(routerPos, ModBlocks.ROUTER.get().defaultBlockState()
                .setValue(RouterBlock.FACING, Direction.UP).setValue(RouterBlock.TIER, RouterTier.ULTIMATE));
        RouterBlockEntity router = helper.getBlockEntity(routerPos);
        router.setNetworkId(network);
        router.setNetworkId(ResourceType.ITEM, network);
        router.setMode(ResourceType.ITEM, Direction.UP, mode);
        return router;
    }

    /** O ferro do Baú em pepitas: bloco 81, lingote 9, pepita 1. */
    private static long nuggets(StorageChestBlockEntity chest) {
        return ChestCompactingGameTests.count(chest, Items.IRON_BLOCK) * 81
                + ChestCompactingGameTests.count(chest, Items.IRON_INGOT) * 9
                + ChestCompactingGameTests.count(chest, Items.IRON_NUGGET);
    }

    /** A inserção de fora, pelo {@code IItemHandler} da lateral, como a de um minerador vizinho. */
    private static void insertFromSide(GameTestHelper helper, ItemStack stack) {
        IItemHandler handler = helper.getLevel().getCapability(Capabilities.ItemHandler.BLOCK,
                helper.absolutePos(A), Direction.NORTH);
        helper.assertTrue(handler != null, "o Baú tem IItemHandler na lateral");
        ItemStack left = stack;
        for (int slot = 0; slot < handler.getSlots() && !left.isEmpty(); slot++) {
            left = handler.insertItem(slot, left, false);
        }
        helper.assertTrue(left.isEmpty(), "coube tudo no Baú de origem");
    }

    private static void sleepingSourceDelivers(GameTestHelper helper, String label, boolean upgrade, ItemStack stack,
            long expected) {
        UUID network = newNetwork(helper, label);
        StorageChestBlockEntity source = ChestCompactingGameTests.chest(helper, A, RouterTier.ULTIMATE);
        StorageChestBlockEntity target = ChestCompactingGameTests.chest(helper, B, RouterTier.ULTIMATE);
        if (upgrade) {
            ChestCompactingGameTests.putUpgrade(source, DECOMPRESS);
        }
        RouterBlockEntity from = router(helper, A, network, PortMode.EXTRACT);
        RouterBlockEntity to = router(helper, B, network, PortMode.INSERT);
        long[] insertedAt = {0};
        helper.startSequence()
                .thenWaitUntil(() -> {
                    helper.assertTrue(NetworkManager.get().contains(from), "origem registrada");
                    helper.assertTrue(NetworkManager.get().contains(to), "destino registrado");
                })
                .thenIdle(IDLE)
                .thenExecute(() -> {
                    insertedAt[0] = helper.getTick();
                    insertFromSide(helper, stack);
                })
                .thenWaitUntil(() -> {
                    helper.assertValueEqual(source.storage().total(), 0L, "a origem esvaziou");
                    helper.assertValueEqual(nuggets(target), expected, "o ferro chegou ao destino");
                })
                .thenExecute(() -> {
                    long delay = helper.getTick() - insertedAt[0];
                    WirelessAutomate.LOGGER.info("[compactacao-roteador] {}: {} ticks até o destino; destino {} blocos, "
                            + "{} lingotes, {} pepitas", label, delay,
                            ChestCompactingGameTests.count(target, Items.IRON_BLOCK),
                            ChestCompactingGameTests.count(target, Items.IRON_INGOT),
                            ChestCompactingGameTests.count(target, Items.IRON_NUGGET));
                    helper.assertTrue(delay <= MAX_DELAY, label + ": demorou " + delay + " ticks");
                })
                .thenSucceed();
    }

    /** Blocos num Baú em Descompactar, com a porta da origem dormindo: as pepitas chegam em poucos ticks. */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void descompactarComPortaDormindoChegaLogo(GameTestHelper helper) {
        sleepingSourceDelivers(helper, "descompactar", true, new ItemStack(Items.IRON_BLOCK, 10), 810);
    }

    /** O controle: as pepitas inseridas direto, sem upgrade. */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void formaBaseComPortaDormindoChegaLogo(GameTestHelper helper) {
        sleepingSourceDelivers(helper, "forma-base", false, new ItemStack(Items.IRON_NUGGET, 64), 64);
    }

    /**
     * Entrada contínua (um bloco por tick por 40 ticks) num Baú em Descompactar: a origem nunca junta mais que
     * alguns ticks de entrada e esvazia logo depois que a entrada para.
     */
    @GameTest(template = "empty", timeoutTicks = 250)
    public static void descompactarContinuoNaoAcumula(GameTestHelper helper) {
        UUID network = newNetwork(helper, "descompactar-continuo");
        StorageChestBlockEntity source = ChestCompactingGameTests.chest(helper, A, RouterTier.ULTIMATE);
        StorageChestBlockEntity target = ChestCompactingGameTests.chest(helper, B, RouterTier.ULTIMATE);
        ChestCompactingGameTests.putUpgrade(source, DECOMPRESS);
        RouterBlockEntity from = router(helper, A, network, PortMode.EXTRACT);
        RouterBlockEntity to = router(helper, B, network, PortMode.INSERT);
        int blocks = 40;
        int[] left = {-1};
        long[] peak = {0};
        long[] stoppedAt = {0};
        helper.onEachTick(() -> {
            if (left[0] > 0) {
                insertFromSide(helper, new ItemStack(Items.IRON_BLOCK));
                left[0]--;
                if (left[0] == 0) {
                    stoppedAt[0] = helper.getTick();
                }
            }
            if (left[0] >= 0) {
                peak[0] = Math.max(peak[0], nuggets(source));
            }
        });
        helper.startSequence()
                .thenWaitUntil(() -> {
                    helper.assertTrue(NetworkManager.get().contains(from), "origem registrada");
                    helper.assertTrue(NetworkManager.get().contains(to), "destino registrado");
                })
                .thenIdle(IDLE)
                .thenExecute(() -> left[0] = blocks)
                .thenWaitUntil(() -> {
                    helper.assertValueEqual(left[0], 0, "a entrada acabou");
                    helper.assertValueEqual(source.storage().total(), 0L, "a origem esvaziou");
                    helper.assertValueEqual(nuggets(target), blocks * 81L, "o ferro chegou ao destino");
                })
                .thenExecute(() -> {
                    long delay = helper.getTick() - stoppedAt[0];
                    WirelessAutomate.LOGGER.info("[compactacao-roteador] contínuo: pico na origem {} pepitas, {} ticks "
                            + "para esvaziar depois do último bloco", peak[0], delay);
                    helper.assertTrue(peak[0] <= 3 * 81, "a origem juntou " + peak[0] + " pepitas");
                    helper.assertTrue(delay <= MAX_DELAY, "esvaziou em " + delay + " ticks");
                })
                .thenSucceed();
    }

    private ChestCompactingRouterGameTests() {
    }
}
