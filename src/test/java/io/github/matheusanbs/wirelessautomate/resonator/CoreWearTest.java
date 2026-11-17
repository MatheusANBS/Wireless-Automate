package io.github.matheusanbs.wirelessautomate.resonator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class CoreWearTest {
    @Test
    void semCorteGastaOLoteInteiro() {
        CoreWear wear = new CoreWear();
        for (int tick = 0; tick < 20; tick++) {
            wear.add(432, 432);
        }
        assertEquals(20, wear.take(20));
    }

    @Test
    void comCorteGastaAParteGeradaArredondadaParaCima() {
        CoreWear wear = new CoreWear();
        // Espaço para 2 dos 20 ticks: 2 de 20.
        for (int tick = 0; tick < 20; tick++) {
            wear.add(tick < 2 ? 432 : 0, 432);
        }
        assertEquals(2, wear.take(20));
        // Um tick e meio de espaço: 1,5 de 20 arredonda para 2.
        for (int tick = 0; tick < 20; tick++) {
            wear.add(tick == 0 ? 432 : tick == 1 ? 216 : 0, 432);
        }
        assertEquals(2, wear.take(20));
    }

    @Test
    void bufferCheioOuSemGeracaoNaoGasta() {
        CoreWear wear = new CoreWear();
        assertFalse(wear.any());
        assertEquals(0, wear.take(20));
        for (int tick = 0; tick < 20; tick++) {
            wear.add(0, 432);
        }
        assertTrue(wear.any(), "houve geração possível, mesmo cortada");
        assertEquals(0, wear.take(20));
        assertFalse(wear.any());
    }

    @Test
    void takeZeraAsSomas() {
        CoreWear wear = new CoreWear();
        wear.add(10, 10);
        assertEquals(20, wear.take(20));
        assertEquals(0, wear.take(20));
    }

    @Test
    void valoresGrandesNaoEstouram() {
        CoreWear wear = new CoreWear();
        for (int tick = 0; tick < 20; tick++) {
            wear.add(Long.MAX_VALUE, Long.MAX_VALUE);
        }
        assertEquals(20, wear.take(20));
    }

    @Test
    void regraPuraComLoteCurto() {
        assertEquals(5, CoreWear.spent(5, 100, 100));
        assertEquals(1, CoreWear.spent(5, 1, 100));
        assertEquals(0, CoreWear.spent(5, 0, 100));
        assertEquals(0, CoreWear.spent(5, 10, 0));
    }
}
