package io.github.matheusanbs.wirelessautomate.client.tablet;

import static io.github.matheusanbs.wirelessautomate.client.tablet.TabletLayout.BODY_Y;
import static io.github.matheusanbs.wirelessautomate.client.tablet.TabletLayout.ROW_H;
import static io.github.matheusanbs.wirelessautomate.client.tablet.TabletLayout.RX;
import static io.github.matheusanbs.wirelessautomate.client.tablet.TabletText.plural;
import static io.github.matheusanbs.wirelessautomate.client.tablet.TabletText.tr;

import io.github.matheusanbs.wirelessautomate.client.FlatButton;
import io.github.matheusanbs.wirelessautomate.client.GuiPaint;
import io.github.matheusanbs.wirelessautomate.client.GuiText;
import io.github.matheusanbs.wirelessautomate.menu.TabletMenu;
import io.github.matheusanbs.wirelessautomate.menu.TabletMenu.Action;
import io.github.matheusanbs.wirelessautomate.menu.TabletSnapshot;
import io.github.matheusanbs.wirelessautomate.menu.TabletSnapshot.GroupView;
import io.github.matheusanbs.wirelessautomate.menu.TabletSnapshot.NetworkView;
import io.github.matheusanbs.wirelessautomate.network.NetworkSavedData;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.jetbrains.annotations.Nullable;

/**
 * A aba Redes do Tablet: à esquerda as redes, com os grupos de redes como seções ({@link NetworkList}); à direita a
 * rede escolhida (nome, dono, nós, membros, cor, privacidade, usar como ativa e remover) ou, com o cabeçalho de um
 * grupo escolhido, o grupo ({@link GroupDetail}). "Nova rede" e "Novo grupo" ficam embaixo da lista; o que acabou
 * de ser criado fica escolhido quando chega.
 */
final class NetworksTab {
    private static final int SWATCH = 10;
    private static final int SWATCH_Y = BODY_Y + 84;

    private final TabletLayout layout;
    private final Supplier<TabletSnapshot> snapshot;
    private final TabletActions actions;
    private final NameFields names;
    private final NetworkList list;
    private final GroupDetail groups;
    private final AccessDetail access;
    private @Nullable UUID selectedNetwork;
    /** O que foi pedido e ainda não chegou: um grupo ({@code true}) ou uma rede; os ids que já existiam. */
    private @Nullable Boolean awaitingGroup;
    private final Set<UUID> known = new HashSet<>();
    private FlatButton makePrivate;
    private FlatButton makePublic;
    private FlatButton use;
    private FlatButton remove;
    private FlatButton create;
    private FlatButton accessButton;

    NetworksTab(TabletLayout layout, TabletMenu menu, Supplier<TabletSnapshot> snapshot, TabletActions actions,
            SideList side, NameFields names) {
        this.layout = layout;
        this.snapshot = snapshot;
        this.actions = actions;
        this.names = names;
        this.list = new NetworkList(snapshot, side, actions);
        this.groups = new GroupDetail(layout, snapshot, actions, names);
        this.access = new AccessDetail(layout, menu, actions, this::selectedNetworkView);
    }

    AccessDetail access() {
        return access;
    }

    /** Outra aba ou outra rede: o acesso volta a ficar fechado. */
    void closeAccess() {
        access.open(false);
    }

    private TabletSnapshot snapshot() {
        return snapshot.get();
    }

    @Nullable UUID selected() {
        return selectedNetwork;
    }

    @Nullable UUID selectedGroup() {
        return groups.selected();
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

    void addButtons(TabletScreen.Buttons buttons, Font font) {
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
        accessButton = buttons.add(TabletScreen.Tab.NETWORKS, new FlatButton(x + RX, y + BODY_Y + 150, rw, ROW_H,
                tr("access.button"), (g, b, hovered) -> TabletButtons.paintText(g, b, hovered, accessLabel()),
                () -> access.open(true)).tooltip(() -> tr("access.button.tooltip")));
        access.addBack(buttons);
        groups.addButtons(buttons);
        buttons.add(TabletScreen.Tab.NETWORKS, names.newToggle(NameFields.Rename.NETWORK, tr("network.new"), font));
        buttons.add(TabletScreen.Tab.NETWORKS, names.newToggle(NameFields.Rename.GROUP, tr("group.new"), font));
        create = buttons.add(TabletScreen.Tab.NETWORKS, names.createButton());
    }

    void refresh(boolean shown) {
        NetworkView network = selectedNetworkView();
        boolean manage = network != null && network.manageable();
        // o acesso é das redes privadas que o jogador gerencia; perdeu isso, a coluna volta aos detalhes
        boolean accessible = manage && !network.isPublic() && groups.selected() == null;
        if (!accessible) {
            access.open(false);
        }
        boolean details = !access.open();
        makePrivate.visible = makePublic.visible = shown && manage && details;
        use.visible = shown && network != null && details;
        use.active = network != null && !isSelectedActive();
        remove.visible = shown && manage && details;
        accessButton.visible = shown && accessible && details;
        access.refresh(shown && accessible);
        groups.refresh(shown && groups.selected() != null);
        create.visible = shown && names.newOpen();
    }

    /** "Acesso" ou "Acesso (N)" com os convidados e times da rede. */
    private Component accessLabel() {
        NetworkView network = selectedNetworkView();
        int count = network == null ? 0 : network.access().guests().size() + network.access().teams().size();
        return count == 0 ? tr("access.button") : tr("access.button.count", count);
    }

    /** O Remover à vista: o da rede ou o do grupo. */
    FlatButton removeButton() {
        return remove.visible ? remove : groups.removeButton();
    }

    /** Uma rede ou um grupo foi pedido ao servidor: fica escolhido quando chegar. */
    void created(boolean group) {
        awaitingGroup = group;
        known.clear();
        TabletSnapshot s = snapshot();
        if (group) {
            s.groups().forEach(g -> known.add(g.id()));
        } else {
            s.networks().forEach(n -> known.add(n.id()));
        }
    }

    /**
     * Snapshot novo: o que acabou de ser criado fica escolhido; a rede ou o grupo escolhido que sumiu sai; sem
     * nenhum, vale a rede ativa ou a primeira.
     */
    void onSnapshot(TabletSnapshot s) {
        if (awaitingGroup != null) {
            pickCreated(s);
        }
        if (selectedNetwork != null && s.network(selectedNetwork).isEmpty()) {
            selectedNetwork = null;
            names.clearRename();
        }
        if (groups.selected() != null && groups.selectedView() == null) {
            groups.select(null);
            names.clearRename();
        }
        if (selectedNetwork == null && groups.selected() == null && !s.networks().isEmpty()) {
            selectedNetwork = s.activeNetwork().filter(id -> s.network(id).isPresent())
                    .orElse(s.networks().get(0).id());
        }
    }

    private void pickCreated(TabletSnapshot s) {
        if (awaitingGroup) {
            for (GroupView g : s.groups()) {
                if (!known.contains(g.id())) {
                    pickGroup(g.id());
                    awaitingGroup = null;
                    return;
                }
            }
        } else {
            for (NetworkView n : s.networks()) {
                if (!known.contains(n.id())) {
                    pickNetwork(n.id());
                    awaitingGroup = null;
                    return;
                }
            }
        }
    }

    private void pickNetwork(UUID id) {
        access.open(false);
        selectedNetwork = id;
        groups.select(null);
        names.clearRename();
        names.disarm();
    }

    private void pickGroup(UUID id) {
        access.open(false);
        selectedNetwork = null;
        groups.select(id);
        names.clearRename();
        names.disarm();
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

    /** Cliques na lista e na coluna da direita (a paleta e o nome da rede, ou o grupo). Devolve se tratou. */
    boolean mouseClicked(Font font, double mouseX, double mouseY) {
        switch (list.mouseClicked(font, mouseX, mouseY)) {
            case NETWORK -> {
                pickNetwork(list.pickedId());
                return true;
            }
            case GROUP -> {
                pickGroup(list.pickedId());
                return true;
            }
            case NONE -> {
            }
        }
        if (mouseX < layout.left + RX) {
            return false;
        }
        if (groups.selected() != null) {
            return groups.mouseClicked(font, mouseX, mouseY);
        }
        if (access.open()) {
            return access.mouseClicked(mouseX, mouseY);
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

    /** A roda na coluna da direita rola as redes do grupo; na esquerda, a lista. */
    void mouseScrolled(Font font, double mouseX, double mouseY, int step) {
        if (mouseX >= layout.left + RX && groups.selected() != null) {
            groups.mouseScrolled(font, step);
        } else if (mouseX >= layout.left + RX && access.open()) {
            access.mouseScrolled(mouseX, mouseY, step);
        } else {
            list.mouseScrolled(font, step);
        }
    }

    @Nullable Component tooltip(Font font, int mouseX, int mouseY) {
        Component row = list.tooltip(font, mouseX, mouseY);
        if (row != null) {
            return row;
        }
        if (groups.selected() != null) {
            return groups.tooltip(font, mouseX, mouseY);
        }
        if (access.open()) {
            return access.tooltip(font, mouseX, mouseY);
        }
        NetworkView network = selectedNetworkView();
        if (network != null && network.manageable() && swatchAt(mouseX, mouseY) >= 0) {
            return tr("color.tooltip");
        }
        if (network != null && network.manageable() && !names.renaming() && names.overTitle(mouseX, mouseY)) {
            return tr("rename.tooltip");
        }
        return null;
    }

    /** A lista de sugestões do acesso, por cima dos widgets; a tela chama depois de desenhá-los. */
    void renderOverlay(GuiGraphics g, Font font) {
        access.renderOverlay(g, font);
    }

    // ------------------------------------------------------------------ ganchos do e2e e das capturas

    void preview(int index) {
        pickNetwork(snapshot().networks().get(index).id());
    }

    void previewGroup(int index) {
        pickGroup(snapshot().groups().get(index).id());
    }

    /** Centro do cabeçalho do grupo na lista, ou {@code null}. */
    int @Nullable [] groupRowCenter(Font font, UUID id) {
        list.reveal(font, id);
        return list.groupCenter(font, id);
    }

    /** Centro da caixa de marcar da rede no detalhe do grupo escolhido, ou {@code null}. */
    int @Nullable [] groupNetworkCenter(Font font, UUID id) {
        return groups.networkCenter(font, id);
    }

    // ------------------------------------------------------------------ desenho

    void render(GuiGraphics g, Font font, int mouseX, int mouseY) {
        list.render(g, font, mouseX, mouseY, selectedNetwork, groups.selected());
        names.renderNewFrame(g);
        if (groups.selected() != null) {
            groups.render(g, font, mouseX, mouseY);
            return;
        }
        int x = layout.left + RX;
        int y = layout.top;
        int rw = layout.rw();
        NetworkView n = selectedNetworkView();
        if (access.open() && n != null) {
            access.render(g, font, mouseX, mouseY);
            return;
        }
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
        Component members = n.isPublic() ? tr("members.public")
                : !n.owned() && !n.manageable() ? tr("members.guest", n.owner()) : tr("members.private", n.owner());
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
