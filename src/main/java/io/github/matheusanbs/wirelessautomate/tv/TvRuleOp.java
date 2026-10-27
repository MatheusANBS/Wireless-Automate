package io.github.matheusanbs.wirelessautomate.tv;

/**
 * A comparação de uma regra: menor que, maior que ou igual a (tolerância relativa de 1e-9). Pura, sem classes do
 * jogo: o codec fica no {@link TvCodecs}.
 */
public enum TvRuleOp {
    LT("lt"),
    GT("gt"),
    EQ("eq");

    private static final double EPSILON = 1e-9;

    private final String id;

    TvRuleOp(String id) {
        this.id = id;
    }

    public boolean test(double actual, double reference) {
        return switch (this) {
            case LT -> actual < reference;
            case GT -> actual > reference;
            case EQ -> Math.abs(actual - reference)
                    <= EPSILON * Math.max(1.0, Math.max(Math.abs(actual), Math.abs(reference)));
        };
    }

    public String id() {
        return id;
    }
}
