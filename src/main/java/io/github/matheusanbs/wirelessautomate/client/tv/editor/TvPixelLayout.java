package io.github.matheusanbs.wirelessautomate.client.tv.editor;

/**
 * As áreas do editor de pixel art numa janela de {@code w} × {@code h} com o canto em ({@code x}, {@code y}), a
 * mesma do editor da Tela (no mínimo {@link TvEditorLayout#MIN_W} × {@link TvEditorLayout#MIN_H}): o cabeçalho com o
 * título, o nome e o tamanho (até antes do botão do tema); as ferramentas em duas colunas à esquerda, com o Limpar
 * tudo no pé delas (um botão de texto curto: ali sobra altura até na janela mínima, e a prévia da coluna da direita
 * fica com ela); a coluna da paleta, da cor, da prévia e de Cancelar e Salvar à direita; e no centro o quadro, com as
 * duas linhas do rodapé embaixo. Toda a sobra vai para o quadro. Pura, com JUnit; refeita só quando a janela muda.
 */
record TvPixelLayout(TvRect header, TvRect tools, TvRect board, TvRect footer, TvRect status, TvRect side,
        TvRect clear) {
    static final int PAD = TvEditorLayout.PAD;
    static final int GAP = TvEditorLayout.GAP;
    /** Um botão de ferramenta: o ícone de 16 px com 1 px de borda. */
    static final int BUTTON = TvIcons.SIZE + 2;
    static final int TOOLS_W = 2 * BUTTON + 2;
    static final int SIDE_W = TvEditorLayout.RIGHT_W;
    static final int FOOTER_H = TvIcons.SIZE;
    static final int STATUS_H = 10;
    static final int CLEAR_H = 14;
    /** O botão do tema do cabeçalho (o {@code ThemeToggle}, 11 px) e a margem dele até a borda. */
    private static final int THEME_ROOM = 8 + 11 + 6;

    static TvPixelLayout of(int x, int y, int w, int h) {
        int top = y + TvEditorLayout.HEADER;
        int bottom = y + h - PAD;
        TvRect header = new TvRect(x + 8, y + 5, Math.max(0, w - 8 - THEME_ROOM), 12);
        TvRect clear = new TvRect(x + PAD, bottom - CLEAR_H, TOOLS_W, CLEAR_H);
        TvRect tools = new TvRect(x + PAD, top, TOOLS_W, Math.max(0, clear.y() - 3 - top));
        TvRect side = new TvRect(x + w - PAD - SIDE_W, top, SIDE_W, bottom - top);
        int centerX = tools.right() + GAP;
        int centerW = Math.max(0, side.x() - GAP - centerX);
        TvRect status = new TvRect(centerX, bottom - STATUS_H, centerW, STATUS_H);
        TvRect footer = new TvRect(centerX, status.y() - 2 - FOOTER_H, centerW, FOOTER_H);
        TvRect board = new TvRect(centerX, top, centerW, Math.max(0, footer.y() - 3 - top));
        return new TvPixelLayout(header, tools, board, footer, status, side, clear);
    }
}
