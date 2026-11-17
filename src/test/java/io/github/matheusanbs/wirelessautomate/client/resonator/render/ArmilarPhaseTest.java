package io.github.matheusanbs.wirelessautomate.client.resonator.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ArmilarPhaseTest {
    private static final double TOL = 1e-8;

    /** {d, dis, prep, cresce, raio, clarao, onda, cabeca, arco, porta, surge, onda2, clarao2}: {@code fase(11 + d, 1)}. */
    private static final double[][] OURO = {
        {-0.7, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0},
        {-0.3, 0, 0.5, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0},
        {0, 0, 1, 0.104, 0.5, 0, 0, 0, 0, 0, 0, 0, 0},
        {0.1, 0, 0.5, 0.648, 1, 0, 0, 0, 0.198250729, 0, 0, 0, 0},
        {0.15, 0, 0.15625, 0.896, 1, 0.68359375, 0, 0.028, 1, 0, 0, 0, 0},
        {0.2, 0, 0, 1, 1, 1, 0.0232907588, 0.104, 1, 0, 0, 0, 0},
        {0.6, 0, 0, 1, 1, 0, 0.912847483, 1, 1, 0.5, 0.0740740741, 0, 0.801749271},
        {0.9, 0, 0, 1, 1, 0, 1, 1, 1, 1, 1, 0.5, 0.352},
        {2, 0.5, 0, 1, 0.5, 0, 1, 1, 0.5, 0.5, 1, 1, 0},
        {2.5, 1, 0, 1, 0, 0, 1, 1, 0, 0, 1, 1, 0},
    };

    @Test
    void tabelaDoFilme() {
        ArmilarPhase p = new ArmilarPhase();
        for (double[] row : OURO) {
            p.set(ArmilarSpec.TC + row[0], 1);
            String d = "d = " + row[0];
            assertEquals(row[1], p.dis, TOL, d + " dis");
            assertEquals(row[2], p.prep, TOL, d + " prep");
            assertEquals(row[3], p.cresce, TOL, d + " cresce");
            assertEquals(row[4], p.raio, TOL, d + " raio");
            assertEquals(row[5], p.clarao, TOL, d + " clarao");
            assertEquals(row[6], p.onda, TOL, d + " onda");
            assertEquals(row[7], p.cabeca, TOL, d + " cabeca");
            assertEquals(row[8], p.arco, TOL, d + " arco");
            assertEquals(row[9], p.porta, TOL, d + " porta");
            assertEquals(row[10], p.surge, TOL, d + " surge");
            assertEquals(row[11], p.onda2, TOL, d + " onda2");
            assertEquals(row[12], p.clarao2, TOL, d + " clarao2");
        }
    }

    @Test
    void comMetadeDaSintonia() {
        ArmilarPhase p = new ArmilarPhase();
        p.set(11.3, 0.5);
        assertEquals(0.5, p.raio, TOL);
        assertEquals(0.448, p.clarao, TOL);
        assertEquals(0.5, p.arco, TOL);
        assertEquals(0.0, p.porta, TOL);
    }

    @Test
    void semSintoniaOsCamposDeVivoSaoZero() {
        ArmilarPhase p = new ArmilarPhase();
        p.set(11.3, 0);
        assertEquals(0.0, p.prep, 0);
        assertEquals(0.0, p.raio, 0);
        assertEquals(0.0, p.clarao, 0);
        assertEquals(0.0, p.arco, 0);
        assertEquals(0.0, p.porta, 0);
        assertEquals(0.0, p.clarao2, 0);
        // os do tempo seguem a linha do tempo (d = 0,3 no filme)
        assertEquals(0.0, p.dis, TOL);
        assertEquals(1.0, p.cresce, TOL);
        assertEquals(0.18256949661908403, p.onda, TOL);
        assertEquals(0.352, p.cabeca, TOL);
        assertEquals(0.0, p.surge, TOL);
        assertEquals(0.0, p.onda2, TOL);
    }

    @Test
    void bufferCheioPulsaEntreCincoESete() {
        assertEquals(0.6, ArmilarPhase.fullPrep(0), 1e-12);
        assertEquals(0.7, ArmilarPhase.fullPrep(0.5), 1e-12);
        assertEquals(0.5, ArmilarPhase.fullPrep(1.5), 1e-12);
        for (int k = 0; k < 200; k++) {
            double t = k * 0.037;
            double v = ArmilarPhase.fullPrep(t);
            assertTrue(v >= 0.5 - 1e-12 && v <= 0.7 + 1e-12);
            assertEquals(v, ArmilarPhase.fullPrep(t + 2), 1e-9);
        }
    }
}
