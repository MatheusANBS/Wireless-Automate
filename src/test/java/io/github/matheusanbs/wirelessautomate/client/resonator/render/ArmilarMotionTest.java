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

    @Test
    void ticksSemDesenhoDaoOMesmoGiroDeQuemDesenhouTodos() {
        double c0 = 5000.0;
        ArmilarMotion seen = new ArmilarMotion();
        ArmilarMotion away = new ArmilarMotion();
        seen.advance(1.0, c0);
        away.advance(1.0, c0);
        for (int k = 1; k <= 100; k++) {
            seen.advance(1.0, c0 + k * DT);
        }
        away.catchUp(1.0, 100, c0 + 100 * DT, DT);
        for (int i = 0; i < 3; i++) {
            assertEquals(seen.spin(i, 1f), away.spin(i, 1f), 1e-9, "giro da órbita " + i);
            assertEquals(seen.spin(i, 0f), away.spin(i, 0f), 1e-9, "giro um tick antes, órbita " + i);
        }
        assertEquals(seen.u(1f), away.u(1f), 1e-12);
    }

    @Test
    void ticksSemDesenhoEmSolto() {
        ArmilarMotion seen = new ArmilarMotion();
        ArmilarMotion away = new ArmilarMotion();
        seen.advance(0.4, 100.0);
        away.advance(0.4, 100.0);
        for (int k = 1; k <= 60; k++) {
            seen.advance(0.6, 100.0 + k * DT);
        }
        away.catchUp(0.6, 60, 100.0 + 60 * DT, DT);
        for (int i = 0; i < 3; i++) {
            assertEquals(seen.spin(i, 1f), away.spin(i, 1f), 1e-9);
        }
        assertEquals(seen.u(1f), away.u(1f), 1e-12);
    }

    @Test
    void acimaDoTetoEncaixaNaConjuncaoELevaUAoAlvo() {
        double c0 = 5000.0;
        ArmilarMotion seen = new ArmilarMotion();
        ArmilarMotion away = new ArmilarMotion();
        seen.advance(0.3, c0);
        away.advance(0.3, c0);
        int ticks = 1000;
        for (int k = 1; k <= ticks; k++) {
            seen.advance(1.0, c0 + k * DT);
        }
        away.catchUp(1.0, ticks, c0 + ticks * DT, DT);
        assertEquals(1.0, away.u(1f), 1e-6);
        for (int i = 0; i < 3; i++) {
            double aligned = ArmilarPose.aligned(c0 + ticks * DT);
            assertEquals(0, Math.IEEEremainder(away.spin(i, 1f) - aligned, 2 * Math.PI), 1e-6, "encaixado " + i);
            assertEquals(0, Math.IEEEremainder(away.spin(i, 1f) - seen.spin(i, 1f), 2 * Math.PI), 1e-3,
                    "igual ao de quem viu, órbita " + i);
        }
    }

    @Test
    void umTickSoEIgualAoAdvance() {
        ArmilarMotion one = new ArmilarMotion();
        ArmilarMotion plain = new ArmilarMotion();
        one.advance(0.5, 100.0);
        plain.advance(0.5, 100.0);
        one.catchUp(0.8, 1, 100.05, DT);
        plain.advance(0.8, 100.05);
        assertEquals(plain.u(0.3f), one.u(0.3f), 1e-15);
        assertEquals(plain.spin(2, 0.3f), one.spin(2, 0.3f), 1e-15);
    }
}
