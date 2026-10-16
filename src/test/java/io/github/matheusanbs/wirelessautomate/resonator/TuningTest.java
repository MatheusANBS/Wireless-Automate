package io.github.matheusanbs.wirelessautomate.resonator;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class TuningTest {
    @Test
    void eficienciaPelaDistanciaDaEstacao() {
        assertEquals(1.0, Tuning.efficiency(0.0), 1e-9);
        assertEquals(1.0, Tuning.efficiency(-0.25), 1e-9);
        assertEquals(0.95, Tuning.efficiency(0.5), 0.005);
        assertEquals(0.72, Tuning.efficiency(1.0), 0.005);
        assertEquals(0.40, Tuning.efficiency(1.5), 0.005);
        assertEquals(0.0, Tuning.efficiency(2.0), 1e-9);
        assertEquals(0.0, Tuning.efficiency(-7.3), 1e-9);
    }

    @Test
    void derivaTemVelocidadeEEstacaoLimitadas() {
        Tuning.Drift d = Tuning.step(98.0, 0.11, 5.0);
        assertEquals(0.12, d.velocity(), 1e-9);
        assertEquals(98.04, d.station(), 1e-9);
        assertEquals(107.5, Tuning.step(107.49, 0.12, 1.0).station(), 1e-9);
        assertEquals(88.5, Tuning.step(88.51, -0.12, -1.0).station(), 1e-9);
        assertEquals(0.03, Tuning.step(98.0, 0.0, 1.0).velocity(), 1e-9);
    }

    @Test
    void dialAndaEmPassosDe005DentroDaFaixa() {
        assertEquals(97.45, Tuning.clampDial(97.4612), 1e-9);
        assertEquals(88.0, Tuning.clampDial(12.0), 1e-9);
        assertEquals(108.0, Tuning.clampDial(300.0), 1e-9);
    }

    @Test
    void estacaoInicialNaFaixaDaDeriva() {
        assertEquals(88.5, Tuning.initialStation(0.0), 1e-9);
        assertEquals(107.5, Tuning.initialStation(1.0), 1e-9);
    }

    @Test
    void sintonizadorAndaAteMeioMhzPorPasso() {
        assertEquals(90.5, Tuning.autoStep(90.0, 98.0), 1e-9);
        assertEquals(97.5, Tuning.autoStep(98.0, 97.0), 1e-9);
        assertEquals(97.4, Tuning.autoStep(97.0, 97.4), 1e-9);
        assertEquals(97.4, Tuning.autoStep(97.4, 97.4), 1e-9);
        assertEquals(97.45, Tuning.autoStep(97.0, 97.4612), 1e-9);
        assertEquals(108.0, Tuning.autoStep(107.8, 108.3), 1e-9);
    }

    @Test
    void tetoPorTierEEficienciaCapada() {
        assertEquals(0.85, Tuning.ceiling(0), 1e-9);
        assertEquals(0.90, Tuning.ceiling(2), 1e-9);
        assertEquals(0.98, Tuning.ceiling(7), 1e-9);
        assertEquals(0.98, Tuning.ceiling(12), 1e-9);
        assertEquals(1.0, Tuning.capped(1.0, false, 2), 1e-9);
        assertEquals(0.90, Tuning.capped(1.0, true, 2), 1e-9);
        assertEquals(0.72, Tuning.capped(0.72, true, 2), 1e-9);
    }
}
