package io.github.matheusanbs.wirelessautomate.tv;

/** As contas do relógio do jogo, puras (com JUnit): o tempo do dia do mundo para hora e dia. */
public final class TvClock {
    private static final long DAY_TICKS = 24_000L;
    /** O tempo 0 do mundo é 06:00 no relógio do jogo. */
    private static final long MORNING_OFFSET = 6_000L;

    private TvClock() {
    }

    /** Os minutos desde a meia-noite do relógio do jogo (0 a 1439): 1000 ticks são uma hora. */
    public static int minuteOfDay(long dayTime) {
        long shifted = Math.floorMod(dayTime + MORNING_OFFSET, DAY_TICKS);
        return (int) (shifted * 60 / 1000);
    }

    /** O dia do mundo, contado de 0. */
    public static long day(long dayTime) {
        return Math.floorDiv(dayTime, DAY_TICKS);
    }
}
