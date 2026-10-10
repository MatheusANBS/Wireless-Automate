package io.github.matheusanbs.wirelessautomate.gametest;

import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.block.RouterBlock;
import io.github.matheusanbs.wirelessautomate.block.RouterBlockEntity;
import io.github.matheusanbs.wirelessautomate.block.RouterTier;
import io.github.matheusanbs.wirelessautomate.network.NetworkManager;
import io.github.matheusanbs.wirelessautomate.network.NetworkSavedData;
import io.github.matheusanbs.wirelessautomate.network.NetworkStats;
import io.github.matheusanbs.wirelessautomate.network.PortMode;
import io.github.matheusanbs.wirelessautomate.network.RedstoneMode;
import io.github.matheusanbs.wirelessautomate.network.ResourceType;
import io.github.matheusanbs.wirelessautomate.registry.ModBlocks;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import org.jetbrains.annotations.Nullable;

/**
 * Laço de transferência do {@link NetworkManager}: itens, fluidos, prioridade, redstone, redes
 * separadas e destinos dormindo. Cada teste cria redes próprias, porque os testes do lote rodam em
 * paralelo e dividem o gerenciador. Máquinas em y=1 com o roteador em cima (facing=UP), então a
 * face configurada é {@link Direction#UP}.
 *
 * <p>Energia não tem GameTest: o vanilla não tem bloco com {@code IEnergyStorage}. A divisão num
 * passe está coberta por {@code EnergySplitTest} (JUnit).
 */
@GameTestHolder(WirelessAutomate.MODID)
@PrefixGameTestTemplate(false)
public final class TransferGameTests {
    private static final BlockPos A = new BlockPos(0, 1, 0);
    private static final BlockPos B = new BlockPos(2, 1, 2);
    private static final BlockPos C = new BlockPos(2, 1, 0);
    private static final BlockPos D = new BlockPos(0, 1, 2);

    private static UUID newNetwork(GameTestHelper helper, String name) {
        return NetworkSavedData.get(helper.getLevel().getServer()).create(UUID.randomUUID(), name).id();
    }

    /** Põe a máquina em {@code machine} e um roteador em cima dela, na rede {@code network}. */
    private static RouterBlockEntity place(GameTestHelper helper, BlockPos machine, BlockState machineState,
            @Nullable UUID network) {
        helper.setBlock(machine, machineState);
        BlockPos routerPos = machine.above();
        helper.setBlock(routerPos, ModBlocks.ROUTER.get().defaultBlockState().setValue(RouterBlock.FACING, Direction.UP));
        RouterBlockEntity router = GameTestCompat.getBlockEntity(helper, routerPos);
        router.setNetworkId(network);
        return router;
    }

    private static RouterBlockEntity chest(GameTestHelper helper, BlockPos pos, @Nullable UUID network, PortMode mode) {
        RouterBlockEntity router = place(helper, pos, Blocks.CHEST.defaultBlockState(), network);
        router.setMode(ResourceType.ITEM, Direction.UP, mode);
        return router;
    }

    private static ChestBlockEntity chestAt(GameTestHelper helper, BlockPos pos) {
        return GameTestCompat.getBlockEntity(helper, pos);
    }

    private static int count(GameTestHelper helper, BlockPos pos, Item item) {
        ChestBlockEntity chest = chestAt(helper, pos);
        int total = 0;
        for (int i = 0; i < chest.getContainerSize(); i++) {
            ItemStack stack = chest.getItem(i);
            if (stack.is(item)) {
                total += stack.getCount();
            }
        }
        return total;
    }

    private static void assertCount(GameTestHelper helper, BlockPos pos, Item item, int expected) {
        GameTestCompat.assertValueEqual(helper, count(helper, pos, item), expected, item + " em " + pos.toShortString());
    }

    private static void waitRegistered(GameTestHelper helper, RouterBlockEntity... routers) {
        for (RouterBlockEntity router : routers) {
            helper.assertTrue(NetworkManager.get().contains(router), "roteador não registrado");
        }
    }

    @GameTest(template = "empty")
    public static void itemsMoveAndAreConserved(GameTestHelper helper) {
        UUID network = newNetwork(helper, "teste-itens");
        chest(helper, A, network, PortMode.EXTRACT);
        chest(helper, B, network, PortMode.INSERT);
        chestAt(helper, A).setItem(0, new ItemStack(Items.DIAMOND, 10));
        chestAt(helper, A).setItem(5, new ItemStack(Items.COBBLESTONE, 5));

        helper.onEachTick(() -> {
            GameTestCompat.assertValueEqual(helper, count(helper, A, Items.DIAMOND) + count(helper, B, Items.DIAMOND), 10, "diamantes");
            GameTestCompat.assertValueEqual(helper, count(helper, A, Items.COBBLESTONE) + count(helper, B, Items.COBBLESTONE), 5, "pedregulho");
        });
        helper.succeedWhen(() -> {
            assertCount(helper, B, Items.DIAMOND, 10);
            assertCount(helper, B, Items.COBBLESTONE, 5);
            helper.assertTrue(chestAt(helper, A).isEmpty(), "origem não esvaziou");
        });
    }

    @GameTest(template = "empty")
    public static void differentNetworksDoNotTrade(GameTestHelper helper) {
        RouterBlockEntity source = chest(helper, A, newNetwork(helper, "teste-rede-1"), PortMode.EXTRACT);
        RouterBlockEntity target = chest(helper, B, newNetwork(helper, "teste-rede-2"), PortMode.INSERT);
        chestAt(helper, A).setItem(0, new ItemStack(Items.DIAMOND, 10));

        helper.startSequence()
                .thenWaitUntil(() -> waitRegistered(helper, source, target))
                .thenIdle(20)
                .thenExecute(() -> {
                    assertCount(helper, A, Items.DIAMOND, 10);
                    assertCount(helper, B, Items.DIAMOND, 0);
                })
                .thenSucceed();
    }

    @GameTest(template = "empty")
    public static void routersWithoutNetworkDoNotTrade(GameTestHelper helper) {
        // Id que não existe no NetworkSavedData conta como "sem rede", assim como null.
        UUID ghost = UUID.randomUUID();
        RouterBlockEntity a = chest(helper, A, ghost, PortMode.EXTRACT);
        RouterBlockEntity c = chest(helper, C, ghost, PortMode.INSERT);
        RouterBlockEntity d = chest(helper, D, null, PortMode.EXTRACT);
        RouterBlockEntity b = chest(helper, B, null, PortMode.INSERT);
        chestAt(helper, A).setItem(0, new ItemStack(Items.DIAMOND, 10));
        chestAt(helper, D).setItem(0, new ItemStack(Items.EMERALD, 10));

        helper.startSequence()
                .thenWaitUntil(() -> waitRegistered(helper, a, b, c, d))
                .thenIdle(20)
                .thenExecute(() -> {
                    assertCount(helper, A, Items.DIAMOND, 10);
                    assertCount(helper, C, Items.DIAMOND, 0);
                    assertCount(helper, D, Items.EMERALD, 10);
                    assertCount(helper, B, Items.EMERALD, 0);
                })
                .thenSucceed();
    }

    @GameTest(template = "empty")
    public static void higherPriorityReceivesFirst(GameTestHelper helper) {
        UUID network = newNetwork(helper, "teste-prioridade");
        chest(helper, A, network, PortMode.EXTRACT);
        RouterBlockEntity high = chest(helper, C, network, PortMode.INSERT);
        high.setPriority(ResourceType.ITEM, Direction.UP, 5);
        chest(helper, B, network, PortMode.INSERT);
        // O destino prioritário só tem um slot livre: 64 diamantes cabem nele, o resto vai para o outro.
        ChestBlockEntity highChest = chestAt(helper, C);
        for (int i = 1; i < highChest.getContainerSize(); i++) {
            highChest.setItem(i, new ItemStack(Items.STONE, 64));
        }
        chestAt(helper, A).setItem(0, new ItemStack(Items.DIAMOND, 64));
        chestAt(helper, A).setItem(1, new ItemStack(Items.DIAMOND, 6));

        helper.succeedWhen(() -> {
            assertCount(helper, A, Items.DIAMOND, 0);
            assertCount(helper, C, Items.DIAMOND, 64);
            assertCount(helper, B, Items.DIAMOND, 6);
        });
    }

    @GameTest(template = "empty")
    public static void itemsStackOntoExistingPilesFirst(GameTestHelper helper) {
        UUID network = newNetwork(helper, "teste-empilhar");
        chest(helper, A, network, PortMode.EXTRACT);
        chest(helper, B, network, PortMode.INSERT);
        // Como o insertItemStacked: completa as pilhas iguais (slots 3 e 7), depois o primeiro vazio (slot 1).
        ChestBlockEntity target = chestAt(helper, B);
        target.setItem(0, new ItemStack(Items.DIRT, 64));
        target.setItem(3, new ItemStack(Items.COBBLESTONE, 60));
        target.setItem(7, new ItemStack(Items.COBBLESTONE, 62));
        chestAt(helper, A).setItem(0, new ItemStack(Items.COBBLESTONE, 30));

        helper.onEachTick(() -> GameTestCompat.assertValueEqual(helper, 
                count(helper, A, Items.COBBLESTONE) + count(helper, B, Items.COBBLESTONE), 152, "pedregulho"));
        helper.succeedWhen(() -> {
            assertCount(helper, A, Items.COBBLESTONE, 0);
            GameTestCompat.assertValueEqual(helper, target.getItem(3).getCount(), 64, "slot 3");
            GameTestCompat.assertValueEqual(helper, target.getItem(7).getCount(), 64, "slot 7");
            helper.assertTrue(target.getItem(1).is(Items.COBBLESTONE), "slot 1 sem pedregulho");
            GameTestCompat.assertValueEqual(helper, target.getItem(1).getCount(), 24, "slot 1");
            helper.assertTrue(target.getItem(2).isEmpty(), "slot 2 deveria estar vazio");
            GameTestCompat.assertValueEqual(helper, target.getItem(0).getCount(), 64, "terra");
        });
    }

    @GameTest(template = "empty")
    public static void reconfiguringSomeNodesKeepsTheOthersRouted(GameTestHelper helper) {
        UUID network = newNetwork(helper, "teste-remontagem");
        RouterBlockEntity a = chest(helper, A, network, PortMode.EXTRACT);
        RouterBlockEntity b = chest(helper, B, network, PortMode.INSERT);
        RouterBlockEntity c = chest(helper, C, network, PortMode.EXTRACT);
        chestAt(helper, C).setItem(0, new ItemStack(Items.EMERALD, 5));

        // A montagem relê só os nós que mudaram (B e C); A entra com as portas já lidas e continua.
        helper.startSequence()
                .thenWaitUntil(() -> waitRegistered(helper, a, b, c))
                .thenWaitUntil(() -> assertCount(helper, B, Items.EMERALD, 5))
                .thenExecute(() -> {
                    c.setMode(ResourceType.ITEM, Direction.UP, PortMode.NONE);
                    b.setPriority(ResourceType.ITEM, Direction.UP, 3);
                    chestAt(helper, A).setItem(0, new ItemStack(Items.DIAMOND, 10));
                    chestAt(helper, C).setItem(0, new ItemStack(Items.EMERALD, 7));
                })
                .thenWaitUntil(() -> assertCount(helper, B, Items.DIAMOND, 10))
                .thenIdle(10)
                .thenExecute(() -> {
                    assertCount(helper, C, Items.EMERALD, 7);
                    assertCount(helper, B, Items.EMERALD, 5);
                })
                .thenSucceed();
    }

    @GameTest(template = "empty")
    public static void redstoneHighWaitsForSignal(GameTestHelper helper) {
        UUID network = newNetwork(helper, "teste-redstone");
        RouterBlockEntity source = chest(helper, A, network, PortMode.EXTRACT);
        source.setRedstone(ResourceType.ITEM, Direction.UP, RedstoneMode.HIGH);
        RouterBlockEntity target = chest(helper, B, network, PortMode.INSERT);
        chestAt(helper, A).setItem(0, new ItemStack(Items.DIAMOND, 10));

        helper.startSequence()
                .thenWaitUntil(() -> waitRegistered(helper, source, target))
                .thenIdle(20)
                .thenExecute(() -> {
                    assertCount(helper, A, Items.DIAMOND, 10);
                    helper.setBlock(A.above().east(), Blocks.REDSTONE_BLOCK);
                })
                .thenWaitUntil(() -> assertCount(helper, B, Items.DIAMOND, 10))
                .thenSucceed();
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void fullDestinationSleepsAndWakes(GameTestHelper helper) {
        UUID network = newNetwork(helper, "teste-dormindo");
        RouterBlockEntity source = chest(helper, A, network, PortMode.EXTRACT);
        RouterBlockEntity target = chest(helper, B, network, PortMode.INSERT);
        ChestBlockEntity full = chestAt(helper, B);
        for (int i = 0; i < full.getContainerSize(); i++) {
            full.setItem(i, new ItemStack(Items.STONE, 64));
        }
        chestAt(helper, A).setItem(0, new ItemStack(Items.DIAMOND, 10));

        helper.startSequence()
                .thenWaitUntil(() -> waitRegistered(helper, source, target))
                // O intervalo dobra a cada recusa: depois de 40 ticks o sono já passa de 3 ticks, então
                // a entrega rápida lá embaixo só acontece se o setChanged do baú acordar o destino.
                .thenIdle(40)
                .thenWaitUntil(() -> {
                    NetworkStats stats = stats(helper, network);
                    helper.assertTrue(stats != null && stats.destinationsSleeping() == 1,
                            "destino cheio não dormiu: " + stats);
                })
                .thenExecute(() -> {
                    assertCount(helper, A, Items.DIAMOND, 10);
                    // Abrir espaço chama setChanged no baú, que acorda o destino na hora (sem esperar o backoff).
                    full.setItem(0, ItemStack.EMPTY);
                })
                .thenExecuteAfter(3, () -> {
                    assertCount(helper, B, Items.DIAMOND, 10);
                    assertCount(helper, A, Items.DIAMOND, 0);
                })
                .thenSucceed();
    }

    /**
     * Tanque de teste em {@code pos} ({@link TestMachines#SIMPLE_TANK}, com {@code water} mB de água), com um
     * roteador em cima. Porte 1.20.1: no {@code main} os testes de fluido usavam caldeirões, que o NeoForge expõe
     * como handler de fluido e o Forge 1.20.1 não.
     */
    private static RouterBlockEntity simpleTank(GameTestHelper helper, BlockPos pos, int water, @Nullable UUID network) {
        BlockPos machine = helper.absolutePos(pos);
        TestMachines.reset(machine);
        if (water > 0) {
            TestMachines.simpleTank(machine).fill(new FluidStack(Fluids.WATER, water), IFluidHandler.FluidAction.EXECUTE);
        }
        return place(helper, pos, TestMachines.SIMPLE_TANK.get().defaultBlockState(), network);
    }

    /** Água no tanque de teste em {@code pos}. */
    private static int water(GameTestHelper helper, BlockPos pos) {
        FluidStack stored = TestMachines.simpleTank(helper.absolutePos(pos)).getFluid();
        return stored.getFluid() == Fluids.WATER ? stored.getAmount() : 0;
    }

    @GameTest(template = "empty")
    public static void fluidMovesBetweenCauldrons(GameTestHelper helper) {
        if (!TestMachines.enabled()) {
            helper.succeed();
            return;
        }
        UUID network = newNetwork(helper, "teste-fluido");
        RouterBlockEntity source = simpleTank(helper, A, 1_000, network);
        source.setMode(ResourceType.FLUID, Direction.UP, PortMode.EXTRACT);
        RouterBlockEntity target = simpleTank(helper, B, 0, network);
        target.setMode(ResourceType.FLUID, Direction.UP, PortMode.INSERT);

        helper.succeedWhen(() -> {
            GameTestCompat.assertValueEqual(helper, water(helper, A), 0, "água na origem");
            GameTestCompat.assertValueEqual(helper, water(helper, B), 1_000, "água no destino");
        });
    }

    /**
     * Slot com pilha maior que uma extração (gaveta, barril com upgrade de pilha) no Elite: a visita
     * repete o slot enquanto houver saldo e itens, em vez de entregar 64 e passar para o próximo.
     * Com 64 por visita o baú levaria 27 ticks para encher.
     */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void bigStackSlotMovesMoreThanAStackPerVisit(GameTestHelper helper) {
        if (!TestMachines.enabled()) {
            helper.succeed();
            return;
        }
        UUID network = newNetwork(helper, "teste-pilha-grande");
        BlockPos machine = helper.absolutePos(A);
        TestMachines.reset(machine);
        TestMachines.bigSlot(machine).set(Items.COBBLESTONE, 10_000);
        RouterBlockEntity source = place(helper, A, TestMachines.BIG_SLOT.get().defaultBlockState(), network);
        helper.setBlock(A.above(), helper.getBlockState(A.above()).setValue(RouterBlock.TIER, RouterTier.ELITE));
        source.setMode(ResourceType.ITEM, Direction.UP, PortMode.EXTRACT);
        RouterBlockEntity target = chest(helper, B, network, PortMode.INSERT);
        int capacity = 27 * 64;
        long[] started = new long[1];

        helper.onEachTick(() -> GameTestCompat.assertValueEqual(helper, TestMachines.bigSlot(machine).count() + count(helper, B, Items.COBBLESTONE),
                10_000, "pedregulho"));
        helper.startSequence()
                .thenWaitUntil(() -> waitRegistered(helper, source, target))
                .thenExecute(() -> started[0] = helper.getTick())
                .thenWaitUntil(() -> assertCount(helper, B, Items.COBBLESTONE, capacity))
                .thenExecute(() -> {
                    long ticks = helper.getTick() - started[0];
                    helper.assertTrue(ticks <= 10, "baú levou " + ticks + " ticks para encher: o slot não repetiu");
                    GameTestCompat.assertValueEqual(helper, TestMachines.bigSlot(machine).count(), 10_000 - capacity, "na origem");
                })
                .thenSucceed();
    }

    /**
     * Baú vanilla de destino com pilhas cheias de pedregulho (64, abaixo do limite 99 do slot), uma
     * pilha pela metade depois delas e slots vazios: as cheias são puladas sem simular, mas a ordem
     * continua a do {@code insertItemStacked}, completando a pilha pela metade antes do vazio.
     */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void fullVanillaStacksKeepStackedOrder(GameTestHelper helper) {
        UUID network = newNetwork(helper, "teste-pilhas-cheias");
        RouterBlockEntity source = chest(helper, A, network, PortMode.EXTRACT);
        helper.setBlock(A.above(), helper.getBlockState(A.above()).setValue(RouterBlock.TIER, RouterTier.ELITE));
        RouterBlockEntity target = chest(helper, B, network, PortMode.INSERT);
        chestAt(helper, A).setItem(0, new ItemStack(Items.COBBLESTONE, 64));
        ChestBlockEntity destination = chestAt(helper, B);
        for (int slot = 0; slot < 3; slot++) {
            destination.setItem(slot, new ItemStack(Items.COBBLESTONE, 64));
        }
        destination.setItem(3, new ItemStack(Items.COBBLESTONE, 10));

        helper.startSequence()
                .thenWaitUntil(() -> waitRegistered(helper, source, target))
                .thenWaitUntil(() -> assertCount(helper, B, Items.COBBLESTONE, 4 * 64 + 10))
                .thenExecute(() -> {
                    for (int slot = 0; slot < 4; slot++) {
                        GameTestCompat.assertValueEqual(helper, destination.getItem(slot).getCount(), 64, "slot " + slot);
                    }
                    GameTestCompat.assertValueEqual(helper, destination.getItem(4).getCount(), 10, "slot 4");
                    helper.assertTrue(destination.getItem(5).isEmpty(), "slot 5 vazio");
                    assertCount(helper, A, Items.COBBLESTONE, 0);
                })
                .thenSucceed();
    }

    /** Fluido num tanque acima da janela de uma visita (16): o cursor de tanques chega nele. */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void fluidBeyondSixteenTanksMoves(GameTestHelper helper) {
        if (!TestMachines.enabled()) {
            helper.succeed();
            return;
        }
        UUID network = newNetwork(helper, "teste-muitos-tanques");
        BlockPos machine = helper.absolutePos(A);
        TestMachines.reset(machine);
        TestMachines.manyTanks(machine).set(18, new FluidStack(Fluids.WATER, 1_000));
        RouterBlockEntity source = place(helper, A, TestMachines.MANY_TANKS.get().defaultBlockState(), network);
        source.setMode(ResourceType.FLUID, Direction.UP, PortMode.EXTRACT);
        RouterBlockEntity target = simpleTank(helper, B, 0, network);
        target.setMode(ResourceType.FLUID, Direction.UP, PortMode.INSERT);

        helper.startSequence()
                .thenWaitUntil(() -> waitRegistered(helper, source, target))
                .thenWaitUntil(() -> {
                    GameTestCompat.assertValueEqual(helper, water(helper, B), 1_000, "água no destino");
                    GameTestCompat.assertValueEqual(helper, TestMachines.manyTanks(machine).amount(18), 0, "tanque 18");
                })
                .thenSucceed();
    }

    /**
     * Origem que devolve a pilha interna e a encolhe ao drenar (como o Rotary Condensentrator do
     * Mekanism): esvaziar o tanque não pode zerar o fluido que vai para o destino, senão ele some.
     */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void fluidFromLiveTankIsNotLost(GameTestHelper helper) {
        if (!TestMachines.enabled()) {
            helper.succeed();
            return;
        }
        UUID network = newNetwork(helper, "teste-tanque-vivo");
        BlockPos machine = helper.absolutePos(A);
        TestMachines.reset(machine);
        TestMachines.liveTank(machine).set(new FluidStack(Fluids.WATER, 1_000));
        RouterBlockEntity source = place(helper, A, TestMachines.LIVE_TANK.get().defaultBlockState(), network);
        source.setMode(ResourceType.FLUID, Direction.UP, PortMode.EXTRACT);
        RouterBlockEntity target = simpleTank(helper, B, 0, network);
        target.setMode(ResourceType.FLUID, Direction.UP, PortMode.INSERT);

        helper.startSequence()
                .thenWaitUntil(() -> waitRegistered(helper, source, target))
                .thenWaitUntil(() -> GameTestCompat.assertValueEqual(helper, TestMachines.liveTank(machine).amount(), 0, "tanque vivo"))
                .thenExecute(() -> GameTestCompat.assertValueEqual(helper, water(helper, B), 1_000, "água no destino"))
                .thenSucceed();
    }

    private static @Nullable NetworkStats stats(GameTestHelper helper, UUID network) {
        for (NetworkStats stats : NetworkManager.get().stats(helper.getLevel().getServer())) {
            if (stats.id().equals(network)) {
                return stats;
            }
        }
        return null;
    }
}
