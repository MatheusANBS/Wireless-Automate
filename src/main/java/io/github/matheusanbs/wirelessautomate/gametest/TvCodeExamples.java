package io.github.matheusanbs.wirelessautomate.gametest;

import java.util.Random;

/**
 * Os códigos legíveis que os testes colam (o {@link TvCodeGameTests}, o {@link TvPixelAnimGameTests} e o e2e da
 * Tela), os mesmos dos exemplos do guia: uma arte 16 × 16, um layout 3 × 2 com Texto, Valor (as horas do relógio) e a
 * Imagem da mesma arte, com cores em {@code #} e por papel do tema, a arte animada e o layout com quadros no JSON; e
 * um céu animado de 128 × 128 gerado aqui.
 */
public final class TvCodeExamples {
    public static final String ART = """
            WA-ARTE1
            nome: Coração
            tamanho: 16x16
            paleta: - 1B1920 7A7366 ECE5D6 8E2A35 C2442A F0603E FFB08A
            # 1 é o contorno, 4 e 5 o vermelho, 6 e 7 o brilho
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

    /**
     * O exemplo da seção "Pixel art animada (quadros)" do guia, com o cabeçalho e as chaves como lá (o guia deixa as
     * fileiras como "(16 linhas de 16 caracteres)"): o coração aceso no quadro 1 e apagado no 2.
     */
    public static final String ART_ANIMATED = """
            WA-ARTE1
            nome: Sinal piscando
            tamanho: 16x16
            paleta: - 1B1920 7A7366 ECE5D6 8E2A35 C2442A F0603E FFB08A
            quadros: 2
            velocidade: 4
            modo: loop
            quadro 1:
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
            quadro 2:
            ................
            ................
            ...1111..1111...
            ..144441144441..
            .14444444444441.
            .14444444444441.
            .14444444444441.
            .14444444444441.
            ..144444444441..
            ...1444444441...
            ....14444441....
            .....144441.....
            ......1441......
            .......11.......
            ................
            ................
            """;

    /** Um layout com uma arte animada no JSON: {@code quadros} no lugar de {@code linhas}, com velocidade e modo. */
    public static final String LAYOUT_ANIMATED = """
            WA-TELA1
            {
              "area_w": 64,
              "area_h": 32,
              "pieces": [
                {"type": "image", "x": 0, "y": 0, "w": 32, "h": 32,
                 "style": {"image": "pisca", "scale": 2}}
              ],
              "images": [
                {"id": "pisca", "nome": "Pisca", "tamanho": "8x8", "paleta": "- C2442A",
                 "velocidade": 3, "modo": "vaivem",
                 "quadros": [
                   ["........", "..1111..", ".111111.", ".111111.", ".111111.", ".111111.", "..1111..", "........"],
                   ["........", "........", "...11...", "..1111..", "..1111..", "...11...", "........", "........"],
                   ["........", "........", "........", "...11...", "...11...", "........", "........", "........"]
                 ]}
              ]
            }
            """;

    public static final String LAYOUT = """
            WA-TELA1
            {
              "area_w": 96,
              "area_h": 64,
              "background": {"kind": "solid", "top": "#1B1920"},
              "pieces": [
                {"type": "text", "x": 4, "y": 4, "w": 88, "h": 10,
                 "style": {"text": "Base norte", "color": "tema:texto", "size": 8}},
                {"type": "value", "x": 4, "y": 20, "w": 56, "h": 16,
                 "bind": {"source": "clock", "metric": "time"},
                 "style": {"color": "#FFB08A", "size": 12}},
                {"type": "image", "x": 60, "y": 28, "w": 32, "h": 32,
                 "style": {"image": "coracao", "scale": 2}}
              ],
              "images": [
                {"id": "coracao", "nome": "Coração", "tamanho": "16x16",
                 "paleta": ["-", "1B1920", "7A7366", "ECE5D6", "8E2A35", "C2442A", "F0603E", "FFB08A"],
                 "linhas": [
                   "................",
                   "................",
                   "...1111..1111...",
                   "..155551155551..",
                   ".15677555555551.",
                   ".15775555555551.",
                   ".15755555555551.",
                   ".15555555555541.",
                   "..155555555541..",
                   "...1555555541...",
                   "....15555541....",
                   ".....155541.....",
                   "......1541......",
                   ".......11.......",
                   "................",
                   "................"
                 ]}
              ]
            }
            """;

    private TvCodeExamples() {
    }

    /**
     * Um céu noturno de 128 × 128 em 6 quadros e 15 cores, no código legível, como o de uma IA que o dono colou: o céu
     * em faixas com pontilhado entre elas, uma lua, 300 estrelas espalhadas e, de quadro em quadro, só algumas
     * estrelas piscando (os quadros quase iguais). Somam 98.304 pixels, dentro do teto padrão da Tela (131.072).
     */
    public static String nightSky() {
        int side = 128;
        int frames = 6;
        StringBuilder out = new StringBuilder(frames * side * (side + 1) + 400);
        out.append("WA-ARTE1\nnome: Céu noturno\ntamanho: 128x128\n");
        out.append("paleta: - 0B0D1F 141838 1D2452 283170 34408E 4A5AAE 6E7FC8 FFFFFF FFF2B0 F5D76E D9B04A 9AA6E0 ")
                .append("C8D0F5 E86A5A 2A1E3C\n");
        out.append("quadros: 6\nvelocidade: 6\nmodo: loop\n");
        Random random = new Random(128);
        int[] stars = new int[300];
        for (int i = 0; i < stars.length; i++) {
            stars[i] = random.nextInt(side * side * 3 / 4);
        }
        for (int f = 0; f < frames; f++) {
            out.append("quadro ").append(f + 1).append(":\n");
            char[][] rows = new char[side][side];
            for (int y = 0; y < side; y++) {
                int band = Math.min(5, y / 20);
                boolean dither = y % 20 >= 16;
                for (int x = 0; x < side; x++) {
                    int color = 1 + band + (dither && (x + y) % 2 == 0 ? 1 : 0);
                    rows[y][x] = y >= 112 ? (x * 7 % 13 < 6 ? 'f' : '1') : Character.forDigit(color, 16);
                }
            }
            for (int i = 0; i < stars.length; i++) {
                int at = stars[i];
                boolean twinkle = (i + f) % 23 == 0;
                rows[at / side][at % side] = twinkle ? 'c' : i % 5 == 0 ? '9' : '8';
            }
            for (int y = 14; y < 34; y++) {
                for (int x = 90; x < 110; x++) {
                    int dx = x - 100;
                    int dy = y - 24;
                    if (dx * dx + dy * dy <= 90) {
                        rows[y][x] = dx * dx + dy * dy > 60 ? 'b' : (dx + dy) % 5 == 0 ? 'a' : '9';
                    }
                }
            }
            for (char[] row : rows) {
                out.append(row).append('\n');
            }
        }
        return out.toString();
    }
}
