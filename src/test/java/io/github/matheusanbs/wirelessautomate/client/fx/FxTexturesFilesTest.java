package io.github.matheusanbs.wirelessautomate.client.fx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

/** O contrato entre o {@link FxSheets} e os PNG que o gerador grava: o tamanho de cada folha e o que há nelas. */
class FxTexturesFilesTest {
    private static final String RAIZ = "/assets/wirelessautomate/textures/";

    private static BufferedImage ler(String caminho) throws IOException {
        try (InputStream in = FxTexturesFilesTest.class.getResourceAsStream(RAIZ + caminho + ".png")) {
            assertNotNull(in, "falta a textura " + caminho);
            return ImageIO.read(in);
        }
    }

    @Test
    void folhasTemOTamanhoDoFxSheets() throws IOException {
        for (FxSheet sheet : FxSheets.all()) {
            BufferedImage img = ler("fx/" + sheet.name());
            assertEquals(sheet.width(), img.getWidth(), sheet.name() + ": largura");
            assertEquals(sheet.height(), img.getHeight(), sheet.name() + ": altura");
        }
    }

    /**
     * Pelo menos um pixel com alfa acima de zero em cada quadro. O filme não dá alfa 255 a todos: a fumaça (teto 170) e
     * os quadros 2 e 3 do clarão (teto 220 e 170) são translúcidos de propósito, então 255 seria exigir mais que o conceito.
     */
    @Test
    void todoQuadroTemPixelVisivel() throws IOException {
        for (FxSheet sheet : FxSheets.all()) {
            BufferedImage img = ler("fx/" + sheet.name());
            for (int f = 0; f < sheet.frames(); f++) {
                assertTrue(temVisivel(img, f * sheet.frameHeight(), sheet.frameHeight()),
                        sheet.name() + ": o quadro " + f + " não tem pixel visível");
            }
        }
    }

    private static boolean temVisivel(BufferedImage img, int y0, int altura) {
        for (int y = y0; y < y0 + altura; y++) {
            for (int x = 0; x < img.getWidth(); x++) {
                if ((img.getRGB(x, y) >>> 24) > 0) {
                    return true;
                }
            }
        }
        return false;
    }

    @Test
    void pecasSolidasExistemComOTamanho() throws IOException {
        Map<String, int[]> tamanhos = Map.of(
                "anel", new int[] {32, 10},
                "grafite", new int[] {8, 8},
                "cristal_sinal", new int[] {8, 16},
                "cristal_lapidado", new int[] {8, 16},
                "cristal_faixa", new int[] {8, 16},
                "cristal_opaco", new int[] {8, 16},
                "cristal_rachado", new int[] {8, 16});
        for (Map.Entry<String, int[]> e : tamanhos.entrySet()) {
            BufferedImage img = ler("armilar/" + e.getKey());
            assertEquals(e.getValue()[0], img.getWidth(), e.getKey() + ": largura");
            assertEquals(e.getValue()[1], img.getHeight(), e.getKey() + ": altura");
        }
    }
}
