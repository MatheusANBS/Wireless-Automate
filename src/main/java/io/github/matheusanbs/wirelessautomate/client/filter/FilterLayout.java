package io.github.matheusanbs.wirelessautomate.client.filter;

/**
 * As medidas fixas da tela de filtro: o tamanho mínimo e máximo, a moldura, a lista, o painel da direita e
 * as linhas das abas Tags e Regra. As que dependem do tamanho atual ficam na {@link FilterScreen}.
 */
final class FilterLayout {
    // tamanho: mínimo e máximo (o padrão, lembrado na sessão, fica na tela)
    static final int MIN_W = 344;
    static final int MIN_H = 252;
    static final int MAX_W = 560;
    static final int MAX_H = 450;
    // moldura
    static final int X0 = 8;
    static final int PANEL_W = 160;
    static final int GAP = 6;
    static final int PAD = 5;
    static final int IW = PANEL_W - 2 * PAD;
    static final int HEAD_Y = 5;
    static final int ROW2_Y = 21;
    static final int SEG_H = 13;
    static final int LABEL_Y = 39;
    static final int SEARCH_Y = 48;
    static final int FIELD_H = 12;
    static final int LIST_Y = 63;
    static final int ROW = 18;
    static final int TABS_Y = 37;
    static final int TAB_H = 13;
    static final int BOX_Y = 50;
    static final int INV_H = 76;
    static final int GRIP = 7;
    static final int EDGE = 3;
    static final int BTN_H = 14;
    // aba Tags
    static final int INSPECT_Y = BOX_Y + 6;
    static final int TAG_SEARCH_Y = BOX_Y + 29;
    static final int CAND_Y = BOX_Y + 45;
    static final int CAND_ROW = 11;
    static final int PREVIEW_H = 30;
    // aba Regra
    static final int COND_Y = BOX_Y + 17;
    static final int COND_ROW = 11;
    static final int TRI_W = 20;
    /** Campo do nível mínimo (3 dígitos), à direita do encantamento. */
    static final int LEVEL_W = 28;
    /** Lista de sugestões do encantamento: até 5 linhas de 12 px e a linha "+N". */
    static final int SUGGEST_ROWS = 5;
    static final int SUGGEST_ROW = 12;
    static final int SUGGEST_MORE_H = 11;

    private FilterLayout() {
    }

    static int thumbHeight(int h, int visible, int total) {
        return Math.max(8, h * visible / Math.max(visible, total));
    }
}
