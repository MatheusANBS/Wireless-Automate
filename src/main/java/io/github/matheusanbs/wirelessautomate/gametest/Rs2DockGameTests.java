package io.github.matheusanbs.wirelessautomate.gametest;

import io.github.matheusanbs.wirelessautomate.block.RouterBlock;
import io.github.matheusanbs.wirelessautomate.block.RouterTier;
import io.github.matheusanbs.wirelessautomate.dock.DockRs2;
import io.github.matheusanbs.wirelessautomate.dock.DockSide;
import io.github.matheusanbs.wirelessautomate.dock.DockStatus;
import io.github.matheusanbs.wirelessautomate.dock.PatternDockBlockEntity;
import io.github.matheusanbs.wirelessautomate.network.NetworkManager;
import io.github.matheusanbs.wirelessautomate.network.PortMode;
import io.github.matheusanbs.wirelessautomate.network.ResourceType;
import io.github.matheusanbs.wirelessautomate.range.RemoteSide;
import io.github.matheusanbs.wirelessautomate.registry.ModBlocks;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * A Doca com o Refined Storage 2 de verdade: o destino que o Autocrafter recebe da fábrica registrada no RS2 (o
 * conjunto dos destinos de todas as fábricas, chamado direto, sem rede), a posição sem Doca, a simulação e a execução
 * na mesma fornalha, as fornalhas cheias e o pendente que recusam sem perder nada, o recurso que não é item nem fluido,
 * a trava "até a máquina esvaziar" e o "tem provedor". Rodam só na run {@code runGameTestServerRefined}, que tem o RS2
 * na pasta mods e liga o namespace {@value #NAMESPACE}. Nenhum tipo do RS2 nas assinaturas: o que usa a API fica em
 * {@link Rs2TestSupport}.
 *
 * <p>O orçamento é do motor inteiro: cada oferta vem depois de um passo que espera folga ({@link #room}).
 */
@GameTestHolder(Rs2DockGameTests.NAMESPACE)
@PrefixGameTestTemplate(false)
public final class Rs2DockGameTests {
    static final String NAMESPACE = "wirelessautomate_refined";

    private static final BlockPos DOCK = new BlockPos(2, 1, 2);
    private static final BlockPos[] FURNACES = {new BlockPos(4, 1, 1), new BlockPos(4, 1, 2), new BlockPos(4, 1, 3)};
    private static final int NORTH = Direction.NORTH.ordinal();
    private static final ResourceLocation FURNACE_ID = BuiltInRegistries.BLOCK.getKey(Blocks.FURNACE);
    private static final String ACCEPTED = "ACCEPTED";
    private static final String REJECTED = "REJECTED";

    private Rs2DockGameTests() {
    }

    private static boolean enabled() {
        return Boolean.getBoolean("wirelessautomate.refinedTests") && ModList.get().isLoaded("refinedstorage");
    }

    private static Block rs2(String path) {
        return BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath("refinedstorage", path));
    }

    /** A run do RS2 carrega o RS2, com o Autocrafter no registro, e a ponte o vê. */
    @GameTest(template = "empty")
    public static void rs2IsLoaded(GameTestHelper helper) {
        if (!Boolean.getBoolean("wirelessautomate.refinedTests")) {
            helper.succeed();
            return;
        }
        helper.assertTrue(ModList.get().isLoaded("refinedstorage"), "o RS2 não carregou na run do RS2");
        helper.assertTrue(DockRs2.LOADED, "a ponte não vê o RS2");
        helper.assertTrue(rs2("autocrafter") != Blocks.AIR, "sem o Autocrafter no registro");
        helper.succeed();
    }

    private static PatternDockBlockEntity dock(GameTestHelper helper) {
        helper.setBlock(DOCK, ModBlocks.PATTERN_DOCK.get().defaultBlockState().setValue(RouterBlock.TIER, RouterTier.BASIC));
        return helper.getBlockEntity(DOCK);
    }

    /** As fornalhas viradas para o norte, vinculadas ao lado Norte com o perfil do Afinador. */
    private static void linkFurnaces(GameTestHelper helper, PatternDockBlockEntity dock, int count) {
        for (int i = 0; i < count; i++) {
            helper.setBlock(FURNACES[i], Blocks.FURNACE.defaultBlockState()
                    .setValue(AbstractFurnaceBlock.FACING, Direction.NORTH));
            helper.assertTrue(dock.link(NORTH, helper.absolutePos(FURNACES[i]), FURNACE_ID, Direction.NORTH.ordinal()),
                    "não vinculou a fornalha " + i);
        }
        DockSide side = dock.side(NORTH);
        side.profile().face(ResourceType.ITEM, RemoteSide.TOP).setMode(PortMode.INSERT);
        side.profile().face(ResourceType.ITEM, RemoteSide.BACK).setMode(PortMode.INSERT);
        side.profile().face(ResourceType.ITEM, RemoteSide.BOTTOM).setMode(PortMode.EXTRACT);
    }

    private static void registered(PatternDockBlockEntity dock) {
        if (!NetworkManager.get().hasVisitor(dock.returns())) {
            throw new GameTestAssertException("a Doca ainda não carregou");
        }
    }

    private static void room() {
        if (!NetworkManager.get().budget().outsideRoom()) {
            throw new GameTestAssertException("sem orçamento neste tick");
        }
    }

    /** O destino de um Autocrafter encostado no lado Norte da Doca. */
    private static Object sink(GameTestHelper helper) {
        return Rs2TestSupport.sink(helper.getLevel(), helper.absolutePos(DOCK), Direction.NORTH);
    }

    private static List<ItemStack> oreAndCoal() {
        return List.of(new ItemStack(Items.IRON_ORE), new ItemStack(Items.COAL));
    }

    private static String accept(Object sink, boolean simulate) {
        return Rs2TestSupport.accept(sink, oreAndCoal(), List.of(), false, simulate);
    }

    private static void assertSlot(GameTestHelper helper, BlockPos furnace, int slot, Item item, int count) {
        ItemStack stack = ((AbstractFurnaceBlockEntity) helper.getBlockEntity(furnace)).getItem(slot);
        helper.assertTrue(count == 0 ? stack.isEmpty() : stack.is(item) && stack.getCount() == count,
                "fornalha " + furnace.toShortString() + ", slot " + slot + ": " + stack);
    }

    private static void assertFurnacesUntouched(GameTestHelper helper, int count) {
        for (int i = 0; i < count; i++) {
            assertSlot(helper, FURNACES[i], 0, Items.COBBLESTONE, 64);
            assertSlot(helper, FURNACES[i], 1, Items.COAL, 0);
        }
    }

    /**
     * O destino é criado antes de haver Doca na posição e a confere a cada pedido: sem Doca, {@code SKIPPED}; com a
     * Doca e sem máquinas, recusado.
     */
    @GameTest(template = "empty")
    public static void noDockSkipped(GameTestHelper helper) {
        if (!enabled()) {
            helper.succeed();
            return;
        }
        Object sink = sink(helper);
        String empty = accept(sink, true);
        helper.assertTrue("SKIPPED".equals(empty), "sem Doca: " + empty);
        helper.assertTrue(Rs2TestSupport.isEmpty(sink), "sem Doca, não está vazio");
        PatternDockBlockEntity dock = dock(helper);
        helper.startSequence()
                .thenWaitUntil(() -> registered(dock))
                .thenWaitUntil(Rs2DockGameTests::room)
                .thenExecute(() -> {
                    String result = accept(sink, true);
                    helper.assertTrue(REJECTED.equals(result), "com a Doca sem máquinas: " + result);
                })
                .thenSucceed();
    }

    /**
     * A simulação escolhe a fornalha e não põe nada nela; a execução entrega nessa mesma fornalha, ainda que o cursor
     * tenha mudado entre as duas.
     */
    @GameTest(template = "empty")
    public static void simulateThenExecuteSameFurnace(GameTestHelper helper) {
        if (!enabled()) {
            helper.succeed();
            return;
        }
        PatternDockBlockEntity dock = dock(helper);
        Object sink = sink(helper);
        helper.startSequence()
                .thenWaitUntil(() -> registered(dock))
                .thenExecute(() -> linkFurnaces(helper, dock, 2))
                .thenWaitUntil(Rs2DockGameTests::room)
                .thenExecute(() -> {
                    String simulated = accept(sink, true);
                    helper.assertTrue(ACCEPTED.equals(simulated), "a simulação: " + simulated);
                    for (int i = 0; i < 2; i++) {
                        assertSlot(helper, FURNACES[i], 0, Items.IRON_ORE, 0);
                    }
                    dock.side(NORTH).setCursor(1);
                    String executed = accept(sink, false);
                    helper.assertTrue(ACCEPTED.equals(executed), "a execução: " + executed);
                    assertSlot(helper, FURNACES[0], 0, Items.IRON_ORE, 1);
                    assertSlot(helper, FURNACES[0], 1, Items.COAL, 1);
                    assertSlot(helper, FURNACES[1], 0, Items.IRON_ORE, 0);
                    helper.assertFalse(dock.side(NORTH).hasPending(), "sobrou pendente");
                })
                .thenSucceed();
    }

    /** Com as fornalhas cheias, recusado sem perder nada: nada entra, nada fica pendente. */
    @GameTest(template = "empty")
    public static void allFullRejectedWithoutLoss(GameTestHelper helper) {
        if (!enabled()) {
            helper.succeed();
            return;
        }
        PatternDockBlockEntity dock = dock(helper);
        Object sink = sink(helper);
        helper.startSequence()
                .thenWaitUntil(() -> registered(dock))
                .thenExecute(() -> {
                    linkFurnaces(helper, dock, 3);
                    for (BlockPos furnace : FURNACES) {
                        ((AbstractFurnaceBlockEntity) helper.getBlockEntity(furnace))
                                .setItem(0, new ItemStack(Items.COBBLESTONE, 64));
                    }
                })
                .thenWaitUntil(Rs2DockGameTests::room)
                .thenExecute(() -> {
                    String result = accept(sink, true);
                    helper.assertTrue(REJECTED.equals(result), "com as fornalhas cheias: " + result);
                    helper.assertTrue(dock.side(NORTH).lastRefusal() == DockStatus.ALL_FULL,
                            "motivo: " + dock.side(NORTH).lastRefusal());
                    helper.assertFalse(dock.side(NORTH).hasPending(), "ficou pendente");
                    assertFurnacesUntouched(helper, 3);
                })
                .thenSucceed();
    }

    /** Um pedido com um recurso que não é item nem fluido é recusado com o motivo do misto, sem entregar nada. */
    @GameTest(template = "empty")
    public static void foreignResourceRejected(GameTestHelper helper) {
        if (!enabled()) {
            helper.succeed();
            return;
        }
        PatternDockBlockEntity dock = dock(helper);
        Object sink = sink(helper);
        helper.startSequence()
                .thenWaitUntil(() -> registered(dock))
                .thenExecute(() -> linkFurnaces(helper, dock, 1))
                .thenWaitUntil(Rs2DockGameTests::room)
                .thenExecute(() -> {
                    String result = Rs2TestSupport.accept(sink, oreAndCoal(), List.of(), true, true);
                    helper.assertTrue(REJECTED.equals(result), "com outro recurso: " + result);
                    helper.assertTrue(dock.side(NORTH).lastRefusal() == DockStatus.MIXED,
                            "motivo: " + dock.side(NORTH).lastRefusal());
                    assertSlot(helper, FURNACES[0], 0, Items.IRON_ORE, 0);
                })
                .thenSucceed();
    }

    /**
     * Com um pendente que não sai (a fornalha dele cheia), recusado com o motivo do pendente, e o pendente fica
     * inteiro. {@code REJECTED} e não {@code LOCKED}: ver {@code Rs2DockSink}.
     */
    @GameTest(template = "empty")
    public static void pendingRejected(GameTestHelper helper) {
        if (!enabled()) {
            helper.succeed();
            return;
        }
        PatternDockBlockEntity dock = dock(helper);
        Object sink = sink(helper);
        helper.startSequence()
                .thenWaitUntil(() -> registered(dock))
                .thenExecute(() -> {
                    linkFurnaces(helper, dock, 1);
                    ((AbstractFurnaceBlockEntity) helper.getBlockEntity(FURNACES[0]))
                            .setItem(0, new ItemStack(Items.COBBLESTONE, 64));
                    dock.side(NORTH).setPending(List.of(new ItemStack(Items.COBBLESTONE, 10)), List.of(),
                            helper.absolutePos(FURNACES[0]));
                })
                .thenWaitUntil(Rs2DockGameTests::room)
                .thenExecute(() -> {
                    String result = accept(sink, true);
                    helper.assertTrue(REJECTED.equals(result), "com pendente: " + result);
                    helper.assertTrue(dock.side(NORTH).lastRefusal() == DockStatus.PENDING,
                            "motivo: " + dock.side(NORTH).lastRefusal());
                    List<ItemStack> pending = dock.side(NORTH).pendingItems();
                    helper.assertTrue(pending.size() == 1 && pending.get(0).is(Items.COBBLESTONE)
                            && pending.get(0).getCount() == 10, "o pendente mudou: " + pending);
                    assertFurnacesUntouched(helper, 1);
                    helper.assertFalse(Rs2TestSupport.isEmpty(sink), "vazio com pendente");
                })
                .thenSucceed();
    }

    /** A trava "até a máquina esvaziar": vazio sem pedido, não vazio logo depois de um. */
    @GameTest(template = "empty")
    public static void isEmptyAfterOrder(GameTestHelper helper) {
        if (!enabled()) {
            helper.succeed();
            return;
        }
        PatternDockBlockEntity dock = dock(helper);
        Object sink = sink(helper);
        helper.startSequence()
                .thenWaitUntil(() -> registered(dock))
                .thenExecute(() -> linkFurnaces(helper, dock, 1))
                .thenWaitUntil(Rs2DockGameTests::room)
                .thenExecute(() -> {
                    helper.assertTrue(Rs2TestSupport.isEmpty(sink), "não vazio sem pedido");
                    helper.assertTrue(ACCEPTED.equals(accept(sink, true)), "a simulação recusou");
                    helper.assertTrue(ACCEPTED.equals(accept(sink, false)), "a execução recusou");
                    helper.assertFalse(Rs2TestSupport.isEmpty(sink), "vazio logo depois do pedido");
                })
                .thenSucceed();
    }

    /**
     * "Tem provedor": o Autocrafter encostado conta (o de cor também); o baú não; um lado sem vizinho do RS2 que recebeu
     * uma oferta nos últimos 60 s conta.
     */
    @GameTest(template = "empty")
    public static void providerDetection(GameTestHelper helper) {
        if (!enabled()) {
            helper.succeed();
            return;
        }
        PatternDockBlockEntity dock = dock(helper);
        helper.setBlock(DOCK.north(), rs2("autocrafter"));
        helper.setBlock(DOCK.south(), rs2("lime_autocrafter"));
        helper.setBlock(DOCK.east(), Blocks.CHEST);
        helper.startSequence()
                .thenWaitUntil(() -> registered(dock))
                .thenExecute(() -> {
                    helper.assertTrue(DockRs2.hasProvider(dock, NORTH), "o Autocrafter não conta");
                    helper.assertTrue(DockRs2.hasProvider(dock, Direction.SOUTH.ordinal()), "o Autocrafter de cor não conta");
                    helper.assertFalse(DockRs2.hasProvider(dock, Direction.EAST.ordinal()), "o baú conta");
                    int west = Direction.WEST.ordinal();
                    helper.setBlock(FURNACES[0], Blocks.FURNACE);
                    helper.assertTrue(dock.link(west, helper.absolutePos(FURNACES[0]), FURNACE_ID,
                            Direction.NORTH.ordinal()), "não vinculou a oeste");
                    helper.assertFalse(DockRs2.hasProvider(dock, west), "a oeste sem vizinho conta");
                    Rs2TestSupport.accept(Rs2TestSupport.sink(helper.getLevel(), helper.absolutePos(DOCK), Direction.WEST),
                            oreAndCoal(), List.of(), false, true);
                    helper.assertTrue(DockRs2.hasProvider(dock, west), "a oferta recente não conta");
                })
                .thenSucceed();
    }
}
