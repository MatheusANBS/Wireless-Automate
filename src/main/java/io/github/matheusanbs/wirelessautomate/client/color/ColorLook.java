package io.github.matheusanbs.wirelessautomate.client.color;

/**
 * As medidas de um visual do {@link ColorPopup}, em px a partir do canto da janela e sem a linha das cores do tema
 * (que, quando existe, fica em {@code themeY} e empurra o resto {@code themeRow} px para baixo). {@code framed}: o visual
 * do Tablet novo (janela com sombra e relevo, bordas {@code FRAME} no quadrado e nas faixas, campo e marcas como no
 * mockup); sem ele, o da Tela de Sinal.
 */
public record ColorLook(int width, int pad, int svW, int svH, int svY, int strip, int stripGap, int hexY, int hexW,
        int hexH, int eyeDx, int eyeY, int noneDx, int recentY, int recent, int recentStep, boolean recentLabel,
        boolean framed, int themeY, int themeRow) {
    /** A Tela de Sinal: 124 de largura, quadrado 84 × 56, faixas de 8. */
    public static final ColorLook SIGNAL = new ColorLook(124, 6, 84, 56, 14, 8, 4, 76, 70, 11, 74, 74, 92, 101, 11, 13,
            true, false, 14, 18);
    /** O Tablet (mockup {@code popup_cor}: 116 × 96, quadrado 88 × 56, matiz de 10, campo 104 × 14, recentes de 8). */
    public static final ColorLook TABLET = new ColorLook(116, 6, 88, 56, 6, 10, 6, 66, 104, 14, 0, 0, 0, 84, 8, 12,
            false, true, 0, 0);

    /** A altura sem a linha do tema. */
    public int baseHeight() {
        return recentY + recent + 4;
    }
}
