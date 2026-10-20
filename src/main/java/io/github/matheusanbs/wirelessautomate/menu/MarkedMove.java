package io.github.matheusanbs.wirelessautomate.menu;

import io.github.matheusanbs.wirelessautomate.block.RouterBlockEntity;
import io.github.matheusanbs.wirelessautomate.network.FaceConfig;
import io.github.matheusanbs.wirelessautomate.network.NodeIndex;
import io.github.matheusanbs.wirelessautomate.network.NodeIndex.NodeKey;
import io.github.matheusanbs.wirelessautomate.network.PortMode;
import io.github.matheusanbs.wirelessautomate.network.RelativeSide;
import io.github.matheusanbs.wirelessautomate.network.ResourceType;
import io.github.matheusanbs.wirelessautomate.range.MachineConfig;
import io.github.matheusanbs.wirelessautomate.range.MachineGroup;
import io.github.matheusanbs.wirelessautomate.range.RemoteMachine;
import io.github.matheusanbs.wirelessautomate.range.RemoteMachines;
import io.github.matheusanbs.wirelessautomate.range.RemoteSide;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;
import net.minecraft.server.MinecraftServer;
import org.jetbrains.annotations.Nullable;

/**
 * O Mover com máquinas marcadas (etapa 3b), por roteador, num lote só: o roteador marcado vai inteiro, como sempre
 * (presa, perfis e exceções), e as máquinas dele marcadas não contam de novo; sem ele, a presa marcada e a máquina
 * marcada (D1) mudam a rede só nas faces com modo, a da máquina gravada como exceção; o grupo inteiro (D2) muda no
 * perfil, e as exceções dele ficam. A face numa rede de onde o jogador não pode tirar fica e é contada.
 */
final class MarkedMove {
    private static final RelativeSide[] SIDES = RelativeSide.values();
    private static final RemoteSide[] REMOTE_SIDES = RemoteSide.values();

    /**
     * @param routers  roteadores inteiros
     * @param groups   grupos inteiros
     * @param machines máquinas, a presa marcada inclusive
     * @param unloaded roteadores descarregados, de fora
     * @param kept     faces em redes de outros, mantidas
     */
    record Result(int routers, int groups, int machines, int unloaded, int kept) {
    }

    private MarkedMove() {
    }

    static Result move(MinecraftServer server, Map<NodeKey, MarkedTargets.Marked> marked,
            Collection<ResourceType> types, @Nullable UUID network, Predicate<UUID> canLeave) {
        NodeIndex index = NodeIndex.get(server);
        int routers = 0;
        int groups = 0;
        int machines = 0;
        int unloaded = 0;
        int[] kept = new int[1];
        for (MarkedTargets.Marked marks : marked.values()) {
            RouterBlockEntity router = TabletNodes.loadedRouter(server, index, marks.entry);
            if (router == null) {
                unloaded++;
                continue;
            }
            if (marks.node) {
                kept[0] += router.setNetworkAllFaces(types, network, canLeave);
                routers++;
                continue;
            }
            MarkedTargets.Resolved targets = MarkedTargets.resolve(router, marks);
            router.batchChanges(() -> {
                if (targets.attached()) {
                    moveAttached(router, types, network, canLeave, kept);
                }
                RemoteMachines remotes = router.remoteMachines();
                for (int id : targets.groups()) {
                    MachineGroup group = remotes.group(id);
                    if (group != null) {
                        MachineConfig config = group.profile().copy();
                        moveFaces(config, types, network, canLeave, kept);
                        router.setGroupProfile(id, config);
                    }
                }
                for (int i : targets.machines()) {
                    RemoteMachine machine = remotes.machine(i);
                    if (machine != null) {
                        MachineConfig config = remotes.effective(machine).copy();
                        moveFaces(config, types, network, canLeave, kept);
                        router.setMachineException(i, config);
                    }
                }
            });
            groups += targets.groups().size();
            machines += targets.machines().size() + (targets.attached() ? 1 : 0);
        }
        return new Result(routers, groups, machines, unloaded, kept[0]);
    }

    /** As faces com modo da presa na rede; uma face sem modo não ganha rede. */
    private static void moveAttached(RouterBlockEntity router, Collection<ResourceType> types, @Nullable UUID network,
            Predicate<UUID> canLeave, int[] kept) {
        for (ResourceType type : types) {
            for (RelativeSide side : SIDES) {
                FaceConfig face = router.face(type, side);
                if (face.mode() != PortMode.NONE && leaves(face.network(), network, canLeave, kept)) {
                    router.setFaceNetwork(type, side, network);
                }
            }
        }
    }

    private static void moveFaces(MachineConfig config, Collection<ResourceType> types, @Nullable UUID network,
            Predicate<UUID> canLeave, int[] kept) {
        for (ResourceType type : types) {
            for (RemoteSide side : REMOTE_SIDES) {
                FaceConfig face = config.face(type, side);
                if (face.mode() != PortMode.NONE && leaves(face.network(), network, canLeave, kept)) {
                    face.setNetwork(network);
                }
            }
        }
    }

    /** A face sai da rede {@code from} para {@code to}: já está nela, não; de uma rede que o jogador não gerencia, conta e não. */
    private static boolean leaves(@Nullable UUID from, @Nullable UUID to, Predicate<UUID> canLeave, int[] kept) {
        if (java.util.Objects.equals(from, to)) {
            return false;
        }
        if (from != null && !canLeave.test(from)) {
            kept[0]++;
            return false;
        }
        return true;
    }
}
