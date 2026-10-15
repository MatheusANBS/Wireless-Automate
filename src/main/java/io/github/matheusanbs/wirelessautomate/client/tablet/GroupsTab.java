package io.github.matheusanbs.wirelessautomate.client.tablet;

import static io.github.matheusanbs.wirelessautomate.client.tablet.TabletLayout.BODY_Y;
import static io.github.matheusanbs.wirelessautomate.client.tablet.TabletLayout.ROW_H;
import static io.github.matheusanbs.wirelessautomate.client.tablet.TabletLayout.RX;
import static io.github.matheusanbs.wirelessautomate.client.tablet.TabletLayout.SIDE_W;
import static io.github.matheusanbs.wirelessautomate.client.tablet.TabletLayout.X0;
import static io.github.matheusanbs.wirelessautomate.client.tablet.TabletText.plural;
import static io.github.matheusanbs.wirelessautomate.client.tablet.TabletText.statusColor;
import static io.github.matheusanbs.wirelessautomate.client.tablet.TabletText.tr;

import io.github.matheusanbs.wirelessautomate.client.FlatButton;
import io.github.matheusanbs.wirelessautomate.client.GuiPaint;
import io.github.matheusanbs.wirelessautomate.client.GuiText;
import io.github.matheusanbs.wirelessautomate.menu.TabletMenu.Action;
import io.github.matheusanbs.wirelessautomate.menu.TabletSnapshot;
import io.github.matheusanbs.wirelessautomate.menu.TabletSnapshot.GroupView;
import io.github.matheusanbs.wirelessautomate.menu.TabletSnapshot.NetworkView;
import io.github.matheusanbs.wirelessautomate.menu.TabletSnapshot.NodeStatus;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;

/**
 * A aba Grupos do Tablet: a lista de grupos à esquerda e o escolhido à direita (nome, resumo, pausar e retomar o
 * grupo todo, marcar as redes dele e remover); o novo grupo fica recolhido embaixo da lista.
 */
final class GroupsTab {
    private static final int GROUP_NET_Y = BODY_Y + 62;
    private static final int GROUP_NET_ROW = 13;
    private static final int GROUP_NET_ROWS = 6;

    private final TabletLayout layout;
    private final Supplier<TabletSnapshot> snapshot;
    private final TabletActions actions;
    private final SideList side;
    private final NameFields names;
    private @Nullable UUID selectedGroup;
    private int groupNetScroll;
    private FlatButton pause;
    private FlatButton remove;
    private FlatButton create;

    GroupsTab(TabletLayout layout, Supplier<TabletSnapshot> snapshot, TabletActions actions, SideList side,
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
        return selectedGroup;
    }

    private @Nullable GroupView selectedGroupView() {
        for (GroupView group : snapshot().groups()) {
            if (group.id().equals(selectedGroup)) {
                return group;
            }
        }
        return null;
    }

    private List<NetworkView> manageableNetworks() {
        List<NetworkView> list = new ArrayList<>();
        for (NetworkView network : snapshot().networks()) {
            if (network.manageable()) {
                list.add(network);
            }
        }
        return list;
    }

    void addButtons(TabletScreen.Buttons buttons) {
        int x = layout.left;
        int y = layout.top;
        pause = buttons.add(TabletScreen.Tab.GROUPS, new FlatButton(x + RX, y + BODY_Y + 26, layout.rw(), 16, tr("group.pause"),
                (g, b, hovered) -> {
                    GroupView group = selectedGroupView();
                    TabletButtons.paintPrimary(g, b, hovered,
                            group != null && group.paused() ? tr("group.resume") : tr("group.pause"));
                }, this::togglePause).tooltip(() -> tr("group.pause.tooltip")));
        remove = buttons.add(TabletScreen.Tab.GROUPS, new FlatButton(x + RX, y + layout.newBoxY(), layout.rw(), ROW_H,
                tr("group.remove"),
                (g, b, hovered) -> TabletButtons.paintDanger(g, b, hovered,
                        names.removeArmed() ? tr("remove.confirm") : tr("group.remove"), names.removeArmed()),
                () -> names.removeSelected(Action.GROUP_REMOVE, selectedGroup))
                .tooltip(() -> tr("group.remove.tooltip")));
        buttons.add(TabletScreen.Tab.GROUPS, names.newToggle(tr("group.new")));
        create = buttons.add(TabletScreen.Tab.GROUPS, names.createButton(Action.GROUP_CREATE));
    }

    void refresh(boolean shown) {
        GroupView group = selectedGroupView();
        pause.visible = shown && group != null && group.manageable();
        pause.active = group != null && !group.networks().isEmpty();
        remove.visible = shown && group != null && group.manageable();
        create.visible = shown && names.newOpen();
    }

    FlatButton removeButton() {
        return remove;
    }

    /** Snapshot novo: o grupo escolhido que sumiu sai; sem nenhum, vale o primeiro. */
    void onSnapshot(TabletSnapshot s) {
        if (selectedGroup != null && selectedGroupView() == null) {
            selectedGroup = null;
            names.clearRename();
        }
        if (selectedGroup == null && !s.groups().isEmpty()) {
            selectedGroup = s.groups().get(0).id();
        }
    }

    // ------------------------------------------------------------------ ações

    private void togglePause() {
        GroupView group = selectedGroupView();
        if (group != null) {
            actions.groupPause(group.id(), group.paused());
        }
    }

    private void toggleGroupNetwork(UUID network) {
        GroupView group = selectedGroupView();
        if (group != null && group.manageable()) {
            actions.groupNetwork(group.id(), network, group.networks().contains(network));
        }
    }

    // ------------------------------------------------------------------ entrada

    /** Rede da lista de marcar do grupo sob o mouse, ou -1. */
    private int groupNetAt(double mouseX, double mouseY) {
        int x = layout.left + RX;
        int y = layout.top + GROUP_NET_Y;
        if (mouseX < x || mouseX >= x + layout.rw() || mouseY < y || mouseY >= y + GROUP_NET_ROWS * GROUP_NET_ROW) {
            return -1;
        }
        int index = (int) ((mouseY - y) / GROUP_NET_ROW) + groupNetScroll;
        return index < manageableNetworks().size() ? index : -1;
    }

    /** Cliques na lista, nas redes do grupo e no nome. Devolve se tratou. */
    boolean mouseClicked(double mouseX, double mouseY) {
        List<GroupView> groups = snapshot().groups();
        int row = side.rowAt(mouseX, mouseY, groups.size());
        if (row >= 0) {
            selectedGroup = groups.get(row).id();
            names.clearRename();
            names.disarm();
            groupNetScroll = 0;
            return true;
        }
        GroupView group = selectedGroupView();
        if (group != null && group.manageable()) {
            int net = groupNetAt(mouseX, mouseY);
            if (net >= 0) {
                toggleGroupNetwork(manageableNetworks().get(net).id());
                return true;
            }
            if (!names.renaming() && names.overTitle(mouseX, mouseY)) {
                names.startRename(NameFields.Rename.GROUP, group.name());
                return true;
            }
        }
        return false;
    }

    /** A roda na coluna da direita rola as redes do grupo; na esquerda, a lista de grupos. */
    void mouseScrolled(double mouseX, int step) {
        if (mouseX >= layout.left + RX) {
            groupNetScroll = Mth.clamp(groupNetScroll + step, 0,
                    Math.max(0, manageableNetworks().size() - GROUP_NET_ROWS));
        } else {
            side.mouseScrolled(step, snapshot().groups().size());
        }
    }

    @Nullable Component tooltip(int mouseX, int mouseY) {
        GroupView group = selectedGroupView();
        if (group != null && group.manageable() && groupNetAt(mouseX, mouseY) >= 0) {
            return tr("group.network.tooltip");
        }
        if (group != null && group.manageable() && !names.renaming() && names.overTitle(mouseX, mouseY)) {
            return tr("rename.tooltip");
        }
        return null;
    }

    /** Centro da linha do grupo na lista lateral, ou {@code null}. */
    int @Nullable [] groupRowCenter(UUID id) {
        List<GroupView> groups = snapshot().groups();
        int scroll = side.scroll();
        for (int i = scroll; i < Math.min(groups.size(), scroll + side.rows()); i++) {
            if (groups.get(i).id().equals(id)) {
                return new int[] {layout.left + X0 + SIDE_W / 2, layout.top + BODY_Y + (i - scroll) * ROW_H + ROW_H / 2};
            }
        }
        return null;
    }

    /** Centro da caixa de marcar da rede na coluna do grupo, ou {@code null}. */
    int @Nullable [] groupNetworkCenter(UUID id) {
        List<NetworkView> networks = manageableNetworks();
        for (int i = groupNetScroll; i < Math.min(networks.size(), groupNetScroll + GROUP_NET_ROWS); i++) {
            if (networks.get(i).id().equals(id)) {
                return new int[] {layout.left + RX + 6, layout.top + GROUP_NET_Y + (i - groupNetScroll) * GROUP_NET_ROW + 6};
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ desenho

    void render(GuiGraphics g, Font font, int mouseX, int mouseY) {
        TabletSnapshot s = snapshot();
        List<GroupView> groups = s.groups();
        side.render(g, font, mouseX, mouseY, groups.size(), i -> {
            GroupView group = groups.get(i);
            int color = group.paused() ? statusColor(NodeStatus.PAUSED) : statusColor(NodeStatus.ACTIVE);
            return new SideList.Row(Component.literal(group.name()), color, group.id().equals(selectedGroup),
                    Component.literal(Integer.toString(group.networks().size())));
        }, tr("groups.empty"));
        names.renderNewFrame(g);

        int x = layout.left + RX;
        int y = layout.top;
        int rw = layout.rw();
        GroupView group = selectedGroupView();
        if (group == null) {
            GuiText.draw(g, font, tr("group.pick"), x, y + BODY_Y + 3, rw, GuiPaint.MUTED);
            return;
        }
        int color = group.paused() ? statusColor(NodeStatus.PAUSED) : statusColor(NodeStatus.ACTIVE);
        names.renderTitle(g, font, mouseX, mouseY, Component.literal(group.name()), color, group.manageable());
        int nodes = 0;
        for (UUID id : group.networks()) {
            Optional<NetworkView> n = s.network(id);
            if (n.isPresent()) {
                nodes += n.get().nodes();
            }
        }
        Component summary = (group.paused() ? tr("status.paused") : tr("group.running")).copy().withColor(color)
                .append(Component.literal(" · ").withColor(GuiPaint.MUTED))
                .append(plural("meta.networks", group.networks().size()).copy().withColor(GuiPaint.MUTED))
                .append(Component.literal(" · ").withColor(GuiPaint.MUTED))
                .append(plural("meta.nodes", nodes).copy().withColor(GuiPaint.MUTED));
        GuiText.draw(g, font, summary, x, y + BODY_Y + 15, rw, GuiPaint.FG);
        renderGroupNetworks(g, font, mouseX, mouseY, group);
        GuiText.wrap(g, font, tr("group.hint"), x, y + layout.newY() - 10, rw, 2, GuiPaint.DISABLED);
    }

    /** As redes que se pode marcar no grupo, com a caixa de marcar e o olho na cor. */
    private void renderGroupNetworks(GuiGraphics g, Font font, int mouseX, int mouseY, GroupView group) {
        int x = layout.left + RX;
        int y = layout.top;
        int rw = layout.rw();
        GuiPaint.text(g, font, tr("group.networks"), x, y + GROUP_NET_Y - 11, GuiPaint.FG);
        List<NetworkView> networks = manageableNetworks();
        int hovered = group.manageable() ? groupNetAt(mouseX, mouseY) : -1;
        if (networks.isEmpty()) {
            GuiText.draw(g, font, tr("networks.empty"), x, y + GROUP_NET_Y + 2, rw, GuiPaint.MUTED);
        }
        for (int i = 0; i < GROUP_NET_ROWS && i + groupNetScroll < networks.size(); i++) {
            int index = i + groupNetScroll;
            NetworkView n = networks.get(index);
            int ry = y + GROUP_NET_Y + i * GROUP_NET_ROW;
            if (index == hovered) {
                g.fill(x, ry, x + rw, ry + GROUP_NET_ROW, GuiPaint.ROW_SELECTED);
            }
            GuiPaint.checkbox(g, x + 2, ry + 2, group.networks().contains(n.id()), GuiPaint.ACCENT);
            GuiPaint.eye(g, x + 15, ry + 4, 0xFF000000 | n.color());
            GuiText.draw(g, font, Component.literal(n.name()), x + 24, ry + 3, rw - 26,
                    group.manageable() ? GuiPaint.FG : GuiPaint.MUTED);
        }
        if (groupNetScroll + GROUP_NET_ROWS < networks.size()) {
            GuiPaint.text(g, font, Component.literal("▼"), x + rw - 8, y + GROUP_NET_Y + GROUP_NET_ROWS * GROUP_NET_ROW - 8,
                    GuiPaint.MUTED);
        }
    }
}
