package io.github.matheusanbs.wirelessautomate.client.tv;

/**
 * O tamanho da fonte (em pixels de desenho da Tela) para um texto caber na caixa da peça: começa no máximo
 * da peça, não passa da altura da caixa e encolhe na proporção da largura. Nunca abaixo de 1 pixel. Lógica
 * pura, com JUnit.
 */
public final class TvFit {
    private TvFit() {
    }

    /**
     * @param widthAtSizeOne a largura do texto com a fonte de 1 pixel de altura
     * @param fit            a opção "Diminuir a fonte para caber"; desligada, o tamanho é o máximo
     */
    public static float size(float maxSize, float boxWidth, float boxHeight, float widthAtSizeOne, boolean fit) {
        if (!fit) {
            return maxSize;
        }
        float size = Math.min(maxSize, boxHeight);
        if (widthAtSizeOne > 0 && widthAtSizeOne * size > boxWidth) {
            size = boxWidth / widthAtSizeOne;
        }
        return Math.max(1f, size);
    }
}
