package io.github.matheusanbs.wirelessautomate.gametest;

import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.block.RouterBlock;
import io.github.matheusanbs.wirelessautomate.block.RouterTier;
import io.github.matheusanbs.wirelessautomate.dock.DockDelivery;
import io.github.matheusanbs.wirelessautomate.dock.DockMachine;
import io.github.matheusanbs.wirelessautomate.dock.DockSide;
import io.github.matheusanbs.wirelessautomate.dock.PatternDockBlockEntity;
import io.github.matheusanbs.wirelessautomate.network.NetworkManager;
import io.github.matheusanbs.wirelessautomate.network.PortMode;
import io.github.matheusanbs.wirelessautomate.network.ResourceType;
import io.github.matheusanbs.wirelessautomate.range.RemoteMachines;
import io.github.matheusanbs.wirelessautomate.range.RemoteSide;
import io.github.matheusanbs.wirelessautomate.registry.ModBlocks;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.items.IItemHandler;

/**
 * A Doca no uso intenso, com números: cinco Ultimate Smelting Factories do Mekanism num lado da Doca (dentro do
 * template {@code empty11}), aceleradas como pela Time Wand ({@code speed} ticks da fábrica por tick do jogo, pelo
 * ticker do bloco), e pedidos de 256 minérios brutos no ritmo de uma CPU do AE2 ({@link #pushes} execuções a cada 3
 * ticks, como a janela da CPU, parando na primeira recusa e tentando de novo no tick seguinte com o que sobrou da
 * janela, com o {@code acceptsPlans} antes, que é o {@link DockDelivery#acceptsOrders}),
 * sem fim ou até um total ({@link #jobLeft}). O baú do provedor é esvaziado a cada tick. Escreve no log a linha
 * "MEDIDA doca-minmax" com a vazão devolvida, a ociosidade de cada fábrica (tick sem produzir nada), as recusas, o
 * atraso da saída (lei de Little: a média do que espera nas saídas sobre a vazão), o custo e o balanço dos lingotes.
 * O controle ({@link #idealTick}) mede o teto das mesmas fábricas sem a Doca.
 */
final class DockMinMaxMedida {
    static final BlockPos DOCK = new BlockPos(5, 1, 2);
    static final BlockPos CHEST = DOCK.north();
    static final int NORTH = Direction.NORTH.ordinal();
    static final ResourceLocation FACTORY_ID = ResourceLocation.fromNamespaceAndPath("mekanism",
            "ultimate_smelting_factory");
    static final int FACTORIES = 5;
    /** Processos (slots de entrada e de saída) da fábrica Ultimate. */
    static final int PROCESSES = 9;
    /** Ticks de uma operação da fábrica sem upgrade. */
    static final int BASE_TICKS = 200;
    /** Execuções por janela de 3 ticks, por padrão: uma CPU com 3 coprocessadores. */
    static final int PUSHES = 4;
    /** Execuções por janela de 3 ticks desta cena (coprocessadores + 1). */
    int pushes = PUSHES;
    /** As execuções de cada um dos últimos 3 ticks (o {@code usedOps} da CPU do AE2). */
    private final int[] usedOps = new int[3];

    final GameTestHelper helper;
    final PatternDockBlockEntity dock;
    final int speed;
    final List<BlockPos> factories = new ArrayList<>();
    final RouterTier tier;

    long ticks;
    long accepted;
    long acceptedWithPending;
    long blockedByPending;
    long refusedAllFull;
    long refusedBudget;
    long refusedOther;
    long offerNanos;
    long engineNanos;
    long engineMaxNanos;
    long returnedStart;
    long chestDrained;
    long starvedSlotTicks;
    long fullOutputSlotTicks;
    long heldOutput;
    long idleFactoryTicks;
    long asleepStarvedTicks;
    long pendingTicks;
    long asleepTicks;

    DockMinMaxMedida(GameTestHelper helper, RouterTier tier, int speed) {
        this.helper = helper;
        this.tier = tier;
        this.speed = speed;
        helper.setBlock(DOCK, ModBlocks.PATTERN_DOCK.get().defaultBlockState().setValue(RouterBlock.TIER, tier));
        helper.setBlock(CHEST, Blocks.CHEST.defaultBlockState());
        for (int i = 0; i < FACTORIES; i++) {
            BlockPos pos = new BlockPos(1 + 2 * i, 1, 5);
            helper.setBlock(pos, BuiltInRegistries.BLOCK.get(FACTORY_ID).defaultBlockState());
            factories.add(pos);
        }
        dock = helper.getBlockEntity(DOCK);
    }

    void registered() {
        if (!NetworkManager.get().hasVisitor(dock.returns())) {
            throw new GameTestAssertException("a volta da Doca não está no motor");
        }
    }

    /** Vincula as fábricas ao lado Norte: entrada de item em Cima (Insere), saída em Baixo (Extrai). */
    void link() {
        for (BlockPos rel : factories) {
            BlockPos pos = helper.absolutePos(rel);
            BlockState state = helper.getLevel().getBlockState(pos);
            ChemicalTestSupport.mekanismSides(helper.getLevel().getBlockEntity(pos));
            helper.assertTrue(dock.link(NORTH, pos, FACTORY_ID, RemoteMachines.frontOf(state)), "não vinculou");
        }
        DockSide side = dock.side(NORTH);
        side.profile().face(ResourceType.ITEM, RemoteSide.TOP).setMode(PortMode.INSERT);
        side.profile().face(ResourceType.ITEM, RemoteSide.BOTTOM).setMode(PortMode.EXTRACT);
    }

    long ideal;

    /**
     * O controle: as mesmas fábricas aceleradas, sem a Doca, com a entrada enchida e a saída esvaziada pelo próprio
     * teste a cada tick. Dá o teto real da fábrica neste ritmo.
     */
    void idealTick(boolean measuring) {
        ServerLevel level = helper.getLevel();
        for (BlockPos rel : factories) {
            BlockPos pos = helper.absolutePos(rel);
            accelerate(level, pos);
            IItemHandler in = level.getCapability(Capabilities.ItemHandler.BLOCK, pos, Direction.UP);
            IItemHandler out = level.getCapability(Capabilities.ItemHandler.BLOCK, pos, Direction.DOWN);
            for (int s = 0; in != null && s < in.getSlots(); s++) {
                in.insertItem(s, new ItemStack(Items.RAW_IRON, 64), false);
            }
            for (int s = 0; out != null && s < out.getSlots(); s++) {
                int got = out.extractItem(s, 64, false).getCount();
                if (measuring) {
                    ideal += got;
                }
            }
        }
        if (measuring) {
            ticks++;
        }
    }

    void reportIdeal(String name) {
        double rate = ideal / (ticks / 20.0);
        double ceiling = FACTORIES * PROCESSES * speed * 20.0 / BASE_TICKS;
        WirelessAutomate.LOGGER.info(String.format(java.util.Locale.ROOT,
                "MEDIDA doca-minmax %s controle speed=%d: %.1f itens/s sem a Doca (teto %.1f/s, %.0f%%)",
                name, speed, rate, ceiling, 100 * rate / ceiling));
    }

    /** Um tick do cenário: as fábricas aceleradas, os pedidos da CPU, o baú esvaziado e, medindo, as contas. */
    void tick(boolean measuring) {
        ServerLevel level = helper.getLevel();
        long now = level.getServer().getTickCount();
        for (int f = 0; f < FACTORIES; f++) {
            BlockPos pos = helper.absolutePos(factories.get(f));
            long outBefore = sum(level, pos, Direction.DOWN);
            accelerate(level, pos);
            if (measuring) {
                long produced = sum(level, pos, Direction.DOWN) - outBefore;
                made[f] += produced;
                if (produced <= 0) {
                    idle[f]++;
                }
            }
        }
        // A janela da CPU do AE2 (CraftingCpuLogic): as execuções dos últimos 3 ticks contam contra o limite.
        usedOps[2] = usedOps[1];
        usedOps[1] = usedOps[0];
        usedOps[0] = orders(pushes - usedOps[1] - usedOps[2], measuring);
        drainChest(measuring);
        if (!measuring) {
            return;
        }
        ticks++;
        long engine = NetworkManager.get().budget().lastUsedNanos();
        engineNanos += engine;
        engineMaxNanos = Math.max(engineMaxNanos, engine);
        DockSide side = dock.side(NORTH);
        if (side.hasPending()) {
            pendingTicks++;
        }
        for (int f = 0; f < FACTORIES; f++) {
            BlockPos pos = helper.absolutePos(factories.get(f));
            IItemHandler in = level.getCapability(Capabilities.ItemHandler.BLOCK, pos, Direction.UP);
            IItemHandler out = level.getCapability(Capabilities.ItemHandler.BLOCK, pos, Direction.DOWN);
            int starved = 0;
            for (int s = 0; in != null && s < in.getSlots(); s++) {
                if (in.getStackInSlot(s).isEmpty()) {
                    starved++;
                }
            }
            starvedSlotTicks += starved;
            DockMachine linked = dock.machineAt(pos);
            if (linked != null && linked.asleep(now)) {
                asleepTicks++;
            }
            if (starved == PROCESSES) {
                idleFactoryTicks++;
                DockMachine machine = dock.machineAt(pos);
                if (machine != null && machine.asleep(now)) {
                    asleepStarvedTicks++;
                }
            }
            for (int s = 0; out != null && s < out.getSlots(); s++) {
                ItemStack stack = out.getStackInSlot(s);
                heldOutput += stack.getCount();
                if (stack.getCount() >= stack.getMaxStackSize()) {
                    fullOutputSlotTicks++;
                }
            }
        }
    }

    /** Lingotes que cada fábrica fez nos ticks acelerados. */
    final long[] made = new long[FACTORIES];
    /** Ticks em que a fábrica não produziu nada nos ticks acelerados (ociosa). */
    final long[] idle = new long[FACTORIES];
    /** No modo do pedido inteiro: quantas execuções a CPU ainda tem (−1 sem fim). */
    long jobLeft = -1;
    long heldStart;

    private static long sum(ServerLevel level, BlockPos pos, Direction face) {
        IItemHandler h = level.getCapability(Capabilities.ItemHandler.BLOCK, pos, face);
        long total = 0;
        for (int s = 0; h != null && s < h.getSlots(); s++) {
            total += h.getStackInSlot(s).getCount();
        }
        return total;
    }

    /** Devolvidos desde o começo da medição. */
    long returnedSince() {
        return dock.side(NORTH).returned() - returnedStart;
    }

    void startMeasure() {
        returnedStart = dock.side(NORTH).returned();
        for (BlockPos rel : factories) {
            heldStart += sum(helper.getLevel(), helper.absolutePos(rel), Direction.DOWN);
        }
    }

    private long heldNow() {
        long held = 0;
        for (BlockPos rel : factories) {
            held += sum(helper.getLevel(), helper.absolutePos(rel), Direction.DOWN);
        }
        return held;
    }

    /** Ticks extras da fábrica (a aceleração da Time Wand), com a energia cheia antes de cada um. */
    @SuppressWarnings("unchecked")
    private void accelerate(ServerLevel level, BlockPos pos) {
        BlockEntity be = level.getBlockEntity(pos);
        if (be == null) {
            return;
        }
        BlockState state = level.getBlockState(pos);
        BlockEntityTicker<BlockEntity> ticker = (BlockEntityTicker<BlockEntity>) state.getTicker(level, be.getType());
        charge(level, pos);
        for (int i = 1; i < speed && ticker != null; i++) {
            ticker.tick(level, pos, state, be);
            charge(level, pos);
        }
    }

    private static void charge(ServerLevel level, BlockPos pos) {
        IEnergyStorage energy = level.getCapability(Capabilities.EnergyStorage.BLOCK, pos, Direction.NORTH);
        if (energy != null) {
            energy.receiveEnergy(Integer.MAX_VALUE, false);
        }
    }

    /** Até {@code remaining} execuções, parando na primeira recusa; devolve quantas entraram. */
    private int orders(int remaining, boolean measuring) {
        int done = 0;
        for (int p = 0; p < remaining && jobLeft != 0; p++) {
            if (!DockDelivery.acceptsOrders(dock, NORTH)) {
                if (measuring) {
                    blockedByPending++;
                }
                return done;
            }
            List<ItemStack> items = List.of(new ItemStack(Items.RAW_IRON, 64), new ItemStack(Items.RAW_IRON, 64),
                    new ItemStack(Items.RAW_IRON, 64), new ItemStack(Items.RAW_IRON, 64));
            long started = System.nanoTime();
            DockDelivery.Result result = DockDelivery.offer(dock, NORTH, items, List.of(), false);
            long spent = System.nanoTime() - started;
            if (result == DockDelivery.Result.ACCEPTED) {
                done++;
            }
            if (!measuring) {
                if (result != DockDelivery.Result.ACCEPTED) {
                    return done;
                }
                continue;
            }
            offerNanos += spent;
            if (result == DockDelivery.Result.ACCEPTED) {
                accepted++;
                if (jobLeft > 0) {
                    jobLeft--;
                }
                if (dock.side(NORTH).hasPending()) {
                    acceptedWithPending++;
                }
                continue;
            }
            int reason = dock.side(NORTH).lastRefusal();
            if (reason == io.github.matheusanbs.wirelessautomate.dock.DockStatus.ALL_FULL) {
                refusedAllFull++;
            } else if (reason == io.github.matheusanbs.wirelessautomate.dock.DockStatus.BUDGET) {
                refusedBudget++;
            } else {
                refusedOther++;
            }
            return done;
        }
        return done;
    }

    private void drainChest(boolean measuring) {
        ChestBlockEntity chest = helper.getBlockEntity(CHEST);
        for (int slot = 0; slot < chest.getContainerSize(); slot++) {
            ItemStack stack = chest.getItem(slot);
            if (!stack.isEmpty()) {
                if (measuring) {
                    chestDrained += stack.getCount();
                }
                chest.setItem(slot, ItemStack.EMPTY);
            }
        }
    }

    /** A linha do log; devolve os itens devolvidos por segundo. */
    double report(String name) {
        double seconds = ticks / 20.0;
        long returned = dock.side(NORTH).returned() - returnedStart;
        double rate = returned / seconds;
        double ceiling = FACTORIES * PROCESSES * speed * 20.0 / BASE_TICKS;
        double outputRate = returned / (double) ticks;
        double heldPerTick = heldOutput / (double) ticks;
        String line = String.format(java.util.Locale.ROOT,
                "MEDIDA doca-minmax %s tier=%s speed=%d pushes=%d ticks=%d: devolvidos %.1f itens/s (teto das fabricas"
                        + " %.1f/s, %.0f%%), baú %d; processos sem entrada %.1f%%; fabricas paradas sem entrada %.1f%%"
                        + " (dormindo na entrega %.1f%%); dormindo na entrega %.1f%%; saidas cheias %.1f%%; espera na"
                        + " saida %.1f ticks (media %.1f itens);"
                        + " pedidos aceitos %d (com pendente %d), barrados pelo pendente %d, todas cheias %d,"
                        + " orçamento %d, outros %d; lado com pendente %.1f%% dos ticks; oferta %.1f µs cada;"
                        + " motor %.3f ms/tick (max %.3f)",
                name, tier.getSerializedName(), speed, pushes, ticks, rate, ceiling, 100 * rate / ceiling, chestDrained,
                100.0 * starvedSlotTicks / (ticks * FACTORIES * PROCESSES),
                100.0 * idleFactoryTicks / (ticks * FACTORIES), 100.0 * asleepStarvedTicks / (ticks * FACTORIES),
                100.0 * asleepTicks / (ticks * FACTORIES),
                100.0 * fullOutputSlotTicks / (ticks * FACTORIES * PROCESSES),
                outputRate == 0 ? 0 : heldPerTick / outputRate, heldPerTick, accepted, acceptedWithPending,
                blockedByPending, refusedAllFull, refusedBudget, refusedOther, 100.0 * pendingTicks / ticks,
                accepted + refusedAllFull + refusedBudget + refusedOther == 0 ? 0
                        : offerNanos / 1000.0 / (accepted + refusedAllFull + refusedBudget + refusedOther),
                engineNanos / 1e6 / ticks, engineMaxNanos / 1e6);
        StringBuilder per = new StringBuilder();
        for (int f = 0; f < FACTORIES; f++) {
            per.append(String.format(java.util.Locale.ROOT, " %.0f%%", 100.0 * idle[f] / ticks));
        }
        long made = 0;
        for (long m : this.made) {
            made += m;
        }
        WirelessAutomate.LOGGER.info(line + "; ociosa por fabrica:" + per + "; balanço: feito nos ticks acelerados "
                + made + ", devolvido " + returned + ", nas saídas " + heldStart + " -> " + heldNow());
        return rate;
    }
}
