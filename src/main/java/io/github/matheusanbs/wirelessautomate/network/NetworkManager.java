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
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * Gerenciador central: um por servidor, processa todas as redes dentro do orçamento de tempo.
 * Os nós não fazem tick (ver docs/especificacao.md, "Arquitetura de performance").
 *
 * <p>Ciclo de cada tick:
 * <ol>
 *   <li>remonta os tipos sujos das redes ({@link NetworkRoutes#rebuild}); {@link #nodeChanged},
 *       {@link #addNode} e {@link #removeNode} só marcam, nunca remontam na hora;
 *   <li>percorre a lista achatada de origens de todas as redes a partir do cursor salvo, uma visita
 *       por origem acordada, e para quando o orçamento acaba, guardando o cursor para o tick seguinte.
 *       Se a volta inteira coube, o tick seguinte começa depois da última origem que moveu algo
 *       ({@link SourceCursor}): sem isso, as primeiras origens da lista levariam sempre todo o espaço
 *       que abre nos destinos.
 *       O relógio é lido uma vez por origem, e serve tanto ao orçamento quanto ao profiler;
 *   <li>com a volta inteira feita e orçamento sobrando, dá voltas extras só nas origens que pararam
 *       por um teto da visita com saldo no balde ({@link #MORE}), enquanto a visita seguinte de cada
 *       uma (estimada pela anterior) couber no que resta: assim o tier
 *       sem limite (e o Elite, acima de 32 pilhas por tick) é limitado pelo orçamento e não pelo teto
 *       de tentativas, sem passar na frente de quem ainda não teve a primeira visita do tick.
 * </ol>
 *
 * <p>Cada tipo de recurso (aba) de um nó entra numa rede própria ({@link RouterBlockEntity#networkId(ResourceType)});
 * o nó é membro de toda rede em que algum tipo dele está, e a montagem de um tipo numa rede só
 * considera os membros cujo tipo está nela. A sujeira também é por tipo: mudar a aba Itens remonta
 * só os itens da rede.
 *
 * <p>Sono e despertar: em volta de cada visita o gerenciador anota a origem visitada. Assim, quando
 * a máquina de um destino avisa mudança porque nós acabamos de inserir nela ({@link #wake}), só as
 * portas do próprio nó acordam, e não as origens que entregam ali (ganhar recursos não abre espaço).
 * Quando a origem dorme, as transferências gravam o motivo ({@link SourceSleep},
 * {@link Port#waitsDestination}): espera destino (todos dormiam, ou ofereceu algo e foi recusada) ou
 * está vazia. Mudanças na máquina de um destino só acordam as que esperam destino.
 *
 * <p>O tempo das portas (balde, sono) é o {@code getTickCount()} do servidor.
 */
public final class NetworkManager {
    /** Teto do sono de origens e destinos, em ticks. */
    static final int MAX_SLEEP_TICKS = 100;
    private static final ResourceType[] TYPES = ResourceType.values();
    /** Máscara com todos os tipos, para {@link #nodeChanged(RouterBlockEntity, int)}. */
    public static final int ALL_TYPES = (1 << TYPES.length) - 1;
    private static final int TICKS_PER_SECOND = 20;
    /** Resultado de uma visita: a origem moveu algo. */
    static final int MOVED = 1;
    /** Resultado de uma visita: parou num teto da visita com saldo no balde e pode mover mais neste tick. */
    static final int MORE = 2;

    private static NetworkManager instance;
    private static volatile boolean configChanged;

    // Visita em andamento; o laço roda numa thread só (a do servidor). Fora de uma visita, visitNode é null.
    private static @Nullable RouterBlockEntity visitNode;
    private static @Nullable Level visitLevel;
    private static BlockPos visitMachine = BlockPos.ZERO;

    private final Map<RouterBlockEntity, NodePorts> nodes = new Reference2ObjectOpenHashMap<>();
    private final Map<UUID, NetworkRoutes> networks = new HashMap<>();
    /** As mesmas redes de {@link #networks}, para iterar sem alocar. */
    private final List<NetworkRoutes> networkList = new ArrayList<>();
    /** Origens com destino de todas as redes, na ordem da visita. */
    private final List<Port> sources = new ArrayList<>();
    private final List<Port> scratch = new ArrayList<>();
    /** Origens que pararam num teto da visita com saldo, para as voltas extras do tick. */
    private final List<Port> hungry = new ArrayList<>();
    private final TickBudget budget = new TickBudget(500_000L);
    private int cursor;
    private boolean dirty;
    /** Redes salvas do servidor atual e a versão delas já conferida ({@link #checkNetworks}). */
    private @Nullable MinecraftServer savedServer;
    private @Nullable NetworkSavedData savedData;
    private int savedVersion;
    /** Entrou rede nova no gerenciador: a existência dela ainda não foi conferida. */
    private boolean networksAdded = true;
    // Contadores cumulativos (benchmark e diagnóstico): só somam; quem lê faz a diferença entre leituras.
    private long visitCount;
    private long exhaustedTicks;
    private long rebuildCount;
    private long rebuildNanos;

    public static NetworkManager get() {
        if (instance == null) {
            instance = new NetworkManager();
        }
        return instance;
    }

    /** Descarta o estado ao parar o servidor, para o próximo mundo começar limpo. */
    public static void reset() {
        instance = null;
        visitNode = null;
        visitLevel = null;
    }

    /** A config dos tiers mudou: todas as redes serão remontadas no próximo tick. Seguro de qualquer thread. */
    public static void configChanged() {
        configChanged = true;
    }

    /** Bit do tipo nas máscaras de {@link #nodeChanged(RouterBlockEntity, int)}. */
    public static int typeBit(ResourceType type) {
        return 1 << type.ordinal();
    }

    public void addNode(RouterBlockEntity node) {
        if (nodes.containsKey(node)) {
            nodeChanged(node);
            return;
        }
        NodePorts ports = new NodePorts(node);
        nodes.put(node, ports);
        for (ResourceType type : TYPES) {
            move(ports, type, node.networkId(type));
        }
    }

    public void removeNode(RouterBlockEntity node) {
        NodePorts ports = nodes.remove(node);
        if (ports != null) {
            for (ResourceType type : TYPES) {
                move(ports, type, null);
            }
        }
    }

    public boolean contains(RouterBlockEntity node) {
        return nodes.containsKey(node);
    }

    /** Como {@link #nodeChanged(RouterBlockEntity, int)} para todos os tipos. */
    public void nodeChanged(RouterBlockEntity node) {
        nodeChanged(node, ALL_TYPES);
    }

    /**
     * A rede ou a configuração de faces dos tipos de {@code types} (máscara de {@link #typeBit}) do nó
     * mudou: marca esses tipos nas redes afetadas para remontar no próximo tick. Compara tipo a tipo:
     * um tipo que trocou de rede sai da antiga e entra na nova (as duas sujam); os outros só sujam a
     * rede em que já estão. Os tipos fora da máscara não são relidos nem remontados.
     */
    public void nodeChanged(RouterBlockEntity node, int types) {
        NodePorts ports = nodes.get(node);
        if (ports == null || types == 0) {
            return;
        }
        ports.invalidate(types);
        for (ResourceType type : TYPES) {
            if ((types & typeBit(type)) == 0) {
                continue;
            }
            UUID network = node.networkId(type);
            if (Objects.equals(network, ports.network(type))) {
                markDirty(network, type);
            } else {
                move(ports, type, network);
            }
        }
    }

    /**
     * A máquina do nó avisou mudança: acorda as portas dele e as origens que esperam destino e
     * entregam nos destinos dele. Se o aviso vem de uma entrega nossa (durante uma visita, numa
     * máquina que não é a da origem visitada), só as portas do nó acordam.
     */
    public void wake(RouterBlockEntity node) {
        NodePorts ports = nodes.get(node);
        if (ports != null) {
            ports.wake(!deliveringInto(node));
        }
    }

    /** A visita em andamento está inserindo na máquina do nó (outra máquina que a da origem). */
    private static boolean deliveringInto(RouterBlockEntity node) {
        RouterBlockEntity source = visitNode;
        if (source == null || node == source) {
            return false;
        }
        return node.getLevel() != visitLevel || !node.machinePos().equals(visitMachine);
    }

    /**
     * A capability de uma face da máquina do nó foi invalidada: acorda só a porta dessa face e tipo e
     * as origens que esperam por ela. Não remonta: as rotas não dependem de capability.
     */
    public void capabilityChanged(RouterBlockEntity node, ResourceType type, Direction face) {
        NodePorts ports = nodes.get(node);
        if (ports != null) {
            ports.capabilityChanged(type, face);
        }
    }

    /** Portas do nó carregado, para o Tablet ler o sono dos destinos ({@link NodeProbe}); não as altera. */
    @Nullable NodePorts ports(RouterBlockEntity node) {
        return nodes.get(node);
    }

    public int nodeCount() {
        return nodes.size();
    }

    public TickBudget budget() {
        return budget;
    }

    /** Visitas a origens desde que o gerenciador foi criado. */
    public long visitCount() {
        return visitCount;
    }

    /** Ticks em que o orçamento acabou antes da volta inteira (o resto ficou para o tick seguinte). */
    public long exhaustedTicks() {
        return exhaustedTicks;
    }

    /** Remontagens de rotas (ticks com alguma rede suja) desde que o gerenciador foi criado. */
    public long rebuildCount() {
        return rebuildCount;
    }

    /** Tempo somado das remontagens, em ns. */
    public long rebuildNanos() {
        return rebuildNanos;
    }

    /** Para GameTests e diagnóstico: o tipo da rede está marcado para remontar no próximo tick. */
    public boolean isDirty(UUID network, ResourceType type) {
        NetworkRoutes routes = networks.get(network);
        return routes != null && (routes.dirtyTypes & typeBit(type)) != 0;
    }

    /**
     * Para GameTests e diagnóstico: o intervalo do próximo sono da origem na face (absoluta) da
     * máquina, que dobra a cada sono e volta a 1 quando algo a acorda; -1 se a porta não existe.
     */
    public int sourceSleepInterval(RouterBlockEntity node, ResourceType type, Direction face) {
        NodePorts ports = nodes.get(node);
        Port port = ports == null ? null : ports.peek(type, face);
        return port == null ? -1 : port.sourceBackoff.nextIntervalTicks();
    }

    /**
     * Retrato das redes que existem e têm nós carregados, para o profiler. Os nós de uma rede são
     * os que têm algum tipo nela: um roteador com Itens na rede A e Energia na B conta nas duas.
     */
    public List<NetworkStats> stats(MinecraftServer server) {
        long now = server.getTickCount();
        List<NetworkStats> result = new ArrayList<>();
        for (NetworkRoutes network : networkList) {
            if (!network.exists || network.members.isEmpty()) {
                continue;
            }
            int sourceCount = 0;
            int destinationCount = 0;
            int sourcesSleeping = 0;
            int destinationsSleeping = 0;
            int destinationsFull = 0;
            for (ResourceType type : TYPES) {
                List<Port> typeSources = network.sourcesOf[type.ordinal()];
                List<Port> typeDestinations = network.destinationsOf[type.ordinal()];
                sourceCount += typeSources.size();
                destinationCount += typeDestinations.size();
                for (Port port : typeSources) {
                    if (port.sourceBackoff.isSleeping(now)) {
                        sourcesSleeping++;
                    }
                }
                for (Port port : typeDestinations) {
                    if (port.destinationBackoff.isSleeping(now)) {
                        destinationsSleeping++;
                        if (NodeProbe.isFull(port, now)) {
                            destinationsFull++;
                        }
                    }
                }
            }
            result.add(new NetworkStats(network.id, network.members.size(), network.averageNanos,
                    network.lastNanos, network.opsLastSecond,
                    sourceCount - sourcesSleeping, sourcesSleeping,
                    destinationCount - destinationsSleeping, destinationsSleeping, destinationsFull));
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
            long started = System.nanoTime();
            rebuild();
            rebuildCount++;
            rebuildNanos += System.nanoTime() - started;
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
        int start = cursor < count ? cursor : 0;
        int index = start;
        int lastMover = -1;
        hungry.clear();
        for (int step = 0; step < count; step++) {
            Port source = sources.get(index);
            if (source.order != null && source.sourceBackoff.isAwake(now)) {
                long time = System.nanoTime();
                // Pelo menos uma visita por tick, mesmo que a montagem tenha gastado o orçamento.
                if (visited > 0 && !budget.hasTime(time)) {
                    exhaustedTicks++;
                    if (timed != null) {
                        timed.tickNanos += time - mark;
                    }
                    cursor = index;
                    hungry.clear();
                    return time;
                }
                if (timed != null) {
                    timed.tickNanos += time - mark;
                }
                timed = source.network;
                mark = time;
                visited++;
                visitCount++;
                int result = visit(source, now);
                if ((result & MOVED) != 0) {
                    lastMover = index;
                }
                if ((result & MORE) != 0) {
                    source.visitNanos = System.nanoTime() - time;
                    hungry.add(source);
                }
            }
            index = index + 1 == count ? 0 : index + 1;
        }
        cursor = SourceCursor.next(start, lastMover, count);
        // Voltas extras, na mesma ordem, só com quem ainda tem o que mover e saldo para isso, e só
        // se a visita (estimada pela última da origem) couber no que resta do orçamento.
        while (!hungry.isEmpty()) {
            int kept = 0;
            for (int i = 0, n = hungry.size(); i < n; i++) {
                Port source = hungry.get(i);
                long time = System.nanoTime();
                if (!budget.fits(time, source.visitNanos)) {
                    continue;
                }
                if (timed != null) {
                    timed.tickNanos += time - mark;
                }
                timed = source.network;
                mark = time;
                visitCount++;
                if ((visit(source, now) & MORE) != 0) {
                    source.visitNanos = System.nanoTime() - time;
                    hungry.set(kept++, source);
                }
            }
            hungry.subList(kept, hungry.size()).clear();
        }
        long time = System.nanoTime();
        if (timed != null) {
            timed.tickNanos += time - mark;
        }
        return time;
    }

    /**
     * Uma visita, com a origem anotada em volta (ver a classe); devolve {@link #MOVED} e
     * {@link #MORE} combinados. Se moveu ou dormiu, a marca de "ofereceu" ({@link Port#offered}) volta
     * a zero: ela vale para a volta sem mover, que pode durar várias visitas.
     */
    private int visit(Port source, long now) {
        visitNode = source.node;
        visitLevel = source.node.getLevel();
        visitMachine = source.machinePos;
        int result;
        try {
            // Sem default: um tipo novo no ResourceType não compila até ganhar o seu caso aqui.
            result = switch (source.type) {
                case ITEM -> ItemTransfer.move(source, now);
                case FLUID -> FluidTransfer.move(source, now) ? MOVED : 0;
                case ENERGY -> EnergyTransfer.move(source, now) ? MOVED : 0;
                case CHEMICAL -> Chemicals.move(source, now) ? MOVED : 0;
            };
        } finally {
            visitNode = null;
            visitLevel = null;
        }
        if ((result & MOVED) != 0 || source.sourceBackoff.isSleeping(now)) {
            source.offered = false;
        }
        return result;
    }

    /**
     * Redes criadas, removidas, pausadas ou retomadas no {@link NetworkSavedData} mudam a validade das
     * rotas. Só confere quando a versão dele mudou, entrou rede nova no gerenciador ou a config mudou.
     */
    private void checkNetworks(MinecraftServer server) {
        if (savedServer != server || savedData == null) {
            savedServer = server;
            savedData = NetworkSavedData.get(server);
            networksAdded = true;
        }
        NetworkSavedData data = savedData;
        boolean all = configChanged;
        if (!all && !networksAdded && data.version() == savedVersion) {
            return;
        }
        configChanged = false;
        networksAdded = false;
        savedVersion = data.version();
        for (int i = 0, n = networkList.size(); i < n; i++) {
            NetworkRoutes network = networkList.get(i);
            // Rede pausada (grupo pausado no Tablet) conta como inexistente: fica sem rotas até retomar.
            boolean exists = data.network(network.id) != null && !data.isPaused(network.id);
            if (all || exists != network.exists) {
                network.exists = exists;
                network.dirtyTypes = NetworkRoutes.ALL_TYPES;
                dirty = true;
            }
        }
    }

    private void rebuild() {
        dirty = false;
        for (int i = networkList.size() - 1; i >= 0; i--) {
            NetworkRoutes network = networkList.get(i);
            if (network.dirtyTypes == 0) {
                continue;
            }
            if (network.members.isEmpty()) {
                network.dirtyTypes = 0;
                networkList.remove(i);
                networks.remove(network.id);
                continue;
            }
            network.rebuild(scratch);
        }
        sources.clear();
        for (int i = 0, n = networkList.size(); i < n; i++) {
            NetworkRoutes network = networkList.get(i);
            for (ResourceType type : TYPES) {
                List<Port> networkSources = network.sourcesOf[type.ordinal()];
                for (int j = 0, m = networkSources.size(); j < m; j++) {
                    Port source = networkSources.get(j);
                    if (source.order != null) {
                        sources.add(source);
                    }
                }
            }
        }
        if (cursor >= sources.size()) {
            cursor = 0;
        }
    }

    /**
     * Passa o tipo {@code type} do nó para a rede {@code network} ({@code null} = nenhuma): limpa as
     * rotas das portas desse tipo, suja o tipo na rede antiga e na nova e acerta os membros (o nó sai
     * da antiga só se nenhum outro tipo dele continuar lá, e entra na nova só se ainda não estava).
     */
    private void move(NodePorts ports, ResourceType type, @Nullable UUID network) {
        UUID before = ports.network(type);
        if (Objects.equals(before, network)) {
            return;
        }
        ports.clearRoutes(type);
        boolean wasMember = network != null && ports.inNetwork(network);
        ports.setNetwork(type, network);
        NetworkRoutes old = before == null ? null : networks.get(before);
        if (old != null) {
            if (!ports.inNetwork(before)) {
                old.members.remove(ports);
            }
            old.dirtyTypes |= typeBit(type);
            dirty = true;
        }
        if (network == null) {
            return;
        }
        NetworkRoutes routes = networks.get(network);
        if (routes == null) {
            routes = new NetworkRoutes(network);
            networks.put(network, routes);
            networkList.add(routes);
            networksAdded = true;
        }
        if (!wasMember) {
            routes.members.add(ports);
        }
        routes.dirtyTypes |= typeBit(type);
        dirty = true;
    }

    private void markDirty(@Nullable UUID network, ResourceType type) {
        NetworkRoutes routes = network == null ? null : networks.get(network);
        if (routes != null) {
            routes.dirtyTypes |= typeBit(type);
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
