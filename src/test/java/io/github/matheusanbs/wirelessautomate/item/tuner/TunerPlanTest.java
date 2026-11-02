package io.github.matheusanbs.wirelessautomate.item.tuner;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import io.github.matheusanbs.wirelessautomate.item.tuner.TunerPlan.Face;
import io.github.matheusanbs.wirelessautomate.item.tuner.TunerPlan.Role;
import io.github.matheusanbs.wirelessautomate.network.PortMode;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class TunerPlanTest {
    private static final int DOWN = 0;
    private static final int UP = 1;
    private static final int NORTH = 2;
    private static final int SOUTH = 3;
    private static final int WEST = 4;
    private static final int EAST = 5;

    private static final PortMode N = PortMode.NONE;
    private static final PortMode I = PortMode.INSERT;
    private static final PortMode E = PortMode.EXTRACT;
    private static final PortMode S = PortMode.BOTH;

    private static Face face(Role role, boolean open, String signature) {
        return new Face(role, open, List.of(signature));
    }

    private static Face[] all(Face face) {
        Face[] faces = new Face[TunerPlan.FACES];
        Arrays.fill(faces, face);
        return faces;
    }

    /** Fornalha (frente Leste): Cima a entrada (aberta), os lados o combustível, Baixo a saída e o combustível. */
    private static Face[] furnace() {
        Face fuel = face(Role.IN, false, "combustível");
        Face[] faces = all(fuel);
        faces[UP] = face(Role.BOTH, true, "entrada");
        faces[DOWN] = face(Role.BOTH, false, "saída+combustível");
        return faces;
    }

    @Test
    void furnaceInsertsOnTopAndOneSideAndExtractsBelow() {
        // Frente Leste: Trás é Oeste.
        PortMode[] expected = new PortMode[6];
        Arrays.fill(expected, N);
        expected[UP] = I;
        expected[WEST] = I;
        expected[DOWN] = E;
        assertArrayEquals(expected, TunerPlan.decide(furnace(), EAST));
    }

    @Test
    void furnaceWithoutFrontUsesNorth() {
        // Sem frente vale Norte: Trás é Sul.
        PortMode[] modes = TunerPlan.decide(furnace(), -1);
        assertArrayEquals(new PortMode[] {E, I, N, I, N, N}, modes);
    }

    @Test
    void storageGetsStorageOnTopOnlyAndTheOtherFacesNone() {
        PortMode[] modes = TunerPlan.decide(all(face(Role.BOTH, true, "27 slots")), NORTH);
        assertArrayEquals(new PortMode[] {N, S, N, N, N, N}, modes);
    }

    @Test
    void storageExposedOnlyBelowStillGetsOneFace() {
        Face[] faces = all(Face.NOTHING);
        faces[DOWN] = face(Role.BOTH, true, "tanque");
        faces[SOUTH] = face(Role.BOTH, true, "tanque");
        // Frente Norte: Trás (Sul) vem antes de Baixo na preferência.
        // As faces sem a capability ficam como estão; a outra com ela vai para Nenhum.
        assertArrayEquals(new PortMode[] {N, null, null, S, null, null}, TunerPlan.decide(faces, NORTH));
    }

    @Test
    void machineWithTheSameMixedFacesExtractsBelowAndInsertsOnTop() {
        // Entrada e saída em todas as faces (não abertas): não é armazenamento.
        PortMode[] modes = TunerPlan.decide(all(face(Role.BOTH, false, "entrada+saída")), NORTH);
        assertArrayEquals(new PortMode[] {E, I, N, N, N, N}, modes);
    }

    @Test
    void openFacesWithDifferentSignaturesAreNotStorage() {
        Face[] faces = all(face(Role.BOTH, true, "a"));
        faces[UP] = face(Role.BOTH, true, "b");
        // Baixo Extrai; Cima (b) e Trás (a) Insere, uma por assinatura.
        assertArrayEquals(new PortMode[] {E, I, N, I, N, N}, TunerPlan.decide(faces, NORTH));
    }

    @Test
    void energyConsumerInsertsOnTopOnly() {
        PortMode[] modes = TunerPlan.decide(all(face(Role.IN, false, "recebe")), WEST);
        assertArrayEquals(new PortMode[] {N, I, N, N, N, N}, modes);
    }

    @Test
    void generatorExtractsBelowOnly() {
        PortMode[] modes = TunerPlan.decide(all(face(Role.OUT, false, "dá")), SOUTH);
        assertArrayEquals(new PortMode[] {E, N, N, N, N, N}, modes);
    }

    @Test
    void generatorWithoutBottomExtractsFromTheBack() {
        Face[] faces = all(face(Role.OUT, false, "dá"));
        faces[DOWN] = Face.NOTHING;
        // Frente Sul: Trás é Norte.
        assertArrayEquals(new PortMode[] {N, N, E, N, N, N}, TunerPlan.decide(faces, SOUTH));
    }

    @Test
    void outputsWithDifferentSignaturesEachExtract() {
        Face[] faces = all(Face.NOTHING);
        faces[DOWN] = face(Role.OUT, false, "produto");
        faces[EAST] = face(Role.OUT, false, "subproduto");
        faces[UP] = face(Role.IN, false, "entrada");
        assertArrayEquals(new PortMode[] {E, I, N, N, N, E}, TunerPlan.decide(faces, NORTH));
    }

    @Test
    void withOutputsTheBothFacesInsert() {
        Face[] faces = all(Face.NOTHING);
        faces[DOWN] = face(Role.OUT, false, "saída");
        faces[NORTH] = face(Role.BOTH, false, "misto");
        assertArrayEquals(new PortMode[] {E, N, I, N, N, N}, TunerPlan.decide(faces, NORTH));
    }

    @Test
    void nothingLeavesTheTypeOut() {
        assertNull(TunerPlan.decide(all(Face.NOTHING), NORTH));
    }

    /** Uma máquina de itens (fornalha) com energia ingênua, que diz que recebe e dá em todas as faces. */
    @Test
    void machineWithNaiveEnergyIsNotStorageAndEnergyOnlyInserts() {
        Face[] energy = all(face(Role.BOTH, true, "recebe e dá"));
        PortMode[][] modes = TunerPlan.decide(new Face[][] {furnace(), energy}, new boolean[] {false, true}, EAST);
        assertArrayEquals(TunerPlan.decide(furnace(), EAST), modes[0]);
        assertArrayEquals(new PortMode[] {N, I, N, N, N, N}, modes[1]);
    }

    @Test
    void consumerThatAlsoSaysItExtractsNeverGetsExtract() {
        // Itens de máquina (entrada em cima, produto embaixo) e energia AMBOS não aberta: energia só Insere.
        Face[] items = all(Face.NOTHING);
        items[UP] = face(Role.IN, false, "entrada");
        items[DOWN] = face(Role.OUT, false, "produto");
        Face[] energy = all(face(Role.BOTH, false, "ingênua"));
        PortMode[][] modes = TunerPlan.decide(new Face[][] {items, energy}, new boolean[] {false, true}, NORTH);
        assertArrayEquals(new PortMode[] {N, I, N, N, N, N}, modes[1]);
    }

    @Test
    void batteryAloneIsStorage() {
        PortMode[][] modes = TunerPlan.decide(new Face[][] {all(face(Role.BOTH, true, "bateria"))},
                new boolean[] {true}, NORTH);
        assertArrayEquals(new PortMode[] {N, S, N, N, N, N}, modes[0]);
    }

    @Test
    void storageNeedsEveryTypeToBeStorage() {
        Face[] items = all(face(Role.BOTH, true, "baú"));
        Face[] fluid = all(face(Role.IN, false, "só entra"));
        PortMode[][] modes = TunerPlan.decide(new Face[][] {items, fluid}, new boolean[] {false, false}, NORTH);
        // Não é armazenamento: os itens viram Baixo Extrai e Cima Insere.
        assertArrayEquals(new PortMode[] {E, I, N, N, N, N}, modes[0]);
        assertArrayEquals(new PortMode[] {N, I, N, N, N, N}, modes[1]);
    }

    @Test
    void realOutputBeatsWeakOutputForExtract() {
        Face[] faces = all(Face.NOTHING);
        faces[DOWN] = new Face(Role.OUT, false, List.of("produto"), true, true);
        faces[SOUTH] = new Face(Role.OUT, false, List.of("produto"), false, true);
        // Frente Norte: Trás (Sul) dá algo agora; Baixo só está vazio. Mesma assinatura: um Extrai só.
        assertArrayEquals(new PortMode[] {N, N, N, E, N, N}, TunerPlan.decide(faces, NORTH));
    }

    @Test
    void realOutputBeatsWeakOutputAmongBothFaces() {
        Face[] faces = all(Face.NOTHING);
        faces[DOWN] = new Face(Role.BOTH, false, List.of("a"), true, true);
        faces[SOUTH] = new Face(Role.BOTH, false, List.of("b"), false, true);
        assertArrayEquals(new PortMode[] {I, N, N, E, N, N}, TunerPlan.decide(faces, NORTH));
    }
}
