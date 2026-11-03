package io.github.matheusanbs.wirelessautomate.client.tablet.networks;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class GroupIconTest {
    @Test
    void chosenThenMachineThenTablet() {
        assertEquals("picareta", GroupIcon.pick("picareta", "fornalha", "tablet"));
        assertEquals("fornalha", GroupIcon.pick(null, "fornalha", "tablet"));
        assertEquals("tablet", GroupIcon.pick(null, null, "tablet"));
        assertEquals("picareta", GroupIcon.pick("picareta", null, "tablet"));
    }
}
