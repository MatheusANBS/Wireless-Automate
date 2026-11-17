package io.github.matheusanbs.wirelessautomate.client.fx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** As guardas das fitas e da anotação de luz, sem GL. */
class FxGuardsTest {
    @AfterEach
    void limpar() {
        FxLights.clear();
        FxQueue.setShadowDetector(null);
    }

    @Test
    void fitaSemRepeticaoNaoDesenha() {
        FxRibbon.Buffer buf = new FxRibbon.Buffer();
        FxContext ctx = new FxContext();
        assertFalse(FxDraw.ribbon(null, ctx, null, 0, buf, 4, 0xFFFFFF, 0f, 0f, 0, 0));
        assertFalse(FxDraw.ribbon(null, ctx, null, 0, buf, 4, 0xFFFFFF, -1f, 0f, 0, 0));
        assertFalse(FxDraw.ribbon(null, ctx, null, 0, buf, 4, 0xFFFFFF, Float.NaN, 0f, 0, 0));
    }

    @Test
    void fitaMaiorQueORascunhoNaoDesenha() {
        FxRibbon.Buffer buf = new FxRibbon.Buffer();
        assertFalse(FxDraw.ribbon(null, new FxContext(), null, 0, buf, FxRibbon.MAX + 1, 0xFFFFFF, 1f, 0f, 0, 0));
    }

    @Test
    void bezierComPassosDemaisCabeNoBuffer() {
        FxRibbon.Buffer buf = new FxRibbon.Buffer();
        float[] p = {0, 0, 0, 1, 2, 3, 4, 5, 6, 7, 8, 9};
        int n = FxRibbon.bezier(p, 100_000, buf);
        assertEquals(FxRibbon.MAX, n);
        // a última amostra é o último controle
        assertEquals(7f, buf.pts[(n - 1) * 3], 1e-4);
        assertEquals(9f, buf.pts[(n - 1) * 3 + 2], 1e-4);
    }

    @Test
    void luzNaPassadaDeSombraNaoEAnotada() {
        FxQueue.setShadowDetector(() -> true);
        FxLights.add(0, 0, 0, 1, 1, 1, 1, 10);
        assertEquals(0, FxLights.count());
        FxQueue.setShadowDetector(() -> false);
        FxLights.add(0, 0, 0, 1, 1, 1, 1, 10);
        assertEquals(1, FxLights.count());
    }
}
