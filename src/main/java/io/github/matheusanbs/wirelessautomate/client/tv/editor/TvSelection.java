package io.github.matheusanbs.wirelessautomate.client.tv.editor;

import java.util.Arrays;

/**
 * A escolha do editor: um conjunto imutável de índices de peças, guardado em ordem crescente e sem repetição. Puro,
 * com JUnit; criado só quando a escolha muda.
 */
public final class TvSelection {
    public static final TvSelection EMPTY = new TvSelection(new int[0]);

    private final int[] items;

    private TvSelection(int[] items) {
        this.items = items;
    }

    public static TvSelection of(int... indices) {
        if (indices.length == 0) {
            return EMPTY;
        }
        int[] sorted = indices.clone();
        Arrays.sort(sorted);
        int n = 0;
        for (int i = 0; i < sorted.length; i++) {
            if (i == 0 || sorted[i] != sorted[i - 1]) {
                sorted[n++] = sorted[i];
            }
        }
        return new TvSelection(n == sorted.length ? sorted : Arrays.copyOf(sorted, n));
    }

    /** Os índices de {@code from} (incluso) a {@code to} (excluso). */
    public static TvSelection range(int from, int to) {
        if (to <= from) {
            return EMPTY;
        }
        int[] items = new int[to - from];
        for (int i = 0; i < items.length; i++) {
            items[i] = from + i;
        }
        return new TvSelection(items);
    }

    public int size() {
        return items.length;
    }

    public boolean isEmpty() {
        return items.length == 0;
    }

    /** O {@code k}-ésimo índice, em ordem crescente. */
    public int get(int k) {
        return items[k];
    }

    public int first() {
        return items[0];
    }

    public int last() {
        return items[items.length - 1];
    }

    public boolean contains(int index) {
        return Arrays.binarySearch(items, index) >= 0;
    }

    /** A escolha com {@code index} tirado se estava, ou posto se não estava. */
    public TvSelection toggle(int index) {
        int at = Arrays.binarySearch(items, index);
        if (at >= 0) {
            int[] out = new int[items.length - 1];
            System.arraycopy(items, 0, out, 0, at);
            System.arraycopy(items, at + 1, out, at, items.length - at - 1);
            return out.length == 0 ? EMPTY : new TvSelection(out);
        }
        int insert = -at - 1;
        int[] out = new int[items.length + 1];
        System.arraycopy(items, 0, out, 0, insert);
        out[insert] = index;
        System.arraycopy(items, insert, out, insert + 1, items.length - insert);
        return new TvSelection(out);
    }

    /** Só os índices abaixo de {@code count} (depois de uma troca de layout com menos peças). */
    public TvSelection below(int count) {
        int n = 0;
        while (n < items.length && items[n] < count) {
            n++;
        }
        return n == items.length ? this : n == 0 ? EMPTY : new TvSelection(Arrays.copyOf(items, n));
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof TvSelection selection && Arrays.equals(items, selection.items);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(items);
    }

    @Override
    public String toString() {
        return Arrays.toString(items);
    }
}
