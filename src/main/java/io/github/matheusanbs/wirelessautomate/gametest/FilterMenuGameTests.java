package io.github.matheusanbs.wirelessautomate.gametest;

import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.block.RouterBlock;
import io.github.matheusanbs.wirelessautomate.block.RouterBlockEntity;
import io.github.matheusanbs.wirelessautomate.filter.Filter;
import io.github.matheusanbs.wirelessautomate.filter.FilterEntry;
import io.github.matheusanbs.wirelessautomate.item.FilterCardItem;
import io.github.matheusanbs.wirelessautomate.menu.CardFilterTarget;
import io.github.matheusanbs.wirelessautomate.menu.FilterMenu;
import io.github.matheusanbs.wirelessautomate.menu.FilterView;
import io.github.matheusanbs.wirelessautomate.menu.RouterFaceFilterTarget;
import io.github.matheusanbs.wirelessautomate.menu.RouterMenu;
import io.github.matheusanbs.wirelessautomate.menu.RouterSnapshot;
import io.github.matheusanbs.wirelessautomate.network.ResourceType;
import io.github.matheusanbs.wirelessautomate.packet.EditFilterPayload;
import io.github.matheusanbs.wirelessautomate.packet.EditFilterPayload.Op;
import io.github.matheusanbs.wirelessautomate.packet.ModPayloads;
import io.github.matheusanbs.wirelessautomate.packet.OpenFilterPayload;
import io.github.matheusanbs.wirelessautomate.registry.ModBlocks;
import io.github.matheusanbs.wirelessautomate.registry.ModDataComponents;
import io.github.matheusanbs.wirelessautomate.registry.ModItems;
import com.mojang.authlib.GameProfile;
import com.mojang.serialization.DynamicOps;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import io.netty.buffer.Unpooled;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import io.github.matheusanbs.wirelessautomate.net.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import org.jetbrains.annotations.Nullable;

/**
 * Lado do servidor da tela de filtro e o Cartão de Filtro: abrir pelo roteador, as edições, o
 * Shift + clique no inventário, importar e exportar o cartão e o menu do próprio cartão. Os
 * handlers são chamados direto, com o menu no {@code containerMenu} do jogador falso, que volta ao
 * inventário no fim de cada teste. O jogador falso é o do {@link GameTestCompat#makeMockServerPlayerInLevel};
 * os dados de abertura vêm do pacote do Forge ({@link GameTestCompat#lastOpenData}).
 */
@GameTestHolder(WirelessAutomate.MODID)
@PrefixGameTestTemplate(false)
public final class FilterMenuGameTests {
    private static final BlockPos CHEST = new BlockPos(1, 1, 1);
    private static final int CONTAINER_ID = 43;
    private static final ResourceLocation INGOTS = new ResourceLocation("c", "ingots");

    private static RouterBlockEntity chestWithRouter(GameTestHelper helper) {
        helper.setBlock(CHEST, Blocks.CHEST);
        BlockPos pos = CHEST.above();
        helper.setBlock(pos, ModBlocks.ROUTER.get().defaultBlockState().setValue(RouterBlock.FACING, Direction.UP));
        return GameTestCompat.getBlockEntity(helper, pos);
    }

    /**
     * Jogador falso do {@link GameTestCompat#makeMockServerPlayerInLevel}, perto do roteador. Porte 1.20.1: no
     * {@code main} era um {@code MockPlayer} que sobrescrevia o {@code openMenu} do NeoForge para guardar os dados
     * de abertura; no Forge a abertura é estática ({@code NetworkHooks.openScreen}), então a tela abre pelo caminho
     * de verdade e os dados de abertura são lidos do pacote na fila de saída ({@link GameTestCompat#lastOpenData}).
     */
    private static ServerPlayer playerNear(GameTestHelper helper, RouterBlockEntity router) {
        ServerPlayer player = GameTestCompat.makeMockServerPlayerInLevel(helper);
        player.moveTo(Vec3.atCenterOf(router.getBlockPos().above()));
        return player;
    }

    private static FilterMenu openFace(ServerPlayer player, RouterBlockEntity router, ResourceType type) {
        RouterFaceFilterTarget target = new RouterFaceFilterTarget(router, type, Direction.UP);
        FilterMenu menu = new FilterMenu(CONTAINER_ID, player.getInventory(), target, target.view(player));
        player.containerMenu = menu;
        return menu;
    }

    private static boolean edit(ServerPlayer player, FilterMenu menu, Op op, int index, long value, String text) {
        return ModPayloads.handleEditFilter(player, new EditFilterPayload(menu.containerId, op, index, value, text));
    }

    private static boolean edit(ServerPlayer player, FilterMenu menu, Op op) {
        return ModPayloads.handleEditFilter(player, EditFilterPayload.of(menu.containerId, op));
    }

    /** Índice no menu do slot {@code inventorySlot} do inventário do jogador. */
    private static int menuSlot(FilterMenu menu, int inventorySlot) {
        for (Slot slot : menu.slots) {
            if (slot.container instanceof Inventory && slot.getContainerSlot() == inventorySlot) {
                return slot.index;
            }
        }
        throw new IllegalStateException("Slot " + inventorySlot + " fora do menu");
    }

    private static void close(ServerPlayer player) {
        player.containerMenu = player.inventoryMenu;
    }

    @GameTest(template = "empty")
    public static void openFilterFromRouter(GameTestHelper helper) {
        RouterBlockEntity router = chestWithRouter(helper);
        ServerPlayer player = playerNear(helper, router);
        try {
            RouterMenu routerMenu = new RouterMenu(CONTAINER_ID, player.getInventory(), router,
                    RouterSnapshot.capture(router, player));
            player.containerMenu = routerMenu;
            helper.assertFalse(ModPayloads.handleOpenFilter(player,
                    new OpenFilterPayload(CONTAINER_ID, ResourceType.ENERGY, Direction.UP)), "abriu filtro de energia");
            helper.assertFalse(ModPayloads.handleOpenFilter(player,
                    new OpenFilterPayload(CONTAINER_ID, ResourceType.CHEMICAL, Direction.UP)), "abriu filtro de químicos");
            helper.assertFalse(ModPayloads.handleOpenFilter(player,
                    new OpenFilterPayload(CONTAINER_ID + 1, ResourceType.ITEM, Direction.UP)), "aceitou outro containerId");
            player.moveTo(Vec3.atCenterOf(router.getBlockPos()).add(0, 20, 0));
            helper.assertFalse(ModPayloads.handleOpenFilter(player,
                    new OpenFilterPayload(CONTAINER_ID, ResourceType.ITEM, Direction.UP)), "abriu de longe");
            player.moveTo(Vec3.atCenterOf(router.getBlockPos().above()));
            helper.assertTrue(player.containerMenu == routerMenu, "trocou de tela numa recusa");

            helper.assertTrue(ModPayloads.handleOpenFilter(player,
                    new OpenFilterPayload(CONTAINER_ID, ResourceType.FLUID, Direction.NORTH)), "recusou o filtro de fluidos");
            if (!(player.containerMenu instanceof FilterMenu menu)
                    || !(menu.target() instanceof RouterFaceFilterTarget target)) {
                throw new GameTestAssertException("tela de filtro não abriu");
            }
            GameTestCompat.assertValueEqual(helper, target.router(), router, "roteador");
            GameTestCompat.assertValueEqual(helper, target.type(), ResourceType.FLUID, "tipo");
            GameTestCompat.assertValueEqual(helper, target.face(), Direction.NORTH, "face");
            FilterView view = menu.view();
            GameTestCompat.assertValueEqual(helper, view.router().orElse(null), router.getBlockPos(), "posição na visão");
            helper.assertFalse(view.isCard(), "visão de face como cartão");
            // O cliente monta o menu com a visão do buffer de abertura.
            RegistryFriendlyByteBuf openData = GameTestCompat.lastOpenData(player);
            helper.assertTrue(openData != null, "sem pacote de abertura");
            FilterMenu client = new FilterMenu(menu.containerId, player.getInventory(), openData);
            GameTestCompat.assertValueEqual(helper, client.view(), view, "visão do buffer de abertura");

            // BACK volta para a tela do roteador.
            helper.assertTrue(edit(player, menu, Op.BACK), "recusou voltar");
            helper.assertTrue(player.containerMenu instanceof RouterMenu back && back.router() == router,
                    "não voltou para o roteador");
        } finally {
            close(player);
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void editsChangeTheFaceFilter(GameTestHelper helper) {
        RouterBlockEntity router = chestWithRouter(helper);
        ServerPlayer player = playerNear(helper, router);
        FilterMenu menu = openFace(player, router, ResourceType.ITEM);
        try {
            helper.assertTrue(menu.pollView() == null, "visão sem mudança");
            helper.assertTrue(edit(player, menu, Op.ADD_TAG, 0, 0, " #c:ingots "), "recusou a tag");
            helper.assertTrue(edit(player, menu, Op.ADD_TAG, 0, 0, "C:Ingots"), "recusou a tag repetida");
            helper.assertFalse(edit(player, menu, Op.ADD_TAG, 0, 0, "não é tag!"), "aceitou tag inválida");
            helper.assertFalse(edit(player, menu, Op.ADD_TAG, 0, 0, "#"), "aceitou tag vazia");
            helper.assertTrue(edit(player, menu, Op.ADD_MOD, 0, 0, "@Mekanism"), "recusou o mod");
            helper.assertFalse(edit(player, menu, Op.ADD_MOD, 0, 0, "dois mods"), "aceitou mod inválido");
            helper.assertFalse(edit(player, menu, Op.ADD_MOD, 0, 0, "c:ingots"), "aceitou id como mod");
            Filter filter = router.face(ResourceType.ITEM, Direction.UP).filter();
            GameTestCompat.assertValueEqual(helper, filter.entries(), List.<FilterEntry>of(new FilterEntry.TagEntry(INGOTS, 0),
                    new FilterEntry.ModEntry("mekanism", 0)), "entradas");

            FilterView view = menu.pollView();
            helper.assertTrue(view != null && view.filter().equals(filter), "visão não acompanhou o filtro");
            helper.assertTrue(menu.pollView() == null, "visão repetida");

            helper.assertTrue(edit(player, menu, Op.SET_STOCK, 1, 64, ""), "recusou o estoque");
            helper.assertFalse(edit(player, menu, Op.SET_STOCK, 1, -1, ""), "aceitou estoque negativo");
            helper.assertFalse(edit(player, menu, Op.SET_STOCK, 1, Integer.MAX_VALUE + 1L, ""), "aceitou estoque enorme");
            helper.assertFalse(edit(player, menu, Op.SET_STOCK, 5, 1, ""), "aceitou índice fora");
            GameTestCompat.assertValueEqual(helper, router.face(ResourceType.ITEM, Direction.UP).filter().entries().get(1).stock(), 64L,
                    "estoque");

            helper.assertTrue(edit(player, menu, Op.SET_LIST_MODE, 0, 1, ""), "recusou lista negra");
            helper.assertFalse(edit(player, menu, Op.SET_LIST_MODE, 0, 2, ""), "aceitou modo inválido");
            helper.assertTrue(edit(player, menu, Op.SET_COMPONENTS, 0, 1, ""), "recusou componentes");
            filter = router.face(ResourceType.ITEM, Direction.UP).filter();
            GameTestCompat.assertValueEqual(helper, filter.listMode(), Filter.ListMode.BLACKLIST, "modo");
            helper.assertTrue(filter.matchComponents(), "componentes");

            helper.assertTrue(edit(player, menu, Op.REMOVE, 0, 0, ""), "recusou remover");
            helper.assertFalse(edit(player, menu, Op.REMOVE, 3, 0, ""), "aceitou remover fora");
            GameTestCompat.assertValueEqual(helper, router.face(ResourceType.ITEM, Direction.UP).filter().entries(),
                    List.<FilterEntry>of(new FilterEntry.ModEntry("mekanism", 64)), "remoção");

            // Recusas por tela: outro id e jogador longe.
            helper.assertFalse(ModPayloads.handleEditFilter(player,
                    EditFilterPayload.of(CONTAINER_ID + 1, Op.CLEAR)), "aceitou outro containerId");
            player.moveTo(Vec3.atCenterOf(router.getBlockPos()).add(0, 20, 0));
            helper.assertFalse(edit(player, menu, Op.CLEAR), "aceitou de longe");
            player.moveTo(Vec3.atCenterOf(router.getBlockPos().above()));

            helper.assertTrue(edit(player, menu, Op.CLEAR), "recusou limpar");
            filter = router.face(ResourceType.ITEM, Direction.UP).filter();
            helper.assertTrue(filter.isEmpty(), "não limpou");
            GameTestCompat.assertValueEqual(helper, filter.listMode(), Filter.ListMode.BLACKLIST, "limpar mudou o modo");

            // Componentes só valem para itens.
            FilterMenu fluids = openFace(player, router, ResourceType.FLUID);
            helper.assertFalse(edit(player, fluids, Op.SET_COMPONENTS, 0, 1, ""), "componentes em fluidos");
        } finally {
            close(player);
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void shiftClickAddsFromInventory(GameTestHelper helper) {
        RouterBlockEntity router = chestWithRouter(helper);
        ServerPlayer player = playerNear(helper, router);
        player.getInventory().setItem(0, new ItemStack(Items.DIAMOND, 5));
        player.getInventory().setItem(9, new ItemStack(Items.WATER_BUCKET));
        try {
            FilterMenu items = openFace(player, router, ResourceType.ITEM);
            int diamond = menuSlot(items, 0);
            items.clicked(diamond, 0, ClickType.QUICK_MOVE, player);
            items.clicked(diamond, 0, ClickType.QUICK_MOVE, player);
            GameTestCompat.assertValueEqual(helper, router.face(ResourceType.ITEM, Direction.UP).filter().entries(),
                    List.<FilterEntry>of(new FilterEntry.ItemEntry(new ItemStack(Items.DIAMOND), 0)),
                    "diamante no filtro (sem duplicar)");
            GameTestCompat.assertValueEqual(helper, player.getInventory().getItem(0).getCount(), 5, "o diamante saiu do lugar");
            items.clicked(menuSlot(items, 1), 0, ClickType.QUICK_MOVE, player);
            GameTestCompat.assertValueEqual(helper, router.face(ResourceType.ITEM, Direction.UP).filter().entries().size(), 1,
                    "slot vazio acrescentou");

            FilterMenu fluids = openFace(player, router, ResourceType.FLUID);
            fluids.clicked(menuSlot(fluids, 9), 0, ClickType.QUICK_MOVE, player);
            fluids.clicked(menuSlot(fluids, 0), 0, ClickType.QUICK_MOVE, player);
            GameTestCompat.assertValueEqual(helper, router.face(ResourceType.FLUID, Direction.UP).filter().entries(),
                    List.<FilterEntry>of(new FilterEntry.FluidEntry(new FluidStack(Fluids.WATER, 1000), 0)),
                    "água no filtro (e o diamante não)");
            helper.assertTrue(player.getInventory().getItem(9).is(Items.WATER_BUCKET), "o balde saiu do lugar");
        } finally {
            close(player);
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void importAndExportCard(GameTestHelper helper) {
        RouterBlockEntity router = chestWithRouter(helper);
        ServerPlayer player = playerNear(helper, router);
        FilterMenu menu = openFace(player, router, ResourceType.ITEM);
        try {
            edit(player, menu, Op.ADD_TAG, 0, 0, "c:ingots");
            Filter filter = router.face(ResourceType.ITEM, Direction.UP).filter();
            menu.pollView();
            helper.assertFalse(edit(player, menu, Op.EXPORT_CARD), "exportou sem cartão");

            ItemStack card = new ItemStack(ModItems.FILTER_CARD.get(), 3);
            player.setItemInHand(InteractionHand.MAIN_HAND, card);
            FilterView withCard = menu.pollView();
            helper.assertTrue(withCard != null && withCard.hasCard(), "visão não viu o cartão");

            helper.assertTrue(edit(player, menu, Op.EXPORT_CARD), "recusou exportar");
            GameTestCompat.assertValueEqual(helper, FilterCardItem.contents(card), new FilterCardItem.Contents(ResourceType.ITEM, filter),
                    "cartão exportado");
            GameTestCompat.assertValueEqual(helper, card.getCount(), 3, "exportar mexeu na pilha");

            helper.assertTrue(edit(player, menu, Op.CLEAR), "recusou limpar");
            helper.assertTrue(edit(player, menu, Op.IMPORT_CARD), "recusou importar");
            GameTestCompat.assertValueEqual(helper, router.face(ResourceType.ITEM, Direction.UP).filter(), filter, "filtro importado");

            // Cartão de fluidos não entra numa face de itens.
            FilterCardItem.setContents(card, new FilterCardItem.Contents(ResourceType.FLUID,
                    Filter.EMPTY.withEntry(new FilterEntry.ModEntry("mekanism", 0))));
            helper.assertFalse(edit(player, menu, Op.IMPORT_CARD), "importou cartão de outro tipo");
            GameTestCompat.assertValueEqual(helper, router.face(ResourceType.ITEM, Direction.UP).filter(), filter,
                    "filtro mudou numa recusa");

            // Ida e volta pelo componente (persistente e de rede).
            FilterCardItem.Contents contents = FilterCardItem.contents(card);
            DynamicOps<Tag> ops = NbtOps.INSTANCE;
            GameTestCompat.assertValueEqual(helper, FilterCardItem.Contents.CODEC.encodeStart(ops, contents)
                    .flatMap(o -> FilterCardItem.Contents.CODEC.parse(ops, o)).getOrThrow(false, error -> {}), contents, "codec");
            RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer());
            FilterCardItem.Contents.STREAM_CODEC.encode(buf, contents);
            GameTestCompat.assertValueEqual(helper, FilterCardItem.Contents.STREAM_CODEC.decode(buf), contents, "codec de rede");
        } finally {
            close(player);
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void cardMenuWritesTheCard(GameTestHelper helper) {
        RouterBlockEntity router = chestWithRouter(helper);
        ServerPlayer player = playerNear(helper, router);
        ItemStack card = new ItemStack(ModItems.FILTER_CARD.get());
        player.setItemInHand(InteractionHand.MAIN_HAND, card);
        try {
            // Clique direito no ar abre a tela do cartão.
            card.use(player.level(), player, InteractionHand.MAIN_HAND);
            if (!(player.containerMenu instanceof FilterMenu menu)
                    || !(menu.target() instanceof CardFilterTarget target)) {
                throw new GameTestAssertException("tela do cartão não abriu");
            }
            helper.assertTrue(menu.view().isCard(), "visão de cartão como face");
            helper.assertTrue(target.stack() == card, "outra pilha");
            RegistryFriendlyByteBuf openData = GameTestCompat.lastOpenData(player);
            helper.assertTrue(openData != null, "sem pacote de abertura");
            GameTestCompat.assertValueEqual(helper, new FilterMenu(menu.containerId, player.getInventory(), openData).view(),
                    menu.view(), "visão do buffer de abertura");

            helper.assertTrue(edit(player, menu, Op.ADD_MOD, 0, 0, "minecraft"), "recusou o mod");
            helper.assertTrue(edit(player, menu, Op.SET_LIST_MODE, 0, 1, ""), "recusou lista negra");
            Filter expected = new Filter(Filter.ListMode.BLACKLIST, false,
                    List.of(new FilterEntry.ModEntry("minecraft", 0)));
            GameTestCompat.assertValueEqual(helper, ModDataComponents.CARD_FILTER.get(card),
                    new FilterCardItem.Contents(ResourceType.ITEM, expected), "componente do cartão");
            FilterView view = menu.pollView();
            helper.assertTrue(view != null && view.filter().equals(expected), "visão não acompanhou o cartão");
            helper.assertTrue(card.getItem().isFoil(card), "cartão com entradas sem brilho");

            helper.assertFalse(edit(player, menu, Op.IMPORT_CARD), "importou no próprio cartão");
            helper.assertFalse(edit(player, menu, Op.EXPORT_CARD), "exportou do próprio cartão");

            // Shift + clique direito com o cartão cheio não troca o tipo.
            player.setShiftKeyDown(true);
            card.use(player.level(), player, InteractionHand.MAIN_HAND);
            GameTestCompat.assertValueEqual(helper, FilterCardItem.contents(card).type(), ResourceType.ITEM, "trocou o tipo cheio");

            // BACK fecha a tela do cartão.
            helper.assertTrue(edit(player, menu, Op.BACK), "recusou fechar");
            helper.assertTrue(player.containerMenu == player.inventoryMenu, "tela do cartão não fechou");

            // Vazio, o tipo alterna e volta.
            FilterCardItem.setContents(card, FilterCardItem.Contents.EMPTY);
            card.use(player.level(), player, InteractionHand.MAIN_HAND);
            GameTestCompat.assertValueEqual(helper, FilterCardItem.contents(card).type(), ResourceType.FLUID, "não virou fluidos");
            card.use(player.level(), player, InteractionHand.MAIN_HAND);
            helper.assertFalse(ModDataComponents.CARD_FILTER.has(card), "cartão de itens vazio com componente");
            player.setShiftKeyDown(false);

            // O cartão saiu da mão: a tela não vale mais.
            CardFilterTarget again = new CardFilterTarget(player, InteractionHand.MAIN_HAND);
            FilterMenu reopened = new FilterMenu(CONTAINER_ID, player.getInventory(), again, again.view(player));
            player.containerMenu = reopened;
            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            helper.assertFalse(reopened.stillValid(player), "tela valeu sem o cartão");
            helper.assertFalse(edit(player, reopened, Op.ADD_MOD, 0, 0, "minecraft"), "editou sem o cartão");
        } finally {
            player.setShiftKeyDown(false);
            close(player);
        }
        helper.succeed();
    }
}
