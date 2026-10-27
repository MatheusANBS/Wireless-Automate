package io.github.matheusanbs.wirelessautomate.client.tv.editor;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class TvSnapTest {
    @Test
    void encaixaNaGradeOuEm1PxComAlt() {
        assertEquals(4, TvSnap.delta(5.9, false));
        assertEquals(8, TvSnap.delta(6.1, false));
        assertEquals(-4, TvSnap.delta(-3, false));
        assertEquals(0, TvSnap.delta(1.9, false));
        assertEquals(6, TvSnap.delta(6.1, true));
    }

    @Test
    void moverFicaDentroDaArea() {
        assertEquals(-4, TvSnap.moveDelta(-100, 4, 12, 64));
        assertEquals(52, TvSnap.moveDelta(100, 4, 12, 64));
        assertEquals(8, TvSnap.moveDelta(8, 4, 12, 64));
    }

    @Test
    void ladoTemMinimoEParaNaBorda() {
        assertEquals(TvSnap.MIN_SIZE, TvSnap.size(16, -100, 0, 64));
        assertEquals(24, TvSnap.size(16, 8, 0, 64));
        assertEquals(10, TvSnap.size(4, 100, 54, 64));
        assertEquals(2, TvSnap.size(4, -100, 62, 64), "perto da borda, o mínimo é o que sobra");
    }

    @Test
    void arredondaNaGrade() {
        assertEquals(8, TvSnap.floor(11));
        assertEquals(12, TvSnap.ceil(9));
        assertEquals(12, TvSnap.ceil(12));
        assertEquals(-4, TvSnap.floor(-1));
    }
}
