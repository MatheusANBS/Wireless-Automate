package io.github.matheusanbs.wirelessautomate.resonator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class CrystalChargeTest {
    @Test
    void cristalCheioAcabaExatamenteNaVidaEmLotesDe20Ticks() {
        int carga = CrystalCharge.FULL;
        for (int lote = 0; lote < 599; lote++) {
            carga = CrystalCharge.drain(carga, 20, 12_000);
        }
        assertFalse(CrystalCharge.isDull(carga), "ainda tem carga no lote 599");
        carga = CrystalCharge.drain(carga, 20, 12_000);
        assertTrue(CrystalCharge.isDull(carga), "acaba no lote 600 = 12.000 ticks");
        assertEquals(0, carga);
    }

    @Test
    void lapidadoDuraODobro() {
        int carga = CrystalCharge.FULL;
        for (int lote = 0; lote < 1_199; lote++) {
            carga = CrystalCharge.drain(carga, 20, 24_000);
        }
        assertFalse(CrystalCharge.isDull(carga));
        assertEquals(0, CrystalCharge.drain(carga, 20, 24_000));
    }

    @Test
    void semTicksOuSemVidaNaoGasta() {
        assertEquals(500_000, CrystalCharge.drain(500_000, 0, 12_000));
        assertEquals(500_000, CrystalCharge.drain(500_000, 20, 0));
    }

    @Test
    void cargaForaDaFaixaVoltaParaDentro() {
        assertEquals(0, CrystalCharge.clamp(-5));
        assertEquals(CrystalCharge.FULL, CrystalCharge.clamp(CrystalCharge.FULL + 1));
        assertEquals(0, CrystalCharge.drain(10, 20, 12_000));
    }

    @Test
    void porcentagemArredondaParaCima() {
        assertEquals(100, CrystalCharge.percent(CrystalCharge.FULL));
        assertEquals(50, CrystalCharge.percent(500_000));
        assertEquals(1, CrystalCharge.percent(1), "com carga nunca mostra 0%");
        assertEquals(0, CrystalCharge.percent(0));
    }
}
