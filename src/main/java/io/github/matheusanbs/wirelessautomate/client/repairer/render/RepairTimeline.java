package io.github.matheusanbs.wirelessautomate.client.repairer.render;

/**
 * A linha do tempo de um reparo, em segundos da referência de 7 s (a do spec e do mockup aprovado); com outro
 * repairTicks as fases escalam juntas (s = 7 × fração andada). Cada envelope vai de 0 a 1 (ou diz a unidade no nome)
 * e é contínuo nas bordas das fases. Lógica pura, com JUnit.
 */
public final class RepairTimeline {
    public static final double TOTAL = 7.0;
    public static final double PULL_END = 0.5;
    public static final double BEAM_START = 1.0;
    public static final double CONVERGE = 5.5;
    public static final double FLASH = 6.3;
    /** O emissor parado e o pico da convergência, na escala em que 1 é o emissor trabalhando. */
    public static final double IDLE = 0.30;
    public static final double PEAK = 1.3;
    /** As runas enquanto há linha andando, fora da onda. */
    public static final double RUNES_WORKING = 0.35;

    private RepairTimeline() {
    }

    /** Os segundos da linha que termina em {@code end}, com {@code duration} ticks, no instante {@code now}. */
    public static double seconds(long end, int duration, double now) {
        if (duration <= 0) {
            return TOTAL;
        }
        return TOTAL * clamp(1 - (end - now) / duration);
    }

    public static double clamp(double x) {
        return x < 0 ? 0 : x > 1 ? 1 : x;
    }

    public static double smooth(double a, double b, double x) {
        double t = clamp((x - a) / (b - a));
        return t * t * (3 - 2 * t);
    }

    /** O brilho do emissor: do parado ao cheio na puxada, sobe na convergência e volta ao parado no fim. */
    public static double emitter(double s) {
        if (s < CONVERGE) {
            return IDLE + (1 - IDLE) * smooth(0, PULL_END, s);
        }
        if (s < FLASH) {
            return 1 + (PEAK - 1) * smooth(CONVERGE, FLASH, s);
        }
        return PEAK + (IDLE - PEAK) * smooth(FLASH, TOTAL, s);
    }

    /** O emissor sem linha andando, pulsando devagar; {@code t} em segundos do relógio do mundo. */
    public static double idleEmitter(double t) {
        return IDLE + 0.08 * Math.sin(t * 1.1);
    }

    /** A onda de luz pelas runas na puxada; {@code angle} é a distância da runa ao pedestal da linha (0 a π). */
    public static double runeWave(double s, double angle) {
        if (s <= 0 || s >= 0.7) {
            return 0;
        }
        double d = (angle - Math.PI * smooth(0, PULL_END, s)) / 0.45;
        return Math.exp(-d * d) * (1 - smooth(PULL_END, 0.7, s));
    }

    /** O pedestal da linha: acende no fim da puxada e apaga depois do clarão. */
    public static double pedestal(double s) {
        return s < FLASH ? smooth(0.3, PULL_END, s) : 1 - smooth(FLASH, TOTAL, s);
    }

    /** A força do feixe: acende de 1,0 a 1,3 s e corta no clarão (6,3 a 6,45 s). */
    public static double beam(double s) {
        return s < FLASH ? smooth(BEAM_START, 1.3, s) : 1 - smooth(FLASH, 6.45, s);
    }

    /** A força da convergência no feixe (0 a 7): 0 no reparo, 7 em 6,0 s. */
    public static int beamSurge(double s) {
        return (int) Math.round(7 * smooth(CONVERGE, 6.0, s));
    }

    /** A largura do feixe sobre a de base: de 1 a 1,7 na convergência. */
    public static double beamWidth(double s) {
        return 1 + 0.7 * smooth(CONVERGE, FLASH, s);
    }

    /** Quanto as rachaduras já fecharam, de dentro para fora: 0 até 1,0 s, 1 em 5,5 s. */
    public static double cracks(double s) {
        return clamp((s - BEAM_START) / (CONVERGE - BEAM_START));
    }

    /** O clarão do próprio cristal (o alfa da cor no shader do cristal): sobe na convergência, pico em 6,4 s, some em 7 s. */
    public static double crystalGlow(double s) {
        if (s < CONVERGE) {
            return 0;
        }
        if (s < FLASH) {
            return 0.6 * smooth(CONVERGE, FLASH, s);
        }
        if (s < 6.4) {
            return 0.6 + 0.4 * smooth(FLASH, 6.4, s);
        }
        return 1 - smooth(6.4, TOTAL, s);
    }

    /** O raio da onda de choque no chão em volta do pedestal, em pixels do modelo: de 0,6 a 3 (ease-out). */
    public static double shockRadius(double s) {
        double u = clamp((s - FLASH) / (TOTAL - FLASH));
        return 0.6 + 2.4 * (1 - (1 - u) * (1 - u));
    }

    public static double shockAlpha(double s) {
        if (s < FLASH || s >= TOTAL) {
            return 0;
        }
        return Math.pow(1 - (s - FLASH) / (TOTAL - FLASH), 1.5);
    }

    /** A saída do reparado: 0 até 6,7 s e 1 em 7 s (gira duas voltas, sobe 1,5 px e encolhe a 0). */
    public static double exit(double s) {
        return smooth(6.7, TOTAL, s);
    }
}
