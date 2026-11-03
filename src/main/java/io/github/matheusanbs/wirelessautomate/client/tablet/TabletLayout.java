package io.github.matheusanbs.wirelessautomate.client.tablet;

/**
 * A grade comum da tela do Tablet: o canto e o tamanho do painel, a linha do título e das abas, o corpo, a
 * coluna lateral da aba Redes de hoje e a geometria do Tablet novo (coluna esquerda, painel direito e rodapé). A
 * tela a põe no lugar a cada {@code init}; as abas leem daqui.
 */
final class TabletLayout {
    // 420 × 280 é o tamanho do desenho; 240 de altura cabe em 1080p com a escala automática. A tela cresce pela
    // borda direita, pela de baixo e pelo canto, e o tamanho fica lembrado na sessão.
    static final int MIN_W = 420;
    static final int MIN_H = 240;
    static final int DEFAULT_W = 420;
    static final int DEFAULT_H = 280;
    static final int X0 = 9;
    /** A linha do título e das abas. */
    static final int HEAD_Y = 10;
    /** A linha embaixo da barra. */
    static final int SEP_Y = 23;
    static final int BODY_Y = 30;
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

    /** Tablet novo: a divisória da coluna esquerda e o começo do painel direito (mockup: 150 e X0 = 158). */
    static final int COLUMN_RULE = 150;
    static final int PANEL_X0 = 158;

    /** A borda direita do painel direito, inclusive (mockup: X1 = 411 com 420 de largura). */
    int panelX1() {
        return w - 9;
    }

    /** A linha do rodapé (mockup: H − 27) e o topo dos botões dele (H − 21). */
    int footerRuleY() {
        return h - 27;
    }

    int footerY() {
        return h - 21;
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
