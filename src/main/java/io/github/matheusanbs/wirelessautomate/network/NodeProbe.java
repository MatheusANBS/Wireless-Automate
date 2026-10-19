package io.github.matheusanbs.wirelessautomate.network;

import io.github.matheusanbs.wirelessautomate.block.RouterBlockEntity;
import net.minecraft.core.Direction;

/**
 * Leitura do estado do motor para o Tablet: só lê as portas de um nó carregado, sem criar rotas nem
 * acordar nada. O menu do Tablet aberto só sonda os nós da página mostrada (e, com o filtro de
 * problemas, todos uma vez por amostra); os cheios de cada rede vêm contados pelo amostrador dos Fluxos
 * ({@link FlowSampler}) e, no {@code /wa profile}, pelo gerenciador ({@link NetworkStats#destinationsFull()}).
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

    /**
     * Destinos de {@code type} do nó que estão cheios agora (ver {@link #FULL_INTERVAL}), os da máquina presa
     * e os das remotas. Só olha as portas que já existem, e delas as que estão numa rota ({@link Port#network}):
     * uma face que saiu de Insere ou perdeu a rede sai da contagem quando as rotas são remontadas, mesmo com
     * o sono do destino ainda correndo. Não cria nenhuma porta. 0 se o nó não está no motor.
     */
    public static int fullDestinations(RouterBlockEntity node, ResourceType type, long now) {
        NodePorts ports = NetworkManager.get().ports(node);
        if (ports == null || ports.networks(type).length == 0) {
            return 0;
        }
        int[] count = {0};
        for (Direction face : DIRECTIONS) {
            Port port = ports.peek(type, face);
            if (port != null && countsAsFull(port, now)) {
                count[0]++;
            }
        }
        ports.forEachRemote(type, port -> {
            if (countsAsFull(port, now)) {
                count[0]++;
            }
        });
        return count[0];
    }

    /**
     * Soma em {@code got} e {@code gave} (por {@link ResourceType#ordinal()}) o total que as portas da máquina remota
     * em {@code machinePos} ({@code pos.asLong()}) receberam e entregaram desde que existem ({@link Port#gotRead} +
     * {@link Port#got}, e o mesmo da entrega). Devolve se algum destino dela com rota está cheio agora. Não cria
     * nenhuma porta nem aloca; uma máquina sem porta (ou um nó fora do motor) não soma nada.
     */
    public static boolean machineTotals(RouterBlockEntity node, long machinePos, long[] got, long[] gave, long now) {
        NodePorts ports = NetworkManager.get().ports(node);
        Port[][] byType = ports == null ? null : ports.machinePorts(machinePos);
        if (byType == null) {
            return false;
        }
        boolean full = false;
        for (int t = 0; t < byType.length; t++) {
            Port[] byFace = byType[t];
            if (byFace == null) {
                continue;
            }
            for (Port port : byFace) {
                if (port == null) {
                    continue;
                }
                got[t] += port.gotRead + port.got;
                gave[t] += port.gaveRead + port.gave;
                full |= countsAsFull(port, now);
            }
        }
        return full;
    }

    /** Um destino com rota, cheio agora. */
    private static boolean countsAsFull(Port port, long now) {
        return port.network != null && port.destination && !port.stopped && isFull(port, now);
    }

    /** O destino dorme depois de recusas seguidas (ver {@link #FULL_INTERVAL}). */
    static boolean isFull(Port port, long now) {
        return port.destinationBackoff.isSleeping(now) && port.destinationBackoff.nextIntervalTicks() >= FULL_INTERVAL;
    }
}
