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
import io.github.matheusanbs.wirelessautomate.storage.SourceTankLevels;
import io.github.matheusanbs.wirelessautomate.storage.StorageSourceTankBlock;
import io.github.matheusanbs.wirelessautomate.storage.StorageSourceTankBlockEntity;
import io.github.matheusanbs.wirelessautomate.storage.StorageTankBlockEntity;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler.FluidAction;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
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
        RouterBlockEntity router = GameTestCompat.getBlockEntity(helper, routerPos);
        router.setNetworkId(network);
        router.setNetworkId(type, network);
        router.setMode(type, Direction.UP, mode);
        return router;
    }

    private static StorageChestBlockEntity storageChest(GameTestHelper helper, BlockPos pos, RouterTier tier) {
        helper.setBlock(pos, ModBlocks.STORAGE_CHEST.get().defaultBlockState().setValue(RouterBlock.TIER, tier));
        return GameTestCompat.getBlockEntity(helper, pos);
    }

    private static ItemStorage storage(GameTestHelper helper, BlockPos pos) {
        StorageChestBlockEntity chest = GameTestCompat.getBlockEntity(helper, pos);
        return chest.storage();
    }

    private static long stored(GameTestHelper helper, BlockPos pos, Item item) {
        return storage(helper, pos).count(new ItemStack(item));
    }

    private static int vanilla(GameTestHelper helper, BlockPos pos, Item item) {
        ChestBlockEntity chest = GameTestCompat.getBlockEntity(helper, pos);
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
    /**
     * A colisão e a seleção de cada armazenamento ({@code StorageShapes}) abraçam o modelo: dentro do bloco,
     * encostadas no chão e com o topo a pelo menos 15/16 (o jogador anda por cima), no Tanque de Source em
     * todos os níveis; e, como os blocos parciais do vanilla, a forma não é um cubo cheio.
     */
    @GameTest(template = "empty")
    public static void storageShapesHugTheModel(GameTestHelper helper) {
        for (StorageKind kind : StorageKind.values()) {
            BlockState base = ModBlocks.STORAGE.get(kind).get().defaultBlockState();
            List<BlockState> states = kind == StorageKind.SOURCE_TANK
                    ? java.util.stream.IntStream.rangeClosed(0, 10).mapToObj(f -> base.setValue(StorageSourceTankBlock.FILL, f)).toList()
                    : List.of(base, base.setValue(RouterBlock.TIER, RouterTier.ULTIMATE));
            for (BlockState state : states) {
                VoxelShape shape = state.getCollisionShape(helper.getLevel(), helper.absolutePos(new BlockPos(1, 1, 1)));
                helper.assertFalse(shape.isEmpty(), kind + " sem colisão");
                AABB bounds = shape.bounds();
                helper.assertTrue(bounds.minX >= 0 && bounds.minY >= 0 && bounds.minZ >= 0
                        && bounds.maxX <= 1 && bounds.maxY <= 1 && bounds.maxZ <= 1, kind + " fora do bloco: " + bounds);
                helper.assertTrue(bounds.minY == 0, kind + " não encosta no chão");
                helper.assertTrue(bounds.maxY >= 15 / 16.0, kind + " sem topo para pisar: " + bounds.maxY);
                helper.assertFalse(state.isCollisionShapeFullBlock(helper.getLevel(), helper.absolutePos(new BlockPos(1, 1, 1))),
                        kind + " é um cubo cheio");
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void storesByTypeAndCount(GameTestHelper helper) {
        ItemStorage storage = storageChest(helper, A, RouterTier.ULTIMATE).storage();
        GameTestCompat.assertValueEqual(helper, storage.insert(new ItemStack(Items.COBBLESTONE), 12_000_000L, false), 12_000_000L, "guardou");
        GameTestCompat.assertValueEqual(helper, storage.insert(new ItemStack(Items.DIAMOND, 7), 5, false), 5L, "diamantes");
        GameTestCompat.assertValueEqual(helper, storage.types(), 2, "tipos");
        GameTestCompat.assertValueEqual(helper, storage.total(), 12_000_005L, "total");
        GameTestCompat.assertValueEqual(helper, storage.extract(new ItemStack(Items.DIAMOND), 9, false), 5L, "tirou só o que havia");
        GameTestCompat.assertValueEqual(helper, storage.types(), 1, "tipo zerado sai da lista");
        GameTestCompat.assertValueEqual(helper, storage.extract(new ItemStack(Items.DIRT), 1, false), 0L, "tipo ausente");
        helper.succeed();
    }

    /** O tier limita o total; o Cartão de Upgrade sobe a capacidade sem perder o conteúdo. */
    @GameTest(template = "empty")
    public static void capacityFollowsTierAndUpgrade(GameTestHelper helper) {
        StorageChestBlockEntity chest = storageChest(helper, A, RouterTier.BASIC);
        long basic = Config.chestCapacity(RouterTier.BASIC);
        ItemStorage storage = chest.storage();
        GameTestCompat.assertValueEqual(helper, storage.insert(new ItemStack(Items.COBBLESTONE), basic + 100, false), basic, "cheio no Básico");
        GameTestCompat.assertValueEqual(helper, storage.insert(new ItemStack(Items.DIRT), 1, true), 0L, "cheio aceita nada");
        helper.assertTrue(StorageBlock.tryUpgrade(helper.getLevel(), helper.absolutePos(A), RouterTier.ADVANCED),
                "upgrade para Avançado");
        helper.assertTrue(StorageBlock.tryUpgrade(helper.getLevel(), helper.absolutePos(A), RouterTier.ULTIMATE),
                "pula para o Ultimate");
        StorageChestBlockEntity upgraded = GameTestCompat.getBlockEntity(helper, A);
        helper.assertTrue(upgraded == chest, "o block entity é o mesmo");
        GameTestCompat.assertValueEqual(helper, upgraded.storage().total(), basic, "conteúdo mantido");
        GameTestCompat.assertValueEqual(helper, upgraded.storage().insert(new ItemStack(Items.DIRT), 100, false), 100L, "cabe mais");
        helper.succeed();
    }

    /** Visto como inventário comum: um slot por tipo mais um vazio, e no máximo uma pilha por extração. */
    @GameTest(template = "empty")
    public static void itemHandlerViewFollowsTheContract(GameTestHelper helper) {
        StorageChestBlockEntity chest = storageChest(helper, A, RouterTier.ULTIMATE);
        chest.storage().insert(new ItemStack(Items.COBBLESTONE), 1_000, false);
        ItemStorageHandler handler = chest.handler();
        GameTestCompat.assertValueEqual(helper, handler.getSlots(), 2, "um tipo + o vazio");
        GameTestCompat.assertValueEqual(helper, handler.getStackInSlot(0).getCount(), 1_000, "quantidade real");
        GameTestCompat.assertValueEqual(helper, handler.extractItem(0, 500, false).getCount(), 64, "uma pilha por extração");
        GameTestCompat.assertValueEqual(helper, handler.insertItem(0, new ItemStack(Items.DIAMOND, 3), false).getCount(), 3,
                "slot de outro tipo recusa");
        helper.assertTrue(handler.insertItem(1, new ItemStack(Items.DIAMOND, 3), false).isEmpty(), "o vazio aceita");
        GameTestCompat.assertValueEqual(helper, chest.storage().total(), 1_000L - 64 + 3, "total");
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
            GameTestCompat.assertValueEqual(helper, stored(helper, A, Items.COBBLESTONE) + stored(helper, B, Items.COBBLESTONE),
                    12_000_000L, "pedregulho");
            GameTestCompat.assertValueEqual(helper, stored(helper, A, Items.DIAMOND) + stored(helper, B, Items.DIAMOND), 1_000L, "diamantes");
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
                    GameTestCompat.assertValueEqual(helper, stored(helper, B, Items.COBBLESTONE), 12_000_000L, "pedregulho no destino");
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

        helper.onEachTick(() -> GameTestCompat.assertValueEqual(helper, 
                stored(helper, A, Items.COBBLESTONE) + vanilla(helper, B, Items.COBBLESTONE), 5_000L, "pedregulho"));
        helper.startSequence()
                .thenWaitUntil(() -> waitRegistered(helper, source, target))
                .thenWaitUntil(() -> GameTestCompat.assertValueEqual(helper, vanilla(helper, B, Items.COBBLESTONE), capacity, "vanilla cheio"))
                .thenIdle(5)
                .thenExecute(() -> GameTestCompat.assertValueEqual(helper, stored(helper, A, Items.COBBLESTONE), 5_000L - capacity, "resto no Baú"))
                .thenSucceed();
    }

    /**
     * Baú → slot de pilha grande (como um barril com upgrade de pilha ou uma gaveta, limite do slot
     * acima de 99): o roteador entrega o que cabe numa chamada só, em vez de uma pilha de 64 por vez.
     * Com 64 por chamada seriam 15.625 inserções.
     */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void chestFillsABigSlotInFewCalls(GameTestHelper helper) {
        if (!TestMachines.enabled()) {
            helper.succeed();
            return;
        }
        UUID network = newNetwork(helper, "teste-bau-pilha-grande");
        BlockPos machine = helper.absolutePos(B);
        TestMachines.reset(machine);
        storageChest(helper, A, RouterTier.ULTIMATE).storage().insert(new ItemStack(Items.COBBLESTONE), 1_000_000L, false);
        helper.setBlock(B, TestMachines.BIG_SLOT.get());
        RouterBlockEntity source = router(helper, A, RouterTier.ULTIMATE, network, PortMode.EXTRACT);
        RouterBlockEntity target = router(helper, B, RouterTier.ULTIMATE, network, PortMode.INSERT);

        helper.onEachTick(() -> GameTestCompat.assertValueEqual(helper, 
                stored(helper, A, Items.COBBLESTONE) + TestMachines.bigSlot(machine).count(), 1_000_000L, "pedregulho"));
        helper.startSequence()
                .thenWaitUntil(() -> waitRegistered(helper, source, target))
                .thenWaitUntil(() -> GameTestCompat.assertValueEqual(helper, TestMachines.bigSlot(machine).count(), 1_000_000, "no slot"))
                .thenExecute(() -> {
                    int inserts = TestMachines.bigSlot(machine).inserts();
                    helper.assertTrue(inserts <= 4, inserts + " inserções: a entrega não passou de uma pilha por chamada");
                    helper.assertTrue(storage(helper, A).isEmpty(), "origem não esvaziou");
                })
                .thenSucceed();
    }

    /**
     * Baú → inventário que declara limite 64 mas não limita o que recebe: nenhuma chamada passa de uma
     * pilha, então nenhum slot passa de 64 (a pilha grande vai só para slots com limite acima de 99).
     */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void chestNeverOverfillsASlotThatTrustsTheCaller(GameTestHelper helper) {
        if (!TestMachines.enabled()) {
            helper.succeed();
            return;
        }
        UUID network = newNetwork(helper, "teste-bau-slot-ingenuo");
        BlockPos machine = helper.absolutePos(B);
        TestMachines.reset(machine);
        storageChest(helper, A, RouterTier.ULTIMATE).storage().insert(new ItemStack(Items.COBBLESTONE), 10_000L, false);
        helper.setBlock(B, TestMachines.NAIVE_SLOTS.get());
        RouterBlockEntity source = router(helper, A, RouterTier.ULTIMATE, network, PortMode.EXTRACT);
        RouterBlockEntity target = router(helper, B, RouterTier.ULTIMATE, network, PortMode.INSERT);
        int capacity = TestMachines.NaiveSlots.SLOTS * 64;

        helper.onEachTick(() -> GameTestCompat.assertValueEqual(helper, 
                stored(helper, A, Items.COBBLESTONE) + TestMachines.naiveSlots(machine).total(), 10_000L, "pedregulho"));
        helper.startSequence()
                .thenWaitUntil(() -> waitRegistered(helper, source, target))
                .thenWaitUntil(() -> GameTestCompat.assertValueEqual(helper, TestMachines.naiveSlots(machine).total(), capacity, "cheio"))
                .thenIdle(5)
                .thenExecute(() -> {
                    TestMachines.NaiveSlots slots = TestMachines.naiveSlots(machine);
                    helper.assertTrue(slots.largestCall() <= 64, "chamada com " + slots.largestCall() + " itens");
                    GameTestCompat.assertValueEqual(helper, slots.largestSlot(), 64, "maior slot");
                    GameTestCompat.assertValueEqual(helper, stored(helper, A, Items.COBBLESTONE), 10_000L - capacity, "resto no Baú");
                })
                .thenSucceed();
    }

    /** Baú vanilla → Baú: tudo entra, por tipo, sem varrer slots do destino. */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void vanillaChestFillsAChest(GameTestHelper helper) {
        UUID network = newNetwork(helper, "teste-vanilla-bau");
        helper.setBlock(A, Blocks.CHEST);
        ChestBlockEntity chest = GameTestCompat.getBlockEntity(helper, A);
        chest.setItem(0, new ItemStack(Items.DIAMOND, 10));
        chest.setItem(3, new ItemStack(Items.COBBLESTONE, 64));
        chest.setItem(7, new ItemStack(Items.COBBLESTONE, 30));
        storageChest(helper, B, RouterTier.BASIC);
        RouterBlockEntity source = router(helper, A, RouterTier.ELITE, network, PortMode.EXTRACT);
        RouterBlockEntity target = router(helper, B, RouterTier.ELITE, network, PortMode.INSERT);

        helper.onEachTick(() -> GameTestCompat.assertValueEqual(helper, 
                vanilla(helper, A, Items.COBBLESTONE) + stored(helper, B, Items.COBBLESTONE), 94L, "pedregulho"));
        helper.startSequence()
                .thenWaitUntil(() -> waitRegistered(helper, source, target))
                .thenWaitUntil(() -> {
                    GameTestCompat.assertValueEqual(helper, stored(helper, B, Items.DIAMOND), 10L, "diamantes no Baú");
                    GameTestCompat.assertValueEqual(helper, stored(helper, B, Items.COBBLESTONE), 94L, "pedregulho no Baú");
                    GameTestCompat.assertValueEqual(helper, storage(helper, B).types(), 2, "dois tipos");
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
        StorageChestBlockEntity original = GameTestCompat.getBlockEntity(helper, A);
        original.setFilter(filter);
        BlockPos absolute = helper.absolutePos(A);
        helper.getLevel().destroyBlock(absolute, true);

        List<ItemEntity> drops = helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(absolute).inflate(2),
                entity -> entity.getItem().is(ModBlocks.STORAGE_CHEST.get().asItem()));
        GameTestCompat.assertValueEqual(helper, drops.size(), 1, "um Baú no chão");
        ItemStack dropped = drops.get(0).getItem().copy();
        drops.get(0).discard();
        StorageContents contents = ModDataComponents.STORAGE_CONTENTS.get(dropped);
        helper.assertTrue(contents != null, "o item leva a referência");
        GameTestCompat.assertValueEqual(helper, contents.total(), 1_000_005L, "resumo do total");
        GameTestCompat.assertValueEqual(helper, contents.types(), 2, "resumo dos tipos");
        GameTestCompat.assertValueEqual(helper, StorageBlockItem.tierOf(dropped), RouterTier.ELITE, "tier no item");

        ItemStack copy = dropped.copy();
        place(helper, dropped, A);
        place(helper, copy, B);
        GameTestCompat.assertValueEqual(helper, storage(helper, A).total(), 1_000_005L, "o conteúdo voltou");
        GameTestCompat.assertValueEqual(helper, stored(helper, A, Items.DIAMOND), 5L, "diamantes de volta");
        helper.assertTrue(storage(helper, B).isEmpty(), "a cópia do item nasce vazia");
        StorageChestBlockEntity placed = GameTestCompat.getBlockEntity(helper, A);
        GameTestCompat.assertValueEqual(helper, placed.filter(), filter, "o filtro voltou com o bloco");
        helper.succeed();
    }

    /** Filtro de entrada: só entra o que ele aceita, e o estoque de uma entrada é "guardar até N". */
    @GameTest(template = "empty")
    public static void inputFilterLimitsWhatEnters(GameTestHelper helper) {
        StorageChestBlockEntity chest = storageChest(helper, A, RouterTier.ULTIMATE);
        chest.setFilter(new Filter(Filter.ListMode.WHITELIST, false,
                List.of(new FilterEntry.ItemEntry(new ItemStack(Items.COBBLESTONE), 100))));
        ItemStorage storage = chest.storage();
        GameTestCompat.assertValueEqual(helper, storage.insert(new ItemStack(Items.DIRT), 10, false), 0L, "terra recusada");
        GameTestCompat.assertValueEqual(helper, storage.insert(new ItemStack(Items.COBBLESTONE), 64, false), 64L, "primeira pilha");
        GameTestCompat.assertValueEqual(helper, storage.insert(new ItemStack(Items.COBBLESTONE), 64, false), 36L, "só até 100");
        helper.assertTrue(chest.handler().insertItem(1, new ItemStack(Items.DIRT), false).getCount() == 1,
                "o funil também passa pelo filtro");
        chest.setFilter(Filter.EMPTY);
        GameTestCompat.assertValueEqual(helper, storage.insert(new ItemStack(Items.DIRT), 10, false), 10L, "sem filtro, tudo entra");
        helper.succeed();
    }

    /** Roteador para um Baú com filtro: o que o filtro recusa fica na origem. */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void routerRespectsTheInputFilter(GameTestHelper helper) {
        UUID network = newNetwork(helper, "teste-bau-filtro");
        helper.setBlock(A, Blocks.CHEST);
        ChestBlockEntity source = GameTestCompat.getBlockEntity(helper, A);
        source.setItem(0, new ItemStack(Items.DIAMOND, 10));
        source.setItem(1, new ItemStack(Items.DIRT, 20));
        storageChest(helper, B, RouterTier.BASIC).setFilter(new Filter(Filter.ListMode.WHITELIST, false,
                List.of(new FilterEntry.ItemEntry(new ItemStack(Items.DIAMOND), 0))));
        RouterBlockEntity from = router(helper, A, RouterTier.ELITE, network, PortMode.EXTRACT);
        RouterBlockEntity to = router(helper, B, RouterTier.ELITE, network, PortMode.INSERT);
        helper.startSequence()
                .thenWaitUntil(() -> waitRegistered(helper, from, to))
                .thenWaitUntil(() -> GameTestCompat.assertValueEqual(helper, stored(helper, B, Items.DIAMOND), 10L, "diamantes no Baú"))
                .thenIdle(10)
                .thenExecute(() -> {
                    GameTestCompat.assertValueEqual(helper, stored(helper, B, Items.DIRT), 0L, "terra recusada");
                    GameTestCompat.assertValueEqual(helper, vanilla(helper, A, Items.DIRT), 20, "terra na origem");
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
        GameTestCompat.assertValueEqual(helper, menu.getCarried().getCount(), 64, "pilha no cursor");
        helper.assertTrue(act(player, menu, StorageActionPayload.Action.INSERT_CARRIED_ONE, ItemStack.EMPTY), "devolver um");
        GameTestCompat.assertValueEqual(helper, menu.getCarried().getCount(), 63, "um a menos no cursor");
        helper.assertTrue(act(player, menu, StorageActionPayload.Action.INSERT_CARRIED, ItemStack.EMPTY), "devolver tudo");
        helper.assertTrue(menu.getCarried().isEmpty(), "cursor vazio");
        GameTestCompat.assertValueEqual(helper, storage.total(), 1_000L, "tudo de volta");
        helper.assertTrue(act(player, menu, StorageActionPayload.Action.TAKE_HALF, cobble), "meia pilha");
        GameTestCompat.assertValueEqual(helper, menu.getCarried().getCount(), 32, "meia pilha no cursor");
        helper.assertFalse(act(player, menu, StorageActionPayload.Action.TAKE_STACK, new ItemStack(Items.DIAMOND)),
                "tipo que não existe");
        menu.setCarried(ItemStack.EMPTY);
        helper.assertTrue(act(player, menu, StorageActionPayload.Action.TAKE_TO_INVENTORY, cobble), "para o inventário");
        int slot = player.getInventory().findSlotMatchingItem(cobble);
        helper.assertTrue(slot >= 0, "pedregulho no inventário");
        GameTestCompat.assertValueEqual(helper, storage.total(), 1_000L - 32 - 64, "saiu do Baú");
        int menuSlot = slot < 9 ? 27 + slot : slot - 9;
        menu.quickMoveStack(player, menuSlot);
        helper.assertTrue(player.getInventory().getItem(slot).isEmpty(), "Shift + clique guardou");
        GameTestCompat.assertValueEqual(helper, storage.total(), 1_000L - 32, "de volta ao Baú");
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
        GameTestCompat.assertValueEqual(helper, first.changes().size(), 2, "dois tipos");
        helper.assertTrue(menu.poll() == null, "nada mudou, nada vai");
        storage.extract(new ItemStack(Items.DIAMOND), 5, false);
        helper.assertTrue(menu.poll() == null, "espera o intervalo");
        helper.startSequence()
                .thenIdle(StorageListMenu.SYNC_INTERVAL + 1)
                .thenExecute(() -> {
                    StorageListMenu.Sync<ItemStack> next = menu.poll();
                    helper.assertTrue(next != null && !next.reset(), "a diferença vai depois do intervalo");
                    GameTestCompat.assertValueEqual(helper, next.changes().size(), 1, "só o tipo que mudou");
                    GameTestCompat.assertValueEqual(helper, next.changes().get(0).count(), 0L, "diamante saiu");
                    GameTestCompat.assertValueEqual(helper, next.header().total(), 500L, "total novo");
                })
                .thenSucceed();
    }

    /** Baú + Cartão de Upgrade de um tier acima na bancada: sobe o tier e mantém a referência ao conteúdo. */
    @GameTest(template = "empty")
    public static void chestUpgradeRecipe(GameTestHelper helper) {
        ItemStack chest = StorageBlockItem.withTier(ModItems.STORAGE_CHEST.get(), RouterTier.BASIC);
        StorageContents contents = new StorageContents(UUID.randomUUID(), 3, 12_345L);
        ModDataComponents.STORAGE_CONTENTS.set(chest, contents);
        CraftingContainer input = GameTestCompat.craftingInput(2, 1,
                List.of(chest, new ItemStack(ModItems.TIER_CORES.get(RouterTier.ADVANCED).get())));
        ItemStack out = helper.getLevel().getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, helper.getLevel())
                .orElseThrow(() -> new GameTestAssertException("sem receita de upgrade do Baú"))
                .assemble(input, helper.getLevel().registryAccess());
        helper.assertTrue(out.is(ModItems.STORAGE_CHEST.get()), "resultado: " + out);
        GameTestCompat.assertValueEqual(helper, StorageBlockItem.tierOf(out), RouterTier.ADVANCED, "tier");
        GameTestCompat.assertValueEqual(helper, ModDataComponents.STORAGE_CONTENTS.get(out), contents, "conteúdo mantido");
        CraftingContainer skip = GameTestCompat.craftingInput(2, 1,
                List.of(chest, new ItemStack(ModItems.TIER_CORES.get(RouterTier.ELITE).get())));
        helper.assertTrue(helper.getLevel().getRecipeManager()
                .getRecipeFor(RecipeType.CRAFTING, skip, helper.getLevel()).isPresent(), "pula tier");
        CraftingContainer down = GameTestCompat.craftingInput(2, 1, List.of(StorageBlockItem.withTier(ModItems.STORAGE_CHEST.get(),
                RouterTier.ELITE), new ItemStack(ModItems.TIER_CORES.get(RouterTier.ADVANCED).get())));
        helper.assertTrue(helper.getLevel().getRecipeManager()
                .getRecipeFor(RecipeType.CRAFTING, down, helper.getLevel()).isEmpty(), "desceu tier");
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
        ServerPlayer player = GameTestCompat.makeMockServerPlayerInLevel(helper);
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
        StorageContents contents = ModDataComponents.STORAGE_CONTENTS.get(given);
        helper.assertTrue(contents != null && contents.id().equals(orphan) && contents.total() == 777,
                "o item aponta para o conteúdo: " + given);
        GameTestCompat.assertValueEqual(helper, StorageBlockItem.tierOf(given), RouterTier.ELITE, "o tier que se perdeu");
        place(helper, given, B);
        GameTestCompat.assertValueEqual(helper, stored(helper, B, Items.DIAMOND), 777L, "os diamantes voltaram");
        helper.succeed();
    }

    /**
     * Conteúdos guardados antes de o tier ser gravado: o {@code recover} escolhe o menor tier que
     * comporta tudo (pela capacidade da config), para nada ficar além do limite.
     */
    @GameTest(template = "empty")
    public static void recoverGuessesTheTierOfOldContents(GameTestHelper helper) {
        long basic = Config.storageCapacity(StorageKind.CHEST, RouterTier.BASIC);
        GameTestCompat.assertValueEqual(helper, StorageCommand.tierFor(new StorageSavedData.Stored(StorageKind.CHEST, null, new ListTag()), 10),
                RouterTier.BASIC, "pouco cabe no Básico");
        GameTestCompat.assertValueEqual(helper, StorageCommand.tierFor(new StorageSavedData.Stored(StorageKind.CHEST, null, new ListTag()),
                basic + 1), RouterTier.ADVANCED, "um a mais que o Básico vai para o Avançado");
        GameTestCompat.assertValueEqual(helper, StorageCommand.tierFor(new StorageSavedData.Stored(StorageKind.CHEST, RouterTier.ELITE,
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
        ServerPlayer player = GameTestCompat.makeMockServerPlayerInLevel(helper);
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
        GameTestCompat.assertValueEqual(helper, drops.size(), 1, "Baús no chão " + drops.stream()
                .map(e -> String.valueOf(ModDataComponents.STORAGE_CONTENTS.get(e.getItem()))).toList());
        StorageContents contents = ModDataComponents.STORAGE_CONTENTS.get(drops.get(0).getItem());
        helper.assertTrue(contents != null && contents.total() == total, "o Baú no chão leva o conteúdo: " + contents);
        drops.forEach(ItemEntity::discard);
    }

    @SuppressWarnings("removal")
    private static ServerPlayer playerNear(GameTestHelper helper, StorageChestBlockEntity chest) {
        ServerPlayer player = GameTestCompat.makeMockServerPlayerInLevel(helper);
        player.moveTo(Vec3.atCenterOf(chest.getBlockPos().above()));
        return player;
    }

    private static StorageListMenu<ItemStack> openMenu(ServerPlayer player, StorageChestBlockEntity chest) {
        StorageListMenu<ItemStack> menu = new StorageListMenu<>(CONTAINER_ID, player.getInventory(), chest);
        player.containerMenu = menu;
        return menu;
    }

    /** A rodinha (um item por vez, sobre um tipo ou sobre um slot) e o Shift + duplo clique (o máximo que couber). */
    @GameTest(template = "empty")
    public static void wheelAndDoubleClickMoveItems(GameTestHelper helper) {
        StorageChestBlockEntity chest = storageChest(helper, A, RouterTier.BASIC);
        ItemStorage storage = chest.storage();
        storage.insert(new ItemStack(Items.COBBLESTONE), 5_000, false);
        long initial = storage.total();
        helper.assertTrue(initial > 36 * 64, "o Baú básico guarda mais que um inventário: " + initial);
        ServerPlayer player = playerNear(helper, chest);
        player.getInventory().clearContent();
        StorageListMenu<ItemStack> menu = openMenu(player, chest);
        ItemStack cobble = new ItemStack(Items.COBBLESTONE);

        helper.assertTrue(act(player, menu, StorageActionPayload.Action.TAKE_ONE_TO_INVENTORY, cobble), "rodinha: tirar um");
        int inv = player.getInventory().findSlotMatchingItem(cobble);
        helper.assertTrue(inv >= 0 && player.getInventory().getItem(inv).getCount() == 1, "um no inventário");
        GameTestCompat.assertValueEqual(helper, storage.total(), initial - 1, "um a menos no Baú");
        int menuSlot = inv < 9 ? 27 + inv : inv - 9;
        helper.assertTrue(slotAct(player, menu, StorageActionPayload.Action.TAKE_ONE_TO_SLOT, menuSlot), "rodinha: puxar para o slot");
        GameTestCompat.assertValueEqual(helper, player.getInventory().getItem(inv).getCount(), 2, "dois no slot");
        helper.assertTrue(slotAct(player, menu, StorageActionPayload.Action.INSERT_ONE_FROM_SLOT, menuSlot), "rodinha: guardar do slot");
        GameTestCompat.assertValueEqual(helper, player.getInventory().getItem(inv).getCount(), 1, "um no slot");
        helper.assertTrue(act(player, menu, StorageActionPayload.Action.INSERT_ONE_FROM_INVENTORY, cobble), "rodinha: guardar um");
        helper.assertTrue(player.getInventory().getItem(inv).isEmpty(), "slot vazio");
        GameTestCompat.assertValueEqual(helper, storage.total(), initial, "tudo de volta");
        helper.assertFalse(act(player, menu, StorageActionPayload.Action.INSERT_ONE_FROM_INVENTORY, cobble), "nada para guardar");
        helper.assertFalse(slotAct(player, menu, StorageActionPayload.Action.INSERT_ONE_FROM_SLOT, 999), "slot fora do menu");

        // Shift + duplo clique com item no cursor: o inventário enche e o cursor fica como está.
        menu.setCarried(new ItemStack(Items.STICK, 5));
        helper.assertTrue(act(player, menu, StorageActionPayload.Action.TAKE_ALL_TO_INVENTORY, cobble), "Shift + duplo clique");
        GameTestCompat.assertValueEqual(helper, menu.getCarried().getCount(), 5, "cursor como estava");
        GameTestCompat.assertValueEqual(helper, player.getInventory().countItem(Items.COBBLESTONE), 36 * 64, "inventário cheio");
        GameTestCompat.assertValueEqual(helper, storage.total(), initial - 36 * 64, "o resto no Baú");
        helper.assertFalse(act(player, menu, StorageActionPayload.Action.TAKE_ONE_TO_INVENTORY, cobble), "sem espaço");
        helper.succeed();
    }

    private static boolean slotAct(ServerPlayer player, StorageListMenu<ItemStack> menu, StorageActionPayload.Action action, int slot) {
        return StorageListMenu.handle(player, new StorageActionPayload(menu.containerId, StorageKind.CHEST, action,
                Optional.empty(), slot));
    }

    private static boolean act(ServerPlayer player, StorageListMenu<ItemStack> menu, StorageActionPayload.Action action, ItemStack key) {
        return StorageListMenu.handle(player, new StorageActionPayload(menu.containerId, StorageKind.CHEST, action,
                key.isEmpty() ? Optional.empty() : Optional.of(key)));
    }

    // ------------------------------------------------------------------ Tanque e Bateria

    private static StorageTankBlockEntity storageTank(GameTestHelper helper, BlockPos pos, RouterTier tier) {
        helper.setBlock(pos, ModBlocks.STORAGE.get(StorageKind.TANK).get().defaultBlockState().setValue(RouterBlock.TIER, tier));
        return GameTestCompat.getBlockEntity(helper, pos);
    }

    private static StorageBatteryBlockEntity storageBattery(GameTestHelper helper, BlockPos pos, RouterTier tier) {
        helper.setBlock(pos, ModBlocks.STORAGE.get(StorageKind.BATTERY).get().defaultBlockState().setValue(RouterBlock.TIER, tier));
        return GameTestCompat.getBlockEntity(helper, pos);
    }

    private static StorageSourceTankBlockEntity storageSourceTank(GameTestHelper helper, BlockPos pos, RouterTier tier) {
        helper.setBlock(pos, ModBlocks.STORAGE.get(StorageKind.SOURCE_TANK).get().defaultBlockState().setValue(RouterBlock.TIER, tier));
        return GameTestCompat.getBlockEntity(helper, pos);
    }

    private static long fluid(GameTestHelper helper, BlockPos pos, net.minecraft.world.level.material.Fluid fluid) {
        StorageTankBlockEntity tank = GameTestCompat.getBlockEntity(helper, pos);
        return tank.storage().count(new FluidStack(fluid, 1));
    }

    private static long energy(GameTestHelper helper, BlockPos pos) {
        StorageBatteryBlockEntity battery = GameTestCompat.getBlockEntity(helper, pos);
        return battery.store().stored();
    }

    /** Tanque: vários fluidos por tipo, capacidade total do tier, e a visão de handler de fluido comum. */
    @GameTest(template = "empty")
    public static void tankStoresFluidsByType(GameTestHelper helper) {
        StorageTankBlockEntity tank = storageTank(helper, A, RouterTier.BASIC);
        long capacity = Config.storageCapacity(StorageKind.TANK, RouterTier.BASIC);
        GameTestCompat.assertValueEqual(helper, tank.handler().fill(new FluidStack(Fluids.WATER, 100_000), FluidAction.EXECUTE), 100_000,
                "água pela visão comum");
        GameTestCompat.assertValueEqual(helper, tank.storage().insert(new FluidStack(Fluids.LAVA, 1), capacity, false),
                capacity - 100_000, "lava até a capacidade");
        GameTestCompat.assertValueEqual(helper, tank.storage().types(), 2, "dois fluidos");
        GameTestCompat.assertValueEqual(helper, tank.handler().getTanks(), 3, "um tanque por fluido + o vazio");
        GameTestCompat.assertValueEqual(helper, tank.handler().drain(new FluidStack(Fluids.WATER, 1_000), FluidAction.EXECUTE).getAmount(),
                1_000, "drena a água pedida");
        GameTestCompat.assertValueEqual(helper, tank.handler().fill(new FluidStack(Fluids.WATER, 5_000), FluidAction.SIMULATE), 1_000,
                "cheio: só cabe o que saiu");
        helper.succeed();
    }

    /** Balde no bloco: esvazia o balde cheio no Tanque e enche o vazio com o que tiver, como num tanque comum. */
    @GameTest(template = "empty")
    @SuppressWarnings("removal")
    public static void bucketOnTheTankBlock(GameTestHelper helper) {
        StorageTankBlockEntity tank = storageTank(helper, A, RouterTier.BASIC);
        ServerPlayer player = GameTestCompat.makeMockServerPlayerInLevel(helper);
        player.moveTo(Vec3.atCenterOf(tank.getBlockPos().above()));
        player.setGameMode(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.WATER_BUCKET));
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(tank.getBlockPos()), Direction.UP, tank.getBlockPos(), false);
        tank.getBlockState().use(helper.getLevel(), player, InteractionHand.MAIN_HAND, hit);
        GameTestCompat.assertValueEqual(helper, fluid(helper, A, Fluids.WATER), 1_000L, "o balde esvaziou no Tanque");
        helper.assertTrue(player.getMainHandItem().is(Items.BUCKET), "balde vazio na mão: " + player.getMainHandItem());
        tank.getBlockState().use(helper.getLevel(), player, InteractionHand.MAIN_HAND, hit);
        GameTestCompat.assertValueEqual(helper, fluid(helper, A, Fluids.WATER), 0L, "o balde encheu");
        helper.assertTrue(player.getMainHandItem().is(Items.WATER_BUCKET), "balde de água na mão: " + player.getMainHandItem());
        helper.succeed();
    }

    /** Filtro de entrada no Tanque: só a lava entra, por qualquer caminho. */
    @GameTest(template = "empty")
    public static void tankInputFilter(GameTestHelper helper) {
        StorageTankBlockEntity tank = storageTank(helper, A, RouterTier.BASIC);
        tank.setFilter(new Filter(Filter.ListMode.WHITELIST, false,
                List.of(new FilterEntry.FluidEntry(new FluidStack(Fluids.LAVA, 1), 0))));
        GameTestCompat.assertValueEqual(helper, tank.handler().fill(new FluidStack(Fluids.WATER, 1_000), FluidAction.EXECUTE), 0, "água recusada");
        GameTestCompat.assertValueEqual(helper, tank.handler().fill(new FluidStack(Fluids.LAVA, 1_000), FluidAction.EXECUTE), 1_000, "lava aceita");
        helper.succeed();
    }

    /**
     * Tanque → Tanque no Ultimate pelo roteador: 30 bilhões de mB passam em poucos ticks. Pela API de
     * fluido do NeoForge ({@code int}) seriam no máximo 2,1 bilhões por tick, ou 15 ticks; entre
     * Tanques o roteador usa o {@code BulkFluids}, em {@code long}. Sem fluido criado nem perdido. Porte 1.20.1:
     * no {@code main} a prova era chegar tudo em até 10 ticks; aqui é o que a afirmação diz, uma visita mover mais
     * que {@link Integer#MAX_VALUE} ({@link RouterBlockEntity#lastVisitMoved}), sem relógio nem orçamento em ms.
     */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void tankToTankMovesBeyondInt(GameTestHelper helper) {
        UUID network = newNetwork(helper, "teste-tanque-tanque");
        long amount = 30_000_000_000L;
        storageTank(helper, A, RouterTier.ULTIMATE).storage().insert(new FluidStack(Fluids.WATER, 1), amount, false);
        storageTank(helper, B, RouterTier.ULTIMATE);
        RouterBlockEntity source = router(helper, A, RouterTier.ULTIMATE, network, ResourceType.FLUID, PortMode.EXTRACT);
        RouterBlockEntity target = router(helper, B, RouterTier.ULTIMATE, network, ResourceType.FLUID, PortMode.INSERT);
        long[] largestVisit = new long[1];
        helper.onEachTick(() -> {
            GameTestCompat.assertValueEqual(helper, fluid(helper, A, Fluids.WATER) + fluid(helper, B, Fluids.WATER), amount, "água");
            largestVisit[0] = Math.max(largestVisit[0], source.lastVisitMoved(ResourceType.FLUID));
        });
        helper.startSequence()
                .thenWaitUntil(() -> waitRegistered(helper, source, target))
                .thenWaitUntil(() -> GameTestCompat.assertValueEqual(helper, fluid(helper, B, Fluids.WATER), amount, "água no destino"))
                .thenExecute(() -> {
                    largestVisit[0] = Math.max(largestVisit[0], source.lastVisitMoved(ResourceType.FLUID));
                    helper.assertTrue(largestVisit[0] > Integer.MAX_VALUE,
                            "a maior visita moveu " + largestVisit[0] + " mB: ainda no teto do int");
                })
                .thenSucceed();
    }

    /** Bateria: capacidade do tier e a visão {@code IEnergyStorage} comum. */
    @GameTest(template = "empty")
    public static void batteryStoresEnergy(GameTestHelper helper) {
        StorageBatteryBlockEntity battery = storageBattery(helper, A, RouterTier.BASIC);
        long capacity = Config.storageCapacity(StorageKind.BATTERY, RouterTier.BASIC);
        GameTestCompat.assertValueEqual(helper, battery.store().insert(capacity + 500, false), capacity, "cheia no Básico");
        GameTestCompat.assertValueEqual(helper, battery.handler().receiveEnergy(10, true), 0, "cheia não recebe");
        GameTestCompat.assertValueEqual(helper, battery.handler().extractEnergy(1_000, false), 1_000, "extrai pela visão comum");
        GameTestCompat.assertValueEqual(helper, battery.handler().getEnergyStored(), (int) (capacity - 1_000), "guardado");
        GameTestCompat.assertValueEqual(helper, battery.signal(), 14, "comparador quase cheio");
        helper.succeed();
    }

    /**
     * Bateria → Bateria no Ultimate pelo roteador: 50 bilhões de FE passam em poucos ticks. Pela API
     * de energia do NeoForge ({@code int}) seriam no máximo 2,1 bilhões por tick, ou 24 ticks; entre
     * Baterias o roteador usa o {@code BulkEnergy}, em {@code long}. Sem energia criada nem perdida. Porte 1.20.1:
     * a prova é uma visita mover mais que {@link Integer#MAX_VALUE} (no {@code main}, chegar tudo em até 10 ticks).
     */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void batteryToBatteryMovesBeyondInt(GameTestHelper helper) {
        UUID network = newNetwork(helper, "teste-bateria-bateria");
        long amount = 50_000_000_000L;
        storageBattery(helper, A, RouterTier.ULTIMATE).store().insert(amount, false);
        storageBattery(helper, B, RouterTier.ULTIMATE);
        RouterBlockEntity source = router(helper, A, RouterTier.ULTIMATE, network, ResourceType.ENERGY, PortMode.EXTRACT);
        RouterBlockEntity target = router(helper, B, RouterTier.ULTIMATE, network, ResourceType.ENERGY, PortMode.INSERT);
        long[] largestVisit = new long[1];
        helper.onEachTick(() -> {
            GameTestCompat.assertValueEqual(helper, energy(helper, A) + energy(helper, B), amount, "energia");
            largestVisit[0] = Math.max(largestVisit[0], source.lastVisitMoved(ResourceType.ENERGY));
        });
        helper.startSequence()
                .thenWaitUntil(() -> waitRegistered(helper, source, target))
                .thenWaitUntil(() -> GameTestCompat.assertValueEqual(helper, energy(helper, B), amount, "energia no destino"))
                .thenExecute(() -> {
                    largestVisit[0] = Math.max(largestVisit[0], source.lastVisitMoved(ResourceType.ENERGY));
                    helper.assertTrue(largestVisit[0] > Integer.MAX_VALUE,
                            "a maior visita moveu " + largestVisit[0] + " FE: ainda no teto do int");
                })
                .thenSucceed();
    }

    /** Tanque e Bateria quebrados no criativo também viram item com o conteúdo, e o conteúdo volta ao colocar. */
    @GameTest(template = "empty")
    @SuppressWarnings("removal")
    public static void tankAndBatteryKeepContentsWhenBroken(GameTestHelper helper) {
        storageTank(helper, A, RouterTier.ELITE).storage().insert(new FluidStack(Fluids.LAVA, 1), 7_654_321L, false);
        storageBattery(helper, B, RouterTier.ELITE).store().insert(9_876_543L, false);
        ServerPlayer player = GameTestCompat.makeMockServerPlayerInLevel(helper);
        player.setGameMode(GameType.CREATIVE);
        for (BlockPos pos : List.of(A, B)) {
            player.moveTo(Vec3.atCenterOf(helper.absolutePos(pos).above()));
            player.gameMode.destroyBlock(helper.absolutePos(pos));
        }
        ItemStack tank = pickUp(helper, A, StorageKind.TANK, 7_654_321L);
        ItemStack battery = pickUp(helper, B, StorageKind.BATTERY, 9_876_543L);
        place(helper, tank, A, StorageKind.TANK);
        place(helper, battery, B, StorageKind.BATTERY);
        GameTestCompat.assertValueEqual(helper, fluid(helper, A, Fluids.LAVA), 7_654_321L, "a lava voltou");
        GameTestCompat.assertValueEqual(helper, energy(helper, B), 9_876_543L, "a energia voltou");
        helper.succeed();
    }

    /** Tanque de Source: guarda em long até a capacidade, e o nível do bloco acompanha o conteúdo. */
    @GameTest(template = "empty")
    public static void sourceTankStoresAndShowsLevel(GameTestHelper helper) {
        StorageSourceTankBlockEntity tank = storageSourceTank(helper, A, RouterTier.BASIC);
        long capacity = Config.storageCapacity(StorageKind.SOURCE_TANK, RouterTier.BASIC);
        GameTestCompat.assertValueEqual(helper, capacity, 10_000L, "capacidade do Básico");
        GameTestCompat.assertValueEqual(helper, tank.store().insert(5_000, false), 5_000L, "metade");
        GameTestCompat.assertValueEqual(helper, helper.getBlockState(A).getValue(StorageSourceTankBlock.FILL), 5, "nível na metade");
        GameTestCompat.assertValueEqual(helper, tank.store().insert(999_999, false), 5_000L, "até a capacidade");
        GameTestCompat.assertValueEqual(helper, helper.getBlockState(A).getValue(StorageSourceTankBlock.FILL), 10, "cheio");
        GameTestCompat.assertValueEqual(helper, tank.signal(), 15, "comparador cheio");
        tank.store().extract(10_000, false);
        GameTestCompat.assertValueEqual(helper, helper.getBlockState(A).getValue(StorageSourceTankBlock.FILL), 0, "vazio");
        helper.succeed();
    }

    /**
     * A capacidade do tier muda na config com o jogo rodando: o nível do tanque carregado se corrige pelo
     * mesmo caminho que o listener da recarga usa, e volta quando a capacidade é restaurada.
     */
    @GameTest(template = "empty")
    public static void sourceTankLevelFollowsConfigReload(GameTestHelper helper) {
        var capacity = Config.STORAGE_CAPACITY.get(StorageKind.SOURCE_TANK).get(RouterTier.BASIC);
        long original = capacity.get();
        StorageSourceTankBlockEntity tank = storageSourceTank(helper, A, RouterTier.BASIC);
        tank.store().insert(5_000, false);
        GameTestCompat.assertValueEqual(helper, helper.getBlockState(A).getValue(StorageSourceTankBlock.FILL), 5, "nível na metade");
        // O onLoad (que põe o tanque no conjunto) roda no tick seguinte à colocação.
        helper.startSequence()
                .thenIdle(3)
                .thenExecute(() -> {
                    try {
                        capacity.set(100_000L);
                        SourceTankLevels.refreshAll();
                        GameTestCompat.assertValueEqual(helper, helper.getBlockState(A).getValue(StorageSourceTankBlock.FILL), 1, "nível com a capacidade nova");
                    } finally {
                        capacity.set(original);
                        SourceTankLevels.refreshAll();
                    }
                    GameTestCompat.assertValueEqual(helper, helper.getBlockState(A).getValue(StorageSourceTankBlock.FILL), 5, "nível com a capacidade restaurada");
                })
                .thenSucceed();
    }

    /** Um bloco com o nível errado (como um chunk salvo com outra capacidade) se corrige pelo tick agendado no onLoad. */
    @GameTest(template = "empty")
    public static void sourceTankFixesWrongLevelOnLoad(GameTestHelper helper) {
        BlockPos abs = helper.absolutePos(A);
        StorageSourceTankBlockEntity tank = storageSourceTank(helper, A, RouterTier.BASIC);
        tank.store().insert(5_000, false);
        // No mesmo tick da colocação (antes do onLoad): o conteúdo é de nível 5 e o bloco diz 9.
        helper.getLevel().setBlock(abs, helper.getLevel().getBlockState(abs).setValue(StorageSourceTankBlock.FILL, 9), 3);
        GameTestCompat.assertValueEqual(helper, helper.getBlockState(A).getValue(StorageSourceTankBlock.FILL), 9, "nível errado forçado");
        helper.startSequence()
                .thenWaitUntil(() -> GameTestCompat.assertValueEqual(helper, helper.getBlockState(A).getValue(StorageSourceTankBlock.FILL), 5, "nível corrigido"))
                .thenSucceed();
    }

    /** Upgrade de tier com Source dentro: o conteúdo fica e o nível é recalculado pela capacidade nova. */
    @GameTest(template = "empty")
    public static void sourceTankUpgradeKeepsSourceAndRefreshesLevel(GameTestHelper helper) {
        StorageSourceTankBlockEntity tank = storageSourceTank(helper, A, RouterTier.BASIC);
        tank.store().insert(10_000, false);
        helper.assertTrue(StorageBlock.tryUpgrade(helper.getLevel(), helper.absolutePos(A), RouterTier.ADVANCED), "upgrade");
        StorageSourceTankBlockEntity upgraded = GameTestCompat.getBlockEntity(helper, A);
        GameTestCompat.assertValueEqual(helper, upgraded.store().stored(), 10_000L, "Source depois do upgrade");
        // 10.000 de 80.000 (um oitavo): nível 2, arredondado para cima.
        GameTestCompat.assertValueEqual(helper, helper.getBlockState(A).getValue(StorageSourceTankBlock.FILL), 2, "nível com a capacidade nova");
        helper.succeed();
    }

    /** Tanque de Source Ultimate (sem limite) quebrado cheio: o item leva a Source e o tier, e o nível volta ao colocar. */
    @GameTest(template = "empty")
    @SuppressWarnings("removal")
    public static void sourceTankKeepsSourceWhenBroken(GameTestHelper helper) {
        long source = 3_000_000_001L;
        storageSourceTank(helper, C, RouterTier.ULTIMATE).store().insert(source, false);
        ServerPlayer player = GameTestCompat.makeMockServerPlayerInLevel(helper);
        player.setGameMode(GameType.CREATIVE);
        player.moveTo(Vec3.atCenterOf(helper.absolutePos(C).above()));
        player.gameMode.destroyBlock(helper.absolutePos(C));
        ItemStack item = pickUp(helper, C, StorageKind.SOURCE_TANK, source);
        GameTestCompat.assertValueEqual(helper, StorageBlockItem.tierOf(item), RouterTier.ULTIMATE, "tier no item");
        place(helper, item, C, StorageKind.SOURCE_TANK);
        StorageSourceTankBlockEntity placed = GameTestCompat.getBlockEntity(helper, C);
        GameTestCompat.assertValueEqual(helper, placed.store().stored(), source, "a Source voltou");
        GameTestCompat.assertValueEqual(helper, helper.getBlockState(C).getValue(StorageSourceTankBlock.FILL), 10, "nível do bloco recolocado");
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
                {GameType.SURVIVAL, new ItemStack(Items.DIAMOND_PICKAXE), 30_000_001L},
                {GameType.SURVIVAL, ItemStack.EMPTY, 30_000_002L},
                {GameType.CREATIVE, ItemStack.EMPTY, 30_000_003L},
        };
        for (Object[] c : cases) {
            long energy = (long) c[2];
            storageBattery(helper, A, RouterTier.ELITE).store().insert(energy, false);
            BlockPos absolute = helper.absolutePos(A);
            ServerPlayer player = GameTestCompat.makeMockServerPlayerInLevel(helper);
            player.moveTo(Vec3.atCenterOf(absolute.above()));
            player.setGameMode((GameType) c[0]);
            player.setItemInHand(InteractionHand.MAIN_HAND, (ItemStack) c[1]);
            player.gameMode.destroyBlock(absolute);
            helper.assertBlockNotPresent(ModBlocks.STORAGE.get(StorageKind.BATTERY).get(), A);
            ItemStack item = pickUp(helper, A, StorageKind.BATTERY, energy);
            GameTestCompat.assertValueEqual(helper, StorageBlockItem.tierOf(item), RouterTier.ELITE, "tier no item (" + c[0] + ")");
            place(helper, item, A, StorageKind.BATTERY);
            GameTestCompat.assertValueEqual(helper, energy(helper, A), energy, "energia de volta (" + c[0] + ", " + c[1] + ")");
            helper.setBlock(A, Blocks.AIR);
        }
        helper.succeed();
    }

    /** Tira do chão o item do armazenamento com o total dado (perto da posição relativa). */
    private static ItemStack pickUp(GameTestHelper helper, BlockPos pos, StorageKind kind, long total) {
        List<ItemEntity> drops = helper.getLevel().getEntitiesOfClass(ItemEntity.class,
                new AABB(helper.absolutePos(pos)).inflate(1.5), entity -> entity.getItem().is(ModItems.STORAGE.get(kind).get()));
        GameTestCompat.assertValueEqual(helper, drops.size(), 1, kind + " no chão");
        StorageContents contents = ModDataComponents.STORAGE_CONTENTS.get(drops.get(0).getItem());
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
        ServerPlayer player = GameTestCompat.makeMockServerPlayerInLevel(helper);
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
        GameTestCompat.assertValueEqual(helper, tank.storage().count(water), 4_000L, "saiu um balde");
        helper.assertTrue(StorageListMenu.handle(player, new StorageActionPayload(CONTAINER_ID, StorageKind.TANK,
                StorageActionPayload.Action.INSERT_CARRIED)), "esvaziar o balde");
        helper.assertTrue(menu.getCarried().is(Items.BUCKET), "balde vazio no cursor");
        GameTestCompat.assertValueEqual(helper, tank.storage().count(water), 5_000L, "voltou o balde");
        menu.setCarried(ItemStack.EMPTY);
        player.getInventory().setItem(9, new ItemStack(Items.WATER_BUCKET));
        menu.quickMoveStack(player, 0);
        GameTestCompat.assertValueEqual(helper, tank.storage().count(water), 6_000L, "Shift + clique esvaziou o balde do inventário");
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
