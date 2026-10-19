package io.github.matheusanbs.wirelessautomate.network;

import io.github.matheusanbs.wirelessautomate.block.RouterBlockEntity;
import io.github.matheusanbs.wirelessautomate.range.MachineCaches;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.core.Direction;
import org.jetbrains.annotations.Nullable;

/**
 * Estado de um nó carregado no {@link NetworkManager}: as redes distintas de cada tipo (as que
 * aparecem nas faces dele: presa, perfis e exceções) e as portas, criadas sob demanda. A rede é da
 * porta ({@link Port#faceNetwork}); o nó é membro de toda rede em que alguma face dele está, de
 * qualquer tipo e em qualquer modo.
 *
 * <p>A leitura das portas de um tipo ({@link #collected}) é uma só, dividida entre as redes do tipo:
 * a primeira rede a montar o tipo depois de uma mudança relê as faces (e limpa as rotas de todas as
 * portas do tipo), e as portas relidas ficam marcadas ({@link Port#pendingFresh}) até a rede de cada
 * uma consumi-las. Por isso o {@link NetworkManager#nodeChanged} suja o tipo em todas as redes do nó.
 */
final class NodePorts {
    private static final int FACES = Direction.values().length;
    private static final ResourceType[] TYPES = ResourceType.values();

    final RouterBlockEntity node;
    /** Nenhuma rede. */
    static final UUID[] NO_NETWORKS = new UUID[0];

    /**
     * As redes distintas de cada tipo (por {@link ResourceType#ordinal()}) nas rotas; podem diferir de
     * {@code node.networks(type)} até o próximo {@code nodeChanged}.
     */
    private final UUID[][] networks = new UUID[TYPES.length][];
    /**
     * Portas da máquina presa: [tipo][face absoluta]. O arranjo das faces de um tipo nasce com a primeira porta
     * dele: o nó paga só pelos tipos que usa.
     */
    private final Port[][] ports = new Port[TYPES.length][];
    /**
     * Portas das máquinas remotas, por {@code pos.asLong()} da máquina, cada uma no mesmo arranjo
     * [tipo][face absoluta], com as faces de um tipo criadas na primeira porta dele; {@code null} até a
     * primeira remota (um nó sem remotas não paga nada).
     * As de máquinas que deixaram de ser porta (desvinculadas, fora do raio ou do limite, sem o
     * upgrade) saem na releitura do tipo ({@link #retainRemotes}).
     */
    private @Nullable Long2ObjectOpenHashMap<Port[][]> remotes;
    /**
     * Portas ativas de cada tipo, com os campos da configuração da face já preenchidos, da última
     * leitura; {@code null} = ler de novo na próxima montagem. Toda mudança do nó que mexe nas rotas
     * passa por {@link NetworkManager#nodeChanged}, que descarta a leitura.
     */
    private final Port[][] collected = new Port[TYPES.length][];
    /** O balde de fase do amostrador dos Fluxos e a posição do nó nele ({@link FlowSampler#add}). */
    @Nullable List<NodePorts> flowPhase;
    int flowSlot = -1;
    /** A configuração que o amostrador dos Fluxos contou; {@code null} até a primeira leitura. */
    FlowSampler.@Nullable NodeConfig flowConfig;

    NodePorts(RouterBlockEntity node) {
        this.node = node;
        Arrays.fill(networks, NO_NETWORKS);
    }

    Port port(ResourceType type, Direction face) {
        Port[] byFace = faces(ports, type);
        Port port = byFace[face.ordinal()];
        if (port == null) {
            port = new Port(node, node.attachedCaches(), face, type);
            byFace[face.ordinal()] = port;
        }
        return port;
    }

    /** As faces do tipo em {@code byType}, criadas na primeira vez. */
    private static Port[] faces(Port[][] byType, ResourceType type) {
        Port[] byFace = byType[type.ordinal()];
        if (byFace == null) {
            byFace = new Port[FACES];
            byType[type.ordinal()] = byFace;
        }
        return byFace;
    }

    /**
     * A porta da máquina remota {@code machine} na face (absoluta) e tipo, criada na primeira vez. A
     * identidade é (nó, posição da máquina, tipo, face): sobrevive às remontagens enquanto a máquina
     * continuar sendo porta.
     */
    Port remotePort(MachineCaches machine, ResourceType type, Direction face) {
        if (remotes == null) {
            remotes = new Long2ObjectOpenHashMap<>();
        }
        long key = machine.pos().asLong();
        Port[][] byType = remotes.get(key);
        if (byType == null) {
            byType = new Port[TYPES.length][];
            remotes.put(key, byType);
        }
        Port[] byFace = faces(byType, type);
        Port port = byFace[face.ordinal()];
        if (port == null || port.machine != machine) {
            // Uma MachineCaches nova para a mesma posição (desvinculada e vinculada de novo): porta nova.
            port = new Port(node, machine, face, type);
            byFace[face.ordinal()] = port;
        }
        return port;
    }

    /** As portas da máquina presa, [tipo][face absoluta] com os tipos sem porta nulos (não alterar). */
    Port[][] attachedPorts() {
        return ports;
    }

    /** O nó tem portas de máquinas remotas (de alguma leitura). */
    boolean hasRemotes() {
        return remotes != null;
    }

    /**
     * As portas da máquina remota em {@code machinePos} ({@code pos.asLong()}), [tipo][face absoluta] com os tipos
     * sem porta nulos, ou {@code null}; não cria.
     */
    Port @Nullable [][] machinePorts(long machinePos) {
        return remotes == null ? null : remotes.get(machinePos);
    }

    /** A porta da máquina remota em {@code machinePos} ({@code pos.asLong()}), se já foi criada; não cria. */
    @Nullable Port peekRemote(long machinePos, ResourceType type, Direction face) {
        Port[][] byType = machinePorts(machinePos);
        Port[] byFace = byType == null ? null : byType[type.ordinal()];
        return byFace == null ? null : byFace[face.ordinal()];
    }

    /**
     * Na releitura do tipo: tira desse tipo as portas das máquinas remotas fora de {@code keep}
     * (posições que continuam sendo porta) e descarta as máquinas que ficaram sem porta nenhuma.
     */
    void retainRemotes(ResourceType type, LongSet keep) {
        if (remotes == null) {
            return;
        }
        int t = type.ordinal();
        ObjectIterator<Long2ObjectMap.Entry<Port[][]>> iterator = remotes.long2ObjectEntrySet().fastIterator();
        while (iterator.hasNext()) {
            Long2ObjectMap.Entry<Port[][]> entry = iterator.next();
            if (keep.contains(entry.getLongKey())) {
                continue;
            }
            Port[][] byType = entry.getValue();
            byType[t] = null;
            boolean empty = true;
            for (Port[] byFace : byType) {
                if (byFace != null) {
                    empty = false;
                    break;
                }
            }
            if (empty) {
                iterator.remove();
            }
        }
        if (remotes.isEmpty()) {
            remotes = null;
        }
    }

    /** Chama {@code action} em cada porta já criada de uma máquina remota no tipo; não cria nada. */
    void forEachRemote(ResourceType type, Consumer<Port> action) {
        if (remotes == null) {
            return;
        }
        int t = type.ordinal();
        for (Port[][] byType : remotes.values()) {
            Port[] byFace = byType[t];
            if (byFace == null) {
                continue;
            }
            for (Port port : byFace) {
                if (port != null) {
                    action.accept(port);
                }
            }
        }
    }

    /** A porta, se já foi criada; não cria. */
    @Nullable Port peek(ResourceType type, Direction face) {
        Port[] byFace = ports[type.ordinal()];
        return byFace == null ? null : byFace[face.ordinal()];
    }

    /** As redes distintas do tipo nas rotas (não alterar o arranjo). */
    UUID[] networks(ResourceType type) {
        return networks[type.ordinal()];
    }

    void setNetworks(ResourceType type, UUID[] ids) {
        networks[type.ordinal()] = ids;
    }

    /** Alguma face do tipo está na rede {@code network}. */
    boolean inNetwork(ResourceType type, UUID network) {
        return contains(networks[type.ordinal()], network);
    }

    /** Alguma face de algum tipo do nó está na rede {@code network}. */
    boolean inNetwork(UUID network) {
        for (UUID[] ids : networks) {
            if (contains(ids, network)) {
                return true;
            }
        }
        return false;
    }

    static boolean contains(UUID[] ids, UUID network) {
        for (UUID id : ids) {
            if (network.equals(id)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Tira das rotas da rede {@code owner} as portas do tipo que ela adotou (presa e remotas): só a rede
     * e a ordem, sem descartar a leitura nem tocar nas portas do mesmo tipo que estão noutra rede.
     */
    void clearRoutes(ResourceType type, NetworkRoutes owner) {
        int t = type.ordinal();
        clearRoutes(ports[t], owner);
        if (remotes != null) {
            for (Port[][] byType : remotes.values()) {
                clearRoutes(byType[t], owner);
            }
        }
    }

    private static void clearRoutes(Port @Nullable [] byFace, NetworkRoutes owner) {
        if (byFace == null) {
            return;
        }
        for (Port port : byFace) {
            if (port != null && port.network == owner) {
                port.network = null;
                port.clearOrder();
            }
        }
    }

    /**
     * Limpa os campos de rota das portas de um tipo, de todas as redes, e descarta a leitura dele, sem
     * tocar nos outros tipos. Só para a releitura ({@link NetworkRoutes}): toda rede com porta desse
     * tipo no nó está suja e remonta na mesma passada.
     */
    void clearRoutes(ResourceType type) {
        int t = type.ordinal();
        collected[t] = null;
        clearRoutes(ports[t]);
        if (remotes != null) {
            for (Port[][] byType : remotes.values()) {
                clearRoutes(byType[t]);
            }
        }
    }

    private static void clearRoutes(Port @Nullable [] byFace) {
        if (byFace == null) {
            return;
        }
        for (Port port : byFace) {
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
        int t = type.ordinal();
        rememberOrders(ports[t]);
        if (remotes != null) {
            for (Port[][] byType : remotes.values()) {
                rememberOrders(byType[t]);
            }
        }
    }

    private static void rememberOrders(Port @Nullable [] byFace) {
        if (byFace == null) {
            return;
        }
        for (Port port : byFace) {
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
     * A máquina presa mudou: acorda as portas dela e, com {@code feeders}, as origens que esperam
     * destino e entregam nos destinos dela. Sem {@code feeders} é uma entrega nossa na máquina: ela
     * ganhou recursos, o que não abre espaço para ninguém, então só as portas da própria máquina
     * acordam (ela pode ser origem de outra rota e agora ter o que dar). As remotas acordam pela
     * própria máquina ({@link #wakeMachine}).
     */
    void wake(boolean feeders) {
        wake(ports, feeders);
    }

    /**
     * Como {@link #wake(boolean)}, nas portas da máquina remota em {@code machinePos}
     * ({@code pos.asLong()}); nada se ela não tem porta (fora do raio ou do limite, ou ainda não lida).
     */
    void wakeMachine(long machinePos, boolean feeders) {
        Port[][] byType = machinePorts(machinePos);
        if (byType != null) {
            wake(byType, feeders);
        }
    }

    private static void wake(Port[][] byType, boolean feeders) {
        for (Port[] byFace : byType) {
            if (byFace == null) {
                continue;
            }
            for (Port port : byFace) {
                if (port == null) {
                    continue;
                }
                wakePort(port);
                if (feeders && port.destination && !port.stopped) {
                    wakeFeeders(port);
                }
            }
        }
    }

    /** O nó saiu do motor: para todas as portas já criadas, da presa e das remotas ({@link Port#stop}). */
    void stopAll(long now) {
        stopAll(ports, now);
        if (remotes != null) {
            for (Port[][] byType : remotes.values()) {
                stopAll(byType, now);
            }
        }
    }

    private static void stopAll(Port[][] byType, long now) {
        for (Port[] byFace : byType) {
            if (byFace == null) {
                continue;
            }
            for (Port port : byFace) {
                if (port != null) {
                    port.stop(now);
                }
            }
        }
    }

    /**
     * O sinal de redstone do roteador mudou: trava ou destrava as portas já lidas com controle por redstone, sem
     * remontar ({@link Port#applyRedstone}). A que destrava acorda as origens que esperam por ela, mesmo que outro
     * aviso do mesmo tick já tenha passado pelo filtro do {@link Port#feedersWokenAt}; as de um tipo ainda por ler
     * pegam o sinal na leitura.
     */
    void poweredChanged(boolean powered, long now) {
        for (Port[] active : collected) {
            if (active == null) {
                continue;
            }
            for (Port port : active) {
                if (port.redstone != RedstoneMode.IGNORE && port.applyRedstone(powered, now) && port.destination) {
                    port.feedersWokenAt = NetworkManager.lastTick();
                    wakeAllFeeders(port);
                }
            }
        }
    }

    /**
     * A capability de uma face mudou (máquina trocada, chunk na borda, mod que invalida): acorda só
     * as portas dessa face e tipo e as origens que esperam por elas. As rotas não dependem de
     * capability, então nada é remontado.
     */
    void capabilityChanged(ResourceType type, Direction face) {
        capabilityChanged(peek(type, face));
    }

    /** Como {@link #capabilityChanged(ResourceType, Direction)}, numa máquina remota ({@code pos.asLong()}). */
    void capabilityChanged(long machinePos, ResourceType type, Direction face) {
        capabilityChanged(peekRemote(machinePos, type, face));
    }

    private static void capabilityChanged(@Nullable Port port) {
        if (port == null) {
            return;
        }
        wakePort(port);
        if (port.destination && !port.stopped) {
            wakeFeeders(port);
        }
    }

    private static void wakePort(Port port) {
        if (port.stopped) {
            return;
        }
        port.sourceBackoff.wake();
        port.destinationBackoff.wake();
        // O inventário mudou: a volta sem achar nada recomeça do zero.
        port.idleSlots = 0;
    }

    private static void wakeFeeders(Port destination) {
        long tick = NetworkManager.lastTick();
        if (destination.feedersWokenAt == tick) {
            return;
        }
        destination.feedersWokenAt = tick;
        wakeAllFeeders(destination);
    }

    private static void wakeAllFeeders(Port destination) {
        wakeWaiting(destination.feeders);
        wakeWaiting(destination.sharedFeeders);
        wakeWaiting(destination.sharedBothFeeders);
    }

    /** Acorda as origens que dormem esperando destino; as vazias ficam para a própria máquina acordar. */
    private static void wakeWaiting(List<Port> sources) {
        for (int i = 0, n = sources.size(); i < n; i++) {
            Port source = sources.get(i);
            if (source.waitsDestination && !source.stopped) {
                source.sourceBackoff.wake();
            }
        }
    }
}
