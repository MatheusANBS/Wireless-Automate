package io.github.matheusanbs.wirelessautomate.gametest;

import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.block.RouterBlock;
import io.github.matheusanbs.wirelessautomate.block.RouterTier;
import io.github.matheusanbs.wirelessautomate.dock.DockDelivery;
import io.github.matheusanbs.wirelessautomate.dock.DockSide;
import io.github.matheusanbs.wirelessautomate.dock.DockWork;
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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * O sinal "trabalhando" para o cliente ({@link PatternDockBlockEntity#signalWork}): uma entrega, o pendente que sai e a
 * volta do resultado disparam o evento de bloco com o lado, que o jogo processa na Doca
 * ({@link PatternDockBlockEntity#workEvents}, {@link PatternDockBlockEntity#lastWorkSide}), e duas entregas dentro do
 * intervalo dão um evento só. O contador é da própria Doca: os testes do lote rodam em paralelo sem se ver.
 */
@GameTestHolder(WirelessAutomate.MODID)
@PrefixGameTestTemplate(false)
public final class DockWorkGameTests {
    private static final BlockPos DOCK = new BlockPos(1, 1, 1);
    private static final int NORTH = Direction.NORTH.ordinal();
    private static final int EAST = Direction.EAST.ordinal();
    private static final ResourceLocation FURNACE = BuiltInRegistries.BLOCK.getKey(Blocks.FURNACE);

    private DockWorkGameTests() {
    }

    /** Uma Doca com fornalhas viradas para o norte em {@code furnaces}, vinculadas ao lado Norte com o perfil delas. */
    private static PatternDockBlockEntity dockWithFurnaces(GameTestHelper helper, BlockPos... furnaces) {
        helper.setBlock(DOCK, ModBlocks.PATTERN_DOCK.get().defaultBlockState().setValue(RouterBlock.TIER,
                RouterTier.BASIC));
        for (BlockPos furnace : furnaces) {
            helper.setBlock(furnace, Blocks.FURNACE.defaultBlockState().setValue(AbstractFurnaceBlock.FACING,
                    Direction.NORTH));
        }
        return helper.getBlockEntity(DOCK);
    }

    private static void link(GameTestHelper helper, PatternDockBlockEntity dock, BlockPos... furnaces) {
        for (BlockPos furnace : furnaces) {
            helper.assertTrue(dock.link(NORTH, helper.absolutePos(furnace), FURNACE, Direction.NORTH.ordinal()),
                    "não vinculou " + furnace);
        }
        DockSide side = dock.side(NORTH);
        side.profile().face(ResourceType.ITEM, RemoteSide.TOP).setMode(PortMode.INSERT);
        side.profile().face(ResourceType.ITEM, RemoteSide.BACK).setMode(PortMode.INSERT);
        side.profile().face(ResourceType.ITEM, RemoteSide.BOTTOM).setMode(PortMode.EXTRACT);
    }

    /** Passo de espera: o orçamento do motor tem folga para uma oferta. */
    private static void room() {
        if (!NetworkManager.get().budget().outsideRoom()) {
            throw new GameTestAssertException("sem orçamento neste tick");
        }
    }

    private static void deliver(GameTestHelper helper, PatternDockBlockEntity dock) {
        List<ItemStack> order = List.of(new ItemStack(Items.IRON_ORE), new ItemStack(Items.COAL));
        helper.assertTrue(DockDelivery.offer(dock, NORTH, order, List.of(), false) == DockDelivery.Result.ACCEPTED,
                "recusou o pedido");
    }

    /** Uma entrega manda o evento de trabalho; o jogo o processa na Doca no fim do tick. */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void deliveryFiresWorkEvent(GameTestHelper helper) {
        BlockPos furnace = new BlockPos(3, 1, 1);
        PatternDockBlockEntity dock = dockWithFurnaces(helper, furnace);
        helper.startSequence()
                .thenIdle(1)
                .thenExecute(() -> {
                    link(helper, dock, furnace);
                    helper.assertTrue(dock.workEvents() == 0, "evento sem entrega: " + dock.workEvents());
                })
                .thenWaitUntil(DockWorkGameTests::room)
                .thenExecute(() -> deliver(helper, dock))
                .thenWaitUntil(() -> assertEvents(helper, dock, 1, NORTH))
                .thenSucceed();
    }

    /**
     * O pendente que sai manda o seu evento ({@code DockPending}): a entrega deixa 16 minérios pendentes, e depois do
     * intervalo a máquina esvazia e o pendente sai.
     */
    @GameTest(template = "empty", timeoutTicks = 250)
    public static void pendingFiresWorkEvent(GameTestHelper helper) {
        BlockPos furnace = new BlockPos(3, 1, 1);
        PatternDockBlockEntity dock = dockWithFurnaces(helper, furnace);
        helper.startSequence()
                .thenIdle(1)
                .thenExecute(() -> link(helper, dock, furnace))
                .thenWaitUntil(DockWorkGameTests::room)
                .thenExecute(() -> {
                    List<ItemStack> order = List.of(new ItemStack(Items.IRON_ORE, 40), new ItemStack(Items.IRON_ORE, 40));
                    helper.assertTrue(DockDelivery.offer(dock, NORTH, order, List.of(), false)
                            == DockDelivery.Result.ACCEPTED, "recusou o pedido");
                    helper.assertTrue(dock.side(NORTH).hasPending(), "sem pendente");
                })
                .thenWaitUntil(() -> assertEvents(helper, dock, 1, NORTH))
                .thenIdle(DockWork.EVENT_INTERVAL + 1)
                .thenExecute(() -> {
                    helper.assertTrue(dock.workEvents() == 1, "eventos antes do pendente: " + dock.workEvents());
                    AbstractFurnaceBlockEntity entity = helper.getBlockEntity(furnace);
                    entity.setItem(0, ItemStack.EMPTY);
                    helper.assertTrue(DockDelivery.flushPending(dock, NORTH), "o pendente não saiu");
                })
                .thenWaitUntil(() -> assertEvents(helper, dock, 2, NORTH))
                .thenSucceed();
    }

    /** A volta do resultado manda o evento do lado dela ({@code DockReturn}): o lingote volta ao baú do lado Leste. */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void returnFiresWorkEvent(GameTestHelper helper) {
        BlockPos furnace = new BlockPos(3, 1, 3);
        PatternDockBlockEntity dock = dockWithFurnaces(helper, furnace);
        helper.setBlock(DOCK.east(), Blocks.CHEST.defaultBlockState());
        helper.startSequence()
                .thenWaitUntil(() -> {
                    if (!NetworkManager.get().hasVisitor(dock.returns())) {
                        throw new GameTestAssertException("a volta da Doca não está no motor");
                    }
                })
                .thenExecute(() -> {
                    helper.assertTrue(dock.link(EAST, helper.absolutePos(furnace), FURNACE, Direction.NORTH.ordinal()),
                            "não vinculou");
                    dock.side(EAST).profile().face(ResourceType.ITEM, RemoteSide.BOTTOM).setMode(PortMode.EXTRACT);
                    AbstractFurnaceBlockEntity entity = helper.getBlockEntity(furnace);
                    entity.setItem(2, new ItemStack(Items.IRON_INGOT, 3));
                    entity.setChanged();
                })
                .thenWaitUntil(() -> assertEvents(helper, dock, 1, EAST))
                .thenSucceed();
    }

    private static void assertEvents(GameTestHelper helper, PatternDockBlockEntity dock, int events, int side) {
        helper.assertTrue(dock.workEvents() == events, "eventos: " + dock.workEvents());
        helper.assertTrue(dock.lastWorkSide() == side, "lado do evento: " + dock.lastWorkSide());
    }

    /** Duas entregas dentro do intervalo: um evento só. */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void twoDeliveriesOneEvent(GameTestHelper helper) {
        BlockPos first = new BlockPos(3, 1, 1);
        BlockPos second = new BlockPos(3, 1, 3);
        PatternDockBlockEntity dock = dockWithFurnaces(helper, first, second);
        helper.startSequence()
                .thenIdle(1)
                .thenExecute(() -> link(helper, dock, first, second))
                .thenWaitUntil(DockWorkGameTests::room)
                .thenExecute(() -> deliver(helper, dock))
                .thenIdle(2)
                .thenWaitUntil(DockWorkGameTests::room)
                .thenExecute(() -> deliver(helper, dock))
                .thenIdle(5)
                .thenExecute(() -> helper.assertTrue(dock.workEvents() == 1, "eventos: " + dock.workEvents()))
                .thenSucceed();
    }
}
