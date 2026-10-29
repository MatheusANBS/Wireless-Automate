package io.github.matheusanbs.wirelessautomate.tv;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

class TvArtTextTest {
    private static final String HEART = """
            WA-ARTE1
            nome: Coração
            tamanho: 8x8
            paleta: - 1B1920 C2442A
            .11..11.
            12211221
            12222221
            12222221
            .122221.
            ..1221..
            ...11...
            ........
            """;

    private static TvArtText.Art noise(int w, int h) {
        Random random = new Random(5);
        int[] palette = new int[16];
        for (int i = 1; i < 16; i++) {
            palette[i] = random.nextInt() | (i == 3 ? 0 : 0xFF000000);
        }
        palette[3] = 0x80123456;
        byte[] pixels = new byte[w * h];
        for (int i = 0; i < pixels.length; i++) {
            pixels[i] = (byte) random.nextInt(16);
        }
        return new TvArtText.Art("Ruído", w, h, palette, pixels);
    }

    private static TvArtText.Art ok(String text) {
        TvArtText.Parsed parsed = TvArtText.parse(text);
        assertNull(parsed.error(), () -> parsed.error() + " " + List.of(parsed.args()));
        return parsed.art();
    }

    private static void fails(String text, String key, Object... args) {
        TvArtText.Parsed parsed = TvArtText.parse(text);
        assertNull(parsed.art());
        assertEquals(key, parsed.error());
        assertArrayEquals(args, parsed.args(), () -> List.of(parsed.args()).toString());
    }

    @Test
    void leOCoracao() {
        TvArtText.Art art = ok(HEART);
        assertEquals("Coração", art.name());
        assertEquals(8, art.width());
        assertEquals(0, art.palette()[0]);
        assertEquals(0xFF1B1920, art.palette()[1]);
        assertEquals(0xFFC2442A, art.palette()[2]);
        assertEquals(0xFF000000, art.palette()[3], "as casas que faltam ficam pretas");
        assertEquals(2, art.pixels()[8 + 1]);
        assertEquals(0, art.pixels()[0]);
    }

    @Test
    void idaEVoltaNasDuasLinguas() {
        TvArtText.Art art = noise(13, 9);
        for (boolean english : new boolean[] {false, true}) {
            String text = TvArtText.format(art, english);
            assertTrue(text.contains(english ? "size: 13x9" : "tamanho: 13x9"), text);
            TvArtText.Art back = ok(text);
            assertEquals(art.name(), back.name());
            assertArrayEquals(art.palette(), back.palette());
            assertArrayEquals(art.pixels(), back.pixels());
        }
    }

    @Test
    void chavesEmInglesMaiusculasEspacosEComentarios() {
        String text = """

                  wa-arte1
                # um comentário
                NAME:   Heart
                Size: 8 x 8

                Palette: transparent #1b1920 C2442AFF
                .11. .11.
                # outro comentário
                1221 1221
                12222221
                12222221
                .122221.
                ..1221..
                ...11...
                ........
                """;
        TvArtText.Art art = ok(text);
        assertEquals("Heart", art.name());
        assertEquals(0xFFC2442A, art.palette()[2]);
        assertArrayEquals(ok(HEART).pixels(), art.pixels());
    }

    @Test
    void cadaErroDizALinha() {
        fails("nada", "code.invalid");
        fails(HEART.replace("tamanho: 8x8", "tamanho: 200x8"), "code.art.size", 3, 8, 128);
        fails(HEART.replace("tamanho: 8x8\n", ""), "code.art.size_missing");
        fails(HEART.replace("paleta:", "cores:"), "code.art.key", 4, "cores");
        fails(HEART.replace("1B1920", "1B19"), "code.art.color", 4, "1B19");
        fails(HEART.replace("paleta: -", "paleta: azul"), "code.art.color", 4, "azul");
        fails(HEART.replace("paleta: - 1B1920 C2442A", "paleta: - " + "112233 ".repeat(16)), "code.art.palette_long",
                4, 16);
        fails(HEART.replace("12222221\n.122221.", "12222221\n.12z221."), "code.art.pixel", 9, "z");
        fails(HEART.replace("..1221..", "..1221."), "code.art.row", 10, 7, 8);
        fails(HEART.replace("........\n", ""), "code.art.rows", 7, 8);
        fails(HEART + "........\n", "code.art.rows", 9, 8);
        fails(HEART.replace("...11...", "nome: x"), "code.art.pixel", 11, "n");
    }

    @Test
    void profundidadeDoJsonForaDasStrings() {
        assertEquals(0, TvCodeText.jsonDepth("\"[[[\""));
        assertEquals(3, TvCodeText.jsonDepth("{\"a\": [ {\"b\": \"]}\\\"[\"} ]}"));
        assertEquals(200, TvCodeText.jsonDepth("[".repeat(200)));
        assertNotNull(TvArtText.rows(noise(8, 8)));
    }
}
