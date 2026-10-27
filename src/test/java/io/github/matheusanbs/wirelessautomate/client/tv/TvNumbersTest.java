package io.github.matheusanbs.wirelessautomate.client.tv;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class TvNumbersTest {
    private static String pt(double v, int dec, boolean abbr, boolean sign) {
        return TvNumbers.format(v, dec, abbr, sign, ',', '.');
    }

    @Test
    void abreviaComSufixo() {
        assertEquals("1,84M", pt(1_840_000, 2, true, false));
        assertEquals("12,5k", pt(12_480, 1, true, false));
        assertEquals("268,4M", pt(268_435_456, 1, true, false));
    }

    @Test
    void arredondamentoQueChegaAMilSobeOSufixo() {
        assertEquals("1,0M", pt(999_960, 1, true, false));
    }

    @Test
    void semAbreviarAgrupaOsMilhares() {
        assertEquals("12.480", pt(12_480, 1, false, false));
        assertEquals("1.840.000", pt(1_840_000, 2, false, false));
    }

    @Test
    void inteiroPequenoSemCasasEFracionarioComCasas() {
        assertEquals("2", pt(2.0, 2, false, false));
        assertEquals("0,5", pt(0.5, 1, false, false));
        assertEquals("13", pt(12.6, 0, false, false));
        assertEquals("999", pt(999, 1, true, false));
    }

    @Test
    void sinal() {
        assertEquals("+64", pt(64, 0, false, true));
        assertEquals("-8", pt(-8, 0, false, true));
        assertEquals("0", pt(0, 0, false, true));
        assertEquals("-1,5k", pt(-1_500, 1, true, false));
    }

    @Test
    void semDadoViraTraco() {
        assertEquals("—", pt(Double.NaN, 1, true, false));
    }

    @Test
    void separadoresDoIngles() {
        assertEquals("1.84M", TvNumbers.format(1_840_000, 2, true, false, '.', ','));
        assertEquals("12,480", TvNumbers.format(12_480, 0, false, false, '.', ','));
    }
}
