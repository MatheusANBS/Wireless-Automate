package io.github.matheusanbs.wirelessautomate.resonator;

import java.util.ArrayList;
import java.util.List;

/**
 * A estrutura 5×5×5 do Ressonador de Sinal: acha a caixa em volta do Núcleo, confere cada posição e
 * devolve os membros já com a posição da carcaça, a face e os bits do vidro (texturas conectadas), ou o
 * problema e onde ele está. Lógica pura: o mundo chega pela {@code Probe}.
 */
public final class ResonatorLayout {
    public static final int SIZE = 5;
    private static final int LAST = SIZE - 1;

    public enum Part {
        CASING, GLASS, CORE, ENERGY_PORT, CRYSTAL_PORT, TUNER, AIR, OTHER
    }

    /** As faces na ordem do {@code Direction} do Minecraft, com a direção para fora. */
    public enum Face {
        DOWN(0, -1, 0), UP(0, 1, 0), NORTH(0, 0, -1), SOUTH(0, 0, 1), WEST(-1, 0, 0), EAST(1, 0, 0);

        private final int dx;
        private final int dy;
        private final int dz;

        Face(int dx, int dy, int dz) {
            this.dx = dx;
            this.dy = dy;
            this.dz = dz;
        }

        public int dx() {
            return dx;
        }

        public int dy() {
            return dy;
        }

        public int dz() {
            return dz;
        }

        /** "Direita" da textura desta face no mundo. */
        int[] right() {
            return switch (this) {
                case NORTH -> new int[] {-1, 0, 0};
                case SOUTH, UP, DOWN -> new int[] {1, 0, 0};
                case WEST -> new int[] {0, 0, 1};
                case EAST -> new int[] {0, 0, -1};
            };
        }

        /** "Cima" da textura desta face no mundo. */
        int[] up() {
            return switch (this) {
                case UP -> new int[] {0, 0, -1};
                case DOWN -> new int[] {0, 0, 1};
                default -> new int[] {0, 1, 0};
            };
        }
    }

    @FunctionalInterface
    public interface Probe {
        Part at(int x, int y, int z);
    }

    public record Member(int x, int y, int z, Part part, String casing, Face face, int glassBits) {
    }

    public enum Problem {
        WRONG_BLOCK, INTERIOR_NOT_EMPTY, EXTRA_CORE, MISSING_ENERGY_PORT, MISSING_CRYSTAL_PORT, TOO_MANY_TUNERS
    }

    public sealed interface Result permits Formed, Broken {
    }

    public record Formed(int minX, int minY, int minZ, List<Member> members) implements Result {
    }

    public record Broken(Problem problem, int x, int y, int z) implements Result {
    }

    /** O resultado de uma caixa candidata: erros, o primeiro problema e, sem erro, os membros. */
    private record Box(int errors, Broken first, Formed formed) {
    }

    private ResonatorLayout() {
    }

    /** Tenta cada caixa em que o Núcleo fica numa posição de face; a primeira sem erro monta, senão vale a de menos erros. */
    public static Result check(int cx, int cy, int cz, Probe probe) {
        Broken best = null;
        int bestErrors = Integer.MAX_VALUE;
        for (Face face : Face.values()) {
            for (int a = 1; a < LAST; a++) {
                for (int b = 1; b < LAST; b++) {
                    int[] local = localCore(face, a, b);
                    Box box = evaluate(cx, cy, cz, cx - local[0], cy - local[1], cz - local[2], probe, bestErrors);
                    if (box == null) {
                        continue;
                    }
                    if (box.errors() == 0) {
                        return box.formed();
                    }
                    if (box.errors() < bestErrors) {
                        bestErrors = box.errors();
                        best = box.first();
                    }
                }
            }
        }
        return best;
    }

    /** Coordenada local do Núcleo: 0 ou 4 no eixo da face, {@code a} e {@code b} nos outros, na ordem x, y, z. */
    private static int[] localCore(Face face, int a, int b) {
        return switch (face) {
            case DOWN -> new int[] {a, 0, b};
            case UP -> new int[] {a, LAST, b};
            case NORTH -> new int[] {a, b, 0};
            case SOUTH -> new int[] {a, b, LAST};
            case WEST -> new int[] {0, a, b};
            case EAST -> new int[] {LAST, a, b};
        };
    }

    /** Avalia a caixa de origem (ox, oy, oz); devolve {@code null} se os erros de posição já alcançam {@code limit}. */
    private static Box evaluate(int cx, int cy, int cz, int ox, int oy, int oz, Probe probe, int limit) {
        Part[][][] parts = new Part[SIZE][SIZE][SIZE];
        int errors = 0;
        Broken first = null;
        for (int x = 0; x < SIZE; x++) {
            for (int y = 0; y < SIZE; y++) {
                for (int z = 0; z < SIZE; z++) {
                    int wx = ox + x;
                    int wy = oy + y;
                    int wz = oz + z;
                    Part part = probe.at(wx, wy, wz);
                    parts[x][y][z] = part;
                    Problem wrong = positionProblem(x, y, z, part, wx == cx && wy == cy && wz == cz);
                    if (wrong != null) {
                        errors++;
                        if (errors >= limit) {
                            return null;
                        }
                        if (first == null) {
                            first = new Broken(wrong, wx, wy, wz);
                        }
                    }
                }
            }
        }
        if (errors > 0) {
            return new Box(errors, first, null);
        }
        return countsAndMembers(cx, cy, cz, ox, oy, oz, parts);
    }

    private static Problem positionProblem(int x, int y, int z, Part part, boolean isCore) {
        int border = border(x) + border(y) + border(z);
        if (border == 0) {
            return part == Part.AIR ? null : Problem.INTERIOR_NOT_EMPTY;
        }
        if (border >= 2) {
            return part == Part.CASING ? null : Problem.WRONG_BLOCK;
        }
        return switch (part) {
            case CASING, GLASS, ENERGY_PORT, CRYSTAL_PORT, TUNER -> null;
            case CORE -> isCore ? null : Problem.EXTRA_CORE;
            case AIR, OTHER -> Problem.WRONG_BLOCK;
        };
    }

    private static int border(int c) {
        return c == 0 || c == LAST ? 1 : 0;
    }

    /** Sem posição errada: confere as Portas e o Sintonizador e monta a lista de membros. */
    private static Box countsAndMembers(int cx, int cy, int cz, int ox, int oy, int oz, Part[][][] parts) {
        int energy = 0;
        int crystal = 0;
        int tuners = 0;
        Broken second = null;
        List<Member> members = new ArrayList<>(98);
        for (int x = 0; x < SIZE; x++) {
            for (int y = 0; y < SIZE; y++) {
                for (int z = 0; z < SIZE; z++) {
                    Part part = parts[x][y][z];
                    if (part == Part.AIR) {
                        continue;
                    }
                    switch (part) {
                        case ENERGY_PORT -> energy++;
                        case CRYSTAL_PORT -> crystal++;
                        case TUNER -> {
                            tuners++;
                            if (tuners == 2) {
                                second = new Broken(Problem.TOO_MANY_TUNERS, ox + x, oy + y, oz + z);
                            }
                        }
                        default -> {
                        }
                    }
                    members.add(member(ox, oy, oz, x, y, z, part, parts));
                }
            }
        }
        int errors = (energy == 0 ? 1 : 0) + (crystal == 0 ? 1 : 0) + (tuners > 1 ? 1 : 0);
        if (errors == 0) {
            return new Box(0, null, new Formed(ox, oy, oz, members));
        }
        Broken first = energy == 0 ? new Broken(Problem.MISSING_ENERGY_PORT, cx, cy, cz)
                : crystal == 0 ? new Broken(Problem.MISSING_CRYSTAL_PORT, cx, cy, cz) : second;
        return new Box(errors, first, null);
    }

    private static Member member(int ox, int oy, int oz, int x, int y, int z, Part part, Part[][][] parts) {
        int border = border(x) + border(y) + border(z);
        Face face = border == 1 ? faceOf(x, y, z) : null;
        String casing = part == Part.CASING ? "" + axis(x) + axis(y) + axis(z) : null;
        int bits = part == Part.GLASS ? glassBits(face, x, y, z, parts) : 0;
        return new Member(ox + x, oy + y, oz + z, part, casing, face, bits);
    }

    private static char axis(int c) {
        return c == 0 ? 'n' : c == LAST ? 'p' : 'c';
    }

    private static Face faceOf(int x, int y, int z) {
        if (x == 0) {
            return Face.WEST;
        }
        if (x == LAST) {
            return Face.EAST;
        }
        if (y == 0) {
            return Face.DOWN;
        }
        if (y == LAST) {
            return Face.UP;
        }
        return z == 0 ? Face.NORTH : Face.SOUTH;
    }

    /** Bit 1 com vidro acima, 2 à direita, 4 abaixo e 8 à esquerda, nos vetores da textura da face. */
    private static int glassBits(Face face, int x, int y, int z, Part[][][] parts) {
        int[] right = face.right();
        int[] up = face.up();
        int bits = 0;
        if (glassAt(parts, x + up[0], y + up[1], z + up[2])) {
            bits |= 1;
        }
        if (glassAt(parts, x + right[0], y + right[1], z + right[2])) {
            bits |= 2;
        }
        if (glassAt(parts, x - up[0], y - up[1], z - up[2])) {
            bits |= 4;
        }
        if (glassAt(parts, x - right[0], y - right[1], z - right[2])) {
            bits |= 8;
        }
        return bits;
    }

    private static boolean glassAt(Part[][][] parts, int x, int y, int z) {
        return x >= 0 && x < SIZE && y >= 0 && y < SIZE && z >= 0 && z < SIZE && parts[x][y][z] == Part.GLASS;
    }
}
