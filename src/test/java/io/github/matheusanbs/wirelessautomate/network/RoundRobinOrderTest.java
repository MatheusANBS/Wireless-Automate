package io.github.matheusanbs.wirelessautomate.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RoundRobinOrderTest {
    private static RoundRobinOrder<String> order(List<String> destinations, Map<String, Integer> priorities) {
        return new RoundRobinOrder<>(destinations, d -> priorities.getOrDefault(d, 0));
    }

    @Test
    void emptyListWorks() {
        RoundRobinOrder<String> order = order(List.of(), Map.of());
        assertEquals(0, order.size());
        assertTrue(order.pass().isEmpty());
        order.delivered("x");
        assertTrue(order.pass().isEmpty());
    }

    @Test
    void higherPriorityFirstAndTiesKeepListOrder() {
        RoundRobinOrder<String> order = order(
                List.of("a", "b", "c", "d", "e"), Map.of("b", 5, "d", 5, "e", -1));
        assertEquals(5, order.size());
        assertEquals(List.of("b", "d", "a", "c", "e"), order.pass());
    }

    @Test
    void roundRobinWrapsAroundWithinGroup() {
        RoundRobinOrder<String> order = order(List.of("a", "b", "c"), Map.of());
        order.delivered("a");
        assertEquals(List.of("b", "c", "a"), order.pass());
        order.delivered("b");
        assertEquals(List.of("c", "a", "b"), order.pass());
        order.delivered("c");
        assertEquals(List.of("a", "b", "c"), order.pass());
    }

    @Test
    void cursorIsPerGroup() {
        RoundRobinOrder<String> order = order(
                List.of("a", "b", "x", "y", "z"), Map.of("a", 1, "b", 1));
        order.delivered("y");
        assertEquals(List.of("a", "b", "z", "x", "y"), order.pass());
        order.delivered("a");
        assertEquals(List.of("b", "a", "z", "x", "y"), order.pass());
    }

    @Test
    void unknownDestinationIsIgnored() {
        RoundRobinOrder<String> order = order(List.of("a", "b"), Map.of());
        order.delivered("b");
        order.delivered("zzz");
        assertEquals(List.of("a", "b"), order.pass());
    }

    @Test
    void matchesByEquals() {
        RoundRobinOrder<String> order = order(List.of("a", "b"), Map.of());
        order.delivered(new String("a"));
        assertEquals(List.of("b", "a"), order.pass());
    }

    @Test
    void passReusesImmutableView() {
        RoundRobinOrder<String> order = order(List.of("a", "b"), Map.of());
        List<String> first = order.pass();
        assertThrows(UnsupportedOperationException.class, () -> first.add("c"));
        order.delivered("a");
        assertEquals(List.of("a", "b"), first);
        assertSame(first, order.pass());
        assertEquals(List.of("b", "a"), first);
    }

    @Test
    void deliveringDuringIterationIsSafe() {
        RoundRobinOrder<String> order = order(List.of("a", "b", "c"), Map.of());
        StringBuilder seen = new StringBuilder();
        for (String destination : order.pass()) {
            seen.append(destination);
            order.delivered(destination);
        }
        assertEquals("abc", seen.toString());
        assertEquals(List.of("a", "b", "c"), order.pass());
    }

    @Test
    void readsPriorityOncePerDistinctDestination() {
        int[] calls = {0};
        RoundRobinOrder<String> order = new RoundRobinOrder<>(List.of("a", "b"), d -> {
            calls[0]++;
            return 0;
        });
        order.pass();
        order.pass();
        assertEquals(2, calls[0]);
    }

    @Test
    void rejectsNullDestination() {
        List<String> withNull = java.util.Arrays.asList("a", null);
        assertThrows(NullPointerException.class, () -> order(withNull, Map.of()));
    }

    @Test
    void sharedLayoutKeepsCursorsPerOrder() {
        RoundRobinOrder.Layout<String> layout = new RoundRobinOrder.Layout<>(List.of("a", "b", "c"), d -> d.equals("c") ? 1 : 0);
        RoundRobinOrder<String> first = new RoundRobinOrder<>(layout);
        RoundRobinOrder<String> second = new RoundRobinOrder<>(layout);
        first.delivered("a");
        assertEquals(List.of("c", "b", "a"), first.pass());
        assertEquals(List.of("c", "a", "b"), second.pass());
        assertEquals(3, second.size());
    }

    @Test
    void passIsStableWhileDelivering() {
        RoundRobinOrder<String> order = order(List.of("a", "b", "c"), Map.of());
        List<String> pass = order.pass();
        order.delivered("a");
        assertEquals(List.of("a", "b", "c"), pass);
        assertEquals(List.of("b", "c", "a"), order.pass());
    }

    @Test
    void passReadsEveryGroupFromItsCursor() {
        RoundRobinOrder<String> order = order(
                List.of("a", "b", "c", "x", "y", "z", "w"), Map.of("a", 2, "b", 2, "c", 2, "w", -1));
        order.delivered("b");
        order.delivered("x");
        List<String> pass = order.pass();
        assertEquals(List.of("c", "a", "b", "y", "z", "x", "w"), pass);
        assertEquals("y", pass.get(3));
        assertThrows(IndexOutOfBoundsException.class, () -> pass.get(7));
    }

    @Test
    void repeatedDestinationCountsByFirstOccurrence() {
        RoundRobinOrder<String> order = order(List.of("a", "b", "a", "c"), Map.of());
        assertEquals(List.of("a", "b", "a", "c"), order.pass());
        // Pelo índice direto (último lido é a repetição) e pela tabela: os dois vão à primeira ocorrência.
        List<String> pass = order.pass();
        String repeated = pass.get(2);
        order.delivered(repeated);
        assertEquals(List.of("b", "a", "c", "a"), order.pass());
        order.delivered(new String("a"));
        assertEquals(List.of("b", "a", "c", "a"), order.pass());
    }

    @Test
    void deliveredAfterReadingAnotherDestinationUsesTheRightOne() {
        RoundRobinOrder<String> order = order(List.of("a", "b", "c"), Map.of());
        List<String> pass = order.pass();
        String b = pass.get(1);
        pass.get(2);
        order.delivered(b);
        assertEquals(List.of("c", "a", "b"), order.pass());
    }

    @Test
    void extremePrioritiesKeepOrder() {
        RoundRobinOrder<String> order = order(List.of("a", "b", "c", "d"),
                Map.of("a", Integer.MIN_VALUE, "b", Integer.MAX_VALUE, "c", 0, "d", Integer.MAX_VALUE));
        assertEquals(List.of("b", "d", "c", "a"), order.pass());
    }

    @Test
    void sameAsComparesIdentityOrderAndPriority() {
        String a = "a";
        String b = "b";
        RoundRobinOrder.Layout<String> layout = new RoundRobinOrder.Layout<>(List.of(a, b), d -> 0);
        assertTrue(layout.sameAs(List.of(a, b), d -> 0));
        assertFalse(layout.sameAs(List.of(b, a), d -> 0));
        assertFalse(layout.sameAs(List.of(a), d -> 0));
        assertFalse(layout.sameAs(List.of(a, b), d -> d.equals("b") ? 1 : 0));
        assertFalse(layout.sameAs(List.of(a, new String("b")), d -> 0));
        assertSame(b, layout.destination(1));
    }

    @Test
    void manyDestinationsFindEachOne() {
        List<Integer> destinations = new java.util.ArrayList<>();
        for (int i = 0; i < 1000; i++) {
            destinations.add(i);
        }
        RoundRobinOrder<Integer> order = new RoundRobinOrder<>(destinations, d -> d % 3);
        for (int i = 0; i < 1000; i += 7) {
            order.delivered(Integer.valueOf(i));
        }
        List<Integer> pass = order.pass();
        assertEquals(1000, pass.size());
        assertEquals(1000, new java.util.HashSet<>(pass).size());
        // Grupo de prioridade 2 (2, 5, 8...): a última entrega nele foi 980 (múltiplo de 7 com resto 2), então começa no 983.
        assertEquals(983, pass.get(0));
    }
}
