package io.github.matheusanbs.wirelessautomate.client;

/**
 * Números curtos para a vazão na tela: até 999 inteiros, depois com sufixo (k, M, G, T, P, E) e uma
 * casa decimal abaixo de 100 ({@code 1240 → "1.2k"}, {@code 123456 → "123k"}). Lógica pura, sem
 * classes do Minecraft.
 */
public final class RateFormat {
    private static final String[] SUFFIXES = {"", "k", "M", "G", "T", "P", "E"};

    private RateFormat() {
    }

    public static String abbreviate(long value) {
        String sign = value < 0 ? "-" : "";
        double v = Math.abs((double) value);
        if (v < 1000) {
            return sign + (long) v;
        }
        int unit = 0;
        while (v >= 1000 && unit < SUFFIXES.length - 1) {
            v /= 1000;
            unit++;
        }
        // arredonda antes de escolher o formato: 999.96k vira 1M, e não "1000k"
        double rounded = v < 100 ? Math.round(v * 10) / 10.0 : Math.round(v);
        if (rounded >= 1000 && unit < SUFFIXES.length - 1) {
            rounded = Math.round(rounded / 100) / 10.0;
            unit++;
        }
        String number = rounded == Math.rint(rounded) ? Long.toString((long) rounded) : Double.toString(rounded);
        return sign + number + SUFFIXES[unit];
    }
}
