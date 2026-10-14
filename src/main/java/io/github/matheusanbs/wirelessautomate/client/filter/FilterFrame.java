package io.github.matheusanbs.wirelessautomate.client.filter;

import static io.github.matheusanbs.wirelessautomate.client.filter.FilterLayout.GAP;
import static io.github.matheusanbs.wirelessautomate.client.filter.FilterLayout.INV_H;
import static io.github.matheusanbs.wirelessautomate.client.filter.FilterLayout.LIST_Y;
import static io.github.matheusanbs.wirelessautomate.client.filter.FilterLayout.PAD;
import static io.github.matheusanbs.wirelessautomate.client.filter.FilterLayout.PANEL_W;
import static io.github.matheusanbs.wirelessautomate.client.filter.FilterLayout.ROW;
import static io.github.matheusanbs.wirelessautomate.client.filter.FilterLayout.X0;

/**
 * As medidas da tela de filtro que dependem do tamanho atual: o canto do painel, a coluna da direita, a
 * largura da lista, o alto do inventário e quantas linhas a lista mostra. Lidas da tela na hora.
 */
final class FilterFrame {
    private final FilterScreen screen;

    FilterFrame(FilterScreen screen) {
        this.screen = screen;
    }

    int left() {
        return screen.getGuiLeft();
    }

    int top() {
        return screen.getGuiTop();
    }

    int panelX() {
        return screen.getXSize() - X0 - PANEL_W;
    }

    int ix() {
        return panelX() + PAD;
    }

    int innerBottom() {
        return screen.getYSize() - X0 - PAD;
    }

    int listW() {
        return screen.getXSize() - X0 - PANEL_W - GAP - X0;
    }

    int invTop() {
        return screen.getYSize() - X0 - INV_H;
    }

    int listRows() {
        return Math.max(1, (invTop() - 12 - LIST_Y) / ROW);
    }
}
