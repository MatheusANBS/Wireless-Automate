package io.github.matheusanbs.wirelessautomate.tv;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

class TvImageCodeTest {
    private static final int MAX_FILE = 8 << 20;

    private static byte[] png(int w, int h) throws IOException {
        BufferedImage image = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                image.setRGB(x, y, 0x80000000 | x * 255 / w << 16 | y * 255 / h << 8);
            }
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        assertTrue(ImageIO.write(image, "png", out));
        return out.toByteArray();
    }

    private static TvCodes.Read<TvImageCode.Image> read(String text) {
        return TvImageCode.read(text, MAX_FILE, 1024, 240);
    }

    @Test
    void idaEVolta() throws IOException {
        byte[] data = png(40, 30);
        String code = TvImageCode.write("Mona Lisa", data, TvImageFormat.PNG, false);
        assertTrue(code.startsWith("WA-IMAGEM1\nnome: Mona Lisa\nformato: png\n"));
        assertTrue(code.lines().skip(3).allMatch(line -> line.length() <= 76));
        TvImageCode.Image image = read(code).value();
        assertNotNull(image);
        assertEquals("Mona Lisa", image.name());
        assertArrayEquals(data, image.data());
        assertEquals(40, image.info().width());
        assertEquals(TvImageFormat.PNG, image.info().format());
    }

    @Test
    void chavesEmInglesComentariosEspacosEUrlSafe() throws IOException {
        byte[] data = png(17, 9);
        String base64 = Base64.getUrlEncoder().withoutPadding().encodeToString(data);
        String spaced = base64.substring(0, 20) + "  " + base64.substring(20, 40) + "\r\n" + base64.substring(40);
        String code = "\n# gerado por um script\n  wa-imagem1  \r\nNAME: Logo\nFormat: PNG\n\n" + spaced + "\n";
        TvImageCode.Image image = read(code).value();
        assertNotNull(image, String.valueOf(read(code).error()));
        assertEquals("Logo", image.name());
        assertArrayEquals(data, image.data());
    }

    @Test
    void semNomeEOFormatoVemDaAssinatura() throws IOException {
        byte[] data = png(8, 8);
        TvImageCode.Image image = read("WA-IMAGEM1\n" + Base64.getEncoder().encodeToString(data)).value();
        assertNotNull(image);
        assertEquals("", image.name(), "quem cola dá o nome padrão");
        assertEquals(TvImageFormat.PNG, TvImageCode.format("png"));
        assertEquals(TvImageFormat.JPG, TvImageCode.format("JPEG"));
    }

    @Test
    void formatoQueNaoBateERecusado() throws IOException {
        String code = TvImageCode.write("x", png(8, 8), TvImageFormat.PNG, true).replace("format: png", "format: gif");
        TvCodes.Read<TvImageCode.Image> read = read(code);
        assertNull(read.value());
        assertEquals("code.image.mismatch", read.error());
        assertEquals("code.image.format", read("WA-IMAGEM1\nformato: bmp\nAAAA").error());
    }

    @Test
    void tetosAntesDeDecodificar() throws IOException {
        byte[] big = png(1100, 4);
        assertEquals("code.image.too_large", read(TvImageCode.write("x", big, TvImageFormat.PNG, false)).error());
        String huge = "WA-IMAGEM1\n" + "A".repeat(TvImageCode.maxText(1024) + 1);
        assertEquals("code.image.file_too_big", TvImageCode.read(huge, 1024, 1024, 240).error(),
                "o texto acima do teto é recusado antes de tudo");
        String overFile = "WA-IMAGEM1\n" + "A".repeat(2000);
        assertEquals("code.image.file_too_big", TvImageCode.read(overFile, 1024, 1024, 240).error(),
                "o arquivo acima do teto é recusado pelo tamanho do base64");
        byte[] gif = TvImageHeaderTest.gif(4, 4, 3, 4, 4);
        assertEquals("code.image.frames", TvImageCode.read(TvImageCode.write("x", gif, TvImageFormat.GIF, false),
                MAX_FILE, 1024, 2).error());
    }

    @Test
    void lixoECodigoQueNaoEImagem() {
        assertFalse(TvImageCode.is("WA-ARTE1\nnome: x"));
        assertFalse(TvImageCode.is(null));
        assertTrue(TvImageCode.is("# comentário\nWA-IMAGEM1\n"));
        assertEquals("code.image.base64", read("WA-IMAGEM1\nnão é base64!").error());
        assertEquals("code.image.empty", read("WA-IMAGEM1\nnome: só nome").error());
        assertEquals("code.image.bad_format", read("WA-IMAGEM1\n" + Base64.getEncoder()
                .encodeToString("BM isto é um bitmap".getBytes())).error());
        assertEquals("code.art.key", read("WA-IMAGEM1\ncor: azul\nAAAA").error());
    }

    @Test
    void tetoDoTextoDoPadrao() {
        // 8 MB em base64 com as quebras de linha e a folga: o número que o relatório e os guias citam.
        assertEquals(11_483_244, TvImageCode.maxText(8 << 20));
    }
}
