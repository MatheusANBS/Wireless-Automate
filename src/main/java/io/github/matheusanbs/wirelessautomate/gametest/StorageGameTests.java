package io.github.matheusanbs.wirelessautomate.gametest;

import io.github.matheusanbs.wirelessautomate.Config;
import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.block.RouterBlock;
import io.github.matheusanbs.wirelessautomate.block.RouterBlockEntity;
import io.github.matheusanbs.wirelessautomate.block.RouterTier;
import io.github.matheusanbs.wirelessautomate.network.NetworkManager;
import io.github.matheusanbs.wirelessautomate.network.NetworkSavedData;
import io.github.matheusanbs.wirelessautomate.network.PortMode;
import io.github.matheusanbs.wirelessautomate.network.ResourceType;
import io.github.matheusanbs.wirelessautomate.registry.ModBlocks;
import io.github.matheusanbs.wirelessautomate.registry.ModDataComponents;
import io.github.matheusanbs.wirelessautomate.storage.ItemStorage;
import io.github.matheusanbs.wirelessautomate.storage.ItemStorageHandler;
import io.github.matheusanbs.wirelessautomate.storage.StorageChestBlock;
import io.github.matheusanbs.wirelessautomate.storage.StorageChestBlockEntity;
import io.github.matheusanbs.wirelessautomate.storage.StorageContents;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
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
                    helper.assertTrue(ticks <= 3, "levou " + ticks + " ticks: o atalho não moveu o tipo inteiro");
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
     * Uma cópia do item nasce vazia: colocar é tirar, então nada duplica.
     */
    @GameTest(template = "empty")
    public static void breakingAndPlacingKeepsTheContents(GameTestHelper helper) {
        storageChest(helper, A, RouterTier.ELITE).storage().insert(new ItemStack(Items.COBBLESTONE), 1_000_000L, false);
        storage(helper, A).insert(new ItemStack(Items.DIAMOND), 5, false);
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
        helper.succeed();
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
