package io.github.matheusanbs.wirelessautomate.network;

import io.github.matheusanbs.wirelessautomate.block.RouterBlockEntity;
import io.github.matheusanbs.wirelessautomate.range.MachineConfig;
import io.github.matheusanbs.wirelessautomate.range.RangeRules;
import io.github.matheusanbs.wirelessautomate.range.RemoteMachine;
import io.github.matheusanbs.wirelessautomate.range.RemoteMachines;
import io.github.matheusanbs.wirelessautomate.range.RemoteSide;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import org.jetbrains.annotations.Nullable;

/**
 * O amostrador dos Fluxos, um por {@link NetworkManager}: só trabalha enquanto alguém olha a aba
 * Fluxos do Tablet ({@link #watch}), e aí uma vez a cada {@link #SAMPLE_TICKS} ticks. Sem ninguém
 * olhando, o tick do gerenciador custa um {@code if} e os contadores das portas só crescem.
 *
 * <p>A cada amostra, para cada rede com rotas e cada tipo carregado:
 * <ul>
 *   <li>lê e zera os contadores das portas ({@link Port#gave}, {@link Port#got}) e os soma pelo modo
 *       da face: o que as Extrai entregaram entra, o que as Insere receberam sai, e o Armazém (Ambos)
 *       tem o que recebeu e o que deu à parte;
 *   <li>conta os estados das portas: movendo (entregou ou recebeu desde a amostra anterior), origem
 *       vazia ou esperando destino (pelo sono e pelo {@link Port#waitsDestination}; uma origem sem
 *       destino ao alcance espera destino) e destino cheio ({@link NodeProbe#isFull}).
 * </ul>
 * Depois, da configuração dos nós carregados que o gerenciador conhece (presa, e as remotas que viram
 * porta pelos perfis e exceções), conta as faces com modo que o redstone desliga, por rede, e as faces
 * com modo e sem rede (ou com uma rede que não existe mais), por tipo e, só dos nós que têm alguma, por nó
 * (o Tablet de quem não é operador só conta os nós que o jogador vê). Não lê o mundo: a configuração
 * está no roteador.
 *
 * <p>A primeira amostra depois de ficar sem ninguém olhando só zera os contadores e reinicia as
 * janelas, porque eles acumularam por um tempo desconhecido. Os números de cada rede × tipo vão para
 * uma {@link FlowWindow} cada, e o resultado da amostra fica no {@link #report()}, imutável. As redes
 * que sumiram do gerenciador e da configuração saem na amostra seguinte; um tipo que deixou de ter
 * porta numa rede recomeça a janela quando voltar.
 *
 * <p>Só a thread do servidor mexe.
 */
public final class FlowSampler {
    /** Ticks entre duas amostras: o segundo dos Fluxos. */
    static final int SAMPLE_TICKS = 20;
    private static final ResourceType[] TYPES = ResourceType.values();
    private static final ResourceType[] LOADED = NetworkRoutes.TRANSFER_TYPES;
    private static final RelativeSide[] SIDES = RelativeSide.values();
    private static final RemoteSide[] REMOTE_SIDES = RemoteSide.values();
    /** Peso da média móvel do custo, por amostra: cerca de cinco amostras de memória. */
    private static final double AVERAGE_WEIGHT = 0.2;

    // Índices dos números de uma rede × tipo.
    private static final int PRODUCED = 0;
    private static final int CONSUMED = 1;
    private static final int STORED = 2;
    private static final int RELEASED = 3;
    private static final int AMOUNTS = 4;

    // Índices dos estados de uma rede × tipo.
    private static final int SOURCES_MOVING = 0;
    private static final int SOURCES_EMPTY = 1;
    private static final int SOURCES_WAITING = 2;
    private static final int DESTINATIONS_MOVING = 3;
    private static final int DESTINATIONS_FULL = 4;
    private static final int REDSTONE = 5;
    private static final int STATES = 6;

    /** As redes do gerenciador (a lista dele, só lida aqui). */
    private final List<NetworkRoutes> networks;
    /** Os nós carregados do gerenciador (a visão dele, só lida aqui). */
    private final Collection<NodePorts> nodes;
    private final Set<UUID> watchers = new HashSet<>();
    private final Map<UUID, NetworkFlows> flows = new HashMap<>();
    private final int[] withoutNetwork = new int[TYPES.length];
    /** As faces sem rede do nó contado agora, por tipo. */
    private final int[] nodeWithout = new int[TYPES.length];
    /** Os nós com alguma face sem rede nesta amostra. */
    private final List<NodeWithoutNetwork> nodesWithout = new ArrayList<>();
    /** A última amostra zerou os contadores: a próxima conta. Falso enquanto ninguém olha. */
    private boolean primed;
    private int stamp;
    private Report report = Report.EMPTY;
    private long samples;
    private long lastNanos;
    private double averageNanos;

    FlowSampler(List<NetworkRoutes> networks, Collection<NodePorts> nodes) {
        this.networks = networks;
        this.nodes = nodes;
    }

    /**
     * O jogador abriu a aba Fluxos: o amostrador passa a rodar. Se ninguém olhava, a próxima amostra
     * só zera. Devolve se ele ainda não estava olhando.
     */
    public boolean watch(UUID player) {
        if (watchers.isEmpty()) {
            primed = false;
        }
        return watchers.add(player);
    }

    /**
     * O jogador fechou a aba Fluxos, o Tablet, ou saiu do servidor. Com o último, o amostrador para e
     * esquece as janelas e o resultado. Devolve se ele estava olhando.
     */
    public boolean unwatch(UUID player) {
        boolean removed = watchers.remove(player);
        if (removed && watchers.isEmpty()) {
            primed = false;
            flows.clear();
            report = Report.EMPTY;
        }
        return removed;
    }

    public boolean isWatching(UUID player) {
        return watchers.contains(player);
    }

    /** O resultado da última amostra; {@link Report#EMPTY} sem ninguém olhando ou antes da primeira. */
    public Report report() {
        return report;
    }

    /** O custo do amostrador, para o {@code /wa profile} e o bench. */
    public Cost cost() {
        return new Cost(watchers.size(), samples, averageNanos, lastNanos);
    }

    /** Chamado pelo gerenciador a cada tick: só amostra com alguém olhando, a cada {@link #SAMPLE_TICKS} ticks. */
    void tick(MinecraftServer server, long now) {
        if (watchers.isEmpty() || now % SAMPLE_TICKS != 0) {
            return;
        }
        long started = System.nanoTime();
        sample(NetworkSavedData.get(server), now);
        long nanos = System.nanoTime() - started;
        samples++;
        lastNanos = nanos;
        averageNanos += (nanos - averageNanos) * AVERAGE_WEIGHT;
    }

    private void sample(NetworkSavedData data, long now) {
        boolean counting = primed;
        primed = true;
        int sampleStamp = ++stamp;
        for (int i = 0, n = networks.size(); i < n; i++) {
            NetworkRoutes routes = networks.get(i);
            NetworkFlows entry = entry(routes.id, sampleStamp);
            for (ResourceType type : LOADED) {
                int t = type.ordinal();
                readSources(entry, t, routes.sourcesOf[t], counting, now);
                readDestinations(entry, t, routes.destinationsOf[t], counting, now);
            }
        }
        Arrays.fill(withoutNetwork, 0);
        nodesWithout.clear();
        for (NodePorts ports : nodes) {
            countConfig(ports.node, data, sampleStamp);
        }
        report = build(counting, sampleStamp, now);
    }

    /** A entrada da rede nesta amostra, criada na primeira vez e zerada na primeira consulta da amostra. */
    private NetworkFlows entry(UUID network, int sampleStamp) {
        NetworkFlows entry = flows.get(network);
        if (entry == null) {
            entry = new NetworkFlows(network);
            flows.put(network, entry);
        }
        if (entry.stamp != sampleStamp) {
            entry.stamp = sampleStamp;
            entry.clearSample();
        }
        return entry;
    }

    private static void readSources(NetworkFlows entry, int t, List<Port> sources, boolean counting, long now) {
        if (sources.isEmpty()) {
            return;
        }
        entry.used[t] = true;
        long[] amounts = entry.amounts[t];
        int[] states = entry.states[t];
        for (int i = 0, n = sources.size(); i < n; i++) {
            Port port = sources.get(i);
            long gave = port.gave;
            port.gave = 0;
            if (counting) {
                amounts[port.destination ? RELEASED : PRODUCED] += gave;
            }
            if (counting && gave > 0) {
                states[SOURCES_MOVING]++;
            } else if (port.order == null) {
                // Sem destino ao alcance o laço não a visita: o que ela tiver espera destino.
                states[SOURCES_WAITING]++;
            } else if (port.sourceBackoff.isSleeping(now)) {
                states[port.waitsDestination ? SOURCES_WAITING : SOURCES_EMPTY]++;
            }
        }
    }

    private static void readDestinations(NetworkFlows entry, int t, List<Port> destinations, boolean counting,
            long now) {
        if (destinations.isEmpty()) {
            return;
        }
        entry.used[t] = true;
        long[] amounts = entry.amounts[t];
        int[] states = entry.states[t];
        for (int i = 0, n = destinations.size(); i < n; i++) {
            Port port = destinations.get(i);
            long got = port.got;
            port.got = 0;
            if (counting) {
                amounts[port.source ? STORED : CONSUMED] += got;
            }
            if (counting && got > 0) {
                states[DESTINATIONS_MOVING]++;
            } else if (NodeProbe.isFull(port, now)) {
                states[DESTINATIONS_FULL]++;
            }
        }
    }

    /**
     * Conta as faces paradas por redstone e as sem rede do nó: as seis da presa e as das remotas que
     * viram porta (dentro do raio e do limite, com o upgrade valendo), pela configuração que vale para
     * cada uma (exceção ou perfil do grupo).
     */
    private void countConfig(RouterBlockEntity node, NetworkSavedData data, int sampleStamp) {
        Arrays.fill(nodeWithout, 0);
        countFaces(node, data, sampleStamp);
        boolean any = false;
        for (int count : nodeWithout) {
            any |= count > 0;
        }
        if (any && node.getLevel() != null) {
            List<Integer> counts = new ArrayList<>(TYPES.length);
            for (int count : nodeWithout) {
                counts.add(count);
            }
            nodesWithout.add(new NodeWithoutNetwork(NodeIndex.NodeKey.of(node.getLevel(), node.getBlockPos()),
                    List.copyOf(counts)));
        }
    }

    private void countFaces(RouterBlockEntity node, NetworkSavedData data, int sampleStamp) {
        boolean powered = node.powered();
        for (ResourceType type : LOADED) {
            for (RelativeSide side : SIDES) {
                countFace(node.face(type, side), type, powered, data, sampleStamp);
            }
        }
        if (!node.rangeActive()) {
            return;
        }
        RemoteMachines remotes = node.remoteMachines();
        List<RemoteMachine> machines = remotes.machines();
        if (machines.isEmpty()) {
            return;
        }
        RangeRules.Reach[] reach = node.machineReach();
        for (int i = 0, n = machines.size(); i < n; i++) {
            if (reach[i] != RangeRules.Reach.ACTIVE) {
                continue;
            }
            MachineConfig config = remotes.effective(machines.get(i));
            for (ResourceType type : LOADED) {
                for (RemoteSide side : REMOTE_SIDES) {
                    countFace(config.face(type, side), type, powered, data, sampleStamp);
                }
            }
        }
    }

    private void countFace(FaceConfig face, ResourceType type, boolean powered, NetworkSavedData data,
            int sampleStamp) {
        if (face.mode() == PortMode.NONE) {
            return;
        }
        UUID network = face.network();
        if (network == null || data.network(network) == null) {
            withoutNetwork[type.ordinal()]++;
            nodeWithout[type.ordinal()]++;
        } else if (!face.redstone().allows(powered)) {
            entry(network, sampleStamp).states[type.ordinal()][REDSTONE]++;
        }
    }

    /**
     * Fecha a amostra: soma os números nas janelas (ou as reinicia, na amostra que só zerou), esquece
     * as redes que não apareceram e os tipos sem porta nem face parada, e monta o resultado.
     */
    private Report build(boolean counting, int sampleStamp, long now) {
        List<FlowStats> rows = new ArrayList<>();
        Iterator<NetworkFlows> it = flows.values().iterator();
        while (it.hasNext()) {
            NetworkFlows entry = it.next();
            if (entry.stamp != sampleStamp) {
                it.remove();
                continue;
            }
            for (ResourceType type : LOADED) {
                int t = type.ordinal();
                int[] states = entry.states[t];
                if (!entry.used[t] && states[REDSTONE] == 0) {
                    entry.windows[t] = null;
                    continue;
                }
                FlowWindow[] windows = entry.windows[t];
                if (windows == null) {
                    windows = newWindows();
                    entry.windows[t] = windows;
                }
                long[] amounts = entry.amounts[t];
                for (int k = 0; k < AMOUNTS; k++) {
                    if (counting) {
                        windows[k].add(amounts[k]);
                        windows[k].roll();
                    } else {
                        windows[k].reset();
                    }
                }
                rows.add(new FlowStats(entry.id, type, windows[PRODUCED].total(), windows[CONSUMED].total(),
                        windows[STORED].total(), windows[RELEASED].total(), windows[PRODUCED].seconds(),
                        states(states)));
            }
        }
        List<Integer> noNetwork = new ArrayList<>(TYPES.length);
        for (int count : withoutNetwork) {
            noNetwork.add(count);
        }
        return new Report(now, List.copyOf(rows), List.copyOf(noNetwork), List.copyOf(nodesWithout));
    }

    private static FlowStats.States states(int[] states) {
        boolean none = true;
        for (int count : states) {
            none &= count == 0;
        }
        return none ? FlowStats.States.NONE : new FlowStats.States(states[SOURCES_MOVING], states[SOURCES_EMPTY],
                states[SOURCES_WAITING], states[DESTINATIONS_MOVING], states[DESTINATIONS_FULL], states[REDSTONE]);
    }

    private static FlowWindow[] newWindows() {
        FlowWindow[] windows = new FlowWindow[AMOUNTS];
        for (int k = 0; k < AMOUNTS; k++) {
            windows[k] = new FlowWindow();
        }
        return windows;
    }

    /**
     * O resultado de uma amostra.
     *
     * @param sampledAt      o tick do servidor da amostra ({@code -1} em {@link #EMPTY})
     * @param flows          os Fluxos de cada rede × tipo em uso (com porta nas rotas ou face parada por
     *                       redstone), sem ordem definida; as redes pausadas só aparecem com faces
     *                       paradas por redstone, porque não têm rotas
     * @param withoutNetwork as faces com modo e sem rede (ou numa rede apagada), pelo ordinal do tipo, de
     *                       todos os nós carregados
     * @param nodesWithout   as mesmas faces por nó, só dos nós que têm alguma: quem não é operador só conta as
     *                       dos nós que vê
     */
    public record Report(long sampledAt, List<FlowStats> flows, List<Integer> withoutNetwork,
            List<NodeWithoutNetwork> nodesWithout) {
        public static final Report EMPTY = new Report(-1, List.of(), zeros(), List.of());

        private static List<Integer> zeros() {
            List<Integer> zeros = new ArrayList<>(TYPES.length);
            for (int i = 0; i < TYPES.length; i++) {
                zeros.add(0);
            }
            return List.copyOf(zeros);
        }

        /** As faces com modo e sem rede do tipo, nos nós carregados. */
        public int withoutNetwork(ResourceType type) {
            return withoutNetwork.get(type.ordinal());
        }

        /** Os Fluxos da rede no tipo, ou {@code null} se o tipo não está em uso nela. */
        public @Nullable FlowStats get(UUID network, ResourceType type) {
            for (FlowStats stats : flows) {
                if (stats.type() == type && stats.network().equals(network)) {
                    return stats;
                }
            }
            return null;
        }
    }

    /** As faces com modo e sem rede de um nó carregado, pelo ordinal do tipo. */
    public record NodeWithoutNetwork(NodeIndex.NodeKey node, List<Integer> counts) {
    }

    /**
     * O custo do amostrador.
     *
     * @param watchers     jogadores com a aba Fluxos aberta
     * @param samples      amostras desde que o gerenciador foi criado
     * @param averageNanos média móvel do tempo de uma amostra, em ns
     * @param lastNanos    tempo da última amostra, em ns
     */
    public record Cost(int watchers, long samples, double averageNanos, long lastNanos) {
    }

    /** Os números de uma rede: os da amostra em curso e as janelas, por ordinal do tipo. */
    private static final class NetworkFlows {
        final UUID id;
        final long[][] amounts = new long[TYPES.length][AMOUNTS];
        final int[][] states = new int[TYPES.length][STATES];
        final boolean[] used = new boolean[TYPES.length];
        /** As janelas de cada tipo em uso, uma por número; {@code null} no tipo sem uso. */
        final FlowWindow[][] windows = new FlowWindow[TYPES.length][];
        int stamp;

        NetworkFlows(UUID id) {
            this.id = id;
        }

        void clearSample() {
            for (int t = 0; t < TYPES.length; t++) {
                Arrays.fill(amounts[t], 0);
                Arrays.fill(states[t], 0);
                used[t] = false;
            }
        }
    }
}
