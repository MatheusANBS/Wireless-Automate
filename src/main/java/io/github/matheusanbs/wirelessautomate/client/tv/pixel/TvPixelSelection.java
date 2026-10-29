package io.github.matheusanbs.wirelessautomate.client.tv.pixel;

/**
 * A seleção do editor de pixel art (decisão 1e da spec da etapa 4): um retângulo marcado dentro da arte, ou um
 * pedaço solto (os pixels levantados da arte, que andam por cima dela e podem sair dos limites). Levantar
 * ({@link #lift}) tira os pixels da arte e deixa o buraco transparente; assentar ({@link #commit}) devolve o pedaço
 * onde ele está, sem cobrir nada com o transparente dele, e marca a parte que ficou dentro da arte. O desfazer é de
 * quem chama: ele guarda a arte antes de levantar. A {@link #version} sobe a cada mudança do que se vê (o pedaço e a
 * caixa). Lógica pura, com JUnit.
 */
public final class TvPixelSelection {
    /** Uma cópia de pixels (a área de transferência e o pedaço solto): {@code width} × {@code height} índices. */
    public record Clip(int width, int height, byte[] pixels) {
    }

    private int x;
    private int y;
    private int w;
    private int h;
    private boolean floating;
    private byte[] piece = new byte[0];
    private int version;

    /** Há algo selecionado: um retângulo marcado ou um pedaço solto. */
    public boolean active() {
        return w > 0;
    }

    public boolean floating() {
        return floating;
    }

    public int x() {
        return x;
    }

    public int y() {
        return y;
    }

    public int width() {
        return w;
    }

    public int height() {
        return h;
    }

    public int version() {
        return version;
    }

    /** O índice do pedaço solto em ({@code px}, {@code py}) relativo ao canto dele. */
    public int pieceAt(int px, int py) {
        return piece[py * w + px];
    }

    /** O ponto (da arte) está dentro do retângulo ou do pedaço. */
    public boolean contains(int px, int py) {
        return active() && px >= x && py >= y && px < x + w && py < y + h;
    }

    /** Marca um retângulo, cortado pela arte; vazio tira a seleção. Um pedaço solto não pode estar no ar. */
    public void select(TvPixelCanvas canvas, int left, int top, int width, int height) {
        int x0 = Math.max(0, left);
        int y0 = Math.max(0, top);
        int x1 = Math.min(canvas.width(), left + width);
        int y1 = Math.min(canvas.height(), top + height);
        floating = false;
        if (x1 <= x0 || y1 <= y0) {
            w = 0;
            h = 0;
        } else {
            x = x0;
            y = y0;
            w = x1 - x0;
            h = y1 - y0;
        }
        version++;
    }

    /** Tira a seleção e larga o pedaço solto, sem devolvê-lo à arte. */
    public void clear() {
        if (active()) {
            w = 0;
            h = 0;
            floating = false;
            version++;
        }
    }

    /** Levanta o retângulo marcado: o pedaço sai da arte e fica solto. Já solto ou sem seleção, nada. */
    public void lift(TvPixelCanvas canvas) {
        if (floating || !active()) {
            return;
        }
        piece = new byte[w * h];
        for (int py = 0; py < h; py++) {
            for (int px = 0; px < w; px++) {
                piece[py * w + px] = (byte) canvas.get(x + px, y + py);
                canvas.set(x + px, y + py, 0);
            }
        }
        floating = true;
        version++;
    }

    /** Assenta o pedaço solto: os pixels não transparentes dele entram na arte e a parte de dentro fica marcada. */
    public void commit(TvPixelCanvas canvas) {
        if (!floating) {
            return;
        }
        for (int py = 0; py < h; py++) {
            for (int px = 0; px < w; px++) {
                int index = piece[py * w + px];
                if (index != 0 && canvas.inside(x + px, y + py)) {
                    canvas.set(x + px, y + py, index);
                }
            }
        }
        select(canvas, x, y, w, h);
    }

    /** Move o pedaço solto (só ele anda; o retângulo marcado é levantado antes por quem chama). */
    public void moveTo(int left, int top) {
        if (floating && (left != x || top != y)) {
            x = left;
            y = top;
            version++;
        }
    }

    /** Uma cópia do que está selecionado (o pedaço solto ou os pixels do retângulo), ou {@code null}. */
    public Clip grab(TvPixelCanvas canvas) {
        if (!active()) {
            return null;
        }
        if (floating) {
            return new Clip(w, h, piece.clone());
        }
        byte[] out = new byte[w * h];
        for (int py = 0; py < h; py++) {
            for (int px = 0; px < w; px++) {
                out[py * w + px] = (byte) canvas.get(x + px, y + py);
            }
        }
        return new Clip(w, h, out);
    }

    /** Apaga os pixels do retângulo marcado na arte (o pedaço solto sai com o {@link #clear}). */
    public void erase(TvPixelCanvas canvas) {
        if (!active() || floating) {
            return;
        }
        for (int py = y; py < y + h; py++) {
            for (int px = x; px < x + w; px++) {
                canvas.set(px, py, 0);
            }
        }
    }

    /** Cola uma cópia como pedaço solto com o canto em ({@code left}, {@code top}). */
    public void paste(Clip clip, int left, int top) {
        x = left;
        y = top;
        w = clip.width();
        h = clip.height();
        piece = clip.pixels().clone();
        floating = true;
        version++;
    }

    /** Vira ou gira o pedaço solto em torno do canto dele (girar troca a largura e a altura). */
    public void transform(TvPixelTransform kind) {
        if (!floating) {
            return;
        }
        piece = kind.apply(piece, w, h);
        if (kind == TvPixelTransform.ROTATE) {
            int old = w;
            w = h;
            h = old;
        }
        version++;
    }
}
