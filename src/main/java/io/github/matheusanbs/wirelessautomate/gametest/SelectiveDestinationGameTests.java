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
import io.github.matheusanbs.wirelessautomate.storage.StorageChestBlockEntity;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BrewingStandBlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Destino seletivo: uma máquina que recusa parte dos itens (aqui o suporte de poções, cuja face de cima só
 * expõe o slot do ingrediente) não para a origem a cada item recusado. A recusa de um item vale só para ele e
 * só até a máquina mudar, e o Baú do mod procura direto pela chave o que ela já recebeu.
 */
@GameTestHolder(WirelessAutomate.MODID)
@PrefixGameTestTemplate(false)
public final class SelectiveDestinationGameTests {
    private static final BlockPos SOURCE = new BlockPos(0, 1, 0);
    private static final BlockPos STAND = new BlockPos(2, 1, 2);
    /** Slot do ingrediente do suporte de poções, o único que a face de cima expõe. */
    private static final int INGREDIENT = 3;

    private static RouterBlockEntity router(GameTestHelper helper, BlockPos machine, UUID network, PortMode mode) {
        BlockPos routerPos = machine.above();
        helper.setBlock(routerPos, ModBlocks.ROUTER.get().defaultBlockState()
                .setValue(RouterBlock.FACING, Direction.UP).setValue(RouterBlock.TIER, RouterTier.ELITE));
        RouterBlockEntity router = helper.getBlockEntity(routerPos);
        router.setNetworkId(ResourceType.ITEM, network);
        router.setMode(ResourceType.ITEM, Direction.UP, mode);
        return router;
    }

    private static UUID network(GameTestHelper helper, String name) {
        return NetworkSavedData.get(helper.getLevel().getServer()).create(UUID.randomUUID(), name).id();
    }

    private static ItemStack named(Item item, int n) {
        ItemStack stack = new ItemStack(item);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal("seletivo " + n));
        return stack;
    }

    private static BrewingStandBlockEntity stand(GameTestHelper helper, ItemStack ingredient) {
        helper.setBlock(STAND, Blocks.BREWING_STAND);
        BrewingStandBlockEntity stand = helper.getBlockEntity(STAND);
        stand.setItem(INGREDIENT, ingredient);
        return stand;
    }

    private static boolean registered(RouterBlockEntity a, RouterBlockEntity b) {
        return NetworkManager.get().contains(a) && NetworkManager.get().contains(b);
    }

    /**
     * Baú do mod com 2.000 tipos que o suporte recusa (papel com nomes) e a verruga do Nether no fim: a verruga
     * chega em poucas visitas, porque a recusa pelo tipo não gasta tentativa. Depois, com o suporte esvaziado e
     * mais verruga no fim da lista, ela volta na visita seguinte, pela chave, sem varrer os 2.000 tipos de novo.
     */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void bulkSourceFindsTheAcceptedItemAmongThousandsOfTypes(GameTestHelper helper) {
        UUID network = network(helper, "seletivo-baú");
        helper.setBlock(SOURCE, ModBlocks.STORAGE_CHEST.get().defaultBlockState().setValue(RouterBlock.TIER, RouterTier.ULTIMATE));
        StorageChestBlockEntity chest = helper.getBlockEntity(SOURCE);
        ItemStorage storage = chest.storage();
        BrewingStandBlockEntity stand = stand(helper, ItemStack.EMPTY);
        RouterBlockEntity source = router(helper, SOURCE, network, PortMode.EXTRACT);
        RouterBlockEntity target = router(helper, STAND, network, PortMode.INSERT);
        long[] start = new long[1];
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(registered(source, target), "roteadores não registrados"))
                .thenExecute(() -> {
                    for (int i = 0; i < 2000; i++) {
                        storage.insert(named(Items.PAPER, i), 1, false);
                    }
                    storage.insert(new ItemStack(Items.NETHER_WART), 16, false);
                    start[0] = helper.getTick();
                })
                .thenWaitUntil(() -> helper.assertValueEqual(stand.getItem(INGREDIENT).getCount(), 16, "verruga no suporte"))
                .thenExecute(() -> helper.assertTrue(helper.getTick() - start[0] <= 40,
                        "a verruga levou " + (helper.getTick() - start[0]) + " ticks para passar pelos 2.000 tipos"))
                .thenExecute(() -> {
                    stand.setItem(INGREDIENT, ItemStack.EMPTY);
                    stand.setChanged();
                    storage.insert(new ItemStack(Items.NETHER_WART), 8, false);
                    start[0] = helper.getTick();
                })
                .thenWaitUntil(() -> helper.assertValueEqual(stand.getItem(INGREDIENT).getCount(), 8, "verruga de volta"))
                .thenExecute(() -> helper.assertTrue(helper.getTick() - start[0] <= 6,
                        "a verruga aprendida levou " + (helper.getTick() - start[0]) + " ticks"))
                .thenExecute(() -> helper.assertValueEqual(storage.count(named(Items.PAPER, 0)), 1L, "o papel saiu"))
                .thenSucceed();
    }

    /**
     * Baú vanilla com 26 pós de redstone diferentes (ingrediente, mas o slot do suporte está ocupado pela
     * verruga) e a verruga no último slot: a verruga entra na mesma visita, sem o suporte dormir a cada pó.
     */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void slotSourceSkipsItemsWithoutRoom(GameTestHelper helper) {
        UUID network = network(helper, "seletivo-slots");
        helper.setBlock(SOURCE, Blocks.CHEST);
        ChestBlockEntity chest = helper.getBlockEntity(SOURCE);
        for (int i = 0; i < 26; i++) {
            chest.setItem(i, named(Items.REDSTONE, i));
        }
        chest.setItem(26, new ItemStack(Items.NETHER_WART, 5));
        BrewingStandBlockEntity stand = stand(helper, new ItemStack(Items.NETHER_WART, 10));
        RouterBlockEntity source = router(helper, SOURCE, network, PortMode.EXTRACT);
        RouterBlockEntity target = router(helper, STAND, network, PortMode.INSERT);
        long[] start = new long[1];
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(registered(source, target), "roteadores não registrados"))
                .thenExecute(() -> start[0] = helper.getTick())
                .thenWaitUntil(() -> helper.assertValueEqual(stand.getItem(INGREDIENT).getCount(), 15, "verruga no suporte"))
                .thenExecute(() -> helper.assertTrue(helper.getTick() - start[0] <= 20,
                        "a verruga levou " + (helper.getTick() - start[0]) + " ticks para passar pelos 26 pós"))
                .thenExecute(() -> helper.assertValueEqual(chest.getItem(0).getCount(), 1, "o pó saiu"))
                .thenSucceed();
    }

    /**
     * A recusa vale só até a máquina mudar: o pó recusado com o slot ocupado pela verruga entra quando a
     * verruga sai do suporte.
     */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void refusalIsForgottenWhenTheMachineChanges(GameTestHelper helper) {
        UUID network = network(helper, "seletivo-muda");
        helper.setBlock(SOURCE, Blocks.CHEST);
        ChestBlockEntity chest = helper.getBlockEntity(SOURCE);
        chest.setItem(0, new ItemStack(Items.REDSTONE, 8));
        BrewingStandBlockEntity stand = stand(helper, new ItemStack(Items.NETHER_WART, 10));
        RouterBlockEntity source = router(helper, SOURCE, network, PortMode.EXTRACT);
        RouterBlockEntity target = router(helper, STAND, network, PortMode.INSERT);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(registered(source, target), "roteadores não registrados"))
                .thenIdle(20)
                .thenExecute(() -> {
                    helper.assertTrue(stand.getItem(INGREDIENT).is(Items.NETHER_WART), "o pó entrou no slot ocupado");
                    helper.assertValueEqual(chest.getItem(0).getCount(), 8, "o pó saiu do baú");
                    stand.setItem(INGREDIENT, ItemStack.EMPTY);
                    stand.setChanged();
                })
                .thenWaitUntil(() -> helper.assertValueEqual(stand.getItem(INGREDIENT).getCount(), 8, "o pó não entrou"))
                .thenExecute(() -> helper.assertTrue(stand.getItem(INGREDIENT).is(Items.REDSTONE), "outro item no suporte"))
                .thenSucceed();
    }

    private SelectiveDestinationGameTests() {
    }
}
