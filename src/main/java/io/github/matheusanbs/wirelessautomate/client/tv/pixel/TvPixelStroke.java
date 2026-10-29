package io.github.matheusanbs.wirelessautomate.client.tv.pixel;

/**
 * Um traço do mouse no editor de pixel art, do apertar ao soltar. As ferramentas de mão livre (lápis, borracha,
 * pontilhado, clarear) pintam cada pixel da linha de Bresenham entre a posição anterior do mouse e a nova, sem repetir
 * o último; o pixel perfect (lápis de 1 px) desfaz o canto de um L assim que o terceiro ponto o forma, devolvendo o
 * pixel do meio ao que era antes do traço. As formas (linha, retângulo, elipse) voltam a arte à cópia do começo e se
 * desenham de novo do ponto do clique até o mouse a cada movimento; com Shift, a linha trava em 0°, 45° e 90° e o
 * retângulo e a elipse ficam com os lados iguais. A cópia do começo é reusada entre os traços. Lógica pura, com JUnit.
 */
final class TvPixelStroke {
    private final TvPixelCanvas canvas;
    private final TvPixelTools tools;
    private byte[] base = new byte[0];
    private final int[] recentX = new int[3];
    private final int[] recentY = new int[3];
    private int recent;
    private TvPixelTool tool = TvPixelTool.PEN;
    private int index;
    private int dir;
    private boolean perfect;
    private boolean filled;
    private int startX;
    private int startY;
    private int lastX;
    private int lastY;

    TvPixelStroke(TvPixelCanvas canvas, TvPixelTools tools) {
        this.canvas = canvas;
        this.tools = tools;
    }

    /**
     * Começa o traço em ({@code x}, {@code y}): {@code index} é a cor (0 apaga), {@code dir} o sentido do clarear,
     * {@code perfect} o pixel perfect e {@code filled} a forma cheia.
     */
    void begin(TvPixelTool tool, int index, int x, int y, int dir, boolean perfect, boolean filled) {
        this.tool = tool;
        this.index = index;
        this.dir = dir;
        this.perfect = perfect;
        this.filled = filled;
        byte[] pixels = canvas.pixels();
        if (base.length != pixels.length) {
            base = new byte[pixels.length];
        }
        System.arraycopy(pixels, 0, base, 0, pixels.length);
        startX = x;
        startY = y;
        lastX = x;
        lastY = y;
        recent = 0;
        if (tool.freehand()) {
            tools.beginShade();
            plot(x, y);
        } else {
            shape(x, y, false);
        }
    }

    void moveTo(int x, int y, boolean shift) {
        if (!tool.freehand()) {
            shape(x, y, shift);
            return;
        }
        int dx = Math.abs(x - lastX);
        int dy = -Math.abs(y - lastY);
        int sx = lastX < x ? 1 : -1;
        int sy = lastY < y ? 1 : -1;
        int err = dx + dy;
        int px = lastX;
        int py = lastY;
        while (true) {
            plot(px, py);
            if (px == x && py == y) {
                break;
            }
            int e2 = 2 * err;
            if (e2 >= dy) {
                err += dy;
                px += sx;
            }
            if (e2 <= dx) {
                err += dx;
                py += sy;
            }
        }
        lastX = x;
        lastY = y;
    }

    private void plot(int x, int y) {
        if (recent > 0 && recentX[recent - 1] == x && recentY[recent - 1] == y) {
            return;
        }
        switch (tool) {
            case SHADE -> tools.shade(x, y, dir);
            case DITHER -> tools.stamp(x, y, index, true);
            default -> tools.stamp(x, y, index, false);
        }
        if (recent == 3) {
            recentX[0] = recentX[1];
            recentY[0] = recentY[1];
            recentX[1] = recentX[2];
            recentY[1] = recentY[2];
            recent = 2;
        }
        recentX[recent] = x;
        recentY[recent] = y;
        recent++;
        if (perfect && recent == 3) {
            unCorner();
        }
    }

    /** Os três últimos pontos fazem um L (o primeiro e o terceiro na diagonal): o do meio volta ao que era. */
    private void unCorner() {
        int ax = recentX[0];
        int ay = recentY[0];
        int bx = recentX[1];
        int by = recentY[1];
        int cx = recentX[2];
        int cy = recentY[2];
        boolean diagonal = Math.abs(ax - cx) == 1 && Math.abs(ay - cy) == 1;
        boolean corner = (bx == ax || by == ay) && (bx == cx || by == cy);
        if (diagonal && corner) {
            tools.restore(bx, by, base);
            recentX[1] = cx;
            recentY[1] = cy;
            recent = 2;
        }
    }

    private void shape(int x, int y, boolean shift) {
        canvas.copyFrom(base);
        int ex = x;
        int ey = y;
        if (shift && tool == TvPixelTool.LINE) {
            int dx = x - startX;
            int dy = y - startY;
            if (Math.abs(dx) > 2 * Math.abs(dy)) {
                ey = startY;
            } else if (Math.abs(dy) > 2 * Math.abs(dx)) {
                ex = startX;
            } else {
                int m = Math.max(Math.abs(dx), Math.abs(dy));
                ex = startX + Integer.signum(dx) * m;
                ey = startY + Integer.signum(dy) * m;
            }
        } else if (shift) {
            int dx = x - startX;
            int dy = y - startY;
            int m = Math.max(Math.abs(dx), Math.abs(dy));
            ex = startX + (dx < 0 ? -m : m);
            ey = startY + (dy < 0 ? -m : m);
        }
        switch (tool) {
            case LINE -> tools.line(startX, startY, ex, ey, index);
            case RECT -> tools.rect(startX, startY, ex, ey, index, filled);
            default -> tools.ellipse(startX, startY, ex, ey, index, filled);
        }
    }
}
