package io.github.matheusanbs.wirelessautomate.client.storage;

/** As medidas fixas da lista do Baú e dos Tanques; colunas e linhas da grade mudam com o tamanho. */
final class StorageListLayout {
    static final int X0 = 8;
    static final int HEAD_Y = 6;
    static final int SEARCH_Y = 21;
    static final int ROW_H = 13;
    static final int GRID_X = 14;
    static final int GRID_Y = 38;
    static final int CELL = 18;
    static final int SORT_W = 52;
    /** Menor tamanho da grade: a largura do inventário do jogador e três linhas. */
    static final int MIN_COLS = 9;
    static final int MIN_ROWS = 3;
    /** Alça de redimensionar no canto e a faixa das bordas que também redimensionam. */
    static final int GRIP = 9;
    static final int EDGE = 3;
    /** Altura fixa do painel além das linhas da grade (cabeçalho, ocupação e inventário). */
    static final int FIXED_H = GRID_Y + 4 + 18 + 82;
    /** Largura fixa além das colunas (margens e barra de rolagem). */
    static final int FIXED_W = GRID_X + 3 + 4 + 8;

    private StorageListLayout() {
    }
}
