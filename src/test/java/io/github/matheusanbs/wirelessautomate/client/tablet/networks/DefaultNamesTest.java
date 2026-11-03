package io.github.matheusanbs.wirelessautomate.client.tablet.networks;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

class DefaultNamesTest {
    @Test
    void firstFreeNameIgnoringCase() {
        assertEquals("Rede", DefaultNames.next("Rede", List.of()));
        assertEquals("Rede 2", DefaultNames.next("Rede", List.of("rede")));
        assertEquals("Rede 4", DefaultNames.next("Rede", List.of("Rede", "Rede 2", "REDE 3")));
    }
}
