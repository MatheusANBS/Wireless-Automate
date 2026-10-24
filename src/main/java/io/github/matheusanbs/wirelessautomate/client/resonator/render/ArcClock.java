package io.github.matheusanbs.wirelessautomate.client.resonator.render;

/**
 * O relógio dos arcos de energia da câmara, puro: quantos arcos cada faixa de vazão acende, o período e a janela das
 * descargas (o shader das fitas usa os mesmos números, no mesmo tempo em segundos, o do {@code GameTime}), as janelas
 * dos arcos ociosos da esfera e o espaçamento mínimo entre dois sons. Tudo sai do tempo e da faixa, sem estado: o
 * mesmo instante dá sempre o mesmo resultado.
 */
final class ArcClock {
    /** A fração do período de uma descarga em que ela está acesa. */
    static final double STRIKE_DUTY = 0.12;
    /** O menor intervalo entre dois sons da mesma câmara, em ticks (no máximo 2 por segundo). */
    static final int SOUND_GAP_TICKS = 10;
    private static final double IDLE_WINDOW = 4.0;
    private static final double IDLE_WINDOW_FULL = 2.5;
    private static final double IDLE_MIN = 0.15;
    private static final double IDLE_SPREAD = 0.15;
    /** Folga no fim da janela: o arco ocioso nunca passa para a janela seguinte. */
    private static final double IDLE_ROOM = 1.0;

    private ArcClock() {
    }

    /** Quantos arcos giram em volta do feixe na faixa de vazão {@code band} (0 a 7). */
    static int arcCount(int band) {
        return band >= 5 ? 3 : band == 4 ? 2 : band == 3 ? 1 : 0;
    }

    /** O período entre as descargas, em segundos, ou 0 se a faixa não tem descarga. */
    static double strikePeriod(int band) {
        return band >= 7 ? 1.0 : band == 6 ? 1.6 : band == 5 ? 2.4 : 0.0;
    }

    /** O número da descarga em {@code seconds}, ou -1 sem descarga na faixa; muda quando uma nova começa. */
    static long strikeIndex(double seconds, int band) {
        double period = strikePeriod(band);
        return period <= 0 ? -1 : (long) Math.floor(seconds / period);
    }

    /** Se a descarga está acesa em {@code seconds}. */
    static boolean striking(double seconds, int band) {
        double period = strikePeriod(band);
        if (period <= 0) {
            return false;
        }
        double phase = seconds / period;
        return phase - Math.floor(phase) < STRIKE_DUTY;
    }

    private static double idleWindowLength(boolean full) {
        return full ? IDLE_WINDOW_FULL : IDLE_WINDOW;
    }

    /** O número da janela do arco ocioso em {@code seconds}. */
    static long idleWindow(double seconds, boolean full) {
        return (long) Math.floor(seconds / idleWindowLength(full));
    }

    /** Quando, em segundos, o arco ocioso da janela {@code window} começa. */
    static double idleStart(long window, boolean full) {
        double length = idleWindowLength(full);
        return window * length + hash01(window, 1) * (length - IDLE_ROOM);
    }

    /** Quanto o arco ocioso da janela {@code window} dura, em segundos. */
    static double idleDuration(long window) {
        return IDLE_MIN + IDLE_SPREAD * hash01(window, 2);
    }

    /** De 0 a 1, o quanto o arco ocioso em curso já andou em {@code seconds}, ou -1 se não há arco agora. */
    static double idleProgress(double seconds, boolean full) {
        long window = idleWindow(seconds, full);
        double start = idleStart(window, full);
        double duration = idleDuration(window);
        double at = seconds - start;
        return at >= 0 && at < duration ? at / duration : -1;
    }

    /** O sorteio {@code k} do arco ocioso da janela de {@code seconds}, de 0 (inclusive) a 1 (exclusivo). */
    static double idleHash(double seconds, boolean full, int k) {
        return hash01(idleWindow(seconds, full), 10 + k);
    }

    /** Se já passou tempo para um som novo: o último foi há {@link #SOUND_GAP_TICKS} ticks ou mais (ou o relógio voltou). */
    static boolean canPlay(long lastTick, long tick) {
        return tick < lastTick || tick - lastTick >= SOUND_GAP_TICKS;
    }

    /** Um sorteio determinístico de 0 (inclusive) a 1 (exclusivo) por número e chave. */
    static double hash01(long n, int k) {
        long z = n * 0x9E3779B97F4A7C15L + k * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        z ^= z >>> 31;
        return (z >>> 11) * 0x1.0p-53;
    }
}
