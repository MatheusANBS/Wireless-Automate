package io.github.matheusanbs.wirelessautomate.tv;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import org.junit.jupiter.api.Test;

class TvRulesTest {
    private static final OptionalDouble NONE = OptionalDouble.empty();

    private static TvRule rule(TvRuleOp op, double value, Optional<TvBinding> bind) {
        return new TvRule(op, value, bind, Optional.empty(), Optional.empty(), false, false);
    }

    @Test
    void aPrimeiraQueBateVence() {
        TvRule first = rule(TvRuleOp.LT, 10, Optional.empty());
        TvRule second = rule(TvRuleOp.LT, 100, Optional.empty());
        assertSame(first, TvRules.first(List.of(first, second), OptionalDouble.of(5), b -> NONE).orElseThrow());
        assertSame(second, TvRules.first(List.of(first, second), OptionalDouble.of(50), b -> NONE).orElseThrow());
        assertTrue(TvRules.first(List.of(first, second), OptionalDouble.of(500), b -> NONE).isEmpty());
    }

    @Test
    void ordemDeterminaQuemVence() {
        TvRule wide = rule(TvRuleOp.LT, 100, Optional.empty());
        TvRule narrow = rule(TvRuleOp.LT, 10, Optional.empty());
        assertSame(wide, TvRules.first(List.of(wide, narrow), OptionalDouble.of(5), b -> NONE).orElseThrow());
    }

    @Test
    void igualTemToleranciaRelativa() {
        TvRule eq = rule(TvRuleOp.EQ, 1_000_000.0, Optional.empty());
        assertTrue(eq.matches(1_000_000.0 + 1e-4));
        assertFalse(eq.matches(1_000_001.0));
        assertTrue(rule(TvRuleOp.EQ, 0, Optional.empty()).matches(0));
        assertTrue(rule(TvRuleOp.GT, 0, Optional.empty()).matches(0.1));
    }

    @Test
    void semValorNaoBateNenhuma() {
        List<TvRule> rules = List.of(rule(TvRuleOp.LT, 1e12, Optional.empty()),
                rule(TvRuleOp.GT, -1e12, Optional.empty()), rule(TvRuleOp.EQ, 0, Optional.empty()));
        assertTrue(TvRules.first(rules, NONE, b -> OptionalDouble.of(0)).isEmpty());
    }
}
