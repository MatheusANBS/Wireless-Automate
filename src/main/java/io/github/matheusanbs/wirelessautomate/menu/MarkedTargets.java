package io.github.matheusanbs.wirelessautomate.menu;

import io.github.matheusanbs.wirelessautomate.block.RouterBlockEntity;
import io.github.matheusanbs.wirelessautomate.menu.TabletMarks.MachineKey;
import io.github.matheusanbs.wirelessautomate.menu.TabletTree.GroupKey;
import io.github.matheusanbs.wirelessautomate.network.NetworkSavedData;
import io.github.matheusanbs.wirelessautomate.network.NodeIndex;
import io.github.matheusanbs.wirelessautomate.network.NodeIndex.NodeKey;
import io.github.matheusanbs.wirelessautomate.range.RemoteMachine;
import io.github.matheusanbs.wirelessautomate.range.RemoteMachines;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntLinkedOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongLinkedOpenHashSet;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.IntPredicate;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

/**
 * O que o Colar nos marcados e o Mover atingem, por roteador (etapa 3b): o roteador marcado, a presa, os grupos de
 * alcance inteiros e as máquinas uma a uma. Primeiro pelo índice ({@link #collect}: só roteadores que o jogador vê, e o
 * bloco marcado reavaliado pela consulta dele), depois pelo roteador carregado ({@link #resolve}), que decide o que é
 * um grupo inteiro (D2): todas as máquinas dele marcadas, uma a uma ou pelo bloco.
 */
final class MarkedTargets {
    /** O que foi marcado num roteador, ainda pelas posições. */
    static final class Marked {
        final NodeIndex.Entry entry;
        /** O roteador marcado (na visão por roteadores). */
        boolean node;
        boolean attached;
        final IntLinkedOpenHashSet groups = new IntLinkedOpenHashSet();
        final LongLinkedOpenHashSet machines = new LongLinkedOpenHashSet();

        Marked(NodeIndex.Entry entry) {
            this.entry = entry;
        }

        boolean remote() {
            return !groups.isEmpty() || !machines.isEmpty();
        }
    }

    /**
     * O que vale num roteador carregado.
     *
     * @param attached a presa
     * @param groups   os ids dos grupos inteiros
     * @param machines os índices das máquinas fora deles
     */
    record Resolved(boolean attached, IntArrayList groups, IntArrayList machines) {
        boolean remote() {
            return !groups.isEmpty() || !machines.isEmpty();
        }
    }

    private MarkedTargets() {
    }

    /**
     * Os marcados por roteador, na ordem em que chegaram: só os roteadores que o jogador vê; o bloco marcado vira as
     * máquinas dele na última página da visão por máquinas ({@code view}), até {@link TabletMenu#MAX_MOVE} ao todo.
     */
    static Map<NodeKey, Marked> collect(ServerPlayer viewer, Collection<NodeKey> nodes, TabletMarks marks,
            @Nullable TabletMachines view) {
        NodeIndex index = NodeIndex.get(viewer.server);
        NetworkSavedData data = NetworkSavedData.get(viewer.server);
        boolean operator = viewer.hasPermissions(2);
        Map<NodeKey, Marked> result = new LinkedHashMap<>();
        Function<NodeKey, Marked> of = key -> {
            Marked marked = result.get(key);
            if (marked != null) {
                return marked;
            }
            NodeIndex.Entry entry = index.entry(key);
            if (entry == null || !TabletNodes.visible(entry, data, operator, viewer.getUUID())) {
                return null;
            }
            marked = new Marked(entry);
            result.put(key, marked);
            return marked;
        };
        for (NodeKey key : nodes) {
            Marked marked = of.apply(key);
            if (marked != null) {
                marked.node = true;
            }
        }
        for (MachineKey machine : marks.machines()) {
            Marked marked = of.apply(machine.node());
            if (marked != null && machine.isAttached()) {
                marked.attached = true;
            } else if (marked != null) {
                marked.machines.add(machine.pos());
            }
        }
        for (GroupKey group : marks.groups()) {
            Marked marked = of.apply(group.node());
            if (marked != null) {
                marked.groups.add(group.group());
            }
        }
        if (view != null) {
            int[] left = {TabletMenu.MAX_MOVE};
            for (ResourceLocation block : marks.blocks()) {
                left[0] -= view.forEachInBlock(block, left[0], (entry, machine) -> {
                    Marked marked = of.apply(entry.key());
                    if (marked != null && machine == MachineOrder.ATTACHED) {
                        marked.attached = true;
                    } else if (marked != null) {
                        marked.machines.add(entry.remotes().pos(machine));
                    }
                });
            }
        }
        return result;
    }

    /**
     * Os alvos no roteador carregado: a presa (também pela posição dela), as máquinas pelas posições (as que saíram
     * ficam de fora) e os grupos inteiros: os marcados e os que têm todas as máquinas marcadas, que saem das
     * máquinas.
     */
    static Resolved resolve(RouterBlockEntity router, Marked marked) {
        boolean attached = marked.attached || marked.machines.contains(router.machinePos().asLong());
        RemoteMachines remotes = router.remoteMachines();
        IntLinkedOpenHashSet groups = new IntLinkedOpenHashSet();
        for (int id : marked.groups) {
            if (remotes.group(id) != null) {
                groups.add(id);
            }
        }
        IntOpenHashSet chosen = new IntOpenHashSet();
        for (long pos : marked.machines) {
            int i = remotes.index(BlockPos.of(pos));
            if (i >= 0) {
                chosen.add(i);
            }
        }
        List<RemoteMachine> all = remotes.machines();
        for (int g = 0; g < remotes.groupCount(); g++) {
            int id = remotes.groupAt(g).id();
            if (!groups.contains(id) && everyMachineOf(all, id, chosen)) {
                groups.add(id);
            }
        }
        IntPredicate inGroup = i -> groups.contains(all.get(i).group());
        IntArrayList machines = new IntArrayList();
        for (int i = 0; i < all.size(); i++) {
            if (chosen.contains(i) && !inGroup.test(i)) {
                machines.add(i);
            }
        }
        return new Resolved(attached, new IntArrayList(groups), machines);
    }

    /** O grupo tem máquinas e todas estão em {@code marked}. */
    private static boolean everyMachineOf(List<RemoteMachine> all, int group, IntOpenHashSet marked) {
        boolean any = false;
        for (int i = 0; i < all.size(); i++) {
            if (all.get(i).group() == group) {
                if (!marked.contains(i)) {
                    return false;
                }
                any = true;
            }
        }
        return any;
    }
}
