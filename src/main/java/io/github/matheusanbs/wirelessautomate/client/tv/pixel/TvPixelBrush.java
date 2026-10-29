package io.github.matheusanbs.wirelessautomate.client.tv.pixel;

import java.util.Arrays;

/**
 * O pincel do editor de pixel art: de {@link #MIN} a {@link #MAX} px, quadrado ou redondo (redondo a partir de
 * {@link #ROUND_FROM} px; abaixo disso é igual ao quadrado). Os deslocamentos de cada tamanho e forma são calculados
 * uma vez, na carga da classe. O pixel do mouse fica no centro do pincel; num tamanho par, o centro cai meio pixel
 * acima e à esquerda, então o pincel cresce mais para a direita e para baixo. Lógica pura, com JUnit.
 */
public final class TvPixelBrush {
    public static final int MIN = 1;
    public static final int MAX = 8;
    public static final int ROUND_FROM = 3;

    private static final int[][] SQUARE = new int[MAX + 1][];
    private static final int[][] ROUND = new int[MAX + 1][];

    static {
        for (int size = MIN; size <= MAX; size++) {
            SQUARE[size] = build(size, false);
            ROUND[size] = build(size, size >= ROUND_FROM);
        }
    }

    private TvPixelBrush() {
    }

    /**
     * Um pixel ({@code dx}, {@code dy}) entra no pincel redondo quando a distância ao centro, ao quadrado, não passa
     * de (lado / 2)² − 0,5: o meio pixel tira os cantos do de 3 px (que vira uma cruz) e arredonda os maiores.
     */
    private static int[] build(int size, boolean round) {
        double center = (size - 1) / 2.0;
        double limit = size * size / 4.0 - 0.5;
        int shift = (size - 1) / 2;
        int[] out = new int[size * size * 2];
        int n = 0;
        for (int dy = 0; dy < size; dy++) {
            for (int dx = 0; dx < size; dx++) {
                if (round && (dx - center) * (dx - center) + (dy - center) * (dy - center) > limit) {
                    continue;
                }
                out[n++] = dx - shift;
                out[n++] = dy - shift;
            }
        }
        return Arrays.copyOf(out, n);
    }

    /** Os deslocamentos do pincel, em pares (dx, dy); o array é compartilhado e ninguém o muda. */
    public static int[] offsets(int size, boolean round) {
        int clamped = Math.max(MIN, Math.min(MAX, size));
        return round ? ROUND[clamped] : SQUARE[clamped];
    }
}
