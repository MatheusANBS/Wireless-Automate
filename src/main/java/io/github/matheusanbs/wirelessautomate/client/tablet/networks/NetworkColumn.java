package io.github.matheusanbs.wirelessautomate.client.tablet.networks;

import static io.github.matheusanbs.wirelessautomate.client.tablet.networks.NetworksText.tr;

import io.github.matheusanbs.wirelessautomate.client.FlatButton;
import io.github.matheusanbs.wirelessautomate.client.GuiPaint;
import io.github.matheusanbs.wirelessautomate.client.GuiText;
import io.github.matheusanbs.wirelessautomate.client.PanelPaint;
import io.github.matheusanbs.wirelessautomate.client.tablet.TabletLayout;
import io.github.matheusanbs.wirelessautomate.client.tablet.TabletScreen;
import io.github.matheusanbs.wirelessautomate.menu.TabletSnapshot.GroupView;
import io.github.matheusanbs.wirelessautomate.menu.TabletSnapshot.NetworkView;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * A coluna esquerda da aba Redes (mockup {@code lista_redes}): os grupos com ícone, nome em negrito e "pausado", as
 * redes de cada um com o quadradinho da cor, o nome e as máquinas, e "Sem grupo" com as avulsas; rola pela roda,
 * recortada entre a barra e o rodapé, onde ficam "+ Rede" (primário) e "+ Grupo". As linhas e os textos são montados
 * no snapshot ({@link #rebuild}); o desenho só pinta as que estão à vista.
 */
final class NetworkColumn {
    /** Onde a divisória da coluna fica, relativo ao painel. */
    static final int RULE = TabletLayout.COLUMN_RULE;
    private static final int RIGHT = 141;
    private static final int SCROLL_STEP = 14;

    /** Uma linha pronta: o ícone do grupo, o nome cortado, o texto da direita e se a rede está num grupo pausado. */
    private record Line(NetworkTree.Row row, @Nullable ItemStack icon, GuiText.Fitted name, @Nullable Component right,
            int rightW, int color, boolean muted) {
    }

    private final TabletLayout layout;
    private final Component paused = tr("networks.paused");
    private final Component loose = tr("networks.no_group");
    private final Component newNetworkText = tr("nets.new_network");
    private final Component newGroupText = tr("nets.new_group");
    private Line[] lines = new Line[0];
    private int contentH = NetworkTree.TOP;
    private int scroll;
    private @Nullable FlatButton newNetwork;
    private @Nullable FlatButton newGroup;

    NetworkColumn(TabletLayout layout) {
        this.layout = layout;
    }

    void addButtons(TabletScreen.Buttons buttons, Font font, Runnable onNewNetwork, Runnable onNewGroup) {
        int y = layout.top + layout.footerY();
        int w1 = font.width(newNetworkText) + 16;
        newNetwork = buttons.add(TabletScreen.Tab.NETWORKS, new FlatButton(layout.left + 8, y, w1, 16, newNetworkText,
                (g, b, hovered) -> PanelPaint.button(g, font, b.getX(), b.getY(), b.getWidth(), b.getHeight(),
                        newNetworkText, true, hovered, b.active), onNewNetwork));
        newGroup = buttons.add(TabletScreen.Tab.NETWORKS, new FlatButton(layout.left + 8 + w1 + 4, y,
                font.width(newGroupText) + 16, 16, newGroupText,
                (g, b, hovered) -> PanelPaint.button(g, font, b.getX(), b.getY(), b.getWidth(), b.getHeight(),
                        newGroupText, false, hovered, b.active), onNewGroup));
    }

    /** Monta as linhas da árvore com os nomes cortados e as larguras medidas. */
    void rebuild(Font font, List<NetworkTree.Row> rows, Map<UUID, NetworkView> networks, Map<UUID, GroupView> groups,
            Map<UUID, ItemStack> icons) {
        Line[] built = new Line[rows.size()];
        int pausedW = font.width(paused);
        for (int i = 0; i < built.length; i++) {
            NetworkTree.Row row = rows.get(i);
            built[i] = switch (row.kind()) {
                case GROUP -> {
                    GroupView group = groups.get(row.id());
                    Component name = Component.literal(group.name()).withStyle(ChatFormatting.BOLD);
                    yield new Line(row, icons.get(group.id()), GuiText.fit(font, name, RIGHT - 28 - pausedW),
                            group.paused() ? paused : null, pausedW, 0, false);
                }
                case LOOSE -> new Line(row, null, GuiText.fit(font, loose, RIGHT - 28), null, 0, 0, true);
                case NETWORK -> {
                    NetworkView network = networks.get(row.id());
                    GroupView group = row.group() == null ? null : groups.get(row.group());
                    Component count = Component.literal(String.valueOf(network.machines()));
                    int countW = font.width(count);
                    yield new Line(row, null, GuiText.fit(font, Component.literal(network.name()),
                            RIGHT - 39 - countW - 4), count, countW, 0xFF000000 | network.color(),
                            group != null && group.paused());
                }
            };
        }
        lines = built;
        contentH = NetworkTree.height(rows);
        scroll = Math.min(scroll, maxScroll());
    }

    private int maxScroll() {
        return Math.max(0, contentH - (layout.h - 27 - NetworkTree.TOP));
    }

    void render(GuiGraphics g, Font font, int mx, int my, @Nullable UUID network, @Nullable UUID group) {
        int left = layout.left;
        int top = layout.top;
        int h = layout.h;
        g.fill(left + RULE, top + 24, left + RULE + 1, top + h - 2, GuiPaint.INSET);
        PanelPaint.rule(g, left + 2, top + h - 27, RULE - 2);
        g.enableScissor(left + 2, top + 24, left + RULE, top + h - 27);
        Line hovered = lineAt(mx, my);
        for (Line line : lines) {
            NetworkTree.Row row = line.row();
            int y = top + row.top() - scroll;
            if (y + row.height() <= top + 24 || y >= top + h - 27) {
                continue;
            }
            boolean chosen = row.kind() == NetworkTree.Kind.NETWORK ? Objects.equals(row.id(), network)
                    : row.kind() == NetworkTree.Kind.GROUP && network == null && Objects.equals(row.id(), group);
            if (chosen) {
                PanelPaint.chosenRow(g, left + 2, y, RULE - 2, row.height());
            } else if (line == hovered) {
                g.fill(left + 2, y, left + RULE, y + row.height(), GuiPaint.ROW_HOVER);
            }
            switch (row.kind()) {
                case GROUP -> renderGroup(g, font, line, left, y);
                case LOOSE -> GuiText.draw(g, font, line.name(), left + 28, y + 6, GuiPaint.MUTED);
                case NETWORK -> {
                    PanelPaint.swatch(g, left + 28, y + 3, 7, line.color());
                    GuiText.draw(g, font, line.name(), left + 39, y + 3, line.muted() ? GuiPaint.MUTED : GuiPaint.FG);
                    GuiPaint.text(g, font, line.right(), left + RIGHT - line.rightW(), y + 3, GuiPaint.MUTED);
                }
            }
        }
        g.disableScissor();
    }

    private static void renderGroup(GuiGraphics g, Font font, Line line, int left, int y) {
        if (line.icon() != null) {
            g.renderItem(line.icon(), left + 8, y + 2);
        }
        GuiText.draw(g, font, line.name(), left + 28, y + 6, GuiPaint.FG);
        if (line.right() != null) {
            GuiPaint.text(g, font, line.right(), left + RIGHT - line.rightW(), y + 6, GuiPaint.MUTED);
        }
    }

    /** A linha clicável (grupo ou rede) sob o mouse, ou {@code null}. */
    private @Nullable Line lineAt(double mx, double my) {
        int left = layout.left;
        int top = layout.top;
        if (mx < left + 2 || mx >= left + RULE || my < top + 24 || my >= top + layout.h - 27) {
            return null;
        }
        double at = my - top + scroll;
        for (Line line : lines) {
            NetworkTree.Row row = line.row();
            if (row.kind() != NetworkTree.Kind.LOOSE && at >= row.top() && at < row.top() + row.height()) {
                return line;
            }
        }
        return null;
    }

    /** A linha clicada, ou {@code null}. */
    @Nullable NetworkTree.Row rowAt(double mx, double my) {
        Line line = lineAt(mx, my);
        return line == null ? null : line.row();
    }

    /** A roda sobre a coluna rola as linhas; fora dela, nada. */
    boolean mouseScrolled(double mx, double my, double delta) {
        if (mx < layout.left || mx >= layout.left + RULE || my < layout.top + 24) {
            return false;
        }
        scroll = Math.max(0, Math.min(maxScroll(), scroll - (int) Math.signum(delta) * SCROLL_STEP));
        return true;
    }

    /** Centro da linha do grupo ou da rede {@code id}; rola até ela se estiver fora da vista. */
    int @Nullable [] rowCenter(NetworkTree.Kind kind, UUID id) {
        for (Line line : lines) {
            NetworkTree.Row row = line.row();
            if (row.kind() == kind && id.equals(row.id())) {
                int viewTop = 24 + scroll;
                int viewBottom = layout.h - 27 + scroll;
                if (row.top() < viewTop || row.top() + row.height() > viewBottom) {
                    scroll = Math.max(0, Math.min(maxScroll(), row.top() - NetworkTree.TOP));
                }
                return new int[] {layout.left + RULE / 2, layout.top + row.top() - scroll + row.height() / 2};
            }
        }
        return null;
    }

    int @Nullable [] newNetworkCenter() {
        return center(newNetwork);
    }

    int @Nullable [] newGroupCenter() {
        return center(newGroup);
    }

    private static int @Nullable [] center(@Nullable FlatButton button) {
        return button == null || !button.visible ? null
                : new int[] {button.getX() + button.getWidth() / 2, button.getY() + button.getHeight() / 2};
    }
}
