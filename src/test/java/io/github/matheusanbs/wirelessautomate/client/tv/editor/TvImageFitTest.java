package io.github.matheusanbs.wirelessautomate.client.tv.editor;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class TvImageFitTest {
    @Test
    void escalaInteiraQueCabeAteODobro() {
        assertEquals(2, TvImageFit.scale(16, 16, 128, 64), "16 px numa Tela 4 × 2: 2×, não 4×");
        assertEquals(1, TvImageFit.scale(48, 16, 64, 64), "48 px de largura em 64 só cabe em 1×");
        assertEquals(2, TvImageFit.scale(8, 8, 32, 32));
        assertEquals(1, TvImageFit.scale(32, 32, 32, 32), "do tamanho da Tela: 1×");
    }

    @Test
    void esticaQuandoNemUmCabe() {
        assertEquals(0, TvImageFit.scale(64, 64, 32, 32));
        assertEquals(0, TvImageFit.scale(64, 16, 32, 320), "a largura sozinha já não cabe");
    }

    @Test
    void caixaCentradaNoPontoEPresaNaArea() {
        assertArrayEquals(new int[] {44, 16, 32, 32}, TvImageFit.box(16, 16, 2, 128, 64, 60, 32));
        assertArrayEquals(new int[] {0, 0, 32, 32}, TvImageFit.box(16, 16, 2, 128, 64, 3, 2), "no canto");
        assertArrayEquals(new int[] {96, 32, 32, 32}, TvImageFit.box(16, 16, 2, 128, 64, 127, 63), "na borda");
    }

    @Test
    void esticadaMantemAProporcaoDaArte() {
        assertArrayEquals(new int[] {0, 0, 32, 32}, TvImageFit.box(64, 64, 0, 32, 32, 16, 16));
        assertArrayEquals(new int[] {0, 8, 32, 16}, TvImageFit.box(64, 32, 0, 32, 32, 16, 16));
        assertArrayEquals(new int[] {8, 0, 16, 32}, TvImageFit.box(32, 64, 0, 32, 32, 16, 16));
    }
}
