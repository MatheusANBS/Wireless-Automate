package io.github.matheusanbs.wirelessautomate.client.tv.editor;

/**
 * As áreas da aba Editar para uma janela de {@code w} × {@code h} com o canto em ({@code x}, {@code y}): a coluna da
 * esquerda de {@link #LEFT_W} px (a faixa das abas Peças, Componentes e Camadas e, embaixo dela, o corpo da aba aberta,
 * com a altura que sobra), a da direita de {@link #RIGHT_W} px (as propriedades) e o centro com a barra da base em
 * cima, a miniatura e a barra de botões embaixo. Toda a largura e a altura que sobram vão para a miniatura. Na escala
 * 3 de 1366 × 768 (447 × 248 úteis) a miniatura tem 187 × 182. Pura, com JUnit; refeita só quando a janela muda.
 */
public record TvEditorLayout(TvRect leftTabs, TvRect leftBody, TvRect[] pieceButtons, TvRect props, TvRect base,
        TvRect canvas, TvRect buttons) {
    public static final int MIN_W = 400;
    public static final int MIN_H = 240;
    /** O cabeçalho da tela (título, abas, ✕ e o tema) fica acima disto. */
    public static final int HEADER = 26;
    public static final int PAD = 6;
    public static final int GAP = 4;
    public static final int LEFT_W = 108;
    public static final int RIGHT_W = 132;
    public static final int BASE_H = 12;
    public static final int BUTTONS_H = TvIcons.SIZE;
    public static final int TABS_H = TvIcons.SIZE;
    public static final int PIECE_H = TvIcons.SIZE;
    public static final int PIECE_COUNT = 8;

    /** Se a janela cabe no mínimo do editor; abaixo disso a aba só mostra o pedido de uma escala menor. */
    public static boolean fits(int w, int h) {
        return w >= MIN_W && h >= MIN_H;
    }

    /** O corpo da aba Componentes: a lista inteira. */
    public TvRect components() {
        return leftBody;
    }

    /** O corpo da aba Camadas: a lista inteira, com a altura toda da coluna. */
    public TvRect layers() {
        return leftBody;
    }

    public static TvEditorLayout of(int x, int y, int w, int h) {
        int top = y + HEADER;
        int bottom = y + h - PAD;
        int leftX = x + PAD;
        int rightX = x + w - PAD - RIGHT_W;
        int centerX = leftX + LEFT_W + GAP;
        int centerW = rightX - GAP - centerX;

        TvRect leftTabs = new TvRect(leftX, top, LEFT_W, TABS_H);
        int bodyY = top + TABS_H + 3;
        TvRect leftBody = new TvRect(leftX, bodyY, LEFT_W, Math.max(0, bottom - bodyY));
        TvRect[] pieceButtons = new TvRect[PIECE_COUNT];
        for (int i = 0; i < PIECE_COUNT; i++) {
            pieceButtons[i] = new TvRect(leftX, bodyY + i * (PIECE_H + 2), LEFT_W, PIECE_H);
        }

        TvRect props = new TvRect(rightX, top, RIGHT_W, bottom - top);
        TvRect base = new TvRect(centerX, top, centerW, BASE_H);
        TvRect buttons = new TvRect(centerX, bottom - BUTTONS_H, centerW, BUTTONS_H);
        int canvasY = base.bottom() + 3;
        TvRect canvas = new TvRect(centerX, canvasY, centerW, Math.max(0, buttons.y() - 3 - canvasY));
        return new TvEditorLayout(leftTabs, leftBody, pieceButtons, props, base, canvas, buttons);
    }
}
