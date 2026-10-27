package io.github.matheusanbs.wirelessautomate.client.tv.editor;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class TvEditorSizeTest {
    @Test
    void prendeAoMinimoEAJanela() {
        assertEquals(400, TvEditorSize.fit(300, 400, 800));
        assertEquals(800, TvEditorSize.fit(900, 400, 800));
        assertEquals(560, TvEditorSize.fit(560, 400, 800));
    }

    @Test
    void janelaMenorQueOMinimoValeAJanela() {
        assertEquals(333, TvEditorSize.fit(560, 400, 333));
        assertEquals(1, TvEditorSize.fit(560, 400, 0));
    }

    @Test
    void arredondaParaPar() {
        assertEquals(480, TvEditorSize.even(480.8));
        assertEquals(482, TvEditorSize.even(481.8));
    }
}
