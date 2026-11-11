package io.github.matheusanbs.wirelessautomate.gametest;

import io.github.matheusanbs.wirelessautomate.Config;
import io.github.matheusanbs.wirelessautomate.block.RouterBlock;
import io.github.matheusanbs.wirelessautomate.block.RouterTier;
import io.github.matheusanbs.wirelessautomate.dock.DockDelivery;
import io.github.matheusanbs.wirelessautomate.dock.DockPendingContents;
import io.github.matheusanbs.wirelessautomate.dock.DockSide;
import io.github.matheusanbs.wirelessautomate.dock.DockStatus;
import io.github.matheusanbs.wirelessautomate.dock.PatternDockBlockEntity;
import io.github.matheusanbs.wirelessautomate.network.Chemicals;
import io.github.matheusanbs.wirelessautomate.network.NetworkManager;
import io.github.matheusanbs.wirelessautomate.network.PortMode;
import io.github.matheusanbs.wirelessautomate.network.ResourceType;
import io.github.matheusanbs.wirelessautomate.range.RemoteSide;
import io.github.matheusanbs.wirelessautomate.registry.ModBlocks;
import io.github.matheusanbs.wirelessautomate.registry.ModDataComponents;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.jetbrains.annotations.Nullable;

/**
 * Químicos na Doca ({@link DockDelivery} chamada direto, sem o AE2 nem o RS2), numa máquina de teste com item por cima,
 * entrada de químico pelos lados e saída de químico por baixo ({@link ChemicalTestTanks#DOCK_MACHINE_BLOCK}): o pedido
 * "item + químico" põe cada um na face do tipo dele; o químico que a máquina recusa na execução vai ao pendente e sai
 * quando ela tem lugar e passa pelo NBT, pelo item da Doca quebrada e pela recolocação sem perda nem duplicação; com
 * dois químicos, cada um vai à face Insere mais seletiva; o químico de saída volta ao tanque de químico encostado no
 * lado, na vazão do tier, e o que o tanque recusa depois de aceitar na simulação volta à máquina. Rodam só na run
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
     * os testes dos químicos nas redes ({@link DockChemicalAe2GameTests}, {@link DockChemicalRs2GameTests}).
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

    /** O pendente químico do lado Norte é 1.000 mB de hidrogênio, lido pelo Mekanism. */
    private static void pendingHydrogen(GameTestHelper helper, PatternDockBlockEntity dock, String where) {
        DockSide side = dock.side(NORTH);
        helper.assertTrue(side != null && side.hasPending() && side.pendingChemicals().size() == 1
                && ChemicalTestSupport.isChemical(side.pendingChemicals().get(0), HYDROGEN, 1_000),
                where + ": " + (side == null ? null : side.pendingChemicals()));
    }

    /** O componente do item tem, no Norte, só 1.000 mB de hidrogênio, em NBT. */
    private static void componentHydrogen(GameTestHelper helper, @Nullable DockPendingContents contents,
            String where) {
        HolderLookup.Provider registries = helper.getLevel().registryAccess();
        helper.assertTrue(contents != null && contents.sides().size() == 1, where + ": " + contents);
        DockPendingContents.Side saved = contents.sides().get(0);
        helper.assertTrue(saved.side() == NORTH && MACHINE_ID.equals(saved.block()) && saved.items().isEmpty()
                && saved.fluids().isEmpty() && saved.chemicals().size() == 1, where + ": " + saved);
        Object chemical = Chemicals.parseStack(saved.chemicals().get(0), registries);
        helper.assertTrue(chemical != null && ChemicalTestSupport.isChemical(chemical, HYDROGEN, 1_000),
                where + ", o químico: " + saved.chemicals());
    }

    /**
     * O hidrogênio no pendente (a entrada mente) passa pelo NBT de um block entity novo, vai ao item da Doca quebrada,
     * que um block entity solto (sem mundo) também guarda e devolve, e volta com a Doca recolocada; vinculada a mesma
     * máquina, sai inteiro nela, uma vez só.
     */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void chemicalPendingSurvivesSaveBreakAndPlace(GameTestHelper helper) {
        if (!ChemicalTestTanks.enabled()) {
            helper.succeed();
            return;
        }
        PatternDockBlockEntity dock = setUp(helper);
        BlockPos machine = helper.absolutePos(MACHINE);
        PatternDockBlockEntity[] placed = new PatternDockBlockEntity[1];
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
                    pendingHydrogen(helper, dock, "pendente");
                    HolderLookup.Provider registries = helper.getLevel().registryAccess();
                    CompoundTag tag = dock.saveWithoutMetadata(registries);
                    PatternDockBlockEntity loaded = new PatternDockBlockEntity(dock.getBlockPos(),
                            dock.getBlockState());
                    loaded.loadWithComponents(tag, registries);
                    pendingHydrogen(helper, loaded, "NBT");
                    // fora do mundo (sem level) o químico também vai ao item
                    componentHydrogen(helper, loaded.collectComponents().get(
                            ModDataComponents.PATTERN_DOCK_PENDING.get()), "item do block entity solto");

                    helper.getLevel().destroyBlock(helper.absolutePos(DOCK), true);
                    List<ItemStack> docks = DockBreakGameTests.takeDocks(helper, DOCK);
                    helper.assertTrue(docks.size() == 1, "Docas no chão: " + docks);
                    ItemStack item = docks.get(0);
                    componentHydrogen(helper, item.get(ModDataComponents.PATTERN_DOCK_PENDING.get()), "item");

                    PatternDockBlockEntity loose = new PatternDockBlockEntity(dock.getBlockPos(),
                            dock.getBlockState());
                    loose.applyComponentsFromItemStack(item);
                    helper.assertTrue(loose.side(NORTH) != null && loose.side(NORTH).keepsPending(),
                            "o block entity solto perdeu o químico do item");
                    componentHydrogen(helper, loose.collectComponents().get(
                            ModDataComponents.PATTERN_DOCK_PENDING.get()), "item de novo do block entity solto");

                    placed[0] = DockBreakGameTests.place(helper, item, DOCK);
                    pendingHydrogen(helper, placed[0], "recolocada");
                    helper.assertTrue(placed[0].side(NORTH).machines().isEmpty(), "o lado voltou com máquinas");
                })
                .thenIdle(1)
                .thenExecute(() -> link(helper, placed[0]))
                .thenWaitUntil(DockChemicalGameTests::room)
                .thenExecute(() -> {
                    helper.assertTrue(ChemicalTestSupport.dockMachineInput(machine, HYDROGEN) == 0,
                            "o hidrogênio entrou com a entrada mentindo");
                    ChemicalTestSupport.dockMachineLying(machine, false);
                    helper.assertTrue(DockDelivery.flushPending(placed[0], NORTH), "o pendente não saiu");
                    long input = ChemicalTestSupport.dockMachineInput(machine, HYDROGEN);
                    helper.assertTrue(input == 1_000, "entrada: " + input);
                    helper.assertFalse(placed[0].side(NORTH).keepsPending(), "sobrou pendente");
                    helper.assertTrue(DockDelivery.flushPending(placed[0], NORTH), "o lado vazio não está livre");
                    helper.assertTrue(ChemicalTestSupport.dockMachineInput(machine, HYDROGEN) == 1_000,
                            "o pendente saiu duas vezes");
                })
                .thenSucceed();
    }

    /**
     * Pedido "1 minério + 1.000 mB de oxigênio + 1.000 mB de hidrogênio" numa máquina com duas entradas de químico: a
     * de trás aceita os dois e a de cima (onde também entra o item) só o oxigênio. O oxigênio vai à de cima, a mais
     * seletiva entre os químicos (o item não conta na carga das faces de químico), e o hidrogênio à de trás. Na de trás
     * o oxigênio barraria o hidrogênio, que iria ao pendente.
     */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void chemicalGoesToMostSelectiveFace(GameTestHelper helper) {
        if (!ChemicalTestTanks.enabled()) {
            helper.succeed();
            return;
        }
        PatternDockBlockEntity dock = setUp(helper);
        BlockPos machine = helper.absolutePos(MACHINE);
        ChemicalTestSupport.dockMachineSelectiveTop(machine, OXYGEN);
        helper.startSequence()
                .thenIdle(1)
                .thenExecute(() -> link(helper, dock))
                .thenWaitUntil(DockChemicalGameTests::room)
                .thenExecute(() -> {
                    DockDelivery.Result result = DockDelivery.offer(dock, NORTH,
                            List.of(new ItemStack(Items.IRON_ORE)), List.of(),
                            List.of(ChemicalTestSupport.chemical(OXYGEN, 1_000),
                                    ChemicalTestSupport.chemical(HYDROGEN, 1_000)), false);
                    helper.assertTrue(result == DockDelivery.Result.ACCEPTED,
                            "recusou: motivo " + dock.side(NORTH).lastRefusal());
                    helper.assertTrue(ChemicalTestSupport.dockMachineItem(machine).is(Items.IRON_ORE),
                            "o item não entrou");
                    long top = ChemicalTestSupport.dockMachineTop(machine, OXYGEN);
                    helper.assertTrue(top == 1_000, "oxigênio na entrada de cima: " + top);
                    long back = ChemicalTestSupport.dockMachineInput(machine, HYDROGEN);
                    helper.assertTrue(back == 1_000, "hidrogênio na entrada de trás: " + back);
                    helper.assertFalse(dock.side(NORTH).hasPending(), "sobrou pendente: "
                            + dock.side(NORTH).pendingChemicals());
                })
                .thenSucceed();
    }

    /**
     * 20.000 mB de oxigênio na saída, mais que um segundo da vazão de químico da Doca Básica
     * ({@code ResourceType.CHEMICAL}, 8.000 mB/s): a primeira visita devolve no máximo um segundo (mais o que o balde
     * enche em dois ticks) e o resto chega depois, inteiro.
     */
    @GameTest(template = "empty", timeoutTicks = 300)
    public static void chemicalReturnRespectsRate(GameTestHelper helper) {
        if (!ChemicalTestTanks.enabled()) {
            helper.succeed();
            return;
        }
        BlockPos tank = helper.absolutePos(TANK);
        ChemicalTestSupport.reset(tank);
        helper.setBlock(TANK, ChemicalTestTanks.BLOCK);
        PatternDockBlockEntity dock = setUp(helper);
        BlockPos machine = helper.absolutePos(MACHINE);
        long rate = Config.TIERS.get(RouterTier.BASIC).rate(ResourceType.CHEMICAL);
        long total = 20_000;
        helper.startSequence()
                .thenWaitUntil(() -> registered(dock))
                .thenExecute(() -> {
                    helper.assertTrue(rate > 0 && rate < total, "vazão de químico da Básica: " + rate);
                    link(helper, dock);
                    ChemicalTestSupport.fillDockMachineOutput(machine, OXYGEN, total);
                })
                .thenWaitUntil(DockChemicalGameTests::room)
                .thenExecute(() -> helper.assertTrue(offer(helper, dock) == DockDelivery.Result.ACCEPTED,
                        "recusou: motivo " + dock.side(NORTH).lastRefusal()))
                .thenWaitUntil(() -> helper.assertTrue(
                        ChemicalTestSupport.amount(helper.getLevel(), tank, OXYGEN) > 0, "nada voltou"))
                .thenExecute(() -> {
                    long first = ChemicalTestSupport.amount(helper.getLevel(), tank, OXYGEN);
                    helper.assertTrue(first <= rate + rate / 10, "a primeira visita passou da vazão: " + first);
                    helper.assertTrue(dock.side(NORTH).returnedChemical() == first,
                            "devolvidos: " + dock.side(NORTH).returnedChemical());
                })
                .thenWaitUntil(() -> helper.assertTrue(
                        ChemicalTestSupport.amount(helper.getLevel(), tank, OXYGEN) == total,
                        "voltaram " + ChemicalTestSupport.amount(helper.getLevel(), tank, OXYGEN)))
                .thenExecute(() -> {
                    helper.assertTrue(ChemicalTestSupport.dockMachineOutput(machine) == 0, "sobrou na saída");
                    helper.assertTrue(dock.side(NORTH).returnedChemical() == total,
                            "devolvidos: " + dock.side(NORTH).returnedChemical());
                })
                .thenSucceed();
    }

    /**
     * O tanque vizinho aceita o oxigênio na simulação e recusa na execução: o que saiu da máquina volta para a saída
     * dela ({@code keepChemical}), nada conta como devolvido e o tanque fica vazio.
     */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void chemicalRefusedByTankGoesBackToMachine(GameTestHelper helper) {
        if (!ChemicalTestTanks.enabled()) {
            helper.succeed();
            return;
        }
        BlockPos tank = helper.absolutePos(TANK);
        ChemicalTestSupport.reset(tank);
        ChemicalTestSupport.tankLying(tank);
        helper.setBlock(TANK, ChemicalTestTanks.BLOCK);
        PatternDockBlockEntity dock = setUp(helper);
        BlockPos machine = helper.absolutePos(MACHINE);
        ChemicalTestSupport.dockMachineTakesBack(machine);
        helper.startSequence()
                .thenWaitUntil(() -> registered(dock))
                .thenExecute(() -> {
                    link(helper, dock);
                    ChemicalTestSupport.fillDockMachineOutput(machine, OXYGEN, 2_000);
                })
                .thenWaitUntil(DockChemicalGameTests::room)
                .thenExecute(() -> helper.assertTrue(offer(helper, dock) == DockDelivery.Result.ACCEPTED,
                        "recusou: motivo " + dock.side(NORTH).lastRefusal()))
                .thenWaitUntil(() -> helper.assertTrue(ChemicalTestSupport.tankRefusals(tank) > 0,
                        "a volta não tentou o tanque"))
                .thenExecute(() -> {
                    long output = ChemicalTestSupport.dockMachineOutput(machine);
                    helper.assertTrue(output == 2_000, "na saída: " + output);
                    helper.assertTrue(ChemicalTestSupport.amount(helper.getLevel(), tank, OXYGEN) == 0,
                            "o tanque que recusou ficou com oxigênio");
                    helper.assertTrue(dock.side(NORTH).returnedChemical() == 0,
                            "devolvidos: " + dock.side(NORTH).returnedChemical());
                })
                .thenSucceed();
    }
}
