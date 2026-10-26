package io.github.matheusanbs.wirelessautomate.client.repairer;

/**
 * As medidas da tela do Reparador, em pixels de interface a partir do canto da tela (as do mockup aprovado,
 * {@code docs/preview/reparador-de-cristais.html}, com o estoque e as saídas um pouco mais altos: no mockup a segunda
 * fileira de saídas invade a linha de estado). Os slots ficam no {@code RepairerMenu}, 1 px para dentro do fundo.
 */
final class RepairerLayout {
    static final int W = 352;
    static final int H = 272;
    static final int SLOT = 18;
    static final int X0 = 8;

    static final Rect TITLE = new Rect(X0, 8, 156, 9);
    static final Rect ENERGY_BAR = new Rect(168, 6, 150, 12);
    static final int THEME_X = W - 8 - 11;
    static final int THEME_Y = 7;

    /** A câmara, com o quadro afundado 1 px para fora. */
    static final Rect CHAMBER = new Rect(8, 26, 192, 132);

    static final Rect CRACKED_LABEL = new Rect(208, 28, 136, 8);
    static final Rect FEED_LABEL = new Rect(208, 81, 136, 8);
    /** "4 por reparo", à direita do slot do estoque. */
    static final Rect FEED_PER = new Rect(232, 96, 76, 8);
    static final Rect REPAIRED_LABEL = new Rect(208, 113, 136, 8);
    /** A seta do estoque para as saídas, 5 × 3 px. */
    static final int ARROW_X = 312;
    static final int ARROW_Y = 98;

    static final Rect STATUS = new Rect(8, 164, 336, 14);
    static final Rect INVENTORY_LABEL = new Rect(95, 181, 162, 8);

    private RepairerLayout() {
    }

    /** Um retângulo relativo ao canto da tela. */
    record Rect(int x, int y, int w, int h) {
        boolean contains(int left, int top, double mouseX, double mouseY) {
            return mouseX >= left + x && mouseX < left + x + w && mouseY >= top + y && mouseY < top + y + h;
        }
    }
}
