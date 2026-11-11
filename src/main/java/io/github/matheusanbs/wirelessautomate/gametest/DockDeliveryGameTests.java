package io.github.matheusanbs.wirelessautomate.gametest;

import io.github.matheusanbs.wirelessautomate.Config;
import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.block.RouterBlock;
import io.github.matheusanbs.wirelessautomate.block.RouterTier;
import io.github.matheusanbs.wirelessautomate.dock.DockDelivery;
import io.github.matheusanbs.wirelessautomate.dock.DockMachine;
import io.github.matheusanbs.wirelessautomate.dock.DockPendingLot;
import io.github.matheusanbs.wirelessautomate.dock.DockSide;
import io.github.matheusanbs.wirelessautomate.dock.DockStatus;
import io.github.matheusanbs.wirelessautomate.dock.PatternDockBlockEntity;
import io.github.matheusanbs.wirelessautomate.network.Chemicals;
import io.github.matheusanbs.wirelessautomate.network.NetworkManager;
import io.github.matheusanbs.wirelessautomate.network.PortMode;
import io.github.matheusanbs.wirelessautomate.network.ResourceType;
import io.github.matheusanbs.wirelessautomate.network.TickBudget;
import io.github.matheusanbs.wirelessautomate.range.RemoteSide;
import io.github.matheusanbs.wirelessautomate.registry.ModBlocks;
import java.util.ArrayList;
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
 * o recurso desconhecido, a simulação seguida da execução na mesma máquina, o pendente (que segura o lado e sai quando a
 * máquina esvazia, passa para outra máquina quando a dele é trocada e guarda o bloco do lado sem máquinas), a máquina descarregada e a de fora do alcance puladas e a recusa sem orçamento.
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

    /**
     * Pedido com item e fluido numa fornalha (sem face de fluido no perfil) é recusado como todas cheias, sem entregar
     * nada; com um recurso que a Doca não conhece (um químico, sem o Mekanism), com o motivo dele; o vazio também, sem
     * motivo novo.
     */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void unknownResourceRefused(GameTestHelper helper) {
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
                    helper.assertTrue(result == DockDelivery.Result.REFUSED, "aceitou o fluido sem face de fluido");
                    helper.assertTrue(dock.side(NORTH).lastRefusal() == DockStatus.ALL_FULL,
                            "motivo " + dock.side(NORTH).lastRefusal());
                    assertSlot(helper, furnace, 0, Items.IRON_ORE, 0);
                    helper.assertFalse(dock.side(NORTH).hasPending(), "ficou pendente");
                    if (!Chemicals.LOADED) {
                        helper.assertTrue(DockDelivery.offer(dock, NORTH, List.of(new ItemStack(Items.IRON_ORE)),
                                List.of(), List.of(new Object()), false) == DockDelivery.Result.REFUSED,
                                "aceitou o recurso desconhecido");
                        helper.assertTrue(dock.side(NORTH).lastRefusal() == DockStatus.MIXED,
                                "motivo " + dock.side(NORTH).lastRefusal());
                        assertSlot(helper, furnace, 0, Items.IRON_ORE, 0);
                    }
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

    /**
     * Duas pilhas de 40 minérios na fornalha do cursor: as duas cabem simuladas uma a uma, e as 16 que sobram vão ao
     * pendente dela, numa linha.
     */
    private static void makePending(GameTestHelper helper, PatternDockBlockEntity dock, BlockPos furnace) {
        List<ItemStack> order = List.of(new ItemStack(Items.IRON_ORE, 40), new ItemStack(Items.IRON_ORE, 40));
        helper.assertTrue(offer(dock, order, false) == DockDelivery.Result.ACCEPTED, "recusou");
        assertSlot(helper, furnace, 0, Items.IRON_ORE, 64);
        DockMachine machine = dock.machineAt(helper.absolutePos(furnace));
        DockPendingLot lot = machine == null ? null : machine.pending();
        helper.assertTrue(lot != null && lot.items().size() == 1 && lot.count(Items.IRON_ORE) == 16,
                "pendente " + (lot == null ? null : lot.items()));
        helper.assertTrue(dock.side(NORTH).loose() == null, "pendente solto");
    }

    /**
     * O pendente segura só a máquina dele: com uma fornalha com sobra, a outra do lado recebe o pedido seguinte; com
     * as duas com sobra, o lado recusa ({@code PENDING}); o pendente sai pelo {@code flushPending} quando a máquina
     * esvazia, e o lado volta a aceitar.
     */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void pendingHoldsOnlyItsMachine(GameTestHelper helper) {
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
                    DockSide side = dock.side(NORTH);
                    helper.assertTrue(DockDelivery.acceptsOrders(dock, NORTH), "a outra fornalha não recebe");
                    helper.assertFalse(side.waitingOnPending(), "o lado espera com uma máquina livre");
                    // o cursor na que tem sobra: ela é pulada, e a outra recebe
                    side.setCursor(0);
                    helper.assertTrue(offer(dock, oreAndCoal(), false) == DockDelivery.Result.ACCEPTED,
                            "a outra fornalha não recebeu: motivo " + side.lastRefusal());
                    assertSlot(helper, other, 0, Items.IRON_ORE, 1);
                    assertSlot(helper, furnace, 0, Items.IRON_ORE, 64);
                })
                .thenWaitUntil(DockDeliveryGameTests::room)
                .thenExecute(() -> {
                    // agora a outra também fica com sobra: 63 cabem, 17 esperam
                    DockSide side = dock.side(NORTH);
                    List<ItemStack> order = List.of(new ItemStack(Items.IRON_ORE, 40),
                            new ItemStack(Items.IRON_ORE, 40));
                    helper.assertTrue(offer(dock, order, false) == DockDelivery.Result.ACCEPTED, "recusou");
                    assertSlot(helper, other, 0, Items.IRON_ORE, 64);
                    DockMachine second = dock.machineAt(helper.absolutePos(other));
                    helper.assertTrue(second.pending() != null && second.pending().count(Items.IRON_ORE) == 17,
                            "pendente da outra " + (second.pending() == null ? null : second.pending().items()));
                    helper.assertFalse(DockDelivery.acceptsOrders(dock, NORTH), "o lado sem máquina livre aceita");
                    helper.assertTrue(side.waitingOnPending(), "o lado não espera");
                })
                .thenWaitUntil(DockDeliveryGameTests::room)
                .thenExecute(() -> {
                    DockSide side = dock.side(NORTH);
                    helper.assertTrue(offer(dock, oreAndCoal(), false) == DockDelivery.Result.REFUSED,
                            "aceitou sem máquina livre");
                    helper.assertTrue(side.lastRefusal() == DockStatus.PENDING, "motivo " + side.lastRefusal());
                    helper.assertFalse(DockDelivery.flushPending(dock, NORTH), "a cheia recebeu o pendente");
                    furnaceAt(helper, furnace).setItem(0, ItemStack.EMPTY);
                    helper.assertFalse(DockDelivery.flushPending(dock, NORTH), "o pendente da outra saiu");
                    assertSlot(helper, furnace, 0, Items.IRON_ORE, 16);
                    helper.assertTrue(dock.machineAt(helper.absolutePos(furnace)).pending() == null,
                            "sobrou pendente na primeira");
                    helper.assertTrue(DockDelivery.acceptsOrders(dock, NORTH), "a máquina sem pendente não aceita");
                    assertSlot(helper, other, 0, Items.IRON_ORE, 64);
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
     * O pendente numa fornalha que vira pedra (quebrada ou trocada, ainda vinculada) passa para a outra fornalha do lado
     * e o lado volta a aceitar pedidos.
     */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void pendingMovesOffReplacedMachine(GameTestHelper helper) {
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
                .thenExecute(() -> helper.setBlock(furnace, Blocks.STONE))
                .thenWaitUntil(() -> helper.assertFalse(dock.side(NORTH).hasPending(),
                        "o pendente ficou na máquina trocada"))
                .thenExecute(() -> {
                    assertSlot(helper, other, 0, Items.IRON_ORE, 16);
                    helper.assertTrue(DockDelivery.acceptsOrders(dock, NORTH), "o lado não voltou a aceitar");
                })
                .thenSucceed();
    }

    /**
     * Com pendente, desvincular a última máquina não faz o lado esquecer o bloco: outro bloco é recusado, e o pendente
     * sai na próxima fornalha vinculada.
     */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void pendingKeepsSideBlock(GameTestHelper helper) {
        PatternDockBlockEntity dock = dock(helper, RouterTier.BASIC);
        BlockPos furnace = new BlockPos(3, 1, 1);
        BlockPos other = new BlockPos(3, 1, 3);
        furnace(helper, furnace);
        furnace(helper, other);
        helper.startSequence()
                .thenIdle(1)
                .thenExecute(() -> link(helper, dock, furnace))
                .thenWaitUntil(DockDeliveryGameTests::room)
                .thenExecute(() -> makePending(helper, dock, furnace))
                .thenExecute(() -> {
                    helper.assertTrue(dock.unlink(NORTH, helper.absolutePos(furnace)), "não desvinculou");
                    DockSide side = dock.side(NORTH);
                    helper.assertTrue(side != null && FURNACE.equals(side.block()) && side.hasPending(),
                            "o lado esqueceu o bloco ou o pendente");
                    helper.assertFalse(dock.link(NORTH, helper.absolutePos(new BlockPos(5, 1, 1)),
                            BuiltInRegistries.BLOCK.getKey(Blocks.CHEST), -1), "aceitou outro bloco");
                    link(helper, dock, other);
                    helper.assertTrue(DockDelivery.flushPending(dock, NORTH), "o pendente não saiu na fornalha nova");
                    assertSlot(helper, other, 0, Items.IRON_ORE, 16);
                })
                .thenSucceed();
    }

    /**
     * A máquina num chunk descarregado de verdade é pulada sem gastar tentativa, e a oferta não carrega o chunk dela.
     * O servidor de GameTest só carrega os chunks dos testes e uma borda pequena em volta; a máquina vai a 96 blocos da
     * Doca Ultimate (no limite do alcance), na primeira de oito direções cujo chunk está descarregado
     * ({@code level.isLoaded} conferido na hora do vínculo e de novo depois da oferta). Vinculada pelo {@code link}
     * direto, sem ler o mundo. Se as oito estiverem carregadas (a grade dos testes em volta), o teste falha dizendo
     * isso, em vez de provar menos.
     */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void unloadedMachineSkipped(GameTestHelper helper) {
        PatternDockBlockEntity dock = dock(helper, RouterTier.ULTIMATE);
        BlockPos furnace = new BlockPos(3, 1, 1);
        furnace(helper, furnace);
        BlockPos dockPos = helper.absolutePos(DOCK);
        BlockPos[] unloaded = new BlockPos[1];
        helper.startSequence()
                .thenIdle(1)
                .thenExecute(() -> {
                    int reach = Config.dockRadius(RouterTier.ULTIMATE);
                    for (int i = 0; i < 8 && unloaded[0] == null; i++) {
                        int dx = i == 0 || i == 4 || i == 5 ? -reach : i == 1 || i == 6 || i == 7 ? reach : 0;
                        int dz = i == 2 || i == 4 || i == 6 ? -reach : i == 3 || i == 5 || i == 7 ? reach : 0;
                        BlockPos candidate = dockPos.offset(dx, 0, dz);
                        if (!helper.getLevel().isLoaded(candidate)) {
                            unloaded[0] = candidate;
                        }
                    }
                    helper.assertTrue(unloaded[0] != null, "os oito chunks a " + reach + " blocos estão carregados");
                    // a descarregada primeiro, no cursor
                    helper.assertTrue(dock.link(NORTH, unloaded[0], FURNACE, Direction.NORTH.ordinal()), "não vinculou");
                    link(helper, dock, furnace);
                    helper.assertTrue(dock.machineAt(unloaded[0]).active(), "a descarregada não está ativa");
                })
                .thenWaitUntil(DockDeliveryGameTests::room)
                .thenExecute(() -> {
                    helper.assertFalse(helper.getLevel().isLoaded(unloaded[0]), "o chunk da máquina carregou antes da oferta");
                    helper.assertTrue(offer(dock, oreAndCoal(), false) == DockDelivery.Result.ACCEPTED, "recusou");
                    assertSlot(helper, furnace, 0, Items.IRON_ORE, 1);
                    helper.assertFalse(dock.machineAt(unloaded[0]).asleep(now(helper)), "a descarregada foi tentada");
                    helper.assertFalse(helper.getLevel().isLoaded(unloaded[0]), "a oferta carregou o chunk da máquina");
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

    /**
     * Um pedido muito maior que a máquina (256 pilhas de pedregulho numa entrada de 64, como o padrão de 524.288 numa
     * Infinite Factory) entra no que cabe e o resto vai ao pendente, sem travar; e o custo não cresce com o tamanho do
     * pedido: a escolha simula uma vez as pilhas iguais seguidas (a simulação não soma, então dá a mesma resposta), a
     * execução para de tentar o item que a máquina já recusou, o pendente guarda uma linha por item, e cada tentativa
     * dele põe no máximo {@code DockPending.FLUSH_WINDOW} pilhas e para na primeira recusada.
     */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void hugeOrderCostDoesNotGrow(GameTestHelper helper) {
        PatternDockBlockEntity dock = dock(helper, RouterTier.ULTIMATE);
        BlockPos machine = new BlockPos(3, 1, 1);
        helper.setBlock(machine, TestMachines.FURNACE);
        TestMachines.Furnace furnace = TestMachines.furnace(helper.absolutePos(machine));
        List<ItemStack> order = new ArrayList<>();
        for (int i = 0; i < 256; i++) {
            order.add(new ItemStack(Items.COBBLESTONE, 64));
        }
        helper.startSequence()
                .thenExecute(() -> {
                    helper.assertTrue(dock.link(NORTH, helper.absolutePos(machine),
                            BuiltInRegistries.BLOCK.getKey(TestMachines.FURNACE), Direction.NORTH.ordinal()),
                            "não vinculou");
                    DockSide side = dock.side(NORTH);
                    side.profile().face(ResourceType.ITEM, RemoteSide.TOP).setMode(PortMode.INSERT);
                    side.profile().face(ResourceType.ITEM, RemoteSide.BOTTOM).setMode(PortMode.EXTRACT);
                })
                .thenWaitUntil(DockDeliveryGameTests::room)
                .thenExecute(() -> {
                    furnace.insertCalls = 0;
                    helper.assertTrue(offer(dock, order, false) == DockDelivery.Result.ACCEPTED, "recusou");
                    DockPendingLot lot = dock.machineAt(helper.absolutePos(machine)).pending();
                    // uma linha só para as 255 pilhas que sobraram
                    helper.assertTrue(furnace.slots[0].getCount() == 64 && lot != null && lot.items().size() == 1
                            && lot.count(Items.COBBLESTONE) == 255 * 64,
                            "na máquina " + furnace.slots[0] + ", pendente " + (lot == null ? null : lot.items()));
                    helper.assertTrue(furnace.insertCalls <= 16, "a oferta chamou insertItem " + furnace.insertCalls
                            + " vezes");
                    furnace.insertCalls = 0;
                    helper.assertFalse(DockDelivery.flushPending(dock, NORTH), "a cheia recebeu o pendente");
                    helper.assertTrue(furnace.insertCalls <= 16, "a tentativa do pendente chamou insertItem "
                            + furnace.insertCalls + " vezes");
                    furnace.slots[0] = ItemStack.EMPTY;
                    furnace.insertCalls = 0;
                    helper.assertFalse(DockDelivery.flushPending(dock, NORTH), "o pendente saiu inteiro");
                    helper.assertTrue(furnace.slots[0].getCount() == 64 && lot.count(Items.COBBLESTONE) == 254 * 64,
                            "depois: na máquina " + furnace.slots[0] + ", pendente " + lot.items());
                    helper.assertTrue(furnace.insertCalls <= 16, "a tentativa com lugar chamou insertItem "
                            + furnace.insertCalls + " vezes");
                })
                .thenSucceed();
    }
}
