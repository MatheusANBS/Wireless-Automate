package io.github.matheusanbs.wirelessautomate.network;

import io.github.matheusanbs.wirelessautomate.Config;
import io.github.matheusanbs.wirelessautomate.block.RouterBlockEntity;
import io.github.matheusanbs.wirelessautomate.block.RouterTier;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * Rotas pré-montadas de uma rede e o que o profiler mede dela. A montagem roda só quando a rede
 * fica suja (ver {@link NetworkManager#nodeChanged}); o laço de transferência apenas lê o resultado.
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
 * destinos do tipo (só extrai, nenhum destino na mesma máquina e face, e a caixa dos destinos
 * inteira no alcance, ver {@link ReachBox}). Essas origens dividem uma única ordem de destinos
 * ({@link RoundRobinOrder.Layout}) e entram uma vez só na lista {@link Port#sharedFeeders}, dividida
 * entre os destinos. As outras conferem destino a destino (O(destinos) cada) e, se a lista der
 * igual à de outra origem, dividem a ordem dela. As faces só são relidas nos nós que mudaram desde
 * a última montagem ({@link NodePorts#collected}); os outros entram com as portas já lidas.
 */
final class NetworkRoutes {
    static final ResourceType[] TRANSFER_TYPES = {ResourceType.ITEM, ResourceType.FLUID, ResourceType.ENERGY};
    private static final ResourceType[] TYPES = ResourceType.values();
    private static final Direction[] DIRECTIONS = Direction.values();
    private static final RouterTier[] TIERS = RouterTier.values();
    private static final Port[] NO_PORTS = new Port[0];
    private static final double AVERAGE_WEIGHT = 0.05;

    final UUID id;
    /** Nós com algum tipo nesta rede, cada um uma vez. */
    final List<NodePorts> members = new ArrayList<>();
    /** Todas as origens ativas, de todos os tipos, inclusive as sem destino. */
    final List<Port> sources = new ArrayList<>();
    final List<Port> destinations = new ArrayList<>();
    // Trabalho da montagem, reaproveitado.
    private final ReachBox destinationBox = new ReachBox();
    private final LongOpenHashSet destinationEndpoints = new LongOpenHashSet();
    private final Map<List<Port>, RoundRobinOrder.Layout<Port>> layouts = new HashMap<>();
    /** Valores da config por tier, lidos uma vez por tipo em cada montagem. */
    private final long[] tierRate = new long[TIERS.length];
    private final int[] tierRange = new int[TIERS.length];
    private final boolean[] tierCrossDimension = new boolean[TIERS.length];
    /** A rede existe no {@link NetworkSavedData}; se não, o id é tratado como "sem rede". */
    boolean exists;
    boolean dirty = true;

    // Profiler.
    long tickNanos;
    long lastNanos;
    double averageNanos;
    int ops;
    int opsLastSecond;

    NetworkRoutes(UUID id) {
        this.id = id;
    }

    /** Remonta origens, destinos e a ordem de entrega. {@code scratch} é uma lista reaproveitada. */
    void rebuild(List<Port> scratch) {
        sources.clear();
        destinations.clear();
        if (!exists) {
            // Só as portas dos tipos desta rede: as dos outros tipos do nó são de outras redes.
            for (int i = 0, n = members.size(); i < n; i++) {
                NodePorts member = members.get(i);
                for (ResourceType type : TYPES) {
                    if (id.equals(member.network(type))) {
                        member.clearRoutes(type);
                    }
                }
            }
            return;
        }
        for (ResourceType type : TRANSFER_TYPES) {
            int firstSource = sources.size();
            int firstDestination = destinations.size();
            collectPorts(type);
            int lastDestination = destinations.size();
            Level destinationLevel = measureDestinations(firstDestination, lastDestination);
            RoundRobinOrder.Layout<Port> everyDestination = null;
            List<Port> broadcast = null;
            readTiers(type);
            for (int i = firstSource; i < sources.size(); i++) {
                Port source = sources.get(i);
                int tier = source.tier.ordinal();
                source.limiter.setRatePerSecond(tierRate[tier]);
                int range = tierRange[tier];
                boolean crossDimension = tierCrossDimension[tier];
                // Rotas novas podem destravar uma origem que dormia por falta de destino.
                source.sourceBackoff.wake();
                if (lastDestination == firstDestination) {
                    continue;
                }
                if (reachesAll(source, destinationLevel, range, crossDimension)) {
                    if (everyDestination == null) {
                        everyDestination = new RoundRobinOrder.Layout<>(
                                destinations.subList(firstDestination, lastDestination), port -> port.priority);
                        broadcast = new ArrayList<>();
                    }
                    source.order = new RoundRobinOrder<>(everyDestination);
                    broadcast.add(source);
                    continue;
                }
                scratch.clear();
                for (int j = firstDestination; j < lastDestination; j++) {
                    Port destination = destinations.get(j);
                    if (!destination.sameEndpoint(source) && !bothToBoth(source, destination)
                            && reachable(source, destination, range, crossDimension)) {
                        scratch.add(destination);
                        destination.feeders.add(source);
                    }
                }
                if (!scratch.isEmpty()) {
                    source.order = new RoundRobinOrder<>(layoutFor(scratch));
                }
            }
            if (broadcast != null) {
                for (int j = firstDestination; j < lastDestination; j++) {
                    destinations.get(j).sharedFeeders = broadcast;
                }
            }
            layouts.clear();
        }
        scratch.clear();
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
     * Prepara o teste rápido de {@link #reachesAll}: a caixa e as faces das máquinas dos destinos
     * {@code [first, last)}. Devolve o mundo deles, ou {@code null} se estão em mais de um.
     */
    private @Nullable Level measureDestinations(int first, int last) {
        destinationBox.clear();
        destinationEndpoints.clear();
        Level level = null;
        for (int j = first; j < last; j++) {
            Port destination = destinations.get(j);
            Level at = destination.node.getLevel();
            if (j == first) {
                level = at;
            } else if (at != level) {
                return null;
            }
            BlockPos pos = destination.node.getBlockPos();
            destinationBox.include(pos.getX(), pos.getY(), pos.getZ());
            destinationEndpoints.add(endpointKey(destination));
        }
        return level;
    }

    /**
     * A origem entrega em todos os destinos do tipo: as regras da montagem não excluem nenhum.
     * Só extrai (senão o Armazém exclui as faces Ambos), nenhum destino é a mesma máquina e face
     * (a chave ignora o mundo: na dúvida, confere um a um) e todos estão ao alcance.
     */
    private boolean reachesAll(Port source, @Nullable Level destinationLevel, int range, boolean crossDimension) {
        if (destinationLevel == null || source.destination || destinationEndpoints.contains(endpointKey(source))) {
            return false;
        }
        if (source.node.getLevel() != destinationLevel) {
            return crossDimension;
        }
        BlockPos pos = source.node.getBlockPos();
        return destinationBox.allWithin(pos.getX(), pos.getY(), pos.getZ(), range);
    }

    private static long endpointKey(Port port) {
        return port.machinePos.asLong() * 6 + port.face.ordinal();
    }

    /** A ordem de destinos da lista em {@code scratch}, dividida com as origens de lista igual. */
    private RoundRobinOrder.Layout<Port> layoutFor(List<Port> scratch) {
        RoundRobinOrder.Layout<Port> layout = layouts.get(scratch);
        if (layout == null) {
            List<Port> key = List.copyOf(scratch);
            layout = new RoundRobinOrder.Layout<>(key, port -> port.priority);
            layouts.put(key, layout);
        }
        return layout;
    }

    /**
     * Junta as origens e os destinos do tipo. Um membro que não mudou desde a última montagem entra
     * com as portas já lidas; os outros têm as faces relidas ({@link #readPorts}).
     */
    private void collectPorts(ResourceType type) {
        for (int m = 0, n = members.size(); m < n; m++) {
            NodePorts member = members.get(m);
            if (!id.equals(member.network(type))) {
                continue;
            }
            Port[] active = member.collected(type);
            if (active == null) {
                member.clearRoutes(type);
                active = readPorts(member, type);
                member.setCollected(type, active);
            }
            for (Port port : active) {
                port.clearOrder();
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
        return switch (type) {
            case ITEM -> tier.itemsPerSecond().get();
            case FLUID, CHEMICAL -> tier.fluidPerSecond().get();
            case ENERGY -> RateLimiter.perSecondFromPerTick(tier.energyPerTick().get());
        };
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
