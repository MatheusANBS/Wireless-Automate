package io.github.matheusanbs.wirelessautomate.tv;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

class TvImageHeaderTest {
    private static byte[] encoded(String format, int w, int h) throws IOException {
        BufferedImage image = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                image.setRGB(x, y, x * 255 / w << 16 | y * 255 / h << 8 | 0x40);
            }
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        assertTrue(ImageIO.write(image, format, out), "o ImageIO escreve " + format);
        return out.toByteArray();
    }

    /** Um PNG só com a assinatura e o IHDR declarando {@code w} × {@code h}. */
    private static byte[] pngHeader(long w, long h) {
        byte[] data = new byte[8 + 8 + 13 + 4];
        byte[] signature = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};
        System.arraycopy(signature, 0, data, 0, 8);
        data[11] = 13;
        data[12] = 'I';
        data[13] = 'H';
        data[14] = 'D';
        data[15] = 'R';
        put32(data, 16, w);
        put32(data, 20, h);
        data[24] = 8;
        data[25] = 6;
        return data;
    }

    private static void put32(byte[] data, int at, long value) {
        data[at] = (byte) (value >>> 24);
        data[at + 1] = (byte) (value >>> 16);
        data[at + 2] = (byte) (value >>> 8);
        data[at + 3] = (byte) value;
    }

    /** Um GIF com {@code frames} quadros de {@code fw} × {@code fh} numa tela lógica de {@code w} × {@code h}. */
    static byte[] gif(int w, int h, int frames, int fw, int fh) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.writeBytes("GIF89a".getBytes());
        out.write(w & 0xFF);
        out.write(w >> 8);
        out.write(h & 0xFF);
        out.write(h >> 8);
        out.write(0x80);
        out.write(0);
        out.write(0);
        out.writeBytes(new byte[] {0, 0, 0, (byte) 255, (byte) 255, (byte) 255});
        for (int i = 0; i < frames; i++) {
            out.writeBytes(new byte[] {0x21, (byte) 0xF9, 4, 0, 10, 0, 0, 0});
            out.write(0x2C);
            out.writeBytes(new byte[] {0, 0, 0, 0});
            out.write(fw & 0xFF);
            out.write(fw >> 8);
            out.write(fh & 0xFF);
            out.write(fh >> 8);
            out.write(0);
            out.writeBytes(new byte[] {2, 2, 0x4C, 0x01, 0});
        }
        out.write(0x3B);
        return out.toByteArray();
    }

    @Test
    void pngJpgEGifDeVerdadeLeemFormatoETamanho() throws IOException {
        TvImageHeader.Result png = TvImageHeader.read(encoded("png", 300, 200), 1024, 240);
        assertTrue(png.ok());
        assertEquals(new TvImageHeader.Info(TvImageFormat.PNG, 300, 200, 1), png.info());
        TvImageHeader.Result jpg = TvImageHeader.read(encoded("jpg", 640, 480), 1024, 240);
        assertTrue(jpg.ok(), "o JPG do ImageIO (com APP0 e tabelas antes do SOF)");
        assertEquals(new TvImageHeader.Info(TvImageFormat.JPG, 640, 480, 1), jpg.info());
        TvImageHeader.Result gif = TvImageHeader.read(encoded("gif", 64, 32), 1024, 240);
        assertTrue(gif.ok());
        assertEquals(new TvImageHeader.Info(TvImageFormat.GIF, 64, 32, 1), gif.info());
    }

    @Test
    void gifContaOsQuadrosSemDescomprimir() {
        TvImageHeader.Result result = TvImageHeader.read(gif(16, 8, 12, 16, 8), 1024, 240);
        assertTrue(result.ok());
        assertEquals(12, result.info().frames());
    }

    @Test
    void dimensaoMaiorQueOTetoERecusada() {
        TvImageHeader.Result big = TvImageHeader.read(pngHeader(4096, 4096), 1024, 240);
        assertFalse(big.ok());
        assertEquals(TvImageHeader.Problem.TOO_LARGE, big.problem());
        assertEquals(TvImageHeader.Problem.TOO_LARGE, TvImageHeader.read(pngHeader(0, 10), 1024, 240).problem(),
                "lado 0");
        assertEquals(TvImageHeader.Problem.TOO_LARGE,
                TvImageHeader.read(pngHeader(0xFFFFFFFFL, 1), 1024, 240).problem(), "um lado de 32 bits não estoura");
        assertTrue(TvImageHeader.read(pngHeader(1024, 1), 1024, 240).ok(), "o teto vale");
    }

    @Test
    void gifComQuadrosDemaisERecusado() {
        TvImageHeader.Result result = TvImageHeader.read(gif(8, 8, 241, 8, 8), 1024, 240);
        assertEquals(TvImageHeader.Problem.TOO_MANY_FRAMES, result.problem());
        assertTrue(TvImageHeader.read(gif(8, 8, 240, 8, 8), 1024, 240).ok());
    }

    @Test
    void assinaturaErradaEEstruturaQuebradaSaoRecusadas() {
        assertEquals(TvImageHeader.Problem.BAD_FORMAT,
                TvImageHeader.read("BM nada de bitmap".getBytes(), 1024, 240).problem());
        assertEquals(TvImageHeader.Problem.BAD_FORMAT, TvImageHeader.read(new byte[0], 1024, 240).problem());
        byte[] cut = pngHeader(10, 10);
        assertEquals(TvImageHeader.Problem.BAD_FORMAT,
                TvImageHeader.read(java.util.Arrays.copyOf(cut, 20), 1024, 240).problem(), "PNG cortado");
        assertEquals(TvImageHeader.Problem.BAD_FORMAT, TvImageHeader.read(gif(8, 8, 1, 16, 8), 1024, 240).problem(),
                "quadro que sai da tela lógica");
        byte[] gif = gif(8, 8, 2, 8, 8);
        assertEquals(TvImageHeader.Problem.BAD_FORMAT,
                TvImageHeader.read(java.util.Arrays.copyOf(gif, gif.length - 6), 1024, 240).problem(),
                "sub-trama cortada no meio");
        byte[] arithmetic = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xC9, 0, 17, 8, 0, 10, 0, 10, 3, 0, 0, 0, 0,
                0, 0, 0, 0, 0, 0, 0};
        assertEquals(TvImageHeader.Problem.BAD_FORMAT, TvImageHeader.read(arithmetic, 1024, 240).problem(),
                "JPG aritmético, que o STB não abre");
        byte[] sosFirst = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xDA, 0, 8, 1, 2, 3, 4, 5, 6};
        assertEquals(TvImageHeader.Problem.BAD_FORMAT, TvImageHeader.read(sosFirst, 1024, 240).problem(),
                "JPG sem SOF antes dos dados");
    }

    @Test
    void resumoEIdDaImagem() {
        String hash = TvImageHash.of("abc".getBytes());
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", hash);
        assertTrue(TvImageHash.valid(hash));
        assertFalse(TvImageHash.valid(hash.toUpperCase()), "maiúsculas não");
        assertFalse(TvImageHash.valid("../" + hash.substring(3)), "nada de caminho");
        assertEquals(hash, TvImageHash.hex(TvImageHash.bytes(hash)));
        UUID id = TvImageHash.idOf(hash);
        assertEquals(8, id.version(), "versão 8: nunca a de uma arte (4)");
        assertEquals(2, id.variant());
        assertEquals(id, TvImageHash.idOf(hash), "sempre o mesmo id");
        assertNotEquals(id, TvImageHash.idOf(TvImageHash.of("abd".getBytes())));
    }

    @Test
    void tetosDoComecoDoEnvio() {
        TvImageLimits limits = TvImageLimits.DEFAULT;
        assertEquals(null, limits.check(TvImageFormat.PNG, 1024, 1024, 1, 1000));
        assertEquals(TvImageRefusal.FILE_TOO_BIG, limits.check(TvImageFormat.PNG, 10, 10, 1, (8 << 20) + 1));
        assertEquals(TvImageRefusal.TOO_LARGE, limits.check(TvImageFormat.JPG, 1025, 10, 1, 1000));
        assertEquals(TvImageRefusal.TOO_MANY_FRAMES, limits.check(TvImageFormat.PNG, 10, 10, 2, 1000),
                "só o GIF tem quadros");
        assertEquals(TvImageRefusal.TOO_MANY_FRAMES, limits.check(TvImageFormat.GIF, 10, 10, 241, 1000));
        assertEquals(null, limits.check(TvImageFormat.GIF, 10, 10, 240, 1000));
        assertEquals(TvImageRefusal.WORLD_FULL, limits.checkSpace(4L << 30, 0, 1));
        assertEquals(null, limits.withoutWorldCap().checkSpace(4L << 30, 0, 1), "sem teto no servidor integrado");
        assertEquals(null, limits.checkSpace(0, 1L << 40, 1), "a cota vem desligada");
    }
}
