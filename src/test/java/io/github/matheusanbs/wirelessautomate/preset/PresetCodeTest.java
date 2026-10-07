package io.github.matheusanbs.wirelessautomate.preset;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.matheusanbs.wirelessautomate.preset.PresetCode.InvalidCodeException;
import io.github.matheusanbs.wirelessautomate.preset.PresetCode.Problem;
import java.nio.charset.StandardCharsets;
import java.util.Random;
import org.junit.jupiter.api.Test;

class PresetCodeTest {
    private static Problem problem(String code) {
        return assertThrows(InvalidCodeException.class, () -> PresetCode.decode(code)).problem();
    }

    @Test
    void roundTrip() throws Exception {
        byte[] raw = "faces: itens em cima extrai, embaixo insere".repeat(20).getBytes(StandardCharsets.UTF_8);
        String code = PresetCode.encode(raw);
        assertTrue(code.startsWith("WA1:"));
        assertTrue(code.length() < raw.length, "comprime texto repetido");
        assertTrue(code.substring(4).matches("[A-Za-z0-9_-]+"), "Base64 URL-safe sem preenchimento: " + code);
        assertArrayEquals(raw, PresetCode.decode(code));
    }

    @Test
    void roundTripRandomBytesAndEmpty() throws Exception {
        byte[] raw = new byte[5000];
        new Random(42).nextBytes(raw);
        assertArrayEquals(raw, PresetCode.decode(PresetCode.encode(raw)));
        assertArrayEquals(new byte[0], PresetCode.decode(PresetCode.encode(new byte[0])));
    }

    @Test
    void toleratesSpacesLineBreaksAndPrefixCase() throws Exception {
        byte[] raw = "abc".getBytes(StandardCharsets.UTF_8);
        String code = PresetCode.encode(raw);
        String broken = "  wa1:" + code.substring(4, 8) + "\n" + code.substring(8) + "  ";
        assertArrayEquals(raw, PresetCode.decode(broken));
    }

    @Test
    void rejectsWrongPrefixAndEmpty() {
        String body = PresetCode.encode(new byte[] {1, 2, 3}).substring(4);
        assertEquals(Problem.PREFIX, problem("WA2:" + body));
        assertEquals(Problem.PREFIX, problem(body));
        assertEquals(Problem.EMPTY, problem("   "));
        assertEquals(Problem.EMPTY, problem(null));
        assertEquals(Problem.CORRUPT, problem("WA1:"));
    }

    @Test
    void rejectsGarbage() {
        assertEquals(Problem.BASE64, problem("WA1:@@@@"));
        assertEquals(Problem.CORRUPT, problem("WA1:AAAAAAAA"));
        String code = PresetCode.encode("um preset qualquer".getBytes(StandardCharsets.UTF_8));
        assertEquals(Problem.CORRUPT, problem(code.substring(0, code.length() - 3)));
    }

    @Test
    void rejectsTooLongAndBombs() {
        assertEquals(Problem.TOO_LONG, problem("WA1:" + "A".repeat(PresetCode.MAX_CODE_LENGTH)));
        // zeros comprimem muito: um código pequeno que descomprime além do limite
        String bomb = PresetCode.encode(new byte[PresetCode.MAX_RAW_BYTES + 1]);
        assertTrue(PresetCode.fits(bomb));
        assertEquals(Problem.TOO_LARGE, problem(bomb));
    }
}
