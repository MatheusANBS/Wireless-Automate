package io.github.matheusanbs.wirelessautomate.client.tv.pixel;

import io.github.matheusanbs.wirelessautomate.tv.TvPixels;
import java.util.Arrays;

/**
 * A arte em edição no editor de pixel art: largura e altura de {@link TvPixels#MIN_SIDE} a {@link TvPixels#MAX_SIDE},
 * a paleta de 16 ARGB (a cor 0 sempre transparente) e um índice da paleta por pixel, linha a linha. Mudável, ao
 * contrário do {@code TvPixelArt}; a {@link #version} sobe a cada pixel ou cor que muda de fato, e o quadro refaz a
 * textura só quando ela sobe. Lógica pura, com JUnit.
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

    /** Um estado guardado da arte, para o desfazer; os arrays são cópias e ninguém os muda. */
    public record Snapshot(int width, int height, int[] palette, byte[] pixels) {
    }

    private final int[] palette = new int[TvPixels.COLORS];
    private int width;
    private int height;
    private byte[] pixels;
    private int version;

    /** Copia a paleta e os pixels; lança {@code IllegalArgumentException} se não valem ({@link TvPixels#problem}). */
    public TvPixelCanvas(int width, int height, int[] palette, byte[] pixels) {
        String problem = TvPixels.problem(width, height, palette, pixels);
        if (problem != null) {
            throw new IllegalArgumentException(problem);
        }
        this.width = width;
        this.height = height;
        System.arraycopy(palette, 0, this.palette, 0, TvPixels.COLORS);
        this.palette[TvPixels.TRANSPARENT] = 0;
        this.pixels = pixels.clone();
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

    /** Sobe a cada mudança de pixel, de cor, de tamanho ou ao voltar um estado guardado. */
    public int version() {
        return version;
    }

    /** A paleta da arte, sem copiar: quem lê não muda. */
    public int[] palette() {
        return palette;
    }

    /** Os pixels da arte, sem copiar: quem lê não muda (o array troca ao mudar o tamanho). */
    public byte[] pixels() {
        return pixels;
    }

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

    /** Troca uma cor da paleta (todos os pixels dela mudam junto); a cor 0 fica transparente. */
    public void setColor(int index, int argb) {
        if (index != TvPixels.TRANSPARENT && palette[index] != argb) {
            palette[index] = argb;
            version++;
        }
    }

    /** Muda o tamanho mantendo o canto de cima à esquerda: corta ou completa com o transparente. */
    public void resize(int newWidth, int newHeight) {
        if (!TvPixels.validSize(newWidth, newHeight) || newWidth == width && newHeight == height) {
            return;
        }
        byte[] out = new byte[newWidth * newHeight];
        int rows = Math.min(height, newHeight);
        int cols = Math.min(width, newWidth);
        for (int y = 0; y < rows; y++) {
            System.arraycopy(pixels, y * width, out, y * newWidth, cols);
        }
        replace(newWidth, newHeight, out);
    }

    /** Troca os pixels inteiros (o array passa a ser da arte), com outro tamanho ou não. */
    void replace(int newWidth, int newHeight, byte[] newPixels) {
        width = newWidth;
        height = newHeight;
        pixels = newPixels;
        version++;
    }

    /** Volta os pixels a uma cópia do mesmo tamanho (o rascunho de uma forma enquanto o mouse anda). */
    void copyFrom(byte[] base) {
        System.arraycopy(base, 0, pixels, 0, pixels.length);
        version++;
    }

    /** Apaga a arte inteira. */
    public void clear() {
        Arrays.fill(pixels, (byte) 0);
        version++;
    }

    public Snapshot snapshot() {
        return new Snapshot(width, height, palette.clone(), pixels.clone());
    }

    public void restore(Snapshot snapshot) {
        width = snapshot.width();
        height = snapshot.height();
        System.arraycopy(snapshot.palette(), 0, palette, 0, TvPixels.COLORS);
        pixels = snapshot.pixels().clone();
        version++;
    }

    /** Se a arte está igual ao estado guardado. */
    public boolean same(Snapshot snapshot) {
        return snapshot.width() == width && snapshot.height() == height && Arrays.equals(snapshot.palette(), palette)
                && Arrays.equals(snapshot.pixels(), pixels);
    }
}
