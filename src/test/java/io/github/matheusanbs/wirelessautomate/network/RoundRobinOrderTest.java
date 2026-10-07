package io.github.matheusanbs.wirelessautomate.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
}
