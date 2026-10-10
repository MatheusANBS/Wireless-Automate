package io.github.matheusanbs.wirelessautomate.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class StorageShapesTest {
    private static final List<String> IDS = List.of("storage_chest", "storage_tank", "storage_battery",
            "storage_chemical_tank", "storage_source_tank");

    @Test
    void todoArmazenamentoTemCaixasDentroDoBloco() {
        for (String id : IDS) {
            double[][] boxes = StorageShapes.boxes(id);
            assertTrue(boxes.length > 0, id + " sem caixas");
            for (double[] b : boxes) {
                assertEquals(6, b.length, id);
                for (int i = 0; i < 3; i++) {
                    assertTrue(0 <= b[i] && b[i] < b[i + 3] && b[i + 3] <= 16, id + ": caixa fora de 0..16 ou vazia");
                }
            }
        }
    }

    @Test
    void todoArmazenamentoTocaOChaoETemTopoParaPisar() {
        for (String id : IDS) {
            double[][] boxes = StorageShapes.boxes(id);
            assertTrue(java.util.Arrays.stream(boxes).anyMatch(b -> b[1] == 0), id + " não encosta no chão");
            assertTrue(java.util.Arrays.stream(boxes).anyMatch(b -> b[4] >= 15), id + " sem topo perto de 16");
        }
    }

    @Test
    void idsSaoOsConhecidos() {
        int n = 0;
        for (String id : StorageShapes.ids()) {
            assertTrue(IDS.contains(id), id);
            n++;
        }
        assertEquals(IDS.size(), n);
        assertThrows(IllegalArgumentException.class, () -> StorageShapes.boxes("storage_x"));
    }
}
