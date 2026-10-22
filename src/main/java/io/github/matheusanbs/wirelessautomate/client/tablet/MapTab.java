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
import io.github.matheusanbs.wirelessautomate.network.NodeIndex.NodeKey;
import io.github.matheusanbs.wirelessautomate.packet.TabletMapPayload;
import io.github.matheusanbs.wirelessautomate.packet.TabletPanelPayload;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;

/**
 * A aba Mapa do Tablet: vista de cima, com a grade, os pontos coloridos pelo status, o zoom pela roda, arrastar para
 * mover o mapa (como uma tela de desenho; o botão Centralizar volta ao jogador), o nó escolhido com Abrir e a
 * legenda. Os pontos são todos os roteadores que o jogador vê na dimensão, mandados pelo servidor só com esta aba
 * aberta ({@link MapPoints}), sem depender da busca, dos filtros nem do teto da Lista. O nó escolhido é o da Lista
 * (o painel vem dele), e as máquinas dele aparecem pelo {@link MapMachines}. Um clique sem arrastar escolhe; arrastar
 * move, e o clique só vale ao soltar.
 */
final class MapTab {
    private static final BlockPos PREVIEW_CENTER = new BlockPos(0, 64, 0);
    private static final int MAP_H = 136;
    /** O mouse precisa andar mais que isto (px) para o clique virar arrasto. */
    private static final double DRAG_SLOP = 3;
    private static final int RECENTER_W = 56;
    private static final int RECENTER_H = 12;

    private final TabletLayout layout;
    private final Supplier<TabletSnapshot> snapshot;
    private final Supplier<@Nullable TabletMapPayload> mapSource;
    private final NodeText nodes;
    private final TabletActions actions;
    private final TabletList list;
    private final MapMachines machines = new MapMachines();
    private float mapZoom = 1f;
    /** O quanto o mapa foi movido do jogador, em blocos. */
    private double panX;
    private double panZ;
    private boolean pressed;
    private boolean dragging;
    private double pressX;
    private double pressY;
    private FlatButton open;
    private FlatButton recenter;
    // os pontos, montados quando a fonte muda
    private MapPoints points = MapPoints.EMPTY;
    private @Nullable Object pointsSource;
    // o último ponto calculado por locate (sem alocar por ponto)
    private int px;
    private int py;
    private boolean inside;

    MapTab(TabletLayout layout, Supplier<TabletSnapshot> snapshot, Supplier<@Nullable TabletMapPayload> mapSource,
            NodeText nodes, TabletActions actions, TabletList list) {
        this.layout = layout;
        this.snapshot = snapshot;
        this.mapSource = mapSource;
        this.nodes = nodes;
        this.actions = actions;
        this.list = list;
    }

    private TabletSnapshot snapshot() {
        return snapshot.get();
    }

    /** Os pontos do servidor; até chegarem (e na prévia), os nós da página da Lista. */
    private MapPoints points() {
        TabletMapPayload payload = mapSource.get();
        Object source = payload != null ? payload : snapshot().nodes();
        if (source != pointsSource) {
            pointsSource = source;
            points = payload != null ? MapPoints.of(payload) : MapPoints.ofPage(snapshot().nodes(), nodes::sameDimension);
        }
        return points;
    }

    /** O jogador, que o cliente já sabe onde está (o servidor não manda); na prévia, a origem. */
    private static BlockPos playerPos() {
        LocalPlayer player = Minecraft.getInstance().player;
        return player == null ? PREVIEW_CENTER : player.blockPosition();
    }

    /** O centro do mapa: o jogador mais o quanto o mapa foi movido. */
    private double viewX() {
        return playerPos().getX() + panX;
    }

    private double viewZ() {
        return playerPos().getZ() + panZ;
    }

    private boolean panned() {
        return panX != 0 || panZ != 0;
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
        recenter = buttons.add(TabletScreen.Tab.MAP, new FlatButton(mapX0() + 4, mapY0() + 4, RECENTER_W, RECENTER_H,
                tr("map.recenter"), (g, b, hovered) -> TabletButtons.paintText(g, b, hovered, tr("map.recenter")),
                this::resetPan).tooltip(() -> tr("map.recenter.tooltip")));
    }

    void refresh(boolean shown) {
        open.visible = shown && list.chosen != null;
        recenter.visible = shown && panned();
    }

    private void resetPan() {
        panX = 0;
        panZ = 0;
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

    /** Blocos por pixel: cabe o nó mais longe do jogador (mínimo 16 blocos, no máximo 4.096), vezes o zoom. */
    private double mapScale() {
        double far = 16;
        BlockPos player = playerPos();
        MapPoints all = points();
        for (int i = 0; i < all.count(); i++) {
            long pos = all.positions[i];
            far = Math.max(far, Math.max(Math.abs(BlockPos.getX(pos) - player.getX()),
                    Math.abs(BlockPos.getZ(pos) - player.getZ())));
        }
        far = Math.min(far, 4096);
        return far / (mapH() / 2.0 - 10) / mapZoom;
    }

    /** Calcula o ponto de {@code pos} no mapa (preso à borda se estiver fora) em {@link #px}, {@link #py} e {@link #inside}. */
    private void locate(long pos, double scale) {
        double x = mapX0() + mapW() / 2.0 + (BlockPos.getX(pos) - viewX()) / scale;
        double y = mapY0() + mapH() / 2.0 + (BlockPos.getZ(pos) - viewZ()) / scale;
        inside = x >= mapX0() + 4 && x <= mapX0() + mapW() - 4 && y >= mapY0() + 4 && y <= mapY0() + mapH() - 4;
        px = (int) Math.round(Mth.clamp(x, mapX0() + 4, mapX0() + mapW() - 4));
        py = (int) Math.round(Mth.clamp(y, mapY0() + 4, mapY0() + mapH() - 4));
    }

    private boolean overMap(double mouseX, double mouseY) {
        return mouseX >= mapX0() && mouseX < mapX0() + mapW() && mouseY >= mapY0() && mouseY < mapY0() + mapH();
    }

    private boolean overRecenter(double mouseX, double mouseY) {
        return recenter.visible && recenter.isMouseOver(mouseX, mouseY);
    }

    /** O índice do ponto sob o mouse (o mais perto, até 7 px), ou -1. */
    private int pointAt(double mouseX, double mouseY) {
        if (!overMap(mouseX, mouseY)) {
            return -1;
        }
        double scale = mapScale();
        MapPoints all = points();
        int best = -1;
        double bestDistance = 7 * 7;
        for (int i = 0; i < all.count(); i++) {
            locate(all.positions[i], scale);
            double d = (px - mouseX) * (px - mouseX) + (py - mouseY) * (py - mouseY);
            if (d <= bestDistance) {
                best = i;
                bestDistance = d;
            }
        }
        return best;
    }

    /** O nó escolhido da Lista na página que a Lista mandou (com nome e tier), ou {@code null}. */
    private @Nullable NodeView pageNode(long pos) {
        for (NodeView node : snapshot().nodes()) {
            if (node.key().pos().asLong() == pos) {
                return node;
            }
        }
        return null;
    }

    private boolean isChosen(long pos) {
        return list.chosen != null && list.chosen.pos().asLong() == pos
                && list.chosen.dimension().location().equals(snapshot().dimension());
    }

    // ------------------------------------------------------------------ entrada

    /** Começa um clique ou um arrasto no mapa: o clique só vale ao soltar. Devolve se tratou. */
    boolean mouseClicked(double mouseX, double mouseY) {
        if (!overMap(mouseX, mouseY) || overRecenter(mouseX, mouseY)) {
            return false;
        }
        pressed = true;
        dragging = false;
        pressX = mouseX;
        pressY = mouseY;
        return true;
    }

    /** Arrasta o mapa: o que está sob o mouse acompanha o mouse. Devolve se tratou. */
    boolean mouseDragged(double mouseX, double mouseY, double dragX, double dragY) {
        if (!pressed) {
            return false;
        }
        if (!dragging && Math.hypot(mouseX - pressX, mouseY - pressY) > DRAG_SLOP) {
            dragging = true;
        }
        if (dragging) {
            double scale = mapScale();
            panX -= dragX * scale;
            panZ -= dragY * scale;
        }
        return true;
    }

    /** Soltar sem ter arrastado escolhe a máquina sob o mouse como alvo do painel, ou o nó (ou nenhum). */
    boolean mouseReleased(double mouseX, double mouseY) {
        if (!pressed) {
            return false;
        }
        boolean moved = dragging;
        pressed = false;
        dragging = false;
        if (moved) {
            return true;
        }
        if (machines.click(mouseX, mouseY, actions)) {
            return true;
        }
        int hit = pointAt(mouseX, mouseY);
        if (hit < 0) {
            list.choose(null);
        } else {
            list.choose(new NodeKey(ResourceKey.create(Registries.DIMENSION, snapshot().dimension()),
                    BlockPos.of(points().positions[hit])));
        }
        return true;
    }

    void mouseScrolled(double mouseX, double mouseY, double scrollY) {
        if (overMap(mouseX, mouseY)) {
            mapZoom = Mth.clamp(mapZoom * (scrollY > 0 ? 1.25f : 0.8f), 0.25f, 16f);
        }
    }

    @Nullable Component tooltip(int mouseX, int mouseY) {
        if (dragging) {
            return null;
        }
        Component machine = machines.tooltip(mouseX, mouseY);
        if (machine != null) {
            return machine;
        }
        int hit = pointAt(mouseX, mouseY);
        if (hit < 0) {
            return null;
        }
        long pos = points().positions[hit];
        NodeView node = pageNode(pos);
        if (node != null) {
            return nodes.nodeTooltip(node, false);
        }
        BlockPos at = BlockPos.of(pos);
        return statusName(points().status(hit)).copy().append("\n").append(Component.literal(
                at.getX() + ", " + at.getY() + ", " + at.getZ()).withColor(GuiPaint.TOOLTIP_MUTED));
    }

    void previewSelect(int index) {
        list.previewChoose(snapshot().nodes().get(index).key());
    }

    /** O centro da máquina {@code machine} do roteador escolhido no mapa, ou {@code null} (gancho do e2e). */
    int @Nullable [] machineCenter(int machine) {
        return machines.center(machine);
    }

    /**
     * As máquinas do roteador escolhido, pelo painel dele, e nenhuma sem o painel ou noutra dimensão; o ponto do
     * roteador fora dos pontos vem do {@link #chosenOffMap}.
     */
    private void prepareMachines(double scale) {
        NodeKey key = chosenKey();
        if (key == null) {
            machines.prepare(null, null, playerPos(), scale, 0, 0, 0, 0);
            return;
        }
        BlockPos view = new BlockPos((int) Math.round(viewX()), playerPos().getY(), (int) Math.round(viewZ()));
        machines.prepare(list.panelPayload(), key, view, scale, mapX0(), mapY0(), mapW(), mapH());
    }

    /** O roteador escolhido, com o painel dele já chegado e na dimensão do mapa, ou {@code null}. */
    private @Nullable NodeKey chosenKey() {
        TabletPanelPayload payload = list.panelPayload();
        NodeKey key = list.chosen;
        if (key == null || payload == null || !payload.node().equals(Optional.of(key))
                || !key.dimension().location().equals(snapshot().dimension())) {
            return null;
        }
        return key;
    }

    /** O ponto do roteador escolhido quando o servidor não o mandou (fora do teto), contornado. */
    private void chosenOffMap(GuiGraphics g, double scale) {
        NodeKey key = chosenKey();
        if (key == null || points().indexOf(key.pos().asLong()) >= 0) {
            return;
        }
        locate(key.pos().asLong(), scale);
        GuiPaint.outline(g, px - 4, py - 4, 9, 9, GuiPaint.FG);
        g.fill(px - 3, py - 2, px + 4, py + 3, GuiPaint.RING);
        g.fill(px - 2, py - 3, px + 3, py + 4, GuiPaint.RING);
        GuiPaint.dot(g, px - 2, py - 2, GuiPaint.MUTED);
    }

    /** Quantas máquinas e pixels de linha o mapa desenha (gancho do e2e). */
    int machinesDrawn() {
        return machines.drawn();
    }

    /** Um ponto do mapa perto do canto de baixo à esquerda, longe dos nós (gancho do e2e). */
    int[] emptyPoint() {
        return new int[] {mapX0() + 8, mapY0() + mapH() - 20};
    }

    /** Move o mapa em blocos, como o arrasto (gancho do e2e). */
    void panBy(double blocksX, double blocksZ) {
        panX += blocksX;
        panZ += blocksZ;
    }

    /** O quanto o mapa foi movido do jogador, em blocos {@code {x, z}} (gancho do e2e). */
    double[] pan() {
        return new double[] {panX, panZ};
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
        chosenOffMap(g, scale);
        GuiPaint.textRight(g, font, tr("map.north"), x0 + w - 4, y0 + 4, GuiPaint.DISABLED);
        GuiText.draw(g, font, tr("map.scale", step), x0 + 4, y0 + mapH() - 11, w - 8, GuiPaint.DISABLED);
        renderInfo(g, font, x0, w);
        renderLegend(g, font, x0, w);
    }

    /** A grade e o jogador; devolve o passo da grade, em blocos. */
    private int renderGrid(GuiGraphics g, int x0, int y0, int w, double scale) {
        // grade: o passo é uma potência de 2 em blocos que dê pelo menos 20 px
        int step = 1;
        while (step / scale < 20) {
            step *= 2;
        }
        double vx = viewX();
        double vz = viewZ();
        int cx = x0 + w / 2;
        int cy = y0 + mapH() / 2;
        int gridColor = GuiPaint.VIEW_GRID;
        int startX = (int) (Math.floorDiv((long) Math.floor(vx - w / 2.0 * scale), (long) step) * step);
        for (long bx = startX; (bx - vx) / scale < w / 2.0; bx += step) {
            int gx = (int) Math.round(cx + (bx - vx) / scale);
            if (gx > x0 && gx < x0 + w - 1) {
                g.fill(gx, y0 + 1, gx + 1, y0 + mapH() - 1, gridColor);
            }
        }
        int startZ = (int) (Math.floorDiv((long) Math.floor(vz - mapH() / 2.0 * scale), (long) step) * step);
        for (long bz = startZ; (bz - vz) / scale < mapH() / 2.0; bz += step) {
            int gy = (int) Math.round(cy + (bz - vz) / scale);
            if (gy > y0 && gy < y0 + mapH() - 1) {
                g.fill(x0 + 1, gy, x0 + w - 1, gy + 1, gridColor);
            }
        }
        // o jogador, onde ele está (no centro enquanto o mapa não foi movido)
        BlockPos player = playerPos();
        double ox = cx + (player.getX() - vx) / scale;
        double oy = cy + (player.getZ() - vz) / scale;
        if (ox >= x0 + 4 && ox <= x0 + w - 4 && oy >= y0 + 4 && oy <= y0 + mapH() - 4) {
            int ix = (int) Math.round(ox);
            int iy = (int) Math.round(oy);
            g.fill(ix - 3, iy, ix + 4, iy + 1, GuiPaint.FG);
            g.fill(ix, iy - 3, ix + 1, iy + 4, GuiPaint.FG);
        }
        return step;
    }

    private void renderPoints(GuiGraphics g, int mouseX, int mouseY, double scale) {
        int hovered = dragging ? -1 : pointAt(mouseX, mouseY);
        MapPoints all = points();
        for (int i = 0; i < all.count(); i++) {
            long pos = all.positions[i];
            locate(pos, scale);
            int color = statusColor(all.status(i));
            boolean chosen = isChosen(pos);
            if (chosen || i == hovered) {
                GuiPaint.outline(g, px - 4, py - 4, 9, 9, chosen ? GuiPaint.FG : GuiPaint.MUTED);
            }
            if (inside) {
                g.fill(px - 3, py - 2, px + 4, py + 3, GuiPaint.RING);
                g.fill(px - 2, py - 3, px + 3, py + 4, GuiPaint.RING);
                GuiPaint.dot(g, px - 2, py - 2, color);
            } else {
                // fora do mapa: presa na borda, vazada
                GuiPaint.outline(g, px - 2, py - 2, 5, 5, color);
            }
        }
    }

    /** A máquina-alvo do painel, o nó escolhido ou a dica. */
    private void renderInfo(GuiGraphics g, Font font, int x0, int w) {
        int infoY = layout.top + mapInfoY();
        if (machines.renderInfo(g, font, x0, infoY, w)) {
            return;
        }
        NodeKey chosenKey = list.chosen;
        if (chosenKey != null && chosenKey.dimension().location().equals(snapshot().dimension())) {
            long pos = chosenKey.pos().asLong();
            int index = points().indexOf(pos);
            NodeView page = pageNode(pos);
            NodeStatus statusValue = page != null ? page.status() : index >= 0 ? points().status(index) : null;
            GuiPaint.box(g, x0, infoY, w - 50, 16, GuiPaint.BUTTON, GuiPaint.BUTTON_BORDER);
            int statusW = 0;
            if (statusValue != null) {
                Component status = statusName(statusValue);
                statusW = font.width(status);
                GuiPaint.text(g, font, status, x0 + w - 56 - statusW, infoY + 4, statusColor(statusValue));
            }
            MutableComponent text = page != null
                    ? nodes.nodeName(page).copy().append(Component.literal("  ").append(nodes.coords(page))
                            .withColor(GuiPaint.MUTED))
                    // um roteador fora da página da Lista: só as coordenadas
                    : Component.literal(chosenKey.pos().getX() + ", " + chosenKey.pos().getY() + ", "
                            + chosenKey.pos().getZ()).withColor(GuiPaint.MUTED);
            GuiText.draw(g, font, text, x0 + 5, infoY + 4, w - 66 - statusW, GuiPaint.FG);
        } else {
            MapPoints all = points();
            Component hint = all.omitted > 0 ? tr("map.hint.omitted", all.omitted)
                    : all.otherDimension > 0 ? tr("map.hint.other", all.otherDimension) : tr("map.hint");
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
