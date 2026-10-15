package io.github.matheusanbs.wirelessautomate.resonator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import io.github.matheusanbs.wirelessautomate.resonator.ResonatorBlueprint.Layout;
import io.github.matheusanbs.wirelessautomate.resonator.ResonatorLayout.Face;
import io.github.matheusanbs.wirelessautomate.resonator.ResonatorLayout.Formed;
import io.github.matheusanbs.wirelessautomate.resonator.ResonatorLayout.Member;
import io.github.matheusanbs.wirelessautomate.resonator.ResonatorLayout.Part;
import java.util.EnumMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class ResonatorBlueprintTest {
    private static final int CX = 14;
    private static final int CY = 22;
    private static final int CZ = 32;

    /** Roda o {@code ResonatorLayout.check} sobre o desenho, posto na origem que o {@code origin} dá. */
    private static ResonatorLayout.Result check(Face coreFace, Layout layout) {
        Part[][][] expected = ResonatorBlueprint.expected(coreFace, layout);
        int[] o = ResonatorBlueprint.origin(CX, CY, CZ, coreFace);
        return ResonatorLayout.check(CX, CY, CZ, (x, y, z) -> {
            int lx = x - o[0];
            int ly = y - o[1];
            int lz = z - o[2];
            boolean inside = lx >= 0 && lx < 5 && ly >= 0 && ly < 5 && lz >= 0 && lz < 5;
            return inside ? expected[lx][ly][lz] : Part.OTHER;
        });
    }

    private static Map<Part, Integer> counts(Part[][][] parts) {
        Map<Part, Integer> counts = new EnumMap<>(Part.class);
        for (Part[][] plane : parts) {
            for (Part[] row : plane) {
                for (Part part : row) {
                    counts.merge(part, 1, Integer::sum);
                }
            }
        }
        return counts;
    }

    @ParameterizedTest
    @EnumSource(Face.class)
    void cadaFaceECadaLayoutMontamComONucleoNaFace(Face coreFace) {
        for (Layout layout : Layout.values()) {
            Formed formed = assertInstanceOf(Formed.class, check(coreFace, layout), coreFace + " " + layout);
            Member core = formed.members().stream().filter(m -> m.part() == Part.CORE).findFirst().orElseThrow();
            assertEquals(coreFace, core.face(), layout.name());
            assertEquals(CX, core.x());
            assertEquals(CY, core.y());
            assertEquals(CZ, core.z());
        }
    }

    @ParameterizedTest
    @EnumSource(Face.class)
    void contagensDaVitrine(Face coreFace) {
        Map<Part, Integer> counts = counts(ResonatorBlueprint.expected(coreFace, Layout.VITRINE));
        assertEquals(85, counts.get(Part.CASING));
        assertEquals(9, counts.get(Part.GLASS));
        assertEquals(27, counts.get(Part.AIR));
        for (Part part : new Part[] {Part.CORE, Part.ENERGY_PORT, Part.CRYSTAL_PORT, Part.TUNER}) {
            assertEquals(1, counts.get(part), part.name());
        }
    }

    @ParameterizedTest
    @EnumSource(Face.class)
    void contagensDaFaixa(Face coreFace) {
        Map<Part, Integer> counts = counts(ResonatorBlueprint.expected(coreFace, Layout.FAIXA));
        assertEquals(82, counts.get(Part.CASING));
        assertEquals(12, counts.get(Part.GLASS));
        for (Part part : new Part[] {Part.CORE, Part.ENERGY_PORT, Part.CRYSTAL_PORT, Part.TUNER}) {
            assertEquals(1, counts.get(part), part.name());
        }
    }

    /** A Vitrine do {@code ResonatorLayoutTest}: origem (10, 20, 30) e Núcleo em (14, 22, 32), virado para o leste. */
    @Test
    void lesteNaVitrineEAVitrineDoTesteDoLayout() {
        assertEquals(java.util.List.of(10, 20, 30),
                java.util.Arrays.stream(ResonatorBlueprint.origin(14, 22, 32, Face.EAST)).boxed().toList());
        Part[][][] expected = ResonatorBlueprint.expected(Face.EAST, Layout.VITRINE);
        for (int x = 0; x < 5; x++) {
            for (int y = 0; y < 5; y++) {
                for (int z = 0; z < 5; z++) {
                    boolean inside = x > 0 && x < 4 && y > 0 && y < 4 && z > 0 && z < 4;
                    Part want = inside ? Part.AIR : Part.CASING;
                    if (z == 4 && x >= 1 && x <= 3 && y >= 1 && y <= 3) {
                        want = Part.GLASS;
                    }
                    if (x == 4 && y == 2 && z == 2) {
                        want = Part.CORE;
                    } else if (x == 0 && y == 2 && z == 2) {
                        want = Part.ENERGY_PORT;
                    } else if (x == 2 && y == 2 && z == 0) {
                        want = Part.CRYSTAL_PORT;
                    } else if (x == 2 && y == 4 && z == 2) {
                        want = Part.TUNER;
                    }
                    assertEquals(want, expected[x][y][z], x + "," + y + "," + z);
                }
            }
        }
    }

    /** Gira com a face: de NORTE, a Porta de Cristais fica a OESTE e o vidro a LESTE. */
    @Test
    void norteGiraAsPecas() {
        Part[][][] expected = ResonatorBlueprint.expected(Face.NORTH, Layout.VITRINE);
        assertEquals(Part.CORE, expected[2][2][0]);
        assertEquals(Part.ENERGY_PORT, expected[2][2][4]);
        assertEquals(Part.CRYSTAL_PORT, expected[0][2][2]);
        assertEquals(Part.TUNER, expected[2][4][2]);
        assertEquals(Part.GLASS, expected[4][2][2]);
    }

    @Test
    void cimaPoeAsPecasNasFacesFixas() {
        Part[][][] expected = ResonatorBlueprint.expected(Face.UP, Layout.VITRINE);
        assertEquals(Part.CORE, expected[2][4][2]);
        assertEquals(Part.ENERGY_PORT, expected[2][0][2]);
        assertEquals(Part.CRYSTAL_PORT, expected[2][2][0]);
        assertEquals(Part.TUNER, expected[4][2][2]);
        assertEquals(Part.GLASS, expected[2][2][4]);
    }

    @Test
    void faixaFicaNaLinhaDeCimaDasFacesLaterais() {
        Part[][][] expected = ResonatorBlueprint.expected(Face.EAST, Layout.FAIXA);
        for (int c = 1; c <= 3; c++) {
            assertEquals(Part.GLASS, expected[c][3][0]);
            assertEquals(Part.GLASS, expected[c][3][4]);
            assertEquals(Part.GLASS, expected[0][3][c]);
            assertEquals(Part.GLASS, expected[4][3][c]);
            assertEquals(Part.CASING, expected[c][2][4]);
        }
    }
}
