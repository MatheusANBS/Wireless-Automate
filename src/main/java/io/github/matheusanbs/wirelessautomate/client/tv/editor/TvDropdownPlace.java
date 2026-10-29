package io.github.matheusanbs.wirelessautomate.client.tv.editor;

/**
 * Onde a lista aberta de um {@link TvDropdown} aparece (puro, com JUnit): logo abaixo do controle, ou logo acima se
 * embaixo não cabem todas as linhas e em cima cabem; sem lugar para todas em nenhum lado, no lado com mais espaço e
 * com as linhas que couberem (a lista rola). A largura é a do controle ou a que os textos pedem, a maior, presa à
 * janela; o canto esquerdo segue o do controle, empurrado para dentro quando a lista passaria da borda.
 *
 * @param rows as linhas à vista
 * @param above se abriu para cima
 */
record TvDropdownPlace(int x, int y, int w, int h, int rows, boolean above) {
    /** O respiro de cima e de baixo, por dentro da borda. */
    static final int PAD = 2;

    static TvDropdownPlace of(TvRect anchor, TvRect bounds, int count, int rowH, int maxRows, int wantedW) {
        int want = Math.max(1, Math.min(count, maxRows));
        int below = bounds.bottom() - anchor.bottom();
        int over = anchor.y() - bounds.y();
        int full = want * rowH + 2 * PAD;
        boolean above = full > below && (full <= over || over > below);
        int room = above ? over : below;
        int rows = Math.max(1, Math.min(want, (room - 2 * PAD) / rowH));
        int h = rows * rowH + 2 * PAD;
        int y = above ? anchor.y() - h : anchor.bottom();
        y = Math.max(bounds.y(), Math.min(y, bounds.bottom() - h));
        int w = Math.min(Math.max(anchor.w(), wantedW), bounds.w());
        int x = Math.max(bounds.x(), Math.min(anchor.x(), bounds.right() - w));
        return new TvDropdownPlace(x, y, w, h, rows, above);
    }
}
