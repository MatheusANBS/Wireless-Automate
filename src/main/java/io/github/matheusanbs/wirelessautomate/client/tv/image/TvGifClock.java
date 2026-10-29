package io.github.matheusanbs.wirelessautomate.client.tv.image;

import java.util.Arrays;

/**
 * O relógio de um GIF animado: as durações de cada quadro e o quadro da vez num instante, pelo relógio do cliente
 * ({@code Util.getMillis}) e não por quando a Tela começou a tocar. Assim duas Telas com o mesmo GIF mostram o mesmo
 * quadro, e um GIF que saiu da vista volta no ponto certo. Lógica pura, com JUnit.
 * <p>
 * As durações seguem os navegadores: um quadro de até {@link #FAST_MS} (o atraso 0 ou 1 centésimo, que muito GIF usa
 * querendo dizer "o padrão") dura {@link #DEFAULT_MS}.
 */
public final class TvGifClock {
    /** Até quanto um atraso conta como "sem atraso". */
    static final int FAST_MS = 10;
    /** A duração de um quadro sem atraso, a dos navegadores. */
    static final int DEFAULT_MS = 100;

    /** O fim de cada quadro desde o começo do laço, em ms (o último é a duração do laço). */
    private final long[] ends;

    /** O relógio com os atrasos do arquivo, em ms; sem nenhum, um quadro só de {@link #DEFAULT_MS}. */
    public TvGifClock(int[] delays) {
        int frames = Math.max(1, delays.length);
        ends = new long[frames];
        long total = 0;
        for (int i = 0; i < frames; i++) {
            total += i < delays.length ? duration(delays[i]) : DEFAULT_MS;
            ends[i] = total;
        }
    }

    /** A duração de um quadro com o atraso {@code delay} ms. */
    static int duration(int delay) {
        return delay <= FAST_MS ? DEFAULT_MS : delay;
    }

    public int frames() {
        return ends.length;
    }

    /** A duração de um laço inteiro, em ms. */
    public long loop() {
        return ends[ends.length - 1];
    }

    /** O quadro que aparece no instante {@code nowMs}, contado do mesmo zero para todo GIF. */
    public int frameAt(long nowMs) {
        if (ends.length == 1) {
            return 0;
        }
        long t = Math.floorMod(nowMs, loop());
        int found = Arrays.binarySearch(ends, t);
        // O fim de um quadro é o começo do próximo: no instante exato do fim, já vale o seguinte.
        return found >= 0 ? found + 1 : -found - 1;
    }
}
