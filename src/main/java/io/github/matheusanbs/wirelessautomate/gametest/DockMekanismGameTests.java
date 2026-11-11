package io.github.matheusanbs.wirelessautomate.gametest;

import io.github.matheusanbs.wirelessautomate.block.RouterBlock;
import io.github.matheusanbs.wirelessautomate.block.RouterTier;
import io.github.matheusanbs.wirelessautomate.dock.DockDelivery;
import io.github.matheusanbs.wirelessautomate.dock.DockPendingLot;
import io.github.matheusanbs.wirelessautomate.dock.DockReturn;
import io.github.matheusanbs.wirelessautomate.dock.DockSide;
import io.github.matheusanbs.wirelessautomate.dock.PatternDockBlockEntity;
import io.github.matheusanbs.wirelessautomate.network.NetworkManager;
import io.github.matheusanbs.wirelessautomate.network.PortMode;
import io.github.matheusanbs.wirelessautomate.network.ResourceType;
import io.github.matheusanbs.wirelessautomate.range.RemoteMachines;
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
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.items.IItemHandler;

/**
 * A volta da Doca com uma máquina de verdade do Mekanism, a Basic Smelting Factory (três processos): ela nunca chama
 * {@code Level.blockEntityChanged} (o {@code setChanged} do Mekanism só marca o chunk), então a volta só acha a saída
 * pelo recuo. O lingote de um pedido de 1 minério bruto chega ao baú encostado no lado, e uma fábrica sem energia,
 * com o minério parado na entrada, fica na fila depois do teto e da folga, porque as faces Insere (os três slots de
 * entrada da fábrica) não estão vazias. Rodam só na run {@code runGameTestServerChemicals}; sem o Mekanism, só passam.
 *
 * <p>As faces da fábrica vêm desligadas: o teste liga a entrada de item em Cima, a saída em Baixo e a energia pelos
 * lados pelo {@link ChemicalTestSupport#mekanismSides} (por reflexão, sem tipos do Mekanism aqui). A energia vai pela
 * capability de FE do NeoForge.
 */
@GameTestHolder(ChemicalGameTests.NAMESPACE)
@PrefixGameTestTemplate(false)
public final class DockMekanismGameTests {
    private static final BlockPos DOCK = new BlockPos(1, 1, 1);
    /** O baú no lugar do provedor, encostado no lado Norte da Doca. */
    private static final BlockPos CHEST = DOCK.north();
    private static final BlockPos FACTORY = new BlockPos(3, 1, 1);
    private static final int NORTH = Direction.NORTH.ordinal();
    private static final ResourceLocation FACTORY_ID = ResourceLocation.fromNamespaceAndPath("mekanism",
            "basic_smelting_factory");
    /** Os slots de entrada da fábrica básica. */
    private static final int INPUT_SLOTS = 3;

    private DockMekanismGameTests() {
    }

    private static PatternDockBlockEntity scene(GameTestHelper helper) {
        helper.setBlock(DOCK, ModBlocks.PATTERN_DOCK.get().defaultBlockState().setValue(RouterBlock.TIER,
                RouterTier.BASIC));
        helper.setBlock(CHEST, Blocks.CHEST.defaultBlockState());
        helper.setBlock(FACTORY, BuiltInRegistries.BLOCK.get(FACTORY_ID).defaultBlockState());
        return helper.getBlockEntity(DOCK);
    }

    /** A fábrica no lado Norte: as faces dela ligadas e o perfil com item Insere em Cima e Extrai em Baixo. */
    private static void link(GameTestHelper helper, PatternDockBlockEntity dock) {
        BlockPos pos = helper.absolutePos(FACTORY);
        BlockState state = helper.getLevel().getBlockState(pos);
        ChemicalTestSupport.mekanismSides(helper.getLevel().getBlockEntity(pos));
        helper.assertTrue(dock.link(NORTH, pos, FACTORY_ID, RemoteMachines.frontOf(state)), "não vinculou");
        DockSide side = dock.side(NORTH);
        side.profile().face(ResourceType.ITEM, RemoteSide.TOP).setMode(PortMode.INSERT);
        side.profile().face(ResourceType.ITEM, RemoteSide.BOTTOM).setMode(PortMode.EXTRACT);
    }

    private static void registered(PatternDockBlockEntity dock) {
        if (!NetworkManager.get().hasVisitor(dock.returns())) {
            throw new GameTestAssertException("a volta da Doca não está no motor");
        }
    }

    private static void room() {
        if (!NetworkManager.get().budget().outsideRoom()) {
            throw new GameTestAssertException("sem orçamento neste tick");
        }
    }

    /** Enche a energia da fábrica pela capability de FE, pela primeira face que aceita. */
    private static void charge(GameTestHelper helper) {
        BlockPos pos = helper.absolutePos(FACTORY);
        for (Direction direction : Direction.values()) {
            IEnergyStorage energy = helper.getLevel().getCapability(Capabilities.EnergyStorage.BLOCK, pos, direction);
            if (energy != null && energy.receiveEnergy(Integer.MAX_VALUE, false) > 0) {
                return;
            }
        }
    }

    private static IItemHandler inputFace(GameTestHelper helper) {
        IItemHandler handler = helper.getLevel().getCapability(Capabilities.ItemHandler.BLOCK,
                helper.absolutePos(FACTORY), Direction.UP);
        helper.assertTrue(handler != null, "a face de cima da fábrica sem inventário");
        return handler;
    }

    private static int chestIngots(GameTestHelper helper) {
        ChestBlockEntity chest = helper.getBlockEntity(CHEST);
        int count = 0;
        for (int slot = 0; slot < chest.getContainerSize(); slot++) {
            if (chest.getItem(slot).is(Items.IRON_INGOT)) {
                count += chest.getItem(slot).getCount();
            }
        }
        return count;
    }

    /** O que a fábrica tem em cima e embaixo, a energia e a fila da volta, para a mensagem da falha. */
    private static String state(GameTestHelper helper, PatternDockBlockEntity dock) {
        StringBuilder out = new StringBuilder();
        BlockPos pos = helper.absolutePos(FACTORY);
        for (Direction face : new Direction[] {Direction.UP, Direction.DOWN}) {
            IItemHandler handler = helper.getLevel().getCapability(Capabilities.ItemHandler.BLOCK, pos, face);
            out.append(face).append('=');
            for (int slot = 0; handler != null && slot < handler.getSlots(); slot++) {
                out.append(handler.getStackInSlot(slot)).append(',');
            }
            out.append(' ');
        }
        IEnergyStorage energy = helper.getLevel().getCapability(Capabilities.EnergyStorage.BLOCK, pos,
                Direction.NORTH);
        out.append("energia=").append(energy == null ? "-" : energy.getEnergyStored() + "/"
                + energy.getMaxEnergyStored());
        out.append(" acordadas=").append(dock.returns().awakeCount())
                .append(" visitas=").append(dock.returns().visits());
        return out.toString();
    }

    private static void order(GameTestHelper helper, PatternDockBlockEntity dock) {
        helper.assertTrue(DockDelivery.offer(dock, NORTH, List.of(new ItemStack(Items.RAW_IRON)), List.of(), false)
                == DockDelivery.Result.ACCEPTED, "recusou: motivo " + dock.side(NORTH).lastRefusal());
        IItemHandler input = inputFace(helper);
        // a regra das faces Insere vazias lê todos os slots de entrada da fábrica pela face de cima
        helper.assertTrue(input.getSlots() == INPUT_SLOTS, "slots de entrada pela face de cima: " + input.getSlots());
    }

    /**
     * O pedido de 1 minério bruto: a fábrica cozinha (200 ticks dela) sem avisar a Doca, e o lingote chega ao baú pela
     * revisita em recuo. O minério no slot de entrada também segura a fábrica na fila durante a receita.
     */
    @GameTest(template = "empty", timeoutTicks = 400)
    public static void factoryOutputReturns(GameTestHelper helper) {
        if (!ChemicalTestTanks.enabled()) {
            helper.succeed();
            return;
        }
        PatternDockBlockEntity dock = scene(helper);
        helper.startSequence()
                .thenWaitUntil(() -> registered(dock))
                .thenExecute(() -> {
                    link(helper, dock);
                    charge(helper);
                })
                .thenWaitUntil(DockMekanismGameTests::room)
                .thenExecute(() -> order(helper, dock))
                .thenWaitUntil(() -> {
                    charge(helper);
                    if (chestIngots(helper) != 1) {
                        throw new GameTestAssertException("o lingote da fábrica não voltou: " + state(helper, dock));
                    }
                })
                .thenExecute(() -> helper.assertTrue(dock.side(NORTH).returned() == 1,
                        "devolvidos: " + dock.side(NORTH).returned()))
                .thenSucceed();
    }

    /**
     * A fábrica sem energia guarda o minério na entrada: com a folga vencida (o {@code lastActive} recuado), ela passa
     * do teto do recuo (~330 ticks) e continua na fila, porque as faces Insere não estão vazias.
     */
    @GameTest(template = "empty", timeoutTicks = 400)
    public static void factoryWithInputStaysQueued(GameTestHelper helper) {
        if (!ChemicalTestTanks.enabled()) {
            helper.succeed();
            return;
        }
        PatternDockBlockEntity dock = scene(helper);
        helper.startSequence()
                .thenWaitUntil(() -> registered(dock))
                .thenExecute(() -> link(helper, dock))
                .thenWaitUntil(DockMekanismGameTests::room)
                .thenExecute(() -> {
                    order(helper, dock);
                    long now = helper.getLevel().getServer().getTickCount();
                    dock.machineAt(helper.absolutePos(FACTORY)).setLastActive(now - DockReturn.IDLE_GRACE_TICKS);
                })
                .thenIdle(350)
                .thenExecute(() -> {
                    helper.assertTrue(dock.returns().awakeCount() == 1, "a fábrica com entrada saiu da fila");
                    IItemHandler input = inputFace(helper);
                    boolean held = false;
                    for (int slot = 0; slot < input.getSlots(); slot++) {
                        held |= input.getStackInSlot(slot).is(Items.RAW_IRON);
                    }
                    helper.assertTrue(held, "o minério saiu da entrada");
                })
                .thenSucceed();
    }

    /**
     * Um pedido de cinco pilhas iguais de 64 entra junto na fábrica (o inventário dela é do Mekanism, que limita cada
     * slot sozinho): os três slots de entrada ficam com 64, e os 128 que não couberam vão ao pendente numa linha só.
     */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void joinedStacksIntoFactory(GameTestHelper helper) {
        if (!ChemicalTestTanks.enabled()) {
            helper.succeed();
            return;
        }
        PatternDockBlockEntity dock = scene(helper);
        helper.startSequence()
                .thenWaitUntil(() -> registered(dock))
                .thenExecute(() -> link(helper, dock))
                .thenWaitUntil(DockMekanismGameTests::room)
                .thenExecute(() -> {
                    IItemHandler input = inputFace(helper);
                    helper.assertTrue(input.getClass().getName().startsWith("mekanism."),
                            "o inventário da fábrica não é do Mekanism: " + input.getClass().getName());
                    List<ItemStack> order = List.of(new ItemStack(Items.RAW_IRON, 64), new ItemStack(Items.RAW_IRON, 64),
                            new ItemStack(Items.RAW_IRON, 64), new ItemStack(Items.RAW_IRON, 64),
                            new ItemStack(Items.RAW_IRON, 64));
                    helper.assertTrue(DockDelivery.offer(dock, NORTH, order, List.of(), false)
                            == DockDelivery.Result.ACCEPTED, "recusou: motivo " + dock.side(NORTH).lastRefusal());
                    int inside = 0;
                    for (int slot = 0; slot < input.getSlots(); slot++) {
                        inside += input.getStackInSlot(slot).getCount();
                    }
                    helper.assertValueEqual(inside, INPUT_SLOTS * 64, "na entrada da fábrica");
                    DockPendingLot pending = dock.side(NORTH).pendingTotal();
                    helper.assertValueEqual(pending.items().size(), 1, "linhas no pendente");
                    helper.assertValueEqual(pending.count(Items.RAW_IRON), 128L, "minério no pendente");
                })
                .thenSucceed();
    }

    /**
     * Ticks extras da fábrica no tick do servidor (o que a Time Wand faz: o ticker do block entity mais vezes), com a
     * energia cheia antes de cada um.
     */
    @SuppressWarnings("unchecked")
    private static void accelerate(GameTestHelper helper, int extra) {
        BlockPos pos = helper.absolutePos(FACTORY);
        BlockEntity be = helper.getLevel().getBlockEntity(pos);
        BlockState state = helper.getLevel().getBlockState(pos);
        BlockEntityTicker<BlockEntity> ticker = (BlockEntityTicker<BlockEntity>) state.getTicker(helper.getLevel(),
                be.getType());
        for (int i = 0; i < extra && ticker != null; i++) {
            charge(helper);
            ticker.tick(helper.getLevel(), pos, state, be);
        }
        charge(helper);
    }

    private static List<ItemStack> stack() {
        return List.of(new ItemStack(Items.RAW_IRON, 64));
    }

    /**
     * A fábrica acelerada (256 ticks dela por tick do servidor) enche a entrada, recusa o pedido seguinte e dorme na
     * entrega; ela nunca avisa a Doca, mas a volta tira a saída dela, e isso a acorda: o pedido seguinte entra assim
     * que cabe, bem antes do teto do sono (100 ticks). Sem o acordar pela volta, a fábrica ficava fora da escolha até o
     * teto, ociosa com a entrada vazia (o uso intenso: 5 Ultimate a 256×, 20 a 45% do tempo paradas).
     */
    @GameTest(template = "empty", timeoutTicks = 300)
    public static void returnWakesSleepingFactory(GameTestHelper helper) {
        if (!ChemicalTestTanks.enabled()) {
            helper.succeed();
            return;
        }
        PatternDockBlockEntity dock = scene(helper);
        long[] refusedAt = {-1};
        int[] accepted = {0};
        helper.startSequence()
                .thenWaitUntil(() -> registered(dock))
                .thenExecute(() -> {
                    link(helper, dock);
                    charge(helper);
                })
                .thenWaitUntil(DockMekanismGameTests::room)
                .thenWaitUntil(() -> {
                    // três pilhas enchem os três slots de entrada; a quarta não cabe e a fábrica dorme. O orçamento
                    // do motor é dividido com os testes do lote: sem folga, a entrega recusa por ele, então cada
                    // oferta espera a folga (e o tick seguinte, se ela acabar no meio).
                    while (accepted[0] < INPUT_SLOTS) {
                        if (!NetworkManager.get().budget().outsideRoom()) {
                            throw new GameTestAssertException("sem folga no orçamento");
                        }
                        helper.assertTrue(DockDelivery.offer(dock, NORTH, stack(), List.of(), false)
                                == DockDelivery.Result.ACCEPTED, "recusou a pilha " + accepted[0]);
                        accepted[0]++;
                    }
                    if (!NetworkManager.get().budget().outsideRoom()) {
                        throw new GameTestAssertException("sem folga no orçamento");
                    }
                    helper.assertTrue(DockDelivery.offer(dock, NORTH, stack(), List.of(), false)
                            == DockDelivery.Result.REFUSED, "a fábrica cheia aceitou");
                    long now = helper.getLevel().getServer().getTickCount();
                    helper.assertTrue(dock.machineAt(helper.absolutePos(FACTORY)).asleep(now),
                            "a fábrica que recusou não dormiu");
                    refusedAt[0] = now;
                })
                .thenWaitUntil(() -> {
                    accelerate(helper, 255);
                    long now = helper.getLevel().getServer().getTickCount();
                    if (now - refusedAt[0] >= 60) {
                        helper.fail("a fábrica não recebeu de novo em 60 ticks (devolvidos "
                                + dock.side(NORTH).returned() + "): " + state(helper, dock));
                    }
                    if (!NetworkManager.get().budget().outsideRoom() || DockDelivery.offer(dock, NORTH, stack(),
                            List.of(), false) != DockDelivery.Result.ACCEPTED) {
                        throw new GameTestAssertException("ainda recusa");
                    }
                })
                .thenSucceed();
    }
}
