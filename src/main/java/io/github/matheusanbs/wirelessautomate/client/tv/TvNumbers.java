package io.github.matheusanbs.wirelessautomate.client.tv;

/**
 * O número de uma peça Valor como texto: abreviado (k, M, G, T, P) ou com os milhares agrupados, com as casas
 * pedidas, o sinal de + opcional e os separadores do idioma. Sem dado ({@code NaN}), um traço. Roda só no
 * redesenho da Tela. Lógica pura, com JUnit.
 */
public final class TvNumbers {
    private static final String[] SUFFIXES = {"k", "M", "G", "T", "P"};
    private static final long[] POW = {1, 10, 100, 1000};

    private TvNumbers() {
    }

    public static String format(double value, int decimals, boolean abbreviate, boolean sign, char decimalSep,
                                char groupSep) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return "—";
        }
        int d = Math.max(0, Math.min(3, decimals));
        StringBuilder out = new StringBuilder(16);
        if (value < 0) {
            out.append('-');
        } else if (sign && value > 0) {
            out.append('+');
        }
        double abs = Math.abs(value);
        if (abbreviate && abs >= 1000) {
            int suffix = -1;
            while (abs >= 1000 && suffix < SUFFIXES.length - 1) {
                abs /= 1000;
                suffix++;
            }
            if (Math.round(abs * POW[d]) >= 1000 * POW[d] && suffix < SUFFIXES.length - 1) {
                abs /= 1000;
                suffix++;
            }
            fixed(out, abs, d, decimalSep);
            return out.append(SUFFIXES[suffix]).toString();
        }
        if (abs >= 1000 || abs == Math.rint(abs)) {
            grouped(out, Math.round(abs), groupSep);
        } else {
            fixed(out, abs, d, decimalSep);
        }
        return out.toString();
    }

    private static void fixed(StringBuilder out, double value, int decimals, char sep) {
        long pow = POW[decimals];
        long scaled = Math.round(value * pow);
        out.append(scaled / pow);
        if (decimals > 0) {
            out.append(sep);
            String frac = Long.toString(scaled % pow);
            for (int k = frac.length(); k < decimals; k++) {
                out.append('0');
            }
            out.append(frac);
        }
    }

    private static void grouped(StringBuilder out, long value, char sep) {
        String digits = Long.toString(value);
        int first = digits.length() % 3 == 0 ? 3 : digits.length() % 3;
        out.append(digits, 0, first);
        for (int k = first; k < digits.length(); k += 3) {
            out.append(sep).append(digits, k, k + 3);
        }
    }
}
