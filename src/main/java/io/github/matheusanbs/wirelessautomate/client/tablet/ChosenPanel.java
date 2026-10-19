package io.github.matheusanbs.wirelessautomate.client.tablet;

import io.github.matheusanbs.wirelessautomate.client.router.NodePanel;
import io.github.matheusanbs.wirelessautomate.menu.PanelAccess;
import io.github.matheusanbs.wirelessautomate.menu.RouterSnapshot;
import io.github.matheusanbs.wirelessautomate.menu.TabletMenu;
import io.github.matheusanbs.wirelessautomate.network.NodeIndex.NodeKey;
import io.github.matheusanbs.wirelessautomate.network.ResourceType;
import io.github.matheusanbs.wirelessautomate.packet.TabletPanelPayload;
import io.github.matheusanbs.wirelessautomate.packet.TabletProfilePayload.Action;
import java.util.List;
import java.util.Optional;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * O painel do nó escolhido na Lista ({@link NodePanel}), em dia com o último {@link TabletPanelPayload} do
 * servidor: um nó novo ganha um painel novo na mesma aba, um painel atrasado de outro nó não vale e, quando o
 * servidor larga a escolha, a Lista larga também.
 */
final class ChosenPanel {
    private final TabletList list;
    private final TabletMenu menu;
    private @Nullable NodePanel panel;
    private @Nullable NodeKey panelNode;
    private int panelVersion = -1;
    /** O nó do último painel do servidor e a versão do painel já conferida por {@link #followServerDrop}. */
    private @Nullable NodeKey serverNode;
    private int dropCheckedVersion = -1;

    ChosenPanel(TabletList list, TabletMenu menu, @Nullable NodeKey restored) {
        this.list = list;
        this.menu = menu;
        this.serverNode = restored;
    }

    /** O painel do servidor para o nó escolhido; um painel atrasado de outro nó não vale. */
    @Nullable TabletPanelPayload payload() {
        TabletPanelPayload payload = menu.panel();
        return payload != null && list.chosen != null && payload.node().equals(Optional.of(list.chosen)) ? payload : null;
    }

    /** O componente do painel do nó escolhido, ou {@code null} sem nó ou antes de o servidor responder. */
    @Nullable NodePanel panel() {
        syncPanel();
        return panel;
    }

    /** O painel como ficou no último {@link #panel()}, sem conferir de novo. */
    @Nullable NodePanel current() {
        return panel;
    }

    /** Põe o painel em dia com o último {@link TabletPanelPayload}: um nó novo ganha um painel novo na mesma aba. */
    private void syncPanel() {
        TabletPanelPayload payload = payload();
        Optional<RouterSnapshot> snap = payload == null ? Optional.empty() : payload.snapshot();
        if (snap.isEmpty()) {
            panel = null;
            panelNode = null;
            return;
        }
        RouterSnapshot s = snap.get();
        if (panel == null || !list.chosen.equals(panelNode)) {
            ResourceType type = panel != null ? panel.type() : list.chipType;
            panel = new NodePanel(new PanelActions(), s, s.range().present() ? s.range().face() : s.facing());
            panel.select(type);
            panelNode = list.chosen;
        } else if (panelVersion != menu.panelVersion()) {
            panel.setSnapshot(s);
        }
        panelVersion = menu.panelVersion();
        panel.readOnly(readOnlyReason(payload));
        list.chipType = panel.type();
    }

    /** O motivo do só leitura, ou {@code null} quando o painel edita. */
    static @Nullable Component readOnlyReason(TabletPanelPayload payload) {
        if (payload.access() == PanelAccess.EDIT) {
            return null;
        }
        if (payload.access() == PanelAccess.READ_UNLOADED) {
            return Component.translatable("gui.wirelessautomate.panel.readonly.unloaded");
        }
        Component tier = payload.snapshot().map(s -> Component.translatable(s.tier().translationKey()))
                .orElse(Component.empty());
        return payload.distance() < 0 ? Component.translatable("gui.wirelessautomate.panel.readonly.dimension", tier)
                : Component.translatable("gui.wirelessautomate.panel.readonly.range", tier, payload.range());
    }

    boolean canEdit() {
        TabletPanelPayload payload = payload();
        return payload != null && payload.access() == PanelAccess.EDIT;
    }

    /** O que o painel do Tablet manda: pelo menu do Tablet (o nó escolhido é o alvo no servidor). */
    private final class PanelActions implements NodePanel.Actions {
        @Override
        public int containerId() {
            return list.actions.containerId();
        }

        @Override
        public void send(CustomPacketPayload payload) {
            list.actions.send(payload);
        }

        @Override
        public void undoPaste() {
            list.actions.profile(Action.UNDO, null, null, List.of());
        }

        @Override
        public void clearIdleNetworks(boolean undo) {
            list.actions.clearIdleNetworks(undo);
        }

        @Override
        public @Nullable ResourceKey<Level> dimension() {
            return list.chosen == null ? null : list.chosen.dimension();
        }
    }

    /**
     * O servidor largou a escolha (o nó sumiu ou deixou de ser visível) e mandou um painel sem nó: larga aqui
     * também, para um clique novo na mesma linha pedir o nó de novo. Só se a escolha daqui é a que o servidor
     * tinha; um painel sem nó atrasado não larga um nó escolhido depois.
     */
    void followServerDrop() {
        int version = menu.panelVersion();
        if (version == dropCheckedVersion) {
            return;
        }
        dropCheckedVersion = version;
        TabletPanelPayload payload = menu.panel();
        if (payload == null) {
            return;
        }
        if (payload.node().isPresent()) {
            serverNode = payload.node().get();
            return;
        }
        if (list.chosen != null && list.chosen.equals(serverNode)) {
            list.chosen = null;
        }
        serverNode = null;
    }
}
