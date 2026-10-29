package io.github.matheusanbs.wirelessautomate.client.tv.pixel;

/** As ferramentas do editor de pixel art, com a tecla de cada uma (decisão 1a da spec da etapa 4). */
public enum TvPixelTool {
    PEN('b'),
    ERASE('e'),
    FILL('g'),
    PICK('i'),
    LINE('l'),
    RECT('r'),
    ELLIPSE('o'),
    SHADE('d'),
    DITHER('t'),
    SELECT('s');

    private static final TvPixelTool[] ALL = values();

    private final char key;

    TvPixelTool(char key) {
        this.key = key;
    }

    public char key() {
        return key;
    }

    /** Pinta seguindo o mouse, ponto a ponto (o resto desenha uma forma do clique até o mouse). */
    public boolean freehand() {
        return this == PEN || this == ERASE || this == SHADE || this == DITHER;
    }

    /** Usa o pincel: o quadro mostra o pincel sob o cursor. */
    public boolean brushed() {
        return freehand() || this == LINE || this == RECT || this == ELLIPSE;
    }

    /** O clique direito apaga com a mesma ferramenta (as outras viram a borracha; o clarear escurece). */
    boolean erasesWithSelf() {
        return this == LINE || this == RECT || this == ELLIPSE || this == DITHER;
    }

    /** A ferramenta da tecla (minúscula), ou {@code null}. */
    public static TvPixelTool forKey(char key) {
        for (TvPixelTool tool : ALL) {
            if (tool.key == key) {
                return tool;
            }
        }
        return null;
    }
}
