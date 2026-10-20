package io.github.matheusanbs.wirelessautomate.menu;

import io.github.matheusanbs.wirelessautomate.block.RouterBlockEntity;
import io.github.matheusanbs.wirelessautomate.network.FaceConfig;
import io.github.matheusanbs.wirelessautomate.network.NodeIndex;
import io.github.matheusanbs.wirelessautomate.network.NodeIndex.NodeKey;
import io.github.matheusanbs.wirelessautomate.network.RelativeSide;
import io.github.matheusanbs.wirelessautomate.network.ResourceType;
import io.github.matheusanbs.wirelessautomate.network.RouterPreset;
import io.github.matheusanbs.wirelessautomate.preset.PresetApplier;
import io.github.matheusanbs.wirelessautomate.preset.ProfileSides;
import io.github.matheusanbs.wirelessautomate.preset.ProfileSource;
import io.github.matheusanbs.wirelessautomate.range.MachineConfig;
import io.github.matheusanbs.wirelessautomate.range.MachineGroup;
import io.github.matheusanbs.wirelessautomate.range.RemoteMachine;
import io.github.matheusanbs.wirelessautomate.range.RemoteMachines;
import io.github.matheusanbs.wirelessautomate.range.RemoteSide;
import java.util.Map;
import net.minecraft.server.level.ServerPlayer;

/**
 * O Colar nos marcados com máquinas (etapa 3b), por roteador, num lote só ({@code batchChanges}): a presa como no
 * colar de sempre; o grupo inteiro com o perfil do grupo, e as exceções dele ficam (D2); a máquina com a exceção dela.
 * Nas máquinas, os lados do perfil passam do quadro do roteador de origem para os da máquina ({@link ProfileSides}).
 * A regra das redes é a do {@link PresetApplier}: a face cuja rede o jogador não pode usar fica com a que tinha.
 */
final class MarkedPaste {
    private static final ResourceType[] TYPES = ResourceType.values();
    private static final RelativeSide[] SIDES = RelativeSide.values();
    private static final RemoteSide[] REMOTE_SIDES = RemoteSide.values();

    /**
     * O que o colar fez.
     *
     * @param routers        presas que receberam
     * @param groups         grupos inteiros que receberam o perfil
     * @param machines       máquinas que receberam a exceção
     * @param exceptionsKept exceções que continuam nos grupos colados
     * @param skipped        roteadores descarregados ou fora do alcance, de fora com as máquinas deles
     * @param refusedOld     havia máquinas ou grupos e o perfil não tem o quadro (copiado antes da etapa 3b)
     */
    record Result(int routers, int groups, int machines, int exceptionsKept, int skipped, boolean refusedOld) {
        boolean pastedAny() {
            return routers + groups + machines > 0;
        }
    }

    private MarkedPaste() {
    }

    /**
     * Cola o perfil {@code profile} nos marcados, menos a presa da origem; o que cada roteador mudou vai para
     * {@code undo}. A regra das redes ({@code rule}) já foi decidida por quem chama.
     */
    static Result paste(ServerPlayer viewer, Map<NodeKey, MarkedTargets.Marked> marked, PresetApplier.Checked rule,
            ProfileSource source, Map<NodeKey, PasteUndo> undo) {
        NodeIndex index = NodeIndex.get(viewer.server);
        RouterPreset preset = rule.preset();
        int routers = 0;
        int groups = 0;
        int machines = 0;
        int kept = 0;
        int skipped = 0;
        boolean refusedOld = false;
        for (Map.Entry<NodeKey, MarkedTargets.Marked> entry : marked.entrySet()) {
            NodeKey key = entry.getKey();
            MarkedTargets.Marked marks = entry.getValue();
            RouterBlockEntity router = TabletNodes.loadedRouter(viewer.server, index, marks.entry);
            if (router == null || !TabletMenu.inReach(viewer, router)) {
                skipped++;
                continue;
            }
            MarkedTargets.Resolved targets = MarkedTargets.resolve(router, marks);
            boolean attached = (marks.node || targets.attached()) && !key.equals(source.origin());
            boolean remote = targets.remote();
            if (remote && !source.pastesOnMachines()) {
                refusedOld = true;
                remote = false;
            }
            if (!attached && !remote) {
                continue;
            }
            PasteUndo.State before = PasteUndo.State.of(router, remote);
            boolean onMachines = remote;
            router.batchChanges(() -> {
                if (attached) {
                    preset.applyTo(router, null);
                }
                if (onMachines) {
                    pasteRemote(router, targets, preset, source.frame());
                }
            });
            if (attached) {
                routers++;
            }
            if (remote) {
                groups += targets.groups().size();
                machines += targets.machines().size();
                kept += exceptionsIn(router.remoteMachines(), targets);
            }
            PasteUndo change = PasteUndo.between(before, PasteUndo.State.of(router, remote), attached);
            if (change != null) {
                undo.put(key, change);
            }
        }
        return new Result(routers, groups, machines, kept, skipped, refusedOld);
    }

    private static void pasteRemote(RouterBlockEntity router, MarkedTargets.Resolved targets, RouterPreset preset,
            int frame) {
        RemoteMachines remotes = router.remoteMachines();
        for (int id : targets.groups()) {
            MachineGroup group = remotes.group(id);
            if (group != null) {
                MachineConfig config = group.profile().copy();
                merge(config, preset, frame);
                router.setGroupProfile(id, config);
            }
        }
        for (int i : targets.machines()) {
            RemoteMachine machine = remotes.machine(i);
            if (machine != null) {
                MachineConfig config = remotes.effective(machine).copy();
                merge(config, preset, frame);
                router.setMachineException(i, config);
            }
        }
    }

    /**
     * Põe as faces do perfil em {@code into}, cada lado relativo no lado da máquina pelo quadro {@code frame}: modo,
     * prioridade, redstone e filtro sempre; a rede só quando a face do perfil leva uma (senão fica a que a máquina
     * tem), como o colar na presa.
     */
    static void merge(MachineConfig into, RouterPreset preset, int frame) {
        for (ResourceType type : TYPES) {
            for (RelativeSide side : SIDES) {
                FaceConfig from = preset.face(type, side);
                FaceConfig to = into.face(type, REMOTE_SIDES[ProfileSides.toRemote(side.ordinal(), frame)]);
                to.copySettingsFrom(from);
                if (from.network() != null) {
                    to.setNetwork(from.network());
                }
            }
        }
    }

    /** As máquinas com exceção nos grupos colados. */
    private static int exceptionsIn(RemoteMachines remotes, MarkedTargets.Resolved targets) {
        int count = 0;
        for (RemoteMachine machine : remotes.machines()) {
            if (machine.hasException() && targets.groups().contains(machine.group())) {
                count++;
            }
        }
        return count;
    }
}
