package io.github.matheusanbs.wirelessautomate.client.tv.pixel;

import io.github.matheusanbs.wirelessautomate.tv.TvPixels;
import java.util.Arrays;

/**
 * A arte em edição no editor de pixel art: largura e altura de {@link TvPixels#MIN_SIDE} a {@link TvPixels#MAX_SIDE},
 * a paleta de 16 ARGB (a cor 0 sempre transparente), os quadros (de 1 a {@link TvPixels#MAX_FRAMES}, um índice da
 * paleta por pixel, linha a linha) e a animação (velocidade e modo). Mudável, ao contrário do {@code TvPixelArt}; a
 * {@link #version} sobe a cada pixel, cor, quadro ou animação que muda de fato, e o quadro refaz a textura só quando
 * ela sobe. Lógica pura, com JUnit.
 * <p>
 * As ferramentas pintam no quadro escolhido ({@link #current}), que é o array {@link #pixels}, só da arte. Os outros
 * quadros ficam em {@link #frame} e nunca são mudados no lugar: um estado guardado ({@link Snapshot}) divide com a
 * arte os arrays dos quadros que não mudaram e copia só o escolhido, então o desfazer de uma arte de 64 quadros não
 * guarda 64 cópias a cada traço. Escolher outro quadro devolve o array do escolhido à lista e copia o novo.
 */
public final class TvPixelCanvas {
    /** A paleta de uma arte nova, em quatro rampas do escuro ao claro (decisão 1c da spec da etapa 4). */
    private static final int[] DEFAULT_PALETTE = {
        0x00000000, 0xFF1B1920, 0xFF7A7366, 0xFFECE5D6,
        0xFF8E2A35, 0xFFC2442A, 0xFFF0603E, 0xFFFFB08A,
        0xFF1E5E4A, 0xFF2F9D5C, 0xFF5CC97A, 0xFFB9EBA3,
        0xFF2C2F7A, 0xFF4B4FB0, 0xFF7A80E0, 0xFFB8BEF5};
    /** O lado de uma arte nova. */
    public static final int NEW_SIDE = 16;

    /**
     * Um estado guardado da arte, para o desfazer. Ninguém muda os arrays: o do quadro escolhido é uma cópia, e os
     * outros são os mesmos que a arte guardava (que ela também não muda).
     */
    public record Snapshot(int width, int height, int[] palette, byte[][] frames, int current, int fps,
            TvPixels.Mode mode) {
        /** Os pixels do quadro que estava escolhido. */
        public byte[] pixels() {
            return frames[current];
        }
    }

    private final int[] palette = new int[TvPixels.COLORS];
    private int width;
    private int height;
    /** Os quadros; o lugar do escolhido fica desatualizado enquanto ele está em {@link #pixels}. */
    private byte[][] frames;
    private int current;
    private byte[] pixels;
    private int fps = TvPixels.DEFAULT_FPS;
    private TvPixels.Mode mode = TvPixels.Mode.LOOP;
    private int version;

    /** Uma arte de um quadro; copia a paleta e os pixels e lança {@code IllegalArgumentException} se não valem. */
    public TvPixelCanvas(int width, int height, int[] palette, byte[] pixels) {
        this(width, height, palette, pixels, 1, TvPixels.DEFAULT_FPS, TvPixels.Mode.LOOP);
    }

    /**
     * Uma arte de {@code frameCount} quadros, um depois do outro em {@code pixels}; copia tudo e lança
     * {@code IllegalArgumentException} se não vale ({@link TvPixels#problem}).
     */
    public TvPixelCanvas(int width, int height, int[] palette, byte[] pixels, int frameCount, int fps,
            TvPixels.Mode mode) {
        String problem = TvPixels.problem(width, height, palette, pixels, frameCount, fps);
        if (problem != null) {
            throw new IllegalArgumentException(problem);
        }
        this.width = width;
        this.height = height;
        System.arraycopy(palette, 0, this.palette, 0, TvPixels.COLORS);
        this.palette[TvPixels.TRANSPARENT] = 0;
        int area = width * height;
        this.frames = new byte[frameCount][];
        for (int f = 0; f < frameCount; f++) {
            frames[f] = Arrays.copyOfRange(pixels, f * area, (f + 1) * area);
        }
        this.pixels = frames[0].clone();
        this.fps = fps;
        this.mode = mode;
    }

    /** Uma arte nova de {@code width} × {@code height}, toda transparente, com a paleta padrão. */
    public static TvPixelCanvas blank(int width, int height) {
        return new TvPixelCanvas(width, height, DEFAULT_PALETTE, new byte[width * height]);
    }

    /** Uma cópia da paleta padrão. */
    public static int[] defaultPalette() {
        return DEFAULT_PALETTE.clone();
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    /** Sobe a cada mudança de pixel, de cor, de tamanho, de quadro, de animação ou ao voltar um estado guardado. */
    public int version() {
        return version;
    }

    /** A paleta da arte, sem copiar: quem lê não muda. */
    public int[] palette() {
        return palette;
    }

    /** Os pixels do quadro escolhido, sem copiar: quem lê não muda (o array troca com o tamanho e com o quadro). */
    public byte[] pixels() {
        return pixels;
    }

    // ------------------------------------------------------------------ os quadros

    public int frames() {
        return frames.length;
    }

    /** O quadro escolhido, o que as ferramentas pintam. */
    public int current() {
        return current;
    }

    /**
     * Os pixels do quadro {@code index}, sem copiar: quem lê não muda. O do quadro escolhido muda no lugar a cada
     * traço; o dos outros só troca de array (a identidade diz se mudou, para a miniatura da faixa).
     */
    public byte[] frame(int index) {
        return index == current ? pixels : frames[index];
    }

    public int fps() {
        return fps;
    }

    public TvPixels.Mode mode() {
        return mode;
    }

    /** Escolhe o quadro que as ferramentas pintam (não é um passo do desfazer, nem muda a {@link #version}). */
    public void select(int index) {
        if (index == current || index < 0 || index >= frames.length) {
            return;
        }
        frames[current] = pixels;
        current = index;
        pixels = frames[index].clone();
    }

    /** Põe {@code content} (do tamanho de um quadro, passa a ser da arte) depois do escolhido e o escolhe. */
    void insertAfterCurrent(byte[] content) {
        frames[current] = pixels;
        byte[][] out = new byte[frames.length + 1][];
        System.arraycopy(frames, 0, out, 0, current + 1);
        out[current + 1] = content;
        System.arraycopy(frames, current + 1, out, current + 2, frames.length - current - 1);
        frames = out;
        current++;
        pixels = content.clone();
        version++;
    }

    /** Tira o quadro escolhido (fica pelo menos um) e escolhe o de antes dele. */
    void removeCurrent() {
        if (frames.length == 1) {
            return;
        }
        byte[][] out = new byte[frames.length - 1][];
        System.arraycopy(frames, 0, out, 0, current);
        System.arraycopy(frames, current + 1, out, current, frames.length - current - 1);
        frames = out;
        current = Math.max(0, current - 1);
        pixels = frames[current].clone();
        version++;
    }

    /** Leva o quadro {@code from} para a posição {@code to}; o escolhido continua o mesmo quadro. */
    void move(int from, int to) {
        if (from == to || from < 0 || to < 0 || from >= frames.length || to >= frames.length) {
            return;
        }
        frames[current] = pixels;
        byte[] moved = frames[from];
        int chosen = current == from ? to : current;
        if (from < to) {
            System.arraycopy(frames, from + 1, frames, from, to - from);
            if (current > from && current <= to) {
                chosen = current - 1;
            }
        } else {
            System.arraycopy(frames, to, frames, to + 1, from - to);
            if (current >= to && current < from) {
                chosen = current + 1;
            }
        }
        frames[to] = moved;
        current = chosen;
        pixels = frames[current].clone();
        version++;
    }

    void setFps(int value) {
        if (value != fps) {
            fps = value;
            version++;
        }
    }

    void setMode(TvPixels.Mode value) {
        if (value != mode) {
            mode = value;
            version++;
        }
    }

    /** Todos os quadros, um depois do outro (para salvar). */
    public byte[] allFrames() {
        int area = width * height;
        byte[] out = new byte[area * frames.length];
        for (int f = 0; f < frames.length; f++) {
            System.arraycopy(frame(f), 0, out, f * area, area);
        }
        return out;
    }

    // ------------------------------------------------------------------ os pixels do quadro escolhido

    public boolean inside(int x, int y) {
        return x >= 0 && y >= 0 && x < width && y < height;
    }

    /** O índice da paleta no pixel ({@code x}, {@code y}), que precisa estar dentro. */
    public int get(int x, int y) {
        return pixels[y * width + x];
    }

    public void set(int x, int y, int index) {
        int k = y * width + x;
        if (pixels[k] != index) {
            pixels[k] = (byte) index;
            version++;
        }
    }

    public int color(int index) {
        return palette[index];
    }

    /** Troca uma cor da paleta (todos os pixels dela mudam junto, em todos os quadros); a cor 0 fica transparente. */
    public void setColor(int index, int argb) {
        if (index != TvPixels.TRANSPARENT && palette[index] != argb) {
            palette[index] = argb;
            version++;
        }
    }

    /** Muda o tamanho de todos os quadros mantendo o canto de cima à esquerda: corta ou completa com o transparente. */
    public void resize(int newWidth, int newHeight) {
        if (!TvPixels.validSize(newWidth, newHeight) || newWidth == width && newHeight == height) {
            return;
        }
        frames[current] = pixels;
        int rows = Math.min(height, newHeight);
        int cols = Math.min(width, newWidth);
        byte[][] out = new byte[frames.length][];
        for (int f = 0; f < frames.length; f++) {
            out[f] = new byte[newWidth * newHeight];
            for (int y = 0; y < rows; y++) {
                System.arraycopy(frames[f], y * width, out[f], y * newWidth, cols);
            }
        }
        width = newWidth;
        height = newHeight;
        frames = out;
        pixels = frames[current].clone();
        version++;
    }

    /** Troca os pixels de todos os quadros pelo resultado de {@code kind} (virar ou girar a arte inteira). */
    void transformAll(TvPixelTransform kind) {
        frames[current] = pixels;
        boolean rotate = kind == TvPixelTransform.ROTATE;
        byte[][] out = new byte[frames.length][];
        for (int f = 0; f < frames.length; f++) {
            out[f] = kind.apply(frames[f], width, height);
        }
        if (rotate) {
            int w = width;
            width = height;
            height = w;
        }
        frames = out;
        pixels = frames[current].clone();
        version++;
    }

    /** Volta os pixels do quadro escolhido a uma cópia do mesmo tamanho (o rascunho de uma forma enquanto o mouse anda). */
    void copyFrom(byte[] base) {
        System.arraycopy(base, 0, pixels, 0, pixels.length);
        version++;
    }

    /** Apaga o quadro escolhido. */
    public void clear() {
        Arrays.fill(pixels, (byte) 0);
        version++;
    }

    public Snapshot snapshot() {
        byte[][] copy = frames.clone();
        copy[current] = pixels.clone();
        return new Snapshot(width, height, palette.clone(), copy, current, fps, mode);
    }

    public void restore(Snapshot snapshot) {
        width = snapshot.width();
        height = snapshot.height();
        System.arraycopy(snapshot.palette(), 0, palette, 0, TvPixels.COLORS);
        frames = snapshot.frames().clone();
        current = snapshot.current();
        pixels = frames[current].clone();
        fps = snapshot.fps();
        mode = snapshot.mode();
        version++;
    }

    /** Se a arte está igual ao estado guardado (o quadro escolhido não conta: escolher não é um passo). */
    public boolean same(Snapshot snapshot) {
        if (snapshot.width() != width || snapshot.height() != height || snapshot.frames().length != frames.length
                || snapshot.fps() != fps || snapshot.mode() != mode || !Arrays.equals(snapshot.palette(), palette)) {
            return false;
        }
        for (int f = 0; f < frames.length; f++) {
            byte[] mine = frame(f);
            byte[] theirs = snapshot.frames()[f];
            if (mine != theirs && !Arrays.equals(mine, theirs)) {
                return false;
            }
        }
        return true;
    }
}
