package io.github.matheusanbs.wirelessautomate.gametest;

import io.github.matheusanbs.wirelessautomate.block.RouterBlock;
import io.github.matheusanbs.wirelessautomate.block.RouterTier;
import io.github.matheusanbs.wirelessautomate.dock.DockDelivery;
import io.github.matheusanbs.wirelessautomate.dock.DockSide;
import io.github.matheusanbs.wirelessautomate.dock.DockStatus;
import io.github.matheusanbs.wirelessautomate.dock.PatternDockBlockEntity;
import io.github.matheusanbs.wirelessautomate.network.Chemicals;
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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Químicos na Doca ({@link DockDelivery} chamada direto, sem o AE2 nem o RS2), numa máquina de teste com item por cima,
 * entrada de químico pelos lados e saída de químico por baixo ({@link ChemicalTestTanks#DOCK_MACHINE_BLOCK}): o pedido
 * "item + químico" põe cada um na face do tipo dele; o químico que a máquina recusa na execução vai ao pendente e sai
 * quando ela tem lugar; o químico de saída volta ao tanque de químico encostado no lado. Rodam só na run
 * {@code runGameTestServerChemicals} (o namespace de {@link ChemicalGameTests}); sem o Mekanism, só passam.
 *
 * <p>Nenhum tipo do Mekanism nas assinaturas: o que usa a API fica em {@link ChemicalTestSupport}.
 */
@GameTestHolder(ChemicalGameTests.NAMESPACE)
@PrefixGameTestTemplate(false)
public final class DockChemicalGameTests {
    private static final BlockPos DOCK = new BlockPos(1, 1, 1);
    /** O tanque de químico no lugar do provedor, encostado no lado Norte da Doca. */
    private static final BlockPos TANK = DOCK.north();
    private static final BlockPos MACHINE = new BlockPos(3, 1, 1);
    private static final int NORTH = Direction.NORTH.ordinal();
    private static final ResourceLocation MACHINE_ID = BuiltInRegistries.BLOCK.getKey(
            ChemicalTestTanks.DOCK_MACHINE_BLOCK);
    private static final ResourceLocation HYDROGEN = ResourceLocation.fromNamespaceAndPath("mekanism", "hydrogen");
    private static final ResourceLocation OXYGEN = ResourceLocation.fromNamespaceAndPath("mekanism", "oxygen");

    private DockChemicalGameTests() {
    }

    /** A Doca Básica e a máquina, ainda sem vínculo. */
    private static PatternDockBlockEntity setUp(GameTestHelper helper) {
        helper.setBlock(DOCK, ModBlocks.PATTERN_DOCK.get().defaultBlockState().setValue(RouterBlock.TIER,
                RouterTier.BASIC));
        ChemicalTestSupport.resetDockMachine(helper.absolutePos(MACHINE));
        helper.setBlock(MACHINE, ChemicalTestTanks.DOCK_MACHINE_BLOCK);
        return helper.getBlockEntity(DOCK);
    }

    private static void link(GameTestHelper helper, PatternDockBlockEntity dock) {
        linkMachine(helper, dock, MACHINE);
    }

    /**
     * A máquina em {@code machine} vinculada ao lado Norte, virada para o norte: item Insere em Cima, químico Insere em
     * Trás e Extrai em Baixo. O químico tem também uma face Insere sem tanque (Cima), que a entrega pula. Também para
     * os testes dos químicos nas redes ({@link DockChemicalAe2GameTests}).
     */
    static void linkMachine(GameTestHelper helper, PatternDockBlockEntity dock, BlockPos machine) {
        helper.assertTrue(dock.link(NORTH, helper.absolutePos(machine), MACHINE_ID, Direction.NORTH.ordinal()),
                "não vinculou a máquina");
        DockSide side = dock.side(NORTH);
        side.profile().face(ResourceType.ITEM, RemoteSide.TOP).setMode(PortMode.INSERT);
        side.profile().face(ResourceType.CHEMICAL, RemoteSide.TOP).setMode(PortMode.INSERT);
        side.profile().face(ResourceType.CHEMICAL, RemoteSide.BACK).setMode(PortMode.INSERT);
        side.profile().face(ResourceType.CHEMICAL, RemoteSide.BOTTOM).setMode(PortMode.EXTRACT);
    }

    private static void room() {
        if (!NetworkManager.get().budget().outsideRoom()) {
            throw new GameTestAssertException("sem orçamento neste tick");
        }
    }

    /** A Doca entrou no laço do motor (o {@code onLoad} roda no tick seguinte ao da colocação). */
    private static void registered(PatternDockBlockEntity dock) {
        if (!NetworkManager.get().hasVisitor(dock.returns())) {
            throw new GameTestAssertException("a volta da Doca não está no motor");
        }
    }

    private static DockDelivery.Result offer(GameTestHelper helper, PatternDockBlockEntity dock) {
        return DockDelivery.offer(dock, NORTH, List.of(new ItemStack(Items.IRON_ORE)), List.of(),
                ChemicalTestSupport.chemicalOrder(HYDROGEN, 1_000), false);
    }

    /** "1 minério + 1.000 mB de hidrogênio": o minério no slot de cima, o hidrogênio na entrada do lado. */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void itemAndChemicalEachToItsFace(GameTestHelper helper) {
        if (!ChemicalTestTanks.enabled()) {
            helper.succeed();
            return;
        }
        PatternDockBlockEntity dock = setUp(helper);
        BlockPos machine = helper.absolutePos(MACHINE);
        helper.startSequence()
                .thenIdle(1)
                .thenExecute(() -> link(helper, dock))
                .thenWaitUntil(DockChemicalGameTests::room)
                .thenExecute(() -> {
                    helper.assertTrue(offer(helper, dock) == DockDelivery.Result.ACCEPTED,
                            "recusou: motivo " + dock.side(NORTH).lastRefusal());
                    ItemStack slot = ChemicalTestSupport.dockMachineItem(machine);
                    helper.assertTrue(slot.is(Items.IRON_ORE) && slot.getCount() == 1, "slot: " + slot);
                    long input = ChemicalTestSupport.dockMachineInput(machine, HYDROGEN);
                    helper.assertTrue(input == 1_000, "entrada: " + input);
                    helper.assertFalse(dock.side(NORTH).hasPending(), "sobrou pendente");
                })
                .thenSucceed();
    }

    /**
     * A entrada de químico aceita na simulação e recusa na execução: o pedido conta como aceito, o hidrogênio vai ao
     * pendente e o lado recusa pedidos novos; com a entrada aceitando de novo, o pendente sai nela.
     */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void chemicalPendingLeavesWhenRoom(GameTestHelper helper) {
        if (!ChemicalTestTanks.enabled()) {
            helper.succeed();
            return;
        }
        PatternDockBlockEntity dock = setUp(helper);
        BlockPos machine = helper.absolutePos(MACHINE);
        helper.startSequence()
                .thenIdle(1)
                .thenExecute(() -> {
                    link(helper, dock);
                    ChemicalTestSupport.dockMachineLying(machine, true);
                })
                .thenWaitUntil(DockChemicalGameTests::room)
                .thenExecute(() -> {
                    helper.assertTrue(offer(helper, dock) == DockDelivery.Result.ACCEPTED,
                            "recusou: motivo " + dock.side(NORTH).lastRefusal());
                    helper.assertTrue(ChemicalTestSupport.dockMachineItem(machine).is(Items.IRON_ORE),
                            "o item não entrou");
                    helper.assertTrue(ChemicalTestSupport.dockMachineInput(machine, HYDROGEN) == 0,
                            "a entrada encheu");
                    DockSide side = dock.side(NORTH);
                    List<Object> pending = side.pendingChemicals();
                    helper.assertTrue(pending.size() == 1 && Chemicals.amount(pending.get(0)) == 1_000,
                            "pendente: " + pending);
                    helper.assertTrue(side.pendingItems().isEmpty() && side.pendingFluids().isEmpty(),
                            "pendente de outro tipo");
                    helper.assertFalse(DockDelivery.acceptsOrders(dock, NORTH), "o lado com pendente aceita pedidos");
                })
                .thenWaitUntil(DockChemicalGameTests::room)
                .thenExecute(() -> {
                    helper.assertTrue(offer(helper, dock) == DockDelivery.Result.REFUSED, "aceitou com o pendente");
                    helper.assertTrue(dock.side(NORTH).lastRefusal() == DockStatus.PENDING,
                            "motivo " + dock.side(NORTH).lastRefusal());
                    ChemicalTestSupport.dockMachineLying(machine, false);
                    helper.assertTrue(DockDelivery.flushPending(dock, NORTH), "o pendente não saiu");
                    long input = ChemicalTestSupport.dockMachineInput(machine, HYDROGEN);
                    helper.assertTrue(input == 1_000, "entrada: " + input);
                    helper.assertTrue(DockDelivery.acceptsOrders(dock, NORTH), "o lado não voltou a aceitar");
                })
                .thenSucceed();
    }

    /**
     * O oxigênio pronto na saída da máquina (a face Extrai de químico, por baixo) volta ao tanque de químico encostado
     * no lado Norte da Doca depois do pedido, que acorda a máquina na volta.
     */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void chemicalReturnsToTank(GameTestHelper helper) {
        if (!ChemicalTestTanks.enabled()) {
            helper.succeed();
            return;
        }
        BlockPos tank = helper.absolutePos(TANK);
        ChemicalTestSupport.reset(tank);
        helper.setBlock(TANK, ChemicalTestTanks.BLOCK);
        PatternDockBlockEntity dock = setUp(helper);
        BlockPos machine = helper.absolutePos(MACHINE);
        helper.startSequence()
                .thenWaitUntil(() -> registered(dock))
                .thenExecute(() -> {
                    link(helper, dock);
                    ChemicalTestSupport.fillDockMachineOutput(machine, OXYGEN, 2_000);
                })
                .thenWaitUntil(DockChemicalGameTests::room)
                .thenExecute(() -> helper.assertTrue(offer(helper, dock) == DockDelivery.Result.ACCEPTED,
                        "recusou: motivo " + dock.side(NORTH).lastRefusal()))
                .thenWaitUntil(() -> helper.assertTrue(
                        ChemicalTestSupport.amount(helper.getLevel(), tank, OXYGEN) == 2_000, "o oxigênio não voltou"))
                .thenExecute(() -> {
                    helper.assertTrue(ChemicalTestSupport.dockMachineOutput(machine) == 0, "sobrou na saída");
                    long returned = dock.side(NORTH).returnedChemical();
                    helper.assertTrue(returned == 2_000, "devolvidos: " + returned);
                    helper.assertTrue(ChemicalTestSupport.dockMachineInput(machine, HYDROGEN) == 1_000,
                            "a entrada de químico voltou ao provedor");
                })
                .thenSucceed();
    }
}
