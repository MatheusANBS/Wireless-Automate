package io.github.matheusanbs.wirelessautomate.network;

import io.github.matheusanbs.wirelessautomate.block.RouterBlockEntity;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.Direction;
import org.jetbrains.annotations.Nullable;

/** Estado de um nó carregado no {@link NetworkManager}: a rede em que está e as portas, criadas sob demanda. */
final class NodePorts {
    private static final int FACES = Direction.values().length;

    final RouterBlockEntity node;
    /** Rede em que o nó está nas rotas; pode diferir de {@code node.networkId()} até o próximo {@code nodeChanged}. */
    @Nullable UUID network;
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

    void clearRoutes() {
        for (Port port : ports) {
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
                List<Port> feeders = port.feeders;
                for (int i = 0, n = feeders.size(); i < n; i++) {
                    feeders.get(i).sourceBackoff.wake();
                }
            }
        }
    }
}
