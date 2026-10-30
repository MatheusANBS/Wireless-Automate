package io.github.matheusanbs.wirelessautomate.client;

/**
 * Números curtos para a vazão na tela: até 999 inteiros, depois com sufixo (k, M, G, T, P, E) e uma
 * casa decimal abaixo de 100 ({@code 1240 → "1.2k"}, {@code 123456 → "123k"}). Lógica pura, sem
 * classes do Minecraft.
 *
 * <p>{@link #abbreviate} arredonda e serve à vazão. {@link #abbreviateAmount} trunca e serve à
 * quantidade guardada e à capacidade: o texto nunca mostra mais do que existe.
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

    /**
     * Como {@link #abbreviate}, mas sempre para baixo (trunca o valor absoluto), de modo que o texto
     * nunca passa da quantidade real ({@code 2664 → "2.6k"}, {@code 99999 → "99.9k"}). Só aritmética
     * inteira, sem a imprecisão do double.
     */
    public static String abbreviateAmount(long value) {
        String sign = value < 0 ? "-" : "";
        // Long.MIN_VALUE não tem positivo: trabalha no negativo e inverte o resto
        long abs = value < 0 ? value : -value;
        int unit = 0;
        long divisor = 1;
        while (unit < SUFFIXES.length - 1 && abs <= -1000 * divisor) {
            divisor *= 1000;
            unit++;
        }
        long whole = -(abs / divisor);
        if (unit == 0) {
            return sign + whole;
        }
        if (whole >= 100) {
            return sign + whole + SUFFIXES[unit];
        }
        long tenths = -((abs % divisor) / (divisor / 10));
        return sign + whole + (tenths == 0 ? "" : "." + tenths) + SUFFIXES[unit];
    }
}
