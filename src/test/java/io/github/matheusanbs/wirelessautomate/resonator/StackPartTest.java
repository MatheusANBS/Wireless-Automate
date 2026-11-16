package io.github.matheusanbs.wirelessautomate.resonator;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class StackPartTest {
    @Test
    void nomesDoEstado() {
        assertEquals("lower", StackPart.LOWER.getSerializedName());
        assertEquals(StackPart.UPPER, StackPart.byName("upper"));
        assertEquals(StackPart.LOOSE, StackPart.byName("nnn"));
        assertEquals("nw", Corner.NW.getSerializedName());
        assertEquals(Corner.NE, Corner.byName("ne"));
        assertEquals(Corner.SE, Corner.byName("?"));
    }
}
