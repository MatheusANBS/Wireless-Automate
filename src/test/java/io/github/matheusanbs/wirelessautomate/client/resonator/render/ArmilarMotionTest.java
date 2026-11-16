package io.github.matheusanbs.wirelessautomate.client.resonator.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ArmilarMotionTest {
    private static final double DT = 0.05;

    private static double step(ArmilarMotion m, double target, double[] clock, double[] prev) {
        for (int i = 0; i < 3; i++) {
            prev[i] = m.spin(i, 1f);
        }
        clock[0] += DT;
        m.advance(target, clock[0]);
        double worst = 0;
        for (int i = 0; i < 3; i++) {
            worst = Math.max(worst, Math.abs(m.spin(i, 1f) - prev[i]));
        }
        return worst;
    }

    @Test
    void naoComecaDeslizando() {
        ArmilarMotion m = new ArmilarMotion();
        m.advance(1.0, 5000.0);
        assertEquals(1.0, m.u(1f), 1e-12);
        for (int i = 0; i < 3; i++) {
            // nasce alinhado: S·(t − TC) módulo 2π
            assertEquals(0, Math.IEEEremainder(m.spin(i, 1f) - ArmilarPose.aligned(5000.0), 2 * Math.PI), 1e-6);
        }
    }

    @Test
    void travaNaConjuncaoDepoisDeSubir() {
        ArmilarMotion m = new ArmilarMotion();
        double[] clock = {5000.0};
        double[] prev = new double[3];
        m.advance(0.0, clock[0]);
        for (int k = 0; k < 20 * 20; k++) {
            step(m, 1.0, clock, prev);
        }
        for (int i = 0; i < 3; i++) {
            assertEquals(0, Math.IEEEremainder(m.spin(i, 1f) - ArmilarPose.aligned(clock[0]), 2 * Math.PI), 1e-3,
                    "órbita " + i);
        }
    }

    @Test
    void semDispararAoTravarDepoisDeMuitoTempoSolto() {
        ArmilarMotion m = new ArmilarMotion();
        double[] clock = {5000.0};
        double[] prev = new double[3];
        m.advance(3.0 / 7, clock[0]);
        for (int k = 0; k < 20 * 600; k++) {
            step(m, 3.0 / 7, clock, prev);
        }
        double worst = 0;
        for (int k = 0; k < 20 * 20; k++) {
            worst = Math.max(worst, step(m, 1.0, clock, prev));
        }
        // a velocidade máxima é 1,2 rad/s (0,06 por tick) mais a puxada do laço de fase (no máximo 0,03)
        assertTrue(worst < 0.25, "maior salto por tick: " + worst);
    }

    @Test
    void soltoGiraNaVelocidadeDeCadaOrbita() {
        ArmilarMotion m = new ArmilarMotion();
        double[] clock = {100.0};
        double[] prev = new double[3];
        m.advance(0.0, clock[0]);
        double s0 = m.spin(0, 1f);
        for (int k = 0; k < 20; k++) {
            step(m, 0.0, clock, prev);
        }
        assertEquals(0.8, m.spin(0, 1f) - s0, 1e-9);
    }

    @Test
    void interpolaPeloParcial() {
        ArmilarMotion m = new ArmilarMotion();
        m.advance(0.0, 100.0);
        m.advance(0.0, 100.05);
        double a = m.spin(0, 0f);
        double b = m.spin(0, 1f);
        assertEquals((a + b) / 2, m.spin(0, 0.5f), 1e-12);
    }
}
