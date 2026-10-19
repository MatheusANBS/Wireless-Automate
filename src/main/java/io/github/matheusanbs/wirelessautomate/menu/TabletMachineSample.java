package io.github.matheusanbs.wirelessautomate.menu;

import io.github.matheusanbs.wirelessautomate.block.RouterBlockEntity;
import io.github.matheusanbs.wirelessautomate.menu.TabletRows.MachineLive;
import io.github.matheusanbs.wirelessautomate.menu.TabletRows.MachineStatus;
import io.github.matheusanbs.wirelessautomate.network.NodeIndex.NodeKey;
import io.github.matheusanbs.wirelessautomate.network.NodeProbe;
import io.github.matheusanbs.wirelessautomate.network.RateMath;
import io.github.matheusanbs.wirelessautomate.network.ResourceType;
import io.github.matheusanbs.wirelessautomate.range.RangeRules;
import io.github.matheusanbs.wirelessautomate.range.RemoteMachine;
import io.github.matheusanbs.wirelessautomate.range.RemoteMachines;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import java.util.Arrays;
import java.util.Iterator;
import net.minecraft.core.BlockPos;
import org.jetbrains.annotations.Nullable;

/**
 * O estado e a vazão das linhas de máquina do Tablet, parte da amostra do servidor ({@link TabletSample}) e a mesma
 * para todos os jogadores. Mede só as máquinas que alguma página pede ({@link #live}), no máximo uma vez por amostra:
 * a primeira página que pede uma máquina numa amostra a mede, as outras leem o que ficou. A vazão é a diferença dos
 * totais das portas da máquina ({@link NodeProbe#machineTotals}) desde a medida da amostra anterior; uma máquina que
 * ninguém pediu na amostra anterior começa de novo, sem vazão. As que ninguém pede numa amostra saem na seguinte.
 *
 * <p>Só a thread do servidor mexe.
 */
final class TabletMachineSample {
    private static final ResourceType[] TYPES = ResourceType.values();

    /** As máquinas medidas de cada nó, por {@code pos.asLong()}. */
    private final Object2ObjectOpenHashMap<NodeKey, NodeSlots> nodes = new Object2ObjectOpenHashMap<>();
    /** A amostra atual, contada por {@link #beginRound}. */
    private int round;
    private final long[] got = new long[TYPES.length];
    private final long[] gave = new long[TYPES.length];

    /**
     * Uma amostra nova começou: saem as máquinas que ninguém pediu na anterior. Na linha de base (Tablets fechados
     * por mais de duas amostras), saem todas, para a primeira vazão não ser a média do intervalo inteiro.
     */
    void beginRound(boolean baseline) {
        round++;
        if (baseline) {
            nodes.clear();
            return;
        }
        Iterator<NodeSlots> byNode = nodes.values().iterator();
        while (byNode.hasNext()) {
            NodeSlots node = byNode.next();
            node.machines.values().removeIf(slot -> slot.round < round - 1);
            if (node.machines.isEmpty()) {
                byNode.remove();
            }
        }
    }

    /**
     * O estado e a vazão da máquina {@code index} (em {@code pos}) do nó {@code key}, medidos uma vez por amostra.
     * Com o roteador descarregado ({@code router} nulo), descarregada e sem vazão. Uma máquina que mudou de índice
     * desde a leitura do índice segue pela posição.
     */
    MachineLive live(@Nullable RouterBlockEntity router, NodeKey key, int index, long pos, long now) {
        if (router == null) {
            return MachineLive.still(MachineStatus.UNLOADED);
        }
        NodeSlots node = nodes.get(key);
        if (node == null) {
            node = new NodeSlots();
            nodes.put(key, node);
        }
        Slot slot = node.machines.get(pos);
        if (slot == null) {
            slot = new Slot();
            node.machines.put(pos, slot);
        } else if (slot.round == round) {
            return slot.live;
        }
        slot.live = measure(router, node, slot, index, pos, now);
        slot.round = round;
        return slot.live;
    }

    private MachineLive measure(RouterBlockEntity router, NodeSlots node, Slot slot, int index, long pos, long now) {
        boolean consecutive = slot.hasTotals && slot.round == round - 1;
        slot.hasTotals = false;
        if (!router.rangeActive()) {
            return MachineLive.still(MachineStatus.NO_UPGRADE);
        }
        RemoteMachines remotes = router.remoteMachines();
        RemoteMachine remote = remotes.machine(index);
        int at = index;
        if (remote == null || remote.pos().asLong() != pos) {
            at = remotes.index(BlockPos.of(pos));
            remote = remotes.machine(at);
        }
        if (remote == null) {
            return MachineLive.still(MachineStatus.MISSING);
        }
        if (node.reachRound != round || node.reach.length != remotes.size()) {
            node.reach = router.machineReach();
            node.reachRound = round;
        }
        MachineStatus status = switch (RouterSnapshot.remoteState(router, remote, node.reach[at])) {
            case UNLOADED -> MachineStatus.UNLOADED;
            case OUT_OF_RANGE -> MachineStatus.OUT_OF_RANGE;
            case OVER_LIMIT -> MachineStatus.OVER_LIMIT;
            case REPLACED -> MachineStatus.REPLACED;
            case MISSING -> MachineStatus.MISSING;
            case LOADED -> null;
        };
        if (status != null) {
            return MachineLive.still(status);
        }
        Arrays.fill(got, 0);
        Arrays.fill(gave, 0);
        boolean full = NodeProbe.machineTotals(router, pos, got, gave, now);
        long elapsed = Math.max(1, now - slot.tick);
        long[] rates = null;
        boolean moved = false;
        int types = TYPES.length;
        for (int t = 0; t < types; t++) {
            long received = got[t] - slot.totals[t];
            long given = gave[t] - slot.totals[types + t];
            if (consecutive && (received > 0 || given > 0)) {
                moved = true;
                if (rates == null) {
                    rates = new long[2 * types];
                }
                boolean perTick = TYPES[t].ratePerTick();
                rates[t] = RateMath.rate(received, elapsed, perTick);
                rates[types + t] = RateMath.rate(given, elapsed, perTick);
            }
            slot.totals[t] = got[t];
            slot.totals[types + t] = gave[t];
        }
        slot.hasTotals = true;
        slot.tick = now;
        MachineStatus measured = full ? MachineStatus.FULL : moved ? MachineStatus.MOVING : MachineStatus.SLEEPING;
        return rates == null ? MachineLive.still(measured) : new MachineLive(measured, rates);
    }

    /** As máquinas medidas de um nó e o alcance delas na amostra (lido uma vez por nó e amostra). */
    private static final class NodeSlots {
        final Long2ObjectOpenHashMap<Slot> machines = new Long2ObjectOpenHashMap<>();
        RangeRules.Reach[] reach = new RangeRules.Reach[0];
        int reachRound = -1;
    }

    /** Uma máquina medida: os totais da última medida e o que ela deu. */
    private static final class Slot {
        /** Em {@code [ordinal]} o recebido e em {@code [tipos + ordinal]} o entregue, como na última medida. */
        final long[] totals = new long[2 * TYPES.length];
        boolean hasTotals;
        long tick;
        int round = Integer.MIN_VALUE;
        MachineLive live = MachineLive.still(MachineStatus.SLEEPING);
    }
}
