package io.github.matheusanbs.wirelessautomate.network;

import io.github.matheusanbs.wirelessautomate.block.RouterBlockEntity;
import io.github.matheusanbs.wirelessautomate.range.MachineConfig;
import io.github.matheusanbs.wirelessautomate.range.RangeRules;
import io.github.matheusanbs.wirelessautomate.range.RemoteMachine;
import io.github.matheusanbs.wirelessautomate.range.RemoteMachines;
import io.github.matheusanbs.wirelessautomate.range.RemoteSide;
import java.util.ArrayList;
import java.util.Arrays;
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
 * O amostrador dos Fluxos, um por {@link NetworkManager}: roda sempre, com ou sem alguém olhando, para a
 * aba Fluxos do Tablet abrir com as janelas cheias. Quem olha ({@link #watch}) só decide para quem o
 * Tablet manda o pacote.
 *
 * <p>O trabalho é espalhado em {@link #SAMPLE_TICKS} baldes de fase pela posição do nó, como no
 * Ressonador: a cada tick só os nós do balde da vez são lidos, e a volta pelos 20 baldes é o segundo dos
 * Fluxos. De cada nó lido:
 * <ul>
 *   <li>lê e zera os contadores das portas com rota ({@link Port#gave}, {@link Port#got}) e os soma na
 *       rede da porta pelo modo da face: o que as Extrai entregaram entra, o que as Insere receberam sai,
 *       e o Armazém (Ambos) tem o que recebeu e o que deu à parte;
 *   <li>conta os estados das portas: movendo (entregou ou recebeu desde a leitura anterior), origem
 *       vazia ou esperando destino (pelo sono e pelo {@link Port#waitsDestination}; uma origem sem
 *       destino ao alcance espera destino; só as Extrai esperam, porque o Armazém guarda o que não
 *       tem para onde ir) e destino cheio ({@link NodeProbe#isFull});
 *   <li>soma as faces com modo que o redstone desliga, por rede, e as faces com modo e sem rede (ou com
 *       uma rede que não existe mais), por tipo e por nó. Essas vêm da configuração (presa, e as remotas
 *       que viram porta pelos perfis e exceções), contada uma vez e guardada no nó ({@link NodeConfig})
 *       até a versão do roteador, a das redes ou a config mudar; não lê o mundo.
 * </ul>
 * Um nó em que nada mudou desde a leitura anterior não tem as portas lidas de novo: o que a leitura achou fica
 * no nó ({@link NodeFlows}) e é somado outra vez. Vale enquanto nenhum contador saiu do zero, nenhum
 * {@link Backoff} das portas mudou ({@link NodePorts#flowChanges}), nenhuma rota foi remontada e nenhuma porta
 * que dormia acordou pelo tempo; a leitura em que algo moveu nunca é repetida.
 * No fim da volta os números de cada rede × tipo entram numa {@link FlowWindow} cada. A primeira volta
 * do gerenciador só zera os contadores e reinicia as janelas, porque eles acumularam por um tempo que não
 * fecha com o segundo. O resultado ({@link #report()}) só é montado quando alguém pede, uma vez por volta:
 * sem ninguém olhando, a amostra não aloca. As redes que não apareceram numa volta saem no fim dela; um
 * tipo que deixou de ter porta numa rede recomeça a janela quando voltar.
 *
 * <p>Só a thread do servidor mexe.
 */
public final class FlowSampler {
    /** Ticks de uma volta pelos baldes: o segundo dos Fluxos. */
    public static final int SAMPLE_TICKS = 20;
    private static final ResourceType[] TYPES = ResourceType.values();
    private static final ResourceType[] LOADED = NetworkRoutes.TRANSFER_TYPES;
    private static final RelativeSide[] SIDES = RelativeSide.values();
    private static final RemoteSide[] REMOTE_SIDES = RemoteSide.values();
    /** Peso da média móvel do custo, por volta: cerca de cinco voltas de memória. */
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

    /** Os nós de cada balde de fase; a posição do nó na lista fica em {@link NodePorts#flowSlot}. */
    private final List<List<NodePorts>> phases = new ArrayList<>(SAMPLE_TICKS);
    private final Set<UUID> watchers = new HashSet<>();
    private final Map<UUID, NetworkFlows> flows = new HashMap<>();
    /** As faces sem rede por tipo, somadas na volta em curso e as da última volta fechada. */
    private final int[] withoutNetwork = new int[TYPES.length];
    private final int[] closedWithout = new int[TYPES.length];
    /** Os nós com alguma face sem rede, na volta em curso e na última fechada (trocadas no fim da volta). */
    private List<NodeWithoutNetwork> nodesWithout = new ArrayList<>();
    private List<NodeWithoutNetwork> closedNodes = new ArrayList<>();
    /** A volta em curso; marca as redes que apareceram nela. */
    private int cycle;
    /** A config mudou tantas vezes: a configuração guardada nos nós vale só com a mesma contagem. */
    private int configEpoch;
    /** As rotas foram remontadas tantas vezes: a leitura guardada nos nós vale só com a mesma contagem. */
    private int routesEpoch;
    /** Pular nós sem mudança ({@link NodeFlows}); só os GameTests desligam, para comparar. */
    private boolean skipUnchanged = true;
    /** Leituras de nó puladas por não ter mudança, desde que o gerenciador foi criado. */
    private long skipped;
    /** A primeira volta já zerou os contadores: as seguintes contam. */
    private boolean primed;
    /** O tick do fim da última volta; {@code -1} antes da primeira. */
    private long closedAt = -1;
    private Report report = Report.EMPTY;
    private long reportAt = -1;
    private long samples;
    private long cycleNanos;
    private long cyclePeakNanos;
    private long lastNanos;
    private long peakNanos;
    private double averageNanos;
    private long totalNanos;

    FlowSampler() {
        for (int i = 0; i < SAMPLE_TICKS; i++) {
            phases.add(new ArrayList<>());
        }
    }

    /** O nó entrou no gerenciador: passa a ser lido no balde da posição dele. */
    void add(NodePorts ports) {
        List<NodePorts> phase = phases.get(Math.floorMod(ports.node.getBlockPos().hashCode(), SAMPLE_TICKS));
        ports.flowPhase = phase;
        ports.flowSlot = phase.size();
        phase.add(ports);
    }

    /** O nó saiu do gerenciador. */
    void remove(NodePorts ports) {
        List<NodePorts> phase = ports.flowPhase;
        if (phase == null) {
            return;
        }
        NodePorts last = phase.remove(phase.size() - 1);
        if (last != ports) {
            phase.set(ports.flowSlot, last);
            last.flowSlot = ports.flowSlot;
        }
        ports.flowPhase = null;
        ports.flowSlot = -1;
    }

    /** A config recarregou: o raio e o limite das remotas podem ter mudado, e a contagem de cada nó é refeita. */
    void configChanged() {
        configEpoch++;
    }

    /** As rotas foram remontadas: as leituras guardadas nos nós deixam de valer. */
    void routesChanged() {
        routesEpoch++;
    }

    /** Para GameTests: liga ou desliga o pulo dos nós sem mudança. */
    public void skipUnchangedForTests(boolean skip) {
        skipUnchanged = skip;
    }

    /** Para GameTests: leituras de nó puladas por não ter mudança, desde que o gerenciador foi criado. */
    public long skippedForTests() {
        return skipped;
    }

    /** O jogador abriu a aba Fluxos. Devolve se ele ainda não estava olhando. */
    public boolean watch(UUID player) {
        return watchers.add(player);
    }

    /** O jogador fechou a aba Fluxos, o Tablet, ou saiu do servidor. Devolve se ele estava olhando. */
    public boolean unwatch(UUID player) {
        return watchers.remove(player);
    }

    public boolean isWatching(UUID player) {
        return watchers.contains(player);
    }

    /**
     * O resultado da última volta fechada, montado na primeira consulta depois dela e o mesmo para todos
     * até a próxima; {@link Report#EMPTY} antes da primeira.
     */
    public Report report() {
        if (reportAt != closedAt) {
            report = build();
            reportAt = closedAt;
        }
        return report;
    }

    /** O custo do amostrador, para o {@code /wa profile} e o bench. */
    public Cost cost() {
        return new Cost(watchers.size(), samples, averageNanos, lastNanos, peakNanos, totalNanos);
    }

    /** Chamado pelo gerenciador a cada tick: lê os nós do balde da vez e, no último balde, fecha a volta. */
    void tick(MinecraftServer server, long now) {
        int phase = (int) (now % SAMPLE_TICKS);
        List<NodePorts> bucket = phases.get(phase);
        boolean closing = phase == SAMPLE_TICKS - 1;
        if (bucket.isEmpty() && !closing) {
            return;
        }
        long started = System.nanoTime();
        if (!bucket.isEmpty()) {
            NetworkSavedData data = NetworkSavedData.get(server);
            for (int i = 0, n = bucket.size(); i < n; i++) {
                read(bucket.get(i), data, now);
            }
        }
        if (closing) {
            close(now);
        }
        long nanos = System.nanoTime() - started;
        cycleNanos += nanos;
        cyclePeakNanos = Math.max(cyclePeakNanos, nanos);
        if (closing) {
            samples++;
            lastNanos = cycleNanos;
            peakNanos = cyclePeakNanos;
            totalNanos += cycleNanos;
            averageNanos += (cycleNanos - averageNanos) * AVERAGE_WEIGHT;
            cycleNanos = 0;
            cyclePeakNanos = 0;
        }
    }

    private void read(NodePorts ports, NetworkSavedData data, long now) {
        NodeFlows last = ports.flowRead;
        if (skipUnchanged && last != null && last.still(ports, routesEpoch, now)) {
            skipped++;
            last.replay(this);
        } else {
            readPorts(ports, now);
        }
        addConfig(ports, data);
    }

    /** Lê as portas do nó e guarda o que achou ({@link NodeFlows}) para a próxima volta. */
    private void readPorts(NodePorts ports, long now) {
        NodeFlows found = ports.flowRead;
        if (found == null) {
            found = new NodeFlows();
            ports.flowRead = found;
        }
        found.begin();
        for (ResourceType type : LOADED) {
            Port[] active = ports.collected(type);
            if (active == null) {
                continue;
            }
            int t = type.ordinal();
            for (Port port : active) {
                NetworkRoutes routes = port.network;
                if (routes == null) {
                    continue;
                }
                NetworkFlows entry = entry(routes);
                entry.used[t] = true;
                int group = found.group(routes, t);
                if (port.stopped) {
                    // Parada pelo redstone: conta como parada por redstone, pela configuração, e não nos estados.
                    port.gaveRead += port.gave;
                    port.gotRead += port.got;
                    port.gave = 0;
                    port.got = 0;
                    continue;
                }
                if (port.source) {
                    readSource(entry, t, port, now, found, group);
                }
                if (port.destination) {
                    readDestination(entry, t, port, now, found, group);
                }
            }
        }
        found.end(ports, routesEpoch);
    }

    private static void readSource(NetworkFlows entry, int t, Port port, long now, NodeFlows found, int group) {
        int[] states = entry.states[t];
        long gave = port.gave;
        port.gave = 0;
        port.gaveRead += gave;
        entry.amounts[t][port.destination ? RELEASED : PRODUCED] += gave;
        if (gave > 0) {
            states[SOURCES_MOVING]++;
            found.moved();
        } else if (port.order == null) {
            // Sem destino ao alcance o laço não a visita: o que ela tiver espera destino.
            if (!port.destination) {
                states[SOURCES_WAITING]++;
                found.add(group, SOURCES_WAITING);
            }
        } else if (port.sourceBackoff.isSleeping(now)) {
            // Acordar pelo tempo não muda o Backoff: a leitura guardada só vale até aqui.
            found.until(port.sourceBackoff.wakeAt());
            if (!port.waitsDestination) {
                states[SOURCES_EMPTY]++;
                found.add(group, SOURCES_EMPTY);
            } else if (!port.destination) {
                states[SOURCES_WAITING]++;
                found.add(group, SOURCES_WAITING);
            }
        }
    }

    private static void readDestination(NetworkFlows entry, int t, Port port, long now, NodeFlows found, int group) {
        long got = port.got;
        port.got = 0;
        port.gotRead += got;
        entry.amounts[t][port.source ? STORED : CONSUMED] += got;
        if (got > 0) {
            entry.states[t][DESTINATIONS_MOVING]++;
            found.moved();
            return;
        }
        if (port.destinationBackoff.isSleeping(now)) {
            found.until(port.destinationBackoff.wakeAt());
        }
        if (NodeProbe.isFull(port, now)) {
            entry.states[t][DESTINATIONS_FULL]++;
            found.add(group, DESTINATIONS_FULL);
        }
    }

    /** A entrada da rede da porta nesta volta, guardada nas rotas para não procurar a cada porta. */
    private NetworkFlows entry(NetworkRoutes routes) {
        NetworkFlows entry = routes.flowEntry;
        if (entry == null || entry.removed) {
            entry = entry(routes.id);
            routes.flowEntry = entry;
        } else {
            touch(entry);
        }
        return entry;
    }

    /** A entrada da rede nesta volta, criada na primeira vez. */
    private NetworkFlows entry(UUID network) {
        NetworkFlows entry = flows.get(network);
        if (entry == null) {
            entry = new NetworkFlows(network);
            flows.put(network, entry);
        }
        touch(entry);
        return entry;
    }

    /** Zera os números da amostra na primeira vez que a rede aparece na volta. */
    private void touch(NetworkFlows entry) {
        if (entry.cycle != cycle) {
            entry.cycle = cycle;
            entry.clearSample();
        }
    }

    /** Soma na volta as faces paradas por redstone e as sem rede do nó, recontadas só se algo mudou. */
    private void addConfig(NodePorts ports, NetworkSavedData data) {
        RouterBlockEntity node = ports.node;
        NodeConfig config = ports.flowConfig;
        if (config == null) {
            config = new NodeConfig();
            ports.flowConfig = config;
        }
        if (!config.counted || config.changeVersion != node.changeVersion() || config.powered != node.powered()
                || config.dataVersion != data.version() || config.epoch != configEpoch) {
            config.recount(node, data);
            config.changeVersion = node.changeVersion();
            config.powered = node.powered();
            config.dataVersion = data.version();
            config.epoch = configEpoch;
            config.counted = true;
        }
        for (int i = 0; i < config.redstoneSize; i++) {
            entry(config.redstoneNetworks[i]).states[config.redstoneTypes[i]][REDSTONE] += config.redstoneCounts[i];
        }
        if (config.withoutNetwork != null) {
            for (int t = 0; t < TYPES.length; t++) {
                withoutNetwork[t] += config.withoutNetwork.counts().get(t);
            }
            nodesWithout.add(config.withoutNetwork);
        }
    }

    /**
     * Fecha a volta: soma os números nas janelas (ou as reinicia, na volta que só zerou), guarda os
     * estados e as faces sem rede da volta para o {@link #report()} e esquece as redes que não apareceram
     * e as janelas dos tipos sem porta nem face parada. Não aloca, fora a primeira janela de um tipo.
     */
    private void close(long now) {
        Iterator<NetworkFlows> it = flows.values().iterator();
        while (it.hasNext()) {
            NetworkFlows entry = it.next();
            if (entry.cycle != cycle) {
                entry.removed = true;
                it.remove();
                continue;
            }
            for (ResourceType type : LOADED) {
                int t = type.ordinal();
                int[] states = entry.states[t];
                if (!entry.used[t] && states[REDSTONE] == 0) {
                    entry.windows[t] = null;
                    entry.shown[t] = false;
                    continue;
                }
                FlowWindow[] windows = entry.windows[t];
                if (windows == null) {
                    windows = newWindows();
                    entry.windows[t] = windows;
                }
                long[] amounts = entry.amounts[t];
                for (int k = 0; k < AMOUNTS; k++) {
                    if (primed) {
                        windows[k].add(amounts[k]);
                        windows[k].roll();
                    } else {
                        windows[k].reset();
                    }
                }
                System.arraycopy(states, 0, entry.closedStates[t], 0, STATES);
                entry.shown[t] = true;
            }
        }
        System.arraycopy(withoutNetwork, 0, closedWithout, 0, withoutNetwork.length);
        Arrays.fill(withoutNetwork, 0);
        List<NodeWithoutNetwork> swap = closedNodes;
        closedNodes = nodesWithout;
        nodesWithout = swap;
        nodesWithout.clear();
        primed = true;
        closedAt = now;
        cycle++;
    }

    /** Monta o resultado da última volta fechada, das janelas e do que o fim da volta guardou. */
    private Report build() {
        if (closedAt < 0) {
            return Report.EMPTY;
        }
        List<FlowStats> rows = new ArrayList<>();
        for (NetworkFlows entry : flows.values()) {
            for (ResourceType type : LOADED) {
                int t = type.ordinal();
                if (!entry.shown[t]) {
                    continue;
                }
                FlowWindow[] windows = entry.windows[t];
                rows.add(new FlowStats(entry.id, type, windows[PRODUCED].total(), windows[CONSUMED].total(),
                        windows[STORED].total(), windows[RELEASED].total(), windows[PRODUCED].seconds(),
                        states(entry.closedStates[t])));
            }
        }
        List<Integer> noNetwork = new ArrayList<>(TYPES.length);
        for (int count : closedWithout) {
            noNetwork.add(count);
        }
        return new Report(closedAt, List.copyOf(rows), List.copyOf(noNetwork), List.copyOf(closedNodes));
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
     * O resultado de uma volta.
     *
     * @param sampledAt      o tick do servidor do fim da volta ({@code -1} em {@link #EMPTY})
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
     * @param samples      voltas fechadas desde que o gerenciador foi criado
     * @param averageNanos média móvel do tempo de uma volta (a soma dos 20 ticks), em ns
     * @param lastNanos    tempo da última volta, em ns
     * @param peakNanos    o tick mais caro da última volta, em ns
     * @param totalNanos   tempo de todas as voltas, em ns (o bench mede pela diferença)
     */
    public record Cost(int watchers, long samples, double averageNanos, long lastNanos, long peakNanos,
            long totalNanos) {
    }

    /**
     * Os números de uma rede, por ordinal do tipo: os da volta em curso, os estados da última volta fechada
     * e as janelas. As rotas da rede guardam a entrada ({@link NetworkRoutes#flowEntry}) até ela sair.
     */
    static final class NetworkFlows {
        final UUID id;
        final long[][] amounts = new long[TYPES.length][AMOUNTS];
        final int[][] states = new int[TYPES.length][STATES];
        final int[][] closedStates = new int[TYPES.length][STATES];
        final boolean[] used = new boolean[TYPES.length];
        /** O tipo entrou no resultado da última volta fechada. */
        final boolean[] shown = new boolean[TYPES.length];
        /** As janelas de cada tipo em uso, uma por número; {@code null} no tipo sem uso. */
        final FlowWindow[][] windows = new FlowWindow[TYPES.length][];
        int cycle = -1;
        /** A rede não apareceu numa volta e saiu do amostrador: as rotas que a guardam pegam outra. */
        boolean removed;

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

    /**
     * O que a última leitura das portas de um nó achou, por rede × tipo: os estados que dependem só delas (vazia,
     * esperando destino e cheio), para somar de novo na volta seguinte sem ler as portas. Vale com as mesmas
     * mudanças do nó ({@link NodePorts#flowChanges}) e a mesma contagem de remontagens, antes do primeiro
     * {@code wakeAt} das portas que dormiam, e nunca depois de uma leitura em que algo moveu (os contadores
     * voltam do zero, e mover de novo é uma mudança).
     */
    static final class NodeFlows {
        private static final NetworkRoutes[] NO_ROUTES = new NetworkRoutes[0];
        private static final int[] NO_INTS = new int[0];

        private NetworkRoutes[] routes = NO_ROUTES;
        private int[] types = NO_INTS;
        private int[] empty = NO_INTS;
        private int[] waiting = NO_INTS;
        private int[] full = NO_INTS;
        private int size;
        private boolean moved;
        private long validUntil;
        private int changes;
        private int routesEpoch;
        private boolean valid;

        void begin() {
            size = 0;
            moved = false;
            validUntil = Long.MAX_VALUE;
            valid = false;
        }

        /** O grupo da rede × tipo, criado na primeira porta dele nesta leitura. */
        int group(NetworkRoutes network, int type) {
            for (int i = 0; i < size; i++) {
                if (routes[i] == network && types[i] == type) {
                    return i;
                }
            }
            if (size == routes.length) {
                int grown = Math.max(2, size * 2);
                routes = Arrays.copyOf(routes, grown);
                types = Arrays.copyOf(types, grown);
                empty = Arrays.copyOf(empty, grown);
                waiting = Arrays.copyOf(waiting, grown);
                full = Arrays.copyOf(full, grown);
            }
            routes[size] = network;
            types[size] = type;
            empty[size] = 0;
            waiting[size] = 0;
            full[size] = 0;
            return size++;
        }

        void add(int group, int state) {
            switch (state) {
                case SOURCES_EMPTY -> empty[group]++;
                case SOURCES_WAITING -> waiting[group]++;
                default -> full[group]++;
            }
        }

        void moved() {
            moved = true;
        }

        /** Uma porta dormia até {@code wakeAt}: a leitura vale só antes. */
        void until(long wakeAt) {
            validUntil = Math.min(validUntil, wakeAt);
        }

        void end(NodePorts ports, int epoch) {
            changes = ports.flowChanges[0];
            routesEpoch = epoch;
            valid = !moved;
        }

        /** Nada mudou nas portas do nó desde a leitura: ela pode ser somada de novo. */
        boolean still(NodePorts ports, int epoch, long now) {
            return valid && changes == ports.flowChanges[0] && routesEpoch == epoch && now < validUntil;
        }

        /** Soma de novo o que a leitura achou, nas redes desta volta. */
        void replay(FlowSampler sampler) {
            for (int i = 0; i < size; i++) {
                NetworkFlows entry = sampler.entry(routes[i]);
                int t = types[i];
                entry.used[t] = true;
                int[] states = entry.states[t];
                states[SOURCES_EMPTY] += empty[i];
                states[SOURCES_WAITING] += waiting[i];
                states[DESTINATIONS_FULL] += full[i];
            }
        }
    }

    /**
     * A configuração de um nó que o amostrador soma a cada volta: as faces paradas por redstone por
     * rede × tipo e as sem rede por tipo. Recontada só quando a versão do roteador (faces, redes, máquinas), o
     * sinal de redstone, a das redes salvas ou a config mudam; um nó sem nada disso guarda só as versões.
     */
    static final class NodeConfig {
        private static final int[] NO_TYPES = new int[0];
        private static final UUID[] NO_NETWORKS = new UUID[0];

        int changeVersion;
        boolean powered;
        int dataVersion;
        int epoch;
        boolean counted;
        /** As faces sem rede do nó; {@code null} se não tem nenhuma. */
        @Nullable NodeWithoutNetwork withoutNetwork;
        UUID[] redstoneNetworks = NO_NETWORKS;
        int[] redstoneTypes = NO_TYPES;
        int[] redstoneCounts = NO_TYPES;
        int redstoneSize;
        private int[] without = NO_TYPES;
        private boolean anyWithout;

        void recount(RouterBlockEntity node, NetworkSavedData data) {
            redstoneSize = 0;
            anyWithout = false;
            if (without.length > 0) {
                Arrays.fill(without, 0);
            }
            boolean powered = node.powered();
            for (ResourceType type : LOADED) {
                for (RelativeSide side : SIDES) {
                    count(node.face(type, side), type, powered, data);
                }
            }
            if (node.rangeActive()) {
                RemoteMachines remotes = node.remoteMachines();
                List<RemoteMachine> machines = remotes.machines();
                if (!machines.isEmpty()) {
                    RangeRules.Reach[] reach = node.machineReach();
                    for (int i = 0, n = machines.size(); i < n; i++) {
                        if (reach[i] != RangeRules.Reach.ACTIVE) {
                            continue;
                        }
                        MachineConfig config = remotes.effective(machines.get(i));
                        for (ResourceType type : LOADED) {
                            for (RemoteSide side : REMOTE_SIDES) {
                                count(config.face(type, side), type, powered, data);
                            }
                        }
                    }
                }
            }
            withoutNetwork = anyWithout && node.getLevel() != null
                    ? new NodeWithoutNetwork(NodeIndex.NodeKey.of(node.getLevel(), node.getBlockPos()), list(without))
                    : null;
        }

        private void count(FaceConfig face, ResourceType type, boolean powered, NetworkSavedData data) {
            if (face.mode() == PortMode.NONE) {
                return;
            }
            UUID network = face.network();
            if (network == null || data.network(network) == null) {
                if (without.length == 0) {
                    without = new int[TYPES.length];
                }
                without[type.ordinal()]++;
                anyWithout = true;
            } else if (!face.redstone().allows(powered)) {
                addRedstone(network, type.ordinal());
            }
        }

        private void addRedstone(UUID network, int type) {
            for (int i = 0; i < redstoneSize; i++) {
                if (redstoneTypes[i] == type && redstoneNetworks[i].equals(network)) {
                    redstoneCounts[i]++;
                    return;
                }
            }
            if (redstoneSize == redstoneNetworks.length) {
                int grown = Math.max(4, redstoneSize * 2);
                redstoneNetworks = Arrays.copyOf(redstoneNetworks, grown);
                redstoneTypes = Arrays.copyOf(redstoneTypes, grown);
                redstoneCounts = Arrays.copyOf(redstoneCounts, grown);
            }
            redstoneNetworks[redstoneSize] = network;
            redstoneTypes[redstoneSize] = type;
            redstoneCounts[redstoneSize] = 1;
            redstoneSize++;
        }

        private static List<Integer> list(int[] counts) {
            List<Integer> list = new ArrayList<>(counts.length);
            for (int count : counts) {
                list.add(count);
            }
            return List.copyOf(list);
        }
    }
}
