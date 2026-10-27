package io.github.matheusanbs.wirelessautomate.client.tv;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class TvFitTest {
    @Test
    void cabeNoMaximo() {
        assertEquals(8f, TvFit.size(8, 100, 20, 5, true));
    }

    @Test
    void encolhePelaLargura() {
        assertEquals(4f, TvFit.size(8, 40, 20, 10, true));
    }

    @Test
    void encolhePelaAltura() {
        assertEquals(6f, TvFit.size(8, 100, 6, 1, true));
    }

    @Test
    void desligadoDevolveOMaximo() {
        assertEquals(8f, TvFit.size(8, 10, 2, 10, false));
    }

    @Test
    void nuncaAbaixoDeUmPixel() {
        assertEquals(1f, TvFit.size(8, 1, 20, 100, true));
    }

    @Test
    void textoVazioFicaNoMaximoQueCabeNaAltura() {
        assertEquals(8f, TvFit.size(8, 10, 20, 0, true));
    }
}
