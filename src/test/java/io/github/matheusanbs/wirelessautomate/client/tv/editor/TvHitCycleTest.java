package io.github.matheusanbs.wirelessautomate.client.tv.editor;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.matheusanbs.wirelessautomate.client.tv.editor.TvEditOpsTest.P;
import java.util.List;
import org.junit.jupiter.api.Test;

class TvHitCycleTest {
    private static final TvEditOps.Access<P> A = TvEditOpsTest.A;

    /** Quatro peças: 0, 1 e 3 se cruzam em (1, 1) e a 2 está longe. */
    private static List<P> stack() {
        return List.of(new P("a", 0, 0, 10, 10, 0), new P("b", 0, 0, 10, 10, 0), new P("c", 200, 200, 10, 10, 0),
                new P("d", 0, 0, 10, 10, 0));
    }

    @Test
    void semEscolhaPegaADeCima() {
        assertEquals(3, TvHitCycle.next(A, stack(), 1, 1, TvSelection.EMPTY));
    }

    @Test
    void cadaCliqueDesceUmaECicla() {
        List<P> pieces = stack();
        assertEquals(1, TvHitCycle.next(A, pieces, 1, 1, TvSelection.of(3)));
        assertEquals(0, TvHitCycle.next(A, pieces, 1, 1, TvSelection.of(1)));
        assertEquals(3, TvHitCycle.next(A, pieces, 1, 1, TvSelection.of(0)));
    }

    @Test
    void foraDeTodasDevolveMenosUm() {
        assertEquals(-1, TvHitCycle.next(A, stack(), 500, 500, TvSelection.EMPTY));
    }
}
