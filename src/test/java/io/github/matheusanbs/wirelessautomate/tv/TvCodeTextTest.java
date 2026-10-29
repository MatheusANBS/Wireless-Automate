package io.github.matheusanbs.wirelessautomate.tv;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Random;
import java.util.zip.Deflater;
import org.junit.jupiter.api.Test;

class TvCodeTextTest {
    private static final String TELA = "WA-TELA1:";
    private static final String ARTE = "WA-ARTE1:";

    private static byte[] noise(int size) {
        byte[] bytes = new byte[size];
        new Random(7).nextBytes(bytes);
        return bytes;
    }

    @Test
    void idaEVoltaComBytesQuaisquer() {
        for (byte[] raw : new byte[][] {new byte[0], "Tela".getBytes(StandardCharsets.UTF_8), noise(70_000)}) {
            String code = TvCodeText.encode(TELA, raw);
            assertTrue(code.startsWith(TELA));
            assertArrayEquals(raw, TvCodeText.decode(TELA, code, 256 * 1024));
        }
    }

    @Test
    void oCodigoEUmaLinhaSoEmBase64UrlSafe() {
        String code = TvCodeText.encode(ARTE, noise(5000));
        String body = code.substring(ARTE.length());
        assertTrue(body.chars().allMatch(c -> Character.isLetterOrDigit(c) || c == '-' || c == '_'), body);
    }

    @Test
    void aceitaEspacosEQuebrasDeLinhaEmVoltaENoMeio() {
        byte[] raw = noise(300);
        String code = TvCodeText.encode(TELA, raw);
        String body = code.substring(TELA.length());
        String messy = "  \n\t" + TELA + body.substring(0, 50) + "\r\n " + body.substring(50) + "\n\n  ";
        assertArrayEquals(raw, TvCodeText.decode(TELA, messy, 1024));
    }

    @Test
    void prefixoErradoNaoLe() {
        String code = TvCodeText.encode(ARTE, noise(100));
        assertNull(TvCodeText.decode(TELA, code, 1024), "um código de arte não é de layout");
        assertNull(TvCodeText.decode(TELA, code.substring(3), 1024));
        assertNull(TvCodeText.decode(TELA, null, 1024));
    }

    @Test
    void lixoNaoLe() {
        assertNull(TvCodeText.decode(TELA, "lixo", 1024));
        assertNull(TvCodeText.decode(TELA, TELA, 1024), "sem corpo");
        assertNull(TvCodeText.decode(TELA, TELA + "não é base64!", 1024));
        assertNull(TvCodeText.decode(TELA, TELA + "QUJDREVGR0g", 1024), "base64 que não é deflate");
        String code = TvCodeText.encode(TELA, noise(2000));
        assertNull(TvCodeText.decode(TELA, code.substring(0, code.length() - 40), 4096), "cortado no meio");
        assertNull(TvCodeText.decode(TELA, code + "AAAA", 4096), "sobra depois do fim");
    }

    @Test
    void textoGrandeDemaisNemELido() {
        String code = TvCodeText.encode(TELA, noise(10));
        StringBuilder big = new StringBuilder(TvCodeText.MAX_TEXT + 1).append(code);
        while (big.length() <= TvCodeText.MAX_TEXT) {
            big.append(' ');
        }
        assertNull(TvCodeText.decode(TELA, big.toString(), 1024));
        assertFalse(code.length() > TvCodeText.MAX_TEXT);
    }

    @Test
    void bombaDeDescompressaoParaNoTeto() {
        // 64 MB de zeros viram uns 64 KB de deflate: a leitura tem que parar logo depois do teto
        Deflater deflater = new Deflater(Deflater.BEST_COMPRESSION);
        byte[] zeros = new byte[1024 * 1024];
        java.io.ByteArrayOutputStream packed = new java.io.ByteArrayOutputStream();
        byte[] chunk = new byte[8192];
        for (int i = 0; i < 64; i++) {
            deflater.setInput(zeros);
            while (!deflater.needsInput()) {
                packed.write(chunk, 0, deflater.deflate(chunk));
            }
        }
        deflater.finish();
        while (!deflater.finished()) {
            packed.write(chunk, 0, deflater.deflate(chunk));
        }
        deflater.end();
        String bomb = TELA + Base64.getUrlEncoder().withoutPadding().encodeToString(packed.toByteArray());
        assertTrue(bomb.length() < TvCodeText.MAX_TEXT, "a bomba cabe no teto do texto");
        assertNull(TvCodeText.decode(TELA, bomb, 256 * 1024));
        byte[] exact = new byte[256 * 1024];
        assertArrayEquals(exact, TvCodeText.decode(TELA, TvCodeText.encode(TELA, exact), 256 * 1024),
                "o teto exato ainda lê");
    }
}
