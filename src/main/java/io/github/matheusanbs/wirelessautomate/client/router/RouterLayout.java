package io.github.matheusanbs.wirelessautomate.client.router;

import io.github.matheusanbs.wirelessautomate.client.ThemeToggle;
import io.github.matheusanbs.wirelessautomate.menu.RouterMenu;
import java.util.function.BooleanSupplier;

/**
 * A disposição da tela do roteador ({@link RouterScreen}) pelo tamanho dela, em coordenadas relativas ao
 * canto da tela. Larga (a de abertura): à esquerda o visor, os cartões e o inventário; à direita o painel
 * inteiro. Estreita: as abas na largura toda, o visor com o cubo e os cartões embaixo à esquerda e, à
 * direita, o resto do painel em cima do inventário. Com o Upgrade de Alcance, o botão Máquinas acima do
 * visor e, se a coluna couber, a lista ao lado dele.
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
    /** Altura do cubo embaixo do visor, na estreita: 3 linhas de 13 px. */
    static final int NARROW_NET_H = 3 * 13 + 2 * 2;
    /** Slot do upgrade (o fundo), no canto direito do cabeçalho; o tier e a vazão ficam à esquerda dele. */
    static final int UPGRADE_BG_Y = RouterMenu.UPGRADE_Y - 1;
    /** Faixa do botão "Máquinas" acima do visor (tirada da altura dele), com o upgrade de alcance. */
    static final int RANGE_STRIP = 14;
    /** Largura da lista quando ela fica ao lado do visor ({@link #sideBySide}). */
    private static final int SIDE_LIST_W = 150;
    /** Espaço entre a lista e o visor lado a lado. */
    private static final int SIDE_GAP = 4;
    /** Largura mínima do visor ao lado da lista: abaixo disso, os dois alternam no mesmo lugar. */
    private static final int SIDE_MIN_VIEW = 140;

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

    /** Fundo dos slots de cartão: embaixo do visor (larga) ou do cubo (estreita). */
    int cardsBgY() {
        return wide() ? inventoryY() - 1 - 4 - 18 : h - 8 - 18;
    }

    /** Topo do visor (ou da lista): abaixo do botão Máquinas, com o upgrade de alcance. */
    int boxY() {
        return columnsY() + (rangeShown.getAsBoolean() ? RANGE_STRIP : 0);
    }

    /** Altura do visor (ou da lista): até o cubo (estreita) ou os cartões (larga). */
    int boxH() {
        int bottom = wide() ? cardsBgY() - 4 : cardsBgY() - 4 - NARROW_NET_H - 4;
        return bottom - boxY();
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

    /**
     * A coluna da esquerda é larga o bastante para a lista e o visor lado a lado; senão, o botão
     * Máquinas alterna os dois no mesmo lugar.
     */
    boolean sideBySide() {
        return rangeShown.getAsBoolean() && lw() >= SIDE_LIST_W + SIDE_GAP + SIDE_MIN_VIEW;
    }

    /** Começo do visor na coluna da esquerda (depois da lista, lado a lado). */
    int viewX() {
        return X0 + (sideBySide() ? SIDE_LIST_W + SIDE_GAP : 0);
    }

    /** Largura do visor (sem a lista, lado a lado). */
    int viewBoxW() {
        return sideBySide() ? lw() - SIDE_LIST_W - SIDE_GAP : lw();
    }

    /** Largura da lista. */
    int listW() {
        return sideBySide() ? SIDE_LIST_W : lw();
    }
}
