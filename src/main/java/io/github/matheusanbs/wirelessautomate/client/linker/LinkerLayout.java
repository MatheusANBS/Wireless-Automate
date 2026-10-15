package io.github.matheusanbs.wirelessautomate.client.linker;

import io.github.matheusanbs.wirelessautomate.client.ThemeToggle;
import io.github.matheusanbs.wirelessautomate.network.ResourceType;
import io.github.matheusanbs.wirelessautomate.range.RemoteSide;

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
     * Quanto os botões de modo recuam para dar lugar ao botão do tema: ele começa a 15 px da borda e os
     * modos acabavam a 9, então só 10 px de recuo deixam 4 de folga (o texto do cabeçalho não cede mais que isso).
     */
    static final int HEAD_SHIFT = ThemeToggle.SIZE - 1;
    static final int SEP_Y = 26;
    static final int BODY_Y = 31;

    // coluna da esquerda: redes e tipo
    static final int LW = 144;
    static final int LIST_Y = 42;
    static final int ROW = 12;
    /** Linhas da lista no tamanho mínimo, no máximo; cada ROW de altura extra soma uma linha. */
    static final int BASE_ROWS = 6;
    static final int TYPE_H = 16;
    // coluna da direita: o corpo do modo
    static final int RX = X0 + LW + 8;
    static final int MAP_H = 104;
    /** Altura que a linha Faces toma do mapa (botões de 12 px e folga). */
    static final int FACES_H = 14;
    static final int FACES_ALL_W = 34;
    static final int FACE_W = 14;
    static final int FACE_STEP = 15;
    static final RemoteSide[] SIDES = RemoteSide.values();

    /** Caixas das abas, na ordem dos botões (Químicos só com o Mekanism, Source só com o Ars Nouveau). */
    static final ResourceType[] TABS = ResourceType.values();

    private LinkerLayout() {
    }
}
