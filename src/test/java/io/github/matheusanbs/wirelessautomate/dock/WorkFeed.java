package io.github.matheusanbs.wirelessautomate.dock;

/** Eventos de trabalho em ordem, aplicados ao {@link DockWork} quando o tempo chega neles, como o cliente os recebe. */
public final class WorkFeed {
    private final long[] ticks;
    private final int[] sides;
    private int next;

    public WorkFeed(long[] ticks, int[] sides) {
        this.ticks = ticks;
        this.sides = sides;
    }

    /** Um evento a cada {@code interval} ticks de {@code from} até {@code to}, alternando os dois lados. */
    public static WorkFeed every(long from, long interval, long to, int sideA, int sideB) {
        int n = (int) ((to - from) / interval) + 1;
        long[] ticks = new long[n];
        int[] sides = new int[n];
        for (int i = 0; i < n; i++) {
            ticks[i] = from + i * interval;
            sides[i] = i % 2 == 0 ? sideA : sideB;
        }
        return new WorkFeed(ticks, sides);
    }

    /** Aplica os eventos com tick até {@code t}. */
    public void advance(DockWork work, double t) {
        while (next < ticks.length && ticks[next] <= t) {
            work.event(ticks[next], sides[next]);
            next++;
        }
    }
}
