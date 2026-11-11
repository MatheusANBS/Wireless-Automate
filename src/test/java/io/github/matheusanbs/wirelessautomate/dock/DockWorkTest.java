package io.github.matheusanbs.wirelessautomate.dock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class DockWorkTest {
    private static final double EPS = 1e-9;

    @Test
    void sendsAtMostOneEventPerInterval() {
        assertTrue(DockWork.shouldSend(0, DockWork.NEVER));
        assertFalse(DockWork.shouldSend(1099, 1000));
        assertTrue(DockWork.shouldSend(1100, 1000));
    }

    @Test
    void loopPhaseAndStart() {
        assertEquals(0, DockWork.loopPhase(0), EPS);
        assertEquals(0.5, DockWork.loopPhase(60), EPS);
        assertEquals(0.25, DockWork.loopPhase(1230), EPS);
        assertEquals(1230 - 30, DockWork.loopStart(1230.5));
        assertEquals(0.5, DockWork.loopPhase(-60), EPS);
    }

    @Test
    void workingForThreeHundredTicksAfterTheLastEvent() {
        DockWork work = new DockWork();
        assertFalse(work.working(0));
        work.event(1000, 2);
        assertTrue(work.working(1000));
        assertTrue(work.working(1299));
        assertFalse(work.working(1300));
        assertFalse(work.working(999));
        work.event(1100, 3);
        assertTrue(work.working(1399));
        assertEquals(3, work.side());
    }

    @Test
    void workLoopOnlyFromTheStartOfALoop() {
        DockWork work = new DockWork();
        work.event(1210, 5);
        // O laço de 1200 começou parado: sem pacote até o seguinte.
        assertFalse(work.workLoop(1250));
        assertTrue(work.workLoop(1320));
        assertEquals(5, work.loopSide(1320));
        // O laço que começa trabalhando termina inteiro, mesmo depois dos 300 ticks.
        assertTrue(work.workLoop(1500));
        assertTrue(work.workLoop(1559.9));
        assertFalse(work.workLoop(1560));
    }

    @Test
    void loopSideWaitsForTheNextLoop() {
        DockWork work = new DockWork();
        work.event(1205, 2);
        work.event(1330, 4);
        assertEquals(2, work.loopSide(1340));
        assertEquals(4, work.loopSide(1440));
    }

    @Test
    void speedRampsUpAndDownInOneSecond() {
        DockWork work = new DockWork();
        assertEquals(0, work.speed(500), EPS);
        work.event(1000, 0);
        assertEquals(0, work.speed(1000), EPS);
        assertEquals(0.5, work.speed(1010), EPS);
        assertEquals(1, work.speed(1020), EPS);
        assertEquals(1, work.speed(1299), EPS);
        assertEquals(0.5, work.speed(1310), EPS);
        assertEquals(0, work.speed(1320), EPS);
        assertEquals(0, work.speed(2000), EPS);
    }

    @Test
    void extraIsTheIntegralOfTheSpeed() {
        DockWork work = new DockWork();
        work.event(1000, 0);
        assertEquals(0, work.extra(1000), EPS);
        assertEquals(10, work.extra(1020), EPS);
        assertEquals(10 + 280, work.extra(1300), EPS);
        assertEquals(10 + 280 + 10, work.extra(1320), EPS);
        assertEquals(300, work.extra(5000), EPS);
        assertEquals(numeric(work, 1000, 1400), work.extra(1400), 1e-3);
    }

    @Test
    void newEventsKeepTheAngleContinuous() {
        DockWork work = new DockWork();
        work.event(1000, 0);
        double before = work.extra(1200);
        work.event(1200, 1);
        assertEquals(before, work.extra(1200), EPS);
        // No meio da descida: o novo período começa na velocidade em que ela estava, sem pulo de ângulo nem de velocidade.
        double atRamp = work.extra(1505);
        double speed = work.speed(1505);
        work.event(1505, 2);
        assertEquals(atRamp, work.extra(1505), EPS);
        assertEquals(speed, work.speed(1505), EPS);
        assertEquals(1, work.speed(1520), EPS);
        // Depois do repouso, outro período soma ao que já andou.
        double rested = work.extra(3000);
        work.event(3000, 3);
        assertEquals(rested, work.extra(3000), EPS);
        assertEquals(rested + 10, work.extra(3020), EPS);
    }

    /** A integral numérica da velocidade (regra do trapézio), para conferir a fórmula fechada. */
    private static double numeric(DockWork work, double from, double to) {
        int steps = 40000;
        double h = (to - from) / steps;
        double sum = 0;
        for (int i = 0; i < steps; i++) {
            double a = from + i * h;
            sum += (work.speed(a) + work.speed(a + h)) * h / 2;
        }
        return sum;
    }
}
