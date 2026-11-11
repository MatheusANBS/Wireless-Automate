package io.github.matheusanbs.wirelessautomate.gametest;

import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.block.RouterBlock;
import io.github.matheusanbs.wirelessautomate.block.RouterTier;
import io.github.matheusanbs.wirelessautomate.dock.DockDelivery;
import io.github.matheusanbs.wirelessautomate.dock.DockMachine;
import io.github.matheusanbs.wirelessautomate.dock.DockPendingContents;
import io.github.matheusanbs.wirelessautomate.dock.DockPendingLot;
import io.github.matheusanbs.wirelessautomate.dock.DockSide;
import io.github.matheusanbs.wirelessautomate.dock.PatternDockBlockEntity;
import io.github.matheusanbs.wirelessautomate.network.Chemicals;
import io.github.matheusanbs.wirelessautomate.network.NetworkManager;
import io.github.matheusanbs.wirelessautomate.network.PortMode;
import io.github.matheusanbs.wirelessautomate.network.ResourceType;
import io.github.matheusanbs.wirelessautomate.range.RemoteSide;
import io.github.matheusanbs.wirelessautomate.registry.ModBlocks;
import io.github.matheusanbs.wirelessautomate.registry.ModDataComponents;
import io.github.matheusanbs.wirelessautomate.storage.StorageBlockItem;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * O pendente por máquina e por item ({@link DockPendingLot}): a migração do NBT e do componente antigos (montados à mão
 * no formato até o 7470507: pilhas de até 99 e {@code pending_at}), a linha só de um resto enorme no NBT, a contagem
 * {@link DockSide#freeMachines} depois de salvar e carregar, o pendente de uma máquina desvinculada que vai à outra do
 * lado, e o {@code long} do componente. A regra "a outra máquina recebe" está no
 * {@code DockDeliveryGameTests.pendingHoldsOnlyItsMachine}.
 */
@GameTestHolder(WirelessAutomate.MODID)
@PrefixGameTestTemplate(false)
public final class DockPendingGameTests {
    private static final BlockPos DOCK = new BlockPos(1, 1, 1);
    private static final BlockPos FIRST = new BlockPos(3, 1, 1);
    private static final BlockPos SECOND = new BlockPos(3, 1, 3);
    private static final int NORTH = Direction.NORTH.ordinal();
    private static final ResourceLocation FURNACE = BuiltInRegistries.BLOCK.getKey(Blocks.FURNACE);

    private DockPendingGameTests() {
    }

    /** A Doca Básica e duas fornalhas viradas para o norte, ainda sem vínculo. */
    private static PatternDockBlockEntity setUp(GameTestHelper helper) {
        helper.setBlock(DOCK, ModBlocks.PATTERN_DOCK.get().defaultBlockState().setValue(RouterBlock.TIER,
                RouterTier.BASIC));
        for (BlockPos pos : List.of(FIRST, SECOND)) {
            helper.setBlock(pos, Blocks.FURNACE.defaultBlockState().setValue(AbstractFurnaceBlock.FACING,
                    Direction.NORTH));
        }
        return helper.getBlockEntity(DOCK);
    }

    /** Vincula as duas fornalhas ao Norte, com o perfil da fornalha (Cima e Trás Insere, Baixo Extrai). */
    private static void linkBoth(GameTestHelper helper, PatternDockBlockEntity dock) {
        for (BlockPos pos : List.of(FIRST, SECOND)) {
            helper.assertTrue(dock.link(NORTH, helper.absolutePos(pos), FURNACE, Direction.NORTH.ordinal()),
                    "não vinculou " + pos);
        }
        DockSide side = dock.side(NORTH);
        side.profile().face(ResourceType.ITEM, RemoteSide.TOP).setMode(PortMode.INSERT);
        side.profile().face(ResourceType.ITEM, RemoteSide.BACK).setMode(PortMode.INSERT);
        side.profile().face(ResourceType.ITEM, RemoteSide.BOTTOM).setMode(PortMode.EXTRACT);
    }

    private static void room() {
        if (!NetworkManager.get().budget().outsideRoom()) {
            throw new GameTestAssertException("sem orçamento neste tick");
        }
    }

    private static DockPendingLot lot(GameTestHelper helper, PatternDockBlockEntity dock, BlockPos relative) {
        DockMachine machine = dock.machineAt(helper.absolutePos(relative));
        return machine == null ? null : machine.pending();
    }

    private static CompoundTag legacyItem(HolderLookup.Provider registries, ItemStack stack) {
        CompoundTag entry = new CompoundTag();
        entry.put("item", stack.save(registries));
        return entry;
    }

    /**
     * O NBT antigo de um lado (a lista {@code pending} com o pedregulho em partes de 99 e 51, o carvão e a água, e o
     * {@code pending_at} na primeira fornalha) carrega como um lote da primeira fornalha, uma linha por item; salvo de
     * novo, sai só no formato novo. O {@code pending_at} na posição da Doca (o item recolocado) vira pendente solto.
     */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void legacyNbtBecomesLines(GameTestHelper helper) {
        PatternDockBlockEntity dock = setUp(helper);
        HolderLookup.Provider registries = helper.getLevel().registryAccess();
        helper.startSequence()
                .thenIdle(1)
                .thenExecute(() -> {
                    linkBoth(helper, dock);
                    CompoundTag tag = dock.saveWithoutMetadata(registries);
                    CompoundTag side = tag.getList("sides", Tag.TAG_COMPOUND).getCompound(0);
                    ListTag pending = new ListTag();
                    pending.add(legacyItem(registries, new ItemStack(Items.COBBLESTONE, 99)));
                    pending.add(legacyItem(registries, new ItemStack(Items.COBBLESTONE, 51)));
                    pending.add(legacyItem(registries, new ItemStack(Items.COAL, 3)));
                    CompoundTag fluid = new CompoundTag();
                    fluid.put("fluid", new FluidStack(Fluids.WATER, 1_000).save(registries));
                    pending.add(fluid);
                    side.put("pending", pending);
                    side.putLong("pending_at", helper.absolutePos(FIRST).asLong());
                    dock.loadWithComponents(tag, registries);

                    DockSide loaded = dock.side(NORTH);
                    DockPendingLot lot = lot(helper, dock, FIRST);
                    helper.assertTrue(lot != null && lot.items().size() == 2 && lot.count(Items.COBBLESTONE) == 150
                            && lot.count(Items.COAL) == 3 && lot.amount(Fluids.WATER) == 1_000,
                            "lote: " + (lot == null ? null : lot.items()));
                    helper.assertTrue(lot(helper, dock, SECOND) == null && loaded.loose() == null,
                            "pendente fora da primeira");
                    helper.assertTrue(loaded.freeMachines() == 1, "livres: " + loaded.freeMachines());

                    CompoundTag again = dock.saveWithoutMetadata(registries).getList("sides", Tag.TAG_COMPOUND)
                            .getCompound(0);
                    helper.assertFalse(again.contains("pending") || again.contains("pending_at"),
                            "formato antigo salvo de novo: " + again);
                    ListTag items = again.getList("machines", Tag.TAG_COMPOUND).getCompound(0).getCompound("pending")
                            .getList("items", Tag.TAG_COMPOUND);
                    helper.assertTrue(items.size() == 2 && items.getCompound(0).getLong("count") == 150,
                            "NBT novo: " + items);

                    // o pending_at na posição da Doca (o item recolocado) não é de máquina: o pendente fica solto
                    side.putLong("pending_at", dock.getBlockPos().asLong());
                    dock.loadWithComponents(tag, registries);
                    DockPendingLot loose = dock.side(NORTH).loose();
                    helper.assertTrue(loose != null && loose.count(Items.COBBLESTONE) == 150
                            && lot(helper, dock, FIRST) == null, "solto: " + (loose == null ? null : loose.items()));
                })
                .thenSucceed();
    }

    /**
     * O componente antigo ({@code items} em pilhas de até 99 e {@code fluids}, montado à mão) é lido como linhas; salvo
     * de novo, sai só com {@code item_lines} e {@code fluid_lines}; a Doca colocada com ele guarda o pendente solto.
     * Uma linha maior que um {@code int} passa inteira pelo codec.
     */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void legacyComponentBecomesLines(GameTestHelper helper) {
        PatternDockBlockEntity dock = setUp(helper);
        HolderLookup.Provider registries = helper.getLevel().registryAccess();
        var ops = registries.createSerializationContext(NbtOps.INSTANCE);
        CompoundTag side = new CompoundTag();
        side.putInt("side", NORTH);
        side.putString("block", FURNACE.toString());
        ListTag items = new ListTag();
        items.add(new ItemStack(Items.COBBLESTONE, 99).save(registries));
        items.add(new ItemStack(Items.COBBLESTONE, 51).save(registries));
        side.put("items", items);
        ListTag fluids = new ListTag();
        fluids.add(new FluidStack(Fluids.LAVA, 250).save(registries));
        side.put("fluids", fluids);
        ListTag legacy = new ListTag();
        legacy.add(side);

        DockPendingContents contents = DockPendingContents.CODEC.parse(ops, legacy).getOrThrow();
        DockPendingContents.Side read = contents.sides().get(0);
        helper.assertTrue(read.items().size() == 1 && read.count(new ItemStack(Items.COBBLESTONE)) == 150
                && read.fluids().size() == 1 && read.fluids().get(0).amount() == 250, "lido: " + read);
        CompoundTag written = ((ListTag) DockPendingContents.CODEC.encodeStart(ops, contents).getOrThrow())
                .getCompound(0);
        helper.assertTrue(written.contains("item_lines") && written.contains("fluid_lines")
                && !written.contains("items") && !written.contains("fluids"), "escrito: " + written);

        long huge = 5_000_000_000L;
        DockPendingContents big = new DockPendingContents(List.of(new DockPendingContents.Side(NORTH, FURNACE,
                List.of(new DockPendingContents.ItemLine(new ItemStack(Items.COBBLESTONE), huge)), List.of(),
                List.of())));
        DockPendingContents back = DockPendingContents.CODEC.parse(ops,
                DockPendingContents.CODEC.encodeStart(ops, big).getOrThrow()).getOrThrow();
        helper.assertTrue(back.equals(big) && back.sides().get(0).items().get(0).count() == huge,
                "long: " + back);

        ItemStack item = StorageBlockItem.withTier(ModBlocks.PATTERN_DOCK.get().asItem(), RouterTier.BASIC);
        item.set(ModDataComponents.PATTERN_DOCK_PENDING.get(), contents);
        dock.applyComponentsFromItemStack(item);
        DockSide placed = dock.side(NORTH);
        helper.assertTrue(placed != null && FURNACE.equals(placed.block()) && placed.loose() != null
                && placed.loose().count(Items.COBBLESTONE) == 150 && placed.loose().amount(Fluids.LAVA) == 250,
                "colocada: " + (placed == null ? null : placed.loose()));
        helper.succeed();
    }

    /**
     * Um resto enorme (255 pilhas de pedregulho) vai ao NBT como uma linha, e volta igual; a primeira fornalha com
     * pendente e a segunda livre contam {@code freeMachines} 1 depois de carregar.
     */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void hugeRestSavesOneLine(GameTestHelper helper) {
        PatternDockBlockEntity dock = setUp(helper);
        HolderLookup.Provider registries = helper.getLevel().registryAccess();
        helper.startSequence()
                .thenIdle(1)
                .thenExecute(() -> {
                    linkBoth(helper, dock);
                    dock.side(NORTH).setPending(List.of(new ItemStack(Items.COBBLESTONE, 255 * 64)), List.of(),
                            helper.absolutePos(FIRST));
                    CompoundTag tag = dock.saveWithoutMetadata(registries);
                    ListTag items = tag.getList("sides", Tag.TAG_COMPOUND).getCompound(0)
                            .getList("machines", Tag.TAG_COMPOUND).getCompound(0).getCompound("pending")
                            .getList("items", Tag.TAG_COMPOUND);
                    helper.assertTrue(items.size() == 1 && items.getCompound(0).getLong("count") == 255 * 64,
                            "NBT: " + items);
                    dock.loadWithComponents(tag, registries);
                    DockPendingLot lot = lot(helper, dock, FIRST);
                    helper.assertTrue(lot != null && lot.count(Items.COBBLESTONE) == 255 * 64,
                            "carregado: " + (lot == null ? null : lot.items()));
                    int free = dock.side(NORTH).freeMachines();
                    helper.assertTrue(free == 1, "livres: " + free);
                    helper.assertTrue(DockDelivery.acceptsOrders(dock, NORTH), "a segunda não recebe");
                })
                .thenSucceed();
    }

    /**
     * As duas fornalhas com sobra (dois pedidos maiores que a entrada): o lado não aceita, e continua sem aceitar
     * depois de salvar e carregar (a contagem volta com o registro no índice). Desvinculada a primeira, o pendente dela
     * fica solto e vai à segunda, junto ao dela, sem perder nem duplicar.
     */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void unlinkedBusyMachinePassesPending(GameTestHelper helper) {
        PatternDockBlockEntity dock = setUp(helper);
        HolderLookup.Provider registries = helper.getLevel().registryAccess();
        List<ItemStack> order = List.of(new ItemStack(Items.IRON_ORE, 40), new ItemStack(Items.IRON_ORE, 40));
        helper.startSequence()
                .thenIdle(1)
                .thenExecute(() -> linkBoth(helper, dock))
                .thenWaitUntil(DockPendingGameTests::room)
                .thenExecute(() -> helper.assertTrue(DockDelivery.offer(dock, NORTH, order, List.of(), false)
                        == DockDelivery.Result.ACCEPTED, "a primeira recusou"))
                .thenWaitUntil(DockPendingGameTests::room)
                .thenExecute(() -> {
                    helper.assertTrue(DockDelivery.offer(dock, NORTH, order, List.of(), false)
                            == DockDelivery.Result.ACCEPTED, "a segunda recusou");
                    helper.assertTrue(lot(helper, dock, FIRST).count(Items.IRON_ORE) == 16
                            && lot(helper, dock, SECOND).count(Items.IRON_ORE) == 16, "pendentes");
                    helper.assertFalse(DockDelivery.acceptsOrders(dock, NORTH), "aceita sem máquina livre");
                    dock.loadWithComponents(dock.saveWithoutMetadata(registries), registries);
                    DockSide side = dock.side(NORTH);
                    helper.assertTrue(side.freeMachines() == 0 && side.waitingOnPending(),
                            "livres depois de carregar: " + side.freeMachines());
                    helper.assertFalse(DockDelivery.acceptsOrders(dock, NORTH), "aceita depois de carregar");
                    // a segunda esvazia: o pendente dela sai, e o da primeira, solto, vai a ela
                    ((AbstractFurnaceBlockEntity) helper.getBlockEntity(SECOND)).setItem(0, ItemStack.EMPTY);
                    helper.assertTrue(dock.unlink(NORTH, helper.absolutePos(FIRST)), "não desvinculou");
                    helper.assertTrue(side.loose() != null && side.loose().count(Items.IRON_ORE) == 16,
                            "o pendente da primeira sumiu");
                })
                // o desvínculo enfileira a tentativa do solto, que só roda com folga no orçamento: o teste a refaz
                .thenWaitUntil(() -> {
                    room();
                    DockDelivery.flushPending(dock, NORTH);
                    helper.assertFalse(dock.side(NORTH).hasPending(), "o pendente não saiu");
                })
                .thenExecute(() -> {
                    ItemStack second = ((AbstractFurnaceBlockEntity) helper.getBlockEntity(SECOND)).getItem(0);
                    helper.assertTrue(second.is(Items.IRON_ORE) && second.getCount() == 32, "segunda: " + second);
                    ItemStack first = ((AbstractFurnaceBlockEntity) helper.getBlockEntity(FIRST)).getItem(0);
                    helper.assertTrue(first.getCount() == 64, "primeira: " + first);
                    helper.assertTrue(DockDelivery.acceptsOrders(dock, NORTH), "o lado não voltou a aceitar");
                })
                .thenSucceed();
    }

    /** Sem o Mekanism, o químico do NBT antigo fica cru, no pendente solto salvo de novo, sem se perder. */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void legacyRawChemicalKept(GameTestHelper helper) {
        if (Chemicals.LOADED) {
            helper.succeed();
            return;
        }
        PatternDockBlockEntity dock = setUp(helper);
        HolderLookup.Provider registries = helper.getLevel().registryAccess();
        helper.startSequence()
                .thenIdle(1)
                .thenExecute(() -> {
                    linkBoth(helper, dock);
                    CompoundTag tag = dock.saveWithoutMetadata(registries);
                    CompoundTag side = tag.getList("sides", Tag.TAG_COMPOUND).getCompound(0);
                    CompoundTag chemical = new CompoundTag();
                    chemical.putString("id", "mekanism:hydrogen");
                    chemical.putLong("amount", 100);
                    CompoundTag entry = new CompoundTag();
                    entry.put("chemical", chemical);
                    ListTag pending = new ListTag();
                    pending.add(entry);
                    side.put("pending", pending);
                    side.putLong("pending_at", helper.absolutePos(FIRST).asLong());
                    dock.loadWithComponents(tag, registries);
                    DockSide loaded = dock.side(NORTH);
                    helper.assertTrue(loaded.rawChemicals().equals(List.of(chemical)) && !loaded.hasPending()
                            && loaded.keepsPending(), "cru: " + loaded.rawChemicals());
                    ListTag saved = dock.saveWithoutMetadata(registries).getList("sides", Tag.TAG_COMPOUND)
                            .getCompound(0).getCompound("loose_pending").getList("chemicals", Tag.TAG_COMPOUND);
                    helper.assertTrue(saved.size() == 1 && chemical.equals(saved.getCompound(0)), "NBT: " + saved);
                })
                .thenSucceed();
    }
}
