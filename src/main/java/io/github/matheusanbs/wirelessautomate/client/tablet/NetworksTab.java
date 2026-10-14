package io.github.matheusanbs.wirelessautomate.client.tablet;

import static io.github.matheusanbs.wirelessautomate.client.tablet.TabletLayout.BODY_Y;
import static io.github.matheusanbs.wirelessautomate.client.tablet.TabletLayout.ROW_H;
import static io.github.matheusanbs.wirelessautomate.client.tablet.TabletLayout.RX;
import static io.github.matheusanbs.wirelessautomate.client.tablet.TabletText.plural;
import static io.github.matheusanbs.wirelessautomate.client.tablet.TabletText.tr;

import io.github.matheusanbs.wirelessautomate.client.FlatButton;
import io.github.matheusanbs.wirelessautomate.client.GuiPaint;
import io.github.matheusanbs.wirelessautomate.client.GuiText;
import io.github.matheusanbs.wirelessautomate.menu.TabletMenu.Action;
import io.github.matheusanbs.wirelessautomate.menu.TabletSnapshot;
import io.github.matheusanbs.wirelessautomate.menu.TabletSnapshot.NetworkView;
import io.github.matheusanbs.wirelessautomate.network.NetworkSavedData;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.jetbrains.annotations.Nullable;

/**
 * A aba Redes do Tablet: a lista de redes à esquerda e a escolhida à direita (nome, dono, nós, membros, cor,
 * privacidade, usar como ativa e remover); a nova rede fica recolhida embaixo da lista.
 */
final class NetworksTab {
    private static final int SWATCH = 10;
    private static final int SWATCH_Y = BODY_Y + 84;

    private final TabletLayout layout;
    private final Supplier<TabletSnapshot> snapshot;
    private final TabletActions actions;
    private final SideList side;
    private final NameFields names;
    private @Nullable UUID selectedNetwork;
    private FlatButton makePrivate;
    private FlatButton makePublic;
    private FlatButton use;
    private FlatButton remove;
    private FlatButton create;

    NetworksTab(TabletLayout layout, Supplier<TabletSnapshot> snapshot, TabletActions actions, SideList side,
            NameFields names) {
        this.layout = layout;
        this.snapshot = snapshot;
        this.actions = actions;
        this.side = side;
        this.names = names;
    }

    private TabletSnapshot snapshot() {
        return snapshot.get();
    }

    @Nullable UUID selected() {
        return selectedNetwork;
    }

    private @Nullable NetworkView selectedNetworkView() {
        return selectedNetwork == null ? null : snapshot().network(selectedNetwork).orElse(null);
    }

    private @Nullable Boolean isSelectedPublic() {
        NetworkView network = selectedNetworkView();
        return network == null ? null : network.isPublic();
    }

    private boolean isSelectedActive() {
        return selectedNetwork != null && snapshot().activeNetwork().equals(Optional.of(selectedNetwork));
    }

    void addButtons(TabletScreen.Buttons buttons) {
        int x = layout.left;
        int y = layout.top;
        int rw = layout.rw();
        makePrivate = buttons.add(TabletScreen.Tab.NETWORKS, new FlatButton(x + RX, y + BODY_Y + 112, rw / 2 - 1, ROW_H,
                tr("private"),
                (g, b, hovered) -> TabletButtons.paintSegment(g, b, hovered, tr("private"), isSelectedPublic() == Boolean.FALSE),
                () -> setPublic(false)).tooltip(() -> tr("private.tooltip")));
        makePublic = buttons.add(TabletScreen.Tab.NETWORKS, new FlatButton(x + RX + rw / 2 + 1, y + BODY_Y + 112,
                rw - rw / 2 - 1, ROW_H, tr("public"),
                (g, b, hovered) -> TabletButtons.paintSegment(g, b, hovered, tr("public"), isSelectedPublic() == Boolean.TRUE),
                () -> setPublic(true)).tooltip(() -> tr("public.tooltip")));
        use = buttons.add(TabletScreen.Tab.NETWORKS, new FlatButton(x + RX, y + BODY_Y + 132, rw, ROW_H, tr("use"),
                (g, b, hovered) -> TabletButtons.paintText(g, b, hovered, isSelectedActive() ? tr("use.active") : tr("use")),
                this::useNetwork).tooltip(() -> tr("use.tooltip")));
        remove = buttons.add(TabletScreen.Tab.NETWORKS, new FlatButton(x + RX, y + layout.newBoxY(), rw, ROW_H,
                tr("network.remove"),
                (g, b, hovered) -> TabletButtons.paintDanger(g, b, hovered,
                        names.removeArmed() ? tr("remove.confirm") : tr("network.remove"), names.removeArmed()),
                () -> names.removeSelected(Action.NETWORK_REMOVE, selectedNetwork))
                .tooltip(() -> tr("network.remove.tooltip")));
        buttons.add(TabletScreen.Tab.NETWORKS, names.newToggle(tr("network.new")));
        create = buttons.add(TabletScreen.Tab.NETWORKS, names.createButton(Action.NETWORK_CREATE));
    }

    void refresh(boolean shown) {
        NetworkView network = selectedNetworkView();
        boolean manage = network != null && network.manageable();
        makePrivate.visible = makePublic.visible = shown && manage;
        use.visible = shown && network != null;
        use.active = network != null && !isSelectedActive();
        remove.visible = shown && manage;
        create.visible = shown && names.newOpen();
    }

    FlatButton removeButton() {
        return remove;
    }

    /** Snapshot novo: a rede escolhida que sumiu sai; sem nenhuma, vale a ativa ou a primeira. */
    void onSnapshot(TabletSnapshot s) {
        if (selectedNetwork != null && s.network(selectedNetwork).isEmpty()) {
            selectedNetwork = null;
            names.clearRename();
        }
        if (selectedNetwork == null && !s.networks().isEmpty()) {
            selectedNetwork = s.activeNetwork().filter(id -> s.network(id).isPresent())
                    .orElse(s.networks().get(0).id());
        }
    }

    // ------------------------------------------------------------------ ações

    private void setPublic(boolean value) {
        NetworkView network = selectedNetworkView();
        if (network != null && network.isPublic() != value) {
            actions.networkPublic(network.id(), value);
        }
    }

    private void useNetwork() {
        if (selectedNetwork != null && !isSelectedActive()) {
            actions.networkUse(selectedNetwork);
        }
    }

    private void setColor(int color) {
        if (selectedNetwork != null) {
            actions.networkColor(selectedNetwork, color);
        }
    }

    // ------------------------------------------------------------------ entrada

    /** Cor da paleta sob o mouse, ou -1. */
    private int swatchAt(double mouseX, double mouseY) {
        int[] palette = NetworkSavedData.palette();
        for (int i = 0; i < palette.length; i++) {
            int sx = layout.left + RX + i * (SWATCH + 2);
            int sy = layout.top + SWATCH_Y;
            if (mouseX >= sx && mouseX < sx + SWATCH && mouseY >= sy && mouseY < sy + SWATCH) {
                return i;
            }
        }
        return -1;
    }

    /** Cliques na lista, na paleta e no nome. Devolve se tratou. */
    boolean mouseClicked(double mouseX, double mouseY) {
        List<NetworkView> networks = snapshot().networks();
        int row = side.rowAt(mouseX, mouseY, networks.size());
        if (row >= 0) {
            selectedNetwork = networks.get(row).id();
            names.clearRename();
            names.disarm();
            return true;
        }
        NetworkView network = selectedNetworkView();
        if (network != null && network.manageable()) {
            int swatch = swatchAt(mouseX, mouseY);
            if (swatch >= 0) {
                setColor(NetworkSavedData.palette()[swatch]);
                return true;
            }
            if (!names.renaming() && names.overTitle(mouseX, mouseY)) {
                names.startRename(NameFields.Rename.NETWORK, network.name());
                return true;
            }
        }
        return false;
    }

    @Nullable Component tooltip(int mouseX, int mouseY) {
        NetworkView network = selectedNetworkView();
        if (network != null && network.manageable() && swatchAt(mouseX, mouseY) >= 0) {
            return tr("color.tooltip");
        }
        if (network != null && network.manageable() && !names.renaming() && names.overTitle(mouseX, mouseY)) {
            return tr("rename.tooltip");
        }
        return null;
    }

    void preview(int index) {
        selectedNetwork = snapshot().networks().get(index).id();
    }

    // ------------------------------------------------------------------ desenho

    void render(GuiGraphics g, Font font, int mouseX, int mouseY) {
        TabletSnapshot s = snapshot();
        List<NetworkView> networks = s.networks();
        side.render(g, font, mouseX, mouseY, networks.size(), i -> {
            NetworkView n = networks.get(i);
            boolean active = s.activeNetwork().equals(Optional.of(n.id()));
            Component mark = active ? tr("mark.active") : n.isPublic() ? tr("mark.public") : null;
            return new SideList.Row(Component.literal(n.name()), 0xFF000000 | n.color(), n.id().equals(selectedNetwork), mark);
        }, tr("networks.empty"));
        names.renderNewFrame(g);

        int x = layout.left + RX;
        int y = layout.top;
        int rw = layout.rw();
        NetworkView n = selectedNetworkView();
        if (n == null) {
            GuiText.draw(g, font, tr("network.pick"), x, y + BODY_Y + 3, rw, GuiPaint.MUTED);
            return;
        }
        names.renderTitle(g, font, mouseX, mouseY, Component.literal(n.name()), 0xFF000000 | n.color(), n.manageable());
        Component owner = n.owned() ? tr("owner.you") : tr("owner", n.owner());
        GuiText.draw(g, font, owner, x, y + BODY_Y + 18, rw, GuiPaint.MUTED);
        MutableComponent nodes = plural("meta.nodes", n.nodes()).copy();
        if (n.unloaded() > 0) {
            nodes.append(", ").append(plural("network.unloaded", n.unloaded()));
        }
        GuiText.draw(g, font, nodes, x, y + BODY_Y + 29, rw, GuiPaint.MUTED);
        Component members = n.isPublic() ? tr("members.public") : tr("members.private", n.owner());
        GuiText.draw(g, font, members, x, y + BODY_Y + 40, rw, GuiPaint.MUTED);
        if (n.manageable()) {
            GuiPaint.text(g, font, tr("color"), x, y + SWATCH_Y - 11, GuiPaint.FG);
            int[] palette = NetworkSavedData.palette();
            for (int i = 0; i < palette.length; i++) {
                int sx = x + i * (SWATCH + 2);
                int sy = y + SWATCH_Y;
                boolean current = (palette[i] & 0xFFFFFF) == n.color();
                GuiPaint.box(g, sx, sy, SWATCH, SWATCH, 0xFF000000 | palette[i], current ? GuiPaint.FG : GuiPaint.BEVEL_DARK);
            }
            GuiPaint.text(g, font, tr("privacy"), x, y + BODY_Y + 101, GuiPaint.FG);
        } else {
            GuiText.wrap(g, font, tr("network.foreign"), x, y + SWATCH_Y, rw, 3, GuiPaint.DISABLED);
        }
    }
}
