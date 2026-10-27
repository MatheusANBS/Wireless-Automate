package io.github.matheusanbs.wirelessautomate.tv;

import static io.github.matheusanbs.wirelessautomate.tv.TvLinkRule.Action.FULL;
import static io.github.matheusanbs.wirelessautomate.tv.TvLinkRule.Action.LINK;
import static io.github.matheusanbs.wirelessautomate.tv.TvLinkRule.Action.NOT_A_STORAGE;
import static io.github.matheusanbs.wirelessautomate.tv.TvLinkRule.Action.NO_PERMISSION;
import static io.github.matheusanbs.wirelessautomate.tv.TvLinkRule.Action.UNLINK;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class TvLinkRuleTest {
    @Test
    void semPermissaoNadaMuda() {
        assertEquals(NO_PERMISSION, TvLinkRule.decide(false, true, true, 0, 32));
        assertEquals(NO_PERMISSION, TvLinkRule.decide(false, false, true, 0, 32));
    }

    @Test
    void ligadoDesliga() {
        assertEquals(UNLINK, TvLinkRule.decide(true, true, true, 32, 32));
    }

    @Test
    void soArmazenamentoDoModLiga() {
        assertEquals(NOT_A_STORAGE, TvLinkRule.decide(true, false, false, 0, 32));
        assertEquals(LINK, TvLinkRule.decide(true, false, true, 31, 32));
    }

    @Test
    void oTetoRecusa() {
        assertEquals(FULL, TvLinkRule.decide(true, false, true, 32, 32));
    }
}
