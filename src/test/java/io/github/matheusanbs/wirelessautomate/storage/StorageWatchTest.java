package io.github.matheusanbs.wirelessautomate.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class StorageWatchTest {
    private static ScalarStore store() {
        return new ScalarStore(() -> { }, () -> 1_000);
    }

    @Test
    void semVigiaNaoHaContador() {
        ScalarStore s = store();
        s.insert(100, false);
        assertNull(s.watch());
    }

    @Test
    void contaSoOQueEntraESaiDeVerdade() {
        ScalarStore s = store();
        StorageWatch watch = s.startWatch();
        s.insert(300, true);
        s.insert(300, false);
        s.extract(100, true);
        s.extract(100, false);
        s.insert(5_000, false);
        assertEquals(300 + 800, watch.totalIn());
        assertEquals(100, watch.totalOut());
    }

    @Test
    void oReplaceContaADiferenca() {
        ScalarStore s = store();
        StorageWatch watch = s.startWatch();
        s.replace(400);
        s.replace(150);
        assertEquals(400, watch.totalIn());
        assertEquals(250, watch.totalOut());
    }

    @Test
    void pararAVigiaLargaOsContadores() {
        ScalarStore s = store();
        s.startWatch();
        s.stopWatch();
        s.insert(10, false);
        assertNull(s.watch());
        assertEquals(0, s.startWatch().totalIn());
    }

    @Test
    void aTaxaTotalVemDaJanela() {
        ScalarStore s = store();
        StorageWatch watch = s.startWatch();
        watch.sample(0);
        s.insert(200, false);
        watch.sample(20);
        assertEquals(200, watch.inPerSecond(null), 1e-9);
        assertEquals(0, watch.outPerSecond(null), 1e-9);
    }
}
