package io.github.matheusanbs.wirelessautomate.client.tv.editor;

/**
 * As contas do zoom da miniatura: os níveis (de "caber", o 1, até 8 vezes) e onde a imagem fica depois de um zoom
 * centrado no mouse ou de um arrasto, sempre dentro da área. Lógica pura, com JUnit.
 */
final class TvZoom {
    /** As escalas de cada nível, vezes a escala em que a imagem cabe na área; o nível 0 é "caber". */
    static final int[] STEPS = {1, 2, 3, 4, 6, 8};

    private TvZoom() {
    }

    /** O nível depois de {@code dir} passos (positivo aproxima), preso entre "caber" e o máximo. */
    static int step(int level, int dir) {
        return Math.max(0, Math.min(STEPS.length - 1, level + dir));
    }

    /**
     * Onde a imagem começa, no eixo, para o ponto da imagem que estava sob {@code anchor} continuar sob ele depois de
     * a escala ir de {@code oldScale} para {@code newScale}.
     */
    static int anchored(int anchor, int oldOrigin, float oldScale, float newScale) {
        return Math.round(anchor - (anchor - oldOrigin) * newScale / oldScale);
    }

    /**
     * Prende o começo da imagem à área: a imagem menor que a área fica centrada; a maior não deixa sobrar vazio nas
     * pontas.
     */
    static int clamp(int origin, int image, int areaStart, int areaSize) {
        if (image <= areaSize) {
            return areaStart + (areaSize - image) / 2;
        }
        return Math.max(areaStart + areaSize - image, Math.min(areaStart, origin));
    }
}
