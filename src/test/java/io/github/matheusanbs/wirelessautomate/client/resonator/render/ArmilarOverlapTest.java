package io.github.matheusanbs.wirelessautomate.client.resonator.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ArmilarOverlapTest {
    private static final double EPS = 1e-6;

    /** As garras do nó em x = r (yaw 0), como caixas {x0, y0, z0, x1, y1, z1}. */
    private static List<double[]> claws(double r) {
        List<double[]> out = new ArrayList<>();
        for (int sx : new int[] {-1, 1}) {
            for (int sz : new int[] {-1, 1}) {
                double x0 = r + sx * ArmilarSpec.GARRA_DE;
                double x1 = r + sx * ArmilarSpec.GARRA_ATE;
                double z0 = sz * ArmilarSpec.GARRA_DE;
                double z1 = sz * ArmilarSpec.GARRA_ATE;
                out.add(new double[] {Math.min(x0, x1), ArmilarSpec.TOPO_BASE, Math.min(z0, z1),
                        Math.max(x0, x1), ArmilarSpec.TOPO_GARRA, Math.max(z0, z1)});
            }
        }
        return out;
    }

    /** O cristal de pé no engaste (centro a 5 px), com a inclinação e a subida dadas (o pivô é a ponta de baixo). */
    private static Convex crystal(double r, double tiltDeg, double lift) {
        double a = Math.toRadians(tiltDeg);
        double[][] rot = {{Math.cos(a), -Math.sin(a), 0}, {Math.sin(a), Math.cos(a), 0}, {0, 0, 1}};
        double[] pivot = {r, ArmilarSpec.TOPO_BASE + lift, 0};
        double[] centre = {r, ArmilarSpec.CENTRO + lift, 0};
        double[] t = new double[3];
        for (int k = 0; k < 3; k++) {
            // p' = R (p + centre − pivot) + pivot = R p + t, com t = R (centre − pivot) + pivot
            t[k] = rot[k][0] * (centre[0] - pivot[0]) + rot[k][1] * (centre[1] - pivot[1])
                    + rot[k][2] * (centre[2] - pivot[2]) + pivot[k];
        }
        return Convex.of(ArmilarMeshes.crystal(), 0, 18, rot, t);
    }

    @Test
    void cristalDePeNaoAtravessaGarrasNemBase() {
        double r = ArmilarSpec.RAIOS[0];
        Convex c = crystal(r, 0, 0);
        for (double[] claw : claws(r)) {
            assertEquals(0, c.countInside(claw, EPS), "garra");
        }
        double[] base = {r - ArmilarSpec.BASE, ArmilarSpec.RING_HALF_Y, -ArmilarSpec.BASE, r + ArmilarSpec.BASE,
                ArmilarSpec.TOPO_BASE, ArmilarSpec.BASE};
        assertEquals(0, c.countInside(base, EPS), "base");
        // a ponta de baixo encosta no topo da base
        double lowest = ArmilarSpec.CENTRO - ArmilarSpec.CRISTAL_H - ArmilarSpec.CRISTAL_PONTA;
        assertEquals(ArmilarSpec.TOPO_BASE, lowest, 1e-9);
    }

    @Test
    void engasteEncostaNoAnelSemEntrar() {
        // a base sai do topo do anel (y = 1) para cima; as garras saem do topo da base
        assertEquals(ArmilarSpec.RING_HALF_Y, 1.0, 0);
        assertTrue(ArmilarSpec.TOPO_BASE > ArmilarSpec.RING_HALF_Y);
        assertEquals(ArmilarSpec.TOPO_BASE, 1.6, 0);
    }

    @Test
    void a20GrausAtravessa() {
        double r = ArmilarSpec.RAIOS[0];
        Convex c = crystal(r, 20, 0);
        int dentro = 0;
        for (double[] claw : claws(r)) {
            dentro += c.countInside(claw, EPS);
        }
        assertTrue(dentro > 0, "o problema medido: a 20° sem subir, as garras entram no cristal");
    }

    @Test
    void opacoInclinadoNaoAtravessaAsGarras() {
        double r = ArmilarSpec.RAIOS[0];
        for (double sign : new double[] {1, -1}) {
            Convex c = crystal(r, sign * ArmilarSpec.OPACO_TILT_DEG, ArmilarSpec.OPACO_LIFT_PX);
            for (double[] claw : claws(r)) {
                assertEquals(0, c.countInside(claw, EPS), "garra a " + sign * ArmilarSpec.OPACO_TILT_DEG);
            }
        }
    }

    /** O sólido de cada segmento do anel (convexo): topo, base, dentro, fora e as duas tampas pelo eixo. */
    private static List<Convex> ringSolids(int orbit) {
        double r = ArmilarSpec.RAIOS[orbit];
        int n = ArmilarSpec.SEGMENTOS[orbit];
        QuadMesh mesh = ArmilarMeshes.ring(orbit);
        List<Convex> out = new ArrayList<>();
        for (int k = 0; k < n; k++) {
            double a0 = k * 2 * Math.PI / n;
            double a1 = (k + 1) * 2 * Math.PI / n;
            double am = (a0 + a1) / 2;
            double fx = Math.cos(am);
            double fz = -Math.sin(am);
            Convex c = new Convex();
            c.add(0, 1, 0, 0, ArmilarSpec.RING_HALF_Y, 0);
            c.add(0, -1, 0, 0, -ArmilarSpec.RING_HALF_Y, 0);
            // as faces de fora e de dentro são cordas: o plano passa pelos vértices da malha (o quad 4k + 1 e o 4k + 2),
            // não pelo ponto de raio ro sobre a bissetriz, que fica para fora da corda
            c.add(fx, 0, fz, mesh.x(4 * k + 1, 0), 0, mesh.z(4 * k + 1, 0));
            c.add(-fx, 0, -fz, mesh.x(4 * k + 2, 0), 0, mesh.z(4 * k + 2, 0));
            // as tampas passam pelo eixo; fora do segmento é o sentido do ângulo decrescente em a0 e crescente em a1
            c.add(Math.sin(a0), 0, Math.cos(a0), 0, 0, 0);
            c.add(-Math.sin(a1), 0, -Math.cos(a1), 0, 0, 0);
            out.add(c);
        }
        return out;
    }

    @Test
    void pinosNaoEntramNoAnel() {
        for (int orbit = 0; orbit < 3; orbit++) {
            double r = ArmilarSpec.RAIOS[orbit];
            double m = ArmilarSpec.PINO_MEIO;
            double[][] pins = {{r + ArmilarSpec.PINO_DE, -m, -m, r + ArmilarSpec.PINO_ATE, m, m},
                    {-r - ArmilarSpec.PINO_ATE, -m, -m, -r - ArmilarSpec.PINO_DE, m, m}};
            for (Convex segment : ringSolids(orbit)) {
                for (double[] pin : pins) {
                    assertEquals(0, segment.countInside(pin, EPS), "pino da órbita " + orbit);
                }
            }
        }
    }

    @Test
    void segmentosDoHorizonteSoSeTocamNaArestaDeDentro() {
        QuadMesh m = ArmilarMeshes.horizon();
        double l = ArmilarSpec.HORIZ_R_IN * Math.tan(Math.PI / 16);
        int n = ArmilarSpec.HORIZ_SEGS;
        List<Convex> boxes = new ArrayList<>();
        for (int k = 0; k < n; k++) {
            boxes.add(Convex.of(m, 6 * k, 6, Convex.IDENTITY, new double[3]));
        }
        for (int k = 0; k < n; k++) {
            double a = k * 2 * Math.PI / n;
            double c = Math.cos(a);
            double s = Math.sin(a);
            Convex next = boxes.get((k + 1) % n);
            for (double x = ArmilarSpec.HORIZ_R_IN; x <= ArmilarSpec.HORIZ_R_OUT; x += 0.1) {
                for (double z = -l; z <= l; z += 0.1) {
                    // o ponto do segmento k, girado para o mundo (o mesmo giro em y da malha)
                    double wx = x * c + z * s;
                    double wz = -x * s + z * c;
                    assertTrue(!next.strictlyInside(wx, 47.5, wz, EPS), "segmento " + k);
                }
            }
        }
    }

    @Test
    void marcosAssentamNoTopoDoHorizonte() {
        assertEquals(ArmilarSpec.HORIZ_Y1, ArmilarSpec.MARCO_Y0, 0);
        assertTrue(ArmilarSpec.MARCO_X0 >= ArmilarSpec.HORIZ_R_IN);
        assertTrue(ArmilarSpec.MARCO_X1 <= ArmilarSpec.HORIZ_R_OUT);
    }
}
