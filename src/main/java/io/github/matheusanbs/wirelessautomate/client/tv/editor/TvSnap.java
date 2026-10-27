package io.github.matheusanbs.wirelessautomate.client.tv.editor;

/**
 * O encaixe do editor, em pixels de desenho da Tela: a grade de {@link #GRID} px, ou 1 px com o Alt segurado, e os
 * limites da área. Puro, com JUnit.
 */
public final class TvSnap {
    /** A grade: 8 casas por bloco de 32 px. */
    public static final int GRID = 4;
    /** O menor lado que a alça deixa. */
    public static final int MIN_SIZE = 4;

    private TvSnap() {
    }

    public static int step(boolean fine) {
        return fine ? 1 : GRID;
    }

    /** Um deslocamento do mouse ({@code raw} px de desenho) arredondado para a grade, ou para 1 px com {@code fine}. */
    public static int delta(double raw, boolean fine) {
        int step = step(fine);
        return (int) Math.round(raw / step) * step;
    }

    /**
     * O deslocamento {@code d} limitado para uma caixa que vai de {@code min} a {@code max} continuar dentro de
     * {@code 0..area}.
     */
    public static int moveDelta(int d, int min, int max, int area) {
        return Math.max(-min, Math.min(area - max, d));
    }

    /**
     * O lado novo de uma caixa que começa em {@code start}: o {@code original} mais {@code d}, no mínimo
     * {@link #MIN_SIZE} (ou o que sobra até a borda, se for menos) e sem passar da área.
     */
    public static int size(int original, int d, int start, int area) {
        int room = Math.max(1, area - start);
        return Math.max(Math.min(MIN_SIZE, room), Math.min(room, original + d));
    }

    /** O valor arredondado para baixo na grade. */
    public static int floor(int value) {
        return Math.floorDiv(value, GRID) * GRID;
    }

    /** O valor arredondado para cima na grade. */
    public static int ceil(int value) {
        return Math.floorDiv(value + GRID - 1, GRID) * GRID;
    }
}
