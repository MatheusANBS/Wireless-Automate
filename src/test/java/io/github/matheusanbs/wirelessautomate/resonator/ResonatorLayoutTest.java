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
    static final int CX = 12;
    static final int CY = 20;
    static final int CZ = 30;

    /** Um mundo falso: a Armilar inteira em volta do Núcleo (CX, CY, CZ) para a frente dada; o resto é "outro bloco". */
    static Map<List<Integer>, Part> armilar(Face front) {
        Map<List<Integer>, Part> world = new HashMap<>();
        for (int lx = 0; lx < 5; lx++) {
            for (int ly = 0; ly < 5; ly++) {
                for (int lz = 0; lz < 5; lz++) {
                    world.put(cell(front, lx, ly, lz), ResonatorLayout.expected(lx, ly, lz));
                }
            }
        }
        return world;
    }

    static List<Integer> cell(Face front, int lx, int ly, int lz) {
        int[] w = ResonatorLayout.world(front, CX, CY, CZ, lx, ly, lz);
        return List.of(w[0], w[1], w[2]);
    }

    private static ResonatorLayout.Result check(Face front, Map<List<Integer>, Part> w) {
        return ResonatorLayout.check(CX, CY, CZ, front, (x, y, z) -> w.getOrDefault(List.of(x, y, z), Part.OTHER));
    }

    private static Formed formed(Face front, Map<List<Integer>, Part> w) {
        return assertInstanceOf(Formed.class, check(front, w));
    }

    private static Member at(Formed f, List<Integer> p) {
        return f.members().stream().filter(m -> m.x() == p.get(0) && m.y() == p.get(1) && m.z() == p.get(2))
                .findFirst().orElseThrow();
    }

    private static Member local(Face front, Formed f, int lx, int ly, int lz) {
        return at(f, cell(front, lx, ly, lz));
    }

    @ParameterizedTest
    @EnumSource(value = Face.class, names = {"SOUTH", "WEST", "NORTH", "EAST"})
    void montaNasQuatroFrentesComOs35Membros(Face front) {
        Formed f = formed(front, armilar(front));
        assertEquals(35, f.members().size());
        assertEquals(front, f.front());
        Member core = at(f, List.of(CX, CY, CZ));
        assertEquals(Part.CORE, core.part());
        assertEquals(front, core.face());
    }

    @Test
    void semSintonizadorMontaComALajeNaCasaDele() {
        Map<List<Integer>, Part> w = armilar(Face.SOUTH);
        w.put(cell(Face.SOUTH, 2, 0, 0), Part.CASING);
        Formed f = formed(Face.SOUTH, w);
        assertEquals(35, f.members().size());
        Member slab = local(Face.SOUTH, f, 2, 0, 0);
        assertEquals(Part.CASING, slab.part());
        assertEquals("p20", slab.slot());
    }

    @Test
    void osSlotsDasLajesSeguemAFrenteSul() {
        Formed f = formed(Face.SOUTH, armilar(Face.SOUTH));
        assertEquals("p00", local(Face.SOUTH, f, 0, 0, 0).slot());
        assertEquals("p44", local(Face.SOUTH, f, 4, 0, 4).slot());
        for (Member m : f.members()) {
            if (m.part() == Part.CASING) {
                assertEquals(Face.SOUTH, m.face());
            }
        }
    }

    @ParameterizedTest
    @EnumSource(value = Face.class, names = {"SOUTH", "WEST", "NORTH", "EAST"})
    void osSlotsDasLajesSaoLocaisEntaoNaoMudamComAFrente(Face front) {
        Formed f = formed(front, armilar(front));
        assertEquals("p00", local(front, f, 0, 0, 0).slot());
        assertEquals("p44", local(front, f, 4, 0, 4).slot());
        assertEquals(front, local(front, f, 0, 0, 0).face());
    }

    @Test
    void asColunasComFrenteSul() {
        Formed f = formed(Face.SOUTH, armilar(Face.SOUTH));
        Member lower = local(Face.SOUTH, f, 3, 1, 3);
        assertEquals(Part.COLUMN, lower.part());
        assertEquals("lower", lower.slot());
        assertEquals("se", lower.corner());
        Member upper = local(Face.SOUTH, f, 1, 2, 1);
        assertEquals("upper", upper.slot());
        assertEquals("nw", upper.corner());
        assertEquals("sw", local(Face.SOUTH, f, 1, 1, 3).corner());
        assertEquals("ne", local(Face.SOUTH, f, 3, 2, 1).corner());
    }

    @Test
    void asColunasComFrenteLesteGiramComAEstrutura() {
        Formed f = formed(Face.EAST, armilar(Face.EAST));
        // A casa local (3, 1, 3) cai no mundo a (dz, -dx) = (1, -1) do centro da Armilar.
        int[] center = ResonatorLayout.world(Face.EAST, CX, CY, CZ, 2, 0, 2);
        List<Integer> p = cell(Face.EAST, 3, 1, 3);
        assertEquals(List.of(center[0] + 1, CY + 1, center[2] - 1), p);
        assertEquals("ne", at(f, p).corner());
    }

    @Test
    void oBercoTemSlotDeBaixoEDeCima() {
        Formed f = formed(Face.SOUTH, armilar(Face.SOUTH));
        Member lower = local(Face.SOUTH, f, 2, 1, 2);
        assertEquals(Part.CRADLE, lower.part());
        assertEquals("lower", lower.slot());
        assertEquals("upper", local(Face.SOUTH, f, 2, 2, 2).slot());
    }

    @Test
    void aFaceDasPecasSegueAFrente() {
        Formed south = formed(Face.SOUTH, armilar(Face.SOUTH));
        assertEquals(Face.EAST, local(Face.SOUTH, south, 4, 0, 2).face());
        assertEquals(Face.WEST, local(Face.SOUTH, south, 0, 0, 2).face());
        assertEquals(Face.NORTH, local(Face.SOUTH, south, 2, 0, 0).face());
        Formed east = formed(Face.EAST, armilar(Face.EAST));
        assertEquals(Face.NORTH, local(Face.EAST, east, 4, 0, 2).face());
        assertEquals(Face.SOUTH, local(Face.EAST, east, 0, 0, 2).face());
        assertEquals(Face.WEST, local(Face.EAST, east, 2, 0, 0).face());
    }

    @Test
    void lajeNaCasaDaEnergiaQuebra() {
        Map<List<Integer>, Part> w = armilar(Face.SOUTH);
        List<Integer> p = cell(Face.SOUTH, 4, 0, 2);
        w.put(p, Part.CASING);
        Broken b = assertInstanceOf(Broken.class, check(Face.SOUTH, w));
        assertEquals(Problem.MISSING_ENERGY_PORT, b.problem());
        assertEquals(p, List.of(b.x(), b.y(), b.z()));
    }

    @Test
    void lajeNaCasaDosCristaisQuebra() {
        Map<List<Integer>, Part> w = armilar(Face.SOUTH);
        List<Integer> p = cell(Face.SOUTH, 0, 0, 2);
        w.put(p, Part.CASING);
        Broken b = assertInstanceOf(Broken.class, check(Face.SOUTH, w));
        assertEquals(Problem.MISSING_CRYSTAL_PORT, b.problem());
        assertEquals(p, List.of(b.x(), b.y(), b.z()));
    }

    @Test
    void arNaCasaDeUmaColunaQuebra() {
        Map<List<Integer>, Part> w = armilar(Face.SOUTH);
        List<Integer> p = cell(Face.SOUTH, 3, 1, 3);
        w.put(p, Part.AIR);
        Broken b = assertInstanceOf(Broken.class, check(Face.SOUTH, w));
        assertEquals(Problem.WRONG_BLOCK, b.problem());
        assertEquals(p, List.of(b.x(), b.y(), b.z()));
    }

    @Test
    void blocoNumaCasaDeArQuebra() {
        Map<List<Integer>, Part> w = armilar(Face.SOUTH);
        List<Integer> p = cell(Face.SOUTH, 2, 3, 2);
        w.put(p, Part.OTHER);
        Broken b = assertInstanceOf(Broken.class, check(Face.SOUTH, w));
        assertEquals(Problem.INTERIOR_NOT_EMPTY, b.problem());
        assertEquals(p, List.of(b.x(), b.y(), b.z()));
    }

    @Test
    void outroNucleoNumaCasaDeLajeQuebra() {
        Map<List<Integer>, Part> w = armilar(Face.SOUTH);
        List<Integer> p = cell(Face.SOUTH, 1, 0, 1);
        w.put(p, Part.CORE);
        Broken b = assertInstanceOf(Broken.class, check(Face.SOUTH, w));
        assertEquals(Problem.EXTRA_CORE, b.problem());
        assertEquals(p, List.of(b.x(), b.y(), b.z()));
    }

    @Test
    void toWorldGiraComoOBlockstate() {
        assertEquals(Face.SOUTH, ResonatorLayout.toWorld(Face.SOUTH, Face.SOUTH));
        assertEquals(Face.WEST, ResonatorLayout.toWorld(Face.WEST, Face.SOUTH));
        assertEquals(Face.NORTH, ResonatorLayout.toWorld(Face.NORTH, Face.SOUTH));
        assertEquals(Face.EAST, ResonatorLayout.toWorld(Face.EAST, Face.SOUTH));
    }
}
