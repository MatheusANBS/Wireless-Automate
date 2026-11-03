package io.github.matheusanbs.wirelessautomate.client.tablet;

/**
 * A grade comum da tela do Tablet: o canto e o tamanho do painel, a linha do título e das abas, o corpo e a
 * geometria do Tablet novo (coluna esquerda, painel direito e rodapé). A tela a põe no lugar a cada {@code init}; as
 * abas leem daqui (a aba Redes, de outro pacote, também).
 */
public final class TabletLayout {
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

    /** Canto do painel na tela. */
    public int left;
    public int top;
    public int w = MIN_W;
    public int h = MIN_H;

    void place(int left, int top, int w, int h) {
        this.left = left;
        this.top = top;
        this.w = w;
        this.h = h;
    }

    /** Tablet novo: a divisória da coluna esquerda e o começo do painel direito (mockup: 150 e X0 = 158). */
    public static final int COLUMN_RULE = 150;
    public static final int PANEL_X0 = 158;

    /** A borda direita do painel direito, inclusive (mockup: X1 = 411 com 420 de largura). */
    public int panelX1() {
        return w - 9;
    }

    /** A linha do rodapé (mockup: H − 27) e o topo dos botões dele (H − 21). */
    public int footerRuleY() {
        return h - 27;
    }

    public int footerY() {
        return h - 21;
    }

    int x1() {
        return w - 9;
    }
}
