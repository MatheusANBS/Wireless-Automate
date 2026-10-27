package io.github.matheusanbs.wirelessautomate.tv;

import java.util.Optional;

/**
 * Uma regra de uma peça: se o dado {@code op} {@code value}, aplica o efeito (trocar a cor do texto ou o fundo,
 * piscar ou esconder). {@code bind} é a ligação própria da regra; sem ela vale o dado da própria peça.
 */
public record TvRule(TvRuleOp op, double value, Optional<TvBinding> bind, Optional<Integer> color,
        Optional<Integer> background, boolean blink, boolean hide) {
    public static final int MAX_PER_PIECE = 8;

    public boolean matches(double actual) {
        return op.test(actual, value);
    }
}
