package io.github.matheusanbs.wirelessautomate.client.resonator.render;

import io.github.matheusanbs.wirelessautomate.resonator.ChamberView;
import java.util.Arrays;

/**
 * Os gatilhos dos sons da Armilar, em lógica pura, um por Núcleo e chamado uma vez por tick do cliente (o som não pode
 * depender de a Armilar estar na tela). Cada evento sai de uma transição, com os mesmos instantes e condições da
 * animação: a trava e a saída da sintonia, o começo da janela da conjunção, o estouro, a cabeça do arco e o impacto
 * (os dois só com vazão), o grão que nasce, o clarão do fim da recarga e a câmara que racha ou fica Opaca. O primeiro
 * tick depois de a Armilar ficar ativa só toma o estado de partida (a cena recriada pelo chunk não repete o que já
 * tocou); a única exceção é a montagem, que sai se o {@code formedAt} é de até {@link #FRESH_TICKS} atrás e ainda não
 * tocou para ele. Nenhum evento passa de {@link #MAX_PER_SECOND} por segundo, nem sai duas vezes no mesmo tick.
 *
 * <p>Também guarda o que os loops seguem: a eficiência e a vazão suavizadas, a ignição da montagem e o buffer cheio.
 */
final class ArmilarSoundTriggers {
    /** Os eventos pontuais; os loops (sol, feixe, buffer cheio) são do {@link ArmilarSoundBox}. */
    enum Event {
        ASSEMBLE, TUNE_LOCK, TUNE_LOST, CONJ_CHARGE, CONJ_BURST, CONJ_ARC, CONJ_IMPACT, GRAIN, DONE, CRACK, DULL
    }

    /** Quem toca: {@code chamber} é a câmara do evento (grão, fim da recarga, rachadura, Opaco), ou -1. */
    interface Sink {
        void play(Event event, int chamber);
    }

    static final int MAX_PER_SECOND = 3;
    private static final int WINDOW_TICKS = 20;
    /** A montagem só toca se o {@code formedAt} é de até 3 s atrás: depois disso o jogador chegou a uma Armilar já montada. */
    static final long FRESH_TICKS = 60;
    static final long NEVER = Long.MIN_VALUE / 2;
    /** O maior salto de ticks entre duas chamadas em que a conjunção ainda vale: o instante que caiu no salto toca, o que passou de longe não. */
    static final long MAX_JUMP_TICKS = 3;
    /** Os {@code d} da conjunção em que cada evento sai (o {@link ArmilarPhase}): o começo da preparação, o pico do clarão, a cabeça que parte e a Porta. */
    static final double CHARGE_D = -0.6;
    static final double BURST_D = 0.15;
    static final double ARC_D = 0.1;
    static final double IMPACT_D = 0.6;
    /** A faixa do tom do loop do feixe: pequena, para a vazão soar sem virar sirene. */
    static final double PITCH_MIN = 0.9;
    static final double PITCH_MAX = 1.15;
    private static final double TICK_SECONDS = 0.05;
    private static final int BAND_MAX = 7;
    private static final byte DULL_FROM_MAX = 2;
    private static final byte DULL_MIN = 3;
    private static final byte DULL_MAX = 4;
    private static final byte CRACKED_MIN = 5;

    private final long[][] stamps = new long[Event.values().length][MAX_PER_SECOND];
    private final int[] head = new int[Event.values().length];
    private final long[] lastTick = new long[Event.values().length];
    private long assembledFor;
    private boolean primed;
    private long lastGameTime;
    private ChamberView view = ChamberView.EMPTY;
    private byte[] chambers = new byte[0];
    private ArmilarGrains grains = new ArmilarGrains();
    private FlashTracker flash = new FlashTracker();
    private boolean tuned;
    private boolean full;
    private byte flowTarget;
    private double flowNow;
    private double u;
    private double ignition;
    private double prevD;

    /** {@code assembledFor} é o {@code formedAt} da última montagem que tocou para este Núcleo, ou {@link #NEVER}. */
    ArmilarSoundTriggers(long assembledFor) {
        this.assembledFor = assembledFor;
        for (long[] row : stamps) {
            Arrays.fill(row, NEVER);
        }
        Arrays.fill(lastTick, NEVER);
    }

    /** O {@code formedAt} da última montagem que tocou, ou {@link #NEVER}. */
    long assembledFor() {
        return assembledFor;
    }

    /** Se a Armilar está ativa (montada e com câmaras): falsa antes do primeiro {@link #tick} ativo e depois de um inativo. */
    boolean active() {
        return primed;
    }

    /**
     * Um tick do cliente. {@code active} é se a estrutura está montada e a view tem câmaras; sem ela o estado volta ao
     * de partida e o próximo tick ativo é um primeiro tick de novo. {@code clock} é o relógio da Armilar em segundos
     * (o do mundo, ou o parado do e2e). Chamar duas vezes no mesmo {@code gameTime} não faz nada.
     */
    void tick(ChamberView view, boolean active, long gameTime, double clock, Sink sink) {
        if (!active) {
            if (primed) {
                reset();
            }
            return;
        }
        if (primed && gameTime == lastGameTime) {
            return;
        }
        boolean quiet = !primed;
        long elapsed = quiet ? 1 : Math.max(1, gameTime - lastGameTime);
        boolean viewChanged = view != this.view;
        byte[] before = chambers;
        if (viewChanged) {
            this.view = view;
            chambers = view.chambers();
            byte[] flows = view.flows();
            flowTarget = flows.length > 0 ? flows[0] : 0;
            full = view.full();
            flash.onView(view, chambers, gameTime);
        }
        smooth(quiet, elapsed);
        ignition = ignition(view.formedAt(), gameTime);
        double d = ArmilarSpec.tau(clock);
        if (viewChanged) {
            assemble(view.formedAt(), gameTime, sink);
        }
        if (!quiet) {
            if (view.tuned() != tuned) {
                emit(view.tuned() ? Event.TUNE_LOCK : Event.TUNE_LOST, -1, gameTime, sink);
            }
            if (viewChanged) {
                chamberEvents(before, chambers, gameTime, sink);
            }
            conjunction(view.tuned(), d, elapsed, gameTime, sink);
        }
        int target = ArmilarRecharge.firstRecharging(chambers, view);
        if (grains.tick(target, chambers.length) && !quiet) {
            emit(Event.GRAIN, target, gameTime, sink);
        }
        flash.tick(view, chambers, gameTime);
        if (!quiet) {
            for (int i = 0; i < chambers.length; i++) {
                if (flash.flash(i, gameTime, 0f) == 0f) {
                    emit(Event.DONE, i, gameTime, sink);
                }
            }
        }
        tuned = view.tuned();
        prevD = d;
        lastGameTime = gameTime;
        primed = true;
    }

    private void reset() {
        primed = false;
        view = ChamberView.EMPTY;
        chambers = new byte[0];
        grains = new ArmilarGrains();
        flash = new FlashTracker();
        flowNow = 0;
        u = 0;
        ignition = 0;
        tuned = false;
        full = false;
        flowTarget = 0;
    }

    /** A eficiência e a vazão suavizadas, como as da cena: a vazão anda {@link ArmilarFlow#FLOW_RATE} por tick e o {@code u} {@link ArmilarMotion#RATE} por segundo. */
    private void smooth(boolean first, long elapsed) {
        double targetU = view.tuned() ? 1 : view.band() / (double) BAND_MAX;
        if (first) {
            u = targetU;
            flowNow = flowTarget;
            return;
        }
        u = targetU + (u - targetU) * Math.pow(1 - Math.min(1, ArmilarMotion.RATE * TICK_SECONDS), elapsed);
        flowNow = ArmilarFlow.approach(flowNow, flowTarget, ArmilarFlow.FLOW_RATE, elapsed);
    }

    /** A ignição da montagem no tick: 1 numa Armilar já montada ({@code formedAt <= 0}). */
    static double ignition(long formedAt, long gameTime) {
        double since = formedAt > 0 ? Math.max(0, (gameTime - formedAt) * TICK_SECONDS) : 0;
        return ArmilarAssembly.ignition(ArmilarAssembly.a(since, formedAt <= 0));
    }

    private void assemble(long formedAt, long gameTime, Sink sink) {
        long age = gameTime - formedAt;
        if (formedAt > 0 && formedAt != assembledFor && age >= 0 && age < FRESH_TICKS) {
            assembledFor = formedAt;
            emit(Event.ASSEMBLE, -1, gameTime, sink);
        }
    }

    /** A rachadura (de qualquer câmara viva ou Opaca) e o Opaco (de uma câmara cheia): as câmaras só mudam com a view. */
    private void chamberEvents(byte[] before, byte[] now, long gameTime, Sink sink) {
        if (before.length != now.length) {
            return;
        }
        for (int i = 0; i < now.length; i++) {
            byte was = before[i];
            byte is = now[i];
            if (is >= CRACKED_MIN && was > 0 && was < CRACKED_MIN) {
                emit(Event.CRACK, i, gameTime, sink);
            } else if (is >= DULL_MIN && is <= DULL_MAX && was > 0 && was <= DULL_FROM_MAX) {
                emit(Event.DULL, i, gameTime, sink);
            }
        }
    }

    /**
     * Os instantes da conjunção, só em sintonia e só entre duas chamadas a até {@link #MAX_JUMP_TICKS} ticks (o instante dentro do salto toca uma vez; salto maior recomeça o laço): a
     * carga ao começar a preparação, o estouro, e com vazão a cabeça do arco e o impacto na Porta.
     */
    private void conjunction(boolean tunedNow, double d, long elapsed, long gameTime, Sink sink) {
        if (!tunedNow || elapsed > MAX_JUMP_TICKS) {
            return;
        }
        if (crossed(CHARGE_D, d)) {
            emit(Event.CONJ_CHARGE, -1, gameTime, sink);
        }
        if (crossed(BURST_D, d)) {
            emit(Event.CONJ_BURST, -1, gameTime, sink);
        }
        if (ArmilarFlow.strength(flowNow) > 0) {
            if (crossed(ARC_D, d)) {
                emit(Event.CONJ_ARC, -1, gameTime, sink);
            }
            if (crossed(IMPACT_D, d)) {
                emit(Event.CONJ_IMPACT, -1, gameTime, sink);
            }
        }
    }

    private boolean crossed(double at, double d) {
        return prevD < at && at <= d;
    }

    private void emit(Event event, int chamber, long gameTime, Sink sink) {
        if (allow(event, gameTime)) {
            sink.play(event, chamber);
        }
    }

    /** Se o evento pode tocar agora: no máximo {@link #MAX_PER_SECOND} nos últimos 20 ticks e uma vez por tick. */
    private boolean allow(Event event, long gameTime) {
        int e = event.ordinal();
        if (lastTick[e] == gameTime || gameTime - stamps[e][head[e]] < WINDOW_TICKS) {
            return false;
        }
        stamps[e][head[e]] = gameTime;
        head[e] = (head[e] + 1) % MAX_PER_SECOND;
        lastTick[e] = gameTime;
        return true;
    }

    /** O volume do loop do sol: a ignição da montagem vezes a eficiência (0 sem Armilar ativa). */
    double sunLevel() {
        return primed ? ignition * ArmilarFlow.sunMul(u) : 0;
    }

    /** A força da vazão suavizada, de 0 a 1 (a mesma do feixe). */
    double flowStrength() {
        return primed ? ArmilarFlow.strength(flowNow) : 0;
    }

    /** O volume do loop do feixe: some junto com a força, de cerca de 0,4 na faixa 1 a 1 na 7, pela ignição. */
    double flowLevel() {
        double k = flowStrength();
        return ArmilarFlow.presence(k) * (0.4 + 0.6 * k) * ignition;
    }

    /** O tom do loop do feixe, de {@link #PITCH_MIN} a {@link #PITCH_MAX} com a força. */
    double flowPitch() {
        return PITCH_MIN + (PITCH_MAX - PITCH_MIN) * flowStrength();
    }

    /** Se o buffer está cheio: o loop da pressão fica baixo enquanto isso. */
    boolean full() {
        return primed && full;
    }
}
