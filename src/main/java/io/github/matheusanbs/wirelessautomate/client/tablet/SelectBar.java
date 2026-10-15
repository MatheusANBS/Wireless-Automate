package io.github.matheusanbs.wirelessautomate.client.tablet;

import static io.github.matheusanbs.wirelessautomate.client.tablet.TabletLayout.ROW_H;
import static io.github.matheusanbs.wirelessautomate.client.tablet.TabletLayout.X0;
import static io.github.matheusanbs.wirelessautomate.client.tablet.TabletText.tr;
import static io.github.matheusanbs.wirelessautomate.client.tablet.TabletText.typeName;

import io.github.matheusanbs.wirelessautomate.client.FlatButton;
import io.github.matheusanbs.wirelessautomate.menu.TabletMenu;
import io.github.matheusanbs.wirelessautomate.menu.TabletSnapshot;
import io.github.matheusanbs.wirelessautomate.menu.TabletSnapshot.NetworkView;
import io.github.matheusanbs.wirelessautomate.network.LoadedTypes;
import io.github.matheusanbs.wirelessautomate.network.NodeIndex.NodeKey;
import io.github.matheusanbs.wirelessautomate.network.ResourceType;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Os botões fixos da aba Lista: Selecionar e, selecionando, o tipo, a rede e o Mover, o Todos e o Colar nos
 * marcados. Guarda a escolha do Mover (tipo e rede).
 */
final class SelectBar {
    /** Tipos que o Mover oferece, depois de "Todas as abas". */
    private static final List<ResourceType> MOVE_TYPES = LoadedTypes.LIST;

    private final TabletLayout layout;
    private final Supplier<TabletSnapshot> snapshot;
    private final NodeText nodes;
    private final TabletActions actions;
    private final TabletList list;
    /** Índice em {@link #MOVE_TYPES}; -1 = todas as abas. */
    private int moveType = -1;
    private Optional<UUID> moveNetwork = Optional.empty();
    private boolean moveNetworkChosen;
    // [Selecionar, tipo, rede, Mover, Todos, Colar nos marcados]
    private final List<FlatButton> buttons = new ArrayList<>();

    SelectBar(TabletLayout layout, Supplier<TabletSnapshot> snapshot, NodeText nodes, TabletActions actions,
            TabletList list) {
        this.layout = layout;
        this.snapshot = snapshot;
        this.nodes = nodes;
        this.actions = actions;
        this.list = list;
    }

    private TabletSnapshot snapshot() {
        return snapshot.get();
    }

    void addButtons(TabletScreen.Buttons adder, Font font) {
        buttons.clear();
        int x = layout.left;
        int lw = list.listWidth();
        int selectW = Math.max(56, font.width(tr("select")) + 10);
        add(adder, new FlatButton(x + X0 + lw - selectW, list.barY(), selectW, ROW_H, tr("select"),
                (g, b, hovered) -> TabletButtons.paintToggle(g, b, hovered, list.multi() ? tr("select.done") : tr("select"),
                        list.multi()),
                list::toggleMulti).tooltip(() -> tr("select.tooltip")));
        // barra de mover (só selecionando): tipo, rede e Mover
        int cycleW = (lw - 4) * 36 / 100;
        add(adder, new FlatButton(x + X0, list.multiBarY(), cycleW, ROW_H, Component.empty(),
                (g, b, hovered) -> TabletButtons.paintCycle(g, b, hovered, moveTypeText()),
                () -> moveType = Math.floorMod(moveType + 1 + (Screen.hasShiftDown() ? -1 : 1), MOVE_TYPES.size() + 1) - 1)
                .tooltip(() -> tr("move.type.tooltip")));
        add(adder, new FlatButton(x + X0 + cycleW + 2, list.multiBarY(), cycleW, ROW_H, Component.empty(),
                (g, b, hovered) -> TabletButtons.paintCycle(g, b, hovered, nodes.networkName(effectiveMoveNetwork())),
                () -> cycleMoveNetwork(Screen.hasShiftDown() ? -1 : 1)).tooltip(() -> tr("move.network.tooltip")));
        add(adder, new FlatButton(x + X0 + 2 * cycleW + 4, list.multiBarY(), lw - 2 * cycleW - 4, ROW_H, tr("move"),
                (g, b, hovered) -> TabletButtons.paintPrimary(g, b, hovered, tr("move")), this::moveSelected)
                .tooltip(() -> tr("move.tooltip", list.selected().size())));
        int allW = font.width(tr("select.all")) + 10;
        add(adder, new FlatButton(x + X0, list.barY(), allW, ROW_H, tr("select.all"),
                (g, b, hovered) -> TabletButtons.paintText(g, b, hovered, tr("select.all")), list::selectAll)
                .tooltip(() -> tr("select.all.tooltip")));
        // colar o perfil nos marcados (só com perfil copiado)
        add(adder, new FlatButton(x + X0 + allW + 2, list.barY(), lw - allW - selectW - 4, ROW_H, Component.empty(),
                (g, b, hovered) -> TabletButtons.paintClipped(g, b, hovered, tr("list.paste_selected", list.selected().size())),
                list::pasteSelected).tooltip(() -> snapshot().profile()
                        .map(p -> tr("list.paste_selected.tooltip", p.sourceName(), list.selected().size())).orElse(null)));
    }

    private void add(TabletScreen.Buttons adder, FlatButton button) {
        buttons.add(adder.add(TabletScreen.Tab.LIST, button));
    }

    void refresh(boolean shown) {
        for (int i = 1; i <= 4; i++) {
            buttons.get(i).visible = shown && list.multi();
        }
        buttons.get(3).active = !list.selected().isEmpty();
        FlatButton pasteSelected = buttons.get(5);
        pasteSelected.visible = shown && list.multi() && snapshot().profile().isPresent();
        pasteSelected.active = !list.selected().isEmpty();
    }

    /** Snapshot novo: sem rede escolhida no Mover, vale a ativa. */
    void onSnapshot(TabletSnapshot s) {
        if (!moveNetworkChosen) {
            moveNetwork = s.activeNetwork();
        }
    }

    private Component moveTypeText() {
        return moveType < 0 ? tr("move.type.all") : typeName(MOVE_TYPES.get(moveType));
    }

    private Optional<UUID> effectiveMoveNetwork() {
        return moveNetwork.filter(id -> snapshot().network(id).isPresent());
    }

    private void cycleMoveNetwork(int direction) {
        // as redes em que o jogador pode pôr nós: as dele, as públicas e, para operador, todas
        List<NetworkView> networks = snapshot().networks();
        // posições: 0..n-1 as redes, n = sem rede
        int current = networks.size();
        Optional<UUID> chosen = effectiveMoveNetwork();
        for (int i = 0; i < networks.size(); i++) {
            if (chosen.isPresent() && networks.get(i).id().equals(chosen.get())) {
                current = i;
            }
        }
        int next = Math.floorMod(current + direction, networks.size() + 1);
        moveNetwork = next == networks.size() ? Optional.empty() : Optional.of(networks.get(next).id());
        moveNetworkChosen = true;
    }

    private void moveSelected() {
        if (list.selected().isEmpty()) {
            return;
        }
        List<NodeKey> keys = new ArrayList<>(list.selected());
        if (keys.size() > TabletMenu.MAX_MOVE) {
            keys = keys.subList(0, TabletMenu.MAX_MOVE);
        }
        actions.moveNodes(keys, moveType < 0 ? Optional.empty() : Optional.of(MOVE_TYPES.get(moveType)),
                effectiveMoveNetwork());
        list.toggleMulti();
    }
}
