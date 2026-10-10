package io.github.matheusanbs.wirelessautomate.net;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

class PacketPartsTest {
    private static byte[] data(int size) {
        byte[] data = new byte[size];
        new Random(size).nextBytes(data);
        return data;
    }

    @Test
    void splitsInPartsOfAtMostTheSize() {
        byte[] data = data(10_001);
        List<PacketParts.Part> parts = PacketParts.split(data, 1000);
        assertEquals(11, parts.size());
        for (int i = 0; i < parts.size(); i++) {
            PacketParts.Part part = parts.get(i);
            assertEquals(10_001, part.total());
            assertEquals(i * 1000, part.offset());
            assertTrue(part.bytes().length <= 1000);
            assertEquals(i == parts.size() - 1, part.last());
        }
        assertEquals(1, parts.get(10).bytes().length);
    }

    @Test
    void exactMultipleHasNoEmptyPart() {
        assertEquals(4, PacketParts.split(data(4000), 1000).size());
        assertEquals(1, PacketParts.split(data(1), 1000).size());
    }

    @Test
    void assemblesInOrder() {
        byte[] data = data(70_000);
        PacketParts.Assembly assembly = new PacketParts.Assembly(100_000);
        List<PacketParts.Part> parts = PacketParts.split(data, 32_000);
        assertNull(assembly.accept(parts.get(0)));
        assertTrue(assembly.pending());
        assertNull(assembly.accept(parts.get(1)));
        assertArrayEquals(data, assembly.accept(parts.get(2)));
        assertFalse(assembly.pending());
        // A mesma montagem recebe o pacote seguinte.
        byte[] next = data(5);
        assertArrayEquals(next, assembly.accept(PacketParts.split(next, 32_000).get(0)));
    }

    @Test
    void newFirstPartDiscardsTheIncompleteOne() {
        PacketParts.Assembly assembly = new PacketParts.Assembly(100_000);
        assertNull(assembly.accept(PacketParts.split(data(3000), 1000).get(0)));
        byte[] other = data(2000);
        List<PacketParts.Part> parts = PacketParts.split(other, 1000);
        assertNull(assembly.accept(parts.get(0)));
        assertArrayEquals(other, assembly.accept(parts.get(1)));
    }

    @Test
    void refusesOutOfOrderAndRecovers() {
        PacketParts.Assembly assembly = new PacketParts.Assembly(100_000);
        List<PacketParts.Part> parts = PacketParts.split(data(3000), 1000);
        assertThrows(IllegalStateException.class, () -> assembly.accept(parts.get(1)));
        assertNull(assembly.accept(parts.get(0)));
        assertThrows(IllegalStateException.class, () -> assembly.accept(parts.get(2)));
        assertFalse(assembly.pending());
        // Parte de outro pacote (total diferente) no meio.
        assertNull(assembly.accept(parts.get(0)));
        assertThrows(IllegalStateException.class,
                () -> assembly.accept(PacketParts.split(data(5000), 1000).get(1)));
        byte[] again = data(3000);
        List<PacketParts.Part> fresh = PacketParts.split(again, 1000);
        assembly.accept(fresh.get(0));
        assembly.accept(fresh.get(1));
        assertArrayEquals(again, assembly.accept(fresh.get(2)));
    }

    @Test
    void refusesAboveTheCeiling() {
        PacketParts.Assembly assembly = new PacketParts.Assembly(2000);
        assertThrows(IllegalStateException.class, () -> assembly.accept(PacketParts.split(data(2001), 1000).get(0)));
        assertFalse(assembly.pending());
        byte[] fits = data(2000);
        List<PacketParts.Part> parts = PacketParts.split(fits, 1000);
        assembly.accept(parts.get(0));
        assertArrayEquals(fits, assembly.accept(parts.get(1)));
    }

    @Test
    void invalidPartsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> new PacketParts.Part(10, 5, new byte[6]));
        assertThrows(IllegalArgumentException.class, () -> new PacketParts.Part(10, -1, new byte[1]));
        assertThrows(IllegalArgumentException.class, () -> new PacketParts.Part(10, 0, new byte[0]));
        assertThrows(IllegalArgumentException.class, () -> PacketParts.split(new byte[0], 10));
    }
}
