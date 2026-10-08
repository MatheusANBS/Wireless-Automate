package io.github.matheusanbs.wirelessautomate.gametest;

import io.github.matheusanbs.wirelessautomate.Config;
import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.block.RouterBlock;
import io.github.matheusanbs.wirelessautomate.block.RouterBlockEntity;
import io.github.matheusanbs.wirelessautomate.block.RouterTier;
import io.github.matheusanbs.wirelessautomate.filter.Filter;
import io.github.matheusanbs.wirelessautomate.filter.FilterEntry;
import io.github.matheusanbs.wirelessautomate.menu.StorageChestMenu;
import io.github.matheusanbs.wirelessautomate.network.NetworkManager;
import io.github.matheusanbs.wirelessautomate.network.NetworkSavedData;
import io.github.matheusanbs.wirelessautomate.network.PortMode;
import io.github.matheusanbs.wirelessautomate.network.ResourceType;
import io.github.matheusanbs.wirelessautomate.packet.StorageActionPayload;
import io.github.matheusanbs.wirelessautomate.registry.ModBlocks;
import io.github.matheusanbs.wirelessautomate.registry.ModDataComponents;
import io.github.matheusanbs.wirelessautomate.registry.ModItems;
import io.github.matheusanbs.wirelessautomate.storage.ItemStorage;
import io.github.matheusanbs.wirelessautomate.storage.ItemStorageHandler;
import io.github.matheusanbs.wirelessautomate.storage.StorageChestBlock;
import io.github.matheusanbs.wirelessautomate.storage.StorageChestBlockEntity;
import io.github.matheusanbs.wirelessautomate.storage.StorageChestBlockItem;
import io.github.matheusanbs.wirelessautomate.storage.StorageContents;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.jetbrains.annotations.Nullable;

/**
 * Baú do mod: o conteúdo por tipo e quantidade {@code long}, a capacidade por tier, a visão como
 * inventário comum, quebrar e colocar sem perder nem duplicar, e o atalho do roteador entre Baús e
 * com baús vanilla. Máquinas em y=1 com o roteador em cima (facing=UP), como em
 * {@link TransferGameTests}; cada teste cria a própria rede.
 */
@GameTestHolder(WirelessAutomate.MODID)
@PrefixGameTestTemplate(false)
public final class StorageGameTests {
    private static final BlockPos A = new BlockPos(0, 1, 0);
    private static final BlockPos B = new BlockPos(2, 1, 2);
    private static final int CONTAINER_ID = 77;

    private static UUID newNetwork(GameTestHelper helper, String name) {
        return NetworkSavedData.get(helper.getLevel().getServer()).create(UUID.randomUUID(), name).id();
    }

    /** Roteador do tier em cima de {@code machine}, na rede, com a face de cima no modo dado. */
    private static RouterBlockEntity router(GameTestHelper helper, BlockPos machine, RouterTier tier,
            @Nullable UUID network, PortMode mode) {
        BlockPos routerPos = machine.above();
        helper.setBlock(routerPos, ModBlocks.ROUTER.get().defaultBlockState()
                .setValue(RouterBlock.FACING, Direction.UP).setValue(RouterBlock.TIER, tier));
        RouterBlockEntity router = helper.getBlockEntity(routerPos);
        router.setNetworkId(network);
        router.setMode(ResourceType.ITEM, Direction.UP, mode);
        return router;
    }

    private static StorageChestBlockEntity storageChest(GameTestHelper helper, BlockPos pos, RouterTier tier) {
        helper.setBlock(pos, ModBlocks.STORAGE_CHEST.get().defaultBlockState().setValue(RouterBlock.TIER, tier));
        return helper.getBlockEntity(pos);
    }

    private static ItemStorage storage(GameTestHelper helper, BlockPos pos) {
        StorageChestBlockEntity chest = helper.getBlockEntity(pos);
        return chest.storage();
    }

    private static long stored(GameTestHelper helper, BlockPos pos, Item item) {
        return storage(helper, pos).count(new ItemStack(item));
    }

    private static int vanilla(GameTestHelper helper, BlockPos pos, Item item) {
        ChestBlockEntity chest = helper.getBlockEntity(pos);
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

    /** Guardar e tirar por tipo, com o total somado de todos os tipos e o tipo zerado saindo da lista. */
    @GameTest(template = "empty")
    public static void storesByTypeAndCount(GameTestHelper helper) {
        ItemStorage storage = storageChest(helper, A, RouterTier.ULTIMATE).storage();
        helper.assertValueEqual(storage.insert(new ItemStack(Items.COBBLESTONE), 12_000_000L, false), 12_000_000L, "guardou");
        helper.assertValueEqual(storage.insert(new ItemStack(Items.DIAMOND, 7), 5, false), 5L, "diamantes");
        helper.assertValueEqual(storage.types(), 2, "tipos");
        helper.assertValueEqual(storage.total(), 12_000_005L, "total");
        helper.assertValueEqual(storage.extract(new ItemStack(Items.DIAMOND), 9, false), 5L, "tirou só o que havia");
        helper.assertValueEqual(storage.types(), 1, "tipo zerado sai da lista");
        helper.assertValueEqual(storage.extract(new ItemStack(Items.DIRT), 1, false), 0L, "tipo ausente");
        helper.succeed();
    }

    /** O tier limita o total; o Cartão de Upgrade sobe a capacidade sem perder o conteúdo. */
    @GameTest(template = "empty")
    public static void capacityFollowsTierAndUpgrade(GameTestHelper helper) {
        StorageChestBlockEntity chest = storageChest(helper, A, RouterTier.BASIC);
        long basic = Config.chestCapacity(RouterTier.BASIC);
        ItemStorage storage = chest.storage();
        helper.assertValueEqual(storage.insert(new ItemStack(Items.COBBLESTONE), basic + 100, false), basic, "cheio no Básico");
        helper.assertValueEqual(storage.insert(new ItemStack(Items.DIRT), 1, true), 0L, "cheio aceita nada");
        helper.assertTrue(StorageChestBlock.tryUpgrade(helper.getLevel(), helper.absolutePos(A), RouterTier.ADVANCED),
                "upgrade para Avançado");
        helper.assertFalse(StorageChestBlock.tryUpgrade(helper.getLevel(), helper.absolutePos(A), RouterTier.ULTIMATE),
                "não pula tier");
        StorageChestBlockEntity upgraded = helper.getBlockEntity(A);
        helper.assertTrue(upgraded == chest, "o block entity é o mesmo");
        helper.assertValueEqual(upgraded.storage().total(), basic, "conteúdo mantido");
        helper.assertValueEqual(upgraded.storage().insert(new ItemStack(Items.DIRT), 100, false), 100L, "cabe mais");
        helper.succeed();
    }

    /** Visto como inventário comum: um slot por tipo mais um vazio, e no máximo uma pilha por extração. */
    @GameTest(template = "empty")
    public static void itemHandlerViewFollowsTheContract(GameTestHelper helper) {
        StorageChestBlockEntity chest = storageChest(helper, A, RouterTier.ULTIMATE);
        chest.storage().insert(new ItemStack(Items.COBBLESTONE), 1_000, false);
        ItemStorageHandler handler = chest.handler();
        helper.assertValueEqual(handler.getSlots(), 2, "um tipo + o vazio");
        helper.assertValueEqual(handler.getStackInSlot(0).getCount(), 1_000, "quantidade real");
        helper.assertValueEqual(handler.extractItem(0, 500, false).getCount(), 64, "uma pilha por extração");
        helper.assertValueEqual(handler.insertItem(0, new ItemStack(Items.DIAMOND, 3), false).getCount(), 3,
                "slot de outro tipo recusa");
        helper.assertTrue(handler.insertItem(1, new ItemStack(Items.DIAMOND, 3), false).isEmpty(), "o vazio aceita");
        helper.assertValueEqual(chest.storage().total(), 1_000L - 64 + 3, "total");
        helper.succeed();
    }

    /**
     * Baú → Baú no Ultimate: os 12 milhões de pedregulho (o teste do dono que parava em ~274 mil
     * itens/s entre barris) passam numa visita, sem item criado nem perdido em nenhum tick.
     */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void chestToChestMovesMillionsAtOnce(GameTestHelper helper) {
        UUID network = newNetwork(helper, "teste-bau-bau");
        storageChest(helper, A, RouterTier.ULTIMATE).storage().insert(new ItemStack(Items.COBBLESTONE), 12_000_000L, false);
        storage(helper, A).insert(new ItemStack(Items.DIAMOND), 1_000, false);
        storageChest(helper, B, RouterTier.ULTIMATE);
        RouterBlockEntity source = router(helper, A, RouterTier.ULTIMATE, network, PortMode.EXTRACT);
        RouterBlockEntity target = router(helper, B, RouterTier.ULTIMATE, network, PortMode.INSERT);
        long[] started = new long[1];

        helper.onEachTick(() -> {
            helper.assertValueEqual(stored(helper, A, Items.COBBLESTONE) + stored(helper, B, Items.COBBLESTONE),
                    12_000_000L, "pedregulho");
            helper.assertValueEqual(stored(helper, A, Items.DIAMOND) + stored(helper, B, Items.DIAMOND), 1_000L, "diamantes");
        });
        helper.startSequence()
                .thenWaitUntil(() -> waitRegistered(helper, source, target))
                .thenExecute(() -> started[0] = helper.getTick())
                .thenWaitUntil(() -> helper.assertTrue(storage(helper, A).isEmpty(), "origem não esvaziou"))
                .thenExecute(() -> {
                    long ticks = helper.getTick() - started[0];
                    // Pilha por pilha (64 por extração, 32 por visita) seriam milhares de ticks; a folga
                    // cobre a remontagem das rotas e o orçamento dividido com os outros testes do lote.
                    helper.assertTrue(ticks <= 20, "levou " + ticks + " ticks: o atalho não moveu o tipo inteiro");
                    helper.assertValueEqual(stored(helper, B, Items.COBBLESTONE), 12_000_000L, "pedregulho no destino");
                })
                .thenSucceed();
    }

    /** Baú → baú vanilla: entrega em pilhas até o vanilla encher; o resto fica no Baú. */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void chestFillsAVanillaChest(GameTestHelper helper) {
        UUID network = newNetwork(helper, "teste-bau-vanilla");
        storageChest(helper, A, RouterTier.BASIC).storage().insert(new ItemStack(Items.COBBLESTONE), 5_000, false);
        helper.setBlock(B, Blocks.CHEST);
        RouterBlockEntity source = router(helper, A, RouterTier.ELITE, network, PortMode.EXTRACT);
        RouterBlockEntity target = router(helper, B, RouterTier.ELITE, network, PortMode.INSERT);
        int capacity = 27 * 64;

        helper.onEachTick(() -> helper.assertValueEqual(
                stored(helper, A, Items.COBBLESTONE) + vanilla(helper, B, Items.COBBLESTONE), 5_000L, "pedregulho"));
        helper.startSequence()
                .thenWaitUntil(() -> waitRegistered(helper, source, target))
                .thenWaitUntil(() -> helper.assertValueEqual(vanilla(helper, B, Items.COBBLESTONE), capacity, "vanilla cheio"))
                .thenIdle(5)
                .thenExecute(() -> helper.assertValueEqual(stored(helper, A, Items.COBBLESTONE), 5_000L - capacity, "resto no Baú"))
                .thenSucceed();
    }

    /** Baú vanilla → Baú: tudo entra, por tipo, sem varrer slots do destino. */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void vanillaChestFillsAChest(GameTestHelper helper) {
        UUID network = newNetwork(helper, "teste-vanilla-bau");
        helper.setBlock(A, Blocks.CHEST);
        ChestBlockEntity chest = helper.getBlockEntity(A);
        chest.setItem(0, new ItemStack(Items.DIAMOND, 10));
        chest.setItem(3, new ItemStack(Items.COBBLESTONE, 64));
        chest.setItem(7, new ItemStack(Items.COBBLESTONE, 30));
        storageChest(helper, B, RouterTier.BASIC);
        RouterBlockEntity source = router(helper, A, RouterTier.ELITE, network, PortMode.EXTRACT);
        RouterBlockEntity target = router(helper, B, RouterTier.ELITE, network, PortMode.INSERT);

        helper.onEachTick(() -> helper.assertValueEqual(
                vanilla(helper, A, Items.COBBLESTONE) + stored(helper, B, Items.COBBLESTONE), 94L, "pedregulho"));
        helper.startSequence()
                .thenWaitUntil(() -> waitRegistered(helper, source, target))
                .thenWaitUntil(() -> {
                    helper.assertValueEqual(stored(helper, B, Items.DIAMOND), 10L, "diamantes no Baú");
                    helper.assertValueEqual(stored(helper, B, Items.COBBLESTONE), 94L, "pedregulho no Baú");
                    helper.assertValueEqual(storage(helper, B).types(), 2, "dois tipos");
                })
                .thenSucceed();
    }

    /**
     * Quebrar leva o conteúdo junto (no servidor, pela referência do item) e colocar o traz de volta.
     * Uma cópia do item nasce vazia: colocar é tirar, então nada duplica. O filtro de entrada vai junto.
     */
    @GameTest(template = "empty")
    public static void breakingAndPlacingKeepsTheContents(GameTestHelper helper) {
        storageChest(helper, A, RouterTier.ELITE).storage().insert(new ItemStack(Items.COBBLESTONE), 1_000_000L, false);
        storage(helper, A).insert(new ItemStack(Items.DIAMOND), 5, false);
        Filter filter = new Filter(Filter.ListMode.BLACKLIST, false,
                List.of(new FilterEntry.ItemEntry(new ItemStack(Items.DIRT), 0)));
        StorageChestBlockEntity original = helper.getBlockEntity(A);
        original.setFilter(filter);
        BlockPos absolute = helper.absolutePos(A);
        helper.getLevel().destroyBlock(absolute, true);

        List<ItemEntity> drops = helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(absolute).inflate(2),
                entity -> entity.getItem().is(ModBlocks.STORAGE_CHEST.get().asItem()));
        helper.assertValueEqual(drops.size(), 1, "um Baú no chão");
        ItemStack dropped = drops.get(0).getItem().copy();
        drops.get(0).discard();
        StorageContents contents = dropped.get(ModDataComponents.STORAGE_CONTENTS.get());
        helper.assertTrue(contents != null, "o item leva a referência");
        helper.assertValueEqual(contents.total(), 1_000_005L, "resumo do total");
        helper.assertValueEqual(contents.types(), 2, "resumo dos tipos");
        helper.assertValueEqual(dropped.get(DataComponents.BLOCK_STATE)
                .get(RouterBlock.TIER), RouterTier.ELITE, "tier no item");

        ItemStack copy = dropped.copy();
        place(helper, dropped, A);
        place(helper, copy, B);
        helper.assertValueEqual(storage(helper, A).total(), 1_000_005L, "o conteúdo voltou");
        helper.assertValueEqual(stored(helper, A, Items.DIAMOND), 5L, "diamantes de volta");
        helper.assertTrue(storage(helper, B).isEmpty(), "a cópia do item nasce vazia");
        StorageChestBlockEntity placed = helper.getBlockEntity(A);
        helper.assertValueEqual(placed.filter(), filter, "o filtro voltou com o bloco");
        helper.succeed();
    }

    /** Filtro de entrada: só entra o que ele aceita, e o estoque de uma entrada é "guardar até N". */
    @GameTest(template = "empty")
    public static void inputFilterLimitsWhatEnters(GameTestHelper helper) {
        StorageChestBlockEntity chest = storageChest(helper, A, RouterTier.ULTIMATE);
        chest.setFilter(new Filter(Filter.ListMode.WHITELIST, false,
                List.of(new FilterEntry.ItemEntry(new ItemStack(Items.COBBLESTONE), 100))));
        ItemStorage storage = chest.storage();
        helper.assertValueEqual(storage.insert(new ItemStack(Items.DIRT), 10, false), 0L, "terra recusada");
        helper.assertValueEqual(storage.insert(new ItemStack(Items.COBBLESTONE), 64, false), 64L, "primeira pilha");
        helper.assertValueEqual(storage.insert(new ItemStack(Items.COBBLESTONE), 64, false), 36L, "só até 100");
        helper.assertTrue(chest.handler().insertItem(1, new ItemStack(Items.DIRT), false).getCount() == 1,
                "o funil também passa pelo filtro");
        chest.setFilter(Filter.EMPTY);
        helper.assertValueEqual(storage.insert(new ItemStack(Items.DIRT), 10, false), 10L, "sem filtro, tudo entra");
        helper.succeed();
    }

    /** Roteador para um Baú com filtro: o que o filtro recusa fica na origem. */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void routerRespectsTheInputFilter(GameTestHelper helper) {
        UUID network = newNetwork(helper, "teste-bau-filtro");
        helper.setBlock(A, Blocks.CHEST);
        ChestBlockEntity source = helper.getBlockEntity(A);
        source.setItem(0, new ItemStack(Items.DIAMOND, 10));
        source.setItem(1, new ItemStack(Items.DIRT, 20));
        storageChest(helper, B, RouterTier.BASIC).setFilter(new Filter(Filter.ListMode.WHITELIST, false,
                List.of(new FilterEntry.ItemEntry(new ItemStack(Items.DIAMOND), 0))));
        RouterBlockEntity from = router(helper, A, RouterTier.ELITE, network, PortMode.EXTRACT);
        RouterBlockEntity to = router(helper, B, RouterTier.ELITE, network, PortMode.INSERT);
        helper.startSequence()
                .thenWaitUntil(() -> waitRegistered(helper, from, to))
                .thenWaitUntil(() -> helper.assertValueEqual(stored(helper, B, Items.DIAMOND), 10L, "diamantes no Baú"))
                .thenIdle(10)
                .thenExecute(() -> {
                    helper.assertValueEqual(stored(helper, B, Items.DIRT), 0L, "terra recusada");
                    helper.assertValueEqual(vanilla(helper, A, Items.DIRT), 20, "terra na origem");
                })
                .thenSucceed();
    }

    /** Os cliques da tela: pegar pilha e meia, devolver um e tudo, para o inventário e Shift + clique de volta. */
    @GameTest(template = "empty")
    public static void screenActionsMoveItems(GameTestHelper helper) {
        StorageChestBlockEntity chest = storageChest(helper, A, RouterTier.BASIC);
        ItemStorage storage = chest.storage();
        storage.insert(new ItemStack(Items.COBBLESTONE), 1_000, false);
        ServerPlayer player = playerNear(helper, chest);
        StorageChestMenu menu = openMenu(player, chest);
        ItemStack cobble = new ItemStack(Items.COBBLESTONE);

        helper.assertTrue(act(player, menu, StorageActionPayload.Action.TAKE_STACK, cobble), "pegar pilha");
        helper.assertValueEqual(menu.getCarried().getCount(), 64, "pilha no cursor");
        helper.assertTrue(act(player, menu, StorageActionPayload.Action.INSERT_CARRIED_ONE, ItemStack.EMPTY), "devolver um");
        helper.assertValueEqual(menu.getCarried().getCount(), 63, "um a menos no cursor");
        helper.assertTrue(act(player, menu, StorageActionPayload.Action.INSERT_CARRIED, ItemStack.EMPTY), "devolver tudo");
        helper.assertTrue(menu.getCarried().isEmpty(), "cursor vazio");
        helper.assertValueEqual(storage.total(), 1_000L, "tudo de volta");
        helper.assertTrue(act(player, menu, StorageActionPayload.Action.TAKE_HALF, cobble), "meia pilha");
        helper.assertValueEqual(menu.getCarried().getCount(), 32, "meia pilha no cursor");
        helper.assertFalse(act(player, menu, StorageActionPayload.Action.TAKE_STACK, new ItemStack(Items.DIAMOND)),
                "tipo que não existe");
        menu.setCarried(ItemStack.EMPTY);
        helper.assertTrue(act(player, menu, StorageActionPayload.Action.TAKE_TO_INVENTORY, cobble), "para o inventário");
        int slot = player.getInventory().findSlotMatchingItem(cobble);
        helper.assertTrue(slot >= 0, "pedregulho no inventário");
        helper.assertValueEqual(storage.total(), 1_000L - 32 - 64, "saiu do Baú");
        int menuSlot = slot < 9 ? 27 + slot : slot - 9;
        menu.quickMoveStack(player, menuSlot);
        helper.assertTrue(player.getInventory().getItem(slot).isEmpty(), "Shift + clique guardou");
        helper.assertValueEqual(storage.total(), 1_000L - 32, "de volta ao Baú");
        helper.succeed();
    }

    /**
     * A tela recebe tudo na abertura e depois só as diferenças, no máximo a cada
     * {@link StorageChestMenu#SYNC_INTERVAL} ticks; um tipo que zera chega com 0.
     */
    @GameTest(template = "empty")
    public static void screenReceivesOnlyChanges(GameTestHelper helper) {
        StorageChestBlockEntity chest = storageChest(helper, A, RouterTier.BASIC);
        ItemStorage storage = chest.storage();
        storage.insert(new ItemStack(Items.COBBLESTONE), 500, false);
        storage.insert(new ItemStack(Items.DIAMOND), 5, false);
        ServerPlayer player = playerNear(helper, chest);
        // Fora do containerMenu do jogador: o tick dele chamaria broadcastChanges e consumiria o envio.
        StorageChestMenu menu = new StorageChestMenu(CONTAINER_ID, player.getInventory(), chest);
        StorageChestMenu.Sync first = menu.poll();
        helper.assertTrue(first != null && first.reset(), "a abertura manda tudo");
        helper.assertValueEqual(first.changes().size(), 2, "dois tipos");
        helper.assertTrue(menu.poll() == null, "nada mudou, nada vai");
        storage.extract(new ItemStack(Items.DIAMOND), 5, false);
        helper.assertTrue(menu.poll() == null, "espera o intervalo");
        helper.startSequence()
                .thenIdle(StorageChestMenu.SYNC_INTERVAL + 1)
                .thenExecute(() -> {
                    StorageChestMenu.Sync next = menu.poll();
                    helper.assertTrue(next != null && !next.reset(), "a diferença vai depois do intervalo");
                    helper.assertValueEqual(next.changes().size(), 1, "só o tipo que mudou");
                    helper.assertValueEqual(next.changes().get(0).count(), 0L, "diamante saiu");
                    helper.assertValueEqual(next.header().total(), 500L, "total novo");
                })
                .thenSucceed();
    }

    /** Baú + Cartão de Upgrade do tier seguinte na bancada: sobe o tier e mantém a referência ao conteúdo. */
    @GameTest(template = "empty")
    public static void chestUpgradeRecipe(GameTestHelper helper) {
        ItemStack chest = StorageChestBlockItem.withTier(ModItems.STORAGE_CHEST.get(), RouterTier.BASIC);
        StorageContents contents = new StorageContents(UUID.randomUUID(), 3, 12_345L);
        chest.set(ModDataComponents.STORAGE_CONTENTS.get(), contents);
        CraftingInput input = CraftingInput.of(2, 1,
                List.of(chest, new ItemStack(ModItems.TIER_CORES.get(RouterTier.ADVANCED).get())));
        ItemStack out = helper.getLevel().getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, helper.getLevel())
                .orElseThrow(() -> new GameTestAssertException("sem receita de upgrade do Baú"))
                .value().assemble(input, helper.getLevel().registryAccess());
        helper.assertTrue(out.is(ModItems.STORAGE_CHEST.get()), "resultado: " + out);
        helper.assertValueEqual(StorageChestBlockItem.tierOf(out), RouterTier.ADVANCED, "tier");
        helper.assertValueEqual(out.get(ModDataComponents.STORAGE_CONTENTS.get()), contents, "conteúdo mantido");
        CraftingInput skip = CraftingInput.of(2, 1,
                List.of(chest, new ItemStack(ModItems.TIER_CORES.get(RouterTier.ELITE).get())));
        helper.assertTrue(helper.getLevel().getRecipeManager()
                .getRecipeFor(RecipeType.CRAFTING, skip, helper.getLevel()).isEmpty(), "não pula tier");
        helper.succeed();
    }

    @SuppressWarnings("removal")
    private static ServerPlayer playerNear(GameTestHelper helper, StorageChestBlockEntity chest) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.moveTo(Vec3.atCenterOf(chest.getBlockPos().above()));
        return player;
    }

    private static StorageChestMenu openMenu(ServerPlayer player, StorageChestBlockEntity chest) {
        StorageChestMenu menu = new StorageChestMenu(CONTAINER_ID, player.getInventory(), chest);
        player.containerMenu = menu;
        return menu;
    }

    private static boolean act(ServerPlayer player, StorageChestMenu menu, StorageActionPayload.Action action, ItemStack key) {
        return StorageChestMenu.handle(player, new StorageActionPayload(menu.containerId, action, key));
    }

    /**
     * Coloca o item do Baú em {@code pos} (relativa) como o {@code BlockItem} faz, clicando numa
     * pedra embaixo. Sem jogador, para a colisão com ele não impedir.
     */
    private static void place(GameTestHelper helper, ItemStack stack, BlockPos pos) {
        helper.setBlock(pos.below(), Blocks.STONE);
        BlockPos floor = helper.absolutePos(pos).below();
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(floor).add(0, 0.5, 0), Direction.UP, floor, false);
        ((BlockItem) stack.getItem()).place(new BlockPlaceContext(helper.getLevel(), null, InteractionHand.MAIN_HAND, stack, hit));
        helper.assertBlockPresent(ModBlocks.STORAGE_CHEST.get(), pos);
    }

    private StorageGameTests() {
    }
}
