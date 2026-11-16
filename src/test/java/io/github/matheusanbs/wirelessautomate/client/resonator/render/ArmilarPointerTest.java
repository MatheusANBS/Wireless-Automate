package io.github.matheusanbs.wirelessautomate.client.resonator.render;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** O ponteiro do mostrador contra os números do filme. */
class ArmilarPointerTest {
    @Test
    void ouroDoFilme() {
        assertEquals(0.5, ArmilarPointer.fraction(0, 0), 1e-9);
        assertEquals(0.700383314, ArmilarPointer.fraction(3, 0), 1e-6);
        assertEquals(0.672446372, ArmilarPointer.fraction(7.7, 0), 1e-6);
        assertEquals(0.62, ArmilarPointer.fraction(3, 1), 1e-9);
    }

    @Test
    void emSintoniaParaEm62PorCento() {
        for (double t = 0; t < 400; t += 7.3) {
            assertEquals(0.62, ArmilarPointer.fraction(t, 1), 1e-12, "t = " + t);
        }
    }

    @Test
    void foraDaSintoniaFicaNoMostrador() {
        for (double t = 0; t < 400; t += 0.37) {
            double f = ArmilarPointer.fraction(t, 0);
            assertEquals(true, f >= 0.14 && f <= 0.86, "t = " + t + " f = " + f);
        }
    }
}
