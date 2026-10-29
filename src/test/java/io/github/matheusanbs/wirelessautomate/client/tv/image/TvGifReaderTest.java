package io.github.matheusanbs.wirelessautomate.client.tv.image;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.matheusanbs.wirelessautomate.client.dev.E2eGif;
import io.github.matheusanbs.wirelessautomate.client.tv.render.TvPremultiply;
import java.awt.image.BufferedImage;
import java.awt.image.IndexColorModel;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

class TvGifReaderTest {
    private static final int[] PALETTE = {0xFF0000, 0x00FF00, 0x0000FF, 0xFFFFFF};
    private static final int RED = 0xFF0000FF;
    private static final int GREEN = 0xFF00FF00;
    private static final int BLUE = 0xFFFF0000;
    private static final int WHITE = 0xFFFFFFFF;

    private static int[] fill(int n, int index) {
        int[] out = new int[n];
        Arrays.fill(out, index);
        return out;
    }

    /** Um quadro 4 × 3 todo vermelho e, por cima, um 2 × 1 em (1, 1) com o descarte pedido. */
    private static TvGifReader twoFrames(int disposal) {
        byte[] gif = E2eGif.write(4, 3, PALETTE, List.of(
                new E2eGif.Frame(0, 0, 4, 3, fill(12, 0), 10, 1, -1, false),
                new E2eGif.Frame(1, 1, 2, 1, new int[] {1, 3}, 10, disposal, 3, false),
                new E2eGif.Frame(0, 0, 1, 1, new int[] {2}, 10, 1, -1, false)));
        return new TvGifReader(gif);
    }

    @Test
    void montaQuadrosComTransparencia() {
        TvGifReader reader = twoFrames(1);
        assertTrue(reader.next());
        assertEquals(0, reader.frame());
        assertTrue(Arrays.stream(reader.canvas()).allMatch(c -> c == RED));
        assertTrue(reader.next());
        // O índice 3 é transparente: o vermelho de baixo continua em (2, 1).
        assertEquals(GREEN, reader.canvas()[1 * 4 + 1]);
        assertEquals(RED, reader.canvas()[1 * 4 + 2]);
        assertTrue(reader.next());
        assertEquals(BLUE, reader.canvas()[0]);
        assertEquals(GREEN, reader.canvas()[5], "o descarte 1 deixa o quadro anterior");
        assertFalse(reader.next(), "fim do arquivo");
    }

    @Test
    void descarte2LimpaORetangulo() {
        TvGifReader reader = twoFrames(2);
        reader.next();
        reader.next();
        reader.next();
        assertEquals(0, reader.canvas()[5], "o retângulo do quadro 2 volta ao transparente");
        assertEquals(RED, reader.canvas()[4]);
    }

    @Test
    void descarte3VoltaAoQueHaviaAntes() {
        TvGifReader reader = twoFrames(3);
        reader.next();
        reader.next();
        reader.next();
        assertEquals(RED, reader.canvas()[5], "o quadro 2 some e o vermelho volta");
        assertEquals(BLUE, reader.canvas()[0]);
    }

    @Test
    void rebobinarComecaDaTelaTransparente() {
        TvGifReader reader = twoFrames(1);
        reader.next();
        reader.next();
        reader.rewind();
        assertEquals(-1, reader.frame());
        assertTrue(Arrays.stream(reader.canvas()).allMatch(c -> c == 0));
        assertTrue(reader.next());
        assertEquals(RED, reader.canvas()[5]);
    }

    @Test
    void entrelacadoVaiNasLinhasCertas() {
        int w = 2;
        int h = 11;
        int[] indices = new int[w * h];
        for (int row = 0; row < h; row++) {
            Arrays.fill(indices, row * w, row * w + w, row % 4);
        }
        byte[] gif = E2eGif.write(w, h, PALETTE, List.of(new E2eGif.Frame(0, 0, w, h, indices, 5, 1, -1, true)));
        TvGifReader reader = new TvGifReader(gif);
        assertTrue(reader.next());
        int[] colors = {RED, GREEN, BLUE, WHITE};
        for (int row = 0; row < h; row++) {
            assertEquals(colors[row % 4], reader.canvas()[row * w], "linha " + row);
        }
        int[] order = new int[h];
        for (int i = 0; i < h; i++) {
            order[i] = TvGifReader.interlacedRow(i, h);
        }
        assertArrayEquals(new int[] {0, 8, 4, 2, 6, 10, 1, 3, 5, 7, 9}, order);
    }

    @Test
    void lzwDeVerdadeComoOImageIoGrava() throws IOException {
        int w = 61;
        int h = 37;
        byte[] r = new byte[256];
        byte[] g = new byte[256];
        byte[] b = new byte[256];
        for (int i = 0; i < 256; i++) {
            r[i] = (byte) i;
            g[i] = (byte) (255 - i);
            b[i] = (byte) (i * 7);
        }
        BufferedImage image = new BufferedImage(w, h, BufferedImage.TYPE_BYTE_INDEXED,
                new IndexColorModel(8, 256, r, g, b));
        Random random = new Random(42);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                // Trechos repetidos e ruído, para o LZW usar a tabela e crescer o código.
                int index = x < 20 ? (x / 4 + y) & 0xFF : random.nextInt(256);
                image.getRaster().setSample(x, y, 0, index);
            }
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        assertTrue(ImageIO.write(image, "gif", out));
        TvGifReader reader = new TvGifReader(out.toByteArray());
        assertTrue(reader.next());
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int rgb = image.getRGB(x, y);
                int expected = 0xFF000000 | (rgb & 0xFF) << 16 | (rgb >> 8 & 0xFF) << 8 | rgb >> 16 & 0xFF;
                assertEquals(expected, reader.canvas()[y * w + x], "pixel " + x + "," + y);
            }
        }
    }

    @Test
    void arquivoCortadoParaSemLancar() {
        byte[] gif = E2eGif.write(4, 3, PALETTE, List.of(E2eGif.Frame.full(4, 3, fill(12, 1), 10),
                E2eGif.Frame.full(4, 3, fill(12, 2), 10)));
        TvGifReader reader = new TvGifReader(Arrays.copyOf(gif, gif.length - 12));
        assertTrue(reader.next());
        assertFalse(reader.next(), "o segundo quadro está cortado");
        assertFalse(reader.next());
    }

    @Test
    void duracoesSemAbrirOsQuadros() {
        byte[] gif = E2eGif.write(4, 3, PALETTE, List.of(E2eGif.Frame.full(4, 3, fill(12, 1), 10),
                E2eGif.Frame.full(4, 3, fill(12, 2), 0), E2eGif.Frame.full(4, 3, fill(12, 3), 25)));
        assertArrayEquals(new int[] {100, 0, 250}, TvGifReader.delays(gif, 240));
        assertArrayEquals(new int[] {100, 0}, TvGifReader.delays(gif, 2));
    }

    @Test
    void relogioPelaHoraDoCliente() {
        TvGifClock clock = new TvGifClock(new int[] {100, 0, 250});
        assertEquals(3, clock.frames());
        assertEquals(450, clock.loop(), "o quadro sem atraso dura 100 ms, como nos navegadores");
        assertEquals(0, clock.frameAt(0));
        assertEquals(0, clock.frameAt(99));
        assertEquals(1, clock.frameAt(100));
        assertEquals(2, clock.frameAt(200));
        assertEquals(2, clock.frameAt(449));
        assertEquals(0, clock.frameAt(450), "volta ao começo do laço");
        assertEquals(clock.frameAt(1_000_000_123L), clock.frameAt(1_000_000_123L % 450), "o mesmo zero para todos");
        assertEquals(0, new TvGifClock(new int[0]).frameAt(12345));
    }

    @Test
    void alfaPreMultiplicado() {
        assertEquals(0xFF102030, TvPremultiply.color(0xFF102030));
        assertEquals(0, TvPremultiply.color(0x00FFFFFF));
        assertEquals(0x80808080, TvPremultiply.color(0x80FFFFFF));
        assertEquals(0x40100000 | 0x08 << 8 | 0x04, TvPremultiply.color(0x40401F10));
    }
}
