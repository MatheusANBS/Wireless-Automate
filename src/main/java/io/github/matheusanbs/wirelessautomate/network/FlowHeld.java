package io.github.matheusanbs.wirelessautomate.network;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * O guardado na rede do {@link FlowSampler}: para as redes × tipos que uma Tela de Sinal pede, cada nó lido soma o
 * conteúdo e a capacidade das máquinas das portas em Armazém dessa rede ({@link HeldReader}, pelos caches de
 * capability das portas). O pedido vale por {@link #LEASE_TICKS} e é renovado por quem lê; sem pedido nada é lido e
 * nada é alocado. Uma máquina conta uma vez por volta, mesmo com várias faces ou em vários roteadores; a que está
 * num chunk descarregado fica fora. O número de uma rede só é publicado numa volta inteira lida com o pedido já
 * valendo desde o começo dela.
 *
 * <p>Só a thread do servidor mexe.
 */
final class FlowHeld {
    /** Quanto um pedido vale sem ser renovado (três voltas): quem lê o renova a cada volta. */
    static final long LEASE_TICKS = 3L * FlowSampler.SAMPLE_TICKS;
    private static final int TYPE_COUNT = ResourceType.values().length;
    private static final ResourceType[] LOADED = NetworkRoutes.TRANSFER_TYPES;
    private static final long NO_REQUEST = Long.MIN_VALUE / 2;

    /** Os pedidos por rede; vazio sem nenhuma Tela pedindo. */
    private final Map<UUID, Demand> demands = new HashMap<>();
    /** Os tipos (bit pelo ordinal) com algum pedido valendo, refeito no pedido e no fim da volta. */
    private int types;
    /** As máquinas já somadas na volta em curso (rede × tipo × posição). */
    private final LongOpenHashSet seen = new LongOpenHashSet();
    private final long[] scratch = new long[2];
    private long reads;

    /** Os pedidos de uma rede: o tick do último por tipo e a primeira volta inteira em que valeu. */
    private static final class Demand {
        final long[] last = new long[TYPE_COUNT];
        final int[] since = new int[TYPE_COUNT];

        Demand() {
            Arrays.fill(last, NO_REQUEST);
        }
    }

    /** Se algum pedido está valendo: só então os nós somam o guardado. */
    boolean any() {
        return types != 0;
    }

    long reads() {
        return reads;
    }

    /** Renova o pedido; o primeiro depois de expirar só vale a partir da volta seguinte ({@code cycle + 1}). */
    void request(UUID network, ResourceType type, long now, int cycle) {
        Demand demand = demands.computeIfAbsent(network, id -> new Demand());
        int t = type.ordinal();
        if (now - demand.last[t] > LEASE_TICKS) {
            demand.since[t] = cycle + 1;
        }
        demand.last[t] = now;
        types |= 1 << t;
    }

    /** Soma o conteúdo e a capacidade das máquinas em Armazém do nó, nas redes × tipos pedidos. */
    void add(FlowSampler sampler, NodePorts ports, long now) {
        for (ResourceType type : LOADED) {
            int t = type.ordinal();
            Port[] active = (types >> t & 1) == 0 ? null : ports.collected(type);
            if (active == null) {
                continue;
            }
            for (Port port : active) {
                NetworkRoutes routes = port.network;
                if (routes == null || !port.source || !port.destination) {
                    continue;
                }
                Demand demand = demands.get(routes.id);
                if (demand == null || now - demand.last[t] > LEASE_TICKS) {
                    continue;
                }
                long key = port.machinePos.asLong() ^ (routes.id.getLeastSignificantBits() * 0x9E3779B97F4A7C15L)
                        ^ ((long) t << 60);
                if (!seen.add(key)) {
                    continue;
                }
                scratch[0] = 0;
                scratch[1] = 0;
                reads++;
                if (HeldReader.read(port, scratch)) {
                    FlowSampler.NetworkFlows entry = sampler.entry(routes);
                    entry.held[t] += scratch[0];
                    entry.heldCapacity[t] += scratch[1];
                }
            }
        }
    }

    /** No fecho da volta {@code cycle}: publica o guardado se o pedido já valia no começo dela; senão esconde. */
    void close(FlowSampler.NetworkFlows entry, int t, long now, int cycle) {
        Demand demand = demands.get(entry.id);
        boolean show = demand != null && demand.since[t] <= cycle && now - demand.last[t] <= LEASE_TICKS;
        entry.heldShown[t] = show;
        if (show) {
            entry.closedHeld[t] = entry.held[t];
            entry.closedHeldCapacity[t] = entry.heldCapacity[t];
        }
    }

    /** Depois do fecho: esquece as máquinas da volta, tira os pedidos que ninguém renovou e refaz os tipos. */
    void endCycle(long now) {
        if (demands.isEmpty()) {
            return;
        }
        seen.clear();
        types = 0;
        Iterator<Demand> it = demands.values().iterator();
        while (it.hasNext()) {
            Demand demand = it.next();
            int live = 0;
            for (int t = 0; t < TYPE_COUNT; t++) {
                if (now - demand.last[t] <= LEASE_TICKS) {
                    live |= 1 << t;
                }
            }
            if (live == 0) {
                it.remove();
            }
            types |= live;
        }
    }
}
