package io.github.matheusanbs.wirelessautomate.client.tablet;

/**
 * A linha escolhida na lista de sugestões de um campo: as setas andam, dando a volta; sem linha escolhida
 * ({@code -1}) o Enter usa o texto digitado. Lógica pura, testada por JUnit.
 */
final class SuggestNav {
    private int size;
    private int selected = -1;

    /** Uma lista nova chegou: nenhuma linha escolhida. */
    void reset(int size) {
        this.size = Math.max(0, size);
        this.selected = -1;
    }

    int size() {
        return size;
    }

    /** A linha escolhida, ou -1. */
    int selected() {
        return selected;
    }

    /** Escolhe a linha sob o mouse; fora da lista, não muda nada. */
    void select(int index) {
        if (index >= 0 && index < size) {
            selected = index;
        }
    }

    /** Larga a linha escolhida (o Enter volta a usar o texto digitado). */
    void clearSelection() {
        selected = -1;
    }

    void next() {
        if (size > 0) {
            selected = (selected + 1) % size;
        }
    }

    void previous() {
        if (size > 0) {
            selected = selected <= 0 ? size - 1 : selected - 1;
        }
    }
}
