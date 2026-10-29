package io.github.matheusanbs.wirelessautomate.tv;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Random;
import org.junit.jupiter.api.Test;

class TvPixelsTest {
    @Test
    void doisPixelsPorByteOParNoMeioByteDeBaixo() {
        byte[] packed = TvPixels.pack(new byte[] {1, 2, 15, 0, 7});
        assertArrayEquals(new byte[] {0x21, 0x0F, 0x07}, packed);
        assertEquals(3, TvPixels.packedLength(5));
        assertEquals(2048, TvPixels.packedLength(64 * 64));
        assertEquals(8192, TvPixels.packedLength(128 * 128));
    }

    @Test
    void idaEVoltaComContagemParEImpar() {
        Random random = new Random(42);
        for (int count : new int[] {64, 81, 9 * 63, 64 * 64, 127 * 128}) {
            byte[] indices = new byte[count];
            for (int i = 0; i < count; i++) {
                indices[i] = (byte) random.nextInt(16);
            }
            byte[] packed = TvPixels.pack(indices);
            assertEquals((count + 1) / 2, packed.length);
            assertArrayEquals(indices, TvPixels.unpack(packed, count));
        }
    }

    @Test
    void tamanhoGravadoErradoNaoDesempacota() {
        assertNull(TvPixels.unpack(new byte[3], 8));
        assertNull(TvPixels.unpack(new byte[5], 8));
        assertNull(TvPixels.unpack(new byte[1], -1));
    }

    @Test
    void tamanhosAceitos() {
        assertTrue(TvPixels.validSize(8, 8));
        assertTrue(TvPixels.validSize(64, 64));
        assertTrue(TvPixels.validSize(8, 64));
        assertTrue(TvPixels.validSize(128, 128));
        assertTrue(TvPixels.validSize(8, 128));
        assertFalse(TvPixels.validSize(7, 8));
        assertFalse(TvPixels.validSize(8, 129));
        assertFalse(TvPixels.validSize(129, 128));
        assertFalse(TvPixels.validSize(0, 0));
    }

    @Test
    void problemaDePaletaPixelsEIndices() {
        int[] palette = new int[16];
        assertNull(TvPixels.problem(8, 8, palette, new byte[64]));
        assertNotNull(TvPixels.problem(8, 8, new int[15], new byte[64]));
        assertNotNull(TvPixels.problem(8, 8, palette, new byte[63]));
        assertNotNull(TvPixels.problem(4, 8, palette, new byte[32]));
        byte[] bad = new byte[64];
        bad[10] = 16;
        assertNotNull(TvPixels.problem(8, 8, palette, bad));
        bad[10] = -1;
        assertNotNull(TvPixels.problem(8, 8, palette, bad));
    }

    @Test
    void resumoPeloConteudoSemIdNemNome() {
        int[] palette = new int[16];
        palette[1] = 0xFF112233;
        byte[] pixels = new byte[128 * 128];
        Random random = new Random(7);
        for (int i = 0; i < pixels.length; i++) {
            pixels[i] = (byte) random.nextInt(16);
        }
        long hash = TvPixels.hash(128, 128, palette, pixels);
        assertEquals(hash, TvPixels.hash(128, 128, palette.clone(), pixels.clone()), "o mesmo conteúdo, o mesmo resumo");

        byte[] onePixel = pixels.clone();
        onePixel[5000] = (byte) ((onePixel[5000] + 1) % 16);
        assertNotEquals(hash, TvPixels.hash(128, 128, palette, onePixel), "um pixel a mais muda o resumo");
        int[] otherColor = palette.clone();
        otherColor[1] = 0xFF112234;
        assertNotEquals(hash, TvPixels.hash(128, 128, otherColor, pixels), "uma cor da paleta muda o resumo");

        byte[] wide = new byte[16 * 8];
        assertNotEquals(TvPixels.hash(16, 8, palette, wide), TvPixels.hash(8, 16, palette, wide),
                "os mesmos pixels em outro tamanho são outra arte");
    }
}
