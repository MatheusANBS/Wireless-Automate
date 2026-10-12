package io.github.matheusanbs.wirelessautomate.range;

import java.util.Locale;

/**
 * Lado de uma máquina remota, relativo à frente dela: Esquerda e Direita são vistas de quem olha a
 * frente. Com a frente em Norte, Esquerda = Leste e Direita = Oeste; girar a frente gira os lados
 * horizontais no sentido horário (Norte, Leste, Sul, Oeste).
 *
 * <p>Só inteiros, nos índices de {@code Direction.ordinal()} (DOWN 0, UP 1, NORTH 2, SOUTH 3, WEST 4,
 * EAST 5), para ser lógica pura testável com JUnit. A frente {@code -1} é "sem frente" e vale NORTH;
 * uma frente vertical também vale NORTH.
 */
public enum RemoteSide {
    FRONT, BACK, LEFT, RIGHT, TOP, BOTTOM;

    private static final int DOWN = 0;
    private static final int UP = 1;
    private static final int NORTH = 2;
    /** Horizontais no sentido horário, por índice de Direction: Norte, Leste, Sul, Oeste. */
    private static final int[] CLOCKWISE = {2, 5, 3, 4};

    /** Posição de cada lado horizontal na volta horária, com a frente em Norte. */
    private int offset() {
        return switch (this) {
            case FRONT -> 0;
            case LEFT -> 1;
            case BACK -> 2;
            case RIGHT -> 3;
            default -> -1;
        };
    }

    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** Passos horários da frente a partir de Norte (0 se a frente não é horizontal). */
    private static int steps(int front) {
        for (int i = 0; i < CLOCKWISE.length; i++) {
            if (CLOCKWISE[i] == front) {
                return i;
            }
        }
        return 0;
    }

    /** A face absoluta deste lado numa máquina virada para {@code front}. */
    public int toAbsolute(int front) {
        if (this == TOP) {
            return UP;
        }
        if (this == BOTTOM) {
            return DOWN;
        }
        return CLOCKWISE[(offset() + steps(front)) % 4];
    }

    /** O lado de uma face absoluta numa máquina virada para {@code front}. */
    public static RemoteSide fromAbsolute(int front, int absolute) {
        if (absolute == UP) {
            return TOP;
        }
        if (absolute == DOWN) {
            return BOTTOM;
        }
        int index = ((indexInClockwise(absolute) - steps(front)) % 4 + 4) % 4;
        return switch (index) {
            case 0 -> FRONT;
            case 1 -> LEFT;
            case 2 -> BACK;
            default -> RIGHT;
        };
    }

    private static int indexInClockwise(int absolute) {
        for (int i = 0; i < CLOCKWISE.length; i++) {
            if (CLOCKWISE[i] == absolute) {
                return i;
            }
        }
        throw new IllegalArgumentException("Face inválida: " + absolute);
    }

    /** Quadro canônico das telas e payloads: o caso com a frente em Norte. */
    public static RemoteSide fromCanonical(int absolute) {
        return fromAbsolute(NORTH, absolute);
    }

    public int canonical() {
        return toAbsolute(NORTH);
    }
}
