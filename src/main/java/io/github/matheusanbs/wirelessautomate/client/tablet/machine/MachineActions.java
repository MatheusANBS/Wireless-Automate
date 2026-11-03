package io.github.matheusanbs.wirelessautomate.client.tablet.machine;

import io.github.matheusanbs.wirelessautomate.client.tablet.TabletActions;
import io.github.matheusanbs.wirelessautomate.client.tablet.machine.MachineListRows.Entry;
import io.github.matheusanbs.wirelessautomate.menu.TabletMenu;
import io.github.matheusanbs.wirelessautomate.menu.TabletSnapshot.Query;
import io.github.matheusanbs.wirelessautomate.network.NodeIndex.NodeKey;
import io.github.matheusanbs.wirelessautomate.packet.SelectTargetPayload;
import io.github.matheusanbs.wirelessautomate.packet.TabletMachinesPayload.Action;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;

/**
 * Tudo o que a aba Máquina manda ao servidor, pelo {@link TabletActions}: os componentes da aba não mandam pacote, pedem
 * à anfitriã, que passa por aqui.
 */
final class MachineActions {
    private final TabletActions actions;
    private final TabletMenu menu;

    MachineActions(TabletActions actions, TabletMenu menu) {
        this.actions = actions;
        this.menu = menu;
    }

    /** Pede a página por máquinas (a visão em que a aba funciona). */
    void machinesView() {
        actions.machines(Action.MACHINES, Optional.empty());
    }

    void query(Query query) {
        actions.query(query);
    }

    /** Abre o bloco recolhido: sem cabeçalhos na lista, uma máquina num bloco recolhido nunca apareceria. */
    void openBlock(ResourceLocation block) {
        actions.machines(Action.OPEN_BLOCK, Optional.of(block));
    }

    /** O "… mais N": mostra todas as máquinas do bloco. */
    void showAll(ResourceLocation block) {
        actions.machines(Action.SHOW_ALL, Optional.of(block));
    }

    void selectNode(NodeKey node) {
        actions.selectNode(node);
    }

    /** O alvo do painel do roteador: a presa ou a máquina da linha. */
    void selectTarget(Entry entry) {
        actions.send(entry.attached() ? SelectTargetPayload.attached(menu.containerId)
                : SelectTargetPayload.machine(menu.containerId, entry.index(), entry.pos()));
    }
}
