package io.github.matheusanbs.wirelessautomate.resonator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class CrystalIntegrityTest {
    @Test
    void recargaGastaUm() {
        assertEquals(7, CrystalIntegrity.spend(8));
        assertEquals(0, CrystalIntegrity.spend(1));
        assertEquals(0, CrystalIntegrity.spend(0));
    }

    @Test
    void rachaComZero() {
        assertTrue(CrystalIntegrity.isCracked(0));
        assertFalse(CrystalIntegrity.isCracked(1));
    }

    @Test
    void limitaAoMaximo() {
        assertEquals(8, CrystalIntegrity.clamp(99, 8));
        assertEquals(0, CrystalIntegrity.clamp(-3, 8));
        assertEquals(5, CrystalIntegrity.clamp(5, 8));
    }
}
