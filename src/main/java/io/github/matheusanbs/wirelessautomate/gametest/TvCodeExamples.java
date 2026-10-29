package io.github.matheusanbs.wirelessautomate.gametest;

/**
 * Os dois códigos legíveis escritos à mão que os testes colam (o {@link TvCodeGameTests} e o e2e da Tela) e que o guia
 * usa de exemplo: uma arte 16 × 16 e um layout 3 × 2 com Texto, Valor (as horas do relógio) e a Imagem da mesma
 * arte, com cores em {@code #} e por papel do tema.
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
}
