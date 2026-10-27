package io.github.matheusanbs.wirelessautomate.client.tv.editor;

/**
 * O tamanho da aba Editar: o que o jogador pediu arrastando a borda, lembrado na sessão (a tela abre de novo no mesmo
 * tamanho), e a regra que o prende ao mínimo do editor e à janela. O pedido fica guardado como foi feito: uma janela
 * menor só limita o que se vê, e ao voltar a crescer o tamanho pedido volta. Lógica pura, com JUnit.
 */
public final class TvEditorSize {
    /** O tamanho com que a aba abre na primeira vez, antes de o jogador arrastar a borda. */
    public static final int DEFAULT_W = 560;
    public static final int DEFAULT_H = 340;

    private static int wantedW = DEFAULT_W;
    private static int wantedH = DEFAULT_H;

    private TvEditorSize() {
    }

    /**
     * O tamanho a usar: {@code wanted} entre {@code min} e {@code max}; numa janela menor que o mínimo vale o máximo
     * (a aba mostra então só o pedido de uma escala menor), nunca menos que 1.
     */
    public static int fit(int wanted, int min, int max) {
        return Math.max(1, max < min ? max : Math.max(min, Math.min(max, wanted)));
    }

    /** Arredonda um tamanho pedido pelo arrasto para um número par, para o painel ficar centrado sem meio pixel. */
    public static int even(double wanted) {
        return (int) Math.round(wanted / 2) * 2;
    }

    public static int wantedWidth() {
        return wantedW;
    }

    public static int wantedHeight() {
        return wantedH;
    }

    public static void remember(int width, int height) {
        wantedW = width;
        wantedH = height;
    }
}
