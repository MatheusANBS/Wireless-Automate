package io.github.matheusanbs.wirelessautomate.gametest;

import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.block.RouterBlock;
import io.github.matheusanbs.wirelessautomate.block.RouterTier;
import io.github.matheusanbs.wirelessautomate.dock.DockDelivery;
import io.github.matheusanbs.wirelessautomate.dock.DockPendingLot;
import io.github.matheusanbs.wirelessautomate.dock.DockStatus;
import io.github.matheusanbs.wirelessautomate.dock.PatternDockBlockEntity;
import io.github.matheusanbs.wirelessautomate.network.NetworkManager;
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
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Pedidos com item e fluido juntos ({@link DockDelivery} chamada direto, sem o AE2), numa máquina de teste com uma
 * face Insere de item e outra de fluido ({@link TestDualMachine}): cada recurso vai para a face do tipo dele; o fluido
 * que a máquina recusa na execução vai para o pendente, o lado segura e o pendente sai quando ela volta a aceitar.
 *
 * <p>Cada oferta espera folga no orçamento num passo antes, como em {@link DockDeliveryGameTests}.
 */
@GameTestHolder(WirelessAutomate.MODID)
@PrefixGameTestTemplate(false)
public final class DockMixedGameTests {
    private static final BlockPos DOCK = new BlockPos(1, 1, 1);
    private static final BlockPos MACHINE = new BlockPos(3, 1, 1);
    private static final int NORTH = Direction.NORTH.ordinal();
    private static final ResourceLocation DUAL = BuiltInRegistries.BLOCK.getKey(TestDualMachine.BLOCK);

    private DockMixedGameTests() {
    }

    /** A Doca e a máquina dupla, ainda sem vínculo. */
    private static PatternDockBlockEntity setUp(GameTestHelper helper) {
        helper.setBlock(DOCK, ModBlocks.PATTERN_DOCK.get().defaultBlockState().setValue(RouterBlock.TIER,
                RouterTier.BASIC));
        TestDualMachine.reset(helper.absolutePos(MACHINE));
        helper.setBlock(MACHINE, TestDualMachine.BLOCK);
        return helper.getBlockEntity(DOCK);
    }

    /** A máquina dupla vinculada ao lado Norte, virada para o norte, com o {@link TestDualMachine#profile}. */
    private static void link(GameTestHelper helper, PatternDockBlockEntity dock) {
        helper.assertTrue(dock.link(NORTH, helper.absolutePos(MACHINE), DUAL, Direction.NORTH.ordinal()),
                "não vinculou a máquina");
        TestDualMachine.profile(dock.side(NORTH));
    }

    private static void room() {
        if (!NetworkManager.get().budget().outsideRoom()) {
            throw new GameTestAssertException("sem orçamento neste tick");
        }
    }

    private static TestDualMachine.Dual machine(GameTestHelper helper) {
        return TestDualMachine.at(helper.absolutePos(MACHINE));
    }

    /** "1 balde de água + 1 item": o minério no slot de cima, a água no tanque do lado, nada pendente. */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void itemAndFluidEachToItsFace(GameTestHelper helper) {
        if (!TestMachines.enabled()) {
            helper.succeed();
            return;
        }
        PatternDockBlockEntity dock = setUp(helper);
        helper.startSequence()
                .thenIdle(1)
                .thenExecute(() -> link(helper, dock))
                .thenWaitUntil(DockMixedGameTests::room)
                .thenExecute(() -> {
                    List<ItemStack> items = List.of(new ItemStack(Items.IRON_ORE));
                    List<FluidStack> fluids = List.of(new FluidStack(Fluids.WATER, 1_000));
                    helper.assertTrue(DockDelivery.offer(dock, NORTH, items, fluids, false)
                            == DockDelivery.Result.ACCEPTED, "recusou: motivo " + dock.side(NORTH).lastRefusal());
                    TestDualMachine.Dual machine = machine(helper);
                    ItemStack slot = machine.items.getStackInSlot(0);
                    helper.assertTrue(slot.is(Items.IRON_ORE) && slot.getCount() == 1, "slot: " + slot);
                    FluidStack tank = machine.tank.getFluid();
                    helper.assertTrue(tank.is(Fluids.WATER) && tank.getAmount() == 1_000, "tanque: " + tank);
                    helper.assertFalse(dock.side(NORTH).hasPending(), "sobrou pendente");
                    helper.assertTrue(items.get(0).getCount() == 1 && fluids.get(0).getAmount() == 1_000,
                            "mudou os stacks do pedido");
                })
                .thenSucceed();
    }

    /**
     * A máquina aceita o item e recusa o fluido na execução (o tanque que mente na simulação): o pedido conta como
     * aceito, o fluido vai ao pendente e o lado recusa pedidos novos; com o tanque aceitando de novo, o pendente sai.
     */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void fluidRefusedOnExecuteGoesPending(GameTestHelper helper) {
        if (!TestMachines.enabled()) {
            helper.succeed();
            return;
        }
        PatternDockBlockEntity dock = setUp(helper);
        helper.startSequence()
                .thenIdle(1)
                .thenExecute(() -> {
                    link(helper, dock);
                    machine(helper).lying = true;
                })
                .thenWaitUntil(DockMixedGameTests::room)
                .thenExecute(() -> {
                    helper.assertTrue(DockDelivery.offer(dock, NORTH, List.of(new ItemStack(Items.IRON_ORE)),
                            List.of(new FluidStack(Fluids.WATER, 1_000)), false) == DockDelivery.Result.ACCEPTED,
                            "recusou: motivo " + dock.side(NORTH).lastRefusal());
                    TestDualMachine.Dual machine = machine(helper);
                    helper.assertTrue(machine.items.getStackInSlot(0).is(Items.IRON_ORE), "o item não entrou");
                    helper.assertTrue(machine.tank.isEmpty(), "o tanque encheu");
                    DockPendingLot lot = dock.machineAt(helper.absolutePos(MACHINE)).pending();
                    helper.assertTrue(lot != null && lot.items().isEmpty() && lot.fluids().size() == 1
                            && lot.amount(Fluids.WATER) == 1_000, "pendente: " + (lot == null ? null : lot.fluids()));
                    helper.assertTrue(dock.side(NORTH).loose() == null, "pendente solto");
                    helper.assertFalse(DockDelivery.acceptsOrders(dock, NORTH), "o lado com pendente aceita pedidos");
                })
                .thenWaitUntil(DockMixedGameTests::room)
                .thenExecute(() -> {
                    helper.assertTrue(DockDelivery.offer(dock, NORTH, List.of(new ItemStack(Items.IRON_ORE)),
                            List.of(), false) == DockDelivery.Result.REFUSED, "aceitou com o pendente");
                    helper.assertTrue(dock.side(NORTH).lastRefusal() == DockStatus.PENDING,
                            "motivo " + dock.side(NORTH).lastRefusal());
                    machine(helper).lying = false;
                    helper.assertTrue(DockDelivery.flushPending(dock, NORTH), "o pendente não saiu");
                    helper.assertTrue(machine(helper).tank.getFluidAmount() == 1_000, "a água não chegou");
                    helper.assertTrue(DockDelivery.acceptsOrders(dock, NORTH), "o lado não voltou a aceitar");
                })
                .thenSucceed();
    }
}
