package io.github.matheusanbs.wirelessautomate.network;

import io.github.matheusanbs.wirelessautomate.Config;
import io.github.matheusanbs.wirelessautomate.block.RouterBlockEntity;
import io.github.matheusanbs.wirelessautomate.range.RemoteIndex;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
 * <p>A rede é da face: cada porta (nó, face, tipo) está na rede da sua face ({@link Port#faceNetwork}),
 * ou em nenhuma. O nó é membro de toda rede que aparece em alguma face dele, de qualquer tipo
 * ({@link RouterBlockEntity#networks(ResourceType)}, lido só no {@link #addNode} e no
 * {@link #nodeChanged}), e a montagem de um tipo numa rede só adota as portas daquela rede. A
 * sujeira é por tipo: mudar a aba Itens remonta só os itens, mas em todas as redes do nó naquele
 * tipo, porque a leitura das portas de um tipo é dividida entre elas ({@link NodePorts}).
 *
 * <p>Sono e despertar: em volta de cada visita o gerenciador anota a máquina da origem visitada
 * (nível e posição). Assim, quando a máquina de um destino avisa mudança porque nós acabamos de
 * inserir nela, só as portas dessa máquina acordam, e não as origens que entregam ali (ganhar
 * recursos não abre espaço). A regra é a mesma para a máquina presa ({@link #wake}, pelo aviso de
 * vizinho) e para as remotas ({@link #wakeMachine}, pelo mixin em {@code Level.blockEntityChanged} e
 * pela carga do chunk, ver {@link RemoteIndex}), e compara sempre a máquina, nunca o nó: a origem
 * remota de um nó entregando na presa do mesmo nó é uma entrega.
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
    /** A origem da visita em andamento é a máquina presa de {@link #visitNode}. */
    private static boolean visitAttached;

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
    /** O amostrador dos Fluxos do Tablet: um balde de nós por tick, fora do orçamento do transporte. */
    private final FlowSampler flows = new FlowSampler();
    private int cursor;
    private boolean dirty;
    /** Redes salvas do servidor atual e a versão delas já conferida ({@link #checkNetworks}). */
    private @Nullable MinecraftServer savedServer;
    private @Nullable NetworkSavedData savedData;
    private int savedVersion;
    /** Entrou rede nova no gerenciador: a existência dela ainda não foi conferida. */
    private boolean networksAdded = true;
    /** O {@link Config#tiersFingerprint()} da última montagem por config. */
    private long tiersFingerprint;
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

    /** O laço está no meio de uma visita (os avisos das máquinas vêm da nossa entrega ou extração). */
    public static boolean visiting() {
        return visitNode != null;
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
        flows.add(ports);
        for (ResourceType type : TYPES) {
            setMembership(ports, type, node.networks(type));
        }
        RemoteIndex.get().update(node);
    }

    public void removeNode(RouterBlockEntity node) {
        NodePorts ports = nodes.remove(node);
        if (ports != null) {
            flows.remove(ports);
            for (ResourceType type : TYPES) {
                setMembership(ports, type, NodePorts.NO_NETWORKS);
            }
            RemoteIndex.get().remove(node);
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
     * A configuração de faces (inclusive a rede delas) dos tipos de {@code types} (máscara de
     * {@link #typeBit}) do nó mudou: descarta a leitura desses tipos e os marca para remontar no
     * próximo tick em todas as redes do nó naquele tipo, as de antes e as de agora
     * ({@link #setMembership}). Os tipos fora da máscara não são relidos nem remontados. Só aqui e no
     * {@link #addNode} as redes do nó são recalculadas.
     *
     * <p>Também é chamado no meio de uma remontagem, quando a leitura de uma remota acha a frente
     * mudada ({@link NetworkRoutes}): as redes já remontadas nessa passada ficam sujas de novo e
     * remontam no tick seguinte. Nesse caso raro (a máquina girou sem avisar), as portas delas nos
     * outros tipos podem ficar um tick sem rota, se outra rede da mesma passada reler o tipo.
     */
    public void nodeChanged(RouterBlockEntity node, int types) {
        NodePorts ports = nodes.get(node);
        if (ports == null || types == 0) {
            return;
        }
        ports.invalidate(types);
        for (ResourceType type : TYPES) {
            if ((types & typeBit(type)) != 0) {
                setMembership(ports, type, node.networks(type));
            }
        }
    }

    /**
     * A máquina presa do nó avisou mudança: acorda as portas dela e as origens que esperam destino e
     * entregam nos destinos dela. Se o aviso vem de uma entrega nossa (durante uma visita, numa
     * máquina que não é a da origem visitada), só as portas da máquina acordam.
     */
    public void wake(RouterBlockEntity node) {
        NodePorts ports = nodes.get(node);
        if (ports != null) {
            ports.wake(!deliveringIntoAttached(node));
        }
    }

    /**
     * A máquina remota do nó em {@code machinePos} avisou mudança ({@code setChanged} ou o chunk
     * carregou): a mesma regra de {@link #wake}, só nas portas dessa máquina.
     */
    public void wakeMachine(RouterBlockEntity node, BlockPos machinePos) {
        NodePorts ports = nodes.get(node);
        if (ports != null) {
            ports.wakeMachine(machinePos.asLong(), !deliveringInto(node.getLevel(), machinePos));
        }
    }

    /**
     * Como {@link #deliveringInto}, na máquina presa do nó. Atalho sem calcular a posição: se a origem
     * visitada é a presa deste mesmo nó, o aviso vem dela e não é entrega (uma origem remota do nó não
     * entra no atalho).
     */
    private static boolean deliveringIntoAttached(RouterBlockEntity node) {
        if (visitNode == null || (node == visitNode && visitAttached)) {
            return false;
        }
        return deliveringInto(node.getLevel(), node.machinePos());
    }

    /**
     * A visita em andamento está inserindo na máquina em {@code machine} de {@code level}: é outra
     * máquina que a da origem. Compara a máquina, não o nó, porque um nó tem várias (a presa e as
     * remotas): a remota de um nó entregando na presa dele é uma entrega.
     */
    private static boolean deliveringInto(@Nullable Level level, BlockPos machine) {
        return visitNode != null && (level != visitLevel || !machine.equals(visitMachine));
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

    /**
     * Como {@link #capabilityChanged(RouterBlockEntity, ResourceType, Direction)}, numa máquina remota
     * do nó, em {@code machinePos}: acorda a porta dessa máquina, face e tipo (se existe) e as origens
     * que esperam por ela.
     */
    public void capabilityChanged(RouterBlockEntity node, BlockPos machinePos, ResourceType type, Direction face) {
        NodePorts ports = nodes.get(node);
        if (ports != null) {
            ports.capabilityChanged(machinePos.asLong(), type, face);
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

    /** O tempo médio por tick da rede movendo recursos, em ns; 0 sem rotas (rede sem nós carregados ou pausada). */
    public double averageNanos(UUID network) {
        NetworkRoutes routes = networks.get(network);
        return routes == null ? 0 : routes.averageNanos;
    }

    /** O amostrador dos Fluxos: quem olha a aba Fluxos e o resultado da última amostra. */
    public FlowSampler flows() {
        return flows;
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

    /** Para GameTests e diagnóstico: o nó carregado é membro da rede nas rotas (alguma face dele está nela). */
    public boolean isMember(UUID network, RouterBlockEntity node) {
        NetworkRoutes routes = networks.get(network);
        NodePorts ports = nodes.get(node);
        return routes != null && ports != null && routes.members.contains(ports);
    }

    /**
     * Para GameTests: a ordem de destinos da porta da máquina presa na face absoluta {@code face}
     * (com os cursores do rodízio), ou {@code null} se a porta não existe ou não tem rota. Só para
     * comparar por identidade ({@code ==}).
     */
    public @Nullable Object sourceOrderForTests(RouterBlockEntity node, ResourceType type, Direction face) {
        Port port = portOrNull(node, null, type, face);
        return port == null ? null : port.order;
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

    /** O mesmo do {@link #sourceSleepInterval} para o sono de destino (o de "cheio"); para os GameTests. */
    public int destinationSleepInterval(RouterBlockEntity node, ResourceType type, Direction face) {
        NodePorts ports = nodes.get(node);
        Port port = ports == null ? null : ports.peek(type, face);
        return port == null ? -1 : port.destinationBackoff.nextIntervalTicks();
    }

    /**
     * Para GameTests e diagnóstico: como {@link #sourceSleepInterval}, na porta da máquina remota em
     * {@code machinePos} (face absoluta); -1 se a porta não existe.
     */
    public int remoteSourceSleepInterval(RouterBlockEntity node, BlockPos machinePos, ResourceType type, Direction face) {
        Port port = portOrNull(node, machinePos, type, face);
        return port == null ? -1 : port.sourceBackoff.nextIntervalTicks();
    }

    /**
     * Para GameTests: põe a porta (da máquina presa com {@code machinePos} nulo, ou da remota) para
     * dormir como origem e como destino até {@code ticks} ticks depois de {@code now}, como um sono
     * longo do backoff. Devolve se a porta existe.
     */
    public boolean sleepForTests(RouterBlockEntity node, @Nullable BlockPos machinePos, ResourceType type,
            Direction face, long now, int ticks) {
        Port port = portOrNull(node, machinePos, type, face);
        if (port == null) {
            return false;
        }
        port.sourceBackoff.sleepUntil(now, now + ticks);
        port.destinationBackoff.sleepUntil(now, now + ticks);
        return true;
    }

    /** Para GameTests: a porta (da presa com {@code machinePos} nulo, ou da remota) está acordada como origem. */
    public boolean sourceAwake(RouterBlockEntity node, @Nullable BlockPos machinePos, ResourceType type,
            Direction face, long now) {
        Port port = portOrNull(node, machinePos, type, face);
        return port != null && port.sourceBackoff.isAwake(now);
    }

    /**
     * Para GameTests: o tick em que a porta (da presa com {@code machinePos} nulo, ou da remota) acorda
     * como origem ({@link Long#MIN_VALUE} se acordada desde sempre ou se não existe), e se ela dorme
     * esperando destino.
     */
    public long sourceWakeAt(RouterBlockEntity node, @Nullable BlockPos machinePos, ResourceType type, Direction face) {
        Port port = portOrNull(node, machinePos, type, face);
        return port == null ? Long.MIN_VALUE : port.sourceBackoff.wakeAt();
    }

    /** Para GameTests: a porta (da presa com {@code machinePos} nulo, ou da remota) dorme esperando destino. */
    public boolean waitsDestination(RouterBlockEntity node, @Nullable BlockPos machinePos, ResourceType type, Direction face) {
        Port port = portOrNull(node, machinePos, type, face);
        return port != null && port.waitsDestination;
    }

    /**
     * Para GameTests e diagnóstico: o que a porta (da presa com {@code machinePos} nulo, ou da remota)
     * entregou como origem desde que existe, o que o amostrador já leu mais o que ainda não leu; -1 se a
     * porta não existe.
     */
    public long gaveForTests(RouterBlockEntity node, @Nullable BlockPos machinePos, ResourceType type, Direction face) {
        Port port = portOrNull(node, machinePos, type, face);
        return port == null ? -1 : port.gaveRead + port.gave;
    }

    /** O mesmo de {@link #gaveForTests}, para o que a porta recebeu como destino; -1 se ela não existe. */
    public long gotForTests(RouterBlockEntity node, @Nullable BlockPos machinePos, ResourceType type, Direction face) {
        Port port = portOrNull(node, machinePos, type, face);
        return port == null ? -1 : port.gotRead + port.got;
    }

    private @Nullable Port portOrNull(RouterBlockEntity node, @Nullable BlockPos machinePos, ResourceType type,
            Direction face) {
        NodePorts ports = nodes.get(node);
        if (ports == null) {
            return null;
        }
        if (machinePos == null) {
            return ports.peek(type, face);
        }
        Port[] byFace = ports.machinePorts(machinePos.asLong());
        return byFace == null ? null : byFace[type.ordinal() * Direction.values().length + face.ordinal()];
    }

    /**
     * Para GameTests e diagnóstico: a máquina remota em {@code machinePos} tem uma porta ativa do tipo
     * na face absoluta {@code face} na leitura da última montagem, em qualquer rede (inclusive face
     * sem rede ou de rede pausada: a leitura é do nó, dividida entre as redes dele). Não cria nada.
     */
    public boolean hasRemotePort(RouterBlockEntity node, BlockPos machinePos, ResourceType type, Direction face) {
        NodePorts ports = nodes.get(node);
        Port[] active = ports == null ? null : ports.collected(type);
        if (active == null) {
            return false;
        }
        for (Port port : active) {
            if (port.machine.isRemote() && port.face == face && port.machinePos.equals(machinePos)) {
                return true;
            }
        }
        return false;
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
            List<NetworkStats.TypeCount> byType = new ArrayList<>(TYPES.length);
            for (ResourceType type : TYPES) {
                List<Port> typeSources = network.sourcesOf[type.ordinal()];
                List<Port> typeDestinations = network.destinationsOf[type.ordinal()];
                int sleeping = 0;
                sourceCount += typeSources.size();
                destinationCount += typeDestinations.size();
                for (Port port : typeSources) {
                    if (port.sourceBackoff.isSleeping(now)) {
                        sourcesSleeping++;
                        sleeping++;
                    }
                }
                for (Port port : typeDestinations) {
                    if (port.destinationBackoff.isSleeping(now)) {
                        destinationsSleeping++;
                        sleeping++;
                        if (NodeProbe.isFull(port, now)) {
                            destinationsFull++;
                        }
                    }
                }
                byType.add(new NetworkStats.TypeCount(typeSources.size(), typeDestinations.size(), sleeping));
            }
            result.add(new NetworkStats(network.id, network.members.size(), network.averageNanos,
                    network.lastNanos, network.opsLastSecond,
                    sourceCount - sourcesSleeping, sourcesSleeping,
                    destinationCount - destinationsSleeping, destinationsSleeping, destinationsFull, List.copyOf(byType)));
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
        // Fora do orçamento do transporte: o custo dele sai à parte no /wa profile.
        flows.tick(server, now);
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
        visitAttached = !source.machine.isRemote();
        int result;
        try {
            // Sem default: um tipo novo no ResourceType não compila até ganhar o seu caso aqui.
            result = switch (source.type) {
                case ITEM -> ItemTransfer.move(source, now);
                case FLUID -> FluidTransfer.move(source, now) ? MOVED : 0;
                case ENERGY -> ScalarTransfer.move(source, now, EnergyAccess.INSTANCE) ? MOVED : 0;
                case CHEMICAL -> Chemicals.move(source, now) ? MOVED : 0;
                case SOURCE -> Sources.move(source, now) ? MOVED : 0;
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
            tiersFingerprint = Config.tiersFingerprint();
        }
        NetworkSavedData data = savedData;
        boolean all = false;
        if (configChanged) {
            configChanged = false;
            // Só os valores dos tiers mudam as rotas: recarregar outra chave (o Ressonador, o chunk loading) não
            // remonta nada.
            long fingerprint = Config.tiersFingerprint();
            all = fingerprint != tiersFingerprint;
            tiersFingerprint = fingerprint;
        }
        if (!all && !networksAdded && data.version() == savedVersion) {
            return;
        }
        networksAdded = false;
        savedVersion = data.version();
        if (all) {
            flows.configChanged();
            // O raio e o limite das máquinas remotas (e se o upgrade vale) saem da config na leitura
            // das portas: relê as faces dos nós com o upgrade valendo ou com portas remotas (que podem
            // ter deixado de valer). Os outros não dependem disso, e reler acordaria as origens deles.
            // Só na recarga da config.
            for (NodePorts ports : nodes.values()) {
                if (ports.hasRemotes() || ports.node.rangeActive()) {
                    ports.invalidate(ALL_TYPES);
                }
                // O upgrade pode ter passado a valer ou deixado de valer (raio 0 no tier).
                RemoteIndex.get().update(ports.node);
            }
        }
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
     * As redes do tipo {@code type} no nó passam a ser {@code now}: suja o tipo em todas as redes de
     * antes e de agora (criando as novas), para que toda rede com uma porta desse tipo neste nó remonte
     * na mesma passada (a releitura limpa as portas do tipo inteiro, de todas as redes; ver
     * {@link NodePorts}). Nas redes que saíram, tira as rotas das portas do tipo e o nó deixa de ser
     * membro se nenhum outro tipo dele continuar lá; nas novas, o nó entra se ainda não era membro.
     */
    private void setMembership(NodePorts ports, ResourceType type, UUID[] now) {
        UUID[] before = ports.networks(type);
        int bit = typeBit(type);
        ports.setNetworks(type, now);
        for (UUID id : before) {
            NetworkRoutes routes = networks.get(id);
            if (routes == null) {
                continue;
            }
            routes.dirtyTypes |= bit;
            dirty = true;
            if (!NodePorts.contains(now, id)) {
                ports.clearRoutes(type, routes);
                if (!ports.inNetwork(id)) {
                    routes.members.remove(ports);
                }
            }
        }
        for (UUID id : now) {
            NetworkRoutes routes = networks.get(id);
            if (routes == null) {
                routes = new NetworkRoutes(id);
                networks.put(id, routes);
                networkList.add(routes);
                networksAdded = true;
            }
            routes.members.add(ports);
            routes.dirtyTypes |= bit;
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
