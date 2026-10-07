package io.github.matheusanbs.wirelessautomate.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class TickBudgetTest {
    private static final long HALF_MS = 500_000L;

    @Test
    void hasTimeUntilLimit() {
        TickBudget budget = new TickBudget(HALF_MS);
        budget.begin(1_000L);
        assertTrue(budget.hasTime(1_000L));
        assertTrue(budget.hasTime(1_000L + HALF_MS - 1));
        assertFalse(budget.hasTime(1_000L + HALF_MS));
    }

    @Test
    void keepsFullBudgetWhileServerIsHealthy() {
        TickBudget budget = new TickBudget(HALF_MS);
        budget.adapt(TickBudget.MSPT_RELAXED);
        assertEquals(HALF_MS, budget.limitNanos());
    }

    @Test
    void shrinksLinearlyAsMsptRises() {
        TickBudget budget = new TickBudget(HALF_MS);
        budget.adapt((TickBudget.MSPT_RELAXED + TickBudget.MSPT_OVERLOADED) / 2);
        assertEquals((long) (HALF_MS * 0.625), budget.limitNanos());
    }

    @Test
    void floorsAtMinimumWhenOverloaded() {
        TickBudget budget = new TickBudget(HALF_MS);
        budget.adapt(200.0);
        assertEquals((long) (HALF_MS * TickBudget.MIN_FACTOR), budget.limitNanos());
        budget.adapt(10.0);
        assertEquals(HALF_MS, budget.limitNanos());
    }

    @Test
    void tracksUsage() {
        TickBudget budget = new TickBudget(HALF_MS);
        budget.begin(0L);
        budget.end(200_000L);
        assertEquals(200_000L, budget.lastUsedNanos());
        assertTrue(budget.averageUsedNanos() > 0 && budget.averageUsedNanos() < 200_000L);
    }

    @Test
    void rejectsNonPositiveBudget() {
        assertThrows(IllegalArgumentException.class, () -> new TickBudget(0L));
    }
}
