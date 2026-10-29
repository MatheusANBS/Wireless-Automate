package io.github.matheusanbs.wirelessautomate.client.tv.pixel;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class TvPixelToolsTest {
    /** A arte como texto, uma linha por linha da arte, um dígito hexadecimal por pixel. */
    static String rows(TvPixelCanvas canvas) {
        StringBuilder out = new StringBuilder();
        for (int y = 0; y < canvas.height(); y++) {
            if (y > 0) {
                out.append('\n');
            }
            for (int x = 0; x < canvas.width(); x++) {
                out.append(Character.forDigit(canvas.get(x, y), 16));
            }
        }
        return out.toString();
    }

    /** As linhas de cima, até {@code count}, só as {@code cols} primeiras colunas. */
    static String corner(TvPixelCanvas canvas, int cols, int count) {
        StringBuilder out = new StringBuilder();
        for (int y = 0; y < count; y++) {
            if (y > 0) {
                out.append('\n');
            }
            for (int x = 0; x < cols; x++) {
                out.append(Character.forDigit(canvas.get(x, y), 16));
            }
        }
        return out.toString();
    }

    private static int count(TvPixelCanvas canvas, int index) {
        int n = 0;
        for (byte b : canvas.pixels()) {
            if (b == index) {
                n++;
            }
        }
        return n;
    }

    @Test
    void pincelQuadradoERedondo() {
        assertEquals(2, TvPixelBrush.offsets(1, true).length, "1 px é um ponto");
        assertEquals(9 * 2, TvPixelBrush.offsets(3, false).length);
        assertEquals(5 * 2, TvPixelBrush.offsets(3, true).length, "o redondo de 3 px é uma cruz");
        assertEquals(4 * 2, TvPixelBrush.offsets(2, true).length, "abaixo de 3 px o redondo é o quadrado");
        assertEquals(64 * 2, TvPixelBrush.offsets(8, false).length);
        assertTrue(TvPixelBrush.offsets(8, true).length < 64 * 2, "o de 8 px perde os cantos");
        assertArrayEquals(TvPixelBrush.offsets(8, false), TvPixelBrush.offsets(99, false), "acima de 8 é 8");
        int[] two = TvPixelBrush.offsets(2, false);
        assertArrayEquals(new int[] {0, 0, 1, 0, 0, 1, 1, 1}, two, "o par cresce para a direita e para baixo");
    }

    @Test
    void espelhosNaBordaDaArte() {
        TvPixelCanvas canvas = TvPixelCanvas.blank(8, 8);
        TvPixelTools tools = new TvPixelTools(canvas);
        tools.mirrors(true, true);
        tools.put(0, 0, 3);
        assertEquals(3, canvas.get(7, 0));
        assertEquals(3, canvas.get(0, 7));
        assertEquals(3, canvas.get(7, 7));
        assertEquals(4, count(canvas, 3));
        tools.brush(3, false);
        tools.stamp(0, 0, 5, false);
        assertEquals(4 * 4, count(canvas, 5), "o pincel cortado pela borda, nos quatro cantos");
    }

    @Test
    void espelhoComSelecaoSoPintaDentroDela() {
        TvPixelCanvas canvas = TvPixelCanvas.blank(8, 8);
        TvPixelTools tools = new TvPixelTools(canvas);
        tools.mirrors(true, false);
        tools.mask(0, 0, 4, 8);
        tools.put(1, 1, 2);
        assertEquals(2, canvas.get(1, 1));
        assertEquals(0, canvas.get(6, 1), "o reflexo caiu fora da seleção");
        tools.mask(0, 0, 8, 8);
        tools.put(1, 2, 2);
        assertEquals(2, canvas.get(6, 2));
    }

    @Test
    void pontilhadoSoNosParesENosReflexosSimetricos() {
        TvPixelCanvas canvas = TvPixelCanvas.blank(8, 8);
        TvPixelTools tools = new TvPixelTools(canvas);
        tools.brush(4, false);
        tools.stamp(2, 2, 1, true);
        for (int y = 0; y < 8; y++) {
            for (int x = 0; x < 8; x++) {
                if (canvas.get(x, y) != 0) {
                    assertEquals(0, (x + y) % 2, "pixel ímpar pintado em " + x + ", " + y);
                }
            }
        }
        assertEquals(8, count(canvas, 1));
        TvPixelCanvas mirrored = TvPixelCanvas.blank(8, 8);
        TvPixelTools both = new TvPixelTools(mirrored);
        both.mirrors(true, false);
        both.brush(2, false);
        both.stamp(1, 1, 1, true);
        assertEquals(mirrored.get(1, 1), mirrored.get(6, 1), "o reflexo do pontilhado é simétrico");
        assertEquals(mirrored.get(2, 2), mirrored.get(5, 2));
    }

    @Test
    void linhaDeBresenham() {
        TvPixelCanvas canvas = TvPixelCanvas.blank(8, 8);
        new TvPixelTools(canvas).line(0, 0, 7, 3, 1);
        assertEquals("""
                11000000
                00110000
                00001100
                00000011""", corner(canvas, 8, 4));
    }

    @Test
    void retanguloContornoECheio() {
        TvPixelCanvas canvas = TvPixelCanvas.blank(8, 8);
        TvPixelTools tools = new TvPixelTools(canvas);
        tools.rect(4, 3, 1, 1, 1, false);
        assertEquals("""
                00000000
                01111000
                01001000
                01111000""", corner(canvas, 8, 4));
        tools.rect(1, 1, 4, 3, 2, true);
        assertEquals(12, count(canvas, 2));
    }

    @Test
    void elipseDeUmPixelEContorno() {
        TvPixelCanvas canvas = TvPixelCanvas.blank(8, 8);
        TvPixelTools tools = new TvPixelTools(canvas);
        tools.ellipse(3, 3, 3, 3, 1, false);
        assertEquals(1, count(canvas, 1), "uma elipse de 1 px é o pixel");
        assertEquals(1, canvas.get(3, 3));
        canvas.clear();
        tools.ellipse(0, 0, 6, 6, 1, false);
        assertEquals(0, canvas.get(3, 3), "o miolo do contorno fica vazio");
        assertEquals(1, canvas.get(3, 0));
        assertEquals(0, canvas.get(0, 0), "o canto fica de fora");
        canvas.clear();
        tools.ellipse(0, 0, 6, 6, 2, true);
        assertEquals(2, canvas.get(3, 3));
        assertEquals(0, canvas.get(0, 0));
    }

    @Test
    void baldeDeVizinhancaQuatroSemPassarPelaDiagonal() {
        TvPixelCanvas canvas = TvPixelCanvas.blank(8, 8);
        TvPixelTools tools = new TvPixelTools(canvas);
        tools.line(0, 3, 3, 0, 1);
        tools.fill(0, 0, 2, false);
        assertEquals(2, canvas.get(0, 0));
        assertEquals(2, canvas.get(1, 1));
        assertEquals(0, canvas.get(7, 7), "a diagonal fecha a área");
        tools.fill(7, 7, 3, false);
        assertEquals(3, canvas.get(4, 4));
        assertEquals(64 - 4 - count(canvas, 2), count(canvas, 3));
    }

    @Test
    void baldeGrandeSemRecursaoEGlobal() {
        TvPixelCanvas canvas = TvPixelCanvas.blank(64, 64);
        TvPixelTools tools = new TvPixelTools(canvas);
        tools.fill(10, 10, 4, false);
        assertEquals(64 * 64, count(canvas, 4));
        canvas.set(5, 5, 7);
        canvas.set(60, 60, 7);
        tools.fill(5, 5, 9, true);
        assertEquals(2, count(canvas, 9), "com Shift, a cor muda na arte inteira");
        tools.mask(0, 0, 32, 32);
        tools.fill(0, 0, 1, true);
        assertEquals(32 * 32 - 1, count(canvas, 1), "o global respeita a seleção");
    }

    @Test
    void clarearUmaVezPorPixelPorTracoENaLinhaDaRampa() {
        TvPixelCanvas canvas = TvPixelCanvas.blank(8, 8);
        TvPixelTools tools = new TvPixelTools(canvas);
        canvas.set(0, 0, 5);
        canvas.set(1, 0, 7);
        canvas.set(2, 0, 1);
        tools.beginShade();
        tools.shade(0, 0, 1);
        tools.shade(0, 0, 1);
        assertEquals(6, canvas.get(0, 0), "uma casa só no mesmo traço");
        tools.shade(1, 0, 1);
        assertEquals(7, canvas.get(1, 0), "a ponta clara da linha não pula para a seguinte");
        tools.shade(2, 0, -1);
        assertEquals(1, canvas.get(2, 0), "a linha 0 nunca escurece até o transparente");
        tools.beginShade();
        tools.shade(0, 0, 1);
        assertEquals(7, canvas.get(0, 0), "no traço seguinte anda de novo");
    }
}
