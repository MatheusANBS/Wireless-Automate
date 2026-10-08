package io.github.matheusanbs.wirelessautomate.network;

import io.github.matheusanbs.wirelessautomate.Config;
import io.github.matheusanbs.wirelessautomate.block.RouterBlockEntity;
import io.github.matheusanbs.wirelessautomate.block.RouterTier;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.objects.ReferenceLinkedOpenHashSet;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.ToIntFunction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * Rotas pré-montadas de uma rede e o que o profiler mede dela. A montagem roda só quando algum tipo
 * da rede fica sujo (ver {@link NetworkManager#nodeChanged}), e só para os tipos sujos
 * ({@link #dirtyTypes}): mudar a aba Itens de um nó não remonta Fluidos. O laço de transferência
 * apenas lê o resultado.
 *
 * <p>Regras da montagem, por tipo de recurso:
 * <ul>
 *   <li>só entram os membros cujo tipo está nesta rede: um nó com Itens aqui e Fluidos noutra rede
 *       só dá portas de itens a esta;
 *   <li>origem = face ativa (modo e redstone) que extrai; destino = face ativa que insere;
 *   <li>uma origem não entrega na mesma máquina e mesma face de onde tira;
 *   <li>uma face Ambos não entrega para outra face Ambos (senão os recursos iriam e voltariam entre
 *       as duas máquinas): ela entrega só em faces que só inserem e recebe só de faces que só extraem;
 *   <li>alcance e dimensão vêm do tier da <b>origem</b>: mesma dimensão e distância euclidiana entre
 *       os roteadores até o alcance (0 = dimensão inteira); outra dimensão só com {@code crossDimension},
 *       e aí sem limite de distância;
 *   <li>a taxa do balde da origem é relida da config a cada montagem;
 *   <li>o filtro da face vai para a porta ({@link Port#filter}); o laço só o consulta, e o matcher
 *       compilado dele vive no próprio {@code Filter}, então remontar não recompila um filtro igual.
 * </ul>
 *
 * <p>Custo: a montagem é O(origens + destinos) no caso comum, em que a origem alcança todos os
 * destinos que as regras lhe permitem: todos os destinos do tipo, para quem só extrai, ou todos os
 * que só inserem, para uma face Ambos; sem destino na mesma máquina e face, e com cada dimensão dos
 * destinos numa caixa inteira ao alcance ({@link ReachBox}; destinos noutra dimensão só pedem
 * {@code crossDimension}). Essas origens dividem uma única ordem de destinos
 * ({@link RoundRobinOrder.Layout}) e entram uma vez só numa lista dividida entre os destinos
 * ({@link Port#sharedFeeders}, {@link Port#sharedBothFeeders}). As outras conferem destino a destino
 * (O(destinos) cada) e, se a lista der igual à de outra origem, dividem a ordem dela. As faces só são
 * relidas nos nós que mudaram desde a última montagem ({@link NodePorts#collected}); os outros entram
 * com as portas já lidas.
 *
 * <p>Remontar não mexe no que não mudou: uma origem cujos destinos são os mesmos (mesmas portas,
 * ordem e prioridades) continua com a mesma {@link RoundRobinOrder} e os cursores dela, e só é
 * acordada se ela mesma foi relida, se apareceu destino novo na lista ou se algum destino dela foi
 * relido (filtro ou prioridade podem ter mudado). Sem isso cada remontagem acordaria todas as origens,
 * zeraria o backoff e o rodízio recomeçaria do primeiro destino.
 */
final class NetworkRoutes {
    /** Tipos com rotas; químicos só com o Mekanism instalado. */
    static final ResourceType[] TRANSFER_TYPES = LoadedTypes.LIST.toArray(new ResourceType[0]);
    private static final ResourceType[] TYPES = ResourceType.values();
    /** Máscara com todos os tipos (bit = {@code 1 << ordinal}). */
    static final int ALL_TYPES = (1 << TYPES.length) - 1;
    private static final Direction[] DIRECTIONS = Direction.values();
    private static final RouterTier[] TIERS = RouterTier.values();
    private static final Port[] NO_PORTS = new Port[0];
    private static final double AVERAGE_WEIGHT = 0.05;
    private static final ToIntFunction<Port> PRIORITY = port -> port.priority;

    final UUID id;
    /** Nós com algum tipo nesta rede, cada um uma vez, na ordem de entrada (remoção O(1)). */
    final ReferenceLinkedOpenHashSet<NodePorts> members = new ReferenceLinkedOpenHashSet<>();
    /** Origens ativas de cada tipo (por ordinal), inclusive as sem destino. */
    final List<Port>[] sourcesOf = newLists();
    /** Destinos ativos de cada tipo (por ordinal). */
    final List<Port>[] destinationsOf = newLists();
    // Trabalho da montagem, reaproveitado.
    private final DestinationSet everyDestination = new DestinationSet();
    private final DestinationSet insertOnly = new DestinationSet();
    private final List<Port> insertOnlyList = new ArrayList<>();
    private final Map<List<Port>, RoundRobinOrder.Layout<Port>> layouts = new HashMap<>();
    /** Última comparação com uma ordem antiga numa lista dividida (muitas origens têm a mesma). */
    private RoundRobinOrder.@Nullable Layout<Port> comparedLayout;
    private @Nullable List<Port> comparedList;
    private boolean comparedSame;
    private boolean comparedNew;
    /** Marca de {@link Port#stamp} para achar destinos novos sem alocar. */
    private static int stamps;
    /** Valores da config por tier, lidos uma vez por tipo em cada montagem. */
    private final long[] tierRate = new long[TIERS.length];
    private final int[] tierRange = new int[TIERS.length];
    private final boolean[] tierCrossDimension = new boolean[TIERS.length];
    /** A rede existe no {@link NetworkSavedData}; se não, o id é tratado como "sem rede". */
    boolean exists;
    /** Tipos a remontar (bit = {@code 1 << ordinal}); 0 = rotas em dia. */
    int dirtyTypes = ALL_TYPES;

    // Profiler.
    long tickNanos;
    long lastNanos;
    double averageNanos;
    int ops;
    int opsLastSecond;

    NetworkRoutes(UUID id) {
        this.id = id;
    }

    @SuppressWarnings("unchecked")
    private static List<Port>[] newLists() {
        List<Port>[] lists = new List[TYPES.length];
        for (int i = 0; i < lists.length; i++) {
            lists[i] = new ArrayList<>();
        }
        return lists;
    }

    /** Remonta os tipos sujos: origens, destinos e a ordem de entrega. {@code scratch} é uma lista reaproveitada. */
    void rebuild(List<Port> scratch) {
        int mask = dirtyTypes;
        dirtyTypes = 0;
        if (!exists) {
            // Só as portas dos tipos desta rede: as dos outros tipos do nó são de outras redes.
            for (ResourceType type : TYPES) {
                if ((mask & NetworkManager.typeBit(type)) == 0) {
                    continue;
                }
                sourcesOf[type.ordinal()].clear();
                destinationsOf[type.ordinal()].clear();
                for (NodePorts member : members) {
                    if (id.equals(member.network(type))) {
                        member.clearRoutes(type);
                    }
                }
            }
            return;
        }
        for (ResourceType type : TRANSFER_TYPES) {
            if ((mask & NetworkManager.typeBit(type)) != 0) {
                rebuild(type, scratch);
            }
        }
        scratch.clear();
    }

    private void rebuild(ResourceType type, List<Port> scratch) {
        List<Port> sources = sourcesOf[type.ordinal()];
        List<Port> destinations = destinationsOf[type.ordinal()];
        sources.clear();
        destinations.clear();
        collectPorts(type, sources, destinations);
        measureDestinations(destinations);
        comparedLayout = null;
        comparedList = null;
        RoundRobinOrder.Layout<Port> everyLayout = null;
        RoundRobinOrder.Layout<Port> insertOnlyLayout = null;
        boolean everyFresh = false;
        boolean insertOnlyFresh = false;
        List<Port> broadcast = null;
        List<Port> broadcastBoth = null;
        readTiers(type);
        for (int i = 0, n = sources.size(); i < n; i++) {
            Port source = sources.get(i);
            RoundRobinOrder<Port> previous = source.previousOrder;
            source.previousOrder = null;
            int tier = source.tier.ordinal();
            source.limiter.setRatePerSecond(tierRate[tier]);
            int range = tierRange[tier];
            boolean crossDimension = tierCrossDimension[tier];
            if (destinations.isEmpty()) {
                // Sem rota a origem não é visitada; quando aparecer destino, ele é novo e a acorda.
                continue;
            }
            if (!source.destination && everyDestination.reachesAll(source, range, crossDimension)) {
                if (everyLayout == null) {
                    everyLayout = new RoundRobinOrder.Layout<>(destinations, PRIORITY);
                    everyFresh = anyFresh(destinations);
                    broadcast = new ArrayList<>();
                }
                assign(source, previous, destinations, everyLayout, everyFresh, true);
                broadcast.add(source);
                continue;
            }
            if (source.destination && insertOnly.reachesAll(source, range, crossDimension)) {
                // Face Ambos que alcança todos os destinos que só inserem: são exatamente os que a
                // conferência um a um daria (ela pula os Ambos e a própria face).
                if (insertOnlyLayout == null) {
                    insertOnlyLayout = new RoundRobinOrder.Layout<>(insertOnlyList, PRIORITY);
                    insertOnlyFresh = anyFresh(insertOnlyList);
                    broadcastBoth = new ArrayList<>();
                }
                assign(source, previous, insertOnlyList, insertOnlyLayout, insertOnlyFresh, true);
                broadcastBoth.add(source);
                continue;
            }
            scratch.clear();
            boolean fresh = false;
            for (int j = 0, m = destinations.size(); j < m; j++) {
                Port destination = destinations.get(j);
                if (!destination.sameEndpoint(source) && !bothToBoth(source, destination)
                        && reachable(source, destination, range, crossDimension)) {
                    scratch.add(destination);
                    destination.feeders.add(source);
                    fresh |= destination.fresh;
                }
            }
            if (!scratch.isEmpty()) {
                assign(source, previous, scratch, null, fresh, false);
            }
        }
        if (broadcast != null) {
            for (int j = 0, m = destinations.size(); j < m; j++) {
                destinations.get(j).sharedFeeders = broadcast;
            }
        }
        if (broadcastBoth != null) {
            for (int j = 0, m = insertOnlyList.size(); j < m; j++) {
                insertOnlyList.get(j).sharedBothFeeders = broadcastBoth;
            }
        }
        layouts.clear();
        insertOnlyList.clear();
        comparedLayout = null;
        comparedList = null;
    }

    /**
     * Dá à origem a ordem dos destinos {@code list}: a antiga, com os cursores, se ela foi montada
     * com os mesmos destinos e prioridades; senão uma nova sobre {@code layout} (ou a dividida de
     * {@link #layoutFor}, se {@code null}). Acorda a origem só se ela foi relida, se algum destino
     * da lista foi relido ou se a lista ganhou destino. {@code shared}: a lista é a mesma para várias
     * origens nesta montagem, e a comparação com a mesma ordem antiga é reaproveitada.
     */
    private void assign(Port source, @Nullable RoundRobinOrder<Port> previous, List<Port> list,
            RoundRobinOrder.@Nullable Layout<Port> layout, boolean listFresh, boolean shared) {
        boolean same;
        boolean gained;
        if (previous == null) {
            same = false;
            gained = true;
        } else if (shared && previous.layout() == comparedLayout && list == comparedList) {
            same = comparedSame;
            gained = comparedNew;
        } else {
            same = previous.layout().sameAs(list, PRIORITY);
            gained = !same && gainedDestination(previous.layout(), list);
            if (shared) {
                comparedLayout = previous.layout();
                comparedList = list;
                comparedSame = same;
                comparedNew = gained;
            }
        }
        if (same) {
            source.order = previous;
        } else {
            source.order = new RoundRobinOrder<>(layout != null ? layout : layoutFor(list));
        }
        if (source.fresh || listFresh || gained) {
            source.sourceBackoff.wake();
        }
    }

    /** {@code list} tem algum destino que não estava em {@code before}. O(destinos), sem alocar. */
    private static boolean gainedDestination(RoundRobinOrder.Layout<Port> before, List<Port> list) {
        int stamp = ++stamps;
        for (int i = 0, n = before.size(); i < n; i++) {
            before.destination(i).stamp = stamp;
        }
        for (int i = 0, n = list.size(); i < n; i++) {
            if (list.get(i).stamp != stamp) {
                return true;
            }
        }
        return false;
    }

    private static boolean anyFresh(List<Port> ports) {
        for (int i = 0, n = ports.size(); i < n; i++) {
            if (ports.get(i).fresh) {
                return true;
            }
        }
        return false;
    }

    private void readTiers(ResourceType type) {
        for (RouterTier tier : TIERS) {
            Config.TierValues values = Config.TIERS.get(tier);
            tierRate[tier.ordinal()] = ratePerSecond(type, values);
            tierRange[tier.ordinal()] = values.range().get();
            tierCrossDimension[tier.ordinal()] = values.crossDimension().get();
        }
    }

    /**
     * Prepara o teste rápido de {@link DestinationSet#reachesAll}: as caixas por dimensão e as faces
     * das máquinas de todos os destinos e dos que só inserem, e a lista destes.
     */
    private void measureDestinations(List<Port> destinations) {
        everyDestination.clear();
        insertOnly.clear();
        insertOnlyList.clear();
        for (int j = 0, n = destinations.size(); j < n; j++) {
            Port destination = destinations.get(j);
            everyDestination.add(destination);
            if (!destination.source) {
                insertOnly.add(destination);
                insertOnlyList.add(destination);
            }
        }
    }

    /**
     * Um conjunto de destinos medido para saber de uma vez se uma origem alcança todos: uma
     * {@link ReachBox} por dimensão (quase sempre uma ou duas) e as faces das máquinas.
     */
    private static final class DestinationSet {
        private Level[] levels = new Level[2];
        private ReachBox[] boxes = {new ReachBox(), new ReachBox()};
        private int levelCount;
        private int count;
        private final LongOpenHashSet endpoints = new LongOpenHashSet();

        void clear() {
            Arrays.fill(levels, 0, levelCount, null);
            levelCount = 0;
            count = 0;
            endpoints.clear();
        }

        void add(Port destination) {
            Level level = destination.node.getLevel();
            int index = 0;
            while (index < levelCount && levels[index] != level) {
                index++;
            }
            if (index == levelCount) {
                if (index == levels.length) {
                    levels = Arrays.copyOf(levels, index * 2);
                    boxes = Arrays.copyOf(boxes, index * 2);
                    for (int i = index; i < boxes.length; i++) {
                        boxes[i] = new ReachBox();
                    }
                }
                levels[index] = level;
                boxes[index].clear();
                levelCount++;
            }
            BlockPos pos = destination.node.getBlockPos();
            boxes[index].include(pos.getX(), pos.getY(), pos.getZ());
            endpoints.add(endpointKey(destination));
            count++;
        }

        /**
         * A origem entrega em todos os destinos do conjunto: nenhum é a mesma máquina e face (a chave
         * ignora o mundo: na dúvida, confere um a um), os da dimensão dela estão todos ao alcance e,
         * se há destinos noutra dimensão, o tier dela cruza dimensões.
         */
        boolean reachesAll(Port source, int range, boolean crossDimension) {
            if (count == 0 || endpoints.contains(endpointKey(source))) {
                return false;
            }
            Level level = source.node.getLevel();
            for (int i = 0; i < levelCount; i++) {
                if (levels[i] != level) {
                    if (!crossDimension) {
                        return false;
                    }
                } else {
                    BlockPos pos = source.node.getBlockPos();
                    if (!boxes[i].allWithin(pos.getX(), pos.getY(), pos.getZ(), range)) {
                        return false;
                    }
                }
            }
            return true;
        }
    }

    private static long endpointKey(Port port) {
        return port.machinePos.asLong() * 6 + port.face.ordinal();
    }

    /** A ordem de destinos da lista em {@code scratch}, dividida com as origens de lista igual. */
    private RoundRobinOrder.Layout<Port> layoutFor(List<Port> scratch) {
        RoundRobinOrder.Layout<Port> layout = layouts.get(scratch);
        if (layout == null) {
            List<Port> key = List.copyOf(scratch);
            layout = new RoundRobinOrder.Layout<>(key, PRIORITY);
            layouts.put(key, layout);
        }
        return layout;
    }

    /**
     * Junta as origens e os destinos do tipo. Um membro que não mudou desde a última montagem entra
     * com as portas já lidas; os outros têm as faces relidas ({@link #readPorts}) e as portas ficam
     * marcadas como relidas ({@link Port#fresh}). A ordem que cada porta tinha vai para
     * {@link Port#previousOrder}.
     */
    private void collectPorts(ResourceType type, List<Port> sources, List<Port> destinations) {
        for (NodePorts member : members) {
            if (!id.equals(member.network(type))) {
                continue;
            }
            Port[] active = member.collected(type);
            boolean fresh = active == null;
            if (fresh) {
                member.rememberOrders(type);
                member.clearRoutes(type);
                active = readPorts(member, type);
                member.setCollected(type, active);
            }
            for (Port port : active) {
                if (!fresh) {
                    port.previousOrder = port.order;
                }
                port.clearOrder();
                port.fresh = fresh;
                if (port.source) {
                    sources.add(port);
                }
                if (port.destination) {
                    destinations.add(port);
                }
            }
        }
    }

    /** Lê as faces ativas do tipo no nó e preenche os campos da configuração nas portas delas. */
    private Port[] readPorts(NodePorts member, ResourceType type) {
        RouterBlockEntity node = member.node;
        boolean powered = node.powered();
        // O facing sai do blockstate: lido uma vez por nó, e não uma por face.
        Direction facing = node.facing();
        Port[] active = NO_PORTS;
        int count = 0;
        RouterTier tier = null;
        BlockPos machine = null;
        for (Direction face : DIRECTIONS) {
            FaceConfig config = node.face(type, RelativeSide.fromAbsolute(facing, face));
            if (!config.isActive(powered)) {
                continue;
            }
            if (tier == null) {
                tier = node.tier();
                machine = node.machinePos();
                active = new Port[DIRECTIONS.length];
            }
            Port port = member.port(type, face);
            port.network = this;
            port.priority = config.priority();
            port.filter = node.filterSet(type, face);
            port.tier = tier;
            port.machinePos = machine;
            port.source = config.mode().extracts();
            port.destination = config.mode().inserts();
            active[count++] = port;
        }
        return count == active.length ? active : Arrays.copyOf(active, count);
    }

    /** As duas portas estão em Ambos: depois de {@link #collectPorts}, origem e destino ao mesmo tempo. */
    private static boolean bothToBoth(Port source, Port destination) {
        return source.destination && destination.source;
    }

    private static boolean reachable(Port source, Port destination, int range, boolean crossDimension) {
        if (source.node.getLevel() != destination.node.getLevel()) {
            return crossDimension;
        }
        if (range <= 0) {
            return true;
        }
        double max = (double) range * range;
        return source.node.getBlockPos().distSqr(destination.node.getBlockPos()) <= max;
    }

    /** Taxa do balde em unidades por segundo (0 = sem limite). */
    static long ratePerSecond(ResourceType type, Config.TierValues tier) {
        long value = tier.rate(type);
        return type.ratePerTick() ? RateLimiter.perSecondFromPerTick(value) : value;
    }

    /** Fecha a medição do tick. */
    void endTick() {
        lastNanos = tickNanos;
        averageNanos += (lastNanos - averageNanos) * AVERAGE_WEIGHT;
        tickNanos = 0;
    }

    /** Fecha a janela de um segundo das operações. */
    void endSecond() {
        opsLastSecond = ops;
        ops = 0;
    }
}
