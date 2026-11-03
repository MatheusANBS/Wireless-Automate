package io.github.matheusanbs.wirelessautomate.client.tablet.kit;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class ChipLayoutTest {
    @Test
    void chipsInARowThenTheLink() {
        // "Lia" 15 e "Rafa" 19 de texto: fichas de 31 e 35, 4 px entre elas; o link 4 px depois do passo (xs + 4)
        ChipLayout.Placed p = ChipLayout.place(new int[] {15, 19}, 216, 411, 52, n -> 12);
        assertArrayEquals(new int[] {216, 251}, p.xs());
        assertEquals(2, p.shown());
        assertEquals(-1, p.moreX());
        assertEquals(294, p.linkX());
    }

    @Test
    void whatDoesNotFitBecomesMore() {
        ChipLayout.Placed p = ChipLayout.place(new int[] {60, 60, 60}, 216, 411, 52, n -> 12);
        assertEquals(1, p.shown());
        assertEquals(216 + 76 + 4, p.moreX());
        assertEquals(216 + 76 + 4 + 28 + 4 + 4, p.linkX());
    }

    @Test
    void exactLimitIsRightPlusOne() {
        // uma ficha de 15: 216 + 31 + (4 + 4 + 52) = 307 = right + 1 cabe; com right 305 não cabe
        assertEquals(1, ChipLayout.place(new int[] {15}, 216, 306, 52, n -> 12).shown());
        assertEquals(0, ChipLayout.place(new int[] {15}, 216, 305, 52, n -> 12).shown());
    }

    @Test
    void noGuests() {
        ChipLayout.Placed p = ChipLayout.place(new int[0], 216, 411, 52, n -> 12);
        assertEquals(0, p.shown());
        assertEquals(-1, p.moreX());
        assertEquals(216, p.linkX());
    }

    @Test
    void firstChipDoesNotFit() {
        ChipLayout.Placed p = ChipLayout.place(new int[] {60, 60}, 216, 250, 52, n -> 12);
        assertEquals(0, p.shown());
        assertEquals(216, p.moreX());
        assertEquals(216 + 12 + 16 + 4 + 4, p.linkX());
    }

    @Test
    void moreWidthFollowsHiddenCount() {
        // "+N" mede 10 por unidade: com 2 escondidas, 20; o link vem 4 depois da ficha de 20 + 16
        ChipLayout.Placed p = ChipLayout.place(new int[] {60, 60, 60}, 216, 411, 52, n -> n * 10);
        assertEquals(1, p.shown());
        assertEquals(296, p.moreX());
        assertEquals(296 + 20 + 16 + 4 + 4, p.linkX());
    }
}
