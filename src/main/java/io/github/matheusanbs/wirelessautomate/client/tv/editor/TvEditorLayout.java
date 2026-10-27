package io.github.matheusanbs.wirelessautomate.client.tv.editor;

/**
 * As áreas da aba Editar pela decisão 2 da spec da etapa 2, para uma janela de {@code w} × {@code h} com o canto em
 * ({@code x}, {@code y}): a coluna da esquerda de {@link #LEFT_W} px (Peças em cima, Componentes embaixo), a da
 * direita de {@link #RIGHT_W} px (as propriedades) e o centro com a barra da base em cima, a miniatura, a barra de
 * botões e as Camadas embaixo ({@link #LAYERS_H} px). Toda a largura e a altura que sobram vão para a miniatura. Na
 * escala 3 de 1366 × 768 (447 × 248 úteis) a miniatura tem 203 × 132. Pura, com JUnit; refeita só quando a janela
 * muda.
 */
public record TvEditorLayout(TvRect piecesLabel, TvRect[] pieceButtons, TvRect componentsLabel, TvRect components,
        TvRect props, TvRect base, TvRect canvas, TvRect buttons, TvRect layers) {
    public static final int MIN_W = 400;
    public static final int MIN_H = 240;
    /** O cabeçalho da tela (título, abas, ✕ e o tema) fica acima disto. */
    public static final int HEADER = 26;
    public static final int PAD = 6;
    public static final int GAP = 4;
    public static final int LEFT_W = 92;
    public static final int RIGHT_W = 132;
    public static final int BASE_H = 12;
    public static final int BUTTONS_H = 14;
    public static final int LAYERS_H = 48;
    public static final int LABEL_H = 9;
    public static final int PIECE_H = 14;
    public static final int PIECE_COUNT = 3;

    /** Se a janela cabe no mínimo do editor; abaixo disso a aba só mostra o pedido de uma escala menor. */
    public static boolean fits(int w, int h) {
        return w >= MIN_W && h >= MIN_H;
    }

    public static TvEditorLayout of(int x, int y, int w, int h) {
        int top = y + HEADER;
        int bottom = y + h - PAD;
        int leftX = x + PAD;
        int rightX = x + w - PAD - RIGHT_W;
        int centerX = leftX + LEFT_W + GAP;
        int centerW = rightX - GAP - centerX;

        TvRect piecesLabel = new TvRect(leftX, top, LEFT_W, LABEL_H);
        TvRect[] pieceButtons = new TvRect[PIECE_COUNT];
        int buttonY = top + LABEL_H + 2;
        for (int i = 0; i < PIECE_COUNT; i++) {
            pieceButtons[i] = new TvRect(leftX, buttonY + i * (PIECE_H + 2), LEFT_W, PIECE_H);
        }
        int componentsY = buttonY + PIECE_COUNT * (PIECE_H + 2) + 3;
        TvRect componentsLabel = new TvRect(leftX, componentsY, LEFT_W, LABEL_H);
        TvRect components = new TvRect(leftX, componentsY + LABEL_H + 2, LEFT_W,
                Math.max(0, bottom - componentsY - LABEL_H - 2));

        TvRect props = new TvRect(rightX, top, RIGHT_W, bottom - top);
        TvRect base = new TvRect(centerX, top, centerW, BASE_H);
        TvRect layers = new TvRect(centerX, bottom - LAYERS_H, centerW, LAYERS_H);
        TvRect buttons = new TvRect(centerX, layers.y() - GAP - BUTTONS_H, centerW, BUTTONS_H);
        int canvasY = base.bottom() + 3;
        TvRect canvas = new TvRect(centerX, canvasY, centerW, Math.max(0, buttons.y() - 3 - canvasY));
        return new TvEditorLayout(piecesLabel, pieceButtons, componentsLabel, components, props, base, canvas,
                buttons, layers);
    }
}
