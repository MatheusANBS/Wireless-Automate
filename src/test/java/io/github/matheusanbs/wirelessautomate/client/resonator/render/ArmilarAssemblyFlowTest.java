package io.github.matheusanbs.wirelessautomate.client.resonator.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ArmilarAssemblyFlowTest {
    @Test
    void montagemFormadaDaTudoUm() {
        for (int i = 0; i < 3; i++) {
            assertEquals(1.0, ArmilarAssembly.ring(1, i), 1e-12);
        }
        // o filme, com a = 1, deixa os cristais de j >= 4 fora de 1 ({1,0324; 1,0815; 1,075; 0,9407; 0,6065}): é o
        // escalonamento, que o A_MAX completa abaixo
        for (int j = 0; j < 4; j++) {
            assertEquals(1.0, ArmilarAssembly.crystal(1, j), 1e-12);
        }
        assertEquals(1.0324074074, ArmilarAssembly.crystal(1, 4), 1e-9);
        assertEquals(0.9407407407, ArmilarAssembly.crystal(1, 7), 1e-9);
        assertEquals(0.6064814815, ArmilarAssembly.crystal(1, 8), 1e-9);
        assertEquals(1.0, ArmilarAssembly.ignition(1), 1e-12);
    }

    @Test
    void montagemNoComecoDaTudoZero() {
        for (int i = 0; i < 3; i++) {
            assertEquals(0.0, ArmilarAssembly.ring(0, i), 1e-12);
        }
        for (int j = 0; j < 9; j++) {
            assertEquals(0.0, ArmilarAssembly.crystal(0, j), 1e-12);
        }
        assertEquals(0.0, ArmilarAssembly.ignition(0), 1e-12);
    }

    @Test
    void anelEntraComSobressalto() {
        double r = ArmilarAssembly.ring(0.3, 0);
        assertTrue(r > 0 && r < 1.1, "ring(0,3; 0) = " + r);
    }

    @Test
    void duracaoDaMontagem() {
        assertEquals(2.5, ArmilarAssembly.FORM_SECONDS, 0);
        assertEquals(0.0, ArmilarAssembly.a(0, false), 1e-12);
        assertEquals(0.5, ArmilarAssembly.a(1.25, false), 1e-12);
        assertEquals(1.0, ArmilarAssembly.a(2.5, false), 1e-12);
        assertEquals(1.25, ArmilarAssembly.A_MAX, 1e-12);
        assertEquals(ArmilarAssembly.A_MAX, ArmilarAssembly.a(3.125, false), 1e-12);
        assertEquals(ArmilarAssembly.A_MAX, ArmilarAssembly.a(9, false), 1e-12);
        assertEquals(ArmilarAssembly.A_MAX, ArmilarAssembly.a(0, true), 1e-12);
    }

    @Test
    void noFimDaMontagemTudoFicaInteiro() {
        double a = ArmilarAssembly.a(0, true);
        for (int j : new int[] {0, 1, 3, 4, 5, 6, 7, 8}) {
            assertEquals(1.0, ArmilarAssembly.crystal(a, j), 1e-12, "j = " + j);
        }
        for (int i = 0; i < 3; i++) {
            assertEquals(1.0, ArmilarAssembly.ring(a, i), 1e-12);
        }
        assertEquals(1.0, ArmilarAssembly.ignition(a), 1e-12);
        // o escalonamento segue: no meio da entrada o eob passa de 1
        assertTrue(ArmilarAssembly.crystal(0.95, 4) > 1.0);
    }

    @Test
    void vazaoEmFaixas() {
        assertEquals(0.0, ArmilarFlow.strength(0), 1e-12);
        assertEquals(0.45 + 0.55 / 7, ArmilarFlow.strength(1), 1e-12);
        assertEquals(1.0, ArmilarFlow.strength(7), 1e-12);
        double prev = -1;
        for (int k = 0; k <= 70; k++) {
            double s = ArmilarFlow.strength(k / 10.0);
            assertTrue(s > prev, "crescente em " + k / 10.0);
            prev = s;
        }
        // a faixa suavizada decai a 0 por aproximação: o resto minúsculo não pode desenhar o arco
        assertEquals(0.0, ArmilarFlow.strength(7 * Math.pow(0.88, 100)), 0.0);
        assertEquals(0.0, ArmilarFlow.strength(0.01), 0.0);
        assertTrue(ArmilarFlow.strength(0.1) > ArmilarFlow.MIN_STRENGTH);
        assertEquals(0.55, ArmilarFlow.sunMul(0), 1e-12);
        assertEquals(1.0, ArmilarFlow.sunMul(1), 1e-12);
    }
}
