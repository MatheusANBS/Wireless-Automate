package io.github.matheusanbs.wirelessautomate.client.tablet;

import java.util.Arrays;

/**
 * Os textos já medidos de cada linha da Lista, por índice, guardados enquanto a chave não muda: a página (o
 * snapshot, comparado por identidade), a árvore das linhas (identidade), o tipo dos quadradinhos e a versão do
 * tema (as cores vão dentro dos textos). Cada linha guarda também a largura com que foi medida; outra largura a
 * refaz sozinha. Nada aloca enquanto a chave e a largura ficam iguais.
 *
 * @param <T> o texto pronto de uma linha
 */
final class LineCache<T> {
    private Object page;
    private Object tree;
    private Object type;
    private int theme;
    private boolean keyed;
    private Object[] values = new Object[0];
    private int[] widths = new int[0];

    /** Confere a chave; se mudou, esquece tudo e passa a guardar {@code size} linhas. */
    void key(Object page, Object tree, Object type, int theme, int size) {
        if (keyed && page == this.page && tree == this.tree && type == this.type && theme == this.theme
                && values.length == size) {
            return;
        }
        keyed = true;
        this.page = page;
        this.tree = tree;
        this.type = type;
        this.theme = theme;
        if (values.length == size) {
            Arrays.fill(values, null);
        } else {
            values = new Object[size];
            widths = new int[size];
        }
    }

    /** O texto da linha {@code i} medido em {@code width}, ou {@code null} se falta montar. */
    @SuppressWarnings("unchecked")
    T get(int i, int width) {
        if (i < 0 || i >= values.length || widths[i] != width) {
            return null;
        }
        return (T) values[i];
    }

    void put(int i, int width, T value) {
        if (i >= 0 && i < values.length) {
            values[i] = value;
            widths[i] = width;
        }
    }
}
