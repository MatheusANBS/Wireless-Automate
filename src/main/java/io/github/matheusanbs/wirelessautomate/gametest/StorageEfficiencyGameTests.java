package io.github.matheusanbs.wirelessautomate.gametest;

import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.block.RouterBlock;
import io.github.matheusanbs.wirelessautomate.block.RouterBlockEntity;
import io.github.matheusanbs.wirelessautomate.block.RouterTier;
import io.github.matheusanbs.wirelessautomate.filter.Filter;
import io.github.matheusanbs.wirelessautomate.filter.FilterEntry;
import io.github.matheusanbs.wirelessautomate.network.NetworkManager;
import io.github.matheusanbs.wirelessautomate.network.NetworkSavedData;
import io.github.matheusanbs.wirelessautomate.network.PortMode;
import io.github.matheusanbs.wirelessautomate.network.ResourceType;
import io.github.matheusanbs.wirelessautomate.registry.ModBlocks;
import io.github.matheusanbs.wirelessautomate.storage.FluidStorage;
import io.github.matheusanbs.wirelessautomate.storage.ItemStorage;
import io.github.matheusanbs.wirelessautomate.storage.StorageBatteryBlockEntity;
import io.github.matheusanbs.wirelessautomate.storage.StorageChestBlockEntity;
import io.github.matheusanbs.wirelessautomate.storage.StorageKind;
import io.github.matheusanbs.wirelessautomate.storage.StorageNeighbors;
import io.github.matheusanbs.wirelessautomate.storage.StorageTankBlockEntity;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ComparatorBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.ComparatorBlockEntity;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
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

    // ------------------------------------------------------------------ S2: estoque sem componentes em O(1)

    private static ItemStack named(Item item, int n) {
        ItemStack stack = new ItemStack(item);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal("s2 " + n));
        return stack;
    }

    /** A soma de todos os tipos do item, tipo a tipo: o que o {@code countItem} contava antes. */
    private static long bruteItem(ItemStorage storage, Item item) {
        long sum = 0;
        for (int i = 0; i < storage.types(); i++) {
            if (storage.key(i).is(item)) {
                sum += storage.count(i);
            }
        }
        return sum;
    }

    private static void assertItemTotals(GameTestHelper helper, ItemStorage storage, String when) {
        for (Item item : new Item[] {Items.PAPER, Items.COBBLESTONE, Items.DIAMOND}) {
            helper.assertValueEqual(storage.countItem(new ItemStack(item)), bruteItem(storage, item), when + ": " + item);
        }
    }

    /**
     * A soma por item mantida dá o mesmo que somar os tipos um a um, depois de inserções e extrações em
     * qualquer ordem (com tipos que zeram e voltam), depois de salvar e carregar e depois de esvaziar.
     */
    @GameTest(template = "empty")
    public static void itemStockTotalsMatchTheTypes(GameTestHelper helper) {
        ItemStorage storage = chest(helper, A, RouterTier.ULTIMATE).storage();
        Random random = new Random(42);
        Item[] items = {Items.PAPER, Items.COBBLESTONE, Items.DIAMOND};
        for (int step = 0; step < 2_000; step++) {
            Item item = items[random.nextInt(items.length)];
            int variant = random.nextInt(6);
            ItemStack key = variant == 0 ? new ItemStack(item) : named(item, variant);
            long amount = 1 + random.nextInt(500);
            if (random.nextInt(3) == 0) {
                storage.extract(key, amount, false);
            } else {
                storage.insert(key, amount, false);
            }
            if (step % 97 == 0) {
                assertItemTotals(helper, storage, "passo " + step);
            }
        }
        assertItemTotals(helper, storage, "fim");
        helper.assertTrue(storage.countItem(new ItemStack(Items.PAPER)) > 0, "papel guardado");
        helper.assertValueEqual(storage.countItem(named(Items.PAPER, 99)), storage.countItem(new ItemStack(Items.PAPER)),
                "componentes da consulta não importam");

        ListTag saved = storage.save(helper.getLevel().registryAccess());
        ItemStorage copy = chest(helper, B, RouterTier.ULTIMATE).storage();
        copy.load(saved, helper.getLevel().registryAccess());
        assertItemTotals(helper, copy, "depois de carregar");
        helper.assertValueEqual(copy.countItem(new ItemStack(Items.PAPER)), storage.countItem(new ItemStack(Items.PAPER)),
                "papel igual depois de carregar");

        copy.clear();
        helper.assertValueEqual(copy.countItem(new ItemStack(Items.PAPER)), 0L, "nada depois de esvaziar");
        helper.succeed();
    }

    /** O mesmo para o Tanque: a soma por fluido, com componentes diferentes. */
    @GameTest(template = "empty")
    public static void fluidStockTotalsMatchTheTypes(GameTestHelper helper) {
        helper.setBlock(A, ModBlocks.STORAGE.get(StorageKind.TANK).get().defaultBlockState()
                .setValue(RouterBlock.TIER, RouterTier.ULTIMATE));
        StorageTankBlockEntity tank = helper.getBlockEntity(A);
        FluidStorage storage = tank.storage();
        Random random = new Random(7);
        for (int step = 0; step < 1_000; step++) {
            FluidStack key = new FluidStack(random.nextBoolean() ? Fluids.WATER : Fluids.LAVA, 1);
            int variant = random.nextInt(4);
            if (variant > 0) {
                key.set(DataComponents.CUSTOM_NAME, Component.literal("s2 " + variant));
            }
            long amount = 1 + random.nextInt(5_000);
            if (random.nextInt(3) == 0) {
                storage.extract(key, amount, false);
            } else {
                storage.insert(key, amount, false);
            }
        }
        for (FluidStack fluid : new FluidStack[] {new FluidStack(Fluids.WATER, 1), new FluidStack(Fluids.LAVA, 1)}) {
            long sum = 0;
            for (int i = 0; i < storage.types(); i++) {
                if (FluidStack.isSameFluid(storage.key(i), fluid)) {
                    sum += storage.count(i);
                }
            }
            helper.assertValueEqual(storage.countFluid(fluid), sum, "soma do fluido " + fluid.getFluid());
        }
        helper.succeed();
    }

    /**
     * O estoque sem componentes da lista branca do roteador, num Baú de destino com tipos do mesmo item
     * com outros componentes: o estoque conta todos e o roteador para no limite.
     */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void routerStockCountsEveryVariantInTheChest(GameTestHelper helper) {
        UUID network = NetworkSavedData.get(helper.getLevel().getServer()).create(UUID.randomUUID(), "teste-estoque-s2").id();
        StorageChestBlockEntity source = chest(helper, A, RouterTier.ELITE);
        source.storage().insert(new ItemStack(Items.PAPER), 1_000, false);
        StorageChestBlockEntity target = chest(helper, B, RouterTier.ELITE);
        for (int i = 0; i < 50; i++) {
            target.storage().insert(named(Items.PAPER, i), 2, false);
        }
        RouterBlockEntity from = router(helper, A, network, PortMode.EXTRACT);
        RouterBlockEntity to = router(helper, B, network, PortMode.INSERT);
        to.setFilter(ResourceType.ITEM, Direction.UP, new Filter(Filter.ListMode.WHITELIST, false,
                List.of(new FilterEntry.ItemEntry(new ItemStack(Items.PAPER), 300))));
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertValueEqual(target.storage().countItem(new ItemStack(Items.PAPER)), 300L,
                        "estoque do destino"))
                .thenIdle(20)
                .thenExecute(() -> {
                    helper.assertValueEqual(target.storage().countItem(new ItemStack(Items.PAPER)), 300L, "parou no estoque");
                    helper.assertValueEqual(target.storage().count(new ItemStack(Items.PAPER)), 200L, "o papel sem nome que entrou");
                    helper.assertValueEqual(source.storage().count(new ItemStack(Items.PAPER)), 800L, "o resto na origem");
                    helper.assertTrue(NetworkManager.get().contains(from) && NetworkManager.get().contains(to), "registrados");
                })
                .thenSucceed();
    }
}
