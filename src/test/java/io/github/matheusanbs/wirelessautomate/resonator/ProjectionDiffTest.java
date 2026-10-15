package io.github.matheusanbs.wirelessautomate.resonator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import io.github.matheusanbs.wirelessautomate.resonator.ProjectionDiff.Result;
import io.github.matheusanbs.wirelessautomate.resonator.ProjectionDiff.State;
import io.github.matheusanbs.wirelessautomate.resonator.ResonatorBlueprint.Layout;
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
    private static final Part[][][] VITRINE = ResonatorBlueprint.expected(Face.EAST, Layout.VITRINE);

    /** Só o Núcleo, em (14, 22, 32); o resto é ar. */
    private static Map<List<Integer>, Part> onlyCore() {
        Map<List<Integer>, Part> world = new HashMap<>();
        world.put(List.of(14, 22, 32), Part.CORE);
        return world;
    }

    private static Map<List<Integer>, Part> complete() {
        Map<List<Integer>, Part> world = new HashMap<>();
        for (int x = 0; x < 5; x++) {
            for (int y = 0; y < 5; y++) {
                for (int z = 0; z < 5; z++) {
                    world.put(List.of(OX + x, OY + y, OZ + z), VITRINE[x][y][z]);
                }
            }
        }
        return world;
    }

    private static Result compare(Map<List<Integer>, Part> world) {
        return ProjectionDiff.compare(VITRINE, OX, OY, OZ, (x, y, z) -> world.getOrDefault(List.of(x, y, z), Part.AIR));
    }

    @Test
    void mundoVazioFaltaTudoMenosONucleo() {
        Result result = compare(onlyCore());
        assertEquals(85, result.missing().get(Part.CASING));
        assertEquals(9, result.missing().get(Part.GLASS));
        assertEquals(1, result.missing().get(Part.ENERGY_PORT));
        assertEquals(1, result.missing().get(Part.CRYSTAL_PORT));
        assertEquals(1, result.missing().get(Part.TUNER));
        assertNull(result.missing().get(Part.CORE));
        assertEquals(0, result.wrong());
        for (int x = 0; x < 5; x++) {
            for (int y = 0; y < 5; y++) {
                for (int z = 0; z < 5; z++) {
                    State want = VITRINE[x][y][z] == Part.AIR || VITRINE[x][y][z] == Part.CORE ? State.OK : State.MISSING;
                    assertEquals(want, result.states()[x][y][z], x + "," + y + "," + z);
                }
            }
        }
    }

    @Test
    void vitrineCompletaFicaTodaOk() {
        Result result = compare(complete());
        assertEquals(0, result.wrong());
        assertEquals(0, result.missing().values().stream().mapToInt(Integer::intValue).sum());
        for (State[][] plane : result.states()) {
            for (State[] row : plane) {
                for (State state : row) {
                    assertEquals(State.OK, state);
                }
            }
        }
    }

    @Test
    void terraNaArestaEErrada() {
        Map<List<Integer>, Part> world = complete();
        world.put(List.of(14, 21, 34), Part.OTHER);
        Result result = compare(world);
        assertEquals(State.WRONG, result.states()[4][1][4]);
        assertEquals(1, result.wrong());
        assertEquals(0, result.missing().values().stream().mapToInt(Integer::intValue).sum());
    }

    @Test
    void terraNoInteriorEErrada() {
        Map<List<Integer>, Part> world = complete();
        world.put(List.of(12, 22, 32), Part.OTHER);
        Result result = compare(world);
        assertEquals(State.WRONG, result.states()[2][2][2]);
        assertEquals(1, result.wrong());
    }

    @Test
    void vidroOndeDeviaHaverCarcacaNaArestaEErrado() {
        Map<List<Integer>, Part> world = complete();
        world.put(List.of(10, 24, 32), Part.GLASS);
        Result result = compare(world);
        assertEquals(State.WRONG, result.states()[0][4][2]);
        assertEquals(1, result.wrong());
    }

    /** No miolo das faces Carcaça e Vidro se trocam: a montagem aceita os dois, e o que já foi posto vale nos dois layouts. */
    @Test
    void carcacaEVidroSeTrocamNoMioloDasFaces() {
        Map<List<Integer>, Part> world = complete();
        world.put(List.of(12, 22, 34), Part.CASING);
        world.put(List.of(10, 21, 31), Part.GLASS);
        Result result = compare(world);
        assertEquals(State.OK, result.states()[2][2][4]);
        assertEquals(State.OK, result.states()[0][1][1]);
        assertEquals(0, result.wrong());
    }

    @Test
    void outraPecaNoLugarDaCarcacaEErrada() {
        Map<List<Integer>, Part> world = complete();
        world.put(List.of(10, 21, 31), Part.TUNER);
        Result result = compare(world);
        assertEquals(State.WRONG, result.states()[0][1][1]);
        assertEquals(1, result.wrong());
    }
}
