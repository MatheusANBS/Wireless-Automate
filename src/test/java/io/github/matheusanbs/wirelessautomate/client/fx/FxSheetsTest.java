package io.github.matheusanbs.wirelessautomate.client.fx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class FxSheetsTest {
    private final List<FxSheet> all = FxSheets.all();

    @Test
    void nomesUnicos() {
        Set<String> names = new HashSet<>();
        for (FxSheet sheet : all) {
            assertTrue(names.add(sheet.name()), "nome repetido: " + sheet.name());
        }
    }

    @Test
    void tintaPrimeiro() {
        List<FxSheet> ink = all.stream().filter(s -> s.layer() == FxSheet.Layer.INK).toList();
        assertEquals(List.of("fumaca", "trilhos"), ink.stream().map(FxSheet::name).toList());
        // as de tinta vêm antes de todas as de luz: a ordem em que o FxBatch desenha
        for (int i = 0; i < all.size(); i++) {
            assertEquals(i < 2 ? FxSheet.Layer.INK : FxSheet.Layer.LIGHT, all.get(i).layer(), all.get(i).name());
        }
    }

    @Test
    void cadaFolhaTemTamanho() {
        for (FxSheet sheet : all) {
            assertTrue(sheet.width() > 0, sheet.name());
            assertTrue(sheet.frames() >= 1, sheet.name());
        }
    }

    @Test
    void tirasTemQuadroDeOitoDeAltura() {
        int strips = 0;
        for (FxSheet sheet : all) {
            if (sheet.repeatU()) {
                strips++;
                assertEquals(8, sheet.frameHeight(), sheet.name());
            }
        }
        assertEquals(5, strips);
    }

    @Test
    void listaImutavel() {
        assertThrows(UnsupportedOperationException.class, () -> all.add(all.get(0)));
    }
}
