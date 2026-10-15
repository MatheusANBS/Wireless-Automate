package io.github.matheusanbs.wirelessautomate.network;

import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.matheusanbs.wirelessautomate.range.RemoteSide;
import org.junit.jupiter.api.Test;

/** Os modos das faces do índice ({@code NodeIndex.faceModes}): 2 bits por tipo e lado, num {@code long}. */
class FaceModeBitsTest {
    @Test
    void everyModeFitsInTwoBits() {
        assertTrue(PortMode.values().length <= 4, PortMode.values().length + " modos não cabem em 2 bits");
    }

    @Test
    void everyTypeAndSideFitsInALong() {
        int bits = ResourceType.values().length * RemoteSide.values().length * 2;
        assertTrue(bits <= Long.SIZE, bits + " bits não cabem num long");
    }
}
