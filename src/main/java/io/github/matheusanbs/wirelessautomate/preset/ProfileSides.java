package io.github.matheusanbs.wirelessautomate.preset;

/**
 * Os lados de um perfil copiado de um roteador ({@code RelativeSide}, no quadro do roteador de origem) convertidos
 * para os lados de uma máquina remota ({@code RemoteSide}, relativos à frente dela), para colar o perfil numa
 * máquina ou num grupo (etapa 3b). Lógica pura, nos índices dos enums, como o {@code RemoteSide}.
 *
 * <p>O que a conversão precisa da origem é o quadro: em que lado relativo do roteador de origem ficavam a frente da
 * máquina presa e o "para cima" do mundo. Os dois vão juntos num número ({@link #frame}), guardado no Copiar
 * ({@link ProfileSource#frame()}). Sem frente, a presa vale Norte, como no {@code RemoteSide}.
 *
 * <p>Os lados relativos são vetores no quadro do caso {@code facing=up} sem giro (FRONT para cima, TOP para o sul,
 * LEFT para leste): o roteador de verdade é esse quadro girado, e girar não muda qual lado é a frente, o cima ou a
 * esquerda. A Esquerda de uma máquina é a frente × cima (com a frente em Norte, Leste).
 */
public final class ProfileSides {
    /** Sem quadro: um perfil copiado antes da etapa 3b, que só cola na presa. */
    public static final int NONE = -1;

    /** Índices de {@code RelativeSide}: FRONT, BACK, TOP, BOTTOM, LEFT, RIGHT. */
    private static final int RELATIVE_SIDES = 6;
    /** O vetor de cada lado relativo no caso {@code facing=up} sem giro: cima, baixo, sul, norte, leste, oeste. */
    private static final int[][] VECTORS = {{0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}, {1, 0, 0}, {-1, 0, 0}};

    /** Índices de {@code RemoteSide}: FRONT, BACK, LEFT, RIGHT, TOP, BOTTOM. */
    private static final int FRONT = 0;
    private static final int BACK = 1;
    private static final int LEFT = 2;
    private static final int RIGHT = 3;
    private static final int TOP = 4;
    private static final int BOTTOM = 5;

    private ProfileSides() {
    }

    /**
     * O quadro com a frente da presa no lado relativo {@code front} e o cima do mundo no lado {@code up} (os dois
     * {@code RelativeSide.ordinal()}); {@link #NONE} se os dois não são perpendiculares.
     */
    public static int frame(int front, int up) {
        if (!side(front) || !side(up) || dot(VECTORS[front], VECTORS[up]) != 0) {
            return NONE;
        }
        return front * RELATIVE_SIDES + up;
    }

    /** O quadro é de um perfil que cola em máquinas (de {@link #frame}, não {@link #NONE}). */
    public static boolean valid(int frame) {
        return frame >= 0 && frame < RELATIVE_SIDES * RELATIVE_SIDES
                && frame(frame / RELATIVE_SIDES, frame % RELATIVE_SIDES) == frame;
    }

    /**
     * O lado da máquina ({@code RemoteSide.ordinal()}) que recebe o lado relativo {@code relative}
     * ({@code RelativeSide.ordinal()}) do perfil, pelo quadro {@code frame} ({@link #valid}).
     */
    public static int toRemote(int relative, int frame) {
        if (!valid(frame) || !side(relative)) {
            throw new IllegalArgumentException("quadro " + frame + ", lado " + relative);
        }
        int[] front = VECTORS[frame / RELATIVE_SIDES];
        int[] up = VECTORS[frame % RELATIVE_SIDES];
        int[] v = VECTORS[relative];
        if (same(v, up, 1)) {
            return TOP;
        }
        if (same(v, up, -1)) {
            return BOTTOM;
        }
        if (same(v, front, 1)) {
            return FRONT;
        }
        if (same(v, front, -1)) {
            return BACK;
        }
        return same(v, cross(front, up), 1) ? LEFT : RIGHT;
    }

    private static boolean side(int index) {
        return index >= 0 && index < RELATIVE_SIDES;
    }

    private static int dot(int[] a, int[] b) {
        return a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
    }

    private static int[] cross(int[] a, int[] b) {
        return new int[] {a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]};
    }

    private static boolean same(int[] a, int[] b, int sign) {
        return a[0] == sign * b[0] && a[1] == sign * b[1] && a[2] == sign * b[2];
    }
}
