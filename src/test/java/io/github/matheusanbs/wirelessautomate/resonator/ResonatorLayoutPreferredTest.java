package io.github.matheusanbs.wirelessautomate.resonator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import io.github.matheusanbs.wirelessautomate.resonator.ResonatorLayout.Face;
import io.github.matheusanbs.wirelessautomate.resonator.ResonatorLayout.Formed;
import io.github.matheusanbs.wirelessautomate.resonator.ResonatorLayout.Part;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** A frente que o Núcleo diz é a primeira tentada: numa estrutura intacta, a leitura é de uma caixa só. */
class ResonatorLayoutPreferredTest {
    private static final int CX = ResonatorLayoutTest.CX;
    private static final int CY = ResonatorLayoutTest.CY;
    private static final int CZ = ResonatorLayoutTest.CZ;

    @Test
    void aFrenteDoNucleoLeSoUmaCaixa() {
        Map<List<Integer>, Part> world = ResonatorLayoutTest.armilar(Face.WEST);
        Set<List<Integer>> read = new HashSet<>();
        ResonatorLayout.Probe probe = (x, y, z) -> {
            read.add(List.of(x, y, z));
            return world.getOrDefault(List.of(x, y, z), Part.OTHER);
        };
        Formed formed = assertInstanceOf(Formed.class, ResonatorLayout.check(CX, CY, CZ, Face.WEST, probe));
        assertEquals(Face.WEST, formed.front());
        assertEquals(125, read.size());
    }

    @Test
    void comAFrenteErradaAchaAOutra() {
        Map<List<Integer>, Part> world = ResonatorLayoutTest.armilar(Face.NORTH);
        ResonatorLayout.Probe probe = (x, y, z) -> world.getOrDefault(List.of(x, y, z), Part.OTHER);
        for (Face said : ResonatorLayout.FRONTS) {
            Formed formed = assertInstanceOf(Formed.class, ResonatorLayout.check(CX, CY, CZ, said, probe));
            assertEquals(Face.NORTH, formed.front(), "dito " + said);
        }
        Formed plain = assertInstanceOf(Formed.class, ResonatorLayout.check(CX, CY, CZ, probe));
        assertEquals(Face.NORTH, plain.front());
    }

    @Test
    void estruturaComDefeitosDaOMesmoProblema() {
        Random random = new Random(15_10_2026L);
        Part[] parts = Part.values();
        for (int round = 0; round < 400; round++) {
            Face built = ResonatorLayout.FRONTS[random.nextInt(ResonatorLayout.FRONTS.length)];
            Map<List<Integer>, Part> world = ResonatorLayoutTest.armilar(built);
            int defects = 1 + random.nextInt(4);
            for (int i = 0; i < defects; i++) {
                world.put(ResonatorLayoutTest.cell(built, random.nextInt(5), random.nextInt(5), random.nextInt(5)),
                        parts[random.nextInt(parts.length)]);
            }
            ResonatorLayout.Probe probe = (x, y, z) -> world.getOrDefault(List.of(x, y, z), Part.OTHER);
            ResonatorLayout.Result plain = ResonatorLayout.check(CX, CY, CZ, probe);
            for (Face preferred : ResonatorLayout.FRONTS) {
                assertEquals(plain, ResonatorLayout.check(CX, CY, CZ, preferred, probe),
                        "rodada " + round + ", frente " + preferred);
            }
        }
    }
}
