package io.github.matheusanbs.wirelessautomate.tv;

import org.jetbrains.annotations.Nullable;

/**
 * A área de uma Tela de Sinal na parede, em células de um bloco, vista de frente: colunas da esquerda para a
 * direita e linhas de baixo para cima. O bloco colocado é a célula ({@code anchorCol}, {@code anchorRow}). As
 * coordenadas do layout são em pixels de desenho, {@link #PIXELS_PER_BLOCK} por bloco, com a origem no canto de
 * cima à esquerda. Lógica pura, sem classes do Minecraft, com JUnit.
 */
public record TvArea(int width, int height, int anchorCol, int anchorRow) {
    public static final TvArea SINGLE = new TvArea(1, 1, 0, 0);
    public static final int PIXELS_PER_BLOCK = 32;

    /** Para onde a tela cresce a partir do bloco colocado. */
    public enum Grow { UP_RIGHT, UP_LEFT, CENTER }

    public TvArea {
        if (width < 1 || height < 1 || anchorCol < 0 || anchorCol >= width || anchorRow < 0 || anchorRow >= height) {
            throw new IllegalArgumentException("área inválida: " + width + "x" + height + " em " + anchorCol + "," + anchorRow);
        }
    }

    public static TvArea grown(int width, int height, Grow grow) {
        return switch (grow) {
            case UP_RIGHT -> new TvArea(width, height, 0, 0);
            case UP_LEFT -> new TvArea(width, height, width - 1, 0);
            case CENTER -> new TvArea(width, height, (width - 1) / 2, (height - 1) / 2);
        };
    }

    /** O preset que dá esta área, ou {@code null} se o bloco está noutra célula. */
    public @Nullable Grow grow() {
        for (Grow grow : Grow.values()) {
            if (grown(width, height, grow).equals(this)) {
                return grow;
            }
        }
        return null;
    }

    public static boolean fits(int width, int height, int maxWidth, int maxHeight) {
        return width >= 1 && height >= 1 && width <= maxWidth && height <= maxHeight;
    }

    public int cells() {
        return width * height;
    }

    /** Quantos blocos para a direita (negativo: esquerda) a célula {@code cell} fica do bloco colocado. */
    public int rightOf(int cell) {
        return cell % width - anchorCol;
    }

    /** Quantos blocos para cima (negativo: baixo) a célula {@code cell} fica do bloco colocado. */
    public int upOf(int cell) {
        return cell / width - anchorRow;
    }

    public int pixelWidth() {
        return width * PIXELS_PER_BLOCK;
    }

    public int pixelHeight() {
        return height * PIXELS_PER_BLOCK;
    }
}
