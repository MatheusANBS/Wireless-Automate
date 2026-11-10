package io.github.matheusanbs.wirelessautomate.dock;

/**
 * Cores das etiquetas dos lados da Doca, numa constante só para a tela e o cubo no mundo. O índice é
 * o {@code Direction.ordinal()} (DOWN 0, UP 1, NORTH 2, SOUTH 3, WEST 4, EAST 5).
 *
 * <p>Lógica pura, sem classes do Minecraft, para ser testada com JUnit.
 */
public final class DockColors {
    private static final int[] SIDE = {
        0xFFC2486B, // baixo
        0xFF8E4FC9, // cima
        0xFFB57A3A, // norte
        0xFF2F6FD6, // sul
        0xFF5E9A2B, // oeste
        0xFFD9931A, // leste
    };

    /** Ordem dos lados na tela, como ordinais de direção: Norte, Sul, Leste, Oeste, Cima, Baixo. */
    public static final int[] ORDER = {2, 3, 5, 4, 1, 0};

    private DockColors() {
    }

    /** Cor ARGB da etiqueta do lado de ordinal {@code directionOrdinal}. */
    public static int side(int directionOrdinal) {
        return SIDE[directionOrdinal];
    }
}
