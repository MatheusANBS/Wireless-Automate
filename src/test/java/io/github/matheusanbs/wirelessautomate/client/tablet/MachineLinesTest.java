package io.github.matheusanbs.wirelessautomate.client.tablet;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.matheusanbs.wirelessautomate.client.tablet.MachineLines.Arrow;
import io.github.matheusanbs.wirelessautomate.client.tablet.MachineLines.Kind;
import io.github.matheusanbs.wirelessautomate.client.tablet.MachineLines.Step;
import java.util.List;
import org.junit.jupiter.api.Test;

class MachineLinesTest {
    /**
     * 0 Fornalha (aberta): 1 presa do roteador 0, 2 e 3 do roteador 0, 4 e 5 do roteador 1, 6 "mais N";
     * 7 Triturador (recolhido); 8 Tanque (aberto): 9 do roteador 1.
     */
    private static MachineLines sample() {
        List<Kind> kinds = List.of(Kind.BLOCK, Kind.MACHINE, Kind.MACHINE, Kind.MACHINE, Kind.MACHINE, Kind.MACHINE,
                Kind.MORE, Kind.BLOCK, Kind.BLOCK, Kind.MACHINE);
        boolean[] collapsed = new boolean[10];
        collapsed[7] = true;
        return new MachineLines(kinds, collapsed);
    }

    @Test
    void upAndDownWalkAllLinesAndStopAtTheEdges() {
        MachineLines lines = sample();
        assertEquals(0, lines.next(-1, 1));
        assertEquals(9, lines.next(-1, -1));
        assertEquals(7, lines.next(6, 1));
        assertEquals(8, lines.next(7, 1));
        assertEquals(9, lines.next(9, 1));
        assertEquals(0, lines.next(0, -1));
        assertEquals(-1, new MachineLines(List.of(), new boolean[0]).next(-1, 1));
    }

    @Test
    void leftClosesTheBlockOrGoesUpToTheHeader() {
        MachineLines lines = sample();
        assertEquals(new Arrow(Step.CLOSE_BLOCK, 0), lines.left(0));
        assertEquals(Step.NONE, lines.left(7).step());
        assertEquals(new Arrow(Step.MOVE_TO, 0), lines.left(4));
        assertEquals(new Arrow(Step.MOVE_TO, 0), lines.left(6));
        assertEquals(new Arrow(Step.MOVE_TO, 8), lines.left(9));
        assertEquals(Step.NONE, lines.left(-1).step());
    }

    @Test
    void rightOpensGoesDownOrShowsAll() {
        MachineLines lines = sample();
        assertEquals(new Arrow(Step.OPEN_BLOCK, 7), lines.right(7));
        assertEquals(new Arrow(Step.MOVE_TO, 1), lines.right(0));
        assertEquals(new Arrow(Step.SHOW_ALL, 6), lines.right(6));
        assertEquals(Step.NONE, lines.right(3).step());
        assertEquals(new Arrow(Step.MOVE_TO, 9), lines.right(8));
        assertEquals(7, lines.header(7));
        assertEquals(0, lines.header(6));
    }
}
