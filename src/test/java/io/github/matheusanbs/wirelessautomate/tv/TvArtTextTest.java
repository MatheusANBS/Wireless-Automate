package io.github.matheusanbs.wirelessautomate.tv;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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

    /** As 16 fileiras do coração do guia (o quadro 1) e do coração apagado (o quadro 2). */
    private static final String HEART_16 = """
            ................
            ................
            ...1111..1111...
            ..155551155551..
            .15677555555551.
            .15775555555551.
            .15755555555551.
            .15555555555541.
            ..155555555541..
            ...1555555541...
            ....15555541....
            .....155541.....
            ......1541......
            .......11.......
            ................
            ................
            """;
    private static final String DIM_16 = HEART_16.replace('5', '4').replace('6', '4').replace('7', '4');

    /**
     * O exemplo da seção "Pixel art animada (quadros)" dos guias de IA, com as chaves e os blocos escritos como lá; o
     * guia deixa as fileiras como "(16 linhas de 16 caracteres)", então elas vêm do coração dos exemplos testados.
     */
    private static String guideExample(boolean english) {
        String head = english ? """
                WA-ARTE1
                name: Blinking signal
                size: 16x16
                palette: - 1B1920 7A7366 ECE5D6 8E2A35 C2442A F0603E FFB08A
                frames: 2
                speed: 4
                mode: loop
                """ : """
                WA-ARTE1
                nome: Sinal piscando
                tamanho: 16x16
                paleta: - 1B1920 7A7366 ECE5D6 8E2A35 C2442A F0603E FFB08A
                quadros: 2
                velocidade: 4
                modo: loop
                """;
        String frame = english ? "frame " : "quadro ";
        return head + frame + "1:\n" + HEART_16 + frame + "2:\n" + DIM_16;
    }

    private static TvArtText.Art animatedNoise(int w, int h, int frames) {
        TvArtText.Art still = noise(w, h * frames);
        return new TvArtText.Art("Ruído", w, h, still.palette(), still.pixels(), frames, 9, TvPixels.Mode.PINGPONG);
    }

    @Test
    void oExemploAnimadoDosGuiasLeComoEsta() {
        for (boolean english : new boolean[] {false, true}) {
            TvArtText.Art art = ok(guideExample(english));
            assertEquals(english ? "Blinking signal" : "Sinal piscando", art.name());
            assertEquals(2, art.frames());
            assertEquals(4, art.fps());
            assertEquals(TvPixels.Mode.LOOP, art.mode());
            assertEquals(2 * 16 * 16, art.pixels().length);
            assertEquals(5, art.pixels()[3 * 16 + 3], "o quadro 1 é o coração");
            assertEquals(4, art.pixels()[256 + 3 * 16 + 3], "o quadro 2 é o apagado");
        }
    }

    @Test
    void idaEVoltaDaArteAnimadaNasDuasLinguas() {
        TvArtText.Art art = animatedNoise(9, 8, 5);
        for (boolean english : new boolean[] {false, true}) {
            String text = TvArtText.format(art, english);
            assertTrue(text.contains(english ? "frames: 5\nspeed: 9\nmode: pingpong\nframe 1:\n"
                    : "quadros: 5\nvelocidade: 9\nmodo: vaivem\nquadro 1:\n"), text);
            assertTrue(text.contains(english ? "\nframe 5:\n" : "\nquadro 5:\n"), text);
            TvArtText.Art back = ok(text);
            assertEquals(5, back.frames());
            assertEquals(9, back.fps());
            assertEquals(TvPixels.Mode.PINGPONG, back.mode());
            assertArrayEquals(art.pixels(), back.pixels());
            assertArrayEquals(art.palette(), back.palette());
        }
    }

    @Test
    void artesParadasContinuamComoAntes() {
        TvArtText.Art art = ok(HEART);
        assertEquals(1, art.frames());
        assertEquals(TvPixels.DEFAULT_FPS, art.fps());
        String text = TvArtText.format(art, false);
        assertFalse(text.contains("quadro"), "a arte parada não ganha as chaves novas: " + text);
        assertEquals(HEART, text.replace("paleta: - 1B1920 C2442A" + " 000000".repeat(13),
                "paleta: - 1B1920 C2442A"), "o texto da arte parada é o de antes");
        TvArtText.Art one = ok(HEART.replace("paleta:", "quadros: 1\npaleta:"));
        assertArrayEquals(art.pixels(), one.pixels(), "quadros: 1 com as linhas direto vale");
    }

    @Test
    void blocosSemAChaveSaoContados() {
        String text = guideExample(false).replace("quadros: 2\n", "");
        assertEquals(2, ok(text).frames());
    }

    @Test
    void cadaErroDosQuadros() {
        String pt = guideExample(false);
        fails(pt.replace("quadros: 2", "quadros: 65"), "code.art.frames", 5, 1, 64);
        fails(pt.replace("quadros: 2", "quadros: dois"), "code.art.frames", 5, 1, 64);
        fails(pt.replace("quadros: 2", "quadros: 3"), "code.art.frames_count", 2, 3);
        fails(pt.replace("velocidade: 4", "velocidade: 21"), "code.art.speed", 6, 1, 20);
        fails(pt.replace("velocidade: 4", "velocidade: 0"), "code.art.speed", 6, 1, 20);
        fails(pt.replace("modo: loop", "modo: girar"), "code.art.mode", 7, "girar");
        fails(pt.replace("quadro 2:", "quadro 3:"), "code.art.frame_order", 25, 2);
        fails(pt.replace("quadro 1:\n", ""), "code.art.frame_order", 8, 1);
        int second = 8 + 16 + 1;
        fails(pt.replace("quadro 2:\n" + DIM_16, "quadro 2:\n" + DIM_16.substring(17)), "code.art.frame_rows", 2,
                15, 16);
        fails(pt.replace("quadro 2:\n................\n", "quadro 2:\n...............\n"), "code.art.row",
                second + 1, 15, 16);
        fails(pt.replace("quadros: 2\n", "").replace("quadro 1:\n", ""), "code.art.frame_order", 7, 1);
        assertEquals("loop", TvArtText.modeName(TvPixels.Mode.LOOP, true));
        assertEquals("vaivem", TvArtText.modeName(TvPixels.Mode.PINGPONG, false));
        assertEquals(TvPixels.Mode.PINGPONG, ok(pt.replace("modo: loop", "MODE: PingPong")).mode());
    }
}
