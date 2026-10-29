package io.github.matheusanbs.wirelessautomate.client.tv.pixel;

import java.util.Arrays;

/**
 * As primitivas de desenho do editor de pixel art sobre um {@link TvPixelCanvas}: o ponto com os espelhos e a
 * máscara, o carimbo do pincel (com o pontilhado), a linha de Bresenham, o retângulo e a elipse (contorno com o
 * pincel ou cheios), o balde de vizinhança 4 sem recursão (uma pilha de índices, cada pixel empilhado uma vez) e o
 * clarear e escurecer por rampa, uma vez por pixel por traço. Os espelhos refletem em torno do meio da arte, na
 * horizontal (esquerda e direita), na vertical (em cima e embaixo) ou nos dois; a máscara é a seleção: com ela, nada
 * muda fora do retângulo. As pilhas e marcas são reusadas entre os traços. Lógica pura, com JUnit.
 */
public final class TvPixelTools {
    private final TvPixelCanvas canvas;
    private boolean mirrorH;
    private boolean mirrorV;
    private int brushSize = TvPixelBrush.MIN;
    private boolean round;
    private int maskX;
    private int maskY;
    private int maskW;
    private int maskH;
    private int[] stack = new int[0];
    private int[] seen = new int[0];
    private int seenMark;

    public TvPixelTools(TvPixelCanvas canvas) {
        this.canvas = canvas;
    }

    public void mirrors(boolean horizontal, boolean vertical) {
        mirrorH = horizontal;
        mirrorV = vertical;
    }

    public void brush(int size, boolean round) {
        this.brushSize = size;
        this.round = round;
    }

    /** Só o retângulo pinta; largura 0 tira a máscara. */
    public void mask(int x, int y, int w, int h) {
        maskX = x;
        maskY = y;
        maskW = w;
        maskH = h;
    }

    private boolean paintable(int x, int y) {
        return canvas.inside(x, y) && (maskW <= 0 || x >= maskX && y >= maskY && x < maskX + maskW
                && y < maskY + maskH);
    }

    private void putOne(int x, int y, int index) {
        if (paintable(x, y)) {
            canvas.set(x, y, index);
        }
    }

    /** Um pixel e os reflexos dele nos espelhos ligados. */
    public void put(int x, int y, int index) {
        int mx = canvas.width() - 1 - x;
        int my = canvas.height() - 1 - y;
        putOne(x, y, index);
        if (mirrorH) {
            putOne(mx, y, index);
        }
        if (mirrorV) {
            putOne(x, my, index);
        }
        if (mirrorH && mirrorV) {
            putOne(mx, my, index);
        }
    }

    /** O pixel ({@code x}, {@code y}) volta ao de {@code base}, com os reflexos (o pixel perfect desfaz um canto). */
    void restore(int x, int y, byte[] base) {
        int mx = canvas.width() - 1 - x;
        int my = canvas.height() - 1 - y;
        restoreOne(x, y, base);
        if (mirrorH) {
            restoreOne(mx, y, base);
        }
        if (mirrorV) {
            restoreOne(x, my, base);
        }
        if (mirrorH && mirrorV) {
            restoreOne(mx, my, base);
        }
    }

    private void restoreOne(int x, int y, byte[] base) {
        if (canvas.inside(x, y)) {
            canvas.set(x, y, base[y * canvas.width() + x]);
        }
    }

    /**
     * O pincel com o centro em ({@code x}, {@code y}); no pontilhado, só os pixels do pincel com {@code x + y} par
     * (contado no pixel desenhado, antes dos espelhos, para o reflexo sair simétrico).
     */
    public void stamp(int x, int y, int index, boolean dither) {
        int[] offsets = TvPixelBrush.offsets(brushSize, round);
        for (int i = 0; i < offsets.length; i += 2) {
            int px = x + offsets[i];
            int py = y + offsets[i + 1];
            if (!dither || (px + py & 1) == 0) {
                put(px, py, index);
            }
        }
    }

    /** A linha de Bresenham de ({@code x0}, {@code y0}) a ({@code x1}, {@code y1}), carimbando o pincel. */
    public void line(int x0, int y0, int x1, int y1, int index) {
        int dx = Math.abs(x1 - x0);
        int dy = -Math.abs(y1 - y0);
        int sx = x0 < x1 ? 1 : -1;
        int sy = y0 < y1 ? 1 : -1;
        int err = dx + dy;
        int x = x0;
        int y = y0;
        while (true) {
            stamp(x, y, index, false);
            if (x == x1 && y == y1) {
                return;
            }
            int e2 = 2 * err;
            if (e2 >= dy) {
                err += dy;
                x += sx;
            }
            if (e2 <= dx) {
                err += dx;
                y += sy;
            }
        }
    }

    /** O retângulo entre os dois cantos: cheio pixel a pixel, ou o contorno com o pincel. */
    public void rect(int x0, int y0, int x1, int y1, int index, boolean filled) {
        int left = Math.min(x0, x1);
        int right = Math.max(x0, x1);
        int top = Math.min(y0, y1);
        int bottom = Math.max(y0, y1);
        if (filled) {
            for (int y = top; y <= bottom; y++) {
                for (int x = left; x <= right; x++) {
                    put(x, y, index);
                }
            }
            return;
        }
        for (int x = left; x <= right; x++) {
            stamp(x, top, index, false);
            stamp(x, bottom, index, false);
        }
        for (int y = top; y <= bottom; y++) {
            stamp(left, y, index, false);
            stamp(right, y, index, false);
        }
    }

    /**
     * A elipse inscrita no retângulo entre os dois cantos: um pixel entra se o centro dele cai dentro da elipse de
     * raios (lado / 2); o contorno são os de dentro com algum vizinho de 4 fora, carimbados com o pincel. Uma elipse
     * de 1 px é o próprio pixel.
     */
    public void ellipse(int x0, int y0, int x1, int y1, int index, boolean filled) {
        int left = Math.min(x0, x1);
        int right = Math.max(x0, x1);
        int top = Math.min(y0, y1);
        int bottom = Math.max(y0, y1);
        double cx = (left + right) / 2.0;
        double cy = (top + bottom) / 2.0;
        double rx = (right - left) / 2.0 + 0.5;
        double ry = (bottom - top) / 2.0 + 0.5;
        for (int y = top; y <= bottom; y++) {
            for (int x = left; x <= right; x++) {
                if (!inEllipse(x, y, cx, cy, rx, ry)) {
                    continue;
                }
                if (filled) {
                    put(x, y, index);
                } else if (!inEllipse(x - 1, y, cx, cy, rx, ry) || !inEllipse(x + 1, y, cx, cy, rx, ry)
                        || !inEllipse(x, y - 1, cx, cy, rx, ry) || !inEllipse(x, y + 1, cx, cy, rx, ry)) {
                    stamp(x, y, index, false);
                }
            }
        }
    }

    private static boolean inEllipse(int x, int y, double cx, double cy, double rx, double ry) {
        double nx = (x - cx) / rx;
        double ny = (y - cy) / ry;
        return nx * nx + ny * ny <= 1;
    }

    /**
     * O balde: a área de vizinhança 4 da cor do pixel clicado vira {@code to}, a partir dele e dos reflexos; com
     * {@code global}, a cor muda em todos os pixels dela (dentro da máscara).
     */
    public void fill(int x, int y, int to, boolean global) {
        if (!canvas.inside(x, y)) {
            return;
        }
        if (global) {
            int from = canvas.get(x, y);
            for (int py = 0; py < canvas.height(); py++) {
                for (int px = 0; px < canvas.width(); px++) {
                    if (canvas.get(px, py) == from && paintable(px, py)) {
                        canvas.set(px, py, to);
                    }
                }
            }
            return;
        }
        int mx = canvas.width() - 1 - x;
        int my = canvas.height() - 1 - y;
        flood(x, y, to);
        if (mirrorH) {
            flood(mx, y, to);
        }
        if (mirrorV) {
            flood(x, my, to);
        }
        if (mirrorH && mirrorV) {
            flood(mx, my, to);
        }
    }

    private void flood(int x, int y, int to) {
        if (!paintable(x, y)) {
            return;
        }
        int from = canvas.get(x, y);
        if (from == to) {
            return;
        }
        int w = canvas.width();
        if (stack.length < w * canvas.height()) {
            stack = new int[w * canvas.height()];
        }
        int top = 0;
        canvas.set(x, y, to);
        stack[top++] = y * w + x;
        while (top > 0) {
            int k = stack[--top];
            int px = k % w;
            int py = k / w;
            top = visit(px + 1, py, from, to, top);
            top = visit(px - 1, py, from, to, top);
            top = visit(px, py + 1, from, to, top);
            top = visit(px, py - 1, from, to, top);
        }
    }

    /** Pinta e empilha o vizinho da cor de origem; pintar antes de empilhar garante cada pixel uma vez só. */
    private int visit(int x, int y, int from, int to, int top) {
        if (paintable(x, y) && canvas.get(x, y) == from) {
            canvas.set(x, y, to);
            stack[top++] = y * canvas.width() + x;
        }
        return top;
    }

    /** Começa um traço do clarear: cada pixel anda no máximo uma casa até o próximo {@code beginShade}. */
    public void beginShade() {
        int size = canvas.width() * canvas.height();
        if (seen.length < size) {
            seen = new int[size];
        }
        if (++seenMark == Integer.MAX_VALUE) {
            Arrays.fill(seen, 0);
            seenMark = 1;
        }
    }

    /** Clareia ({@code dir} 1) ou escurece (-1) o pincel em ({@code x}, {@code y}) e os reflexos, pela rampa. */
    public void shade(int x, int y, int dir) {
        int[] offsets = TvPixelBrush.offsets(brushSize, round);
        for (int i = 0; i < offsets.length; i += 2) {
            int px = x + offsets[i];
            int py = y + offsets[i + 1];
            int mx = canvas.width() - 1 - px;
            int my = canvas.height() - 1 - py;
            shadeOne(px, py, dir);
            if (mirrorH) {
                shadeOne(mx, py, dir);
            }
            if (mirrorV) {
                shadeOne(px, my, dir);
            }
            if (mirrorH && mirrorV) {
                shadeOne(mx, my, dir);
            }
        }
    }

    private void shadeOne(int x, int y, int dir) {
        if (!paintable(x, y)) {
            return;
        }
        int k = y * canvas.width() + x;
        if (seen[k] == seenMark) {
            return;
        }
        seen[k] = seenMark;
        canvas.set(x, y, TvPixelRamp.step(canvas.get(x, y), dir));
    }
}
