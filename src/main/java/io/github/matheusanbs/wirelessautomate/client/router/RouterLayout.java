package io.github.matheusanbs.wirelessautomate.client.router;

import io.github.matheusanbs.wirelessautomate.client.ThemeToggle;
import io.github.matheusanbs.wirelessautomate.menu.RouterMenu;
import java.util.function.BooleanSupplier;

/**
 * A disposição da tela do roteador ({@link RouterScreen}) pelo tamanho dela, em coordenadas relativas ao
 * canto da tela. Larga (a de abertura): à esquerda o visor e o inventário; à direita o painel inteiro, com
 * os slots de cartão na linha do Filtro. Estreita: as abas na largura toda, o visor com o quadro das faces embaixo
 * à esquerda e, à direita, o resto do painel em cima do inventário. Com o Upgrade de Alcance, o visor encolhe e a
 * lista das máquinas fica embaixo dele, com a faixa "Máquinas · 10/16" no meio.
 */
final class RouterLayout {
    // O mínimo é largo e baixo o bastante para caber na menor escala automática (320×240).
    static final int MIN_W = 300;
    static final int MIN_H = 240;
    static final int X0 = 9;
    static final int HEAD_Y = 8;
    /** Quanto os slots de upgrade recuam para dar lugar ao botão do tema no canto direito do cabeçalho. */
    static final int HEAD_SHIFT = ThemeToggle.SIZE + 4;
    static final int PILL_H = 13;
    /** Topo das abas (estreita) ou das duas colunas (larga). */
    static final int TOP_Y = 26;
    /** Topo das colunas na disposição estreita, abaixo das abas e da linha. */
    static final int NARROW_BODY_Y = 47;
    /** Inventário (e a coluna da direita na estreita): 9 slots. */
    private static final int INV_W = 162;
    static final int COLUMN_GAP = 8;
    /** Largura do painel na disposição larga: do mínimo (as abas com o nome da ativa) ao máximo. */
    private static final int PANEL_MIN = 210;
    private static final int PANEL_MAX = 240;
    /** A partir desta largura, a disposição larga. */
    private static final int WIDE_MIN = 2 * X0 + COLUMN_GAP + INV_W + PANEL_MIN;
    /** Altura do quadro das faces embaixo do visor, na estreita (com Limpar, Copiar e Colar embaixo do cubo). */
    static final int NARROW_NET_H = PanelLayout.NARROW_FRAME_H;
    /** Slot do upgrade (o fundo), no canto direito do cabeçalho; o tier e a vazão ficam à esquerda dele. */
    static final int UPGRADE_BG_Y = RouterMenu.UPGRADE_Y - 1;
    /** Faixa do título "Máquinas · 10/16" entre o visor e a lista, com o upgrade de alcance. */
    static final int STRIP_H = 12;
    /** Vão entre o visor e a faixa. */
    private static final int STRIP_GAP = 3;
    /** Altura mínima e máxima do visor quando a lista fica embaixo dele. */
    private static final int MIN_VIEW_H = 44;
    private static final int MAX_VIEW_H = 100;

    private final BooleanSupplier rangeShown;
    private int w = MIN_W;
    private int h = MIN_H;

    /** @param rangeShown o Upgrade de Alcance vale (o botão Máquinas e a lista entram na coluna) */
    RouterLayout(BooleanSupplier rangeShown) {
        this.rangeShown = rangeShown;
    }

    void setSize(int w, int h) {
        this.w = w;
        this.h = h;
    }

    int x1() {
        return w - 9;
    }

    /** Disposição larga: o painel inteiro ao lado do visor. */
    boolean wide() {
        return w >= WIDE_MIN;
    }

    /** Largura da coluna da direita: o painel (larga) ou o resto do painel com o inventário (estreita). */
    int rw() {
        return wide() ? Math.max(PANEL_MIN, Math.min(PANEL_MAX, w - 2 * X0 - COLUMN_GAP - INV_W)) : INV_W;
    }

    /** Começo da coluna da direita, presa à borda direita. */
    int rx() {
        return x1() - rw();
    }

    /** Largura da coluna da esquerda (visor, cubo na estreita, cartões e, na larga, o inventário). */
    int lw() {
        return rx() - COLUMN_GAP - X0;
    }

    /** Topo das colunas. */
    int columnsY() {
        return wide() ? TOP_Y : NARROW_BODY_Y;
    }

    /** Topo do inventário do jogador (o canto do item), colado na borda de baixo. */
    int inventoryY() {
        return RouterMenu.INVENTORY_Y + (h - MIN_H);
    }

    /** O inventário fica na coluna da esquerda (larga) ou embaixo do painel (estreita). */
    int inventoryX() {
        return (wide() ? X0 : rx()) + 1;
    }

    /** Topo do quadro das faces embaixo do visor, na estreita. */
    int narrowNetY() {
        return h - 8 - NARROW_NET_H;
    }

    /** Topo do visor, o da coluna. */
    int boxY() {
        return columnsY();
    }

    /** Altura da coluna do visor (e, com o upgrade de alcance, da lista embaixo dele): até o quadro das faces ou o inventário. */
    int boxH() {
        int bottom = wide() ? inventoryY() - 1 - 4 : narrowNetY() - 4;
        return bottom - boxY();
    }

    /** Altura do visor: a coluna inteira, ou, com a lista das máquinas embaixo, uma parte dela. */
    int viewH() {
        int total = boxH();
        if (!rangeShown.getAsBoolean()) {
            return total;
        }
        return Math.max(MIN_VIEW_H, Math.min(MAX_VIEW_H, total * 38 / 100));
    }

    /** Topo da faixa "Máquinas · 10/16", embaixo do visor. */
    int stripY() {
        return boxY() + viewH() + STRIP_GAP;
    }

    /** Topo da lista das máquinas, embaixo da faixa. */
    int listY() {
        return stripY() + STRIP_H;
    }

    /** Altura da lista, até o fim da coluna. */
    int listH() {
        return boxY() + boxH() - listY();
    }

    /** Fundo do slot do upgrade, no canto direito do cabeçalho. */
    int upgradeBgX() {
        return w - 26 - 1 - HEAD_SHIFT;
    }

    /** Fundo do slot do Upgrade de Alcance, à esquerda do slot de upgrade. */
    int rangeBgX() {
        return upgradeBgX() - 18;
    }

    /** Onde termina a pílula do tier: antes dos dois slots de upgrade, com folga. */
    int headEnd() {
        return rangeBgX() - 4;
    }
}
