package io.github.matheusanbs.wirelessautomate.client.tv.editor;

/** Um retângulo do editor em coordenadas da tela (já com o canto da janela somado). Puro. */
public record TvRect(int x, int y, int w, int h) {
    public static final TvRect NONE = new TvRect(0, 0, 0, 0);

    public boolean contains(double mx, double my) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    public int right() {
        return x + w;
    }

    public int bottom() {
        return y + h;
    }

    public int centerX() {
        return x + w / 2;
    }

    public int centerY() {
        return y + h / 2;
    }

    public int[] center() {
        return new int[] {centerX(), centerY()};
    }
}
