package io.github.matheusanbs.wirelessautomate.gametest;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.github.matheusanbs.wirelessautomate.Config;
import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.command.StorageCommand;
import io.github.matheusanbs.wirelessautomate.block.RouterBlock;
import io.github.matheusanbs.wirelessautomate.block.RouterBlockEntity;
import io.github.matheusanbs.wirelessautomate.block.RouterTier;
import io.github.matheusanbs.wirelessautomate.filter.Filter;
import io.github.matheusanbs.wirelessautomate.filter.FilterEntry;
import io.github.matheusanbs.wirelessautomate.menu.StorageListMenu;
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
import io.github.matheusanbs.wirelessautomate.storage.StorageBatteryBlockEntity;
import io.github.matheusanbs.wirelessautomate.storage.StorageBlock;
import io.github.matheusanbs.wirelessautomate.storage.StorageBlockItem;
import io.github.matheusanbs.wirelessautomate.storage.StorageChestBlockEntity;
import io.github.matheusanbs.wirelessautomate.storage.StorageContents;
import io.github.matheusanbs.wirelessautomate.storage.StorageKind;
import io.github.matheusanbs.wirelessautomate.storage.StorageSavedData;
import io.github.matheusanbs.wirelessautomate.storage.StorageSourceTankBlock;
import io.github.matheusanbs.wirelessautomate.storage.StorageSourceTankBlockEntity;
import io.github.matheusanbs.wirelessautomate.storage.StorageTankBlockEntity;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.ListTag;
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
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;
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
    private static final BlockPos C = new BlockPos(0, 1, 2);
    private static final int CONTAINER_ID = 77;

    private static UUID newNetwork(GameTestHelper helper, String name) {
        return NetworkSavedData.get(helper.getLevel().getServer()).create(UUID.randomUUID(), name).id();
    }

    /** Roteador do tier em cima de {@code machine}, na rede, com a face de cima de itens no modo dado. */
    private static RouterBlockEntity router(GameTestHelper helper, BlockPos machine, RouterTier tier,
            @Nullable UUID network, PortMode mode) {
        return router(helper, machine, tier, network, ResourceType.ITEM, mode);
    }

    /** O mesmo, para o tipo de recurso dado. */
    private static RouterBlockEntity router(GameTestHelper helper, BlockPos machine, RouterTier tier,
            @Nullable UUID network, ResourceType type, PortMode mode) {
        BlockPos routerPos = machine.above();
        helper.setBlock(routerPos, ModBlocks.ROUTER.get().defaultBlockState()
                .setValue(RouterBlock.FACING, Direction.UP).setValue(RouterBlock.TIER, tier));
        RouterBlockEntity router = helper.getBlockEntity(routerPos);
        router.setNetworkId(network);
        router.setNetworkId(type, network);
        router.setMode(type, Direction.UP, mode);
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
        helper.assertTrue(StorageBlock.tryUpgrade(helper.getLevel(), helper.absolutePos(A), RouterTier.ADVANCED),
                "upgrade para Avançado");
        helper.assertFalse(StorageBlock.tryUpgrade(helper.getLevel(), helper.absolutePos(A), RouterTier.ULTIMATE),
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
        StorageListMenu<ItemStack> menu = openMenu(player, chest);
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
     * {@link StorageListMenu#SYNC_INTERVAL} ticks; um tipo que zera chega com 0.
     */
    @GameTest(template = "empty")
    public static void screenReceivesOnlyChanges(GameTestHelper helper) {
        StorageChestBlockEntity chest = storageChest(helper, A, RouterTier.BASIC);
        ItemStorage storage = chest.storage();
        storage.insert(new ItemStack(Items.COBBLESTONE), 500, false);
        storage.insert(new ItemStack(Items.DIAMOND), 5, false);
        ServerPlayer player = playerNear(helper, chest);
        // Fora do containerMenu do jogador: o tick dele chamaria broadcastChanges e consumiria o envio.
        StorageListMenu<ItemStack> menu = new StorageListMenu<>(CONTAINER_ID, player.getInventory(), chest);
        StorageListMenu.Sync<ItemStack> first = menu.poll();
        helper.assertTrue(first != null && first.reset(), "a abertura manda tudo");
        helper.assertValueEqual(first.changes().size(), 2, "dois tipos");
        helper.assertTrue(menu.poll() == null, "nada mudou, nada vai");
        storage.extract(new ItemStack(Items.DIAMOND), 5, false);
        helper.assertTrue(menu.poll() == null, "espera o intervalo");
        helper.startSequence()
                .thenIdle(StorageListMenu.SYNC_INTERVAL + 1)
                .thenExecute(() -> {
                    StorageListMenu.Sync<ItemStack> next = menu.poll();
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
        ItemStack chest = StorageBlockItem.withTier(ModItems.STORAGE_CHEST.get(), RouterTier.BASIC);
        StorageContents contents = new StorageContents(UUID.randomUUID(), 3, 12_345L);
        chest.set(ModDataComponents.STORAGE_CONTENTS.get(), contents);
        CraftingInput input = CraftingInput.of(2, 1,
                List.of(chest, new ItemStack(ModItems.TIER_CORES.get(RouterTier.ADVANCED).get())));
        ItemStack out = helper.getLevel().getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, helper.getLevel())
                .orElseThrow(() -> new GameTestAssertException("sem receita de upgrade do Baú"))
                .value().assemble(input, helper.getLevel().registryAccess());
        helper.assertTrue(out.is(ModItems.STORAGE_CHEST.get()), "resultado: " + out);
        helper.assertValueEqual(StorageBlockItem.tierOf(out), RouterTier.ADVANCED, "tier");
        helper.assertValueEqual(out.get(ModDataComponents.STORAGE_CONTENTS.get()), contents, "conteúdo mantido");
        CraftingInput skip = CraftingInput.of(2, 1,
                List.of(chest, new ItemStack(ModItems.TIER_CORES.get(RouterTier.ELITE).get())));
        helper.assertTrue(helper.getLevel().getRecipeManager()
                .getRecipeFor(RecipeType.CRAFTING, skip, helper.getLevel()).isEmpty(), "não pula tier");
        helper.succeed();
    }

    /**
     * O bug do dono: quebrado no criativo, o Baú não dropava nada e o conteúdo ficava órfão no
     * servidor. Agora ele solta o item com o conteúdo, como a caixa de shulker.
     */
    @GameTest(template = "empty")
    public static void creativeBreakDropsTheContents(GameTestHelper helper) {
        breakByPlayer(helper, GameType.CREATIVE, ItemStack.EMPTY, 1_234);
    }

    /** Sem a picareta no sobrevivência a loot table não roda; o conteúdo ainda vira item. */
    @GameTest(template = "empty")
    public static void breakWithoutToolDropsTheContents(GameTestHelper helper) {
        breakByPlayer(helper, GameType.SURVIVAL, ItemStack.EMPTY, 2_345);
    }

    /** Com a picareta, só a loot table dropa: um item, não dois. */
    @GameTest(template = "empty")
    public static void breakWithPickaxeDropsOnce(GameTestHelper helper) {
        breakByPlayer(helper, GameType.SURVIVAL, new ItemStack(Items.DIAMOND_PICKAXE), 3_456);
    }

    /** Explosão: o Baú cheio também vira item (a loot table não tem mais a condição de sobreviver). */
    @GameTest(template = "empty")
    public static void explosionDropsTheContents(GameTestHelper helper) {
        storageChest(helper, A, RouterTier.BASIC).storage().insert(new ItemStack(Items.COBBLESTONE), 5_000, false);
        BlockPos absolute = helper.absolutePos(A);
        helper.getLevel().explode(null, absolute.getX() + 0.5, absolute.getY() + 0.5, absolute.getZ() + 0.5, 2.0F,
                Level.ExplosionInteraction.BLOCK);
        helper.assertBlockNotPresent(ModBlocks.STORAGE_CHEST.get(), A);
        assertDroppedWith(helper, absolute, 5_000L);
        helper.succeed();
    }

    /**
     * {@code /wa storage recover}: um conteúdo órfão no servidor volta como item, e colocar o item
     * traz o conteúdo de volta.
     */
    @GameTest(template = "empty")
    public static void recoverCommandGivesTheContentsBack(GameTestHelper helper) {
        StorageChestBlockEntity chest = storageChest(helper, A, RouterTier.ELITE);
        chest.storage().insert(new ItemStack(Items.DIAMOND), 777, false);
        // Sai do mundo sem drop nenhum, como um /setblock: o conteúdo fica órfão no servidor.
        helper.getLevel().setBlock(helper.absolutePos(A), Blocks.AIR.defaultBlockState(), 3);
        UUID orphan = null;
        for (Map.Entry<UUID, StorageSavedData.Stored> entry : StorageSavedData.get(helper.getLevel().getServer()).contents().entrySet()) {
            if (entry.getValue().data() instanceof ListTag list && list.size() == 1
                    && list.getCompound(0).getLong("count") == 777) {
                orphan = entry.getKey();
            }
        }
        helper.assertTrue(orphan != null, "o conteúdo ficou no servidor");
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.getInventory().clearContent();
        try {
            helper.getLevel().getServer().getCommands().getDispatcher().execute("wa storage recover " + orphan,
                    player.createCommandSourceStack().withPermission(2));
        } catch (CommandSyntaxException e) {
            throw new GameTestAssertException("o comando falhou: " + e.getMessage());
        }
        // Pelo item só: o entregue tem componentes (tier e conteúdo), que o findSlotMatchingItem compara.
        ItemStack given = ItemStack.EMPTY;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            if (player.getInventory().getItem(i).is(ModItems.STORAGE_CHEST.get())) {
                given = player.getInventory().getItem(i);
            }
        }
        StorageContents contents = given.get(ModDataComponents.STORAGE_CONTENTS.get());
        helper.assertTrue(contents != null && contents.id().equals(orphan) && contents.total() == 777,
                "o item aponta para o conteúdo: " + given);
        helper.assertValueEqual(StorageBlockItem.tierOf(given), RouterTier.ELITE, "o tier que se perdeu");
        place(helper, given, B);
        helper.assertValueEqual(stored(helper, B, Items.DIAMOND), 777L, "os diamantes voltaram");
        helper.succeed();
    }

    /**
     * Conteúdos guardados antes de o tier ser gravado: o {@code recover} escolhe o menor tier que
     * comporta tudo (pela capacidade da config), para nada ficar além do limite.
     */
    @GameTest(template = "empty")
    public static void recoverGuessesTheTierOfOldContents(GameTestHelper helper) {
        long basic = Config.storageCapacity(StorageKind.CHEST, RouterTier.BASIC);
        helper.assertValueEqual(StorageCommand.tierFor(new StorageSavedData.Stored(StorageKind.CHEST, null, new ListTag()), 10),
                RouterTier.BASIC, "pouco cabe no Básico");
        helper.assertValueEqual(StorageCommand.tierFor(new StorageSavedData.Stored(StorageKind.CHEST, null, new ListTag()),
                basic + 1), RouterTier.ADVANCED, "um a mais que o Básico vai para o Avançado");
        helper.assertValueEqual(StorageCommand.tierFor(new StorageSavedData.Stored(StorageKind.CHEST, RouterTier.ELITE,
                new ListTag()), 10), RouterTier.ELITE, "gravado vale o gravado");
        helper.succeed();
    }

    /**
     * Um Baú com {@code total} pedregulhos quebrado por um jogador no modo e com a ferramenta dados:
     * exatamente um item com o conteúdo no chão. O total é diferente em cada teste, porque os testes
     * do lote rodam lado a lado e os drops de um vizinho podem cair perto.
     */
    @SuppressWarnings("removal")
    private static void breakByPlayer(GameTestHelper helper, GameType mode, ItemStack tool, long total) {
        storageChest(helper, A, RouterTier.ADVANCED).storage().insert(new ItemStack(Items.COBBLESTONE), total, false);
        BlockPos absolute = helper.absolutePos(A);
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.moveTo(Vec3.atCenterOf(absolute.above()));
        player.setGameMode(mode);
        player.setItemInHand(InteractionHand.MAIN_HAND, tool);
        player.gameMode.destroyBlock(absolute);
        helper.assertBlockNotPresent(ModBlocks.STORAGE_CHEST.get(), A);
        assertDroppedWith(helper, absolute, total);
        helper.succeed();
    }

    /**
     * Exatamente um Baú no chão perto de {@code absolute}, e levando o conteúdo com esse total. Conta
     * também os vazios: um drop sem a referência é o bug de o conteúdo sumir.
     */
    private static void assertDroppedWith(GameTestHelper helper, BlockPos absolute, long total) {
        List<ItemEntity> drops = helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(absolute).inflate(1.5),
                entity -> entity.getItem().is(ModItems.STORAGE_CHEST.get()));
        helper.assertValueEqual(drops.size(), 1, "Baús no chão " + drops.stream()
                .map(e -> String.valueOf(e.getItem().get(ModDataComponents.STORAGE_CONTENTS.get()))).toList());
        StorageContents contents = drops.get(0).getItem().get(ModDataComponents.STORAGE_CONTENTS.get());
        helper.assertTrue(contents != null && contents.total() == total, "o Baú no chão leva o conteúdo: " + contents);
        drops.forEach(ItemEntity::discard);
    }

    @SuppressWarnings("removal")
    private static ServerPlayer playerNear(GameTestHelper helper, StorageChestBlockEntity chest) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.moveTo(Vec3.atCenterOf(chest.getBlockPos().above()));
        return player;
    }

    private static StorageListMenu<ItemStack> openMenu(ServerPlayer player, StorageChestBlockEntity chest) {
        StorageListMenu<ItemStack> menu = new StorageListMenu<>(CONTAINER_ID, player.getInventory(), chest);
        player.containerMenu = menu;
        return menu;
    }

    private static boolean act(ServerPlayer player, StorageListMenu<ItemStack> menu, StorageActionPayload.Action action, ItemStack key) {
        return StorageListMenu.handle(player, new StorageActionPayload(menu.containerId, StorageKind.CHEST, action,
                key.isEmpty() ? Optional.empty() : Optional.of(key)));
    }

    // ------------------------------------------------------------------ Tanque e Bateria

    private static StorageTankBlockEntity storageTank(GameTestHelper helper, BlockPos pos, RouterTier tier) {
        helper.setBlock(pos, ModBlocks.STORAGE.get(StorageKind.TANK).get().defaultBlockState().setValue(RouterBlock.TIER, tier));
        return helper.getBlockEntity(pos);
    }

    private static StorageBatteryBlockEntity storageBattery(GameTestHelper helper, BlockPos pos, RouterTier tier) {
        helper.setBlock(pos, ModBlocks.STORAGE.get(StorageKind.BATTERY).get().defaultBlockState().setValue(RouterBlock.TIER, tier));
        return helper.getBlockEntity(pos);
    }

    private static StorageSourceTankBlockEntity storageSourceTank(GameTestHelper helper, BlockPos pos, RouterTier tier) {
        helper.setBlock(pos, ModBlocks.STORAGE.get(StorageKind.SOURCE_TANK).get().defaultBlockState().setValue(RouterBlock.TIER, tier));
        return helper.getBlockEntity(pos);
    }

    private static long fluid(GameTestHelper helper, BlockPos pos, net.minecraft.world.level.material.Fluid fluid) {
        StorageTankBlockEntity tank = helper.getBlockEntity(pos);
        return tank.storage().count(new FluidStack(fluid, 1));
    }

    private static long energy(GameTestHelper helper, BlockPos pos) {
        StorageBatteryBlockEntity battery = helper.getBlockEntity(pos);
        return battery.store().stored();
    }

    /** Tanque: vários fluidos por tipo, capacidade total do tier, e a visão de handler de fluido comum. */
    @GameTest(template = "empty")
    public static void tankStoresFluidsByType(GameTestHelper helper) {
        StorageTankBlockEntity tank = storageTank(helper, A, RouterTier.BASIC);
        long capacity = Config.storageCapacity(StorageKind.TANK, RouterTier.BASIC);
        helper.assertValueEqual(tank.handler().fill(new FluidStack(Fluids.WATER, 400_000), FluidAction.EXECUTE), 400_000,
                "água pela visão comum");
        helper.assertValueEqual(tank.storage().insert(new FluidStack(Fluids.LAVA, 1), capacity, false),
                capacity - 400_000, "lava até a capacidade");
        helper.assertValueEqual(tank.storage().types(), 2, "dois fluidos");
        helper.assertValueEqual(tank.handler().getTanks(), 3, "um tanque por fluido + o vazio");
        helper.assertValueEqual(tank.handler().drain(new FluidStack(Fluids.WATER, 1_000), FluidAction.EXECUTE).getAmount(),
                1_000, "drena a água pedida");
        helper.assertValueEqual(tank.handler().fill(new FluidStack(Fluids.WATER, 5_000), FluidAction.SIMULATE), 1_000,
                "cheio: só cabe o que saiu");
        helper.succeed();
    }

    /** Balde no bloco: esvazia o balde cheio no Tanque e enche o vazio com o que tiver, como num tanque comum. */
    @GameTest(template = "empty")
    @SuppressWarnings("removal")
    public static void bucketOnTheTankBlock(GameTestHelper helper) {
        StorageTankBlockEntity tank = storageTank(helper, A, RouterTier.BASIC);
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.moveTo(Vec3.atCenterOf(tank.getBlockPos().above()));
        player.setGameMode(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.WATER_BUCKET));
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(tank.getBlockPos()), Direction.UP, tank.getBlockPos(), false);
        tank.getBlockState().useItemOn(player.getMainHandItem(), helper.getLevel(), player, InteractionHand.MAIN_HAND, hit);
        helper.assertValueEqual(fluid(helper, A, Fluids.WATER), 1_000L, "o balde esvaziou no Tanque");
        helper.assertTrue(player.getMainHandItem().is(Items.BUCKET), "balde vazio na mão: " + player.getMainHandItem());
        tank.getBlockState().useItemOn(player.getMainHandItem(), helper.getLevel(), player, InteractionHand.MAIN_HAND, hit);
        helper.assertValueEqual(fluid(helper, A, Fluids.WATER), 0L, "o balde encheu");
        helper.assertTrue(player.getMainHandItem().is(Items.WATER_BUCKET), "balde de água na mão: " + player.getMainHandItem());
        helper.succeed();
    }

    /** Filtro de entrada no Tanque: só a lava entra, por qualquer caminho. */
    @GameTest(template = "empty")
    public static void tankInputFilter(GameTestHelper helper) {
        StorageTankBlockEntity tank = storageTank(helper, A, RouterTier.BASIC);
        tank.setFilter(new Filter(Filter.ListMode.WHITELIST, false,
                List.of(new FilterEntry.FluidEntry(new FluidStack(Fluids.LAVA, 1), 0))));
        helper.assertValueEqual(tank.handler().fill(new FluidStack(Fluids.WATER, 1_000), FluidAction.EXECUTE), 0, "água recusada");
        helper.assertValueEqual(tank.handler().fill(new FluidStack(Fluids.LAVA, 1_000), FluidAction.EXECUTE), 1_000, "lava aceita");
        helper.succeed();
    }

    /**
     * Tanque → Tanque no Ultimate pelo roteador: 30 bilhões de mB passam em poucos ticks. Pela API de
     * fluido do NeoForge ({@code int}) seriam no máximo 2,1 bilhões por tick, ou 15 ticks; entre
     * Tanques o roteador usa o {@code BulkFluids}, em {@code long}. Sem fluido criado nem perdido.
     */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void tankToTankMovesBeyondInt(GameTestHelper helper) {
        UUID network = newNetwork(helper, "teste-tanque-tanque");
        long amount = 30_000_000_000L;
        storageTank(helper, A, RouterTier.ULTIMATE).storage().insert(new FluidStack(Fluids.WATER, 1), amount, false);
        storageTank(helper, B, RouterTier.ULTIMATE);
        RouterBlockEntity source = router(helper, A, RouterTier.ULTIMATE, network, ResourceType.FLUID, PortMode.EXTRACT);
        RouterBlockEntity target = router(helper, B, RouterTier.ULTIMATE, network, ResourceType.FLUID, PortMode.INSERT);
        long[] started = new long[1];
        helper.onEachTick(() -> helper.assertValueEqual(fluid(helper, A, Fluids.WATER) + fluid(helper, B, Fluids.WATER),
                amount, "água"));
        helper.startSequence()
                .thenWaitUntil(() -> waitRegistered(helper, source, target))
                .thenExecute(() -> started[0] = helper.getTick())
                .thenWaitUntil(() -> helper.assertValueEqual(fluid(helper, B, Fluids.WATER), amount, "água no destino"))
                .thenExecute(() -> helper.assertTrue(helper.getTick() - started[0] <= 10,
                        "levou " + (helper.getTick() - started[0]) + " ticks: ainda no teto do int"))
                .thenSucceed();
    }

    /** Bateria: capacidade do tier e a visão {@code IEnergyStorage} comum. */
    @GameTest(template = "empty")
    public static void batteryStoresEnergy(GameTestHelper helper) {
        StorageBatteryBlockEntity battery = storageBattery(helper, A, RouterTier.BASIC);
        long capacity = Config.storageCapacity(StorageKind.BATTERY, RouterTier.BASIC);
        helper.assertValueEqual(battery.store().insert(capacity + 500, false), capacity, "cheia no Básico");
        helper.assertValueEqual(battery.handler().receiveEnergy(10, true), 0, "cheia não recebe");
        helper.assertValueEqual(battery.handler().extractEnergy(1_000, false), 1_000, "extrai pela visão comum");
        helper.assertValueEqual(battery.handler().getEnergyStored(), (int) (capacity - 1_000), "guardado");
        helper.assertValueEqual(battery.signal(), 14, "comparador quase cheio");
        helper.succeed();
    }

    /**
     * Bateria → Bateria no Ultimate pelo roteador: 50 bilhões de FE passam em poucos ticks. Pela API
     * de energia do NeoForge ({@code int}) seriam no máximo 2,1 bilhões por tick, ou 24 ticks; entre
     * Baterias o roteador usa o {@code BulkEnergy}, em {@code long}. Sem energia criada nem perdida.
     */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void batteryToBatteryMovesBeyondInt(GameTestHelper helper) {
        UUID network = newNetwork(helper, "teste-bateria-bateria");
        long amount = 50_000_000_000L;
        storageBattery(helper, A, RouterTier.ULTIMATE).store().insert(amount, false);
        storageBattery(helper, B, RouterTier.ULTIMATE);
        RouterBlockEntity source = router(helper, A, RouterTier.ULTIMATE, network, ResourceType.ENERGY, PortMode.EXTRACT);
        RouterBlockEntity target = router(helper, B, RouterTier.ULTIMATE, network, ResourceType.ENERGY, PortMode.INSERT);
        long[] started = new long[1];
        helper.onEachTick(() -> helper.assertValueEqual(energy(helper, A) + energy(helper, B), amount, "energia"));
        helper.startSequence()
                .thenWaitUntil(() -> waitRegistered(helper, source, target))
                .thenExecute(() -> started[0] = helper.getTick())
                .thenWaitUntil(() -> helper.assertValueEqual(energy(helper, B), amount, "energia no destino"))
                .thenExecute(() -> helper.assertTrue(helper.getTick() - started[0] <= 10,
                        "levou " + (helper.getTick() - started[0]) + " ticks: ainda no teto do int"))
                .thenSucceed();
    }

    /** Tanque e Bateria quebrados no criativo também viram item com o conteúdo, e o conteúdo volta ao colocar. */
    @GameTest(template = "empty")
    @SuppressWarnings("removal")
    public static void tankAndBatteryKeepContentsWhenBroken(GameTestHelper helper) {
        storageTank(helper, A, RouterTier.ELITE).storage().insert(new FluidStack(Fluids.LAVA, 1), 7_654_321L, false);
        storageBattery(helper, B, RouterTier.ELITE).store().insert(9_876_543L, false);
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setGameMode(GameType.CREATIVE);
        for (BlockPos pos : List.of(A, B)) {
            player.moveTo(Vec3.atCenterOf(helper.absolutePos(pos).above()));
            player.gameMode.destroyBlock(helper.absolutePos(pos));
        }
        ItemStack tank = pickUp(helper, A, StorageKind.TANK, 7_654_321L);
        ItemStack battery = pickUp(helper, B, StorageKind.BATTERY, 9_876_543L);
        place(helper, tank, A, StorageKind.TANK);
        place(helper, battery, B, StorageKind.BATTERY);
        helper.assertValueEqual(fluid(helper, A, Fluids.LAVA), 7_654_321L, "a lava voltou");
        helper.assertValueEqual(energy(helper, B), 9_876_543L, "a energia voltou");
        helper.succeed();
    }

    /** Tanque de Source: guarda em long até a capacidade, e o nível do bloco acompanha o conteúdo. */
    @GameTest(template = "empty")
    public static void sourceTankStoresAndShowsLevel(GameTestHelper helper) {
        StorageSourceTankBlockEntity tank = storageSourceTank(helper, A, RouterTier.BASIC);
        long capacity = Config.storageCapacity(StorageKind.SOURCE_TANK, RouterTier.BASIC);
        helper.assertValueEqual(capacity, 160_000L, "capacidade do Básico");
        helper.assertValueEqual(tank.store().insert(80_000, false), 80_000L, "metade");
        helper.assertValueEqual(helper.getBlockState(A).getValue(StorageSourceTankBlock.FILL), 5, "nível na metade");
        helper.assertValueEqual(tank.store().insert(999_999, false), 80_000L, "até a capacidade");
        helper.assertValueEqual(helper.getBlockState(A).getValue(StorageSourceTankBlock.FILL), 10, "cheio");
        helper.assertValueEqual(tank.signal(), 15, "comparador cheio");
        tank.store().extract(160_000, false);
        helper.assertValueEqual(helper.getBlockState(A).getValue(StorageSourceTankBlock.FILL), 0, "vazio");
        helper.succeed();
    }

    /** Upgrade de tier com Source dentro: o conteúdo fica e o nível é recalculado pela capacidade nova. */
    @GameTest(template = "empty")
    public static void sourceTankUpgradeKeepsSourceAndRefreshesLevel(GameTestHelper helper) {
        StorageSourceTankBlockEntity tank = storageSourceTank(helper, A, RouterTier.BASIC);
        tank.store().insert(160_000, false);
        helper.assertTrue(StorageBlock.tryUpgrade(helper.getLevel(), helper.absolutePos(A), RouterTier.ADVANCED), "upgrade");
        StorageSourceTankBlockEntity upgraded = helper.getBlockEntity(A);
        helper.assertValueEqual(upgraded.store().stored(), 160_000L, "Source depois do upgrade");
        helper.assertValueEqual(helper.getBlockState(A).getValue(StorageSourceTankBlock.FILL), 1, "nível com a capacidade nova");
        helper.succeed();
    }

    /** Tanque de Source Ultimate (sem limite) quebrado cheio: o item leva a Source e o tier, e o nível volta ao colocar. */
    @GameTest(template = "empty")
    @SuppressWarnings("removal")
    public static void sourceTankKeepsSourceWhenBroken(GameTestHelper helper) {
        long source = 3_000_000_001L;
        storageSourceTank(helper, C, RouterTier.ULTIMATE).store().insert(source, false);
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setGameMode(GameType.CREATIVE);
        player.moveTo(Vec3.atCenterOf(helper.absolutePos(C).above()));
        player.gameMode.destroyBlock(helper.absolutePos(C));
        ItemStack item = pickUp(helper, C, StorageKind.SOURCE_TANK, source);
        helper.assertValueEqual(StorageBlockItem.tierOf(item), RouterTier.ULTIMATE, "tier no item");
        place(helper, item, C, StorageKind.SOURCE_TANK);
        StorageSourceTankBlockEntity placed = helper.getBlockEntity(C);
        helper.assertValueEqual(placed.store().stored(), source, "a Source voltou");
        helper.assertValueEqual(helper.getBlockState(C).getValue(StorageSourceTankBlock.FILL), 10, "nível do bloco recolocado");
        helper.succeed();
    }

    /**
     * Bateria quebrada por um jogador de cada jeito (sobrevivência com picareta, sem picareta e no
     * criativo): o item leva a energia e colocá-lo a devolve.
     */
    @GameTest(template = "empty")
    @SuppressWarnings("removal")
    public static void batteryKeepsEnergyHoweverBroken(GameTestHelper helper) {
        Object[][] cases = {
                {GameType.SURVIVAL, new ItemStack(Items.DIAMOND_PICKAXE), 3_000_000_001L},
                {GameType.SURVIVAL, ItemStack.EMPTY, 3_000_000_002L},
                {GameType.CREATIVE, ItemStack.EMPTY, 3_000_000_003L},
        };
        for (Object[] c : cases) {
            long energy = (long) c[2];
            storageBattery(helper, A, RouterTier.ELITE).store().insert(energy, false);
            BlockPos absolute = helper.absolutePos(A);
            ServerPlayer player = helper.makeMockServerPlayerInLevel();
            player.moveTo(Vec3.atCenterOf(absolute.above()));
            player.setGameMode((GameType) c[0]);
            player.setItemInHand(InteractionHand.MAIN_HAND, (ItemStack) c[1]);
            player.gameMode.destroyBlock(absolute);
            helper.assertBlockNotPresent(ModBlocks.STORAGE.get(StorageKind.BATTERY).get(), A);
            ItemStack item = pickUp(helper, A, StorageKind.BATTERY, energy);
            helper.assertValueEqual(StorageBlockItem.tierOf(item), RouterTier.ELITE, "tier no item (" + c[0] + ")");
            place(helper, item, A, StorageKind.BATTERY);
            helper.assertValueEqual(energy(helper, A), energy, "energia de volta (" + c[0] + ", " + c[1] + ")");
            helper.setBlock(A, Blocks.AIR);
        }
        helper.succeed();
    }

    /** Tira do chão o item do armazenamento com o total dado (perto da posição relativa). */
    private static ItemStack pickUp(GameTestHelper helper, BlockPos pos, StorageKind kind, long total) {
        List<ItemEntity> drops = helper.getLevel().getEntitiesOfClass(ItemEntity.class,
                new AABB(helper.absolutePos(pos)).inflate(1.5), entity -> entity.getItem().is(ModItems.STORAGE.get(kind).get()));
        helper.assertValueEqual(drops.size(), 1, kind + " no chão");
        StorageContents contents = drops.get(0).getItem().get(ModDataComponents.STORAGE_CONTENTS.get());
        helper.assertTrue(contents != null && contents.total() == total, kind + " leva o conteúdo: " + contents);
        ItemStack stack = drops.get(0).getItem().copy();
        drops.get(0).discard();
        return stack;
    }

    /** Tela do Tanque: balde vazio no cursor enche com o fluido clicado; cheio, esvazia; Shift + clique esvazia do inventário. */
    @GameTest(template = "empty")
    @SuppressWarnings({"removal", "unchecked"})
    public static void tankScreenFillsAndEmptiesBuckets(GameTestHelper helper) {
        StorageTankBlockEntity tank = storageTank(helper, A, RouterTier.BASIC);
        tank.storage().insert(new FluidStack(Fluids.WATER, 1), 5_000, false);
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.moveTo(Vec3.atCenterOf(tank.getBlockPos().above()));
        player.getInventory().clearContent();
        StorageListMenu<FluidStack> menu = (StorageListMenu<FluidStack>) (StorageListMenu<?>)
                new StorageListMenu<>(CONTAINER_ID, player.getInventory(), tank);
        player.containerMenu = menu;
        FluidStack water = new FluidStack(Fluids.WATER, 1);
        menu.setCarried(new ItemStack(Items.BUCKET));
        helper.assertTrue(StorageListMenu.handle(player, new StorageActionPayload(CONTAINER_ID, StorageKind.TANK,
                StorageActionPayload.Action.TAKE_STACK, Optional.of(water))), "encher o balde");
        helper.assertTrue(menu.getCarried().is(Items.WATER_BUCKET), "balde de água no cursor: " + menu.getCarried());
        helper.assertValueEqual(tank.storage().count(water), 4_000L, "saiu um balde");
        helper.assertTrue(StorageListMenu.handle(player, new StorageActionPayload(CONTAINER_ID, StorageKind.TANK,
                StorageActionPayload.Action.INSERT_CARRIED)), "esvaziar o balde");
        helper.assertTrue(menu.getCarried().is(Items.BUCKET), "balde vazio no cursor");
        helper.assertValueEqual(tank.storage().count(water), 5_000L, "voltou o balde");
        menu.setCarried(ItemStack.EMPTY);
        player.getInventory().setItem(9, new ItemStack(Items.WATER_BUCKET));
        menu.quickMoveStack(player, 0);
        helper.assertValueEqual(tank.storage().count(water), 6_000L, "Shift + clique esvaziou o balde do inventário");
        helper.assertTrue(player.getInventory().countItem(Items.BUCKET) == 1, "o balde vazio voltou ao inventário");
        helper.succeed();
    }

    /**
     * Coloca o item do Baú em {@code pos} (relativa) como o {@code BlockItem} faz, clicando numa
     * pedra embaixo. Sem jogador, para a colisão com ele não impedir.
     */
    private static void place(GameTestHelper helper, ItemStack stack, BlockPos pos) {
        place(helper, stack, pos, StorageKind.CHEST);
    }

    private static void place(GameTestHelper helper, ItemStack stack, BlockPos pos, StorageKind kind) {
        helper.setBlock(pos.below(), Blocks.STONE);
        BlockPos floor = helper.absolutePos(pos).below();
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(floor).add(0, 0.5, 0), Direction.UP, floor, false);
        ((BlockItem) stack.getItem()).place(new BlockPlaceContext(helper.getLevel(), null, InteractionHand.MAIN_HAND, stack, hit));
        helper.assertBlockPresent(ModBlocks.STORAGE.get(kind).get(), pos);
    }

    private StorageGameTests() {
    }
}
