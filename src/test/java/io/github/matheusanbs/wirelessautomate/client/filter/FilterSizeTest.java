package io.github.matheusanbs.wirelessautomate.client.filter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class FilterSizeTest {
    @Test
    void padraoCabeNoMinimoENoMaximo() {
        assertTrue(FilterSize.savedWidth() >= FilterLayout.MIN_W && FilterSize.savedWidth() <= FilterLayout.MAX_W);
        assertTrue(FilterSize.savedHeight() >= FilterLayout.MIN_H && FilterSize.savedHeight() <= FilterLayout.MAX_H);
    }

    @Test
    void alturaPadraoNaGradeDeLinhas() {
        assertEquals(0, (FilterSize.savedHeight() - FilterLayout.MIN_H) % FilterLayout.ROW);
    }

    @Test
    void larguraPadraoPar() {
        assertEquals(0, FilterSize.savedWidth() % 2);
    }

    @Test
    void padraoNumaJanelaGrandeEhOProprioPadrao() {
        assertEquals(FilterSize.savedWidth(), FilterSize.width(1920));
        assertEquals(FilterSize.savedHeight(), FilterSize.height(1080));
    }
}
