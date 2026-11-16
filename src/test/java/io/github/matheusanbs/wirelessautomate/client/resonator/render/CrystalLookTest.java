package io.github.matheusanbs.wirelessautomate.client.resonator.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.matheusanbs.wirelessautomate.client.resonator.render.CrystalLook.Tex;
import org.junit.jupiter.api.Test;

class CrystalLookTest {
    private static final Tex N = null;

    @Test
    void tabelaDosCodigos() {
        boolean[] empty = {true, false, false, false, false, false, false};
        boolean[] dull = {false, false, false, true, true, true, true};
        boolean[] cracked = {false, false, false, false, false, true, true};
        boolean[] cut = {false, false, true, false, true, false, true};
        Tex[] live = {N, Tex.SINAL, Tex.LAPIDADO, Tex.SINAL, Tex.LAPIDADO, Tex.SINAL, Tex.LAPIDADO};
        Tex[] body = {N, Tex.SINAL, Tex.LAPIDADO, Tex.OPACO, Tex.OPACO, Tex.RACHADO, Tex.RACHADO};
        double[] filament = {0, 1, 1, 0, 0, 0.35, 0.35};
        boolean[] star = {false, true, true, false, false, false, false};
        for (int c = 0; c < 7; c++) {
            byte code = (byte) c;
            assertEquals(empty[c], CrystalLook.empty(code), "vazio " + c);
            assertEquals(dull[c], CrystalLook.dull(code), "opaco " + c);
            assertEquals(cracked[c], CrystalLook.cracked(code), "rachado " + c);
            assertEquals(cut[c], CrystalLook.cut(code), "lapidado " + c);
            assertEquals(live[c], CrystalLook.liveTex(code), "liveTex " + c);
            assertEquals(body[c], CrystalLook.bodyTex(code), "bodyTex " + c);
            assertEquals(filament[c], CrystalLook.filament(code), 1e-12, "filamento " + c);
            assertEquals(star[c], CrystalLook.star(code), "estrela " + c);
        }
        assertNull(CrystalLook.liveTex((byte) 0));
        assertFalse(CrystalLook.star((byte) 4));
        assertTrue(CrystalLook.star((byte) 2));
    }

    @Test
    void caminhosDasTexturas() {
        assertEquals("textures/armilar/cristal_sinal.png", Tex.SINAL.texture().getPath());
        assertEquals("textures/armilar/cristal_lapidado.png", Tex.LAPIDADO.texture().getPath());
        assertEquals("textures/armilar/cristal_faixa.png", Tex.FAIXA.texture().getPath());
        assertEquals("textures/armilar/cristal_opaco.png", Tex.OPACO.texture().getPath());
        assertEquals("textures/armilar/cristal_rachado.png", Tex.RACHADO.texture().getPath());
        assertEquals("wirelessautomate", Tex.OPACO.texture().getNamespace());
    }
}
