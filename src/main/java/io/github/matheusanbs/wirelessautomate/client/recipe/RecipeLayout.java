package io.github.matheusanbs.wirelessautomate.client.recipe;

/** As medidas fixas do editor de receitas (tamanho fixo), em coordenadas do painel. */
final class RecipeLayout {
    static final int W = 356;
    static final int H = 276;
    static final int M = 6;

    // coluna da esquerda: busca, lista e recarga
    static final int LIST_X = M;
    static final int LIST_W = 134;
    static final int SEARCH_Y = 22;
    static final int LIST_Y = 37;
    static final int ROW_H = 20;
    static final int ROWS = 5;
    static final int LIST_H = ROWS * ROW_H + 4;
    static final int PENDING_Y = LIST_Y + LIST_H + 10;

    // coluna da direita: a receita escolhida
    static final int X0 = 148;
    static final int RW = W - M - X0;
    static final int GRID_X = X0 + 4;
    static final int GRID_Y = 64;
    static final int ARROW_X = X0 + 66;
    static final int ARROW_Y = GRID_Y + 20;
    static final int OUT_X = X0 + 92;
    static final int OUT_Y = GRID_Y + 14;
    static final int OUT_SIZE = 26;
    static final int STEP_X = X0 + 124;
    static final int INSPECT_Y = 122;
    static final int INSPECT_H = 34;
    static final int ACTIONS_Y = 162;

    private RecipeLayout() {
    }
}
