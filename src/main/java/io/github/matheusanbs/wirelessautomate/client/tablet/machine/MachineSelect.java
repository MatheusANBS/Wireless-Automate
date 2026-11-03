package io.github.matheusanbs.wirelessautomate.client.tablet.machine;

import io.github.matheusanbs.wirelessautomate.client.tablet.machine.MachineListRows.Entry;
import io.github.matheusanbs.wirelessautomate.menu.RouterSnapshot;
import io.github.matheusanbs.wirelessautomate.menu.TabletMenu;
import io.github.matheusanbs.wirelessautomate.network.NodeIndex.NodeKey;
import io.github.matheusanbs.wirelessautomate.packet.TabletPanelPayload;
import io.github.matheusanbs.wirelessautomate.range.EditTarget;
import java.util.Objects;
import org.jetbrains.annotations.Nullable;

/**
 * A máquina escolhida da aba Máquina. Quem decide é o servidor: o painel que ele manda ({@link TabletPanelPayload}) traz
 * o nó e o alvo, então o clique com o Tablet numa máquina abre a aba com ela escolhida sem código próprio. O clique na
 * lista manda o nó e o alvo ({@link #choose}) e vale como a escolha até o painel novo chegar (ou {@link #PENDING_TICKS}
 * passarem), para a faixa da linha não esperar a ida e volta.
 */
final class MachineSelect {
    private static final int PENDING_TICKS = 60;

    /** O roteador e a máquina: a presa ({@code index} −1) ou a vinculada. */
    record Choice(NodeKey router, boolean attached, int index) {
        boolean matches(Entry entry) {
            return entry.machine() && router.equals(entry.router()) && attached == entry.attached()
                    && (attached || index == entry.index());
        }
    }

    private final TabletMenu menu;
    private final MachineActions send;
    private @Nullable Choice pending;
    private int pendingTicks;
    private int panelVersion = -1;
    private @Nullable Choice fromPanel;

    MachineSelect(TabletMenu menu, MachineActions send) {
        this.menu = menu;
        this.send = send;
    }

    /** O que a lista mostra como escolhido: o clique que espera o painel, senão o painel do servidor. */
    @Nullable Choice current() {
        Choice panel = panel();
        if (pending != null) {
            if (pending.equals(panel) || pendingTicks <= 0) {
                pending = null;
            } else {
                return pending;
            }
        }
        return panel;
    }

    /** A cada tique da tela. */
    void tick() {
        if (pendingTicks > 0) {
            pendingTicks--;
        }
    }

    /** A escolha do último painel do servidor; um alvo de grupo (ou sem o painel lido) não é máquina. */
    private @Nullable Choice panel() {
        if (menu.panelVersion() != panelVersion) {
            panelVersion = menu.panelVersion();
            fromPanel = read(menu.panel());
        }
        return fromPanel;
    }

    private static @Nullable Choice read(@Nullable TabletPanelPayload payload) {
        if (payload == null || payload.node().isEmpty() || payload.snapshot().isEmpty()) {
            return null;
        }
        EditTarget target = payload.snapshot().map(RouterSnapshot::target).orElse(null);
        if (target == null || target.kind() == EditTarget.Kind.GROUP) {
            return null;
        }
        return target.attached() ? new Choice(payload.node().get(), true, -1)
                : new Choice(payload.node().get(), false, target.id());
    }

    /** O clique numa máquina: o nó (só se mudou) e depois o alvo, como a árvore da Lista faz. */
    void choose(Entry entry) {
        Choice choice = new Choice(entry.router(), entry.attached(), entry.attached() ? -1 : entry.index());
        if (choice.equals(current())) {
            return;
        }
        pending = choice;
        pendingTicks = PENDING_TICKS;
        TabletPanelPayload payload = menu.panel();
        if (payload == null || !Objects.equals(payload.node().orElse(null), entry.router())) {
            send.selectNode(entry.router());
        }
        send.selectTarget(entry);
    }
}
