package io.github.matheusanbs.wirelessautomate.network;

import io.github.matheusanbs.wirelessautomate.Config;
import io.github.matheusanbs.wirelessautomate.block.RouterBlockEntity;
import io.github.matheusanbs.wirelessautomate.block.RouterTier;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/**
 * Rotas pré-montadas de uma rede e o que o profiler mede dela. A montagem roda só quando a rede
 * fica suja (ver {@link NetworkManager#nodeChanged}); o laço de transferência apenas lê o resultado.
 *
 * <p>Regras da montagem, por tipo de recurso:
 * <ul>
 *   <li>origem = face ativa (modo e redstone) que extrai; destino = face ativa que insere;
 *   <li>uma origem não entrega na mesma máquina e mesma face de onde tira;
 *   <li>alcance e dimensão vêm do tier da <b>origem</b>: mesma dimensão e distância euclidiana entre
 *       os roteadores até o alcance (0 = dimensão inteira); outra dimensão só com {@code crossDimension},
 *       e aí sem limite de distância;
 *   <li>a taxa do balde da origem é relida da config a cada montagem;
 *   <li>o filtro da face vai para a porta ({@link Port#filter}); o laço só o consulta, e o matcher
 *       compilado dele vive no próprio {@code Filter}, então remontar não recompila um filtro igual.
 * </ul>
 */
final class NetworkRoutes {
    static final ResourceType[] TRANSFER_TYPES = {ResourceType.ITEM, ResourceType.FLUID, ResourceType.ENERGY};
    private static final Direction[] DIRECTIONS = Direction.values();
    private static final double AVERAGE_WEIGHT = 0.05;

    final UUID id;
    final List<NodePorts> members = new ArrayList<>();
    /** Todas as origens ativas, de todos os tipos, inclusive as sem destino. */
    final List<Port> sources = new ArrayList<>();
    final List<Port> destinations = new ArrayList<>();
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
        for (int i = 0, n = members.size(); i < n; i++) {
            members.get(i).clearRoutes();
        }
        sources.clear();
        destinations.clear();
        if (!exists) {
            return;
        }
        for (ResourceType type : TRANSFER_TYPES) {
            int firstSource = sources.size();
            int firstDestination = destinations.size();
            collectPorts(type);
            for (int i = firstSource; i < sources.size(); i++) {
                Port source = sources.get(i);
                Config.TierValues tier = Config.TIERS.get(source.tier);
                source.limiter.setRatePerSecond(ratePerSecond(type, tier));
                int range = tier.range().get();
                boolean crossDimension = tier.crossDimension().get();
                scratch.clear();
                for (int j = firstDestination; j < destinations.size(); j++) {
                    Port destination = destinations.get(j);
                    if (!destination.sameEndpoint(source) && reachable(source, destination, range, crossDimension)) {
                        scratch.add(destination);
                        destination.feeders.add(source);
                    }
                }
                source.order = scratch.isEmpty() ? null : new RoundRobinOrder<>(scratch, port -> port.priority);
                // Rotas novas podem destravar uma origem que dormia por falta de destino.
                source.sourceBackoff.wake();
            }
        }
        scratch.clear();
    }

    private void collectPorts(ResourceType type) {
        for (int m = 0, n = members.size(); m < n; m++) {
            NodePorts member = members.get(m);
            RouterBlockEntity node = member.node;
            boolean powered = node.powered();
            RouterTier tier = null;
            BlockPos machine = null;
            for (Direction face : DIRECTIONS) {
                FaceConfig config = node.face(type, face);
                if (!config.isActive(powered)) {
                    continue;
                }
                if (tier == null) {
                    tier = node.tier();
                    machine = node.machinePos();
                }
                Port port = member.port(type, face);
                port.network = this;
                port.priority = config.priority();
                port.filter = config.filter();
                port.tier = tier;
                port.machinePos = machine;
                if (config.mode().extracts()) {
                    port.source = true;
                    sources.add(port);
                }
                if (config.mode().inserts()) {
                    port.destination = true;
                    destinations.add(port);
                }
            }
        }
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
