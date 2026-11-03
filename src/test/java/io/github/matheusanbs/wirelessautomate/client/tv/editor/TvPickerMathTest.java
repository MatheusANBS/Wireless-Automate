package io.github.matheusanbs.wirelessautomate.client.tv.editor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class TvPickerMathTest {
    @Test
    void numberDrag() {
        assertFalse(TvNumberDrag.started(2));
        assertTrue(TvNumberDrag.started(-3));
        assertEquals(15, TvNumberDrag.value(10, 20, 1, 0, 100, false));
        assertEquals(30, TvNumberDrag.value(10, 20, 1, 0, 100, true));
        assertEquals(0, TvNumberDrag.value(10, -200, 1, 0, 100, false));
        assertEquals(100, TvNumberDrag.value(10, 9999, 5, 0, 100, false));
        assertEquals(10, TvNumberDrag.value(10, 3, 1, 0, 100, false));
    }
}
