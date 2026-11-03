package io.github.matheusanbs.wirelessautomate.network;

import io.github.matheusanbs.wirelessautomate.block.RouterBlockEntity;
import io.github.matheusanbs.wirelessautomate.network.VerdictFacts.FaceFact;
import io.github.matheusanbs.wirelessautomate.network.VerdictFacts.Reach;
import io.github.matheusanbs.wirelessautomate.range.MachineConfig;
import io.github.matheusanbs.wirelessautomate.range.RemoteMachine;
import io.github.matheusanbs.wirelessautomate.range.RemoteSide;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import org.jetbrains.annotations.Nullable;

/**
 * Lê os fatos de uma máquina (a presa ou uma remota) para o veredito do Tablet: o modo e a rede de cada face vêm da
 * configuração (uma face sem rede não tem rota, então a porta dela não é montada nem relida); o que o motor faz vem das
 * portas que já existem ({@link NodePorts#peek}, {@link NodePorts#peekRemote}), sem criar nenhuma. Nunca consulta
 * capability nem o mundo: quem chama já resolveu o alcance ({@link Reach}) e só chama com a máquina carregada.
 * Uma passada por segundo, de uma máquina só: até {@code tipos × 6} faces e, para cada origem, os destinos dela.
 */
public final class VerdictProbe {
    private static final Direction[] DIRECTIONS = Direction.values();
    private static final FaceSide[] SIDES = FaceSide.values();

    private VerdictProbe() {
    }

    /**
     * Os fatos da máquina em {@code machine} ({@code null} = a presa do roteador). Com {@code reach} diferente de
     * {@link Reach#OK}, só o alcance, sem olhar as portas. As faces saem ordenadas por tipo ({@link LoadedTypes#LIST})
     * e depois por face, e só as que têm modo.
     */
    public static VerdictFacts read(RouterBlockEntity node, @Nullable BlockPos machine, Reach reach, long now) {
        if (reach != Reach.OK) {
            return new VerdictFacts(reach, List.of());
        }
        MinecraftServer server = node.getLevel() == null ? null : node.getLevel().getServer();
        if (server == null) {
            return new VerdictFacts(Reach.UNLOADED, List.of());
        }
        NodePorts ports = NetworkManager.get().ports(node);
        NetworkSavedData data = NetworkSavedData.get(server);
        List<FaceFact> faces = new ArrayList<>(4);
        if (machine == null) {
            readAttached(node, ports, data, now, faces);
        } else {
            RemoteMachine remote = node.remoteMachines().machine(node.remoteMachines().index(machine));
            if (remote == null) {
                return new VerdictFacts(Reach.MISSING, List.of());
            }
            readRemote(node, remote, ports, data, now, faces);
        }
        return new VerdictFacts(Reach.OK, faces);
    }

    private static void readAttached(RouterBlockEntity node, @Nullable NodePorts ports, NetworkSavedData data, long now,
            List<FaceFact> out) {
        for (ResourceType type : LoadedTypes.LIST) {
            for (Direction face : DIRECTIONS) {
                var config = node.face(type, face);
                if (config.mode() == PortMode.NONE) {
                    continue;
                }
                Port port = ports == null ? null : ports.peek(type, face);
                out.add(fact(type, face, config.mode(), config.network(), port, data, now));
            }
        }
    }

    private static void readRemote(RouterBlockEntity node, RemoteMachine remote, @Nullable NodePorts ports,
            NetworkSavedData data, long now, List<FaceFact> out) {
        MachineConfig config = node.remoteMachines().effective(remote);
        long key = remote.pos().asLong();
        for (ResourceType type : LoadedTypes.LIST) {
            // As seis faces absolutas, na ordem do Direction (a mesma de FaceSide), cada uma pelo lado dela na máquina.
            for (Direction face : DIRECTIONS) {
                RemoteSide side = RemoteSide.fromAbsolute(remote.front(), face.ordinal());
                var faceConfig = config.face(type, side);
                if (faceConfig.mode() == PortMode.NONE) {
                    continue;
                }
                Port port = ports == null ? null : ports.peekRemote(key, type, face);
                out.add(fact(type, face, faceConfig.mode(), faceConfig.network(), port, data, now));
            }
        }
    }

    private static FaceFact fact(ResourceType type, Direction face, PortMode mode, @Nullable UUID network,
            @Nullable Port port, NetworkSavedData data, long now) {
        boolean source = mode.extracts();
        boolean destination = mode.inserts();
        boolean paused = network != null && data.isPaused(network);
        if (port == null) {
            return new FaceFact(type, SIDES[face.get3DDataValue()], source, destination, network, paused, false, false,
                    false, false, 0, List.of(), false, false);
        }
        int full = 0;
        List<Long> positions = List.of();
        RoundRobinOrder<Port> order = port.order;
        if (source && order != null) {
            RoundRobinOrder.Layout<Port> layout = order.layout();
            for (int i = 0, n = layout.size(); i < n; i++) {
                Port target = layout.destination(i);
                if (!NodeProbe.countsAsFull(target, now)) {
                    continue;
                }
                full++;
                long pos = target.machinePos.asLong();
                if (positions.size() < Verdict.MAX_POSITIONS && !positions.contains(pos)) {
                    if (positions.isEmpty()) {
                        positions = new ArrayList<>(Verdict.MAX_POSITIONS);
                    }
                    positions.add(pos);
                }
            }
        }
        boolean moving = port.got > 0 || port.gave > 0;
        return new FaceFact(type, SIDES[face.get3DDataValue()], source, destination, network, paused, port.stopped, moving,
                source && port.sourceBackoff.isSleeping(now), port.waitsDestination, full, positions,
                destination && NodeProbe.isFull(port, now), !port.filter.isEmpty());
    }
}
