package io.github.matheusanbs.wirelessautomate.client.resonator.screen;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class PendingDialTest {
    @Test
    void semCliquePendenteValeODoServidor() {
        PendingDial dial = new PendingDial();
        assertEquals(94.0, dial.base(94.0, 0), 1e-9);
    }

    @Test
    void cliquesSeguidosSomamMesmoComEstadoIntermediario() {
        PendingDial dial = new PendingDial();
        dial.sent(94.05, 0);
        dial.sent(dial.base(94.0, 10) + 0.05, 10);
        // chega o estado do primeiro clique, o segundo ainda não
        assertEquals(94.10, dial.base(94.05, 20), 1e-9);
        dial.sent(dial.base(94.05, 20) + 0.05, 20);
        assertEquals(94.15, dial.base(94.10, 30), 1e-9);
    }

    @Test
    void oServidorConfirmaELimpa() {
        PendingDial dial = new PendingDial();
        dial.sent(94.10, 0);
        assertEquals(94.10, dial.base(94.10, 10), 1e-9);
        // depois de confirmado, vale o do servidor (o Sintonizador ou outro jogador o mexeu)
        assertEquals(95.0, dial.base(95.0, 20), 1e-9);
    }

    @Test
    void oPendenteSemConfirmacaoExpira() {
        PendingDial dial = new PendingDial();
        dial.sent(94.10, 0);
        assertEquals(94.10, dial.base(94.0, PendingDial.TIMEOUT_MS), 1e-9);
        assertEquals(94.0, dial.base(94.0, PendingDial.TIMEOUT_MS + 1), 1e-9);
    }
}
