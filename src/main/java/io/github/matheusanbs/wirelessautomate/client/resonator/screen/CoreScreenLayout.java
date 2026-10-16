package io.github.matheusanbs.wirelessautomate.client.resonator.screen;

/**
 * As medidas da tela do Núcleo, em pixels de interface a partir do canto da tela (as do mockup aprovado,
 * {@code docs/preview/ressonador-etapa3.html}). Os slots ficam no {@code ResonatorMenu}, 1 px para dentro do fundo.
 */
final class CoreScreenLayout {
    static final int W = 352;
    static final int H = 244;
    static final int HEAD_Y = 8;
    static final int X0 = 10;
    static final int BUTTON = 14;
    static final int RIGHT_W = 152;
    /** Altura de uma linha de texto da coluna da direita. */
    static final int LINE_H = 8;

    static final Rect SCOPE = new Rect(10, 28, 172, 50);
    static final Rect POINTER = new Rect(10, 80, 172, 22);
    static final Rect LAMP = new Rect(10, 106, 172, 8);
    static final Rect DIAL = new Rect(10, 120, 172, BUTTON);
    static final Rect MODE = new Rect(10, 138, 172, BUTTON);
    static final Rect CRYSTALS_LABEL = new Rect(190, 28, RIGHT_W, LINE_H);
    static final Rect CRYSTAL_SLOTS = new Rect(190, 38, RIGHT_W, 18);
    static final Rect REDSTONE_SLOT = new Rect(190, 60, 18, 18);
    static final Rect EFFICIENCY = new Rect(190, 84, RIGHT_W, LINE_H);
    static final Rect EFFICIENCY_BAR = new Rect(190, 94, RIGHT_W, 6);
    static final Rect GENERATION = new Rect(190, 106, RIGHT_W, LINE_H);
    static final Rect ENERGY = new Rect(190, 120, RIGHT_W, LINE_H);
    static final Rect ENERGY_BAR = new Rect(190, 130, RIGHT_W, 6);
    static final Rect ENERGY_HINT = new Rect(190, 140, RIGHT_W, LINE_H);
    static final Rect INVENTORY = new Rect(10, 158, 162, 54);
    static final Rect HOTBAR = new Rect(10, 216, 162, 18);
    static final Rect STRUCTURE = new Rect(190, 158, RIGHT_W, LINE_H);
    static final int[] STRUCTURE_LINES = {170, 182, 194, 206};

    /** Os botões do dial: « e ‹ à esquerda do campo, › e » à direita. */
    static final int[] DIAL_BUTTONS = {10, 26, 152, 168};
    static final int FIELD_X = 42;
    static final int FIELD_W = 108;
    static final int MANUAL_W = 64;
    static final int AUTO_X = 78;
    static final int AUTO_W = 104;

    private CoreScreenLayout() {
    }

    /** Um retângulo relativo ao canto da tela. */
    record Rect(int x, int y, int w, int h) {
        boolean contains(int left, int top, double mouseX, double mouseY) {
            return mouseX >= left + x && mouseX < left + x + w && mouseY >= top + y && mouseY < top + y + h;
        }
    }
}
