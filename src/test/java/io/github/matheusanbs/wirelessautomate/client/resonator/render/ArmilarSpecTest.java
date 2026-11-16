package io.github.matheusanbs.wirelessautomate.client.resonator.render;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class ArmilarSpecTest {
    @Test
    void tauComOsNumerosDoFilme() {
        assertEquals(-0.528024488, ArmilarSpec.tau(0), 1e-8);
        assertEquals(-0.764012244, ArmilarSpec.tau(5), 1e-8);
        assertEquals(0.0, ArmilarSpec.tau(11), 1e-9);
        assertEquals(1.5, ArmilarSpec.tau(12.5), 1e-9);
        assertEquals(2.6, ArmilarSpec.tau(13.6), 1e-9);
        assertEquals(-0.0117918517, ArmilarSpec.tau(100), 1e-8);
        assertEquals(-0.773331452, ArmilarSpec.tau(5000.123), 1e-7);
    }

    @Test
    void periodoEhAVoltaDosAneisTravados() {
        assertEquals(2 * Math.PI / 1.2, ArmilarSpec.PERIODO, 1e-12);
    }
}
