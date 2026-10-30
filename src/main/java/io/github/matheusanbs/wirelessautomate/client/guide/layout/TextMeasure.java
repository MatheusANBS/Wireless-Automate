package io.github.matheusanbs.wirelessautomate.client.guide.layout;

/** A medida de texto em unidades de folha, na escala 1; o cliente implementa com o {@code Font}, o JUnit com uma falsa. */
public interface TextMeasure {
    /** Largura do texto no estilo dado, na escala 1. */
    int width(String text, TextStyle style);

    int lineHeight();

    /** O tamanho de uma imagem das páginas, em unidades; sem a imagem à mão, um quadrado de 32. */
    default Size imageSize(String path) {
        return new Size(32, 32);
    }

    record Size(int w, int h) {}
}
