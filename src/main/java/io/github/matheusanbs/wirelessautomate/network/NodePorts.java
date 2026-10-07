package io.github.matheusanbs.wirelessautomate.network;

import io.github.matheusanbs.wirelessautomate.block.RouterBlockEntity;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.Direction;
import org.jetbrains.annotations.Nullable;

/**
 * Estado de um nó carregado no {@link NetworkManager}: a rede de cada tipo (aba) e as portas,
 * criadas sob demanda. O nó é membro de toda rede em que algum tipo dele está.
 */
final class NodePorts {
    private static final int FACES = Direction.values().length;
    private static final ResourceType[] TYPES = ResourceType.values();

    final RouterBlockEntity node;
    /**
     * Rede de cada tipo (por {@link ResourceType#ordinal()}) nas rotas; pode diferir de
     * {@code node.networkId(type)} até o próximo {@code nodeChanged}.
     */
    private final UUID[] networks = new UUID[TYPES.length];
    /** [tipo × 6 + face absoluta]. */
    private final Port[] ports = new Port[ResourceType.values().length * FACES];

    NodePorts(RouterBlockEntity node) {
        this.node = node;
    }

    Port port(ResourceType type, Direction face) {
        int index = type.ordinal() * FACES + face.ordinal();
        Port port = ports[index];
        if (port == null) {
            port = new Port(node, face, type);
            ports[index] = port;
        }
        return port;
    }

    @Nullable UUID network(ResourceType type) {
        return networks[type.ordinal()];
    }

    void setNetwork(ResourceType type, @Nullable UUID network) {
        networks[type.ordinal()] = network;
    }

    /** Algum tipo do nó está na rede {@code network}. */
    boolean inNetwork(UUID network) {
        for (UUID id : networks) {
            if (network.equals(id)) {
                return true;
            }
        }
        return false;
    }

    /** Limpa os campos de rota das portas de um tipo, sem tocar nas dos outros (que podem estar noutra rede). */
    void clearRoutes(ResourceType type) {
        int first = type.ordinal() * FACES;
        for (int i = first; i < first + FACES; i++) {
            Port port = ports[i];
            if (port != null) {
                port.clearRoute();
            }
        }
    }

    /** A máquina mudou: acorda as portas do nó e as origens que entregam nos destinos dele. */
    void wake() {
        for (Port port : ports) {
            if (port == null) {
                continue;
            }
            port.sourceBackoff.wake();
            port.destinationBackoff.wake();
            // O inventário mudou: a volta sem achar nada recomeça do zero.
            port.idleSlots = 0;
            if (port.destination) {
                wakeAll(port.feeders);
                wakeAll(port.sharedFeeders);
            }
        }
    }

    private static void wakeAll(List<Port> sources) {
        for (int i = 0, n = sources.size(); i < n; i++) {
            sources.get(i).sourceBackoff.wake();
        }
    }
}
