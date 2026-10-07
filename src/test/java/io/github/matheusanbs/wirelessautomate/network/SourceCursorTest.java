package io.github.matheusanbs.wirelessautomate.network;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class SourceCursorTest {
    @Test
    void emptyListStaysAtZero() {
        assertEquals(0, SourceCursor.next(0, -1, 0));
    }

    @Test
    void nobodyMovedStepsOne() {
        assertEquals(4, SourceCursor.next(3, -1, 10));
        assertEquals(0, SourceCursor.next(9, -1, 10));
    }

    @Test
    void startsAfterLastMover() {
        assertEquals(5, SourceCursor.next(0, 4, 10));
        assertEquals(0, SourceCursor.next(6, 9, 10));
        assertEquals(3, SourceCursor.next(8, 2, 10));
    }

    @Test
    void everybodyMovedStillRotates() {
        // A volta começou em 3 e a última que moveu foi a 2: voltar para 3 repetiria a ordem.
        assertEquals(4, SourceCursor.next(3, 2, 10));
        assertEquals(0, SourceCursor.next(9, 8, 10));
        assertEquals(0, SourceCursor.next(0, 0, 1));
    }

    @Test
    void scarceSpaceIsSharedInTurn() {
        // Um slot abre por tick e só a primeira origem da volta o pega: todas são servidas em 10 ticks.
        int count = 10;
        int cursor = 0;
        Set<Integer> served = new HashSet<>();
        for (int tick = 0; tick < count; tick++) {
            served.add(cursor);
            cursor = SourceCursor.next(cursor, cursor, count);
        }
        assertEquals(count, served.size());
    }
}
