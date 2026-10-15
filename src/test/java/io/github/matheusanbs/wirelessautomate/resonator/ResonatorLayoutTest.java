package io.github.matheusanbs.wirelessautomate.resonator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import io.github.matheusanbs.wirelessautomate.resonator.ResonatorLayout.Broken;
import io.github.matheusanbs.wirelessautomate.resonator.ResonatorLayout.Face;
import io.github.matheusanbs.wirelessautomate.resonator.ResonatorLayout.Formed;
import io.github.matheusanbs.wirelessautomate.resonator.ResonatorLayout.Member;
import io.github.matheusanbs.wirelessautomate.resonator.ResonatorLayout.Part;
import io.github.matheusanbs.wirelessautomate.resonator.ResonatorLayout.Problem;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ResonatorLayoutTest {
    /** A Vitrine com origem em (10, 20, 30): janela 3×3 ao sul, Núcleo no leste, Portas no oeste e no norte, Sintonizador em cima. */
    private static Map<List<Integer>, Part> vitrine() {
        Map<List<Integer>, Part> w = new HashMap<>();
        for (int x = 0; x < 5; x++) for (int y = 0; y < 5; y++) for (int z = 0; z < 5; z++) {
            boolean dentro = x > 0 && x < 4 && y > 0 && y < 4 && z > 0 && z < 4;
            w.put(List.of(10 + x, 20 + y, 30 + z), dentro ? Part.AIR : Part.CASING);
        }
        for (int x = 1; x < 4; x++) for (int y = 1; y < 4; y++) w.put(List.of(10 + x, 20 + y, 34), Part.GLASS);
        w.put(List.of(14, 22, 32), Part.CORE);
        w.put(List.of(10, 22, 32), Part.ENERGY_PORT);
        w.put(List.of(12, 22, 30), Part.CRYSTAL_PORT);
        w.put(List.of(12, 24, 32), Part.TUNER);
        return w;
    }

    private static ResonatorLayout.Result check(Map<List<Integer>, Part> w) {
        return ResonatorLayout.check(14, 22, 32, (x, y, z) -> w.getOrDefault(List.of(x, y, z), Part.OTHER));
    }

    private static Member at(Formed f, int x, int y, int z) {
        return f.members().stream().filter(m -> m.x() == x && m.y() == y && m.z() == z).findFirst().orElseThrow();
    }

    @Test
    void vitrineMontaComOs98Membros() {
        Formed f = assertInstanceOf(Formed.class, check(vitrine()));
        assertEquals(List.of(10, 20, 30), List.of(f.minX(), f.minY(), f.minZ()));
        assertEquals(98, f.members().size());
        assertEquals("cnn", at(f, 12, 20, 30).casing());
        assertEquals("npn", at(f, 10, 24, 30).casing());
        assertEquals("cpc", at(f, 11, 24, 31).casing());
        assertEquals(Face.EAST, at(f, 14, 22, 32).face());
        assertEquals(Face.SOUTH, at(f, 12, 22, 34).face());
    }

    @Test
    void bitsDoVidroSeguemATabelaDasFaces() {
        Formed f = assertInstanceOf(Formed.class, check(vitrine()));
        assertEquals(15, at(f, 12, 22, 34).glassBits());
        assertEquals(1 | 2, at(f, 11, 21, 34).glassBits());
        assertEquals(4 | 8, at(f, 13, 23, 34).glassBits());
    }

    @Test
    void semPortaDeEnergiaNaoMonta() {
        Map<List<Integer>, Part> w = vitrine();
        w.put(List.of(10, 22, 32), Part.CASING);
        Broken b = assertInstanceOf(Broken.class, check(w));
        assertEquals(Problem.MISSING_ENERGY_PORT, b.problem());
        assertEquals(List.of(14, 22, 32), List.of(b.x(), b.y(), b.z()));
    }

    @Test
    void duasPortasDeCadaMontam() {
        Map<List<Integer>, Part> w = vitrine();
        w.put(List.of(10, 21, 32), Part.ENERGY_PORT);
        w.put(List.of(12, 21, 30), Part.CRYSTAL_PORT);
        assertInstanceOf(Formed.class, check(w));
    }

    @Test
    void doisSintonizadoresNaoMontam() {
        Map<List<Integer>, Part> w = vitrine();
        w.put(List.of(11, 24, 31), Part.TUNER);
        assertEquals(Problem.TOO_MANY_TUNERS, assertInstanceOf(Broken.class, check(w)).problem());
    }

    @Test
    void pecaNaArestaApontaOBlocoErrado() {
        Map<List<Integer>, Part> w = vitrine();
        w.put(List.of(14, 24, 32), Part.GLASS);
        Broken b = assertInstanceOf(Broken.class, check(w));
        assertEquals(Problem.WRONG_BLOCK, b.problem());
        assertEquals(List.of(14, 24, 32), List.of(b.x(), b.y(), b.z()));
    }

    @Test
    void interiorPrecisaEstarVazio() {
        Map<List<Integer>, Part> w = vitrine();
        w.put(List.of(12, 22, 32), Part.OTHER);
        Broken b = assertInstanceOf(Broken.class, check(w));
        assertEquals(Problem.INTERIOR_NOT_EMPTY, b.problem());
        assertEquals(List.of(12, 22, 32), List.of(b.x(), b.y(), b.z()));
    }

    @Test
    void segundoNucleoNaoMonta() {
        Map<List<Integer>, Part> w = vitrine();
        w.put(List.of(11, 22, 30), Part.CORE);
        assertEquals(Problem.EXTRA_CORE, assertInstanceOf(Broken.class, check(w)).problem());
    }

    @Test
    void buracoNaCascaApontaABorda() {
        Map<List<Integer>, Part> w = vitrine();
        w.put(List.of(10, 20, 30), Part.AIR);
        Broken b = assertInstanceOf(Broken.class, check(w));
        assertEquals(Problem.WRONG_BLOCK, b.problem());
        assertEquals(List.of(10, 20, 30), List.of(b.x(), b.y(), b.z()));
    }
}
