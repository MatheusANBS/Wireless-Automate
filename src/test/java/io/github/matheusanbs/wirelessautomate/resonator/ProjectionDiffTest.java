package io.github.matheusanbs.wirelessautomate.resonator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import io.github.matheusanbs.wirelessautomate.resonator.ProjectionDiff.Result;
import io.github.matheusanbs.wirelessautomate.resonator.ProjectionDiff.State;
import io.github.matheusanbs.wirelessautomate.resonator.ResonatorLayout.Face;
import io.github.matheusanbs.wirelessautomate.resonator.ResonatorLayout.Part;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ProjectionDiffTest {
    private static final int OX = 10;
    private static final int OY = 20;
    private static final int OZ = 30;
    private static final Part[][][] ARMILAR = ResonatorBlueprint.expected(Face.SOUTH);

    private static Map<List<Integer>, Part> complete() {
        Map<List<Integer>, Part> world = new HashMap<>();
        for (int x = 0; x < 5; x++) {
            for (int y = 0; y < 5; y++) {
                for (int z = 0; z < 5; z++) {
                    world.put(List.of(OX + x, OY + y, OZ + z), ARMILAR[x][y][z]);
                }
            }
        }
        return world;
    }

    private static Result compare(Map<List<Integer>, Part> world) {
        return ProjectionDiff.compare(ARMILAR, OX, OY, OZ, (x, y, z) -> world.getOrDefault(List.of(x, y, z), Part.AIR));
    }

    private static int missingTotal(Result result) {
        return result.missing().values().stream().mapToInt(Integer::intValue).sum();
    }

    @Test
    void mundoSoComONucleoFaltaTudoMenosONucleo() {
        Map<List<Integer>, Part> world = new HashMap<>();
        world.put(List.of(OX + 2, OY, OZ + 4), Part.CORE);
        Result result = compare(world);
        assertEquals(21, result.missing().get(Part.CASING));
        assertEquals(8, result.missing().get(Part.COLUMN));
        assertEquals(2, result.missing().get(Part.CRADLE));
        assertEquals(1, result.missing().get(Part.ENERGY_PORT));
        assertEquals(1, result.missing().get(Part.CRYSTAL_PORT));
        assertEquals(1, result.missing().get(Part.TUNER));
        assertNull(result.missing().get(Part.CORE));
        assertEquals(0, result.wrong());
    }

    @Test
    void armilarCompletaFicaTodaOk() {
        Result result = compare(complete());
        assertEquals(0, result.wrong());
        assertEquals(0, missingTotal(result));
        for (State[][] plane : result.states()) {
            for (State[] row : plane) {
                for (State state : row) {
                    assertEquals(State.OK, state);
                }
            }
        }
    }

    @Test
    void arNaCasaDeUmaColunaContaComoFalta() {
        Map<List<Integer>, Part> world = complete();
        world.put(List.of(OX + 3, OY + 1, OZ + 3), Part.AIR);
        Result result = compare(world);
        assertEquals(State.MISSING, result.states()[3][1][3]);
        assertEquals(1, result.missing().get(Part.COLUMN));
        assertEquals(0, result.wrong());
    }

    @Test
    void lajeNaCasaDoSintonizadorEstaCerta() {
        Map<List<Integer>, Part> world = complete();
        world.put(List.of(OX + 2, OY, OZ), Part.CASING);
        Result result = compare(world);
        assertEquals(State.OK, result.states()[2][0][0]);
        assertNull(result.missing().get(Part.TUNER));
        assertEquals(0, result.wrong());
    }

    @Test
    void lajeNoLugarDeUmaColunaEErrada() {
        Map<List<Integer>, Part> world = complete();
        world.put(List.of(OX + 1, OY + 1, OZ + 1), Part.CASING);
        Result result = compare(world);
        assertEquals(State.WRONG, result.states()[1][1][1]);
        assertEquals(1, result.wrong());
    }

    @Test
    void blocoNoInteriorEErrado() {
        Map<List<Integer>, Part> world = complete();
        world.put(List.of(OX + 2, OY + 3, OZ + 2), Part.OTHER);
        Result result = compare(world);
        assertEquals(State.WRONG, result.states()[2][3][2]);
        assertEquals(1, result.wrong());
    }

    @Test
    void sintonizadorNoLugarDeUmaLajeEErrado() {
        Map<List<Integer>, Part> world = complete();
        world.put(List.of(OX, OY, OZ), Part.TUNER);
        Result result = compare(world);
        assertEquals(State.WRONG, result.states()[0][0][0]);
    }
}
