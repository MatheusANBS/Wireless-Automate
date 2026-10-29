package io.github.matheusanbs.wirelessautomate.client.tv.pixel;

/**
 * A vista do quadro do editor de pixel art, sem barra de rolagem (decisão 1g da spec da etapa 4): o zoom em níveis
 * de {@link #ZOOMS} px por pixel da arte e o deslocamento do canto da arte em relação ao canto do quadro. A roda dá
 * zoom no ponto do mouse (o pixel sob ele fica parado), arrastar move a vista, e enquadrar escolhe o maior nível em
 * que a arte cabe com {@link #FIT_MARGIN} px de folga e a centra. Lógica pura, com JUnit.
 */
public final class TvPixelView {
    /** O 1 é para a arte de 128 × 128 caber inteira no quadro da janela mínima. */
    static final int[] ZOOMS = {1, 2, 3, 4, 6, 8, 10, 12, 16, 20, 24, 32, 40, 48};
    public static final int MIN_ZOOM = ZOOMS[0];
    public static final int MAX_ZOOM = ZOOMS[ZOOMS.length - 1];
    static final int FIT_MARGIN = 24;

    private int zoom = 20;
    private int panX;
    private int panY;

    public int zoom() {
        return zoom;
    }

    /** O canto de cima à esquerda da arte, relativo ao canto do quadro. */
    public int panX() {
        return panX;
    }

    public int panY() {
        return panY;
    }

    /** O pixel da arte sob o ponto do quadro (pode cair fora da arte). */
    public int cellX(double boardX) {
        return (int) Math.floor((boardX - panX) / zoom);
    }

    public int cellY(double boardY) {
        return (int) Math.floor((boardY - panY) / zoom);
    }

    /** O maior nível em que uma arte {@code artW} × {@code artH} cabe no quadro, centrada. */
    public void fit(int boardW, int boardH, int artW, int artH) {
        double room = Math.min((boardW - FIT_MARGIN) / (double) artW, (boardH - FIT_MARGIN) / (double) artH);
        int chosen = ZOOMS[0];
        for (int level : ZOOMS) {
            if (level <= room) {
                chosen = level;
            }
        }
        zoom = chosen;
        panX = Math.round((boardW - artW * zoom) / 2f);
        panY = Math.round((boardH - artH * zoom) / 2f);
    }

    /** Um nível acima ({@code dir} 1) ou abaixo (-1), mantendo parado o ponto ({@code mx}, {@code my}) do quadro. */
    public boolean zoomAt(double mx, double my, int dir) {
        int at = 0;
        while (at < ZOOMS.length - 1 && ZOOMS[at] < zoom) {
            at++;
        }
        int next = ZOOMS[Math.max(0, Math.min(ZOOMS.length - 1, at + Integer.signum(dir)))];
        if (next == zoom) {
            return false;
        }
        panX = (int) Math.round(mx - (mx - panX) * next / (double) zoom);
        panY = (int) Math.round(my - (my - panY) * next / (double) zoom);
        zoom = next;
        return true;
    }

    /** Leva a vista até o pixel ({@code x}, {@code y}), no centro do quadro. */
    public void centerOn(double x, double y, int boardW, int boardH) {
        panX = (int) Math.round(boardW / 2.0 - (x + 0.5) * zoom);
        panY = (int) Math.round(boardH / 2.0 - (y + 0.5) * zoom);
    }

    public void panTo(int x, int y) {
        panX = x;
        panY = y;
    }

    public void panBy(int dx, int dy) {
        panX += dx;
        panY += dy;
    }
}
