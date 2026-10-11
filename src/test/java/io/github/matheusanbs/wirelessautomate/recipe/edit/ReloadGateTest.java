package io.github.matheusanbs.wirelessautomate.recipe.edit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ReloadGateTest {
    @Test
    void oneReloadAtATime() {
        ReloadGate gate = new ReloadGate();
        gate.changed();
        assertTrue(gate.tryStart());
        assertFalse(gate.tryStart());
        assertFalse(gate.reloadOnClose());
        gate.finish(true);
        assertTrue(gate.tryStart());
    }

    @Test
    void pendingLeavesOnStartAndComesBackOnFailure() {
        ReloadGate gate = new ReloadGate();
        gate.changed();
        gate.changed();
        assertTrue(gate.tryStart());
        assertEquals(0, gate.pending());
        gate.finish(false);
        assertEquals(2, gate.pending());
    }

    @Test
    void saveDuringReloadStaysPending() {
        ReloadGate gate = new ReloadGate();
        gate.changed();
        gate.tryStart();
        gate.changed();
        gate.serverReloaded();
        gate.finish(true);
        assertEquals(1, gate.pending());
        assertTrue(gate.reloadOnClose());
    }

    @Test
    void closingDoesNotRetryAfterFailure() {
        ReloadGate gate = new ReloadGate();
        gate.changed();
        gate.tryStart();
        gate.finish(false);
        assertFalse(gate.reloadOnClose());
        assertTrue(gate.tryStart(), "o botão ainda tenta");
        gate.finish(false);
        gate.changed();
        assertTrue(gate.reloadOnClose(), "uma gravação nova volta a recarregar ao fechar");
    }

    @Test
    void outsideReloadClearsEverything() {
        ReloadGate gate = new ReloadGate();
        gate.changed();
        gate.tryStart();
        gate.finish(false);
        gate.serverReloaded();
        assertEquals(0, gate.pending());
        assertFalse(gate.reloadOnClose());
    }

    @Test
    void nothingToReloadOnClose() {
        assertFalse(new ReloadGate().reloadOnClose());
    }
}
