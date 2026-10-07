package io.github.matheusanbs.wirelessautomate.network;

import io.github.matheusanbs.wirelessautomate.block.RouterBlockEntity;
import net.minecraft.core.Direction;

/**
 * Leitura do estado do motor para o Tablet: só lê as portas de um nó carregado, sem criar rotas nem
 * acordar nada. O menu do Tablet aberto só sonda os nós da página mostrada (e, com o filtro de
 * problemas, todos uma vez por amostra); os cheios de cada rede vêm contados nas estatísticas do
 * gerenciador ({@link NetworkStats#destinationsFull()}).
 */
public final class NodeProbe {
    /**
     * Um destino que já dorme com este intervalo (ticks) recusou várias vezes seguidas: conta como
     * cheio. Uma recusa isolada (intervalo 1 ou 2) é o vai e vem normal de um inventário quase cheio.
     */
    static final int FULL_INTERVAL = 8;
    private static final Direction[] DIRECTIONS = Direction.values();

    private NodeProbe() {
    }

    /** Destinos de {@code type} do nó que estão cheios agora (ver {@link #FULL_INTERVAL}). 0 se o nó não está no motor. */
    public static int fullDestinations(RouterBlockEntity node, ResourceType type, long now) {
        NodePorts ports = NetworkManager.get().ports(node);
        if (ports == null || ports.network(type) == null) {
            return 0;
        }
        int count = 0;
        for (Direction face : DIRECTIONS) {
            if (!node.face(type, face).mode().inserts()) {
                continue;
            }
            Port port = ports.port(type, face);
            if (port.destination && isFull(port, now)) {
                count++;
            }
        }
        return count;
    }

    /** O destino dorme depois de recusas seguidas (ver {@link #FULL_INTERVAL}). */
    static boolean isFull(Port port, long now) {
        return port.destinationBackoff.isSleeping(now) && port.destinationBackoff.nextIntervalTicks() >= FULL_INTERVAL;
    }
}
