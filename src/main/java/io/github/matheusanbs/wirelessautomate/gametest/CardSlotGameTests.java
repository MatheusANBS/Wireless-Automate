package io.github.matheusanbs.wirelessautomate.gametest;

import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.block.RouterBlock;
import io.github.matheusanbs.wirelessautomate.block.RouterBlockEntity;
import io.github.matheusanbs.wirelessautomate.filter.Filter;
import io.github.matheusanbs.wirelessautomate.filter.FilterEntry;
import io.github.matheusanbs.wirelessautomate.filter.FilterSet;
import io.github.matheusanbs.wirelessautomate.item.FilterCardItem;
import io.github.matheusanbs.wirelessautomate.menu.RouterMenu;
import io.github.matheusanbs.wirelessautomate.menu.RouterSnapshot;
import io.github.matheusanbs.wirelessautomate.network.NetworkManager;
import io.github.matheusanbs.wirelessautomate.network.NetworkSavedData;
import io.github.matheusanbs.wirelessautomate.network.PortMode;
import io.github.matheusanbs.wirelessautomate.network.RelativeSide;
import io.github.matheusanbs.wirelessautomate.network.ResourceType;
import io.github.matheusanbs.wirelessautomate.packet.ModPayloads;
import io.github.matheusanbs.wirelessautomate.packet.SelectFacePayload;
import io.github.matheusanbs.wirelessautomate.registry.ModBlocks;
import io.github.matheusanbs.wirelessautomate.registry.ModItems;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.RegistryAccess;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.jetbrains.annotations.Nullable;

/**
 * Slots de Cartão de Filtro por face e por tipo: a regra do conjunto (embutido + cartões), o uso
 * no motor, a validação dos slots do menu, a persistência, a seleção pela tela e o drop ao quebrar.
 * Mesma montagem dos outros testes: baús em y=1 com o roteador em cima (facing=UP), face UP.
 */
@GameTestHolder(WirelessAutomate.MODID)
@PrefixGameTestTemplate(false)
public final class CardSlotGameTests {
    private static final BlockPos A = new BlockPos(0, 1, 0);
    private static final BlockPos B = new BlockPos(2, 1, 2);
    private static final int CONTAINER_ID = 43;
    /** Índice do primeiro slot da mochila no {@link RouterMenu} (depois dos de cartão). */
    private static final int INVENTORY_START = RouterMenu.CARD_SLOT_COUNT;

    private static RouterBlockEntity chestWithRouter(GameTestHelper helper, BlockPos machine, @Nullable UUID network) {
        helper.setBlock(machine, Blocks.CHEST);
        BlockPos pos = machine.above();
        helper.setBlock(pos, ModBlocks.ROUTER.get().defaultBlockState().setValue(RouterBlock.FACING, Direction.UP));
        RouterBlockEntity router = helper.getBlockEntity(pos);
        router.setNetworkId(network);
        return router;
    }

    private static RelativeSide side(RouterBlockEntity router, Direction face) {
        return RelativeSide.fromAbsolute(router.facing(), face);
    }

    private static ItemStack card(ResourceType type, Filter filter) {
        ItemStack card = new ItemStack(ModItems.FILTER_CARD.get());
        FilterCardItem.setContents(card, new FilterCardItem.Contents(type, filter));
        return card;
    }

    private static Filter whitelist(Item... items) {
        return new Filter(Filter.ListMode.WHITELIST, false,
                Arrays.stream(items).map(i -> (FilterEntry) new FilterEntry.ItemEntry(new ItemStack(i), 0))
                        .toList());
    }

    private static int count(GameTestHelper helper, BlockPos pos, Item item) {
        ChestBlockEntity chest = helper.getBlockEntity(pos);
        int total = 0;
        for (int i = 0; i < chest.getContainerSize(); i++) {
            if (chest.getItem(i).is(item)) {
                total += chest.getItem(i).getCount();
            }
        }
        return total;
    }

    private static void assertCount(GameTestHelper helper, BlockPos pos, Item item, int expected) {
        helper.assertValueEqual(count(helper, pos, item), expected, item + " em " + pos.toShortString());
    }

    @SuppressWarnings("removal")
    private static ServerPlayer playerNear(GameTestHelper helper, RouterBlockEntity router) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.moveTo(Vec3.atCenterOf(router.getBlockPos().above()));
        return player;
    }

    private static RouterMenu openMenu(ServerPlayer player, RouterBlockEntity router) {
        RouterMenu menu = new RouterMenu(CONTAINER_ID, player.getInventory(), router,
                RouterSnapshot.capture(router, player));
        player.containerMenu = menu;
        return menu;
    }

    /** Índice no menu do slot do inventário com um cartão do tipo. */
    private static int inventorySlotOf(RouterMenu menu, ResourceType type) {
        for (int i = INVENTORY_START; i < menu.slots.size(); i++) {
            ItemStack stack = menu.getSlot(i).getItem();
            if (FilterCardItem.isCard(stack) && FilterCardItem.contents(stack).type() == type) {
                return i;
            }
        }
        throw new IllegalStateException("Sem cartão de " + type + " no inventário");
    }

    private static void close(ServerPlayer player) {
        player.containerMenu = player.inventoryMenu;
    }

    @GameTest(template = "empty")
    public static void filterSetRules(GameTestHelper helper) {
        ItemStack diamond = new ItemStack(Items.DIAMOND);
        ItemStack stone = new ItemStack(Items.STONE);
        ItemStack gold = new ItemStack(Items.GOLD_INGOT);
        Filter onlyDiamond = whitelist(Items.DIAMOND);
        Filter onlyStone = whitelist(Items.STONE);
        Filter notGold = new Filter(Filter.ListMode.BLACKLIST, false,
                List.of(new FilterEntry.ItemEntry(new ItemStack(Items.GOLD_INGOT), 0)));

        FilterSet none = FilterSet.of(Filter.EMPTY, List.of(Filter.EMPTY, Filter.EMPTY));
        helper.assertTrue(none.isEmpty() && none.testItem(gold), "conjunto sem entradas não passou tudo");
        helper.assertTrue(Filter.EMPTY.asSet() == FilterSet.EMPTY, "embutido vazio sem cartões");
        helper.assertTrue(onlyDiamond.asSet() == onlyDiamond.asSet(), "conjunto de um filtro não ficou em cache");

        // Embutido vazio não conta: só o cartão decide.
        FilterSet cardOnly = FilterSet.of(Filter.EMPTY, List.of(onlyDiamond));
        helper.assertTrue(cardOnly.testItem(diamond) && !cardOnly.testItem(stone), "embutido vazio deixou passar");

        // Passa se algum aceitar.
        FilterSet union = FilterSet.of(onlyStone, List.of(onlyDiamond));
        helper.assertTrue(union.testItem(diamond) && union.testItem(stone) && !union.testItem(gold), "união");
        FilterSet withBlack = FilterSet.of(onlyStone, List.of(notGold));
        helper.assertTrue(withBlack.testItem(diamond) && !withBlack.testItem(gold), "lista negra no conjunto");

        // Estoque: o da primeira regra que casa, embutido primeiro.
        Filter stocked = new Filter(Filter.ListMode.WHITELIST, false,
                List.of(new FilterEntry.ItemEntry(new ItemStack(Items.DIAMOND), 64)));
        Filter stockedLess = new Filter(Filter.ListMode.WHITELIST, false,
                List.of(new FilterEntry.ItemEntry(new ItemStack(Items.DIAMOND), 8)));
        helper.assertValueEqual(FilterSet.of(stocked, List.of(stockedLess)).itemStock(diamond), 64L, "embutido primeiro");
        helper.assertValueEqual(FilterSet.of(onlyStone, List.of(stockedLess)).itemStock(diamond), 8L,
                "estoque do cartão");
        helper.assertValueEqual(FilterSet.of(onlyDiamond, List.of(stockedLess)).itemStock(diamond), 0L,
                "regra sem estoque vem antes");
        helper.assertValueEqual(FilterSet.of(onlyDiamond, List.of(stockedLess)).itemStock(stone), 0L,
                "estoque de quem não casa");

        Filter water = new Filter(Filter.ListMode.WHITELIST, false,
                List.of(new FilterEntry.FluidEntry(new FluidStack(Fluids.WATER, 1), 500)));
        FilterSet fluids = FilterSet.of(Filter.EMPTY, List.of(water));
        helper.assertTrue(fluids.testFluid(new FluidStack(Fluids.WATER, 1000))
                && !fluids.testFluid(new FluidStack(Fluids.LAVA, 1000)), "cartão de fluido");
        helper.assertValueEqual(fluids.fluidStock(new FluidStack(Fluids.WATER, 1000)), 500L, "estoque de fluido");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void cardOnSourceLetsItsItemsOut(GameTestHelper helper) {
        UUID network = NetworkSavedData.get(helper.getLevel().getServer()).create(UUID.randomUUID(), "cartao-origem").id();
        RouterBlockEntity source = chestWithRouter(helper, A, network);
        RouterBlockEntity target = chestWithRouter(helper, B, network);
        source.setMode(ResourceType.ITEM, Direction.UP, PortMode.EXTRACT);
        source.setFilter(ResourceType.ITEM, Direction.UP, whitelist(Items.COBBLESTONE));
        target.setMode(ResourceType.ITEM, Direction.UP, PortMode.INSERT);
        ChestBlockEntity chest = helper.getBlockEntity(A);
        chest.setItem(0, new ItemStack(Items.COBBLESTONE, 3));
        chest.setItem(1, new ItemStack(Items.DIAMOND, 10));
        chest.setItem(2, new ItemStack(Items.GOLD_INGOT, 4));

        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(NetworkManager.get().contains(source)
                        && NetworkManager.get().contains(target), "roteadores não registrados"))
                // Só o filtro embutido: o pedregulho sai, o diamante fica.
                .thenWaitUntil(() -> assertCount(helper, B, Items.COBBLESTONE, 3))
                .thenIdle(10)
                .thenExecute(() -> {
                    assertCount(helper, A, Items.DIAMOND, 10);
                    // Cartão de diamante: passa se o embutido ou o cartão aceitar.
                    source.setCard(ResourceType.ITEM, side(source, Direction.UP), 1,
                            card(ResourceType.ITEM, whitelist(Items.DIAMOND)));
                    helper.assertValueEqual(source.cardCount(ResourceType.ITEM, Direction.UP), 1, "cartões da face");
                })
                .thenWaitUntil(() -> assertCount(helper, B, Items.DIAMOND, 10))
                .thenIdle(10)
                .thenExecute(() -> {
                    assertCount(helper, A, Items.GOLD_INGOT, 4);
                    assertCount(helper, B, Items.GOLD_INGOT, 0);
                })
                .thenSucceed();
    }

    @GameTest(template = "empty")
    public static void slotsTakeOnlyCardsOfTheTab(GameTestHelper helper) {
        RouterBlockEntity router = chestWithRouter(helper, A, null);
        ServerPlayer player = playerNear(helper, router);
        ItemStack itemCard = card(ResourceType.ITEM, whitelist(Items.DIAMOND));
        ItemStack fluidCard = card(ResourceType.FLUID, Filter.EMPTY);
        helper.assertTrue(RouterBlockEntity.acceptsCard(ResourceType.ITEM, itemCard), "cartão de itens recusado");
        helper.assertFalse(RouterBlockEntity.acceptsCard(ResourceType.ITEM, fluidCard), "cartão de fluidos em itens");
        helper.assertFalse(RouterBlockEntity.acceptsCard(ResourceType.ITEM, new ItemStack(Items.DIAMOND)), "diamante");
        helper.assertFalse(RouterBlockEntity.acceptsCard(ResourceType.ENERGY, itemCard), "cartão em energia");

        player.getInventory().setItem(0, fluidCard.copyWithCount(2));
        player.getInventory().setItem(1, itemCard.copyWithCount(3));
        RouterMenu menu = openMenu(player, router);
        try {
            helper.assertFalse(menu.getSlot(0).mayPlace(fluidCard), "slot de itens aceitou cartão de fluidos");
            helper.assertTrue(menu.getSlot(0).mayPlace(itemCard), "slot de itens recusou cartão de itens");
            menu.clicked(inventorySlotOf(menu, ResourceType.FLUID), 0, ClickType.QUICK_MOVE, player);
            helper.assertTrue(router.card(ResourceType.ITEM, side(router, Direction.UP), 0).isEmpty()
                    && router.card(ResourceType.ITEM, side(router, Direction.UP), 1).isEmpty(),
                    "Shift + clique pôs cartão de fluidos no slot de itens");

            // Shift + clique: um cartão por clique, no primeiro slot livre, e só um por slot.
            menu.clicked(inventorySlotOf(menu, ResourceType.ITEM), 0, ClickType.QUICK_MOVE, player);
            ItemStack placed = router.card(ResourceType.ITEM, side(router, Direction.UP), 0);
            helper.assertTrue(placed.is(ModItems.FILTER_CARD.get()) && placed.getCount() == 1, "cartão no slot 0");
            helper.assertTrue(router.card(ResourceType.ITEM, side(router, Direction.UP), 1).isEmpty(),
                    "mais de um cartão por clique");
            helper.assertValueEqual(menu.getSlot(inventorySlotOf(menu, ResourceType.ITEM)).getItem().getCount(), 2,
                    "cartões no inventário");
            helper.assertValueEqual(FilterCardItem.contents(placed).filter(), whitelist(Items.DIAMOND), "filtro do cartão");

            // Na aba de fluidos o cartão de fluidos entra.
            helper.assertTrue(ModPayloads.handleSelectFace(player,
                    new SelectFacePayload(menu.containerId, ResourceType.FLUID, Direction.UP)), "seleção recusada");
            helper.assertTrue(menu.getSlot(0).mayPlace(fluidCard), "slot de fluidos recusou cartão de fluidos");
            helper.assertFalse(menu.getSlot(0).mayPlace(itemCard), "slot de fluidos aceitou cartão de itens");
            menu.clicked(inventorySlotOf(menu, ResourceType.FLUID), 0, ClickType.QUICK_MOVE, player);
            helper.assertTrue(router.card(ResourceType.FLUID, side(router, Direction.UP), 0).is(ModItems.FILTER_CARD.get()),
                    "cartão de fluidos fora do slot");

            // Energia: slots inativos e nada entra.
            ModPayloads.handleSelectFace(player, new SelectFacePayload(menu.containerId, ResourceType.ENERGY, Direction.UP));
            helper.assertFalse(menu.getSlot(0).isActive(), "slot ativo em energia");
            helper.assertTrue(menu.getSlot(0).getItem().isEmpty(), "slot de energia mostrou cartão");

            // Shift + clique num slot de cartão devolve ao inventário.
            ModPayloads.handleSelectFace(player, new SelectFacePayload(menu.containerId, ResourceType.ITEM, Direction.UP));
            menu.clicked(0, 0, ClickType.QUICK_MOVE, player);
            helper.assertTrue(router.card(ResourceType.ITEM, side(router, Direction.UP), 0).isEmpty(), "cartão não saiu");
            helper.assertValueEqual(menu.getSlot(inventorySlotOf(menu, ResourceType.ITEM)).getItem().getCount(), 3,
                    "cartão não voltou ao inventário");
        } finally {
            close(player);
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void cardsSurviveSaveAndLoad(GameTestHelper helper) {
        RegistryAccess registries = helper.getLevel().registryAccess();
        RouterBlockEntity router = chestWithRouter(helper, A, null);
        ItemStack itemCard = card(ResourceType.ITEM, whitelist(Items.DIAMOND, Items.EMERALD));
        ItemStack fluidCard = card(ResourceType.FLUID, new Filter(Filter.ListMode.BLACKLIST, false,
                List.of(new FilterEntry.FluidEntry(new FluidStack(Fluids.LAVA, 1), 0))));
        router.setCard(ResourceType.ITEM, RelativeSide.BACK, 1, itemCard.copy());
        router.setCard(ResourceType.FLUID, RelativeSide.LEFT, 0, fluidCard.copy());

        CompoundTag saved = router.saveWithoutMetadata(registries);
        RouterBlockEntity copy = new RouterBlockEntity(router.getBlockPos(), router.getBlockState());
        copy.loadWithComponents(saved, registries);
        helper.assertTrue(ItemStack.matches(copy.card(ResourceType.ITEM, RelativeSide.BACK, 1), itemCard),
                "cartão de itens");
        helper.assertTrue(ItemStack.matches(copy.card(ResourceType.FLUID, RelativeSide.LEFT, 0), fluidCard),
                "cartão de fluidos");
        helper.assertTrue(copy.card(ResourceType.ITEM, RelativeSide.BACK, 0).isEmpty(), "slot vazio ganhou cartão");
        helper.assertTrue(copy.card(ResourceType.ENERGY, RelativeSide.BACK, 0).isEmpty(), "energia com cartão");

        // Uma carga sem cartões esvazia os slots.
        router.removeAllCards();
        copy.loadWithComponents(router.saveWithoutMetadata(registries), registries);
        helper.assertTrue(copy.card(ResourceType.ITEM, RelativeSide.BACK, 1).isEmpty(), "carga não esvaziou");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void breakingDropsCards(GameTestHelper helper) {
        RouterBlockEntity router = chestWithRouter(helper, A, null);
        BlockPos routerPos = A.above();
        router.setCard(ResourceType.ITEM, RelativeSide.FRONT, 0, card(ResourceType.ITEM, whitelist(Items.DIAMOND)));
        router.setCard(ResourceType.FLUID, RelativeSide.TOP, 1, card(ResourceType.FLUID, Filter.EMPTY));
        helper.destroyBlock(routerPos);
        helper.assertBlockNotPresent(ModBlocks.ROUTER.get(), routerPos);
        helper.assertItemEntityCountIs(ModItems.FILTER_CARD.get(), routerPos, 2.0, 2);
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void selectionSwapsWhatSlotsShow(GameTestHelper helper) {
        RouterBlockEntity router = chestWithRouter(helper, A, null);
        ServerPlayer player = playerNear(helper, router);
        ItemStack up = card(ResourceType.ITEM, whitelist(Items.DIAMOND));
        ItemStack north = card(ResourceType.FLUID, Filter.EMPTY);
        router.setCard(ResourceType.ITEM, side(router, Direction.UP), 0, up);
        router.setCard(ResourceType.FLUID, side(router, Direction.NORTH), 1, north);
        RouterMenu menu = openMenu(player, router);
        try {
            // Começa na aba de itens e na face onde o roteador está preso, como a tela.
            helper.assertValueEqual(menu.selectedFace(), Direction.UP, "face inicial");
            helper.assertTrue(menu.getSlot(0).getItem() == up && menu.getSlot(1).getItem().isEmpty(),
                    "slots da face inicial");

            helper.assertTrue(ModPayloads.handleSelectFace(player,
                    new SelectFacePayload(menu.containerId, ResourceType.FLUID, Direction.NORTH)), "seleção recusada");
            helper.assertTrue(menu.getSlot(0).getItem().isEmpty() && menu.getSlot(1).getItem() == north,
                    "slots não acompanharam a seleção");

            // Pôr pelo slot grava na face e no tipo selecionados, e conta para o motor.
            ModPayloads.handleSelectFace(player, new SelectFacePayload(menu.containerId, ResourceType.ITEM,
                    Direction.DOWN));
            int before = router.changeVersion();
            ItemStack down = card(ResourceType.ITEM, whitelist(Items.STONE));
            menu.getSlot(1).set(down);
            helper.assertTrue(router.card(ResourceType.ITEM, side(router, Direction.DOWN), 1) == down,
                    "slot não gravou no roteador");
            helper.assertTrue(router.changeVersion() != before, "mudança de cartão não avisou");
            helper.assertTrue(router.filterSet(ResourceType.ITEM, Direction.DOWN).testItem(new ItemStack(Items.STONE))
                    && !router.filterSet(ResourceType.ITEM, Direction.DOWN).testItem(new ItemStack(Items.DIAMOND)),
                    "conjunto da face sem o cartão");

            // Pacote com outro containerId (ou sem a tela) é recusado.
            helper.assertFalse(ModPayloads.handleSelectFace(player,
                    new SelectFacePayload(menu.containerId + 1, ResourceType.ITEM, Direction.UP)), "containerId errado");
            helper.assertValueEqual(menu.selectedFace(), Direction.DOWN, "seleção mudou com pacote recusado");
        } finally {
            close(player);
        }
        helper.assertFalse(ModPayloads.handleSelectFace(player,
                new SelectFacePayload(CONTAINER_ID, ResourceType.ITEM, Direction.UP)), "sem a tela aberta");
        helper.succeed();
    }
}
