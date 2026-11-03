package io.github.matheusanbs.wirelessautomate.client.tablet.kit;

import java.util.function.IntUnaryOperator;

/**
 * As fichas dos convidados numa linha só (mockup {@code rede_detalhe}): cada uma com o texto mais 16 px, 4 px entre
 * elas, e o link "+ convidar" 4 px depois da última. O que não cabe antes de {@code right} vira uma ficha "+N", e o link
 * cabe sempre. Pura, com JUnit.
 */
public final class ChipLayout {
    private static final int PAD = 16;
    private static final int GAP = 4;

    private ChipLayout() {
    }

    /** {@code moreX} é −1 sem a ficha "+N". */
    public record Placed(int[] xs, int shown, int moreX, int linkX) {
    }

    public static Placed place(int[] textWidths, int x, int right, int linkW, IntUnaryOperator moreTextW) {
        int n = textWidths.length;
        int[] xs = new int[n];
        int at = x;
        int shown = 0;
        for (int i = 0; i < n; i++) {
            int w = textWidths[i] + PAD;
            int hidden = n - i - 1;
            int tail = (hidden > 0 ? GAP + moreTextW.applyAsInt(hidden) + PAD : 0) + GAP + GAP + linkW;
            if (at + w + tail > right + 1) {
                break;
            }
            xs[shown++] = at;
            at += w + GAP;
        }
        int moreX = -1;
        if (shown < n) {
            moreX = at;
            at += moreTextW.applyAsInt(n - shown) + PAD + GAP;
        }
        int[] placed = new int[shown];
        System.arraycopy(xs, 0, placed, 0, shown);
        return new Placed(placed, shown, moreX, at + GAP);
    }
}
