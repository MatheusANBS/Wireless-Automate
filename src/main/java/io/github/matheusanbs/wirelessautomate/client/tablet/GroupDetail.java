package io.github.matheusanbs.wirelessautomate.client.tablet;

import static io.github.matheusanbs.wirelessautomate.client.tablet.TabletLayout.BODY_Y;
import static io.github.matheusanbs.wirelessautomate.client.tablet.TabletLayout.ROW_H;
import static io.github.matheusanbs.wirelessautomate.client.tablet.TabletLayout.RX;
import static io.github.matheusanbs.wirelessautomate.client.tablet.TabletText.plural;
import static io.github.matheusanbs.wirelessautomate.client.tablet.TabletText.statusColor;
import static io.github.matheusanbs.wirelessautomate.client.tablet.TabletText.tr;

import io.github.matheusanbs.wirelessautomate.client.FlatButton;
import io.github.matheusanbs.wirelessautomate.client.GuiPaint;
import io.github.matheusanbs.wirelessautomate.client.GuiText;
import io.github.matheusanbs.wirelessautomate.client.GuiThemes;
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
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * O detalhe de um grupo de redes na coluna da direita da aba Redes (o cabeçalho do grupo escolhido na lista): o
 * nome, que vira o campo de renomear, o resumo, pausar e retomar o grupo todo, marcar as redes dele (com "em
 * <grupo>" ao lado da rede que já está noutro) e remover de dois cliques. À direita do nome, o slot do ícone do
 * grupo: recebe um item arrastado do JEI (o slot pisca enquanto o item está no ar) ou o item da outra mão num clique;
 * um clique sem item na outra mão tira o ícone. As linhas das redes, o resumo e o ícone são montados quando as redes,
 * os grupos, o grupo escolhido ou o tema mudam.
 */
final class GroupDetail {
    private static final Component MORE = Component.literal("▼");
    private static final int GROUP_NET_Y = BODY_Y + 62;
    private static final int GROUP_NET_ROW = 13;
    private static final int GROUP_NET_ROWS = 6;
    private static final int SLOT = 18;

    /** Uma rede que se pode marcar no grupo: o nome e, se está noutro grupo, "em <grupo>". */
    private record NetRow(UUID id, int color, Component name, @Nullable Component elsewhere, int elsewhereW) {
    }

    private final TabletLayout layout;
    private final Supplier<TabletSnapshot> snapshot;
    private final TabletActions actions;
    private final NameFields names;
    private @Nullable UUID selectedGroup;
    private int groupNetScroll;
    private FlatButton pause;
    private FlatButton remove;
    // montados para o grupo escolhido
    private List<NetRow> rows = List.of();
    private Component summary = Component.empty();
    private ItemStack icon = ItemStack.EMPTY;
    /** Um item do JEI está sendo arrastado: o slot pisca para mostrar onde soltar. */
    private boolean ghostDragging;
    private @Nullable List<NetworkView> builtNetworks;
    private @Nullable List<GroupView> builtGroups;
    private @Nullable UUID builtGroup;
    private int builtTheme = -1;

    GroupDetail(TabletLayout layout, Supplier<TabletSnapshot> snapshot, TabletActions actions, NameFields names) {
        this.layout = layout;
        this.snapshot = snapshot;
        this.actions = actions;
        this.names = names;
    }

    private TabletSnapshot snapshot() {
        return snapshot.get();
    }

    @Nullable UUID selected() {
        return selectedGroup;
    }

    /** Escolhe o grupo ({@code null}: nenhum). */
    void select(@Nullable UUID group) {
        if (group == null || !group.equals(selectedGroup)) {
            groupNetScroll = 0;
        }
        selectedGroup = group;
    }

    @Nullable GroupView selectedView() {
        if (selectedGroup == null) {
            return null;
        }
        for (GroupView group : snapshot().groups()) {
            if (group.id().equals(selectedGroup)) {
                return group;
            }
        }
        return null;
    }

    /** As redes que se pode marcar e o resumo, montados de novo só quando algo deles mudou. */
    private List<NetRow> rows(Font font, GroupView group) {
        TabletSnapshot s = snapshot();
        if (s.networks() == builtNetworks && s.groups() == builtGroups && group.id().equals(builtGroup)
                && builtTheme == GuiThemes.version()) {
            return rows;
        }
        builtNetworks = s.networks();
        builtGroups = s.groups();
        builtGroup = group.id();
        builtTheme = GuiThemes.version();
        List<NetRow> built = new ArrayList<>();
        for (NetworkView n : s.networks()) {
            if (!n.manageable()) {
                continue;
            }
            Component elsewhere = null;
            for (GroupView other : s.groups()) {
                if (!other.id().equals(group.id()) && other.networks().contains(n.id())) {
                    elsewhere = tr("group.elsewhere", other.name());
                    break;
                }
            }
            built.add(new NetRow(n.id(), 0xFF000000 | n.color(), Component.literal(n.name()), elsewhere,
                    elsewhere == null ? 0 : font.width(elsewhere)));
        }
        rows = built;
        icon = group.icon().map(id -> new ItemStack(BuiltInRegistries.ITEM.get(id))).orElse(ItemStack.EMPTY);
        int nodes = 0;
        for (UUID id : group.networks()) {
            Optional<NetworkView> n = s.network(id);
            if (n.isPresent()) {
                nodes += n.get().nodes();
            }
        }
        int color = color(group);
        summary = (group.paused() ? tr("status.paused") : tr("group.running")).copy().withColor(color)
                .append(Component.literal(" · ").withColor(GuiPaint.MUTED))
                .append(plural("meta.networks", group.networks().size()).copy().withColor(GuiPaint.MUTED))
                .append(Component.literal(" · ").withColor(GuiPaint.MUTED))
                .append(plural("meta.nodes", nodes).copy().withColor(GuiPaint.MUTED));
        return rows;
    }

    private static int color(GroupView group) {
        return group.paused() ? statusColor(NodeStatus.PAUSED) : statusColor(NodeStatus.ACTIVE);
    }

    void addButtons(TabletScreen.Buttons buttons) {
        int x = layout.left;
        int y = layout.top;
        pause = buttons.add(TabletScreen.Tab.NETWORKS, new FlatButton(x + RX, y + BODY_Y + 26, layout.rw(), 16,
                tr("group.pause"), (g, b, hovered) -> {
                    GroupView group = selectedView();
                    TabletButtons.paintPrimary(g, b, hovered,
                            group != null && group.paused() ? tr("group.resume") : tr("group.pause"));
                }, this::togglePause).tooltip(() -> tr("group.pause.tooltip")));
        remove = buttons.add(TabletScreen.Tab.NETWORKS, new FlatButton(x + RX, y + layout.newBoxY(), layout.rw(),
                ROW_H, tr("group.remove"),
                (g, b, hovered) -> TabletButtons.paintDanger(g, b, hovered,
                        names.removeArmed() ? tr("remove.confirm") : tr("group.remove"), names.removeArmed()),
                () -> names.removeSelected(Action.GROUP_REMOVE, selectedGroup))
                .tooltip(() -> tr("group.remove.tooltip")));
    }

    /** {@code shown}: a aba Redes aberta com um grupo escolhido. */
    void refresh(boolean shown) {
        GroupView group = selectedView();
        pause.visible = shown && group != null && group.manageable();
        pause.active = group != null && !group.networks().isEmpty();
        remove.visible = shown && group != null && group.manageable();
    }

    FlatButton removeButton() {
        return remove;
    }

    // ------------------------------------------------------------------ ícone

    /** O slot do ícone do grupo escolhido, {x, y, w, h}, ou {@code null} se não há grupo que o jogador gerencie. */
    int @Nullable [] iconSlot() {
        GroupView group = selectedView();
        if (group == null || !group.manageable() || names.renaming()) {
            return null;
        }
        return new int[] {layout.left + RX + layout.rw() - SLOT, layout.top + BODY_Y - 2, SLOT, SLOT};
    }

    private boolean overIcon(double mouseX, double mouseY) {
        int[] at = iconSlot();
        return at != null && mouseX >= at[0] && mouseX < at[0] + at[2] && mouseY >= at[1] && mouseY < at[1] + at[3];
    }

    void ghostDragging(boolean dragging) {
        ghostDragging = dragging;
    }

    /** Usa o item como ícone do grupo escolhido. */
    void acceptIcon(ItemStack stack) {
        GroupView group = selectedView();
        if (group != null && group.manageable() && !stack.isEmpty()) {
            actions.groupIcon(group.id(), BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
        }
    }

    /** O clique no slot: o item da outra mão vira o ícone; sem item, o ícone sai. */
    private void clickIcon(GroupView group) {
        ItemStack offhand = Minecraft.getInstance().player == null ? ItemStack.EMPTY
                : Minecraft.getInstance().player.getOffhandItem();
        if (!offhand.isEmpty()) {
            acceptIcon(offhand);
        } else if (group.icon().isPresent()) {
            actions.groupIcon(group.id(), "");
        }
    }

    // ------------------------------------------------------------------ ações

    private void togglePause() {
        GroupView group = selectedView();
        if (group != null) {
            actions.groupPause(group.id(), group.paused());
        }
    }

    // ------------------------------------------------------------------ entrada

    /** Rede da lista de marcar do grupo sob o mouse, ou -1. */
    private int groupNetAt(double mouseX, double mouseY, int count) {
        int x = layout.left + RX;
        int y = layout.top + GROUP_NET_Y;
        if (mouseX < x || mouseX >= x + layout.rw() || mouseY < y || mouseY >= y + GROUP_NET_ROWS * GROUP_NET_ROW) {
            return -1;
        }
        int index = (int) ((mouseY - y) / GROUP_NET_ROW) + groupNetScroll;
        return index < count ? index : -1;
    }

    /** Cliques nas redes do grupo e no nome. Devolve se tratou. */
    boolean mouseClicked(Font font, double mouseX, double mouseY) {
        GroupView group = selectedView();
        if (group == null || !group.manageable()) {
            return false;
        }
        if (overIcon(mouseX, mouseY)) {
            clickIcon(group);
            return true;
        }
        List<NetRow> list = rows(font, group);
        int net = groupNetAt(mouseX, mouseY, list.size());
        if (net >= 0) {
            UUID network = list.get(net).id();
            actions.groupNetwork(group.id(), network, group.networks().contains(network));
            return true;
        }
        if (!names.renaming() && names.overTitle(mouseX, mouseY)) {
            names.startRename(NameFields.Rename.GROUP, group.name());
            return true;
        }
        return false;
    }

    void mouseScrolled(Font font, int step) {
        GroupView group = selectedView();
        int count = group == null ? 0 : rows(font, group).size();
        groupNetScroll = Mth.clamp(groupNetScroll + step, 0, Math.max(0, count - GROUP_NET_ROWS));
    }

    @Nullable Component tooltip(Font font, int mouseX, int mouseY) {
        GroupView group = selectedView();
        if (group == null || !group.manageable()) {
            return null;
        }
        if (overIcon(mouseX, mouseY)) {
            return tr("group.icon.tooltip");
        }
        if (groupNetAt(mouseX, mouseY, rows(font, group).size()) >= 0) {
            return tr("group.network.tooltip");
        }
        if (!names.renaming() && names.overTitle(mouseX, mouseY)) {
            return tr("rename.tooltip");
        }
        return null;
    }

    /** Centro da caixa de marcar da rede na coluna do grupo, ou {@code null}. */
    int @Nullable [] networkCenter(Font font, UUID id) {
        GroupView group = selectedView();
        List<NetRow> list = group == null ? List.of() : rows(font, group);
        for (int i = groupNetScroll; i < Math.min(list.size(), groupNetScroll + GROUP_NET_ROWS); i++) {
            if (list.get(i).id().equals(id)) {
                return new int[] {layout.left + RX + 6, layout.top + GROUP_NET_Y + (i - groupNetScroll) * GROUP_NET_ROW + 6};
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ desenho

    void render(GuiGraphics g, Font font, int mouseX, int mouseY) {
        GroupView group = selectedView();
        if (group == null) {
            return;
        }
        int x = layout.left + RX;
        int y = layout.top;
        int rw = layout.rw();
        List<NetRow> list = rows(font, group);
        int reserve = group.manageable() || !icon.isEmpty() ? SLOT + 4 : 0;
        names.renderTitle(g, font, mouseX, mouseY, tr("group.title", group.name()), color(group), group.manageable(),
                reserve);
        boolean dropHere = ghostDragging && iconSlot() != null;
        GuiText.draw(g, font, dropHere ? tr("group.icon.drop") : summary, x, y + BODY_Y + 15, rw - reserve,
                dropHere ? GuiPaint.ACCENT : GuiPaint.FG);
        if (reserve > 0) {
            renderIcon(g, x + rw - SLOT, y + BODY_Y - 2, mouseX, mouseY, dropHere);
        }
        renderNetworks(g, font, mouseX, mouseY, group, list);
        GuiText.wrap(g, font, tr("group.hint"), x, y + layout.newY() - 10, rw, 2, GuiPaint.DISABLED);
    }

    /** O slot do ícone; com um item do JEI no ar, a moldura pisca na cor de destaque para mostrar onde soltar. */
    private void renderIcon(GuiGraphics g, int sx, int sy, int mouseX, int mouseY, boolean dropHere) {
        GuiPaint.slot(g, sx, sy);
        if (!icon.isEmpty()) {
            g.renderItem(icon, sx + 1, sy + 1);
        }
        if (dropHere) {
            int color = (System.currentTimeMillis() / 300) % 2 == 0 ? GuiPaint.ACCENT : GuiPaint.FG;
            GuiPaint.outline(g, sx - 1, sy - 1, SLOT + 2, SLOT + 2, color);
            GuiPaint.outline(g, sx - 2, sy - 2, SLOT + 4, SLOT + 4, color);
        } else if (overIcon(mouseX, mouseY)) {
            GuiPaint.outline(g, sx - 1, sy - 1, SLOT + 2, SLOT + 2, GuiPaint.BUTTON_HOVER_BORDER);
        }
    }

    /** As redes que se pode marcar no grupo, com a caixa de marcar, o olho na cor e "em <grupo>". */
    private void renderNetworks(GuiGraphics g, Font font, int mouseX, int mouseY, GroupView group, List<NetRow> list) {
        int x = layout.left + RX;
        int y = layout.top;
        int rw = layout.rw();
        GuiPaint.text(g, font, tr("group.networks"), x, y + GROUP_NET_Y - 11, GuiPaint.FG);
        int hovered = group.manageable() ? groupNetAt(mouseX, mouseY, list.size()) : -1;
        if (list.isEmpty()) {
            GuiText.draw(g, font, tr("networks.empty"), x, y + GROUP_NET_Y + 2, rw, GuiPaint.MUTED);
        }
        for (int i = 0; i < GROUP_NET_ROWS && i + groupNetScroll < list.size(); i++) {
            int index = i + groupNetScroll;
            NetRow n = list.get(index);
            int ry = y + GROUP_NET_Y + i * GROUP_NET_ROW;
            if (index == hovered) {
                g.fill(x, ry, x + rw, ry + GROUP_NET_ROW, GuiPaint.ROW_SELECTED);
            }
            GuiPaint.checkbox(g, x + 2, ry + 2, group.networks().contains(n.id()), GuiPaint.ACCENT);
            GuiPaint.eye(g, x + 15, ry + 4, n.color());
            int right = x + rw - 2;
            if (n.elsewhere() != null && right - n.elsewhereW() - 4 - (x + 24) >= 24) {
                GuiPaint.textRight(g, font, n.elsewhere(), right, ry + 3, GuiPaint.MUTED);
                right -= n.elsewhereW() + 4;
            }
            GuiText.draw(g, font, n.name(), x + 24, ry + 3, right - x - 24,
                    group.manageable() ? GuiPaint.FG : GuiPaint.MUTED);
        }
        if (groupNetScroll + GROUP_NET_ROWS < list.size()) {
            GuiPaint.text(g, font, MORE, x + rw - 8, y + GROUP_NET_Y + GROUP_NET_ROWS * GROUP_NET_ROW - 8,
                    GuiPaint.MUTED);
        }
    }
}
