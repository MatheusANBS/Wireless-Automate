package io.github.matheusanbs.wirelessautomate.client.tablet.kit;

/**
 * A geometria dos segmentos do Tablet (mockup {@code segmentos}): cada parte tem o texto mais 12 px (mais 17 com
 * ícone), e a seguinte começa 1 px antes do fim da anterior, dividindo a borda. Pura, com JUnit.
 */
public final class SegmentLayout {
    private SegmentLayout() {
    }

    public static int[] widths(int[] textWidths, boolean icons) {
        int[] out = new int[textWidths.length];
        for (int i = 0; i < out.length; i++) {
            out[i] = textWidths[i] + 12 + (icons ? 17 : 0);
        }
        return out;
    }

    public static int[] lefts(int x, int[] widths) {
        int[] out = new int[widths.length];
        for (int i = 0; i < widths.length; i++) {
            out[i] = x;
            x += widths[i] - 1;
        }
        return out;
    }

    /** A parte sob {@code mx}, ou −1; na borda dividida vale a da direita. */
    public static int at(int x, int[] widths, double mx) {
        int hit = -1;
        for (int i = 0; i < widths.length; i++) {
            if (mx >= x && mx < x + widths[i]) {
                hit = i;
            }
            x += widths[i] - 1;
        }
        return hit;
    }
}
