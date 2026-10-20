package io.github.matheusanbs.wirelessautomate.client.tablet;

import static io.github.matheusanbs.wirelessautomate.client.tablet.TabletLayout.BODY_Y;
import static io.github.matheusanbs.wirelessautomate.client.tablet.TabletLayout.MIN_H;
import static io.github.matheusanbs.wirelessautomate.client.tablet.TabletLayout.X0;
import static io.github.matheusanbs.wirelessautomate.client.tablet.TabletText.statusColor;
import static io.github.matheusanbs.wirelessautomate.client.tablet.TabletText.statusName;
import static io.github.matheusanbs.wirelessautomate.client.tablet.TabletText.tr;

import io.github.matheusanbs.wirelessautomate.client.FlatButton;
import io.github.matheusanbs.wirelessautomate.client.GuiPaint;
import io.github.matheusanbs.wirelessautomate.client.GuiText;
import io.github.matheusanbs.wirelessautomate.menu.TabletSnapshot;
import io.github.matheusanbs.wirelessautomate.menu.TabletSnapshot.NodeStatus;
import io.github.matheusanbs.wirelessautomate.menu.TabletSnapshot.NodeView;
import io.github.matheusanbs.wirelessautomate.packet.TabletPanelPayload;
import java.util.Locale;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;

/**
 * A aba Mapa do Tablet: vista de cima centrada no jogador, com a grade, os pontos coloridos pelo status, o zoom
 * pela roda, o nó escolhido com Abrir e a legenda. O nó escolhido é o da Lista (o painel vem dele), e as máquinas
 * dele aparecem pelo {@link MapMachines}.
 */
final class MapTab {
    private static final BlockPos PREVIEW_CENTER = new BlockPos(0, 64, 0);
    private static final int MAP_H = 136;

    private final TabletLayout layout;
    private final Supplier<TabletSnapshot> snapshot;
    private final NodeText nodes;
    private final TabletActions actions;
    private final TabletList list;
    private final MapMachines machines = new MapMachines();
    private float mapZoom = 1f;
    private FlatButton open;

    MapTab(TabletLayout layout, Supplier<TabletSnapshot> snapshot, NodeText nodes, TabletActions actions,
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

    /** O centro do mapa: o jogador, que o cliente já sabe onde está (o servidor não manda); na prévia, a origem. */
    private static BlockPos center() {
        LocalPlayer player = Minecraft.getInstance().player;
        return player == null ? PREVIEW_CENTER : player.blockPosition();
    }

    void addButtons(TabletScreen.Buttons buttons) {
        int x = layout.left;
        int y = layout.top;
        open = buttons.add(TabletScreen.Tab.MAP, new FlatButton(x + layout.x1() - 46, y + mapInfoY(), 46, 16, tr("open"),
                (g, b, hovered) -> TabletButtons.paintPrimary(g, b, hovered, tr("open")), () -> {
                    if (list.chosen != null) {
                        actions.openNode(list.chosen);
                    }
                }).tooltip(() -> tr("open.tooltip")));
    }

    void refresh(boolean shown) {
        open.visible = shown && mapNode() != null;
    }

    // ------------------------------------------------------------------ geometria

    private int mapH() {
        return MAP_H + (layout.h - MIN_H);
    }

    private int mapInfoY() {
        return BODY_Y + mapH() + 4;
    }

    private int legendY() {
        return mapInfoY() + 20;
    }

    private int mapX0() {
        return layout.left + X0;
    }

    private int mapY0() {
        return layout.top + BODY_Y;
    }

    private int mapW() {
        return layout.x1() - X0;
    }

    /** Blocos por pixel: cabe o nó mais longe da página (mínimo 16 blocos), vezes o zoom. */
    private double mapScale() {
        double far = 16;
        BlockPos center = center();
        for (NodeView node : snapshot().nodes()) {
            if (nodes.sameDimension(node)) {
                far = Math.max(far, Math.max(Math.abs(node.key().pos().getX() - center.getX()),
                        Math.abs(node.key().pos().getZ() - center.getZ())));
            }
        }
        far = Math.min(far, 4096);
        return far / (mapH() / 2.0 - 10) / mapZoom;
    }

    /** Ponto do nó no mapa (preso à borda se estiver fora); {@code [x, y, dentro]}. */
    private int[] mapPoint(NodeView node, double scale) {
        BlockPos center = center();
        int cx = mapX0() + mapW() / 2;
        int cy = mapY0() + mapH() / 2;
        double px = cx + (node.key().pos().getX() - center.getX()) / scale;
        double py = cy + (node.key().pos().getZ() - center.getZ()) / scale;
        boolean inside = px >= mapX0() + 4 && px <= mapX0() + mapW() - 4 && py >= mapY0() + 4 && py <= mapY0() + mapH() - 4;
        px = Mth.clamp(px, mapX0() + 4, mapX0() + mapW() - 4);
        py = Mth.clamp(py, mapY0() + 4, mapY0() + mapH() - 4);
        return new int[] {(int) Math.round(px), (int) Math.round(py), inside ? 1 : 0};
    }

    private boolean overMap(double mouseX, double mouseY) {
        return mouseX >= mapX0() && mouseX < mapX0() + mapW() && mouseY >= mapY0() && mouseY < mapY0() + mapH();
    }

    private @Nullable NodeView mapNodeAt(double mouseX, double mouseY) {
        if (!overMap(mouseX, mouseY)) {
            return null;
        }
        double scale = mapScale();
        NodeView best = null;
        double bestDistance = 7 * 7;
        for (NodeView node : snapshot().nodes()) {
            if (!nodes.sameDimension(node)) {
                continue;
            }
            int[] p = mapPoint(node, scale);
            double d = (p[0] - mouseX) * (p[0] - mouseX) + (p[1] - mouseY) * (p[1] - mouseY);
            if (d <= bestDistance) {
                best = node;
                bestDistance = d;
            }
        }
        return best;
    }

    private @Nullable NodeView mapNode() {
        if (list.chosen == null) {
            return null;
        }
        for (NodeView node : snapshot().nodes()) {
            if (node.key().equals(list.chosen)) {
                return node;
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ entrada

    /** Clicar no mapa escolhe a máquina sob o mouse como alvo do painel, ou o nó (ou nenhum). Devolve se tratou. */
    boolean mouseClicked(double mouseX, double mouseY) {
        if (overMap(mouseX, mouseY)) {
            if (machines.click(mouseX, mouseY, actions)) {
                return true;
            }
            NodeView node = mapNodeAt(mouseX, mouseY);
            list.choose(node == null ? null : node.key());
            return true;
        }
        return false;
    }

    void mouseScrolled(double mouseX, double mouseY, double scrollY) {
        if (overMap(mouseX, mouseY)) {
            mapZoom = Mth.clamp(mapZoom * (scrollY > 0 ? 1.25f : 0.8f), 0.25f, 16f);
        }
    }

    @Nullable Component tooltip(int mouseX, int mouseY) {
        Component machine = machines.tooltip(mouseX, mouseY);
        if (machine != null) {
            return machine;
        }
        NodeView node = mapNodeAt(mouseX, mouseY);
        if (node != null) {
            return nodes.nodeTooltip(node, false);
        }
        return null;
    }

    void previewSelect(int index) {
        list.previewChoose(snapshot().nodes().get(index).key());
    }

    /** O centro da máquina {@code machine} do roteador escolhido no mapa, ou {@code null} (gancho do e2e). */
    int @Nullable [] machineCenter(int machine) {
        return machines.center(machine);
    }

    /** Põe as máquinas do nó escolhido em dia com o painel e a vista; sem painel dele ou noutra dimensão, nenhuma. */
    private void prepareMachines(double scale) {
        NodeView node = mapNode();
        TabletPanelPayload payload = list.panelPayload();
        if (node == null || payload == null || !nodes.sameDimension(node)) {
            machines.prepare(null, null, center(), scale, 0, 0, 0, 0);
            return;
        }
        machines.prepare(payload, node, center(), scale, mapX0(), mapY0(), mapW(), mapH());
    }

    /** Quantas máquinas e pixels de linha o mapa desenha (gancho do e2e). */
    int machinesDrawn() {
        return machines.drawn();
    }

    /** Um ponto do mapa perto do canto de baixo à esquerda, longe dos nós (gancho do e2e). */
    int[] emptyPoint() {
        return new int[] {mapX0() + 8, mapY0() + mapH() - 20};
    }

    // ------------------------------------------------------------------ desenho

    void render(GuiGraphics g, Font font, int mouseX, int mouseY) {
        int x0 = mapX0();
        int y0 = mapY0();
        int w = mapW();
        g.fill(x0 + 1, y0 + 1, x0 + w - 1, y0 + mapH() - 1, GuiPaint.VIEW_PAPER);
        GuiPaint.outline(g, x0, y0, w, mapH(), GuiPaint.LINE);
        double scale = mapScale();
        int step = renderGrid(g, x0, y0, w, scale);
        prepareMachines(scale);
        machines.render(g, mouseX, mouseY);
        renderPoints(g, mouseX, mouseY, scale);
        GuiPaint.textRight(g, font, tr("map.north"), x0 + w - 4, y0 + 4, GuiPaint.DISABLED);
        GuiText.draw(g, font, tr("map.scale", step), x0 + 4, y0 + mapH() - 11, w - 8, GuiPaint.DISABLED);
        renderInfo(g, font, x0, w);
        renderLegend(g, font, x0, w);
    }

    /** A grade e o jogador no centro; devolve o passo da grade, em blocos. */
    private int renderGrid(GuiGraphics g, int x0, int y0, int w, double scale) {
        // grade: o passo é uma potência de 2 em blocos que dê pelo menos 20 px
        int step = 1;
        while (step / scale < 20) {
            step *= 2;
        }
        BlockPos center = center();
        int cx = x0 + w / 2;
        int cy = y0 + mapH() / 2;
        int gridColor = GuiPaint.VIEW_GRID;
        int startX = Math.floorDiv(center.getX() - (int) (w / 2 * scale), step) * step;
        for (int bx = startX; (bx - center.getX()) / scale < w / 2.0; bx += step) {
            int px = (int) Math.round(cx + (bx - center.getX()) / scale);
            if (px > x0 && px < x0 + w - 1) {
                g.fill(px, y0 + 1, px + 1, y0 + mapH() - 1, gridColor);
            }
        }
        int startZ = Math.floorDiv(center.getZ() - (int) (mapH() / 2 * scale), step) * step;
        for (int bz = startZ; (bz - center.getZ()) / scale < mapH() / 2.0; bz += step) {
            int py = (int) Math.round(cy + (bz - center.getZ()) / scale);
            if (py > y0 && py < y0 + mapH() - 1) {
                g.fill(x0 + 1, py, x0 + w - 1, py + 1, gridColor);
            }
        }
        // o jogador, no centro
        g.fill(cx - 3, cy, cx + 4, cy + 1, GuiPaint.FG);
        g.fill(cx, cy - 3, cx + 1, cy + 4, GuiPaint.FG);
        return step;
    }

    private void renderPoints(GuiGraphics g, int mouseX, int mouseY, double scale) {
        NodeView hovered = mapNodeAt(mouseX, mouseY);
        for (NodeView node : snapshot().nodes()) {
            if (!nodes.sameDimension(node)) {
                continue;
            }
            int[] p = mapPoint(node, scale);
            int color = statusColor(node.status());
            boolean chosen = node.key().equals(list.chosen);
            if (chosen || node == hovered) {
                GuiPaint.outline(g, p[0] - 4, p[1] - 4, 9, 9, chosen ? GuiPaint.FG : GuiPaint.MUTED);
            }
            if (p[2] == 1) {
                g.fill(p[0] - 3, p[1] - 2, p[0] + 4, p[1] + 3, GuiPaint.RING);
                g.fill(p[0] - 2, p[1] - 3, p[0] + 3, p[1] + 4, GuiPaint.RING);
                GuiPaint.dot(g, p[0] - 2, p[1] - 2, color);
            } else {
                // fora do mapa: presa na borda, vazada
                GuiPaint.outline(g, p[0] - 2, p[1] - 2, 5, 5, color);
            }
        }
    }

    /** A máquina-alvo do painel, o nó escolhido ou a dica. */
    private void renderInfo(GuiGraphics g, Font font, int x0, int w) {
        int otherDimension = 0;
        for (NodeView node : snapshot().nodes()) {
            if (!nodes.sameDimension(node)) {
                otherDimension++;
            }
        }
        int infoY = layout.top + mapInfoY();
        if (machines.renderInfo(g, font, x0, infoY, w)) {
            return;
        }
        NodeView chosen = mapNode();
        if (chosen != null) {
            GuiPaint.box(g, x0, infoY, w - 50, 16, GuiPaint.BUTTON, GuiPaint.BUTTON_BORDER);
            Component status = statusName(chosen.status());
            int statusW = font.width(status);
            GuiPaint.text(g, font, status, x0 + w - 56 - statusW, infoY + 4, statusColor(chosen.status()));
            MutableComponent text = nodes.nodeName(chosen).copy().append(Component.literal("  ")
                    .append(nodes.coords(chosen)).withColor(GuiPaint.MUTED));
            GuiText.draw(g, font, text, x0 + 5, infoY + 4, w - 66 - statusW, GuiPaint.FG);
        } else {
            Component hint = otherDimension > 0 ? tr("map.hint.other", otherDimension) : tr("map.hint");
            GuiText.draw(g, font, hint, x0, infoY + 4, w, GuiPaint.DISABLED);
        }
    }

    private void renderLegend(GuiGraphics g, Font font, int x0, int w) {
        int lx = x0;
        int ly = layout.top + legendY();
        for (NodeStatus status : NodeStatus.values()) {
            Component name = tr("legend." + status.name().toLowerCase(Locale.ROOT));
            int width = 8 + font.width(name) + 8;
            if (lx + width > x0 + w) {
                lx = x0;
                ly += 10;
            }
            GuiPaint.dot(g, lx, ly + 1, statusColor(status));
            GuiPaint.text(g, font, name, lx + 8, ly, GuiPaint.MUTED);
            lx += width;
        }
    }
}
