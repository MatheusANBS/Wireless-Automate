package io.github.matheusanbs.wirelessautomate.tv;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class TvRateGateTest {
    @Test
    void dezPorSegundoEDepoisRecusa() {
        TvRateGate gate = new TvRateGate();
        UUID id = UUID.randomUUID();
        for (int i = 0; i < TvRateGate.PER_SECOND; i++) {
            assertTrue(gate.allow(id, 100));
        }
        assertFalse(gate.allow(id, 100));
        assertFalse(gate.allow(id, 119));
    }

    @Test
    void aJanelaRecomecaEOutroJogadorNaoContaJunto() {
        TvRateGate gate = new TvRateGate();
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        for (int i = 0; i < TvRateGate.PER_SECOND; i++) {
            gate.allow(a, 0);
        }
        assertTrue(gate.allow(b, 0));
        assertTrue(gate.allow(a, 20));
        gate.forget(a);
        assertTrue(gate.allow(a, 20));
    }

    @Test
    void relogioQueVoltaRecomeca() {
        TvRateGate gate = new TvRateGate();
        UUID id = UUID.randomUUID();
        for (int i = 0; i < TvRateGate.PER_SECOND; i++) {
            gate.allow(id, 5000);
        }
        assertTrue(gate.allow(id, 10));
    }
}
