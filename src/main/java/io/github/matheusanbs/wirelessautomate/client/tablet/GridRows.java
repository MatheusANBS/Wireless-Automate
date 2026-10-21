package io.github.matheusanbs.wirelessautomate.client.tablet;

import static io.github.matheusanbs.wirelessautomate.client.tablet.TabletText.tr;

import io.github.matheusanbs.wirelessautomate.client.GuiPaint;
import io.github.matheusanbs.wirelessautomate.client.GuiText;
import io.github.matheusanbs.wirelessautomate.client.ResourceStyle;
import io.github.matheusanbs.wirelessautomate.client.router.FaceNet;
import io.github.matheusanbs.wirelessautomate.client.tablet.ListLayout.Hot;
import io.github.matheusanbs.wirelessautomate.client.tablet.ListLayout.Kind;
import io.github.matheusanbs.wirelessautomate.menu.TabletSnapshot.NodeView;
import io.github.matheusanbs.wirelessautomate.network.LoadedTypes;
import io.github.matheusanbs.wirelessautomate.network.PortMode;
import io.github.matheusanbs.wirelessautomate.network.ResourceType;
import io.github.matheusanbs.wirelessautomate.range.RemoteSide;
import java.util.List;
import java.util.UUID;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * A Grade da Lista do Tablet, como o Controller do XNet: uma linha por roteador (14 px) e uma coluna de 30 px por
 * tipo carregado, com uma tarja de 4 × 8 por face com modo (na cor do modo, contorno de grafite) ou um traço sem
 * faces; depois o estado e a rede. Usa só o que a página já leva ({@code faceModes}, o estado, as redes). Clicar numa
 * linha escolhe o nó e abre o painel dele, na aba do tipo da célula clicada.
 */
final class GridRows {
    static final int HEADER_H = 12;
    static final int ROW_H = 14;
    private static final int TYPE_W = 30;
    private static final int STATE_W = 72;
    private static final int NAME_MIN = 110;
    private static final int NETWORK_MAX = 90;
    private static final int STRIPE_W = 4;
    private static final int STRIPE_H = 8;
    private static final int PAD = 4;
    /** Cada nó tem até este tanto de células de tipo (o argumento da área é {@code nó * CELLS + tipo}). */
    static final int CELLS = 8;

    private final TabletList list;
    /** Onde as colunas começam, refeitas em {@link #arrange}. */
    private int typesX;
    private int stateX;
    private int networkX;
    private int right;

    GridRows(TabletList list) {
        this.list = list;
    }

    int rowCount() {
        return list.snapshot().nodes().size();
    }

    /** Arranja as linhas a partir da rolagem e as células de cada uma, nas áreas do {@code layout}. */
    void arrange(ListLayout layout) {
        List<ResourceType> types = LoadedTypes.LIST;
        right = layout.x0 + layout.listW - 3;
        int typesW = types.size() * TYPE_W;
        int stateW = STATE_W;
        int networkW = Math.min(NETWORK_MAX, layout.listW - NAME_MIN - typesW - stateW - 2 * PAD);
        if (networkW < 40) {
            // sem lugar para a rede, ela sai (a rede de cada nó segue na dica e no painel)
            networkW = 0;
        }
        networkX = right - networkW;
        stateX = networkX - stateW;
        typesX = stateX - typesW;
        int y = layout.rowsTop + HEADER_H;
        layout.drawnUntil = list.scroll;
        List<NodeView> nodes = list.snapshot().nodes();
        for (int r = list.scroll; r < nodes.size(); r++) {
            if (y + ROW_H > layout.rowsBottom) {
                break;
            }
            layout.drawnUntil = r + 1;
            layout.hots.add(new Hot(Kind.GRID_ROW, layout.x0, y, layout.listW, ROW_H, r));
            for (int t = 0; t < types.size(); t++) {
                layout.hots.add(new Hot(Kind.GRID_CELL, typesX + t * TYPE_W, y, TYPE_W, ROW_H, r * CELLS + t));
            }
            y += ROW_H;
        }
        layout.rowsEndY = y;
    }

    /** O cabeçalho (Roteador, os ícones dos tipos, Estado, Rede) e as linhas arranjadas. */
    void render(GuiGraphics g, Font font, ListLayout layout, int mx, int my) {
        List<ResourceType> types = LoadedTypes.LIST;
        int hy = layout.rowsTop;
        g.fill(layout.x0, hy, layout.x0 + layout.listW, hy + HEADER_H, GuiPaint.INSET);
        GuiText.draw(g, font, tr("grid.router"), layout.x0 + PAD + 8, hy + 2, typesX - layout.x0 - 2 * PAD, GuiPaint.MUTED);
        for (int t = 0; t < types.size(); t++) {
            ResourceStyle.drawIcon(g, types.get(t), typesX + t * TYPE_W + (TYPE_W - ResourceStyle.ICON) / 2, hy - 1);
        }
        GuiText.draw(g, font, tr("grid.state"), stateX, hy + 2, STATE_W - 2, GuiPaint.MUTED);
        if (networkX < right) {
            GuiText.draw(g, font, tr("grid.network"), networkX, hy + 2, right - networkX, GuiPaint.MUTED);
        }
        List<NodeView> nodes = list.snapshot().nodes();
        for (Hot hot : layout.hots) {
            if (hot.kind() == Kind.GRID_ROW) {
                renderRow(g, font, hot, nodes.get(hot.arg()), types, mx, my);
            }
        }
    }

    private void renderRow(GuiGraphics g, Font font, Hot hot, NodeView node, List<ResourceType> types, int mx, int my) {
        int x = hot.x();
        int y = hot.y();
        boolean chosen = node.key().equals(list.chosen);
        if (chosen) {
            g.fill(x, y, x + hot.w(), y + ROW_H, GuiPaint.ROW_SELECTED);
            g.fill(x, y, x + 2, y + ROW_H, GuiPaint.ACCENT);
        } else if (hot.contains(mx, my)) {
            g.fill(x, y, x + hot.w(), y + ROW_H, GuiPaint.ROW_HOVER);
        }
        GuiPaint.dot(g, x + PAD + 1, y + (ROW_H - 5) / 2, GuiPaint.tierColor(node.tier()));
        int nameX = x + PAD + 9;
        GuiText.draw(g, font, list.texts.nodeName(node), nameX, y + 3, typesX - nameX - PAD, GuiPaint.FG);
        for (int t = 0; t < types.size(); t++) {
            renderCell(g, node, types.get(t), typesX + t * TYPE_W, y);
        }
        Component state = list.texts.gridState(node);
        GuiText.draw(g, font, state, stateX, y + 3, STATE_W - 3, GuiPaint.MUTED);
        if (networkX < right) {
            renderNetwork(g, font, node, networkX, y);
        }
    }

    /** Uma tarja por face com modo, na ordem do cubo; sem nenhuma, um traço apagado. */
    private void renderCell(GuiGraphics g, NodeView node, ResourceType type, int x, int y) {
        int count = 0;
        for (RemoteSide side : FaceNet.CHIP_ORDER) {
            if (node.faceMode(type, side) != PortMode.NONE) {
                count++;
            }
        }
        if (count == 0) {
            g.fill(x + TYPE_W / 2 - 3, y + ROW_H / 2, x + TYPE_W / 2 + 3, y + ROW_H / 2 + 1, GuiPaint.DISABLED);
            return;
        }
        int width = count * STRIPE_W + (count - 1);
        int sx = x + (TYPE_W - width) / 2;
        int sy = y + (ROW_H - STRIPE_H) / 2;
        for (RemoteSide side : FaceNet.CHIP_ORDER) {
            PortMode mode = node.faceMode(type, side);
            if (mode == PortMode.NONE) {
                continue;
            }
            GuiPaint.outline(g, sx, sy, STRIPE_W, STRIPE_H, GuiPaint.FG);
            g.fill(sx + 1, sy + 1, sx + STRIPE_W - 1, sy + STRIPE_H - 1, GuiPaint.modeColor(mode));
            sx += STRIPE_W + 1;
        }
    }

    /** A rede do nó: bolinha e nome da primeira (com "+N" se há mais), ou um traço coral sem rede. */
    private void renderNetwork(GuiGraphics g, Font font, NodeView node, int x, int y) {
        UUID first = null;
        int distinct = 0;
        for (ResourceType type : LoadedTypes.LIST) {
            for (UUID id : node.networks(type)) {
                if (first == null) {
                    first = id;
                }
                if (!id.equals(first)) {
                    distinct++;
                }
            }
        }
        if (first == null) {
            g.fill(x + 1, y + ROW_H / 2, x + 8, y + ROW_H / 2 + 1, GuiPaint.ACCENT);
            return;
        }
        var network = list.snapshot().network(first);
        int color = network.map(n -> 0xFF000000 | n.color()).orElse(GuiPaint.MUTED);
        GuiPaint.dot(g, x, y + (ROW_H - 5) / 2, color);
        Component name = Component.literal(network.map(n -> n.name()).orElse("?")
                + (distinct > 0 ? " +" + distinct : ""));
        GuiText.draw(g, font, name, x + 8, y + 3, right - x - 8, GuiPaint.FG);
    }

    /** O tipo da célula {@code arg} de uma área {@link Kind#GRID_CELL}. */
    static ResourceType typeOf(int arg) {
        return LoadedTypes.LIST.get(Math.min(arg % CELLS, LoadedTypes.LIST.size() - 1));
    }
}
