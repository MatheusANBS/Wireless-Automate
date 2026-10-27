package io.github.matheusanbs.wirelessautomate.client.tv.editor;

/**
 * A conversão entre HSV (matiz, saturação e brilho, de 0 a 1) e RGB do seletor de cor. Lógica pura, com JUnit; só
 * roda em evento do seletor, nunca por quadro.
 */
final class TvHsv {
    private TvHsv() {
    }

    /** O RGB ({@code 0xRRGGBB}) de um matiz {@code h} (volta em 1), uma saturação {@code s} e um brilho {@code v}. */
    static int toRgb(float h, float s, float v) {
        float hue = (h - (float) Math.floor(h)) * 6f;
        int sector = (int) hue;
        float f = hue - sector;
        float p = v * (1 - s);
        float q = v * (1 - s * f);
        float t = v * (1 - s * (1 - f));
        float r;
        float g;
        float b;
        switch (sector) {
            case 0 -> { r = v; g = t; b = p; }
            case 1 -> { r = q; g = v; b = p; }
            case 2 -> { r = p; g = v; b = t; }
            case 3 -> { r = p; g = q; b = v; }
            case 4 -> { r = t; g = p; b = v; }
            default -> { r = v; g = p; b = q; }
        }
        return Math.round(r * 255) << 16 | Math.round(g * 255) << 8 | Math.round(b * 255);
    }

    /** O matiz, a saturação e o brilho de um RGB, nessa ordem. */
    static float[] fromRgb(int rgb) {
        float r = (rgb >> 16 & 0xFF) / 255f;
        float g = (rgb >> 8 & 0xFF) / 255f;
        float b = (rgb & 0xFF) / 255f;
        float max = Math.max(r, Math.max(g, b));
        float min = Math.min(r, Math.min(g, b));
        float delta = max - min;
        float h;
        if (delta == 0) {
            h = 0;
        } else if (max == r) {
            h = ((g - b) / delta % 6 + 6) % 6 / 6f;
        } else if (max == g) {
            h = ((b - r) / delta + 2) / 6f;
        } else {
            h = ((r - g) / delta + 4) / 6f;
        }
        return new float[] {h, max == 0 ? 0 : delta / max, max};
    }
}
