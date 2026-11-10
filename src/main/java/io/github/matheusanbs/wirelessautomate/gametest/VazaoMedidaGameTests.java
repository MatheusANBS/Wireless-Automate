package io.github.matheusanbs.wirelessautomate.gametest;

import io.github.matheusanbs.wirelessautomate.Config;
import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.block.RouterBlock;
import io.github.matheusanbs.wirelessautomate.block.RouterBlockEntity;
import io.github.matheusanbs.wirelessautomate.block.RouterTier;
import io.github.matheusanbs.wirelessautomate.network.NetworkManager;
import io.github.matheusanbs.wirelessautomate.network.NetworkSavedData;
import io.github.matheusanbs.wirelessautomate.network.PortMode;
import io.github.matheusanbs.wirelessautomate.network.ResourceType;
import io.github.matheusanbs.wirelessautomate.registry.ModBlocks;
import io.github.matheusanbs.wirelessautomate.storage.StorageChestBlockEntity;
import io.github.matheusanbs.wirelessautomate.storage.StorageKind;
import io.github.matheusanbs.wirelessautomate.storage.StorageTankBlockEntity;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Medições de vazão por tier (não afirmam nada, só registram no log as linhas "MEDIDA vazao"): quanto uma face
 * move de fato, por segundo, contra a vazão configurada do tier, com origem e destino sem gargalo. Quatro grupos:
 * itens de uma máquina de um slot que entrega uma pilha por extração (gaveta, barril com upgrade) para um ralo;
 * itens de um Baú do mod para outro (um tipo inteiro por chamada); fluido de um tanque comum para um tanque de
 * entrada; fluido de um Tanque do mod para outro. Um lote por caso, para o orçamento do tick não ser dividido com
 * outro teste. Só com as máquinas de teste ligadas.
 */
@GameTestHolder(WirelessAutomate.MODID)
@PrefixGameTestTemplate(false)
public final class VazaoMedidaGameTests {
    private static final BlockPos A = new BlockPos(0, 1, 0);
    private static final BlockPos B = new BlockPos(2, 1, 2);
    private static final int ITEM_TICKS = 60;
    private static final int FLUID_TICKS = 40;
    private static final long PLENTY = 2_000_000_000L;

    private VazaoMedidaGameTests() {
    }

    @GameTestGenerator
    public static Collection<TestFunction> vazaoPorTier() {
        List<TestFunction> tests = new ArrayList<>();
        for (RouterTier tier : RouterTier.values()) {
            tests.add(test("itens_gaveta", tier, helper -> drawerToSink(helper, tier)));
            tests.add(test("itens_bau", tier, helper -> chestToChest(helper, tier)));
            tests.add(test("fluido_tanque", tier, helper -> tanksToTyped(helper, tier)));
            tests.add(test("fluido_tanque_mod", tier, helper -> modTankToModTank(helper, tier)));
        }
        return tests;
    }

    private static TestFunction test(String group, RouterTier tier, Consumer<GameTestHelper> body) {
        String name = "vazao_" + group + "_" + tier.getSerializedName();
        return new TestFunction("wirelessautomate_" + name, name, WirelessAutomate.MODID + ":empty", 400, 0, true,
                helper -> {
                    if (!TestMachines.enabled()) {
                        helper.succeed();
                        return;
                    }
                    body.accept(helper);
                });
    }

    private static UUID network(GameTestHelper helper, String name) {
        return NetworkSavedData.get(helper.getLevel().getServer()).create(UUID.randomUUID(), name).id();
    }

    private static RouterBlockEntity router(GameTestHelper helper, BlockPos machine, RouterTier tier, ResourceType type,
            UUID network, PortMode mode) {
        BlockPos routerPos = machine.above();
        helper.setBlock(routerPos, ModBlocks.ROUTER.get().defaultBlockState()
                .setValue(RouterBlock.FACING, Direction.UP).setValue(RouterBlock.TIER, tier));
        RouterBlockEntity router = helper.getBlockEntity(routerPos);
        router.setNetworkId(type, network);
        router.setMode(type, Direction.UP, mode);
        return router;
    }

    private static long expected(RouterTier tier, ResourceType type) {
        return Config.TIERS.get(tier).rate(type);
    }

    /**
     * Mede {@code ticks} ticks depois dos roteadores registrados e de 10 ticks de aquecimento: {@code delivered} dá o
     * total entregue (só cresce), {@code refill} roda a cada tick para a origem não esvaziar e o destino não encher.
     */
    private static void measure(GameTestHelper helper, String group, RouterTier tier, ResourceType type, int ticks,
            RouterBlockEntity source, RouterBlockEntity target, LongSupplier delivered, Runnable refill) {
        long expected = expected(tier, type);
        long[] start = new long[1];
        long[] modNanos = new long[1];
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(NetworkManager.get().contains(source)
                        && NetworkManager.get().contains(target), "roteadores não registrados"))
                .thenExecuteFor(10, refill)
                .thenExecute(() -> start[0] = delivered.getAsLong())
                .thenExecuteFor(ticks, () -> {
                    refill.run();
                    modNanos[0] += NetworkManager.get().budget().lastUsedNanos();
                })
                .thenExecute(() -> {
                    long moved = delivered.getAsLong() - start[0];
                    double perSecond = moved * 20.0 / ticks;
                    String pct = expected == 0 ? "sem limite" : String.format("%.1f%%", perSecond * 100.0 / expected);
                    WirelessAutomate.LOGGER.info("MEDIDA vazao|{}|{}|esperado/s={}|medido/s={}|{}|mod_ms/tick={}",
                            group, tier.getSerializedName(), expected, Math.round(perSecond), pct,
                            String.format("%.3f", modNanos[0] / 1e6 / ticks));
                })
                .thenSucceed();
    }

    private static void drawerToSink(GameTestHelper helper, RouterTier tier) {
        UUID network = network(helper, "vazao-gaveta-" + tier.getSerializedName());
        BlockPos sourcePos = helper.absolutePos(A);
        BlockPos sinkPos = helper.absolutePos(B);
        TestMachines.reset(sourcePos);
        TestMachines.reset(sinkPos);
        TestMachines.BigSlot drawer = TestMachines.bigSlot(sourcePos);
        drawer.set(Items.COBBLESTONE, Integer.MAX_VALUE);
        TestMachines.VoidSink sink = TestMachines.voidSink(sinkPos);
        helper.setBlock(A, TestMachines.BIG_SLOT);
        helper.setBlock(B, TestMachines.VOID_SINK);
        RouterBlockEntity source = router(helper, A, tier, ResourceType.ITEM, network, PortMode.EXTRACT);
        RouterBlockEntity target = router(helper, B, tier, ResourceType.ITEM, network, PortMode.INSERT);
        measure(helper, "itens_gaveta", tier, ResourceType.ITEM, ITEM_TICKS, source, target, sink::total,
                () -> drawer.set(Items.COBBLESTONE, Integer.MAX_VALUE));
    }

    private static void chestToChest(GameTestHelper helper, RouterTier tier) {
        UUID network = network(helper, "vazao-bau-" + tier.getSerializedName());
        Block chest = ModBlocks.STORAGE.get(StorageKind.CHEST).get();
        helper.setBlock(A, chest.defaultBlockState().setValue(RouterBlock.TIER, RouterTier.ULTIMATE));
        helper.setBlock(B, chest.defaultBlockState().setValue(RouterBlock.TIER, RouterTier.ULTIMATE));
        StorageChestBlockEntity from = helper.getBlockEntity(A);
        StorageChestBlockEntity to = helper.getBlockEntity(B);
        ItemStack cobble = new ItemStack(Items.COBBLESTONE);
        from.storage().insert(cobble, PLENTY, false);
        RouterBlockEntity source = router(helper, A, tier, ResourceType.ITEM, network, PortMode.EXTRACT);
        RouterBlockEntity target = router(helper, B, tier, ResourceType.ITEM, network, PortMode.INSERT);
        measure(helper, "itens_bau", tier, ResourceType.ITEM, ITEM_TICKS, source, target, () -> to.storage().total(),
                () -> {
                    long have = from.storage().total();
                    if (have < PLENTY) {
                        from.storage().insert(cobble, PLENTY - have, false);
                    }
                });
    }

    private static void tanksToTyped(GameTestHelper helper, RouterTier tier) {
        UUID network = network(helper, "vazao-fluido-" + tier.getSerializedName());
        BlockPos sourcePos = helper.absolutePos(A);
        BlockPos sinkPos = helper.absolutePos(B);
        TestMachines.reset(sourcePos);
        TestMachines.reset(sinkPos);
        TestMachines.ManyTanks tanks = TestMachines.manyTanks(sourcePos);
        FluidStack water = new FluidStack(Fluids.WATER, Integer.MAX_VALUE);
        tanks.set(0, water);
        TestMachines.TypedTank sink = TestMachines.typedTank(sinkPos);
        sink.accept(Fluids.WATER);
        helper.setBlock(A, TestMachines.MANY_TANKS);
        helper.setBlock(B, TestMachines.TYPED_TANK);
        RouterBlockEntity source = router(helper, A, tier, ResourceType.FLUID, network, PortMode.EXTRACT);
        RouterBlockEntity target = router(helper, B, tier, ResourceType.FLUID, network, PortMode.INSERT);
        // O ralo é esvaziado a cada tick: o entregue é a soma do que ele tinha antes de cada esvaziamento.
        long[] total = new long[1];
        measure(helper, "fluido_tanque", tier, ResourceType.FLUID, FLUID_TICKS, source, target, () -> total[0],
                () -> {
                    total[0] += sink.amount();
                    sink.clear();
                    tanks.set(0, water);
                });
    }

    private static void modTankToModTank(GameTestHelper helper, RouterTier tier) {
        UUID network = network(helper, "vazao-tanque-mod-" + tier.getSerializedName());
        Block tank = ModBlocks.STORAGE.get(StorageKind.TANK).get();
        helper.setBlock(A, tank.defaultBlockState().setValue(RouterBlock.TIER, RouterTier.ULTIMATE));
        helper.setBlock(B, tank.defaultBlockState().setValue(RouterBlock.TIER, RouterTier.ULTIMATE));
        StorageTankBlockEntity from = helper.getBlockEntity(A);
        StorageTankBlockEntity to = helper.getBlockEntity(B);
        FluidStack water = new FluidStack(Fluids.WATER, 1);
        from.storage().insert(water, PLENTY, false);
        RouterBlockEntity source = router(helper, A, tier, ResourceType.FLUID, network, PortMode.EXTRACT);
        RouterBlockEntity target = router(helper, B, tier, ResourceType.FLUID, network, PortMode.INSERT);
        measure(helper, "fluido_tanque_mod", tier, ResourceType.FLUID, FLUID_TICKS, source, target,
                () -> to.storage().total(), () -> {
                    long have = from.storage().total();
                    if (have < PLENTY) {
                        from.storage().insert(water, PLENTY - have, false);
                    }
                });
    }
}
