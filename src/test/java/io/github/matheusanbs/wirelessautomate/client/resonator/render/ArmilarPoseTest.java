package io.github.matheusanbs.wirelessautomate.client.resonator.render;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class ArmilarPoseTest {
    @Test
    void tiltEPrecComOFilme() {
        // u = 0: sem assentamento, só a inclinação solta
        assertEquals(0.62, ArmilarPose.tilt(0, 0, 0, 1e9), 1e-9);
        assertEquals(-0.718163232, ArmilarPose.tilt(1, 0, 0, 1e9), 1e-8);
        assertEquals(0.881887775, ArmilarPose.tilt(2, 0, 0, 1e9), 1e-8);
        assertEquals(0.707329715, ArmilarPose.tilt(0, 3.3, 0, 1e9), 1e-8);
        assertEquals(0.536989938, ArmilarPose.tilt(0, 5000.123, 0, 1e9), 1e-8);
        // u = 1, longe do assentamento: deitada
        assertEquals(0.0, ArmilarPose.tilt(0, 5000.123, 1, 1e9), 1e-12);
        // u = 1 no t = 11 do filme, 0,4 s depois do assentamento (t − 10,6)
        assertEquals(0.0277092284, ArmilarPose.tilt(0, 11, 1, 0.4), 1e-8);
        assertEquals(-0.00200063039, ArmilarPose.tilt(1, 11, 1, 0.4), 1e-8);
        assertEquals(-0.0298711189, ArmilarPose.tilt(2, 11, 1, 0.4), 1e-8);
        assertEquals(1500.0369, ArmilarPose.prec(0, 5000.123), 1e-3);
        assertEquals(-997.9246, ArmilarPose.prec(1, 5000.123), 1e-3);
        assertEquals(704.21722, ArmilarPose.prec(2, 5000.123), 1e-3);
        assertEquals(2.1, ArmilarPose.prec(1, 0), 1e-9);
    }

    @Test
    void giroSoltoEhAVelocidadeDeCadaOrbita() {
        // sem sintonia o giro é off × t: 0,8, −0,55 e 0,4 rad/s (pose(·, t, 0).spin do filme)
        assertEquals(0.8, ArmilarPose.omega(0, 0), 1e-12);
        assertEquals(-0.55, ArmilarPose.omega(1, 0), 1e-12);
        assertEquals(0.4, ArmilarPose.omega(2, 0), 1e-12);
        // travado, todas na volta dos anéis
        for (int i = 0; i < 3; i++) {
            assertEquals(ArmilarSpec.S, ArmilarPose.omega(i, 1), 1e-12);
        }
    }

    @Test
    void anguloTravadoNaConjuncaoEhZero() {
        assertEquals(0.0, ArmilarPose.aligned(ArmilarSpec.TC), 1e-12);
        assertEquals(1.2 * 1.5, ArmilarPose.aligned(ArmilarSpec.TC + 1.5), 1e-12);
    }
}
