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
    /**
     * Portas ativas de cada tipo, com os campos da configuração da face já preenchidos, da última
     * leitura; {@code null} = ler de novo na próxima montagem. Toda mudança do nó que mexe nas rotas
     * passa por {@link NetworkManager#nodeChanged}, que descarta a leitura.
     */
    private final Port[][] collected = new Port[TYPES.length][];

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

    /** A porta, se já foi criada; não cria. */
    @Nullable Port peek(ResourceType type, Direction face) {
        return ports[type.ordinal() * FACES + face.ordinal()];
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

    /**
     * Limpa os campos de rota das portas de um tipo e descarta a leitura dele, sem tocar nos outros
     * tipos (que podem estar noutra rede).
     */
    void clearRoutes(ResourceType type) {
        collected[type.ordinal()] = null;
        int first = type.ordinal() * FACES;
        for (int i = first; i < first + FACES; i++) {
            Port port = ports[i];
            if (port != null) {
                port.clearRoute();
            }
        }
    }

    /** A configuração do nó mudou nos tipos de {@code types} (máscara por ordinal): a próxima montagem relê as faces deles. */
    void invalidate(int types) {
        for (int t = 0; t < TYPES.length; t++) {
            if ((types & (1 << t)) != 0) {
                collected[t] = null;
            }
        }
    }

    /**
     * Guarda a ordem atual de cada porta do tipo em {@link Port#previousOrder}, antes de a montagem
     * limpar as rotas: uma origem cujos destinos não mudaram continua com os mesmos cursores.
     */
    void rememberOrders(ResourceType type) {
        int first = type.ordinal() * FACES;
        for (int i = first; i < first + FACES; i++) {
            Port port = ports[i];
            if (port != null) {
                port.previousOrder = port.order;
            }
        }
    }

    /** As portas ativas do tipo lidas na última montagem, ou {@code null} se é preciso ler de novo. */
    Port @Nullable [] collected(ResourceType type) {
        return collected[type.ordinal()];
    }

    void setCollected(ResourceType type, Port[] active) {
        collected[type.ordinal()] = active;
    }

    /**
     * A máquina mudou: acorda as portas do nó e, com {@code feeders}, as origens que esperam destino
     * e entregam nos destinos dele. Sem {@code feeders} é uma entrega nossa na máquina: ela ganhou
     * recursos, o que não abre espaço para ninguém, então só as portas do próprio nó acordam (a
     * máquina pode ser origem de outra rota e agora ter o que dar).
     */
    void wake(boolean feeders) {
        for (Port port : ports) {
            if (port == null) {
                continue;
            }
            wakePort(port);
            if (feeders && port.destination) {
                wakeFeeders(port);
            }
        }
    }

    /**
     * A capability de uma face mudou (máquina trocada, chunk na borda, mod que invalida): acorda só
     * as portas dessa face e tipo e as origens que esperam por elas. As rotas não dependem de
     * capability, então nada é remontado.
     */
    void capabilityChanged(ResourceType type, Direction face) {
        Port port = ports[type.ordinal() * FACES + face.ordinal()];
        if (port == null) {
            return;
        }
        wakePort(port);
        if (port.destination) {
            wakeFeeders(port);
        }
    }

    private static void wakePort(Port port) {
        port.sourceBackoff.wake();
        port.destinationBackoff.wake();
        // O inventário mudou: a volta sem achar nada recomeça do zero.
        port.idleSlots = 0;
    }

    private static void wakeFeeders(Port destination) {
        wakeWaiting(destination.feeders);
        wakeWaiting(destination.sharedFeeders);
        wakeWaiting(destination.sharedBothFeeders);
    }

    /** Acorda as origens que dormem esperando destino; as vazias ficam para a própria máquina acordar. */
    private static void wakeWaiting(List<Port> sources) {
        for (int i = 0, n = sources.size(); i < n; i++) {
            Port source = sources.get(i);
            if (source.waitsDestination) {
                source.sourceBackoff.wake();
            }
        }
    }
}
