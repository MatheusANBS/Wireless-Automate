package io.github.matheusanbs.wirelessautomate.resonator;

import io.github.matheusanbs.wirelessautomate.resonator.ResonatorLayout.Part;
import java.util.EnumMap;
import java.util.Map;

/**
 * A comparação da projeção: o desenho esperado contra o que há no mundo, posição a posição. Lógica pura; o mundo
 * chega pela {@code Probe}.
 */
public final class ProjectionDiff {
    private static final int SIZE = ResonatorLayout.SIZE;
    private static final int LAST = SIZE - 1;

    /** {@code MISSING}: ar onde devia haver peça; {@code WRONG}: outra coisa no lugar (inclusive no interior). */
    public enum State {
        OK, MISSING, WRONG
    }

    /** Os estados em índices locais {@code [x][y][z]}, o que falta por peça (só os {@code MISSING}) e os errados. */
    public record Result(State[][][] states, Map<Part, Integer> missing, int wrong) {
    }

    private ProjectionDiff() {
    }

    public static Result compare(Part[][][] expected, int ox, int oy, int oz, ResonatorLayout.Probe actual) {
        State[][][] states = new State[SIZE][SIZE][SIZE];
        Map<Part, Integer> missing = new EnumMap<>(Part.class);
        int wrong = 0;
        for (int x = 0; x < SIZE; x++) {
            for (int y = 0; y < SIZE; y++) {
                for (int z = 0; z < SIZE; z++) {
                    Part want = expected[x][y][z];
                    Part have = actual.at(ox + x, oy + y, oz + z);
                    State state = stateOf(want, have, isFaceFill(x, y, z));
                    states[x][y][z] = state;
                    if (state == State.WRONG) {
                        wrong++;
                    } else if (state == State.MISSING && want != Part.CORE) {
                        missing.merge(want, 1, Integer::sum);
                    }
                }
            }
        }
        return new Result(states, missing, wrong);
    }

    /**
     * No miolo de uma face, Carcaça e Vidro valem um pelo outro: a montagem aceita os dois, e o que já foi
     * colocado continua certo quando o jogador troca entre Vitrine e Faixa.
     */
    private static State stateOf(Part want, Part have, boolean faceFill) {
        if (have == want || faceFill && isFill(want) && isFill(have)) {
            return State.OK;
        }
        return have == Part.AIR ? State.MISSING : State.WRONG;
    }

    private static boolean isFill(Part part) {
        return part == Part.CASING || part == Part.GLASS;
    }

    /** Posição de face (só um eixo na borda); arestas, cantos e o interior ficam de fora. */
    private static boolean isFaceFill(int x, int y, int z) {
        return border(x) + border(y) + border(z) == 1;
    }

    private static int border(int c) {
        return c == 0 || c == LAST ? 1 : 0;
    }
}
