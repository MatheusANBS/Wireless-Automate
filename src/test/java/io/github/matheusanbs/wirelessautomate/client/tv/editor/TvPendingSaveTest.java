package io.github.matheusanbs.wirelessautomate.client.tv.editor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class TvPendingSaveTest {
    @Test
    void semPendenteNadaAcontece() {
        TvPendingSave<String> pending = new TvPendingSave<>();
        assertFalse(pending.pending());
        assertFalse(pending.tick());
        assertNull(pending.release());
    }

    @Test
    void aRespostaSoltaOPendente() {
        TvPendingSave<String> pending = new TvPendingSave<>();
        pending.begin("a");
        assertTrue(pending.pending());
        assertEquals("a", pending.release());
        assertFalse(pending.pending());
    }

    @Test
    void semRespostaOPrazoSolta() {
        TvPendingSave<String> pending = new TvPendingSave<>();
        pending.begin("a");
        for (int i = 0; i < TvPendingSave.TIMEOUT_TICKS - 1; i++) {
            assertFalse(pending.tick());
        }
        assertTrue(pending.tick());
        assertFalse(pending.pending());
        assertFalse(pending.tick());
    }

    @Test
    void umSalvarNovoRecomecaAContagem() {
        TvPendingSave<String> pending = new TvPendingSave<>();
        pending.begin("a");
        for (int i = 0; i < TvPendingSave.TIMEOUT_TICKS - 5; i++) {
            pending.tick();
        }
        pending.begin("b");
        for (int i = 0; i < TvPendingSave.TIMEOUT_TICKS - 1; i++) {
            assertFalse(pending.tick());
        }
        assertEquals("b", pending.sent());
    }
}
