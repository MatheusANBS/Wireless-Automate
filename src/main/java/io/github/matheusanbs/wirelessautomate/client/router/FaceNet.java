package io.github.matheusanbs.wirelessautomate.client.router;

import io.github.matheusanbs.wirelessautomate.range.RemoteSide;
import java.util.List;
import org.jetbrains.annotations.Nullable;

/**
 * As faces desdobradas como um cubo (lógica pura): a planificação em cruz numa grade de 4 colunas × 3
 * linhas, com Cima em cima da Frente; Esquerda, Frente, Direita e Trás na linha do meio; e Baixo
 * embaixo da Frente. Esquerda e Direita são as de quem olha a frente da máquina ({@link RemoteSide}).
 * Também a ordem dos seis quadradinhos das faces na Lista do Tablet e a escolha do nome que cabe numa
 * célula.
 */
public final class FaceNet {
    /** Os seis lados, sem clonar o array a cada quadro. */
    public static final RemoteSide[] SIDES = RemoteSide.values();
    public static final int COLS = 4;
    public static final int ROWS = 3;
    /** Os quadradinhos da Lista, numa grade 3 × 2: Cima, Frente, Esquerda / Baixo, Trás, Direita. */
    public static final List<RemoteSide> CHIP_ORDER = List.of(RemoteSide.TOP, RemoteSide.FRONT, RemoteSide.LEFT,
            RemoteSide.BOTTOM, RemoteSide.BACK, RemoteSide.RIGHT);

    private static final RemoteSide[][] GRID = {
            {null, RemoteSide.TOP, null, null},
            {RemoteSide.LEFT, RemoteSide.FRONT, RemoteSide.RIGHT, RemoteSide.BACK},
            {null, RemoteSide.BOTTOM, null, null}};

    /** O nome de um lado numa célula: inteiro, abreviado ("Esq.") ou a letra. */
    public enum Label { FULL, SHORT, LETTER }

    private FaceNet() {
    }

    /** A célula do lado, {@code {coluna, linha}}. */
    public static int[] cell(RemoteSide side) {
        return switch (side) {
            case TOP -> new int[] {1, 0};
            case LEFT -> new int[] {0, 1};
            case FRONT -> new int[] {1, 1};
            case RIGHT -> new int[] {2, 1};
            case BACK -> new int[] {3, 1};
            case BOTTOM -> new int[] {1, 2};
        };
    }

    /** O lado da célula, ou {@code null} numa célula vazia ou fora da grade. */
    public static @Nullable RemoteSide at(int col, int row) {
        if (col < 0 || col >= COLS || row < 0 || row >= ROWS) {
            return null;
        }
        return GRID[row][col];
    }

    /**
     * O lado sob o ponto ({@code dx}, {@code dy}), relativo ao canto da planificação, com células de
     * {@code cellW} × {@code cellH} separadas por {@code gap}; {@code null} no vão entre duas células,
     * numa célula vazia ou fora.
     */
    public static @Nullable RemoteSide sideAt(int dx, int dy, int cellW, int cellH, int gap) {
        if (dx < 0 || dy < 0) {
            return null;
        }
        int col = dx / (cellW + gap);
        int row = dy / (cellH + gap);
        if (dx - col * (cellW + gap) >= cellW || dy - row * (cellH + gap) >= cellH) {
            return null;
        }
        return at(col, row);
    }

    /** O nome que cabe em {@code room}: o inteiro, senão o abreviado, senão a letra. */
    public static Label label(int fullWidth, int shortWidth, int room) {
        if (fullWidth <= room) {
            return Label.FULL;
        }
        return shortWidth <= room ? Label.SHORT : Label.LETTER;
    }
}
