package io.github.matheusanbs.wirelessautomate.tv;

import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.function.Function;

/** A avaliação das regras de uma peça. Lógica pura, chamada só no redesenho da Tela. */
public final class TvRules {
    private TvRules() {
    }

    /**
     * A primeira regra, de cima para baixo, que bate. A regra com ligação própria lê o valor por {@code byBinding};
     * a sem ligação lê {@code own}. Valor ausente (o traço) não bate nenhuma.
     */
    public static Optional<TvRule> first(List<TvRule> rules, OptionalDouble own,
            Function<TvBinding, OptionalDouble> byBinding) {
        for (TvRule rule : rules) {
            OptionalDouble value = rule.bind().isPresent() ? byBinding.apply(rule.bind().get()) : own;
            if (value.isPresent() && rule.matches(value.getAsDouble())) {
                return Optional.of(rule);
            }
        }
        return Optional.empty();
    }
}
