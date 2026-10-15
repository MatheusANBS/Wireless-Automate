package io.github.matheusanbs.wirelessautomate.resonator;

/** A sintonia do Ressonador de Sinal: eficiência pelo desvio, deriva da estação e o dial. Lógica pura. */
public final class Tuning {
    public static final double MIN_MHZ = 88.0;
    public static final double MAX_MHZ = 108.0;
    public static final double DIAL_STEP = 0.05;
    static final double PLATEAU = 0.25;
    static final double ZERO = 2.0;
    static final double MAX_VELOCITY = 0.12;
    static final double STEP_SIGMA = 0.03;
    static final double STATION_MIN = 88.5;
    static final double STATION_MAX = 107.5;

    /** Um passo da deriva: estação em MHz e velocidade em MHz por minuto. */
    public record Drift(double station, double velocity) {
    }

    private Tuning() {
    }

    /** 100% dentro do platô, 0% a partir de 2 MHz, e a curva da spec entre os dois. */
    public static double efficiency(double deviation) {
        double d = Math.abs(deviation);
        if (d <= PLATEAU) {
            return 1.0;
        }
        if (d >= ZERO) {
            return 0.0;
        }
        return 1.0 - Math.pow((d - PLATEAU) / (ZERO - PLATEAU), 1.5);
    }

    /** Um passo de 20 s: a velocidade anda pelo sorteio e a estação anda um terço da velocidade. */
    public static Drift step(double station, double velocity, double gaussian) {
        double v = clamp(velocity + gaussian * STEP_SIGMA, -MAX_VELOCITY, MAX_VELOCITY);
        return new Drift(clamp(station + v / 3, STATION_MIN, STATION_MAX), v);
    }

    public static double clampDial(double mhz) {
        double passo = Math.round(mhz / DIAL_STEP) * DIAL_STEP;
        return clamp(Math.round(passo * 100) / 100.0, MIN_MHZ, MAX_MHZ);
    }

    /** A estação da primeira montagem, a partir de um sorteio uniforme em [0; 1]. */
    public static double initialStation(double unit01) {
        return STATION_MIN + clamp(unit01, 0, 1) * (STATION_MAX - STATION_MIN);
    }

    private static double clamp(double v, double min, double max) {
        return Math.max(min, Math.min(max, v));
    }
}
