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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

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

    private static final int[] ORIGEM = {10, 20, 30};

    /** Direita e cima de cada face, escritos à mão (a tabela do brief), na ordem do enum {@code Face}. */
    private static int[][] tabela(Face f) {
        return switch (f) {
            case NORTH -> new int[][] {{-1, 0, 0}, {0, 1, 0}};
            case SOUTH -> new int[][] {{1, 0, 0}, {0, 1, 0}};
            case WEST -> new int[][] {{0, 0, 1}, {0, 1, 0}};
            case EAST -> new int[][] {{0, 0, -1}, {0, 1, 0}};
            case UP -> new int[][] {{1, 0, 0}, {0, 0, -1}};
            case DOWN -> new int[][] {{1, 0, 0}, {0, 0, 1}};
        };
    }

    private static List<Integer> centro(Face f) {
        return List.of(ORIGEM[0] + 2 + 2 * f.dx(), ORIGEM[1] + 2 + 2 * f.dy(), ORIGEM[2] + 2 + 2 * f.dz());
    }

    private static List<Integer> somar(List<Integer> p, int[] d, int k) {
        return List.of(p.get(0) + k * d[0], p.get(1) + k * d[1], p.get(2) + k * d[2]);
    }

    @ParameterizedTest
    @EnumSource(Face.class)
    void nucleoEmQualquerFaceMontaComVidroNaOposta(Face faceDoNucleo) {
        Face oposta = Face.values()[faceDoNucleo.ordinal() ^ 1];
        Map<List<Integer>, Part> w = new HashMap<>();
        for (int x = 0; x < 5; x++) for (int y = 0; y < 5; y++) for (int z = 0; z < 5; z++) {
            boolean dentro = x > 0 && x < 4 && y > 0 && y < 4 && z > 0 && z < 4;
            w.put(List.of(10 + x, 20 + y, 30 + z), dentro ? Part.AIR : Part.CASING);
        }
        int[][] t = tabela(oposta);
        List<Integer> c = centro(oposta);
        for (int r = -1; r <= 1; r++) for (int u = -1; u <= 1; u++) {
            w.put(somar(somar(c, t[0], r), t[1], u), Part.GLASS);
        }
        List<Integer> nucleo = centro(faceDoNucleo);
        w.put(nucleo, Part.CORE);
        List<Face> outras = new java.util.ArrayList<>();
        for (Face f : Face.values()) if (f != faceDoNucleo && f != oposta) outras.add(f);
        w.put(centro(outras.get(0)), Part.ENERGY_PORT);
        w.put(centro(outras.get(1)), Part.CRYSTAL_PORT);

        Formed f = assertInstanceOf(Formed.class, ResonatorLayout.check(nucleo.get(0), nucleo.get(1), nucleo.get(2),
                (x, y, z) -> w.getOrDefault(List.of(x, y, z), Part.OTHER)));
        assertEquals(List.of(10, 20, 30), List.of(f.minX(), f.minY(), f.minZ()));
        assertEquals(98, f.members().size());
        assertEquals(faceDoNucleo, at(f, nucleo.get(0), nucleo.get(1), nucleo.get(2)).face());
        assertEquals(oposta, at(f, c.get(0), c.get(1), c.get(2)).face());
        assertEquals(15, at(f, c.get(0), c.get(1), c.get(2)).glassBits());
        int[][] cantos = {{1, 1, 4 | 8}, {-1, 1, 4 | 2}, {1, -1, 1 | 8}, {-1, -1, 1 | 2}};
        for (int[] k : cantos) {
            List<Integer> p = somar(somar(c, t[0], k[0]), t[1], k[1]);
            assertEquals(k[2], at(f, p.get(0), p.get(1), p.get(2)).glassBits(), oposta + " " + k[0] + "," + k[1]);
        }
    }

    @Test
    void relataACaixaComMenosErrosEPrimeiroProblemaDela() {
        Map<List<Integer>, Part> w = vitrine();
        w.put(List.of(14, 20, 34), Part.AIR);
        Broken um = assertInstanceOf(Broken.class, check(w));
        assertEquals(Problem.WRONG_BLOCK, um.problem());
        assertEquals(List.of(14, 20, 34), List.of(um.x(), um.y(), um.z()));

        w.put(List.of(10, 20, 34), Part.AIR);
        Broken dois = assertInstanceOf(Broken.class, check(w));
        assertEquals(Problem.WRONG_BLOCK, dois.problem());
        assertEquals(List.of(10, 20, 34), List.of(dois.x(), dois.y(), dois.z()));
    }

    @Test
    void semPortaDeCristaisEPrecedenciaDaEnergia() {
        Map<List<Integer>, Part> w = vitrine();
        w.put(List.of(12, 22, 30), Part.CASING);
        Broken b = assertInstanceOf(Broken.class, check(w));
        assertEquals(Problem.MISSING_CRYSTAL_PORT, b.problem());
        assertEquals(List.of(14, 22, 32), List.of(b.x(), b.y(), b.z()));

        w.put(List.of(10, 22, 32), Part.CASING);
        assertEquals(Problem.MISSING_ENERGY_PORT, assertInstanceOf(Broken.class, check(w)).problem());
    }

    @Test
    void posicaoDoSegundoSintonizadorEDoNucleoExtra() {
        Map<List<Integer>, Part> w = vitrine();
        w.put(List.of(11, 24, 31), Part.TUNER);
        Broken t = assertInstanceOf(Broken.class, check(w));
        assertEquals(Problem.TOO_MANY_TUNERS, t.problem());
        assertEquals(List.of(12, 24, 32), List.of(t.x(), t.y(), t.z()));

        Map<List<Integer>, Part> v = vitrine();
        v.put(List.of(11, 22, 30), Part.CORE);
        Broken n = assertInstanceOf(Broken.class, check(v));
        assertEquals(Problem.EXTRA_CORE, n.problem());
        assertEquals(List.of(11, 22, 30), List.of(n.x(), n.y(), n.z()));
    }
}
