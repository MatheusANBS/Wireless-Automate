package io.github.matheusanbs.wirelessautomate.client.router;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.matheusanbs.wirelessautomate.network.PortMode;
import org.junit.jupiter.api.Test;

class ModeCycleTest {
    @Test
    void nextGoesExtractInsertStorageNoneAndWraps() {
        assertEquals(PortMode.INSERT, ModeCycle.next(PortMode.EXTRACT));
        assertEquals(PortMode.BOTH, ModeCycle.next(PortMode.INSERT));
        assertEquals(PortMode.NONE, ModeCycle.next(PortMode.BOTH));
        assertEquals(PortMode.EXTRACT, ModeCycle.next(PortMode.NONE));
    }

    @Test
    void previousIsTheOppositeWalk() {
        for (PortMode mode : PortMode.values()) {
            assertEquals(mode, ModeCycle.previous(ModeCycle.next(mode)));
        }
        assertEquals(PortMode.NONE, ModeCycle.previous(PortMode.EXTRACT));
    }
}
