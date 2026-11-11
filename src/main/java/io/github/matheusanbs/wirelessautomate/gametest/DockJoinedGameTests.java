package io.github.matheusanbs.wirelessautomate.gametest;

import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.block.RouterBlock;
import io.github.matheusanbs.wirelessautomate.block.RouterTier;
import io.github.matheusanbs.wirelessautomate.dock.DockDelivery;
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
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * A volta juntada numa rede que simula ({@code dock/DockReturnItems}): duas máquinas de 1.000 pedregulhos num lado
 * encostado na rede de teste ({@link TestMachines.FakeNetwork}, uma {@code DockReturnNetwork} como a ME) dão uma
 * simulação e uma inserção por visita; com a rede aceitando só parte, as máquinas só dão o que coube; com a rede que
 * mente (aceita na simulação e recusa de verdade), o que ela recusou volta às máquinas. Nada se perde (nada no chão) nem
 * duplica (a rede, as máquinas e o contador do lado fecham). E a entrega com a dica de slot ({@code dock/DockStacker}):
 * a dica não passa de um pedido para o seguinte, e o inventário comum nunca recebe mais que uma pilha por chamada.
 */
@GameTestHolder(WirelessAutomate.MODID)
@PrefixGameTestTemplate(false)
public final class DockJoinedGameTests {
    private static final BlockPos DOCK = new BlockPos(1, 1, 1);
    private static final BlockPos NETWORK = DOCK.north();
    private static final BlockPos FIRST = new BlockPos(3, 1, 1);
    private static final BlockPos SECOND = new BlockPos(3, 1, 3);
    private static final int NORTH = Direction.NORTH.ordinal();
    private static final int EACH = 1000;

    private DockJoinedGameTests() {
    }

    /** A Doca Ultimate (sem limite de vazão), a rede de teste ao norte e dois slots grandes vazios. */
    private static PatternDockBlockEntity scene(GameTestHelper helper) {
        for (BlockPos rel : List.of(NETWORK, FIRST, SECOND)) {
            TestMachines.reset(helper.absolutePos(rel));
        }
        helper.setBlock(DOCK, ModBlocks.PATTERN_DOCK.get().defaultBlockState().setValue(RouterBlock.TIER,
                RouterTier.ULTIMATE));
        helper.setBlock(NETWORK, TestMachines.FAKE_NETWORK);
        helper.setBlock(FIRST, TestMachines.BIG_SLOT);
        helper.setBlock(SECOND, TestMachines.BIG_SLOT);
        return helper.getBlockEntity(DOCK);
    }

    private static void registered(PatternDockBlockEntity dock) {
        if (!NetworkManager.get().hasVisitor(dock.returns())) {
            throw new GameTestAssertException("a volta da Doca não está no motor");
        }
    }

    /** Põe {@link #EACH} pedregulhos nos dois slots, vincula os dois ao lado Norte (Baixo Extrai) e os acorda. */
    private static void fill(GameTestHelper helper, PatternDockBlockEntity dock) {
        for (BlockPos rel : List.of(FIRST, SECOND)) {
            BlockPos pos = helper.absolutePos(rel);
            TestMachines.bigSlot(pos).set(Items.COBBLESTONE, EACH);
            helper.assertTrue(dock.link(NORTH, pos, BuiltInRegistries.BLOCK.getKey(TestMachines.BIG_SLOT),
                    Direction.NORTH.ordinal()), "não vinculou " + rel);
        }
        dock.side(NORTH).profile().face(ResourceType.ITEM, RemoteSide.BOTTOM).setMode(PortMode.EXTRACT);
        for (BlockPos rel : List.of(FIRST, SECOND)) {
            helper.getLevel().blockEntityChanged(helper.absolutePos(rel));
        }
    }

    private static TestMachines.FakeNetwork network(GameTestHelper helper) {
        return TestMachines.fakeNetwork(helper.absolutePos(NETWORK));
    }

    private static int held(GameTestHelper helper) {
        return TestMachines.bigSlot(helper.absolutePos(FIRST)).count()
                + TestMachines.bigSlot(helper.absolutePos(SECOND)).count();
    }

    private static int putBack(GameTestHelper helper) {
        return TestMachines.bigSlot(helper.absolutePos(FIRST)).inserts()
                + TestMachines.bigSlot(helper.absolutePos(SECOND)).inserts();
    }

    private static void nothingOnTheGround(GameTestHelper helper) {
        List<ItemEntity> dropped = helper.getLevel().getEntitiesOfClass(ItemEntity.class,
                new AABB(helper.absolutePos(DOCK)).inflate(4));
        helper.assertTrue(dropped.isEmpty(), "itens no chão: " + dropped);
    }

    /** Espera a primeira visita que pôs algo na rede (as duas máquinas acordam no mesmo tick). */
    private static void returned(GameTestHelper helper) {
        if (network(helper).stored == 0) {
            throw new GameTestAssertException("nada voltou ainda");
        }
    }

    /** A rede aceita tudo: os 2.000 numa visita, com uma simulação e uma inserção, e as máquinas vazias. */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void joinedReturnOneCallPerItem(GameTestHelper helper) {
        PatternDockBlockEntity dock = scene(helper);
        helper.startSequence()
                .thenWaitUntil(() -> registered(dock))
                .thenExecute(() -> fill(helper, dock))
                .thenWaitUntil(() -> returned(helper))
                .thenExecute(() -> {
                    TestMachines.FakeNetwork network = network(helper);
                    helper.assertValueEqual(network.stored, 2L * EACH, "na rede");
                    helper.assertValueEqual(held(helper), 0, "nas máquinas");
                    helper.assertValueEqual(network.simulations, 1, "simulações na rede");
                    helper.assertValueEqual(network.inserts, 1, "inserções na rede");
                    helper.assertValueEqual(dock.side(NORTH).returned(), 2L * EACH, "contador do lado");
                })
                .thenSucceed();
    }

    /**
     * A rede só tem lugar para 1.500: as máquinas dão só isso (nada sai para voltar depois), ficam com 500 e o lado
     * marca o provedor cheio.
     */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void joinedReturnPartialNetwork(GameTestHelper helper) {
        PatternDockBlockEntity dock = scene(helper);
        helper.startSequence()
                .thenWaitUntil(() -> registered(dock))
                .thenExecute(() -> {
                    network(helper).capacity = 1500;
                    fill(helper, dock);
                })
                .thenWaitUntil(() -> returned(helper))
                .thenExecute(() -> {
                    helper.assertValueEqual(network(helper).stored, 1500L, "na rede");
                    helper.assertValueEqual(held(helper), 500, "nas máquinas");
                    helper.assertValueEqual(putBack(helper), 0, "devolvidos às máquinas (saiu mais que coube)");
                    helper.assertValueEqual(dock.side(NORTH).returned(), 1500L, "contador do lado");
                    helper.assertTrue(dock.side(NORTH).providerFull(), "o lado não marcou o provedor cheio");
                    nothingOnTheGround(helper);
                })
                .thenSucceed();
    }

    /**
     * A rede aceita tudo na simulação e só 1.500 de verdade: os 500 recusados voltam às máquinas de onde saíram, o
     * contador do lado conta só o que entrou, e nada vai ao chão.
     */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void joinedReturnLyingNetwork(GameTestHelper helper) {
        PatternDockBlockEntity dock = scene(helper);
        helper.startSequence()
                .thenWaitUntil(() -> registered(dock))
                .thenExecute(() -> {
                    network(helper).realCapacity = 1500;
                    fill(helper, dock);
                })
                .thenWaitUntil(() -> returned(helper))
                .thenExecute(() -> {
                    helper.assertValueEqual(network(helper).stored, 1500L, "na rede");
                    helper.assertValueEqual(held(helper), 500, "nas máquinas");
                    helper.assertTrue(putBack(helper) > 0, "o recusado não voltou às máquinas");
                    helper.assertValueEqual(dock.side(NORTH).returned(), 1500L, "contador do lado");
                    helper.assertTrue(dock.side(NORTH).providerFull(), "o lado não marcou o provedor cheio");
                    nothingOnTheGround(helper);
                })
                .thenSucceed();
    }

    // ------------------------------------------------------------------ a entrega com a dica de slot

    private static PatternDockBlockEntity deliveryScene(GameTestHelper helper, Block machine) {
        TestMachines.reset(helper.absolutePos(FIRST));
        helper.setBlock(DOCK, ModBlocks.PATTERN_DOCK.get().defaultBlockState().setValue(RouterBlock.TIER,
                RouterTier.BASIC));
        helper.setBlock(FIRST, machine);
        return helper.getBlockEntity(DOCK);
    }

    /** Vincula a máquina em {@link #FIRST} ao lado Norte, com Cima Insere. */
    private static void linkInput(GameTestHelper helper, PatternDockBlockEntity dock, Block machine) {
        helper.assertTrue(dock.link(NORTH, helper.absolutePos(FIRST), BuiltInRegistries.BLOCK.getKey(machine),
                Direction.NORTH.ordinal()), "não vinculou");
        dock.side(NORTH).profile().face(ResourceType.ITEM, RemoteSide.TOP).setMode(PortMode.INSERT);
    }

    private static void room() {
        if (!NetworkManager.get().budget().outsideRoom()) {
            throw new GameTestAssertException("sem orçamento neste tick");
        }
    }

    private static void offer(GameTestHelper helper, PatternDockBlockEntity dock, ItemStack... stacks) {
        helper.assertTrue(DockDelivery.offer(dock, NORTH, List.of(stacks), List.of(), false)
                == DockDelivery.Result.ACCEPTED, "pedido recusado: " + dock.side(NORTH).lastRefusal());
    }

    private static void assertSlot(GameTestHelper helper, ChestBlockEntity chest, int slot, ItemStack expected) {
        ItemStack stack = chest.getItem(slot);
        helper.assertTrue(ItemStack.matches(stack, expected), "slot " + slot + ": " + stack + ", esperado " + expected);
    }

    /**
     * A dica do slot vale só dentro de um pedido: o primeiro põe 32 pedregulhos no slot 1 (o 0 tem terra); trocado o
     * slot 0 por 1 pedregulho entre os pedidos, o segundo vai para ele, como no {@code insertItemStacked} (com a dica
     * velha, iria para o slot 1). No mesmo pedido, três pilhas de 64 enchem os slots na ordem do helper.
     */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void deliveryHintStaysInOneOrder(GameTestHelper helper) {
        PatternDockBlockEntity dock = deliveryScene(helper, Blocks.CHEST);
        ChestBlockEntity chest = helper.getBlockEntity(FIRST);
        helper.startSequence()
                .thenExecute(() -> {
                    linkInput(helper, dock, Blocks.CHEST);
                    chest.setItem(0, new ItemStack(Items.DIRT));
                })
                .thenWaitUntil(DockJoinedGameTests::room)
                .thenExecute(() -> {
                    offer(helper, dock, new ItemStack(Items.COBBLESTONE, 32));
                    assertSlot(helper, chest, 1, new ItemStack(Items.COBBLESTONE, 32));
                    chest.setItem(0, new ItemStack(Items.COBBLESTONE));
                })
                .thenWaitUntil(DockJoinedGameTests::room)
                .thenExecute(() -> {
                    offer(helper, dock, new ItemStack(Items.COBBLESTONE, 32));
                    assertSlot(helper, chest, 0, new ItemStack(Items.COBBLESTONE, 33));
                    assertSlot(helper, chest, 1, new ItemStack(Items.COBBLESTONE, 32));
                    chest.setItem(2, new ItemStack(Items.DIRT));
                    chest.setItem(3, new ItemStack(Items.COBBLESTONE, 10));
                })
                .thenWaitUntil(DockJoinedGameTests::room)
                .thenExecute(() -> {
                    // Como o helper: o 0 (+31), o 1 (+32) e o 3 (+54) vão a 64, e o resto (75) enche o 4 e põe 11 no 5.
                    offer(helper, dock, new ItemStack(Items.COBBLESTONE, 64), new ItemStack(Items.COBBLESTONE, 64),
                            new ItemStack(Items.COBBLESTONE, 64));
                    assertSlot(helper, chest, 0, new ItemStack(Items.COBBLESTONE, 64));
                    assertSlot(helper, chest, 1, new ItemStack(Items.COBBLESTONE, 64));
                    assertSlot(helper, chest, 2, new ItemStack(Items.DIRT));
                    assertSlot(helper, chest, 3, new ItemStack(Items.COBBLESTONE, 64));
                    assertSlot(helper, chest, 4, new ItemStack(Items.COBBLESTONE, 64));
                    assertSlot(helper, chest, 5, new ItemStack(Items.COBBLESTONE, 11));
                    helper.assertFalse(dock.side(NORTH).hasPending(), "sobrou pendente");
                })
                .thenSucceed();
    }

    /**
     * Três pilhas iguais para um inventário comum que guarda a pilha como veio ({@link TestMachines.NaiveSlots}): a
     * entrega não as junta, e nenhuma chamada passa de 64.
     */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void deliveryNeverJoinsForCommonInventory(GameTestHelper helper) {
        PatternDockBlockEntity dock = deliveryScene(helper, TestMachines.NAIVE_SLOTS);
        helper.startSequence()
                .thenExecute(() -> linkInput(helper, dock, TestMachines.NAIVE_SLOTS))
                .thenWaitUntil(DockJoinedGameTests::room)
                .thenExecute(() -> {
                    offer(helper, dock, new ItemStack(Items.COBBLESTONE, 64), new ItemStack(Items.COBBLESTONE, 64),
                            new ItemStack(Items.COBBLESTONE, 64));
                    TestMachines.NaiveSlots slots = TestMachines.naiveSlots(helper.absolutePos(FIRST));
                    helper.assertValueEqual(slots.largestCall(), 64, "maior pilha numa chamada");
                    helper.assertValueEqual(slots.total(), 192, "na máquina");
                })
                .thenSucceed();
    }
}
