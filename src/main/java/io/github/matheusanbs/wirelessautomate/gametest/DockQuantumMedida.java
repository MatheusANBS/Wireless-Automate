package io.github.matheusanbs.wirelessautomate.gametest;

import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.block.RouterTier;
import io.github.matheusanbs.wirelessautomate.dock.DockProbe;
import io.github.matheusanbs.wirelessautomate.dock.DockSide;
import io.github.matheusanbs.wirelessautomate.dock.DockStatus;
import io.github.matheusanbs.wirelessautomate.network.NetworkManager;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongArrays;
import java.util.Locale;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;

/**
 * O uso intenso da Doca com o AE2 de verdade: as cinco Ultimate Smelting Factories da {@link DockMinMaxMedida} (sem o
 * baú e sem a CPU emulada), um Pattern Provider do AE2 ao norte da Doca com o padrão de 256 minérios brutos → 256
 * lingotes, a rede ME (energia criativa, um drive com uma célula de 256k) e um Quantum Computer do Advanced AE: o
 * maior que a config padrão deixa montar (7×7×7: a casca de Quantum Structure, o Quantum Core no centro, um Multi
 * Threader e 123 Quantum Accelerators), ou só o Quantum Core (8 coprocessadores). O pedido de 100.000 lingotes (391
 * execuções) vai pela CPU, e o resultado volta direto à rede ME pela volta da Doca ({@code Ae2DockNetwork}).
 *
 * <p>Conta pelo {@link DockProbe}: as perguntas do provedor ({@code acceptsPlans}), as ofertas aceitas e recusadas (com
 * o motivo), os empurrões aceitos por tick, o custo da entrega e o da volta. Escreve a linha "MEDIDA doca-quantum" no
 * log, junto com a "MEDIDA doca-minmax" da cena (a ociosidade das fábricas e o balanço dos lingotes).
 */
final class DockQuantumMedida implements DockProbe.Listener {
    static final BlockPos PROVIDER = DockMinMaxMedida.CHEST;
    static final BlockPos ENERGY = PROVIDER.west();
    static final BlockPos DRIVE = PROVIDER.east();
    /** O canto de baixo do Quantum Computer de 7×7×7; a camada de baixo encosta no topo do provedor. */
    static final BlockPos CUBE_MIN = new BlockPos(2, 2, 1);
    static final int CUBE = 7;
    /** O pedido: 100.000 lingotes em execuções de 256 (391, o AE2 arredonda para cima). */
    static final int TARGET = 100_000;
    static final int PER_JOB = 256;
    static final int ORDERS = (TARGET + PER_JOB - 1) / PER_JOB;

    final GameTestHelper helper;
    final DockMinMaxMedida scene;
    final boolean fullComputer;
    int coProcessors;

    long asks;
    long asksPending;
    long asksOther;
    long offers;
    long accepted;
    long refusedPending;
    long refusedAllFull;
    long refusedBudget;
    long refusedOther;
    long deliveryNanos;
    long deliveryMaxOffer;
    long refusedNanos;
    /** O custo de cada tick (a entrega mais a volta), para os percentis. */
    final LongArrayList tickNanos = new LongArrayList();
    long returnNanos;
    long returnVisits;
    long ticks;
    long ticksWithPush;
    long maxAcceptedPerTick;
    long maxOffersPerTick;
    long maxDeliveryTick;
    long maxReturnTick;
    long engineNanos;
    /** Os contadores do tick em curso. */
    private long tickAccepted;
    private long tickOffers;
    private long tickDelivery;
    private long tickReturn;

    DockQuantumMedida(GameTestHelper helper, int speed, boolean fullComputer) {
        this.helper = helper;
        this.fullComputer = fullComputer;
        scene = new DockMinMaxMedida(helper, RouterTier.ULTIMATE, speed, false);
        helper.setBlock(PROVIDER, Ae2DockGameTests.ae2("pattern_provider"));
        helper.setBlock(ENERGY, Ae2DockGameTests.ae2("creative_energy_cell"));
        helper.setBlock(DRIVE, Ae2DockGameTests.ae2("drive"));
        helper.assertTrue(Ae2TestSupport.insertCell(helper.getLevel(), helper.absolutePos(DRIVE),
                ResourceLocation.fromNamespaceAndPath("ae2", "item_storage_cell_256k"), 0), "a célula não entrou");
        helper.assertTrue(Ae2TestSupport.installPattern(helper.getLevel(), helper.absolutePos(PROVIDER),
                Ae2TestSupport.processingPattern(Items.IRON_INGOT, PER_JOB, Items.RAW_IRON, PER_JOB)),
                "o padrão não entrou no provedor");
        if (fullComputer) {
            buildComputer(helper);
        } else {
            // o Quantum Core sozinho só se liga por cima e por baixo: em cima do provedor
            helper.setBlock(PROVIDER.above(), quantum("quantum_core"));
        }
    }

    static Block quantum(String path) {
        return BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath("advanced_ae", path));
    }

    /** A casca de Quantum Structure, o Core no centro, um Multi Threader num canto do miolo e o resto Accelerators. */
    static void buildComputer(GameTestHelper helper) {
        Block structure = quantum("quantum_structure");
        Block core = quantum("quantum_core");
        Block threader = quantum("quantum_multi_threader");
        Block accelerator = quantum("quantum_accelerator");
        int last = CUBE - 1;
        int middle = CUBE / 2;
        for (int x = 0; x < CUBE; x++) {
            for (int y = 0; y < CUBE; y++) {
                for (int z = 0; z < CUBE; z++) {
                    boolean shell = x == 0 || y == 0 || z == 0 || x == last || y == last || z == last;
                    Block block;
                    if (shell) {
                        block = structure;
                    } else if (x == middle && y == middle && z == middle) {
                        block = core;
                    } else if (x == 1 && y == 1 && z == 1) {
                        block = threader;
                    } else {
                        block = accelerator;
                    }
                    helper.setBlock(CUBE_MIN.offset(x, y, z), block);
                }
            }
        }
    }

    /** A rede está pronta: o nó ativo, o padrão conhecido e o Quantum Computer formado. */
    void ready() {
        String missing = Ae2TestSupport.notReady(helper.getLevel(), helper.absolutePos(PROVIDER), Items.IRON_INGOT);
        if (missing != null) {
            throw new GameTestAssertException("a rede ainda não subiu: " + missing);
        }
        coProcessors = Ae2TestSupport.coProcessors(helper.getLevel(), helper.absolutePos(PROVIDER));
        String cpus = Ae2TestSupport.cpus(helper.getLevel(), helper.absolutePos(PROVIDER));
        if (!cpus.equals("AdvCraftingCPU") || fullComputer && coProcessors < 1000) {
            throw new GameTestAssertException("CPUs " + cpus + ", coprocessadores " + coProcessors);
        }
    }

    void stock() {
        long in = Ae2TestSupport.stock(helper.getLevel(), helper.absolutePos(PROVIDER), Items.RAW_IRON,
                ORDERS * PER_JOB);
        helper.assertTrue(in == (long) ORDERS * PER_JOB, "o minério não coube: " + in);
    }

    Object plan() {
        return Ae2TestSupport.plan(helper.getLevel(), helper.absolutePos(PROVIDER), Items.IRON_INGOT, TARGET);
    }

    void submit(Object plan) {
        scene.startMeasure();
        DockProbe.listener = this;
        String error = Ae2TestSupport.submit(helper.getLevel(), helper.absolutePos(PROVIDER), plan);
        if (error != null) {
            DockProbe.listener = null;
            throw new GameTestAssertException(error);
        }
    }

    /** O tick em que o último lingote voltou (ver {@code DockDefinitivoMedida#allReturnedTick}); 0 antes disso. */
    long allReturnedTick;

    /** O pedido acabou: todos os lingotes voltaram e a rede não pede mais nada. */
    boolean done() {
        return scene.returnedSince() >= (long) ORDERS * PER_JOB
                && !Ae2TestSupport.requesting(helper.getLevel(), helper.absolutePos(PROVIDER), Items.IRON_INGOT);
    }

    /** Um tick: as fábricas aceleradas e as contas da cena, e fecha os contadores do tick. */
    void tick() {
        scene.tick(true);
        ticks++;
        if (allReturnedTick == 0 && scene.returnedSince() >= (long) ORDERS * PER_JOB) {
            allReturnedTick = ticks;
        }
        engineNanos += NetworkManager.get().budget().lastUsedNanos();
        if (tickAccepted > 0) {
            ticksWithPush++;
        }
        maxAcceptedPerTick = Math.max(maxAcceptedPerTick, tickAccepted);
        maxOffersPerTick = Math.max(maxOffersPerTick, tickOffers);
        maxDeliveryTick = Math.max(maxDeliveryTick, tickDelivery);
        maxReturnTick = Math.max(maxReturnTick, tickReturn);
        tickNanos.add(tickDelivery + tickReturn);
        tickAccepted = 0;
        tickOffers = 0;
        tickDelivery = 0;
        tickReturn = 0;
    }

    @Override
    public void asked(boolean accepts) {
        asks++;
        if (!accepts) {
            DockSide side = scene.dock.side(DockMinMaxMedida.NORTH);
            if (side != null && side.hasPending()) {
                asksPending++;
            } else {
                asksOther++;
            }
        }
    }

    @Override
    public void offered(boolean ok, int reason, long nanos) {
        offers++;
        tickOffers++;
        deliveryNanos += nanos;
        tickDelivery += nanos;
        deliveryMaxOffer = Math.max(deliveryMaxOffer, nanos);
        if (ok) {
            accepted++;
            tickAccepted++;
            return;
        }
        refusedNanos += nanos;
        if (reason == DockStatus.PENDING) {
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
        long returned = scene.returnedSince();
        double seconds = ticks / 20.0;
        double rate = returned / seconds;
        WirelessAutomate.LOGGER.info(String.format(Locale.ROOT,
                "MEDIDA doca-quantum %s coprocessadores=%d speed=%d: %d lingotes em %d ticks (%.1f s), %.1f itens/s;"
                        + " empurrões aceitos %d (%.2f por tick, max %d; ticks com empurrão %.1f%%);"
                        + " perguntas %d (não: pendente %d, outro %d); ofertas %d (max %d por tick): recusadas pendente"
                        + " %d, todas cheias %d, orçamento %d, outras %d; entrega %.4f ms/tick (max %.3f), %.1f µs por"
                        + " oferta (max %.1f); volta %.4f ms/tick (max %.3f), %d visitas, %.1f µs por visita, %.1f ns"
                        + " por item; motor (lastUsed, com a dívida) %.4f ms/tick",
                name, coProcessors, scene.speed, returned, ticks, seconds, rate, accepted,
                accepted / (double) ticks, maxAcceptedPerTick, 100.0 * ticksWithPush / ticks, asks, asksPending,
                asksOther, offers, maxOffersPerTick, refusedPending, refusedAllFull, refusedBudget, refusedOther,
                deliveryNanos / 1e6 / ticks, maxDeliveryTick / 1e6, offers == 0 ? 0 : deliveryNanos / 1e3 / offers,
                deliveryMaxOffer / 1e3, returnNanos / 1e6 / ticks, maxReturnTick / 1e6, returnVisits,
                returnVisits == 0 ? 0 : returnNanos / 1e3 / returnVisits,
                returned == 0 ? 0 : returnNanos / (double) returned, engineNanos / 1e6 / ticks));
        long[] sorted = tickNanos.toLongArray();
        LongArrays.quickSort(sorted);
        long over = 0;
        for (long n : sorted) {
            if (n > 1_000_000) {
                over++;
            }
        }
        long refused = offers - accepted;
        WirelessAutomate.LOGGER.info(String.format(Locale.ROOT,
                "MEDIDA doca-quantum %s tudo devolvido no tick %d: %.1f itens/s ate a ultima volta", name,
                allReturnedTick, allReturnedTick == 0 ? 0 : returned / (allReturnedTick / 20.0)));
        WirelessAutomate.LOGGER.info(String.format(Locale.ROOT,
                "MEDIDA doca-quantum %s por tick (entrega + volta): p50 %.4f ms, p99 %.4f ms, max %.3f ms, acima de 1 ms"
                        + " em %d de %d ticks; oferta recusada %.1f µs cada (%d), aceita %.1f µs cada",
                name, percentile(sorted, 0.50) / 1e6, percentile(sorted, 0.99) / 1e6,
                sorted.length == 0 ? 0 : sorted[sorted.length - 1] / 1e6, over, sorted.length,
                refused == 0 ? 0 : refusedNanos / 1e3 / refused, refused,
                accepted == 0 ? 0 : (deliveryNanos - refusedNanos) / 1e3 / accepted));
        scene.report(name);
    }

    private static long percentile(long[] sorted, double p) {
        return sorted.length == 0 ? 0 : sorted[Math.min(sorted.length - 1, (int) (p * sorted.length))];
    }
}
