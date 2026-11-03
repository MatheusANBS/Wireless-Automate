package io.github.matheusanbs.wirelessautomate.client.tablet.machine;

import io.github.matheusanbs.wirelessautomate.block.RouterTier;
import io.github.matheusanbs.wirelessautomate.menu.TabletMachineRows;
import io.github.matheusanbs.wirelessautomate.menu.TabletRows.MachineLive;
import io.github.matheusanbs.wirelessautomate.menu.TabletRows.MachineStatus;
import io.github.matheusanbs.wirelessautomate.network.NodeIndex.NodeKey;
import io.github.matheusanbs.wirelessautomate.network.ResourceType;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/**
 * A página de máquinas do servidor (blocos, máquinas e "mais N") achatada na lista da aba Máquina: os cabeçalhos de
 * bloco saem e cada máquina leva o bloco do cabeçalho de antes. Sem classes de tela, para os GameTests montarem a
 * página à mão; o estado e o nome de cada linha são do {@link MachineState} (lógica pura).
 */
public final class MachineListRows {
    private static final int TYPES = ResourceType.values().length;

    private MachineListRows() {
    }

    public enum Kind { MACHINE, MORE }

    /**
     * Uma linha da lista.
     *
     * @param router o roteador da máquina; {@code null} no "mais N"
     * @param label  o rótulo dado pelo jogador, vazio = sem
     * @param number o "#N" da vinculada; 0 na presa e no "mais N"
     * @param hidden no "mais N", quantas máquinas faltam
     */
    public record Entry(Kind kind, @Nullable NodeKey router, RouterTier tier, boolean attached, int index, int number,
            BlockPos pos, ResourceLocation block, String label, MachineStatus status, MachineState.State state,
            int hidden) {
        public boolean machine() {
            return kind == Kind.MACHINE;
        }

        /** O nome na lista, dado o nome do bloco. */
        public String name(String blockName) {
            return MachineState.name(label, attached, number, blockName);
        }

        /** A mesma máquina que {@code other}: o mesmo roteador e a mesma presa ou o mesmo índice. */
        public boolean sameMachine(Entry other) {
            return machine() && other.machine() && router.equals(other.router) && attached == other.attached
                    && (attached || index == other.index);
        }
    }

    /** As linhas da página, na ordem dela e sem os cabeçalhos de bloco. */
    public static List<Entry> of(TabletMachineRows page) {
        List<Entry> entries = new ArrayList<>(page.rows().size());
        ResourceLocation block = null;
        int live = 0;
        for (TabletMachineRows.Row row : page.rows()) {
            switch (row) {
                case TabletMachineRows.Block header -> block = header.block();
                case TabletMachineRows.More more -> entries.add(new Entry(Kind.MORE, null, RouterTier.BASIC, false,
                        -1, 0, BlockPos.ZERO, more.block(), "", MachineStatus.SLEEPING,
                        MachineState.of(MachineStatus.SLEEPING, new long[0]), more.hidden()));
                case TabletMachineRows.Machine machine -> {
                    MachineLive state = live < page.lives().size() ? page.lives().get(live) : null;
                    live++;
                    entries.add(machine(page, machine, block, state));
                }
            }
        }
        return entries;
    }

    private static Entry machine(TabletMachineRows page, TabletMachineRows.Machine machine,
            @Nullable ResourceLocation block, @Nullable MachineLive live) {
        TabletMachineRows.RouterRef router = page.routers().get(machine.router());
        MachineStatus status = live == null ? MachineStatus.UNLOADED : live.status();
        long[] flow = new long[TYPES];
        if (live != null) {
            for (ResourceType type : ResourceType.values()) {
                flow[type.ordinal()] = live.received(type) + live.given(type);
            }
        }
        return new Entry(Kind.MACHINE, router.key(), router.tier(), machine.attached(), machine.index(),
                machine.number(), BlockPos.of(machine.pos()), block, machine.label(), status,
                MachineState.of(status, flow), 0);
    }
}
