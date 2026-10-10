package io.github.matheusanbs.wirelessautomate.gametest;

import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.block.RouterBlock;
import io.github.matheusanbs.wirelessautomate.block.RouterBlockEntity;
import io.github.matheusanbs.wirelessautomate.filter.Filter;
import io.github.matheusanbs.wirelessautomate.filter.FilterEntry;
import io.github.matheusanbs.wirelessautomate.filter.FilterTags;
import io.github.matheusanbs.wirelessautomate.network.FaceConfig;
import io.github.matheusanbs.wirelessautomate.network.NetworkManager;
import io.github.matheusanbs.wirelessautomate.network.NetworkSavedData;
import io.github.matheusanbs.wirelessautomate.network.NetworkStats;
import io.github.matheusanbs.wirelessautomate.network.PortMode;
import io.github.matheusanbs.wirelessautomate.network.ResourceType;
import io.github.matheusanbs.wirelessautomate.network.RouterPreset;
import io.github.matheusanbs.wirelessautomate.registry.ModBlocks;
import io.github.matheusanbs.wirelessautomate.registry.ModDataComponents;
import io.github.matheusanbs.wirelessautomate.registry.ModItems;
import io.netty.buffer.Unpooled;
import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import io.github.matheusanbs.wirelessautomate.net.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import com.mojang.serialization.DynamicOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TagsUpdatedEvent;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import org.jetbrains.annotations.Nullable;

/**
 * Filtros: a correspondência (exato, tag, mod, componentes, estoque), o uso no motor (origem e
 * destino, itens e fluidos) e a persistência (block entity, preset do Configurador). Mesma
 * montagem dos {@code TransferGameTests}: máquinas em y=1 com o roteador em cima, face {@link Direction#UP}.
 */
@GameTestHolder(WirelessAutomate.MODID)
@PrefixGameTestTemplate(false)
public final class FilterGameTests {
    private static final BlockPos A = new BlockPos(0, 1, 0);
    private static final BlockPos B = new BlockPos(2, 1, 2);
    private static final BlockPos C = new BlockPos(2, 1, 0);
    private static final BlockPos D = new BlockPos(0, 1, 2);
    private static final ResourceLocation LOGS = new ResourceLocation("logs");

    private static UUID newNetwork(GameTestHelper helper, String name) {
        return NetworkSavedData.get(helper.getLevel().getServer()).create(UUID.randomUUID(), name).id();
    }

    private static RouterBlockEntity place(GameTestHelper helper, BlockPos machine, BlockState machineState,
            @Nullable UUID network) {
        helper.setBlock(machine, machineState);
        BlockPos routerPos = machine.above();
        helper.setBlock(routerPos, ModBlocks.ROUTER.get().defaultBlockState().setValue(RouterBlock.FACING, Direction.UP));
        RouterBlockEntity router = GameTestCompat.getBlockEntity(helper, routerPos);
        router.setNetworkId(network);
        return router;
    }

    private static RouterBlockEntity chest(GameTestHelper helper, BlockPos pos, UUID network, PortMode mode,
            Filter filter) {
        RouterBlockEntity router = place(helper, pos, Blocks.CHEST.defaultBlockState(), network);
        router.setMode(ResourceType.ITEM, Direction.UP, mode);
        router.setFilter(ResourceType.ITEM, Direction.UP, filter);
        return router;
    }

    private static ChestBlockEntity chestAt(GameTestHelper helper, BlockPos pos) {
        return GameTestCompat.getBlockEntity(helper, pos);
    }

    private static int count(GameTestHelper helper, BlockPos pos, Predicate<ItemStack> which) {
        ChestBlockEntity chest = chestAt(helper, pos);
        int total = 0;
        for (int i = 0; i < chest.getContainerSize(); i++) {
            ItemStack stack = chest.getItem(i);
            if (!stack.isEmpty() && which.test(stack)) {
                total += stack.getCount();
            }
        }
        return total;
    }

    private static void assertCount(GameTestHelper helper, BlockPos pos, Item item, int expected) {
        GameTestCompat.assertValueEqual(helper, count(helper, pos, stack -> stack.is(item)), expected, item + " em " + pos.toShortString());
    }

    private static void waitRegistered(GameTestHelper helper, RouterBlockEntity... routers) {
        for (RouterBlockEntity router : routers) {
            helper.assertTrue(NetworkManager.get().contains(router), "roteador não registrado");
        }
    }

    private static Filter filter(Filter.ListMode mode, boolean components, FilterEntry... entries) {
        return new Filter(mode, components, List.of(entries));
    }

    private static Filter whitelist(FilterEntry... entries) {
        return filter(Filter.ListMode.WHITELIST, false, entries);
    }

    private static Filter blacklist(FilterEntry... entries) {
        return filter(Filter.ListMode.BLACKLIST, false, entries);
    }

    private static FilterEntry item(Item item) {
        return new FilterEntry.ItemEntry(new ItemStack(item), 0);
    }

    private static FilterEntry item(ItemStack stack, long stock) {
        return new FilterEntry.ItemEntry(stack, stock);
    }

    private static ItemStack named(Item item, int count) {
        ItemStack stack = new ItemStack(item, count);
        stack.setHoverName(Component.literal("Joia"));
        return stack;
    }

    @GameTest(template = "empty")
    public static void matchingRules(GameTestHelper helper) {
        ItemStack diamond = new ItemStack(Items.DIAMOND);
        ItemStack stone = new ItemStack(Items.STONE);
        ItemStack log = new ItemStack(Items.OAK_LOG);
        ItemStack linker = new ItemStack(ModItems.LINKER.get());
        FluidStack water = new FluidStack(Fluids.WATER, 1000);
        FluidStack lava = new FluidStack(Fluids.LAVA, 1000);

        for (Filter empty : new Filter[] {Filter.EMPTY, blacklist()}) {
            helper.assertTrue(empty.testItem(stone) && empty.testFluid(lava), "sem entradas não passou tudo");
            GameTestCompat.assertValueEqual(helper, empty.itemStock(stone), 0L, "estoque sem entradas");
        }

        Filter exact = whitelist(item(Items.DIAMOND));
        helper.assertTrue(exact.testItem(diamond) && !exact.testItem(stone), "exato");
        helper.assertTrue(exact.testItem(new ItemStack(Items.DIAMOND, 64)), "a quantidade importou");
        helper.assertTrue(!exact.testFluid(water), "entrada de item casou com fluido");
        Filter notDiamond = blacklist(item(Items.DIAMOND));
        helper.assertTrue(!notDiamond.testItem(diamond) && notDiamond.testItem(stone), "lista negra não inverteu");

        Filter logs = whitelist(new FilterEntry.TagEntry(LOGS, 0));
        for (int i = 0; i < 2; i++) {
            // A segunda volta passa pelo cache por item.
            helper.assertTrue(logs.testItem(log) && logs.testItem(new ItemStack(Items.BIRCH_LOG)), "tag");
            helper.assertTrue(!logs.testItem(diamond), "tag casou com diamante");
        }
        // Recarga de tags: o cache é descartado e a resposta continua certa.
        FilterTags.onTagsUpdated(new TagsUpdatedEvent(helper.getLevel().registryAccess(), false, false));
        helper.assertTrue(logs.testItem(log) && !logs.testItem(diamond), "tag depois da recarga");

        Filter vanilla = whitelist(new FilterEntry.ModEntry("minecraft", 0));
        helper.assertTrue(vanilla.testItem(diamond) && !vanilla.testItem(linker), "mod minecraft");
        helper.assertTrue(vanilla.testFluid(lava), "mod minecraft em fluido");
        Filter ours = whitelist(new FilterEntry.ModEntry(WirelessAutomate.MODID, 0));
        helper.assertTrue(ours.testItem(linker) && !ours.testItem(diamond), "mod do Wireless Automate");

        ItemStack jewel = named(Items.DIAMOND, 1);
        Filter loose = whitelist(item(jewel, 0));
        Filter strict = filter(Filter.ListMode.WHITELIST, true, item(jewel, 0));
        helper.assertTrue(loose.testItem(diamond) && loose.testItem(jewel), "componentes ignorados");
        helper.assertTrue(!strict.testItem(diamond) && strict.testItem(named(Items.DIAMOND, 5)), "componentes exigidos");

        Filter stocked = whitelist(new FilterEntry.TagEntry(LOGS, 5), item(new ItemStack(Items.OAK_LOG), 9),
                item(new ItemStack(Items.DIAMOND), 3));
        GameTestCompat.assertValueEqual(helper, stocked.itemStock(log), 5L, "estoque da primeira entrada que casa");
        GameTestCompat.assertValueEqual(helper, stocked.itemStock(diamond), 3L, "estoque do exato");
        GameTestCompat.assertValueEqual(helper, stocked.itemStock(stone), 0L, "estoque de quem não casa");
        GameTestCompat.assertValueEqual(helper, stocked.withListMode(Filter.ListMode.BLACKLIST).itemStock(log), 0L,
                "estoque em lista negra");

        Filter fluids = whitelist(new FilterEntry.FluidEntry(water, 0));
        helper.assertTrue(fluids.testFluid(water) && !fluids.testFluid(lava), "fluido exato");
        helper.assertTrue(!fluids.testItem(diamond), "entrada de fluido casou com item");
        Filter waterTag = whitelist(new FilterEntry.TagEntry(new ResourceLocation("water"), 250));
        helper.assertTrue(waterTag.testFluid(water) && !waterTag.testFluid(lava), "tag de fluido");
        GameTestCompat.assertValueEqual(helper, waterTag.fluidStock(water), 250L, "estoque de fluido");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void whitelistOnSourceLetsOnlyListedLeave(GameTestHelper helper) {
        UUID network = newNetwork(helper, "filtro-branca-origem");
        RouterBlockEntity source = chest(helper, A, network, PortMode.EXTRACT, whitelist(item(Items.DIAMOND)));
        RouterBlockEntity target = chest(helper, B, network, PortMode.INSERT, Filter.EMPTY);
        chestAt(helper, A).setItem(0, new ItemStack(Items.COBBLESTONE, 5));
        chestAt(helper, A).setItem(1, new ItemStack(Items.DIAMOND, 10));

        helper.startSequence()
                .thenWaitUntil(() -> waitRegistered(helper, source, target))
                .thenWaitUntil(() -> assertCount(helper, B, Items.DIAMOND, 10))
                .thenIdle(10)
                .thenExecute(() -> {
                    assertCount(helper, A, Items.COBBLESTONE, 5);
                    assertCount(helper, B, Items.COBBLESTONE, 0);
                })
                .thenSucceed();
    }

    @GameTest(template = "empty")
    public static void blacklistOnDestinationRefusesWithoutBlockingOthers(GameTestHelper helper) {
        UUID network = newNetwork(helper, "filtro-negra-destino");
        RouterBlockEntity source = chest(helper, A, network, PortMode.EXTRACT, Filter.EMPTY);
        RouterBlockEntity target = chest(helper, B, network, PortMode.INSERT, blacklist(item(Items.COBBLESTONE)));
        chestAt(helper, A).setItem(0, new ItemStack(Items.COBBLESTONE, 5));

        helper.startSequence()
                .thenWaitUntil(() -> waitRegistered(helper, source, target))
                // Recusas repetidas do pedregulho: pelo backoff de "cheio", o destino já estaria
                // dormindo por dezenas de ticks; recusa de filtro não faz dormir.
                .thenIdle(40)
                .thenExecute(() -> {
                    NetworkStats stats = stats(helper, network);
                    helper.assertTrue(stats != null && stats.destinationsSleeping() == 0,
                            "recusa do filtro fez o destino dormir: " + stats);
                    assertCount(helper, A, Items.COBBLESTONE, 5);
                    chestAt(helper, A).setItem(1, new ItemStack(Items.DIAMOND, 10));
                })
                .thenExecuteAfter(3, () -> {
                    assertCount(helper, B, Items.DIAMOND, 10);
                    assertCount(helper, A, Items.COBBLESTONE, 5);
                    assertCount(helper, B, Items.COBBLESTONE, 0);
                })
                .thenSucceed();
    }

    @GameTest(template = "empty")
    public static void tagAndModEntries(GameTestHelper helper) {
        UUID logs = newNetwork(helper, "filtro-tag");
        RouterBlockEntity logSource = chest(helper, A, logs, PortMode.EXTRACT,
                whitelist(new FilterEntry.TagEntry(LOGS, 0)));
        RouterBlockEntity logTarget = chest(helper, B, logs, PortMode.INSERT, Filter.EMPTY);
        chestAt(helper, A).setItem(0, new ItemStack(Items.DIAMOND, 2));
        chestAt(helper, A).setItem(1, new ItemStack(Items.OAK_LOG, 4));
        chestAt(helper, A).setItem(2, new ItemStack(Items.BIRCH_LOG, 3));

        UUID mods = newNetwork(helper, "filtro-mod");
        RouterBlockEntity modSource = chest(helper, C, mods, PortMode.EXTRACT,
                whitelist(new FilterEntry.ModEntry(WirelessAutomate.MODID, 0)));
        RouterBlockEntity modTarget = chest(helper, D, mods, PortMode.INSERT, Filter.EMPTY);
        chestAt(helper, C).setItem(0, new ItemStack(Items.DIAMOND, 3));
        chestAt(helper, C).setItem(1, new ItemStack(ModItems.LINKER.get()));

        helper.startSequence()
                .thenWaitUntil(() -> waitRegistered(helper, logSource, logTarget, modSource, modTarget))
                .thenWaitUntil(() -> {
                    assertCount(helper, B, Items.OAK_LOG, 4);
                    assertCount(helper, B, Items.BIRCH_LOG, 3);
                    assertCount(helper, D, ModItems.LINKER.get(), 1);
                })
                .thenIdle(10)
                .thenExecute(() -> {
                    assertCount(helper, A, Items.DIAMOND, 2);
                    assertCount(helper, C, Items.DIAMOND, 3);
                    assertCount(helper, D, Items.DIAMOND, 0);
                })
                .thenSucceed();
    }

    @GameTest(template = "empty")
    public static void matchComponentsSeparatesNamedItems(GameTestHelper helper) {
        ItemStack jewel = named(Items.DIAMOND, 1);
        UUID strictNetwork = newNetwork(helper, "filtro-componentes");
        RouterBlockEntity strict = chest(helper, A, strictNetwork, PortMode.EXTRACT,
                filter(Filter.ListMode.WHITELIST, true, item(jewel, 0)));
        RouterBlockEntity strictTarget = chest(helper, B, strictNetwork, PortMode.INSERT, Filter.EMPTY);
        chestAt(helper, A).setItem(0, new ItemStack(Items.DIAMOND, 5));
        chestAt(helper, A).setItem(1, named(Items.DIAMOND, 2));

        UUID looseNetwork = newNetwork(helper, "filtro-sem-componentes");
        RouterBlockEntity loose = chest(helper, C, looseNetwork, PortMode.EXTRACT, whitelist(item(jewel, 0)));
        RouterBlockEntity looseTarget = chest(helper, D, looseNetwork, PortMode.INSERT, Filter.EMPTY);
        chestAt(helper, C).setItem(0, new ItemStack(Items.DIAMOND, 5));
        chestAt(helper, C).setItem(1, named(Items.DIAMOND, 2));

        Predicate<ItemStack> isJewel = stack -> stack.hasCustomHoverName();
        helper.startSequence()
                .thenWaitUntil(() -> waitRegistered(helper, strict, strictTarget, loose, looseTarget))
                .thenWaitUntil(() -> {
                    GameTestCompat.assertValueEqual(helper, count(helper, B, isJewel), 2, "nomeados no destino");
                    assertCount(helper, D, Items.DIAMOND, 7);
                })
                .thenIdle(10)
                .thenExecute(() -> {
                    assertCount(helper, A, Items.DIAMOND, 5);
                    assertCount(helper, B, Items.DIAMOND, 2);
                })
                .thenSucceed();
    }

    @GameTest(template = "empty")
    public static void sourceStockKeepsN(GameTestHelper helper) {
        UUID network = newNetwork(helper, "filtro-estoque-origem");
        RouterBlockEntity source = chest(helper, A, network, PortMode.EXTRACT,
                whitelist(item(new ItemStack(Items.DIAMOND), 4)));
        RouterBlockEntity target = chest(helper, B, network, PortMode.INSERT, Filter.EMPTY);
        chestAt(helper, A).setItem(0, new ItemStack(Items.DIAMOND, 6));
        chestAt(helper, A).setItem(3, new ItemStack(Items.DIAMOND, 4));

        helper.startSequence()
                .thenWaitUntil(() -> waitRegistered(helper, source, target))
                .thenWaitUntil(() -> assertCount(helper, B, Items.DIAMOND, 6))
                .thenIdle(20)
                .thenExecute(() -> {
                    assertCount(helper, A, Items.DIAMOND, 4);
                    assertCount(helper, B, Items.DIAMOND, 6);
                    // Chegam mais 3: só eles saem.
                    chestAt(helper, A).setItem(10, new ItemStack(Items.DIAMOND, 3));
                })
                .thenWaitUntil(() -> assertCount(helper, B, Items.DIAMOND, 9))
                .thenIdle(10)
                .thenExecute(() -> assertCount(helper, A, Items.DIAMOND, 4))
                .thenSucceed();
    }

    @GameTest(template = "empty")
    public static void destinationStockAcceptsUpToN(GameTestHelper helper) {
        UUID network = newNetwork(helper, "filtro-estoque-destino");
        RouterBlockEntity source = chest(helper, A, network, PortMode.EXTRACT, Filter.EMPTY);
        RouterBlockEntity target = chest(helper, B, network, PortMode.INSERT,
                whitelist(item(new ItemStack(Items.DIAMOND), 7)));
        chestAt(helper, A).setItem(0, new ItemStack(Items.DIAMOND, 20));

        helper.startSequence()
                .thenWaitUntil(() -> waitRegistered(helper, source, target))
                .thenWaitUntil(() -> assertCount(helper, B, Items.DIAMOND, 7))
                .thenIdle(20)
                .thenExecute(() -> {
                    assertCount(helper, A, Items.DIAMOND, 13);
                    assertCount(helper, B, Items.DIAMOND, 7);
                    // Gastou 3 no destino: o baú avisa a mudança e ele completa de novo.
                    ChestBlockEntity chest = chestAt(helper, B);
                    for (int i = 0; i < chest.getContainerSize(); i++) {
                        chest.setItem(i, ItemStack.EMPTY);
                    }
                    chest.setItem(0, new ItemStack(Items.DIAMOND, 4));
                })
                .thenWaitUntil(() -> {
                    assertCount(helper, B, Items.DIAMOND, 7);
                    assertCount(helper, A, Items.DIAMOND, 10);
                })
                .thenSucceed();
    }

    /**
     * Destino com estoque e uma origem com muitas pilhas do item: na mesma visita, a contagem do
     * destino é lembrada e soma o que entregamos, e ele para exatamente no estoque.
     */
    @GameTest(template = "empty")
    public static void destinationStockHoldsAcrossManySourceSlots(GameTestHelper helper) {
        UUID network = newNetwork(helper, "filtro-estoque-muitas-pilhas");
        RouterBlockEntity source = chest(helper, A, network, PortMode.EXTRACT, Filter.EMPTY);
        RouterBlockEntity target = chest(helper, B, network, PortMode.INSERT,
                whitelist(item(new ItemStack(Items.DIAMOND), 7)));
        for (int slot = 0; slot < 20; slot++) {
            chestAt(helper, A).setItem(slot, new ItemStack(Items.DIAMOND, 1));
        }

        helper.startSequence()
                .thenWaitUntil(() -> waitRegistered(helper, source, target))
                .thenWaitUntil(() -> assertCount(helper, B, Items.DIAMOND, 7))
                .thenIdle(20)
                .thenExecute(() -> {
                    assertCount(helper, A, Items.DIAMOND, 13);
                    assertCount(helper, B, Items.DIAMOND, 7);
                    // Gastou 2: completa de novo, com a contagem refeita na visita seguinte.
                    ChestBlockEntity chest = chestAt(helper, B);
                    for (int i = 0; i < chest.getContainerSize(); i++) {
                        chest.setItem(i, ItemStack.EMPTY);
                    }
                    chest.setItem(4, new ItemStack(Items.DIAMOND, 5));
                })
                .thenWaitUntil(() -> {
                    assertCount(helper, B, Items.DIAMOND, 7);
                    assertCount(helper, A, Items.DIAMOND, 11);
                })
                .thenIdle(10)
                .thenExecute(() -> assertCount(helper, B, Items.DIAMOND, 7))
                .thenSucceed();
    }

    /**
     * Destino que já tem o estoque, espalhado em vários slots, e uma origem com muitas pilhas: a
     * varredura para no estoque e nada é entregue, visita após visita.
     */
    @GameTest(template = "empty")
    public static void destinationAtStockReceivesNothing(GameTestHelper helper) {
        UUID network = newNetwork(helper, "filtro-estoque-atingido");
        RouterBlockEntity source = chest(helper, A, network, PortMode.EXTRACT, Filter.EMPTY);
        RouterBlockEntity target = chest(helper, B, network, PortMode.INSERT,
                whitelist(item(new ItemStack(Items.DIAMOND), 7)));
        for (int slot = 0; slot < 27; slot++) {
            chestAt(helper, A).setItem(slot, new ItemStack(Items.DIAMOND, 2));
        }
        for (int slot = 0; slot < 7; slot++) {
            chestAt(helper, B).setItem(slot * 3, new ItemStack(Items.DIAMOND, 1));
        }

        helper.startSequence()
                .thenWaitUntil(() -> waitRegistered(helper, source, target))
                .thenIdle(40)
                .thenExecute(() -> {
                    assertCount(helper, A, Items.DIAMOND, 54);
                    assertCount(helper, B, Items.DIAMOND, 7);
                })
                .thenSucceed();
    }

    /**
     * Tanque de teste em {@code pos} ({@link TestMachines#SIMPLE_TANK}, com {@code content}), com um roteador em
     * cima. Porte 1.20.1: no {@code main} este teste usava caldeirões, que o NeoForge expõe como handler de fluido e
     * o Forge 1.20.1 não.
     */
    private static RouterBlockEntity simpleTank(GameTestHelper helper, BlockPos pos, FluidStack content, UUID network) {
        BlockPos machine = helper.absolutePos(pos);
        TestMachines.reset(machine);
        if (!content.isEmpty()) {
            TestMachines.simpleTank(machine).fill(content, IFluidHandler.FluidAction.EXECUTE);
        }
        return place(helper, pos, TestMachines.SIMPLE_TANK.get().defaultBlockState(), network);
    }

    private static int amount(GameTestHelper helper, BlockPos pos, net.minecraft.world.level.material.Fluid fluid) {
        FluidStack stored = TestMachines.simpleTank(helper.absolutePos(pos)).getFluid();
        return stored.getFluid() == fluid ? stored.getAmount() : 0;
    }

    @GameTest(template = "empty")
    public static void fluidFilterOnCauldrons(GameTestHelper helper) {
        if (!TestMachines.enabled()) {
            helper.succeed();
            return;
        }
        UUID waterNetwork = newNetwork(helper, "filtro-agua");
        RouterBlockEntity water = simpleTank(helper, A, new FluidStack(Fluids.WATER, 1_000), waterNetwork);
        water.setMode(ResourceType.FLUID, Direction.UP, PortMode.EXTRACT);
        water.setFilter(ResourceType.FLUID, Direction.UP,
                whitelist(new FilterEntry.FluidEntry(new FluidStack(Fluids.WATER, 1), 0)));
        RouterBlockEntity waterTarget = simpleTank(helper, B, FluidStack.EMPTY, waterNetwork);
        waterTarget.setMode(ResourceType.FLUID, Direction.UP, PortMode.INSERT);

        // A lava sai livre, mas o destino só aceita água.
        UUID lavaNetwork = newNetwork(helper, "filtro-lava");
        RouterBlockEntity lava = simpleTank(helper, C, new FluidStack(Fluids.LAVA, 1_000), lavaNetwork);
        lava.setMode(ResourceType.FLUID, Direction.UP, PortMode.EXTRACT);
        RouterBlockEntity lavaTarget = simpleTank(helper, D, FluidStack.EMPTY, lavaNetwork);
        lavaTarget.setMode(ResourceType.FLUID, Direction.UP, PortMode.INSERT);
        lavaTarget.setFilter(ResourceType.FLUID, Direction.UP,
                whitelist(new FilterEntry.FluidEntry(new FluidStack(Fluids.WATER, 1), 0)));

        helper.startSequence()
                .thenWaitUntil(() -> waitRegistered(helper, water, waterTarget, lava, lavaTarget))
                .thenWaitUntil(() -> {
                    GameTestCompat.assertValueEqual(helper, amount(helper, A, Fluids.WATER), 0, "água na origem");
                    GameTestCompat.assertValueEqual(helper, amount(helper, B, Fluids.WATER), 1_000, "água no destino");
                })
                .thenIdle(20)
                .thenExecute(() -> {
                    GameTestCompat.assertValueEqual(helper, amount(helper, C, Fluids.LAVA), 1_000, "lava na origem");
                    GameTestCompat.assertValueEqual(helper, amount(helper, D, Fluids.LAVA), 0, "lava no destino");
                })
                .thenSucceed();
    }

    @GameTest(template = "empty")
    public static void filterSurvivesSaveLoadAndConfigurator(GameTestHelper helper) {
        RouterBlockEntity node = place(helper, A, Blocks.CHEST.defaultBlockState(), null);
        RouterBlockEntity target = place(helper, B, Blocks.CHEST.defaultBlockState(), null);
        Filter itemFilter = filter(Filter.ListMode.BLACKLIST, true, item(named(Items.DIAMOND, 1), 3),
                new FilterEntry.TagEntry(LOGS, 0), new FilterEntry.ModEntry("mekanism", 12));
        Filter fluidFilter = whitelist(new FilterEntry.FluidEntry(new FluidStack(Fluids.WATER, 1), 1000));
        node.setMode(ResourceType.ITEM, Direction.UP, PortMode.EXTRACT);
        node.setFilter(ResourceType.ITEM, Direction.UP, itemFilter);
        node.setFilter(ResourceType.FLUID, Direction.NORTH, fluidFilter);

        // Block entity.
        CompoundTag saved = node.saveWithoutMetadata();
        RouterBlockEntity copy = new RouterBlockEntity(node.getBlockPos(), node.getBlockState());
        copy.load(saved);
        GameTestCompat.assertValueEqual(helper, copy.face(ResourceType.ITEM, Direction.UP).filter(), itemFilter, "filtro de itens");
        GameTestCompat.assertValueEqual(helper, copy.face(ResourceType.FLUID, Direction.NORTH).filter(), fluidFilter, "filtro de fluidos");

        // Tag antiga, sem filtro, e entradas que não leem (item que não existe, tipo desconhecido).
        CompoundTag old = new CompoundTag();
        old.putString("mode", "EXTRACT");
        FaceConfig parsedOld = FaceConfig.load(old);
        GameTestCompat.assertValueEqual(helper, parsedOld.mode(), PortMode.EXTRACT, "modo da tag antiga");
        GameTestCompat.assertValueEqual(helper, parsedOld.filter(), Filter.EMPTY, "tag antiga ganhou filtro");
        CompoundTag withFilter = new FaceConfig().save();
        helper.assertTrue(withFilter.isEmpty(), "face padrão salvou algo");
        FaceConfig configured = new FaceConfig();
        configured.setFilter(whitelist(item(Items.DIAMOND)));
        CompoundTag faceTag = configured.save();
        ListTag entries = faceTag.getCompound("filter").getList("entries", Tag.TAG_COMPOUND);
        CompoundTag missing = new CompoundTag();
        missing.putString("kind", "item");
        missing.putString("item", "naoexiste:coisa");
        entries.add(missing);
        CompoundTag unknown = new CompoundTag();
        unknown.putString("kind", "quimico");
        entries.add(unknown);
        FaceConfig lenient = FaceConfig.load(faceTag);
        GameTestCompat.assertValueEqual(helper, lenient.filter(), whitelist(item(Items.DIAMOND)), "entradas inválidas não foram puladas");

        // Preset: codecs com registros.
        RouterPreset preset = RouterPreset.copyOf(node);
        DynamicOps<Tag> ops = NbtOps.INSTANCE;
        Tag presetTag = RouterPreset.CODEC.encodeStart(ops, preset).getOrThrow(false, error -> {});
        GameTestCompat.assertValueEqual(helper, RouterPreset.CODEC.parse(ops, presetTag).getOrThrow(false, error -> {}), preset, "codec do preset");
        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer());
        try {
            RouterPreset.STREAM_CODEC.encode(buf, preset);
            GameTestCompat.assertValueEqual(helper, RouterPreset.STREAM_CODEC.decode(buf), preset, "stream codec do preset");
        } finally {
            buf.release();
        }

        // Configurador: copia de um roteador e cola no outro.
        @SuppressWarnings("removal")
        ServerPlayer player = GameTestCompat.makeMockServerPlayerInLevel(helper);
        ItemStack configurator = new ItemStack(ModItems.CONFIGURATOR.get());
        player.setItemInHand(InteractionHand.MAIN_HAND, configurator);
        helper.assertTrue(use(player, node, true), "copiar não agiu");
        helper.assertTrue(ModDataComponents.PRESET.has(configurator), "nada copiado");
        helper.assertTrue(use(player, target, false), "colar não agiu");
        GameTestCompat.assertValueEqual(helper, target.face(ResourceType.ITEM, Direction.UP).filter(), itemFilter, "filtro colado");
        GameTestCompat.assertValueEqual(helper, target.face(ResourceType.FLUID, Direction.NORTH).filter(), fluidFilter,
                "filtro de fluido colado");
        helper.succeed();
    }

    private static @Nullable NetworkStats stats(GameTestHelper helper, UUID network) {
        for (NetworkStats stats : NetworkManager.get().stats(helper.getLevel().getServer())) {
            if (stats.id().equals(network)) {
                return stats;
            }
        }
        return null;
    }

    private static boolean use(ServerPlayer player, RouterBlockEntity router, boolean sneak) {
        player.setShiftKeyDown(sneak);
        BlockPos pos = router.getBlockPos();
        UseOnContext context = new UseOnContext(player, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false));
        return ModItems.CONFIGURATOR.get().useOn(context).consumesAction();
    }
}
