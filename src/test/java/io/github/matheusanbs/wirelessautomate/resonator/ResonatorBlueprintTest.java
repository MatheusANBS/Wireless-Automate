package io.github.matheusanbs.wirelessautomate.resonator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import io.github.matheusanbs.wirelessautomate.resonator.ResonatorLayout.Face;
import io.github.matheusanbs.wirelessautomate.resonator.ResonatorLayout.Formed;
import io.github.matheusanbs.wirelessautomate.resonator.ResonatorLayout.Member;
import io.github.matheusanbs.wirelessautomate.resonator.ResonatorLayout.Part;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class ResonatorBlueprintTest {
    private static final int CX = 14;
    private static final int CY = 22;
    private static final int CZ = 32;

    /** Roda o {@code ResonatorLayout.check} sobre o desenho, posto na origem que o {@code origin} dá. */
    private static ResonatorLayout.Result check(Face front) {
        Part[][][] expected = ResonatorBlueprint.expected(front);
        int[] o = ResonatorBlueprint.origin(CX, CY, CZ, front);
        return ResonatorLayout.check(CX, CY, CZ, front, (x, y, z) -> {
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
    @EnumSource(value = Face.class, names = {"SOUTH", "WEST", "NORTH", "EAST"})
    void cadaFrenteMontaComONucleoNoLugar(Face front) {
        Formed formed = assertInstanceOf(Formed.class, check(front), front.name());
        assertEquals(front, formed.front());
        Member core = formed.members().stream().filter(m -> m.part() == Part.CORE).findFirst().orElseThrow();
        assertEquals(front, core.face());
        assertEquals(List.of(CX, CY, CZ), List.of(core.x(), core.y(), core.z()));
    }

    @ParameterizedTest
    @EnumSource(value = Face.class, names = {"SOUTH", "WEST", "NORTH", "EAST"})
    void contagensDaArmilar(Face front) {
        Map<Part, Integer> counts = counts(ResonatorBlueprint.expected(front));
        assertEquals(21, counts.get(Part.CASING));
        assertEquals(1, counts.get(Part.CORE));
        assertEquals(1, counts.get(Part.ENERGY_PORT));
        assertEquals(1, counts.get(Part.CRYSTAL_PORT));
        assertEquals(1, counts.get(Part.TUNER));
        assertEquals(8, counts.get(Part.COLUMN));
        assertEquals(2, counts.get(Part.CRADLE));
        assertEquals(90, counts.get(Part.AIR));
    }

    @ParameterizedTest
    @EnumSource(value = Face.class, names = {"SOUTH", "WEST", "NORTH", "EAST"})
    void oNucleoFicaNaCasaDaCaixaIgualANucleoMenosOrigem(Face front) {
        int[] o = ResonatorBlueprint.origin(CX, CY, CZ, front);
        Part[][][] expected = ResonatorBlueprint.expected(front);
        assertEquals(Part.CORE, expected[CX - o[0]][CY - o[1]][CZ - o[2]]);
    }

    @ParameterizedTest
    @EnumSource(value = Face.class, names = {"SOUTH", "WEST", "NORTH", "EAST"})
    void aOrigemEOCantoMinimoDasCasas(Face front) {
        int[] o = ResonatorBlueprint.origin(CX, CY, CZ, front);
        int minX = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        for (int lx = 0; lx < 5; lx += 4) {
            for (int lz = 0; lz < 5; lz += 4) {
                int[] w = ResonatorLayout.world(front, CX, CY, CZ, lx, 0, lz);
                minX = Math.min(minX, w[0]);
                minZ = Math.min(minZ, w[2]);
            }
        }
        assertEquals(List.of(minX, CY, minZ), List.of(o[0], o[1], o[2]));
    }
}
