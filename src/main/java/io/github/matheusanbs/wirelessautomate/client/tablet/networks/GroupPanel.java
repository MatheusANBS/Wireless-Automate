package io.github.matheusanbs.wirelessautomate.client.tablet.networks;

import static io.github.matheusanbs.wirelessautomate.client.tablet.networks.NetworksText.plural;
import static io.github.matheusanbs.wirelessautomate.client.tablet.networks.NetworksText.tr;

import io.github.matheusanbs.wirelessautomate.client.ArmedClick;
import io.github.matheusanbs.wirelessautomate.client.FlatButton;
import io.github.matheusanbs.wirelessautomate.client.GuiPaint;
import io.github.matheusanbs.wirelessautomate.client.GuiText;
import io.github.matheusanbs.wirelessautomate.client.PanelPaint;
import io.github.matheusanbs.wirelessautomate.client.tablet.TabletLayout;
import io.github.matheusanbs.wirelessautomate.client.tablet.TabletScreen;
import io.github.matheusanbs.wirelessautomate.client.tablet.kit.PanelHeader;
import io.github.matheusanbs.wirelessautomate.menu.TabletSnapshot.GroupView;
import io.github.matheusanbs.wirelessautomate.menu.TabletSnapshot.NetworkView;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * O grupo escolhido na aba Redes (mockup {@code grupo_detalhe}): o cabeçalho com o slot do ícone e o nome editável,
 * "Redes do grupo" (cor, nome e máquinas; clicar escolhe a rede), o link "+ pôr uma rede neste grupo" e o rodapé com
 * Pausar grupo e "Apagar grupo" de dois cliques. Os textos são montados no {@link #set}.
 */
final class GroupPanel {
    private static final int X0 = TabletLayout.PANEL_X0;
    private static final int ROWS_Y = 75;
    private static final int ROW_H = 14;
    private static final Component MORE_UP = Component.literal("▲");
    private static final Component MORE_DOWN = Component.literal("▼");

    /** Uma rede do grupo pronta para desenhar. */
    private record Line(UUID id, int color, GuiText.Fitted name, Component machines, int machinesW) {
    }

    private final TabletLayout layout;
    private final NetworksPanel host;
    private final NetworksWindows windows;
    private final NetworkActions send;
    private final PanelHeader header = new PanelHeader();
    private final ArmedClick removeArm = new ArmedClick();
    private final Component networksLabel = tr("group.networks");
    private final Component addLink = tr("nets.add_to_group");
    private final Component remove = tr("nets.remove_group");
    private final Component removeArmed = tr("nets.remove.armed");
    private final Component pauseText = tr("nets.pause_group");
    private final Component resumeText = tr("nets.resume_group");
    private final Component renameTip = tr("rename.tooltip");
    private final Component removeTip = tr("group.remove.tooltip");
    private int addLinkW;
    private int removeW;
    private int removeArmedW;
    private int pauseW;
    private int resumeW;
    private @Nullable FlatButton pause;

    private @Nullable GroupView group;
    private ItemStack icon = ItemStack.EMPTY;
    private Line[] lines = new Line[0];
    /** A primeira rede à vista em "Redes do grupo" (a roda rola quando não cabem todas). */
    private int scroll;

    GroupPanel(TabletLayout layout, NetworksPanel host, NetworksWindows windows, NetworkActions send) {
        this.layout = layout;
        this.host = host;
        this.windows = windows;
        this.send = send;
    }

    void addButtons(TabletScreen.Buttons buttons, Font font) {
        addLinkW = font.width(addLink);
        removeW = font.width(remove);
        removeArmedW = font.width(removeArmed);
        pauseW = font.width(pauseText) + 16;
        resumeW = font.width(resumeText) + 16;
        pause = buttons.add(TabletScreen.Tab.NETWORKS, new FlatButton(layout.left + X0, layout.top + layout.footerY(),
                pauseW, 16, pauseText, (g, b, hovered) -> PanelPaint.button(g, font, b.getX(), b.getY(), b.getWidth(),
                        b.getHeight(), b.getMessage(), false, hovered, b.active), send::pauseGroup));
    }

    /** O grupo, o ícone dele e as redes que existem no snapshot, na ordem do grupo. */
    void set(Font font, GroupView view, ItemStack groupIcon, Map<UUID, NetworkView> networks) {
        if (group == null || !group.id().equals(view.id())) {
            removeArm.disarm();
            scroll = 0;
        }
        group = view;
        icon = groupIcon;
        int x1 = layout.panelX1();
        List<Line> built = new ArrayList<>();
        int machines = 0;
        for (UUID id : view.networks()) {
            NetworkView network = networks.get(id);
            if (network == null) {
                continue;
            }
            machines += network.machines();
            Component count = plural("nets.machines", network.machines());
            int countW = font.width(count);
            built.add(new Line(id, 0xFF000000 | network.color(),
                    GuiText.fit(font, Component.literal(network.name()), x1 - X0 - 11 - countW - 6), count, countW));
        }
        lines = built.toArray(new Line[0]);
        scroll = Math.min(scroll, maxScroll());
        Component sub = tr("nets.group.sub", plural("nets.networks", lines.length), plural("nets.machines", machines));
        header.set(font, Component.literal(view.name()), sub, null, 0, view.manageable(),
                x1 - X0 - 21 - (view.manageable() ? 11 : 0));
    }

    void clear() {
        group = null;
        removeArm.disarm();
    }

    void refresh(boolean shown) {
        if (pause == null) {
            return;
        }
        pause.visible = shown && group != null;
        if (group == null) {
            return;
        }
        pause.setMessage(group.paused() ? resumeText : pauseText);
        pause.setWidth(group.paused() ? resumeW : pauseW);
        pause.active = group.manageable();
    }

    /** Quantas linhas cabem acima do link e do rodapé. */
    private int roomRows() {
        return Math.max(1, (layout.footerRuleY() - 20 - ROWS_Y) / ROW_H);
    }

    /** Quantas linhas de rede estão à vista, a partir da rolagem. */
    private int visibleRows() {
        return Math.max(0, Math.min(lines.length - scroll, roomRows()));
    }

    private int maxScroll() {
        return Math.max(0, lines.length - roomRows());
    }

    /** A roda sobre o painel rola as redes do grupo, uma linha por passo. */
    void mouseScrolled(double delta) {
        scroll = Math.max(0, Math.min(maxScroll(), scroll - (int) Math.signum(delta)));
    }

    private int linkY() {
        return ROWS_Y + visibleRows() * ROW_H + 5;
    }

    // ------------------------------------------------------------------ desenho

    void render(GuiGraphics g, Font font, int mx, int my) {
        GroupView v = group;
        if (v == null) {
            return;
        }
        int left = layout.left;
        int top = layout.top;
        int x1 = layout.panelX1();
        PanelPaint.slot(g, left + X0 - 1, top + 29, 18, false);
        g.renderItem(icon, left + X0, top + 30);
        header.render(g, font, left, top, X0, x1);
        GuiPaint.text(g, font, networksLabel, left + X0, top + 61, GuiPaint.MUTED);
        if (scroll > 0) {
            GuiPaint.text(g, font, MORE_UP, left + x1 - 16, top + 61, GuiPaint.MUTED);
        }
        if (scroll < maxScroll()) {
            GuiPaint.text(g, font, MORE_DOWN, left + x1 - 6, top + 61, GuiPaint.MUTED);
        }
        int hovered = rowAt(mx, my);
        for (int i = 0, n = visibleRows(); i < n; i++) {
            Line line = lines[scroll + i];
            int y = top + ROWS_Y + i * ROW_H;
            if (i == hovered) {
                g.fill(left + X0 - 2, y - 2, left + x1 + 1, y + ROW_H - 2, GuiPaint.ROW_HOVER);
            }
            PanelPaint.swatch(g, left + X0, y + 1, 7, line.color());
            GuiText.draw(g, font, line.name(), left + X0 + 11, y + 1, GuiPaint.FG);
            GuiPaint.text(g, font, line.machines(), left + x1 - line.machinesW(), y + 1, GuiPaint.MUTED);
        }
        if (v.manageable()) {
            PanelPaint.link(g, font, addLink, left + X0, top + linkY());
            boolean armed = removeArm.armed(System.currentTimeMillis());
            int w = armed ? removeArmedW : removeW;
            int color = armed ? GuiPaint.DANGER : overRemove(mx, my) ? GuiPaint.FG : GuiPaint.MUTED;
            GuiPaint.text(g, font, armed ? removeArmed : remove, left + x1 - w, top + layout.h - 16, color);
        }
    }

    // ------------------------------------------------------------------ entrada

    private int rowAt(double mx, double my) {
        int left = layout.left;
        int y0 = layout.top + ROWS_Y - 2;
        if (mx < left + X0 - 2 || mx > left + layout.panelX1() || my < y0) {
            return -1;
        }
        int i = (int) ((my - y0) / ROW_H);
        return i < visibleRows() ? i : -1;
    }

    private boolean overLink(double mx, double my) {
        int x = layout.left + X0;
        int y = layout.top + linkY();
        return mx >= x && mx < x + addLinkW && my >= y - 1 && my < y + 10;
    }

    private boolean overRemove(double mx, double my) {
        int w = removeArm.armed(System.currentTimeMillis()) ? removeArmedW : removeW;
        int right = layout.left + layout.panelX1();
        int y = layout.top + layout.h - 16;
        return mx >= right - w && mx < right && my >= y - 1 && my < y + 9;
    }

    boolean mouseClicked(double mx, double my) {
        GroupView v = group;
        if (v == null) {
            return false;
        }
        boolean onRemove = overRemove(mx, my);
        if (!onRemove) {
            removeArm.disarm();
        }
        int row = rowAt(mx, my);
        if (row >= 0) {
            host.selectNetwork(lines[scroll + row].id());
            return true;
        }
        if (!v.manageable()) {
            return false;
        }
        int sx = layout.left + X0 - 1;
        int sy = layout.top + 29;
        if (mx >= sx && mx < sx + 18 && my >= sy && my < sy + 18) {
            // o slot do ícone: o clique fica reservado à grade de ícones
            return true;
        }
        if (header.overTitle(mx, my, layout.left, layout.top, X0)) {
            // em edição, o clique é do campo (posiciona o cursor); a tela o repassa
            if (windows.titleEditing()) {
                return false;
            }
            windows.startTitle();
            return true;
        }
        if (overLink(mx, my)) {
            windows.openAddToGroup(layout.left + X0, layout.top + linkY() - 6);
            return true;
        }
        if (onRemove) {
            if (removeArm.click(System.currentTimeMillis())) {
                send.removeGroup();
            }
            return true;
        }
        return false;
    }

    @Nullable Component tooltip(int mx, int my) {
        if (group == null || !group.manageable()) {
            return null;
        }
        if (header.overTitle(mx, my, layout.left, layout.top, X0)) {
            return renameTip;
        }
        return overRemove(mx, my) ? removeTip : null;
    }

    /** O link "+ pôr uma rede neste grupo", que a lista aberta redesenha por cima do véu. */
    void renderLink(GuiGraphics g, Font font) {
        PanelPaint.link(g, font, addLink, layout.left + X0, layout.top + linkY());
    }

    // ------------------------------------------------------------------ ganchos

    int titleWidth() {
        return layout.panelX1() - X0 - 21 - 4;
    }

    int @Nullable [] addToGroupCenter() {
        return group == null || !group.manageable() ? null
                : new int[] {layout.left + X0 + addLinkW / 2, layout.top + linkY() + 4};
    }

    int @Nullable [] pauseCenter() {
        return pause == null || !pause.visible ? null
                : new int[] {pause.getX() + pause.getWidth() / 2, pause.getY() + 8};
    }

    int @Nullable [] removeCenter() {
        if (group == null || !group.manageable()) {
            return null;
        }
        int w = removeArm.armed(System.currentTimeMillis()) ? removeArmedW : removeW;
        return new int[] {layout.left + layout.panelX1() - w / 2, layout.top + layout.h - 12};
    }

    int[] iconSlotCenter() {
        return new int[] {layout.left + X0 + 8, layout.top + 38};
    }
}
