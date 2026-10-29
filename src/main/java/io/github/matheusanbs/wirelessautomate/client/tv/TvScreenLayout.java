package io.github.matheusanbs.wirelessautomate.client.tv;

/**
 * As medidas da tela da Tela de Sinal em pixels de interface a partir do canto da tela: o cabeçalho, comum às abas, a
 * aba Tamanho, 320 × 220 de tamanho fixo, que segue o mockup aprovado ({@code docs/preview/tela-de-sinal.html},
 * "Escolher o tamanho"): os controles à esquerda e a prévia da parede à direita, e a aba Acesso, do mesmo tamanho. A
 * aba Editar tem as medidas dela no {@code TvEditorLayout}.
 */
final class TvScreenLayout {
    static final int W = 320;
    static final int H = 220;

    static final Rect TITLE = new Rect(8, 8, 80, 9);
    static final Rect SIZE_TAB = new Rect(92, 5, 64, 14);
    static final Rect EDIT_TAB = new Rect(160, 5, 64, 14);
    /** Só para o dono e os operadores; termina antes do botão do tema, que fica a 8 px da borda na aba de 320. */
    static final Rect ACCESS_TAB = new Rect(228, 5, 64, 14);
    static final int THEME_Y = 6;
    /** A largura do "Descartar?" que o ✕ armado ganha à esquerda. */
    static final int DISCARD_W = 64;
    /** A linha embaixo do cabeçalho. */
    static final int RULE_Y = 22;

    static final Rect WIDTH_LABEL = new Rect(8, 30, 142, 9);
    static final int WIDTH_ROW = 41;
    static final Rect HEIGHT_LABEL = new Rect(8, 60, 142, 9);
    static final int HEIGHT_ROW = 71;
    static final int STEP = 14;
    static final int MINUS_X = 8;
    static final int VALUE_X = 24;
    static final int VALUE_W = 30;
    static final int PLUS_X = 56;
    static final int UNIT_X = 74;
    static final int UNIT_W = 76;
    /** "Cresce a partir do bloco para", em até duas linhas. */
    static final Rect GROW_LABEL = new Rect(8, 90, 142, 20);
    static final int GROW_X = 8;
    static final int GROW_Y = 111;
    static final int GROW_W = 142;
    static final int GROW_H = 14;
    static final int GROW_GAP = 2;
    static final Rect APPLY = new Rect(8, 166, 72, 16);
    /** "Área livre: N blocos" ou o problema. */
    static final Rect MESSAGE = new Rect(8, 192, 304, 9);
    /** A prévia da parede, afundada. */
    static final Rect PREVIEW = new Rect(158, 28, 154, 154);

    /** Aba Acesso: o que a lista dá, em até duas linhas, e o aviso da última ação. */
    static final Rect ACCESS_HINT = new Rect(8, 28, 304, 20);
    static final Rect ACCESS_NOTICE = new Rect(8, 50, 304, 9);
    /** O rótulo dos convidados; o painel de acesso segue dali até o fim da aba. */
    static final int ACCESS_LABEL_Y = 62;

    private TvScreenLayout() {
    }

    /** Os três botões do crescer, na ordem do {@code TvArea.Grow}. */
    static final Rect[] GROW_BUTTONS = {growButton(0), growButton(1), growButton(2)};

    private static Rect growButton(int index) {
        return new Rect(GROW_X, GROW_Y + index * (GROW_H + GROW_GAP), GROW_W, GROW_H);
    }

    /** Um retângulo relativo ao canto da tela. */
    record Rect(int x, int y, int w, int h) {
        boolean contains(int left, int top, double mouseX, double mouseY) {
            return mouseX >= left + x && mouseX < left + x + w && mouseY >= top + y && mouseY < top + y + h;
        }

        int centerX(int left) {
            return left + x + w / 2;
        }

        int centerY(int top) {
            return top + y + h / 2;
        }
    }
}
