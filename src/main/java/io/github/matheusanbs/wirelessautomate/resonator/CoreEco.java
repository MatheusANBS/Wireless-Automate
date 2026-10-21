package io.github.matheusanbs.wirelessautomate.resonator;

/**
 * As contas do Eco Ressonante: cada Eco soma {@code bonus} (0,05 = +5%) à geração base, {@code fator = 1 + eco × bonus};
 * com {@code max} acima de 0 o Eco vale no máximo {@code max}, e com 0 não tem teto. Tudo em {@code long}, saturando em
 * {@code Long.MAX_VALUE}. Lógica pura.
 */
public final class CoreEco {
    private CoreEco() {
    }

    /** O Eco que vale: nunca negativo e, com teto ({@code max > 0}), no máximo {@code max}. */
    public static long effective(long eco, long max) {
        long value = Math.max(0, eco);
        return max > 0 ? Math.min(value, max) : value;
    }

    /** O fator sobre a geração base. */
    public static double factor(long eco, double bonus, long max) {
        return 1 + effective(eco, max) * Math.max(0, bonus);
    }

    /** {@code base} no fator do Eco, arredondado para baixo e saturando em {@code Long.MAX_VALUE}; nunca abaixo da base. */
    public static long apply(long base, long eco, double bonus, long max) {
        if (base <= 0 || bonus <= 0 || effective(eco, max) == 0) {
            return base;
        }
        double result = Math.floor(base * factor(eco, bonus, max));
        if (result >= (double) Long.MAX_VALUE) {
            return Long.MAX_VALUE;
        }
        return Math.max(base, (long) result);
    }

    /**
     * Quantos dos {@code offered} cristais o Eco ainda aceita: até o teto, com {@code max > 0}, ou até
     * {@code Long.MAX_VALUE}, sem ele.
     */
    public static int room(long eco, long max, int offered) {
        long limit = max > 0 ? max : Long.MAX_VALUE;
        long free = Math.max(0, limit - Math.max(0, eco));
        return (int) Math.max(0, Math.min(offered, free));
    }

    /** Soma {@code count} ao Eco, saturando em {@code Long.MAX_VALUE}. */
    public static long add(long eco, long count) {
        long value = Math.max(0, eco);
        long sum = value + Math.max(0, count);
        return sum < value ? Long.MAX_VALUE : sum;
    }
}
