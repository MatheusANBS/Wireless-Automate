package io.github.matheusanbs.wirelessautomate.client.tablet;

/**
 * A grade comum da tela do Tablet: o canto e o tamanho do painel, as linhas do cabeçalho e das abas, o corpo e a
 * coluna lateral das abas Redes e Grupos. A tela a põe no lugar a cada {@code init}; as abas leem daqui.
 */
final class TabletLayout {
    // O mínimo é 300×240 e abre em 420×280 (a lista e o painel lado a lado); a tela cresce pela
    // borda direita, pela de baixo e pelo canto, e o tamanho fica lembrado na sessão.
    static final int MIN_W = 300;
    static final int MIN_H = 240;
    static final int DEFAULT_W = 420;
    static final int DEFAULT_H = 280;
    static final int X0 = 9;
    static final int HEAD_Y = 8;
    static final int TAB_Y = 26;
    static final int TAB_H = 15;
    static final int SEP_Y = 45;
    static final int BODY_Y = 50;
    static final int ROW_H = 14;
    // redes e grupos
    static final int SIDE_W = 120;
    static final int RX = X0 + SIDE_W + 8;
    private static final int SIDE_BOTTOM = 200;
    private static final int NEW_Y = 203;
    private static final int NEW_BOX_Y = 217;

    /** Canto do painel na tela. */
    int left;
    int top;
    int w = MIN_W;
    int h = MIN_H;

    void place(int left, int top, int w, int h) {
        this.left = left;
        this.top = top;
        this.w = w;
        this.h = h;
    }

    int x1() {
        return w - 9;
    }

    int rw() {
        return x1() - RX;
    }

    int sideBottom() {
        return SIDE_BOTTOM + (h - MIN_H);
    }

    int newY() {
        return NEW_Y + (h - MIN_H);
    }

    int newBoxY() {
        return NEW_BOX_Y + (h - MIN_H);
    }
}
