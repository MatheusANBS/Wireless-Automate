package io.github.matheusanbs.wirelessautomate.client.fx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.client.renderer.RenderType;
import org.junit.jupiter.api.Test;

class FxTypesTest {
    private final List<RenderType> batch = FxTypes.batchTypes();

    @Test
    void todoTipoDeAddEstaNoLote() {
        for (FxSheet sheet : FxSheets.all()) {
            assertTrue(batch.contains(FxTypes.add(sheet)), "aditivo fora do lote: " + sheet.name());
            assertTrue(batch.contains(FxTypes.of(sheet)), "tipo da folha fora do lote: " + sheet.name());
        }
    }

    @Test
    void loteSemRepetidos() {
        Set<RenderType> seen = new HashSet<>(batch);
        assertEquals(batch.size(), seen.size());
    }

    @Test
    void ordemTintaAditivoLuz() {
        List<FxSheet> sheets = FxSheets.all();
        long ink = sheets.stream().filter(s -> s.layer() == FxSheet.Layer.INK).count();
        int adds = sheets.size();
        assertEquals(sheets.size() + adds, batch.size(), "uma entrada por folha, mais um aditivo por folha");
        for (int i = 0; i < ink; i++) {
            assertEquals(FxTypes.of(sheets.get(i)), batch.get(i), "tinta primeiro");
        }
        for (int i = 0; i < adds; i++) {
            assertEquals(FxTypes.add(sheets.get(i)), batch.get((int) ink + i), "depois o aditivo");
        }
        for (int i = (int) ink; i < sheets.size(); i++) {
            assertEquals(FxTypes.of(sheets.get(i)), batch.get(adds + i), "por fim a luz");
        }
    }
}
