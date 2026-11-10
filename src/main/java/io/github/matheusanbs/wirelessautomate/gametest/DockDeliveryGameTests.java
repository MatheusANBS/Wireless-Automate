package io.github.matheusanbs.wirelessautomate.gametest;

import io.github.matheusanbs.wirelessautomate.Config;
import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.block.RouterBlock;
import io.github.matheusanbs.wirelessautomate.block.RouterTier;
import io.github.matheusanbs.wirelessautomate.dock.DockDelivery;
import io.github.matheusanbs.wirelessautomate.dock.DockMachine;
import io.github.matheusanbs.wirelessautomate.dock.DockSide;
import io.github.matheusanbs.wirelessautomate.dock.DockStatus;
import io.github.matheusanbs.wirelessautomate.dock.PatternDockBlockEntity;
import io.github.matheusanbs.wirelessautomate.network.NetworkManager;
import io.github.matheusanbs.wirelessautomate.network.PortMode;
import io.github.matheusanbs.wirelessautomate.network.ResourceType;
import io.github.matheusanbs.wirelessautomate.network.TickBudget;
import io.github.matheusanbs.wirelessautomate.range.RangeRules;
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
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * A entrega de um pedido ({@link DockDelivery}) chamada direto, sem o AE2: o minério na entrada e o carvão no
 * combustível da fornalha (a face mais seletiva), o revezamento pelo cursor, a recusa com todas cheias sem perder nada,
 * o pedido misto, a simulação seguida da execução na mesma máquina, o pendente (que segura o lado e sai quando a
 * máquina esvazia), a máquina descarregada e a de fora do alcance puladas e a recusa sem orçamento.
 *
 * <p>O orçamento é do motor inteiro e os testes do lote rodam no mesmo tick: cada oferta vem depois de um passo que
 * espera folga ({@link #room}), para a primeira oferta do lote (que carrega as classes) não recusar as dos outros.
 */
@GameTestHolder(WirelessAutomate.MODID)
@PrefixGameTestTemplate(false)
public final class DockDeliveryGameTests {
    private static final BlockPos DOCK = new BlockPos(1, 1, 1);
    private static final int NORTH = Direction.NORTH.ordinal();
    private static final ResourceLocation FURNACE = BuiltInRegistries.BLOCK.getKey(Blocks.FURNACE);

    private DockDeliveryGameTests() {
    }

    private static PatternDockBlockEntity dock(GameTestHelper helper, RouterTier tier) {
        helper.setBlock(DOCK, ModBlocks.PATTERN_DOCK.get().defaultBlockState().setValue(RouterBlock.TIER, tier));
        return helper.getBlockEntity(DOCK);
    }

    /** Uma fornalha virada para o norte em {@code relative}. */
    private static void furnace(GameTestHelper helper, BlockPos relative) {
        helper.setBlock(relative, Blocks.FURNACE.defaultBlockState().setValue(AbstractFurnaceBlock.FACING,
                Direction.NORTH));
    }

    private static AbstractFurnaceBlockEntity furnaceAt(GameTestHelper helper, BlockPos relative) {
        return helper.getBlockEntity(relative);
    }

    /** Vincula a fornalha em {@code relative} ao lado Norte e dá ao lado o perfil da fornalha. */
    private static DockMachine link(GameTestHelper helper, PatternDockBlockEntity dock, BlockPos relative) {
        BlockPos pos = helper.absolutePos(relative);
        helper.assertTrue(dock.link(NORTH, pos, FURNACE, Direction.NORTH.ordinal()), "não vinculou " + relative);
        furnaceProfile(dock.side(NORTH));
        return dock.machineAt(pos);
    }

    /** O perfil que o Afinador dá à fornalha: Cima (entrada) e Trás (combustível) Insere, Baixo Extrai. */
    private static void furnaceProfile(DockSide side) {
        side.profile().face(ResourceType.ITEM, RemoteSide.TOP).setMode(PortMode.INSERT);
        side.profile().face(ResourceType.ITEM, RemoteSide.BACK).setMode(PortMode.INSERT);
        side.profile().face(ResourceType.ITEM, RemoteSide.BOTTOM).setMode(PortMode.EXTRACT);
    }

    /** Passo de espera: o orçamento tem folga para uma oferta (o próximo passo roda no mesmo tick). */
    private static void room() {
        if (!NetworkManager.get().budget().outsideRoom()) {
            throw new GameTestAssertException("sem orçamento neste tick");
        }
    }

    private static List<ItemStack> oreAndCoal() {
        return List.of(new ItemStack(Items.IRON_ORE), new ItemStack(Items.COAL));
    }

    private static DockDelivery.Result offer(PatternDockBlockEntity dock, List<ItemStack> items, boolean simulate) {
        return DockDelivery.offer(dock, NORTH, items, List.of(), simulate);
    }

    private static void assertSlot(GameTestHelper helper, BlockPos furnace, int slot, Item item, int count) {
        ItemStack stack = furnaceAt(helper, furnace).getItem(slot);
        helper.assertTrue(count == 0 ? stack.isEmpty() : stack.is(item) && stack.getCount() == count,
                "fornalha " + furnace + ", slot " + slot + ": " + stack);
    }

    private static long now(GameTestHelper helper) {
        return helper.getLevel().getServer().getTickCount();
    }

    /** Minério e carvão: o carvão vai para Trás (a face que só aceita ele), o minério para Cima. */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void oreToInputCoalToFuel(GameTestHelper helper) {
        PatternDockBlockEntity dock = dock(helper, RouterTier.BASIC);
        BlockPos furnace = new BlockPos(3, 1, 1);
        furnace(helper, furnace);
        helper.startSequence()
                .thenIdle(1)
                .thenExecute(() -> link(helper, dock, furnace))
                .thenWaitUntil(DockDeliveryGameTests::room)
                .thenExecute(() -> {
                    List<ItemStack> order = oreAndCoal();
                    helper.assertTrue(DockDelivery.acceptsOrders(dock, NORTH), "o lado não aceita pedidos");
                    helper.assertTrue(offer(dock, order, false) == DockDelivery.Result.ACCEPTED, "recusou");
                    // no mesmo tick: no seguinte a fornalha acende e queima o carvão
                    assertSlot(helper, furnace, 0, Items.IRON_ORE, 1);
                    assertSlot(helper, furnace, 1, Items.COAL, 1);
                    helper.assertTrue(order.get(0).getCount() == 1 && order.get(1).getCount() == 1,
                            "mudou os stacks do pedido");
                    helper.assertFalse(dock.side(NORTH).hasPending(), "sobrou pendente");
                })
                .thenSucceed();
    }

    /** Três pedidos em três fornalhas: um em cada, pelo cursor. */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void threeOrdersThreeFurnaces(GameTestHelper helper) {
        PatternDockBlockEntity dock = dock(helper, RouterTier.BASIC);
        BlockPos[] furnaces = {new BlockPos(3, 1, 1), new BlockPos(3, 1, 3), new BlockPos(1, 1, 3)};
        for (BlockPos furnace : furnaces) {
            furnace(helper, furnace);
        }
        var sequence = helper.startSequence()
                .thenIdle(1)
                .thenExecute(() -> {
                    for (BlockPos furnace : furnaces) {
                        link(helper, dock, furnace);
                    }
                });
        for (int i = 0; i < furnaces.length; i++) {
            int order = i;
            sequence.thenWaitUntil(DockDeliveryGameTests::room)
                    .thenExecute(() -> {
                        helper.assertTrue(offer(dock, oreAndCoal(), false) == DockDelivery.Result.ACCEPTED,
                                "recusou o pedido " + order);
                        helper.assertTrue(dock.side(NORTH).cursor() == (order + 1) % furnaces.length,
                                "cursor " + dock.side(NORTH).cursor() + " depois do pedido " + order);
                    });
        }
        sequence.thenExecute(() -> {
            for (BlockPos furnace : furnaces) {
                assertSlot(helper, furnace, 0, Items.IRON_ORE, 1);
            }
        }).thenSucceed();
    }

    /** Todas cheias: recusa com o motivo, nada entra, nada vai ao pendente, e as que recusaram dormem. */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void allFullRefusesWithoutLoss(GameTestHelper helper) {
        PatternDockBlockEntity dock = dock(helper, RouterTier.BASIC);
        BlockPos[] furnaces = {new BlockPos(3, 1, 1), new BlockPos(3, 1, 3)};
        for (BlockPos furnace : furnaces) {
            furnace(helper, furnace);
        }
        helper.startSequence()
                .thenIdle(1)
                .thenExecute(() -> {
                    for (BlockPos furnace : furnaces) {
                        link(helper, dock, furnace);
                        // entrada cheia e sem combustível: nada queima
                        furnaceAt(helper, furnace).setItem(0, new ItemStack(Items.IRON_ORE, 64));
                    }
                })
                .thenWaitUntil(DockDeliveryGameTests::room)
                .thenExecute(() -> {
                    List<ItemStack> order = oreAndCoal();
                    helper.assertTrue(offer(dock, order, false) == DockDelivery.Result.REFUSED, "aceitou");
                    DockSide side = dock.side(NORTH);
                    helper.assertTrue(side.lastRefusal() == DockStatus.ALL_FULL, "motivo " + side.lastRefusal());
                    helper.assertFalse(side.hasPending(), "foi ao pendente");
                    helper.assertTrue(order.get(0).getCount() == 1 && order.get(1).getCount() == 1,
                            "mudou os stacks do pedido");
                    for (BlockPos furnace : furnaces) {
                        assertSlot(helper, furnace, 0, Items.IRON_ORE, 64);
                        assertSlot(helper, furnace, 1, Items.COAL, 0);
                        helper.assertTrue(dock.machineAt(helper.absolutePos(furnace)).asleep(now(helper)),
                                "a cheia não dormiu: " + furnace);
                    }
                })
                .thenSucceed();
    }

    /** Pedido com item e fluido é recusado com o motivo; pedido vazio também, sem motivo novo. */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void mixedOrderRefused(GameTestHelper helper) {
        PatternDockBlockEntity dock = dock(helper, RouterTier.BASIC);
        BlockPos furnace = new BlockPos(3, 1, 1);
        furnace(helper, furnace);
        helper.startSequence()
                .thenIdle(1)
                .thenExecute(() -> link(helper, dock, furnace))
                .thenWaitUntil(DockDeliveryGameTests::room)
                .thenExecute(() -> {
                    DockDelivery.Result result = DockDelivery.offer(dock, NORTH, List.of(new ItemStack(Items.IRON_ORE)),
                            List.of(new FluidStack(Fluids.WATER, 1000)), false);
                    helper.assertTrue(result == DockDelivery.Result.REFUSED, "aceitou o misto");
                    helper.assertTrue(dock.side(NORTH).lastRefusal() == DockStatus.MIXED,
                            "motivo " + dock.side(NORTH).lastRefusal());
                    assertSlot(helper, furnace, 0, Items.IRON_ORE, 0);
                    helper.assertTrue(DockDelivery.offer(dock, NORTH, List.of(ItemStack.EMPTY), List.of(), false)
                            == DockDelivery.Result.REFUSED, "aceitou o vazio");
                })
                .thenSucceed();
    }

    /** A simulação guarda a máquina e a execução do mesmo tick entrega nela, mesmo com o cursor mudado no meio. */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void simulateThenExecuteSameMachine(GameTestHelper helper) {
        PatternDockBlockEntity dock = dock(helper, RouterTier.BASIC);
        BlockPos first = new BlockPos(3, 1, 1);
        BlockPos second = new BlockPos(3, 1, 3);
        furnace(helper, first);
        furnace(helper, second);
        helper.startSequence()
                .thenIdle(1)
                .thenExecute(() -> {
                    link(helper, dock, first);
                    link(helper, dock, second);
                })
                .thenWaitUntil(DockDeliveryGameTests::room)
                .thenExecute(() -> {
                    List<ItemStack> order = oreAndCoal();
                    helper.assertTrue(offer(dock, order, true) == DockDelivery.Result.ACCEPTED, "a simulação recusou");
                    assertSlot(helper, first, 0, Items.IRON_ORE, 0);
                    // a execução segue a máquina da simulação, não o cursor
                    dock.side(NORTH).setCursor(1);
                    helper.assertTrue(offer(dock, order, false) == DockDelivery.Result.ACCEPTED, "a execução recusou");
                    assertSlot(helper, first, 0, Items.IRON_ORE, 1);
                    assertSlot(helper, first, 1, Items.COAL, 1);
                    assertSlot(helper, second, 0, Items.IRON_ORE, 0);
                    helper.assertTrue(dock.side(NORTH).cursor() == 1, "cursor " + dock.side(NORTH).cursor());
                })
                .thenSucceed();
    }

    /** Duas pilhas de 40 minérios: as duas cabem simuladas uma a uma, e as 16 que sobram vão ao pendente. */
    private static void makePending(GameTestHelper helper, PatternDockBlockEntity dock, BlockPos furnace) {
        List<ItemStack> order = List.of(new ItemStack(Items.IRON_ORE, 40), new ItemStack(Items.IRON_ORE, 40));
        helper.assertTrue(offer(dock, order, false) == DockDelivery.Result.ACCEPTED, "recusou");
        DockSide side = dock.side(NORTH);
        assertSlot(helper, furnace, 0, Items.IRON_ORE, 64);
        helper.assertTrue(side.pendingItems().size() == 1 && side.pendingItems().get(0).getCount() == 16
                && side.pendingAt().equals(helper.absolutePos(furnace)), "pendente " + side.pendingItems());
        helper.assertFalse(DockDelivery.acceptsOrders(dock, NORTH), "o lado com pendente aceita pedidos");
    }

    /** O pendente segura o lado (recusa {@code PENDING}) e sai pelo {@code flushPending} quando a máquina esvazia. */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void pendingHoldsSideUntilFlushed(GameTestHelper helper) {
        PatternDockBlockEntity dock = dock(helper, RouterTier.BASIC);
        BlockPos furnace = new BlockPos(3, 1, 1);
        BlockPos other = new BlockPos(3, 1, 3);
        furnace(helper, furnace);
        furnace(helper, other);
        helper.startSequence()
                .thenIdle(1)
                .thenExecute(() -> {
                    link(helper, dock, furnace);
                    link(helper, dock, other);
                })
                .thenWaitUntil(DockDeliveryGameTests::room)
                .thenExecute(() -> makePending(helper, dock, furnace))
                .thenWaitUntil(DockDeliveryGameTests::room)
                .thenExecute(() -> {
                    // a outra fornalha está vazia, mas o lado espera o pendente
                    helper.assertTrue(offer(dock, oreAndCoal(), false) == DockDelivery.Result.REFUSED,
                            "aceitou com pendente");
                    helper.assertTrue(dock.side(NORTH).lastRefusal() == DockStatus.PENDING,
                            "motivo " + dock.side(NORTH).lastRefusal());
                    assertSlot(helper, other, 0, Items.IRON_ORE, 0);
                    helper.assertFalse(DockDelivery.flushPending(dock, NORTH), "a cheia recebeu o pendente");
                    furnaceAt(helper, furnace).setItem(0, ItemStack.EMPTY);
                    helper.assertTrue(DockDelivery.flushPending(dock, NORTH), "o pendente não saiu");
                    assertSlot(helper, furnace, 0, Items.IRON_ORE, 16);
                    helper.assertTrue(DockDelivery.acceptsOrders(dock, NORTH), "o lado sem pendente não aceita");
                })
                .thenSucceed();
    }

    /** O aviso da máquina que guarda o pendente (o {@code setChanged} dela) o tenta logo depois, sozinho. */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void pendingLeavesWhenMachineChanges(GameTestHelper helper) {
        PatternDockBlockEntity dock = dock(helper, RouterTier.BASIC);
        BlockPos furnace = new BlockPos(3, 1, 1);
        furnace(helper, furnace);
        helper.startSequence()
                .thenIdle(1)
                .thenExecute(() -> link(helper, dock, furnace))
                .thenWaitUntil(DockDeliveryGameTests::room)
                .thenExecute(() -> makePending(helper, dock, furnace))
                .thenExecute(() -> {
                    AbstractFurnaceBlockEntity entity = furnaceAt(helper, furnace);
                    entity.setItem(0, ItemStack.EMPTY);
                    entity.setChanged();
                })
                .thenWaitUntil(() -> helper.assertFalse(dock.side(NORTH).hasPending(), "o pendente não saiu"))
                .thenExecute(() -> assertSlot(helper, furnace, 0, Items.IRON_ORE, 16))
                .thenSucceed();
    }

    /**
     * A máquina com o chunk descarregado é pulada sem gastar tentativa. Descarregar um chunk no meio de um GameTest
     * não é confiável (os vizinhos do template ficam carregados), então a máquina fica logo abaixo da altura mínima do
     * mundo, onde {@code level.isLoaded} é falso como num chunk descarregado; a Doca Ultimate a deixa no alcance.
     */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void unloadedMachineSkipped(GameTestHelper helper) {
        PatternDockBlockEntity dock = dock(helper, RouterTier.ULTIMATE);
        BlockPos furnace = new BlockPos(3, 1, 1);
        furnace(helper, furnace);
        BlockPos dockPos = helper.absolutePos(DOCK);
        BlockPos unloaded = new BlockPos(dockPos.getX(), helper.getLevel().getMinBuildHeight() - 1, dockPos.getZ());
        helper.startSequence()
                .thenIdle(1)
                .thenExecute(() -> {
                    helper.assertFalse(helper.getLevel().isLoaded(unloaded), "a posição de teste está carregada");
                    helper.assertTrue(RangeRules.chebyshev(dockPos.getX(), dockPos.getY(), dockPos.getZ(),
                            unloaded.getX(), unloaded.getY(), unloaded.getZ()) <= Config.dockRadius(RouterTier.ULTIMATE),
                            "a posição de teste está fora do alcance");
                    // a descarregada primeiro, no cursor
                    helper.assertTrue(dock.link(NORTH, unloaded, FURNACE, Direction.NORTH.ordinal()), "não vinculou");
                    link(helper, dock, furnace);
                    helper.assertTrue(dock.machineAt(unloaded).active(), "a descarregada não está ativa");
                })
                .thenWaitUntil(DockDeliveryGameTests::room)
                .thenExecute(() -> {
                    helper.assertTrue(offer(dock, oreAndCoal(), false) == DockDelivery.Result.ACCEPTED, "recusou");
                    assertSlot(helper, furnace, 0, Items.IRON_ORE, 1);
                    helper.assertFalse(dock.machineAt(unloaded).asleep(now(helper)), "a descarregada foi tentada");
                })
                .thenSucceed();
    }

    /** A config baixou o raio: a máquina de fora do alcance continua vinculada, inativa, e é pulada. */
    @GameTest(template = "empty", timeoutTicks = 100, batch = "wirelessautomate_config_dock")
    public static void outOfReachMachineSkipped(GameTestHelper helper) {
        PatternDockBlockEntity dock = dock(helper, RouterTier.BASIC);
        BlockPos far = new BlockPos(1, 1, 4);
        BlockPos near = new BlockPos(2, 1, 1);
        furnace(helper, far);
        furnace(helper, near);
        // a Doca Básica lê o raio do Avançado
        var radius = Config.TIERS.get(RouterTier.ADVANCED).machineRadius();
        int before = radius.get();
        helper.startSequence()
                .thenIdle(1)
                .thenWaitUntil(DockDeliveryGameTests::room)
                .thenExecute(() -> {
                    radius.set(1);
                    try {
                        link(helper, dock, far);
                        link(helper, dock, near);
                        DockMachine farMachine = dock.machineAt(helper.absolutePos(far));
                        helper.assertTrue(farMachine != null && !farMachine.active(), "a de longe ficou ativa");
                        helper.assertTrue(offer(dock, oreAndCoal(), false) == DockDelivery.Result.ACCEPTED, "recusou");
                        assertSlot(helper, near, 0, Items.IRON_ORE, 1);
                        assertSlot(helper, far, 0, Items.IRON_ORE, 0);
                    } finally {
                        radius.set(before);
                    }
                })
                .thenSucceed();
    }

    /** Sem folga no orçamento, a oferta recusa com {@code BUDGET} antes de consultar a máquina. */
    @GameTest(template = "empty", timeoutTicks = 100, batch = "wirelessautomate_dock_budget")
    public static void noBudgetRefuses(GameTestHelper helper) {
        PatternDockBlockEntity dock = dock(helper, RouterTier.BASIC);
        BlockPos furnace = new BlockPos(3, 1, 1);
        furnace(helper, furnace);
        helper.startSequence()
                .thenIdle(1)
                .thenExecute(() -> link(helper, dock, furnace))
                .thenExecute(() -> {
                    TickBudget budget = NetworkManager.get().budget();
                    budget.charge(budget.limitNanos());
                    helper.assertFalse(budget.outsideRoom(), "o orçamento ainda tem folga");
                    List<ItemStack> order = oreAndCoal();
                    helper.assertTrue(offer(dock, order, false) == DockDelivery.Result.REFUSED, "aceitou sem orçamento");
                    helper.assertTrue(dock.side(NORTH).lastRefusal() == DockStatus.BUDGET,
                            "motivo " + dock.side(NORTH).lastRefusal());
                    assertSlot(helper, furnace, 0, Items.IRON_ORE, 0);
                    helper.assertFalse(dock.machineAt(helper.absolutePos(furnace)).asleep(now(helper)),
                            "a máquina foi consultada");
                })
                .thenSucceed();
    }
}
