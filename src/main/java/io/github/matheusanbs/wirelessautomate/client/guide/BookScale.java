package io.github.matheusanbs.wirelessautomate.client.guide;

/**
 * A escala do livro na janela, sem a escala de interface do jogo: cada pixel da textura vale {@code k} pixels de tela,
 * com {@code k} o maior par com que o livro e as fitas cabem com margem (mínimo 2), e o livro fica centrado. As contas
 * do mouse (coordenadas de interface) para pixels da textura e unidades de folha saem daqui. Sem Minecraft: a tela
 * monta uma no {@code init} e a usa até o próximo redimensionamento.
 */
public final class BookScale {
    /** A textura do livro, em pixels. */
    public static final int TEX_W = 300;
    public static final int TEX_H = 196;
    /** Onde as fitas começam (sobre a borda da capa) e a largura do livro com a fita mais comprida. */
    public static final int RIBBON_X = 293;
    public static final int TOTAL_W = RIBBON_X + 22;
    /** A área de conteúdo de cada folha, em pixels da textura. */
    public static final int LEFT_X = 23;
    public static final int RIGHT_X = 161;
    public static final int CONTENT_Y = 24;
    /** Margem mínima, em pixels de tela, entre o livro e a borda da janela. */
    public static final int MARGIN = 16;

    private final int k;
    private final int ox;
    private final int oy;
    private final double guiScale;

    private BookScale(int k, int ox, int oy, double guiScale) {
        this.k = k;
        this.ox = ox;
        this.oy = oy;
        this.guiScale = guiScale;
    }

    /** A escala para uma janela de {@code winW × winH} pixels com a escala de interface {@code guiScale}. */
    public static BookScale of(int winW, int winH, double guiScale) {
        int k = pixelsPerTexel(winW, winH);
        return new BookScale(k, (winW - TOTAL_W * k) / 2, (winH - TEX_H * k) / 2, guiScale);
    }

    /** O maior {@code k} par com que o livro cabe na janela com a margem; nunca menos de 2. */
    public static int pixelsPerTexel(int winW, int winH) {
        int k = 2;
        while (fits(k + 2, winW, winH)) {
            k += 2;
        }
        return k;
    }

    private static boolean fits(int k, int winW, int winH) {
        return TOTAL_W * k + 2 * MARGIN <= winW && TEX_H * k + 2 * MARGIN <= winH;
    }

    public int k() {
        return k;
    }

    /** O canto do livro em coordenadas de interface (o que a {@code PoseStack} da tela recebe). */
    public float originGuiX() {
        return (float) (ox / guiScale);
    }

    public float originGuiY() {
        return (float) (oy / guiScale);
    }

    /** Quantas unidades de interface vale um pixel da textura. */
    public float guiPerTexel() {
        return (float) (k / guiScale);
    }

    /** Coordenada de interface para pixels da textura. */
    public double texX(double guiX) {
        return (guiX * guiScale - ox) / k;
    }

    public double texY(double guiY) {
        return (guiY * guiScale - oy) / k;
    }

    /** Pixels da textura para coordenada de interface (para a dica de um item da fita, por exemplo). */
    public double guiX(double texX) {
        return (texX * k + ox) / guiScale;
    }

    public double guiY(double texY) {
        return (texY * k + oy) / guiScale;
    }

    /** Unidade de folha a partir de um pixel da textura: 2 por pixel, a partir do canto da área de conteúdo. */
    public static double unit(double tex, int origin) {
        return (tex - origin) * 2;
    }
}
