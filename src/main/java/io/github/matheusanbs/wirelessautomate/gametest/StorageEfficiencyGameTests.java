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
import io.github.matheusanbs.wirelessautomate.registry.ModDataComponents;
import io.github.matheusanbs.wirelessautomate.registry.ModItems;
import io.github.matheusanbs.wirelessautomate.storage.FluidStorage;
import io.github.matheusanbs.wirelessautomate.storage.ItemStorage;
import io.github.matheusanbs.wirelessautomate.storage.StorageBatteryBlockEntity;
import io.github.matheusanbs.wirelessautomate.storage.StorageChestBlockEntity;
import io.github.matheusanbs.wirelessautomate.storage.StorageContents;
import io.github.matheusanbs.wirelessautomate.storage.StorageKind;
import io.github.matheusanbs.wirelessautomate.storage.StorageMath;
import io.github.matheusanbs.wirelessautomate.storage.StorageNeighbors;
import io.github.matheusanbs.wirelessautomate.storage.StorageSavedData;
import io.github.matheusanbs.wirelessautomate.storage.StorageTankBlockEntity;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ComparatorBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.ComparatorBlockEntity;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

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
        for (int i = 0; i < storage.slots(); i++) {
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
            for (int i = 0; i < storage.slots(); i++) {
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

    // ------------------------------------------------------------------ S3: a chave codificada uma vez só

    /**
     * Salvar o Baú duas vezes reusa a chave codificada de cada tipo (o mesmo {@code Tag}); o NBT faz a ida e
     * volta (tipos, componentes e quantidades), e o Baú carregado salva igual ao original.
     */
    @GameTest(template = "empty")
    public static void chestNbtRoundTripsAndReusesTheKeys(GameTestHelper helper) {
        ItemStorage storage = chest(helper, A, RouterTier.ULTIMATE).storage();
        storage.insert(new ItemStack(Items.COBBLESTONE), 5_000_000L, false);
        for (int i = 0; i < 20; i++) {
            storage.insert(named(Items.PAPER, i), 10 + i, false);
        }
        ItemStack damaged = new ItemStack(Items.DIAMOND_PICKAXE);
        damaged.setDamageValue(17);
        storage.insert(damaged, 3, false);

        var registries = helper.getLevel().registryAccess();
        ListTag first = storage.save(registries);
        ListTag second = storage.save(registries);
        helper.assertValueEqual(first.size(), storage.types(), "um registro por tipo");
        for (int i = 0; i < first.size(); i++) {
            helper.assertTrue(first.getCompound(i).get("key") == second.getCompound(i).get("key"),
                    "a chave " + i + " foi recodificada");
        }
        helper.assertTrue(first.equals(second), "os dois salvamentos são iguais");

        ItemStorage copy = chest(helper, B, RouterTier.ULTIMATE).storage();
        copy.load(first, registries);
        helper.assertValueEqual(copy.types(), storage.types(), "tipos");
        helper.assertValueEqual(copy.total(), storage.total(), "total");
        for (int i = 0; i < storage.slots(); i++) {
            helper.assertValueEqual(copy.count(storage.key(i)), storage.count(i), "quantidade do tipo " + i);
        }
        helper.assertValueEqual(copy.count(damaged), 3L, "a picareta com o dano");
        helper.assertTrue(copy.save(registries).equals(first), "o carregado salva igual");
        helper.succeed();
    }

    /** O formato antigo do Baú (a chave no campo {@code item}) ainda carrega, e salva no formato atual. */
    @GameTest(template = "empty")
    public static void chestLoadsTheOldItemField(GameTestHelper helper) {
        var registries = helper.getLevel().registryAccess();
        ListTag old = new ListTag();
        CompoundTag entry = new CompoundTag();
        entry.put("item", new ItemStack(Items.DIAMOND).save(registries));
        entry.putLong("count", 1_234L);
        old.add(entry);
        ItemStorage storage = chest(helper, A, RouterTier.BASIC).storage();
        storage.load(old, registries);
        helper.assertValueEqual(storage.count(new ItemStack(Items.DIAMOND)), 1_234L, "diamantes do formato antigo");
        CompoundTag saved = storage.save(registries).getCompound(0);
        helper.assertTrue(saved.contains("key") && !saved.contains("item"), "salva no formato atual");
        helper.succeed();
    }

    // ------------------------------------------------------------------ S4: a pilha do slot sem cópia por chamada

    /**
     * {@code getStackInSlot} devolve a mesma pilha enquanto a quantidade do tipo não muda; quando muda, uma
     * nova, e a antiga fica como estava (quem a guardou para comparar vê a diferença). A extração devolve uma
     * pilha nova, e alterar a do slot (contra o contrato) não mexe no conteúdo.
     */
    @GameTest(template = "empty")
    public static void slotViewIsReusedUntilTheCountChanges(GameTestHelper helper) {
        StorageChestBlockEntity entity = chest(helper, A, RouterTier.ELITE);
        ItemStorage storage = entity.storage();
        IItemHandler handler = entity.handler();
        storage.insert(new ItemStack(Items.COBBLESTONE), 100, false);
        ItemStack first = handler.getStackInSlot(0);
        helper.assertTrue(first == handler.getStackInSlot(0), "a mesma pilha sem mudança");
        helper.assertValueEqual(first.getCount(), 100, "quantidade");

        storage.insert(new ItemStack(Items.COBBLESTONE), 50, false);
        ItemStack second = handler.getStackInSlot(0);
        helper.assertTrue(second != first, "pilha nova quando a quantidade muda");
        helper.assertValueEqual(first.getCount(), 100, "a antiga ficou como estava");
        helper.assertValueEqual(second.getCount(), 150, "a nova tem a quantidade nova");

        ItemStack taken = handler.extractItem(0, 10, false);
        helper.assertTrue(taken != second && taken.getCount() == 10, "a extração devolve uma pilha nova");
        helper.assertValueEqual(second.getCount(), 150, "a extração não mexeu na pilha do slot");
        helper.assertValueEqual(handler.getStackInSlot(0).getCount(), 140, "o slot depois da extração");

        handler.getStackInSlot(0).shrink(40);
        helper.assertValueEqual(storage.count(new ItemStack(Items.COBBLESTONE)), 140L, "o conteúdo não mudou");
        helper.assertValueEqual(handler.getStackInSlot(0).getCount(), 140, "a pilha alterada foi trocada");
        helper.assertTrue(handler.getStackInSlot(1).isEmpty(), "o slot vazio do fim");
        helper.succeed();
    }

    /** O mesmo no Tanque, pelo {@code getFluidInTank}. */
    @GameTest(template = "empty")
    public static void tankViewIsReusedUntilTheAmountChanges(GameTestHelper helper) {
        helper.setBlock(A, ModBlocks.STORAGE.get(StorageKind.TANK).get().defaultBlockState()
                .setValue(RouterBlock.TIER, RouterTier.ELITE));
        StorageTankBlockEntity tank = helper.getBlockEntity(A);
        tank.storage().insert(new FluidStack(Fluids.WATER, 1), 4_000, false);
        FluidStack first = tank.handler().getFluidInTank(0);
        helper.assertTrue(first == tank.handler().getFluidInTank(0), "o mesmo fluido sem mudança");
        tank.storage().extract(new FluidStack(Fluids.WATER, 1), 1_000, false);
        FluidStack second = tank.handler().getFluidInTank(0);
        helper.assertTrue(second != first && first.getAmount() == 4_000 && second.getAmount() == 3_000,
                "um novo quando a quantidade muda, o antigo como estava");
        helper.succeed();
    }

    /** Um funil vanilla embaixo do Baú tira os itens pela visão de slot. */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void hopperPullsFromTheChest(GameTestHelper helper) {
        BlockPos above = new BlockPos(1, 2, 1);
        BlockPos hopper = new BlockPos(1, 1, 1);
        helper.setBlock(hopper, Blocks.HOPPER);
        StorageChestBlockEntity entity = chest(helper, above, RouterTier.BASIC);
        entity.storage().insert(new ItemStack(Items.DIAMOND), 3, false);
        helper.startSequence()
                .thenWaitUntil(() -> {
                    HopperBlockEntity funnel = helper.getBlockEntity(hopper);
                    int count = 0;
                    for (int i = 0; i < funnel.getContainerSize(); i++) {
                        count += funnel.getItem(i).getCount();
                    }
                    helper.assertValueEqual(count, 3, "diamantes no funil");
                })
                .thenExecute(() -> helper.assertTrue(entity.storage().isEmpty(), "o Baú esvaziou"))
                .thenSucceed();
    }

    // ------------------------------------------------------------------ S5: o StorageSavedData não cresce sem limite

    /** O guardado que tem esta quantidade de diamantes (cada teste usa uma diferente), ou {@code null}. */
    private static @Nullable UUID storedWithDiamonds(GameTestHelper helper, long count) {
        for (Map.Entry<UUID, StorageSavedData.Stored> entry : StorageSavedData.get(helper.getLevel().getServer()).contents().entrySet()) {
            if (entry.getValue().data() instanceof ListTag list && list.size() == 1
                    && list.getCompound(0).getLong("count") == count) {
                return entry.getKey();
            }
        }
        return null;
    }

    /** Um Baú que sai do mundo sem drop nenhum (como um {@code /setblock}) não deixa nada no servidor. */
    @GameTest(template = "empty")
    public static void noDropStoresNothing(GameTestHelper helper) {
        chest(helper, A, RouterTier.BASIC).storage().insert(new ItemStack(Items.DIAMOND), 4_321, false);
        helper.getLevel().setBlock(helper.absolutePos(A), Blocks.AIR.defaultBlockState(), 3);
        helper.assertTrue(storedWithDiamonds(helper, 4_321) == null, "o conteúdo sem drop ficou guardado");
        helper.succeed();
    }

    /** Quebrado no sobrevivência com a picareta, o conteúdo vai para o servidor pelo id que o drop leva. */
    @GameTest(template = "empty")
    public static void survivalBreakStoresTheContents(GameTestHelper helper) {
        chest(helper, A, RouterTier.BASIC).storage().insert(new ItemStack(Items.DIAMOND), 4_322, false);
        BlockPos absolute = helper.absolutePos(A);
        ServerPlayer player = TestPlayers.inLevel(helper);
        player.moveTo(Vec3.atCenterOf(absolute.above()));
        player.setGameMode(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIAMOND_PICKAXE));
        player.gameMode.destroyBlock(absolute);
        UUID stored = storedWithDiamonds(helper, 4_322);
        helper.assertTrue(stored != null, "o conteúdo foi guardado");
        List<ItemEntity> drops = helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(absolute).inflate(1.5),
                entity -> entity.getItem().is(ModItems.STORAGE_CHEST.get()));
        helper.assertValueEqual(drops.size(), 1, "um Baú no chão");
        StorageContents contents = drops.get(0).getItem().get(ModDataComponents.STORAGE_CONTENTS.get());
        helper.assertTrue(contents != null && contents.id().equals(stored), "o drop aponta para o guardado");
        drops.get(0).discard();
        helper.succeed();
    }

    /**
     * O prazo: o guardado há mais dias que o prazo sai, o recente fica, o sem data ganha a data da varredura, e o
     * item no inventário renova o prazo uma vez por dia. Num {@code StorageSavedData} à parte, sem dividir o do
     * servidor com os outros testes.
     */
    @GameTest(template = "empty")
    public static void stashExpiresAfterTheDeadline(GameTestHelper helper) {
        StorageSavedData data = new StorageSavedData();
        long day = StorageMath.TICKS_PER_DAY;
        long start = 10 * day;
        UUID old = UUID.randomUUID();
        UUID recent = UUID.randomUUID();
        UUID carried = UUID.randomUUID();
        data.put(old, StorageKind.CHEST, RouterTier.BASIC, new ListTag(), start);
        data.put(carried, StorageKind.CHEST, RouterTier.BASIC, new ListTag(), start);
        data.put(recent, StorageKind.CHEST, RouterTier.BASIC, new ListTag(), start + 50 * day);

        data.touch(carried, start + 3 * day);
        helper.assertValueEqual(data.contents().get(carried).storedAt(), start + 3 * day, "carregar renova o prazo");
        data.touch(carried, start + 3 * day + 100);
        helper.assertValueEqual(data.contents().get(carried).storedAt(), start + 3 * day, "renova no máximo uma vez por dia");

        helper.assertValueEqual(data.expire(start + 92 * day, 90), 1, "um vencido");
        helper.assertTrue(!data.contains(old), "o antigo saiu");
        helper.assertTrue(data.contains(recent) && data.contains(carried), "o recente e o carregado ficaram");
        helper.assertValueEqual(data.expire(start + 1_000 * day, 0), 0, "0 é sem prazo");
        helper.succeed();
    }

    /** A data faz a ida e volta pelo NBT; o formato sem data carrega sem data e ganha a da primeira varredura. */
    @GameTest(template = "empty")
    public static void stashDateRoundTripsAndOldEntriesStartCounting(GameTestHelper helper) {
        var registries = helper.getLevel().registryAccess();
        StorageSavedData data = new StorageSavedData();
        UUID dated = UUID.randomUUID();
        data.put(dated, StorageKind.TANK, RouterTier.ELITE, new ListTag(), 12_345L);
        CompoundTag saved = data.save(new CompoundTag(), registries);
        UUID undated = UUID.randomUUID();
        CompoundTag oldEntry = new CompoundTag();
        oldEntry.putString("kind", StorageKind.CHEST.getSerializedName());
        oldEntry.put("data", new ListTag());
        saved.getCompound("contents").put(undated.toString(), oldEntry);

        StorageSavedData loaded = StorageSavedData.FACTORY.deserializer().apply(saved, registries);
        helper.assertValueEqual(loaded.contents().get(dated).storedAt(), 12_345L, "a data voltou");
        helper.assertValueEqual(loaded.contents().get(dated).kind(), StorageKind.TANK, "o tipo voltou");
        helper.assertValueEqual(loaded.contents().get(undated).storedAt(), StorageSavedData.UNDATED, "sem data");
        loaded.expire(99_999L, 90);
        helper.assertValueEqual(loaded.contents().get(undated).storedAt(), 99_999L, "a varredura põe a data");
        helper.succeed();
    }
}
