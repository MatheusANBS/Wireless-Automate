package io.github.matheusanbs.wirelessautomate.dock;

/**
 * O "trabalhando" da Doca no cliente, sem classes do Minecraft: o relógio do laço da animação e a rampa da velocidade,
 * a partir dos eventos de trabalho que o servidor manda ({@link PatternDockBlockEntity#EVENT_WORK}, no máximo um a
 * cada {@link #EVENT_INTERVAL} ticks por Doca, com o lado).
 *
 * <p>Trabalha enquanto o último evento tem menos de {@link #WORKING_TICKS} ticks, a mesma regra do "trabalhando" da
 * tela ({@link DockStatus#WORKING_TICKS}). O laço da animação dura {@link #LOOP_TICKS} (6 s) e é o mesmo para todas as
 * Docas (fase pelo tempo do mundo); um laço é de trabalho quando começa trabalhando, então o pacote só entra no começo
 * de um laço e o laço que começou trabalhando termina inteiro.
 *
 * <p>A velocidade ({@link #speed}, 0 no repouso e 1 no trabalho) sobe e desce em rampa de {@link #RAMP_TICKS} (1 s).
 * O renderer gira cada peça por {@code repouso × t + (trabalho − repouso) × extra(t)}, e o {@link #extra} é a integral
 * da velocidade: contínua, então a rampa muda a velocidade sem pulo de ângulo. Cada período de trabalho (do evento que o
 * começa até a rampa de descida acabar) entra na integral pela fórmula fechada; ao começar um novo, a integral do
 * anterior vai para {@code extraBase}. Um evento que chega no meio da descida começa o novo período da velocidade em que
 * ela estava. Os ticks são os do relógio do cliente ({@code Level.getGameTime}); nada é salvo.
 */
public final class DockWork {
    /** O intervalo mínimo entre dois eventos de trabalho da mesma Doca, no servidor. */
    public static final int EVENT_INTERVAL = 100;
    /** Quanto tempo a Doca trabalha depois do último evento. */
    public static final long WORKING_TICKS = DockStatus.WORKING_TICKS;
    /** O laço da animação (6 s). */
    public static final int LOOP_TICKS = 120;
    /** A rampa da velocidade (1 s), na subida e na descida. */
    public static final int RAMP_TICKS = 20;
    /** Sem evento ainda. */
    public static final long NEVER = Long.MIN_VALUE;

    private long lastEvent = NEVER;
    private int side;
    private int previousSide;
    /** O começo do período de trabalho atual e a velocidade nele. */
    private long periodStart = NEVER;
    private double startSpeed;
    /** A integral da velocidade dos períodos anteriores ao atual. */
    private double extraBase;

    /** O servidor manda um evento: o último foi há {@link #EVENT_INTERVAL} ticks ou mais (ou nunca). */
    public static boolean shouldSend(long now, long lastSent) {
        return lastSent == NEVER || now - lastSent >= EVENT_INTERVAL;
    }

    /** Chegou um evento de trabalho no tick {@code tick}, do lado {@code side} ({@code Direction.ordinal()}). */
    public void event(long tick, int side) {
        previousSide = lastEvent == NEVER ? side : this.side;
        this.side = side;
        if (periodStart == NEVER || tick >= end()) {
            double now = speed(tick);
            if (periodStart != NEVER) {
                extraBase += periodIntegral(tick);
            }
            periodStart = tick;
            startSpeed = now;
        }
        lastEvent = tick;
    }

    /** O tick do último evento, ou {@link #NEVER}. */
    public long lastEvent() {
        return lastEvent;
    }

    /** Trabalha no tick {@code tick}: o último evento tem menos de {@link #WORKING_TICKS} ticks. */
    public boolean working(long tick) {
        return lastEvent != NEVER && tick >= lastEvent && tick - lastEvent < WORKING_TICKS;
    }

    /** A fase do laço da animação no tempo {@code t} (ticks com a fração do quadro), de 0 a 1. */
    public static double loopPhase(double t) {
        double phase = (t % LOOP_TICKS) / LOOP_TICKS;
        return phase < 0 ? phase + 1 : phase;
    }

    /** O tick em que começou o laço que está tocando no tempo {@code t}. */
    public static long loopStart(double t) {
        return (long) Math.floor(t / LOOP_TICKS) * LOOP_TICKS;
    }

    /** O laço que toca no tempo {@code t} é de trabalho: ele começou trabalhando. */
    public boolean workLoop(double t) {
        return working(loopStart(t));
    }

    /** O lado do pacote no laço do tempo {@code t}: o do último evento que chegou até o começo do laço. */
    public int loopSide(double t) {
        return lastEvent <= loopStart(t) ? side : previousSide;
    }

    /** O lado do último evento. */
    public int side() {
        return side;
    }

    /** A velocidade no tempo {@code t}: 0 no repouso, 1 no trabalho, em rampa entre os dois. */
    public double speed(double t) {
        if (periodStart == NEVER || t <= periodStart) {
            return periodStart == NEVER ? 0 : startSpeed;
        }
        double end = end();
        if (t < end) {
            return Math.min(1, startSpeed + (t - periodStart) / RAMP_TICKS);
        }
        return Math.max(0, 1 - (t - end) / RAMP_TICKS);
    }

    /** A integral da velocidade até o tempo {@code t}, em ticks: o tempo a mais que as peças andaram no ritmo do trabalho. */
    public double extra(double t) {
        return extraBase + (periodStart == NEVER ? 0 : periodIntegral(t));
    }

    /** O fim do trabalho do período atual (o começo da rampa de descida). */
    private long end() {
        return lastEvent + WORKING_TICKS;
    }

    /** A integral da velocidade do período atual, do começo dele até {@code t}. */
    private double periodIntegral(double t) {
        if (t <= periodStart) {
            return 0;
        }
        double end = end();
        double rising = Math.min(t, end) - periodStart;
        double toFull = (1 - startSpeed) * RAMP_TICKS;
        double sum = rising <= toFull
                ? startSpeed * rising + rising * rising / (2.0 * RAMP_TICKS)
                : startSpeed * toFull + toFull * toFull / (2.0 * RAMP_TICKS) + (rising - toFull);
        if (t > end) {
            double falling = Math.min(t - end, RAMP_TICKS);
            sum += falling - falling * falling / (2.0 * RAMP_TICKS);
        }
        return sum;
    }
}
