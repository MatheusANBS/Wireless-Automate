package io.github.matheusanbs.wirelessautomate.gametest;

import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.block.RouterBlock;
import io.github.matheusanbs.wirelessautomate.block.RouterTier;
import io.github.matheusanbs.wirelessautomate.dock.DockPendingContents;
import io.github.matheusanbs.wirelessautomate.dock.DockProbe;
import io.github.matheusanbs.wirelessautomate.dock.DockSide;
import io.github.matheusanbs.wirelessautomate.dock.DockStatus;
import io.github.matheusanbs.wirelessautomate.dock.PatternDockBlockEntity;
import io.github.matheusanbs.wirelessautomate.network.NetworkManager;
import io.github.matheusanbs.wirelessautomate.network.PortMode;
import io.github.matheusanbs.wirelessautomate.network.ResourceType;
import io.github.matheusanbs.wirelessautomate.range.RemoteMachines;
import io.github.matheusanbs.wirelessautomate.range.RemoteSide;
import io.github.matheusanbs.wirelessautomate.registry.ModBlocks;
import io.github.matheusanbs.wirelessautomate.registry.ModDataComponents;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongArrays;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;

/**
 * O "teste definitivo" do dono: Infinite Smelting Factories do Mekanism Extras (17 processos, entradas e saídas de
 * 4.096 por slot) com os upgrades instalados de verdade (o Creative Upgrade do Extras, 8 Speed e 8 Energy), aceleradas
 * a 256× como pela Time Wand (o ticker do bloco chamado 255 vezes a mais por tick), num lado de uma Doca Ultimate; um
 * Pattern Provider do AE2 ao norte, com o padrão de processamento {@code perJob} minérios brutos → {@code perJob}
 * lingotes; a rede ME (energia criativa, duas células de 256k); e a CPU: a do AE2, de 3×3×3 (11 de 256k e 16
 * Crafting Accelerators: 16 coprocessadores), ou o Quantum Computer de 7×7×7 do Advanced AE
 * ({@link DockQuantumMedida#buildComputer}). O pedido vai pela CPU e volta direto à rede ME.
 *
 * <p>Com o Creative Upgrade, o Extras zera o tempo da receita ({@code getTicksRequired} 0) e o gasto de energia: cada
 * chamada do ticker faz uma operação por processo, e os Speed Upgrades não mudam nada. O teto é {@code 17 × 256} lingotes
 * por tick por fábrica (87.040 por segundo).
 *
 * <p>Conta pelo {@link DockProbe} (as perguntas e as ofertas do provedor, com o motivo e o custo, e as visitas da
 * volta) e por tick (o que cada fábrica fez nos ticks acelerados, os processos sem entrada, as saídas cheias, o
 * pendente). Escreve as linhas "MEDIDA doca-definitivo" no log.
 */
final class DockDefinitivoMedida implements DockProbe.Listener {
    static final BlockPos DOCK = new BlockPos(5, 1, 2);
    static final BlockPos PROVIDER = DOCK.north();
    static final BlockPos ENERGY = PROVIDER.west();
    static final BlockPos DRIVE = PROVIDER.east();
    static final int NORTH = Direction.NORTH.ordinal();
    static final ResourceLocation FACTORY_ID = ResourceLocation.fromNamespaceAndPath("mekanism_extras",
            "infinite_smelting_factory");
    /** Processos da Infinite Factory ({@code ExtraFactoryTier.INFINITE}). */
    static final int PROCESSES = 17;
    /** Itens por slot de entrada e de saída da Infinite: 64 × (8 << 3). */
    static final int SLOT_LIMIT = 4096;
    /** A Time Wand no máximo. */
    static final int SPEED = 256;

    final GameTestHelper helper;
    final PatternDockBlockEntity dock;
    final List<BlockPos> factories = new ArrayList<>();
    final int perJob;
    final long target;
    final long orders;
    final boolean quantum;
    int coProcessors;
    int upgrades;

    long asks;
    long asksPending;
    long offers;
    long accepted;
    long refusedPending;
    long refusedAllFull;
    long refusedBudget;
    long refusedOther;
    long deliveryNanos;
    long deliveryMaxOffer;
    long returnNanos;
    long returnVisits;
    final LongArrayList tickNanos = new LongArrayList();
    long maxAcceptedPerTick;
    long maxDeliveryTick;
    long maxReturnTick;
    private long tickAccepted;
    private long tickDelivery;
    private long tickReturn;

    long ticks;
    long returnedStart;
    long heldStart;
    long heldSum;
    long made;
    long idleFactoryTicks;
    long fullOutputSlotTicks;
    long emptyInputSlotTicks;
    long pendingTicks;
    long pendingMax;
    long pendingRun;
    long pendingRunMax;
    /** Ticks com toda máquina ativa do lado com pendente (o lado recusa pedidos). */
    long waitingTicks;
    /** O maior NBT da Doca (o block entity) e do componente {@code pattern_dock_pending} com o pendente, em bytes. */
    long nbtMax;
    long componentMax;
    /** O que as fábricas fizeram em cada tick acelerado, para o regime (do 5º tick ao do último empurrão). */
    final LongArrayList madePerTick = new LongArrayList();
    long lastPushTick;
    /**
     * O tick em que o último lingote voltou à rede (0 antes disso). O pedido só acaba quando a rede também deixa de
     * pedir, e o Advanced AE pode levar um tick a mais para isso (o {@code isRequesting} vem do fim do tick do AE2, e
     * depende de quantas inserções passaram pelas CPUs): a vazão da Doca é contada até aqui.
     */
    long allReturnedTick;

    DockDefinitivoMedida(GameTestHelper helper, int count, int perJob, long target, boolean quantum) {
        this.helper = helper;
        this.perJob = perJob;
        this.target = target;
        this.orders = (target + perJob - 1) / perJob;
        this.quantum = quantum;
        helper.setBlock(DOCK, ModBlocks.PATTERN_DOCK.get().defaultBlockState().setValue(RouterBlock.TIER,
                RouterTier.ULTIMATE));
        for (int i = 0; i < count; i++) {
            BlockPos pos = new BlockPos(1 + 2 * i, 1, 5);
            helper.setBlock(pos, BuiltInRegistries.BLOCK.get(FACTORY_ID).defaultBlockState());
            factories.add(pos);
        }
        dock = helper.getBlockEntity(DOCK);
        helper.setBlock(PROVIDER, Ae2DockGameTests.ae2("pattern_provider"));
        helper.setBlock(ENERGY, Ae2DockGameTests.ae2("creative_energy_cell"));
        helper.setBlock(DRIVE, Ae2DockGameTests.ae2("drive"));
        ResourceLocation cell = ResourceLocation.fromNamespaceAndPath("ae2", "item_storage_cell_256k");
        helper.assertTrue(Ae2TestSupport.insertCell(helper.getLevel(), helper.absolutePos(DRIVE), cell, 0)
                && Ae2TestSupport.insertCell(helper.getLevel(), helper.absolutePos(DRIVE), cell, 1),
                "as células não entraram");
        helper.assertTrue(Ae2TestSupport.installPattern(helper.getLevel(), helper.absolutePos(PROVIDER),
                Ae2TestSupport.processingPattern(Items.IRON_INGOT, perJob, Items.RAW_IRON, perJob)),
                "o padrão não entrou no provedor");
        if (quantum) {
            DockQuantumMedida.buildComputer(helper);
        } else {
            buildCpu();
        }
    }

    /**
     * A CPU do AE2 em cima do provedor: 3×3×3, 11 Crafting Storage de 256k (o pedido de 1.000.000 não cabe em menos
     * bytes) e 16 Crafting Accelerators.
     */
    private void buildCpu() {
        Block storage = Ae2DockGameTests.ae2("256k_crafting_storage");
        Block accelerator = Ae2DockGameTests.ae2("crafting_accelerator");
        int placed = 0;
        for (int x = 4; x <= 6; x++) {
            for (int y = 2; y <= 4; y++) {
                for (int z = 0; z <= 2; z++) {
                    helper.setBlock(new BlockPos(x, y, z), placed++ < 11 ? storage : accelerator);
                }
            }
        }
    }

    void registered() {
        if (!NetworkManager.get().hasVisitor(dock.returns())) {
            throw new GameTestAssertException("a volta da Doca não está no motor");
        }
    }

    /** Vincula as fábricas ao lado Norte (entrada em Cima, saída em Baixo) e instala os upgrades. */
    void link() {
        for (BlockPos rel : factories) {
            BlockPos pos = helper.absolutePos(rel);
            BlockState state = helper.getLevel().getBlockState(pos);
            BlockEntity be = helper.getLevel().getBlockEntity(pos);
            ChemicalTestSupport.mekanismSides(be);
            upgrades += ChemicalTestSupport.mekanismUpgrades(be, "CREATIVE", 1, "SPEED", 8, "ENERGY", 8);
            helper.assertTrue(dock.link(NORTH, pos, FACTORY_ID, RemoteMachines.frontOf(state)), "não vinculou");
        }
        DockSide side = dock.side(NORTH);
        side.profile().face(ResourceType.ITEM, RemoteSide.TOP).setMode(PortMode.INSERT);
        side.profile().face(ResourceType.ITEM, RemoteSide.BOTTOM).setMode(PortMode.EXTRACT);
    }

    void ready() {
        String missing = Ae2TestSupport.notReady(helper.getLevel(), helper.absolutePos(PROVIDER), Items.IRON_INGOT);
        if (missing != null) {
            throw new GameTestAssertException("a rede ainda não subiu: " + missing);
        }
        coProcessors = Ae2TestSupport.coProcessors(helper.getLevel(), helper.absolutePos(PROVIDER));
        if (quantum ? coProcessors < 1000 : coProcessors != 16) {
            throw new GameTestAssertException("CPUs " + Ae2TestSupport.cpus(helper.getLevel(),
                    helper.absolutePos(PROVIDER)) + ", coprocessadores " + coProcessors);
        }
    }

    void stock() {
        long want = orders * perJob;
        long in = Ae2TestSupport.stock(helper.getLevel(), helper.absolutePos(PROVIDER), Items.RAW_IRON, want);
        helper.assertTrue(in == want, "o minério não coube: " + in);
    }

    Object plan() {
        return Ae2TestSupport.plan(helper.getLevel(), helper.absolutePos(PROVIDER), Items.IRON_INGOT, target);
    }

    void submit(Object plan) {
        returnedStart = dock.side(NORTH).returned();
        heldStart = held();
        DockProbe.listener = this;
        String error = Ae2TestSupport.submit(helper.getLevel(), helper.absolutePos(PROVIDER), plan);
        if (error != null) {
            DockProbe.listener = null;
            throw new GameTestAssertException(error);
        }
    }

    long returnedSince() {
        return dock.side(NORTH).returned() - returnedStart;
    }

    boolean done() {
        return returnedSince() >= orders * perJob
                && !Ae2TestSupport.requesting(helper.getLevel(), helper.absolutePos(PROVIDER), Items.IRON_INGOT);
    }

    /** Um tick: as fábricas aceleradas (255 chamadas a mais do ticker) e as contas. */
    void tick() {
        ServerLevel level = helper.getLevel();
        long tickMade = 0;
        for (BlockPos rel : factories) {
            BlockPos pos = helper.absolutePos(rel);
            long before = sum(level, pos, Direction.DOWN);
            accelerate(level, pos);
            long produced = sum(level, pos, Direction.DOWN) - before;
            tickMade += produced;
            if (produced <= 0) {
                idleFactoryTicks++;
            }
        }
        madePerTick.add(tickMade);
        ticks++;
        if (allReturnedTick == 0 && returnedSince() >= orders * perJob) {
            allReturnedTick = ticks;
        }
        DockSide side = dock.side(NORTH);
        if (side.hasPending()) {
            pendingTicks++;
            pendingRun++;
            pendingRunMax = Math.max(pendingRunMax, pendingRun);
            long items = side.pendingTotal().itemCount();
            if (items > pendingMax) {
                pendingMax = items;
                measureNbt();
            }
        } else {
            pendingRun = 0;
        }
        if (side.waitingOnPending()) {
            waitingTicks++;
        }
        for (BlockPos rel : factories) {
            BlockPos pos = helper.absolutePos(rel);
            IItemHandler in = level.getCapability(Capabilities.ItemHandler.BLOCK, pos, Direction.UP);
            IItemHandler out = level.getCapability(Capabilities.ItemHandler.BLOCK, pos, Direction.DOWN);
            for (int s = 0; in != null && s < in.getSlots(); s++) {
                if (in.getStackInSlot(s).isEmpty()) {
                    emptyInputSlotTicks++;
                }
            }
            for (int s = 0; out != null && s < out.getSlots(); s++) {
                int count = out.getStackInSlot(s).getCount();
                heldSum += count;
                if (count >= SLOT_LIMIT) {
                    fullOutputSlotTicks++;
                }
            }
        }
        maxAcceptedPerTick = Math.max(maxAcceptedPerTick, tickAccepted);
        maxDeliveryTick = Math.max(maxDeliveryTick, tickDelivery);
        maxReturnTick = Math.max(maxReturnTick, tickReturn);
        tickNanos.add(tickDelivery + tickReturn);
        tickAccepted = 0;
        tickDelivery = 0;
        tickReturn = 0;
    }

    @SuppressWarnings("unchecked")
    private static void accelerate(ServerLevel level, BlockPos pos) {
        BlockEntity be = level.getBlockEntity(pos);
        if (be == null) {
            return;
        }
        BlockState state = level.getBlockState(pos);
        BlockEntityTicker<BlockEntity> ticker = (BlockEntityTicker<BlockEntity>) state.getTicker(level, be.getType());
        for (int i = 1; i < SPEED && ticker != null; i++) {
            ticker.tick(level, pos, state, be);
        }
    }

    private static long sum(ServerLevel level, BlockPos pos, Direction face) {
        IItemHandler h = level.getCapability(Capabilities.ItemHandler.BLOCK, pos, face);
        long total = 0;
        for (int s = 0; h != null && s < h.getSlots(); s++) {
            total += h.getStackInSlot(s).getCount();
        }
        return total;
    }

    private long held() {
        long total = 0;
        for (BlockPos rel : factories) {
            total += sum(helper.getLevel(), helper.absolutePos(rel), Direction.DOWN);
        }
        return total;
    }

    @Override
    public void asked(boolean accepts) {
        asks++;
        if (!accepts) {
            DockSide side = dock.side(NORTH);
            if (side != null && side.waitingOnPending()) {
                asksPending++;
            }
        }
    }

    @Override
    public void offered(boolean ok, int reason, long nanos) {
        offers++;
        deliveryNanos += nanos;
        tickDelivery += nanos;
        deliveryMaxOffer = Math.max(deliveryMaxOffer, nanos);
        if (ok) {
            accepted++;
            tickAccepted++;
            lastPushTick = ticks;
        } else if (reason == DockStatus.PENDING) {
            refusedPending++;
        } else if (reason == DockStatus.ALL_FULL) {
            refusedAllFull++;
        } else if (reason == DockStatus.BUDGET) {
            refusedBudget++;
        } else {
            refusedOther++;
        }
    }

    @Override
    public void returned(long nanos) {
        returnNanos += nanos;
        tickReturn += nanos;
        returnVisits++;
    }

    void report(String name) {
        DockProbe.listener = null;
        long returned = returnedSince();
        long heldNow = held();
        made = returned + heldNow - heldStart;
        int count = factories.size();
        double seconds = ticks / 20.0;
        double rate = returned / seconds;
        double ceiling = (double) count * PROCESSES * SPEED * 20;
        long slots = ticks * count * PROCESSES;
        WirelessAutomate.LOGGER.info(String.format(Locale.ROOT,
                "MEDIDA doca-definitivo %s fabricas=%d padrao=%d coprocessadores=%d upgrades=%d: %d lingotes em %d"
                        + " ticks (%.1f s), %.1f itens/s (teto %.0f/s, %.1f%%); feito %d (%.1f%% do teto);"
                        + " fabricas paradas %.1f%% dos ticks; processos sem entrada %.1f%%; saidas cheias %.1f%%;"
                        + " media nas saidas %.0f itens; lado com pendente %.1f%% dos ticks (max %d itens, %d ticks"
                        + " seguidos); lado esperando (toda maquina com pendente) %.1f%% dos ticks; NBT da Doca max"
                        + " %d bytes, componente max %d bytes",
                name, count, perJob, coProcessors, upgrades, returned, ticks, seconds, rate, ceiling,
                100 * rate / ceiling, made, 100.0 * made / (ticks * ceiling / 20), 100.0 * idleFactoryTicks
                        / (ticks * count), 100.0 * emptyInputSlotTicks / slots, 100.0 * fullOutputSlotTicks / slots,
                heldSum / (double) ticks, 100.0 * pendingTicks / ticks, pendingMax, pendingRunMax,
                100.0 * waitingTicks / ticks, nbtMax, componentMax));
        long regimeMade = 0;
        int regimeTicks = 0;
        for (int t = 5; t < lastPushTick && t < madePerTick.size(); t++) {
            regimeMade += madePerTick.getLong(t);
            regimeTicks++;
        }
        // Nos ticks acelerados a fábrica faz 255 das 256 chamadas do tick; a do tick normal fica fora desta conta.
        double regimeCeiling = (double) count * PROCESSES * (SPEED - 1);
        double returnRate = allReturnedTick == 0 ? 0 : returned / (allReturnedTick / 20.0);
        WirelessAutomate.LOGGER.info(String.format(Locale.ROOT,
                "MEDIDA doca-definitivo %s tudo devolvido no tick %d: %.1f itens/s ate a ultima volta (%.1f%% do teto)",
                name, allReturnedTick, returnRate, 100 * returnRate / ceiling));
        WirelessAutomate.LOGGER.info(String.format(Locale.ROOT,
                "MEDIDA doca-definitivo %s regime (tick 5 ao %d, o do ultimo empurrao): %.0f%% do teto dos ticks"
                        + " acelerados",
                name, lastPushTick, regimeTicks == 0 ? 0 : 100.0 * regimeMade / (regimeTicks * regimeCeiling)));
        long[] sorted = tickNanos.toLongArray();
        LongArrays.quickSort(sorted);
        long over = 0;
        for (long n : sorted) {
            if (n > 1_000_000) {
                over++;
            }
        }
        WirelessAutomate.LOGGER.info(String.format(Locale.ROOT,
                "MEDIDA doca-definitivo %s empurroes aceitos %d (%.2f por tick, max %d); perguntas %d (nao, pendente %d);"
                        + " ofertas %d: recusadas pendente %d, todas cheias %d, orcamento %d, outras %d;"
                        + " entrega %.4f ms/tick (max %.3f), %.1f us por oferta (max %.1f); volta %.4f ms/tick"
                        + " (max %.3f), %d visitas, %.1f us por visita, %.1f ns por item; por tick p50 %.4f, p99 %.4f,"
                        + " max %.3f ms, acima de 1 ms em %d de %d",
                name, accepted, accepted / (double) ticks, maxAcceptedPerTick, asks, asksPending, offers,
                refusedPending, refusedAllFull, refusedBudget, refusedOther, deliveryNanos / 1e6 / ticks,
                maxDeliveryTick / 1e6, offers == 0 ? 0 : deliveryNanos / 1e3 / offers, deliveryMaxOffer / 1e3,
                returnNanos / 1e6 / ticks, maxReturnTick / 1e6, returnVisits,
                returnVisits == 0 ? 0 : returnNanos / 1e3 / returnVisits,
                returned == 0 ? 0 : returnNanos / (double) returned, percentile(sorted, 0.5) / 1e6,
                percentile(sorted, 0.99) / 1e6, sorted.length == 0 ? 0 : sorted[sorted.length - 1] / 1e6, over,
                sorted.length));
    }

    /**
     * Mede o NBT da Doca e o do componente do item (o pendente que iria no item, se ela quebrasse agora), em bytes.
     * Só quando o pendente cresce: o custo fica fora dos cronômetros da entrega e da volta.
     */
    private void measureNbt() {
        var registries = helper.getLevel().registryAccess();
        nbtMax = Math.max(nbtMax, bytes(dock.saveWithoutMetadata(registries)));
        var contents = dock.collectComponents().get(ModDataComponents.PATTERN_DOCK_PENDING.get());
        if (contents != null) {
            Tag tag = DockPendingContents.CODEC.encodeStart(registries.createSerializationContext(NbtOps.INSTANCE),
                    contents).getOrThrow();
            CompoundTag wrapper = new CompoundTag();
            wrapper.put("c", tag);
            componentMax = Math.max(componentMax, bytes(wrapper));
        }
    }

    private static long bytes(CompoundTag tag) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (DataOutputStream data = new DataOutputStream(out)) {
            NbtIo.write(tag, data);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return out.size();
    }

    private static long percentile(long[] sorted, double p) {
        return sorted.length == 0 ? 0 : sorted[Math.min(sorted.length - 1, (int) (p * sorted.length))];
    }
}
