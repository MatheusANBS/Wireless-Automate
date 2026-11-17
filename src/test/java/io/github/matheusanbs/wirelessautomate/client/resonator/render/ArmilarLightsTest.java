package io.github.matheusanbs.wirelessautomate.client.resonator.render;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** As três luzes contra o {@code luzes(t, {a, u})} do filme rodado em Node. */
class ArmilarLightsTest {
    private static final double TOL = 1e-6;

    private static double[][] compute(double t, double a, double u, double flowK) {
        ArmilarPhase phase = new ArmilarPhase();
        phase.set(t, u);
        double[][] out = new double[ArmilarLights.COUNT][8];
        ArmilarLights.compute(t, a, flowK, phase, out);
        return out;
    }

    @Test
    void kDasTresLuzesCombinaComOFilme() {
        double[][] gold = {
                {11, 0.726742041, 0, 0},
                {10.5, 0.62531445, 0, 0},
                {11.1, 1.08754258, 0, 0},
                {11.2, 1.2013577, 1.4, 0},
                {11.62, 1.2371649, 0, 1.2544},
                {12, 1.24998757, 0, 1.6},
                {13, 0.933481804, 0, 0.8}};
        for (double[] row : gold) {
            double[][] out = compute(row[0], 1, 1, 1);
            assertEquals(row[1], out[0][ArmilarLights.K], TOL, row[0] + " sol");
            assertEquals(row[2], out[1][ArmilarLights.K], TOL, row[0] + " horizonte");
            assertEquals(row[3], out[2][ArmilarLights.K], TOL, row[0] + " Porta");
        }
    }

    @Test
    void semConjuncaoSoOSolAcende() {
        double[][] out = compute(3, 0.5, 0, 1);
        assertEquals(0.31722556, out[0][ArmilarLights.K], TOL);
        assertEquals(0, out[1][ArmilarLights.K], TOL);
        assertEquals(0, out[2][ArmilarLights.K], TOL);
    }

    @Test
    void aVazaoMexeSoNaPorta() {
        double half = compute(12, 1, 1, 0.5)[2][ArmilarLights.K];
        assertEquals(0.8, half, TOL);
        double[][] none = compute(12, 1, 1, 0);
        assertEquals(0, none[2][ArmilarLights.K], TOL);
        // a conjunção acontece no sol: ele não depende da vazão
        assertEquals(compute(12, 1, 1, 1)[0][ArmilarLights.K], none[0][ArmilarLights.K], TOL);
    }

    @Test
    void posicoesRaiosECores() {
        double[][] out = compute(11.2, 1, 1, 1);
        assertEquals(0, out[0][ArmilarLights.X], TOL);
        assertEquals(47.5, out[0][ArmilarLights.Y], TOL);
        assertEquals(0, out[0][ArmilarLights.Z], TOL);
        assertEquals(32.5, out[1][ArmilarLights.X], TOL);
        assertEquals(47.5, out[1][ArmilarLights.Y], TOL);
        // a Porta: 44 do filme mais o deslocamento do jogo
        assertEquals(ArmilarSpec.PORTA_LUZ[0], out[2][ArmilarLights.X], TOL);
        assertEquals(8, out[2][ArmilarLights.Y], TOL);
        assertEquals(64, out[0][ArmilarLights.RADIUS], TOL);
        assertEquals(26, out[1][ArmilarLights.RADIUS], TOL);
        assertEquals(34, out[2][ArmilarLights.RADIUS], TOL);
        assertEquals(0.55, out[0][ArmilarLights.G], TOL);
        assertEquals(0.8, out[1][ArmilarLights.G], TOL);
        assertEquals(0.75, out[2][ArmilarLights.G], TOL);
    }
}
