package io.github.matheusanbs.wirelessautomate.network;

import io.github.matheusanbs.wirelessautomate.Config;
import io.github.matheusanbs.wirelessautomate.block.RouterBlockEntity;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import org.jetbrains.annotations.Nullable;

/**
 * Gerenciador central: um por servidor, processa todas as redes dentro do orçamento de tempo.
 * Os nós não fazem tick (ver docs/especificacao.md, "Arquitetura de performance").
 *
 * <p>Ciclo de cada tick:
 * <ol>
 *   <li>remonta as redes sujas ({@link NetworkRoutes#rebuild}); {@link #nodeChanged}, {@link #addNode}
 *       e {@link #removeNode} só marcam, nunca remontam na hora;
 *   <li>percorre a lista achatada de origens de todas as redes a partir do cursor salvo, uma visita
 *       por origem acordada, e para quando o orçamento acaba, guardando o cursor para o tick seguinte.
 *       O relógio é lido uma vez por origem, e serve tanto ao orçamento quanto ao profiler.
 * </ol>
 *
 * <p>O tempo das portas (balde, sono) é o {@code getTickCount()} do servidor.
 */
public final class NetworkManager {
    /** Teto do sono de origens e destinos, em ticks. */
    static final int MAX_SLEEP_TICKS = 100;
    private static final int TICKS_PER_SECOND = 20;

    private static NetworkManager instance;
    private static volatile boolean configChanged;

    private final Map<RouterBlockEntity, NodePorts> nodes = new Reference2ObjectOpenHashMap<>();
    private final Map<UUID, NetworkRoutes> networks = new HashMap<>();
    /** As mesmas redes de {@link #networks}, para iterar sem alocar. */
    private final List<NetworkRoutes> networkList = new ArrayList<>();
    /** Origens com destino de todas as redes, na ordem da visita. */
    private final List<Port> sources = new ArrayList<>();
    private final List<Port> scratch = new ArrayList<>();
    private final TickBudget budget = new TickBudget(500_000L);
    private int cursor;
    private boolean dirty;

    public static NetworkManager get() {
        if (instance == null) {
            instance = new NetworkManager();
        }
        return instance;
    }

    /** Descarta o estado ao parar o servidor, para o próximo mundo começar limpo. */
    public static void reset() {
        instance = null;
    }

    /** A config dos tiers mudou: todas as redes serão remontadas no próximo tick. Seguro de qualquer thread. */
    public static void configChanged() {
        configChanged = true;
    }

    public void addNode(RouterBlockEntity node) {
        if (nodes.containsKey(node)) {
            nodeChanged(node);
            return;
        }
        NodePorts ports = new NodePorts(node);
        nodes.put(node, ports);
        join(ports, node.networkId());
    }

    public void removeNode(RouterBlockEntity node) {
        NodePorts ports = nodes.remove(node);
        if (ports != null) {
            leave(ports);
        }
    }

    public boolean contains(RouterBlockEntity node) {
        return nodes.containsKey(node);
    }

    /** A rede ou a configuração de faces do nó mudou: marca as redes afetadas para remontar no próximo tick. */
    public void nodeChanged(RouterBlockEntity node) {
        NodePorts ports = nodes.get(node);
        if (ports == null) {
            return;
        }
        UUID network = node.networkId();
        if (Objects.equals(network, ports.network)) {
            markDirty(network);
        } else {
            leave(ports);
            join(ports, network);
        }
    }

    /** Um vizinho do nó avisou mudança: acorda as portas dele e as origens que entregam nos destinos dele. */
    public void wake(RouterBlockEntity node) {
        NodePorts ports = nodes.get(node);
        if (ports != null) {
            ports.wake();
        }
    }

    public int nodeCount() {
        return nodes.size();
    }

    public TickBudget budget() {
        return budget;
    }

    /** Retrato das redes que existem e têm nós carregados, para o profiler. */
    public List<NetworkStats> stats(MinecraftServer server) {
        long now = server.getTickCount();
        List<NetworkStats> result = new ArrayList<>();
        for (NetworkRoutes network : networkList) {
            if (!network.exists || network.members.isEmpty()) {
                continue;
            }
            int sourcesSleeping = 0;
            int destinationsSleeping = 0;
            for (Port port : network.sources) {
                if (port.sourceBackoff.isSleeping(now)) {
                    sourcesSleeping++;
                }
            }
            for (Port port : network.destinations) {
                if (port.destinationBackoff.isSleeping(now)) {
                    destinationsSleeping++;
                }
            }
            result.add(new NetworkStats(network.id, network.members.size(), network.averageNanos,
                    network.lastNanos, network.opsLastSecond,
                    network.sources.size() - sourcesSleeping, sourcesSleeping,
                    network.destinations.size() - destinationsSleeping, destinationsSleeping));
        }
        return result;
    }

    public void tick(MinecraftServer server) {
        budget.setBaseNanos((long) (Config.TICK_BUDGET_MS.get() * 1_000_000L));
        if (Config.ADAPTIVE_BUDGET.get()) {
            budget.adapt(server.getAverageTickTimeNanos() / 1_000_000.0);
        } else {
            budget.adapt(0);
        }
        budget.begin(System.nanoTime());
        long now = server.getTickCount();

        if (!networkList.isEmpty()) {
            checkNetworks(server);
        }
        if (dirty) {
            rebuild();
        }
        long end = transfer(now);

        for (int i = 0, n = networkList.size(); i < n; i++) {
            NetworkRoutes network = networkList.get(i);
            network.endTick();
            if (now % TICKS_PER_SECOND == 0) {
                network.endSecond();
            }
        }
        budget.end(end);
    }

    /** Visita as origens a partir do cursor enquanto houver orçamento. Devolve o último {@code nanoTime}. */
    private long transfer(long now) {
        int count = sources.size();
        NetworkRoutes timed = null;
        long mark = 0;
        int visited = 0;
        int index = cursor < count ? cursor : 0;
        for (int step = 0; step < count; step++) {
            Port source = sources.get(index);
            if (source.order != null && source.sourceBackoff.isAwake(now)) {
                long time = System.nanoTime();
                // Pelo menos uma visita por tick, mesmo que a montagem tenha gastado o orçamento.
                if (visited > 0 && !budget.hasTime(time)) {
                    if (timed != null) {
                        timed.tickNanos += time - mark;
                    }
                    cursor = index;
                    return time;
                }
                if (timed != null) {
                    timed.tickNanos += time - mark;
                }
                timed = source.network;
                mark = time;
                visited++;
                visit(source, now);
            }
            index = index + 1 == count ? 0 : index + 1;
        }
        long time = System.nanoTime();
        if (timed != null) {
            timed.tickNanos += time - mark;
        }
        return time;
    }

    private void visit(Port source, long now) {
        if (source.node.tier() != source.tier && source.network != null) {
            // Upgrade de tier não passa por nodeChanged: vazão e alcance mudam na próxima montagem.
            markDirty(source.network.id);
        }
        switch (source.type) {
            case ITEM -> ItemTransfer.move(source, now);
            case FLUID -> FluidTransfer.move(source, now);
            case ENERGY -> EnergyTransfer.move(source, now);
            case CHEMICAL -> {
            }
        }
    }

    /** Redes criadas ou removidas no {@link NetworkSavedData} mudam a validade das rotas. */
    private void checkNetworks(MinecraftServer server) {
        NetworkSavedData data = NetworkSavedData.get(server);
        boolean all = configChanged;
        configChanged = false;
        for (int i = 0, n = networkList.size(); i < n; i++) {
            NetworkRoutes network = networkList.get(i);
            boolean exists = data.network(network.id) != null;
            if (all || exists != network.exists) {
                network.exists = exists;
                network.dirty = true;
                dirty = true;
            }
        }
    }

    private void rebuild() {
        dirty = false;
        for (int i = networkList.size() - 1; i >= 0; i--) {
            NetworkRoutes network = networkList.get(i);
            if (!network.dirty) {
                continue;
            }
            network.dirty = false;
            if (network.members.isEmpty()) {
                networkList.remove(i);
                networks.remove(network.id);
                continue;
            }
            network.rebuild(scratch);
        }
        sources.clear();
        for (int i = 0, n = networkList.size(); i < n; i++) {
            List<Port> networkSources = networkList.get(i).sources;
            for (int j = 0, m = networkSources.size(); j < m; j++) {
                Port source = networkSources.get(j);
                if (source.order != null) {
                    sources.add(source);
                }
            }
        }
        if (cursor >= sources.size()) {
            cursor = 0;
        }
    }

    private void join(NodePorts ports, @Nullable UUID network) {
        ports.network = network;
        if (network == null) {
            return;
        }
        NetworkRoutes routes = networks.get(network);
        if (routes == null) {
            routes = new NetworkRoutes(network);
            networks.put(network, routes);
            networkList.add(routes);
        }
        routes.members.add(ports);
        routes.dirty = true;
        dirty = true;
    }

    private void leave(NodePorts ports) {
        ports.clearRoutes();
        UUID network = ports.network;
        ports.network = null;
        NetworkRoutes routes = network == null ? null : networks.get(network);
        if (routes != null) {
            routes.members.remove(ports);
            routes.dirty = true;
            dirty = true;
        }
    }

    private void markDirty(@Nullable UUID network) {
        NetworkRoutes routes = network == null ? null : networks.get(network);
        if (routes != null) {
            routes.dirty = true;
            dirty = true;
        }
    }

    /** Algum destino da passada está acordado. */
    static boolean hasAwakeDestination(List<Port> pass, long now) {
        for (int i = 0, n = pass.size(); i < n; i++) {
            if (pass.get(i).destinationBackoff.isAwake(now)) {
                return true;
            }
        }
        return false;
    }
}
