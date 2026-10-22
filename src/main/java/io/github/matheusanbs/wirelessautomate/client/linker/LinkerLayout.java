package io.github.matheusanbs.wirelessautomate.client.linker;

import io.github.matheusanbs.wirelessautomate.client.ThemeToggle;

/** As medidas fixas da tela do Vinculador, em coordenadas do painel. */
final class LinkerLayout {
    /** Tamanho mínimo e margem da janela no máximo. */
    static final int MIN_W = 300;
    static final int MIN_H = 204;
    static final int WINDOW_MARGIN = 8;
    static final int GRIP = 7;
    static final int EDGE = 3;
    static final int X0 = 9;
    static final int HEAD_Y = 8;
    /**
     * Quanto os botões do cabeçalho recuam para dar lugar ao botão do tema: ele começa a 15 px da borda e os
     * botões acabavam a 9, então só 10 px de recuo deixam 4 de folga (o texto do cabeçalho não cede mais que isso).
     */
    static final int HEAD_SHIFT = ThemeToggle.SIZE - 1;
    static final int SEP_Y = 26;
    static final int BODY_Y = 31;

    // coluna da esquerda: o roteador escolhido e a ajuda
    static final int LW = 144;
    // coluna da direita: a prévia da área e o botão
    static final int RX = X0 + LW + 8;
    static final int MAP_H = 96;

    private LinkerLayout() {
    }
}
