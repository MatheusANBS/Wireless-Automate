package io.github.matheusanbs.wirelessautomate.resonator;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.matheusanbs.wirelessautomate.resonator.ResonatorBlueprint.Layout;
import io.github.matheusanbs.wirelessautomate.resonator.ResonatorLayout.Face;
import io.github.matheusanbs.wirelessautomate.resonator.ResonatorLayout.Part;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.junit.jupiter.api.Test;

/** Tentar primeiro a caixa da frente do Núcleo não muda o resultado: a mesma caixa, os mesmos membros, o mesmo problema. */
class ResonatorLayoutPreferredTest {
    private static final int CX = 40;
    private static final int CY = 60;
    private static final int CZ = 80;

    private static Map<List<Integer>, Part> structure(Face coreFace, Layout layout) {
        Map<List<Integer>, Part> world = new HashMap<>();
        Part[][][] expected = ResonatorBlueprint.expected(coreFace, layout);
        int[] o = ResonatorBlueprint.origin(CX, CY, CZ, coreFace);
        for (int x = 0; x < 5; x++) {
            for (int y = 0; y < 5; y++) {
                for (int z = 0; z < 5; z++) {
                    world.put(List.of(o[0] + x, o[1] + y, o[2] + z), expected[x][y][z]);
                }
            }
        }
        return world;
    }

    private static void assertSameForEveryFace(Map<List<Integer>, Part> world) {
        ResonatorLayout.Probe probe = (x, y, z) -> world.getOrDefault(List.of(x, y, z), Part.OTHER);
        ResonatorLayout.Result plain = ResonatorLayout.check(CX, CY, CZ, probe);
        for (Face preferred : Face.values()) {
            assertEquals(plain, ResonatorLayout.check(CX, CY, CZ, preferred, probe), "frente " + preferred);
        }
    }

    @Test
    void estruturaInteiraDaMesmaCaixa() {
        for (Face face : Face.values()) {
            for (Layout layout : Layout.values()) {
                assertSameForEveryFace(structure(face, layout));
            }
        }
    }

    @Test
    void estruturaComDefeitosDaOMesmoProblema() {
        Random random = new Random(15_10_2026L);
        Part[] parts = Part.values();
        for (int round = 0; round < 400; round++) {
            Face face = Face.values()[random.nextInt(6)];
            Map<List<Integer>, Part> world = structure(face, random.nextBoolean() ? Layout.VITRINE : Layout.FAIXA);
            int defects = 1 + random.nextInt(4);
            for (int i = 0; i < defects; i++) {
                List<Integer> pos = List.of(CX - 4 + random.nextInt(9), CY - 4 + random.nextInt(9),
                        CZ - 4 + random.nextInt(9));
                if (!pos.equals(List.of(CX, CY, CZ))) {
                    world.put(pos, parts[random.nextInt(parts.length)]);
                }
            }
            assertSameForEveryFace(world);
        }
    }
}
