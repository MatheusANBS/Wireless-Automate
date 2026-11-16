package io.github.matheusanbs.wirelessautomate.resonator;

import java.util.ArrayList;
import java.util.List;
import org.jetbrains.annotations.Nullable;

/**
 * A Armilar do Ressonador de Sinal: 35 blocos numa caixa 5×5×5 em volta do Núcleo. Confere as 125 casas, cada
 * uma contra a tabela da spec, e devolve os membros já com o slot, a face e o canto, ou o problema e onde ele
 * está. As coordenadas locais têm a frente para o sul (x de 0 a 4 para o leste, y de baixo para cima, z de 0 atrás
 * a 4 na frente) e o Núcleo em (2, 0, 4); o {@link #world} leva a casa local ao mundo para cada frente. Lógica pura:
 * o mundo chega pela {@code Probe}.
 */
public final class ResonatorLayout {
    public static final int SIZE = 5;
    private static final int LAST = SIZE - 1;
    private static final int MID = SIZE / 2;
    private static final int CELLS = SIZE * SIZE * SIZE;
    /** As quatro frentes horizontais, na ordem da busca; a frente é sempre horizontal. */
    public static final Face[] FRONTS = {Face.SOUTH, Face.WEST, Face.NORTH, Face.EAST};

    public enum Part {
        CASING, CORE, ENERGY_PORT, CRYSTAL_PORT, TUNER, COLUMN, CRADLE, AIR, OTHER
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
    }

    @FunctionalInterface
    public interface Probe {
        Part at(int x, int y, int z);
    }

    /**
     * Um bloco da estrutura. {@code slot}: a posição da Laje ({@code "p20"}) ou a parte da Coluna e do Berço
     * ({@code "lower"}, {@code "upper"}); {@code face}: a frente da estrutura (Laje e Núcleo) ou a direção da peça;
     * {@code corner}: o canto da Coluna ({@code "se"}, {@code "sw"}, {@code "nw"}, {@code "ne"}).
     */
    public record Member(int x, int y, int z, Part part, @Nullable String slot, @Nullable Face face,
            @Nullable String corner) {
    }

    /**
     * O que impede montar. {@code SHARED_WALL} não sai daqui (a regra é do mundo, pelo índice dos membros): a
     * posição é a do Núcleo dono da parede.
     */
    public enum Problem {
        WRONG_BLOCK, INTERIOR_NOT_EMPTY, EXTRA_CORE, MISSING_ENERGY_PORT, MISSING_CRYSTAL_PORT, SHARED_WALL
    }

    public sealed interface Result permits Formed, Broken {
    }

    /** A estrutura montada: o canto mínimo da caixa, a frente em que ela fechou e os 35 membros. */
    public record Formed(int minX, int minY, int minZ, Face front, List<Member> members) implements Result {
    }

    public record Broken(Problem problem, int x, int y, int z) implements Result {
    }

    /** O resultado de uma frente candidata: erros, o primeiro problema e, sem erro, a estrutura. */
    private record Box(int errors, Broken first, Formed formed) {
    }

    private ResonatorLayout() {
    }

    /** A peça esperada na casa local (frente para o sul): a tabela da spec. */
    public static Part expected(int lx, int ly, int lz) {
        if (ly == 0) {
            if (lx == MID && lz == LAST) {
                return Part.CORE;
            }
            if (lx == LAST && lz == MID) {
                return Part.ENERGY_PORT;
            }
            if (lx == 0 && lz == MID) {
                return Part.CRYSTAL_PORT;
            }
            if (lx == MID && lz == 0) {
                return Part.TUNER;
            }
            return Part.CASING;
        }
        if (ly <= 2) {
            if (lx == MID && lz == MID) {
                return Part.CRADLE;
            }
            if ((lx == 1 || lx == 3) && (lz == 1 || lz == 3)) {
                return Part.COLUMN;
            }
        }
        return Part.AIR;
    }

    /** O deslocamento (dx, dz) do referencial local (frente sul) levado ao mundo para a frente {@code front}. */
    static int[] rotate(Face front, int dx, int dz) {
        return new int[] {rotateX(front, dx, dz), rotateZ(front, dx, dz)};
    }

    private static int rotateX(Face front, int dx, int dz) {
        return switch (front) {
            case SOUTH -> dx;
            case WEST -> -dz;
            case NORTH -> -dx;
            case EAST -> dz;
            default -> throw new IllegalArgumentException("frente vertical: " + front);
        };
    }

    private static int rotateZ(Face front, int dx, int dz) {
        return switch (front) {
            case SOUTH -> dz;
            case WEST -> dx;
            case NORTH -> -dz;
            case EAST -> -dx;
            default -> throw new IllegalArgumentException("frente vertical: " + front);
        };
    }

    /** A posição no mundo da casa local, com o Núcleo em (cx, cy, cz) e a frente {@code front}. */
    public static int[] world(Face front, int cx, int cy, int cz, int lx, int ly, int lz) {
        return new int[] {worldX(front, cx, lx, lz), cy + ly, worldZ(front, cz, lx, lz)};
    }

    private static int worldX(Face front, int cx, int lx, int lz) {
        return cx - rotateX(front, 0, LAST - MID) + rotateX(front, lx - MID, lz - MID);
    }

    private static int worldZ(Face front, int cz, int lx, int lz) {
        return cz - rotateZ(front, 0, LAST - MID) + rotateZ(front, lx - MID, lz - MID);
    }

    /** A direção local (frente sul) levada ao mundo. */
    public static Face toWorld(Face front, Face local) {
        int dx = rotateX(front, local.dx(), local.dz());
        int dz = rotateZ(front, local.dx(), local.dz());
        for (Face face : FRONTS) {
            if (face.dx() == dx && face.dz() == dz) {
                return face;
            }
        }
        throw new IllegalArgumentException("direção vertical: " + local);
    }

    /** Tenta primeiro a frente sul; veja {@link #check(int, int, int, Face, Probe)}. */
    public static Result check(int cx, int cy, int cz, Probe probe) {
        return check(cx, cy, cz, Face.SOUTH, probe);
    }

    /**
     * Tenta primeiro a frente {@code front} (a que o Núcleo já tem: numa estrutura montada e intacta a resposta sai
     * nela, com uma leitura de 125 casas) e depois as outras três, na ordem de {@link #FRONTS}. A primeira sem erro
     * monta; senão vale o {@code Broken} da frente de menos erros.
     */
    public static Result check(int cx, int cy, int cz, Face front, Probe probe) {
        Broken best = null;
        int bestErrors = Integer.MAX_VALUE;
        for (int i = -1; i < FRONTS.length; i++) {
            Face face = i < 0 ? front : FRONTS[i];
            if (i >= 0 && face == front) {
                continue;
            }
            Box box = evaluate(cx, cy, cz, face, probe, bestErrors);
            if (box == null) {
                continue;
            }
            if (box.errors() == 0) {
                return box.formed();
            }
            bestErrors = box.errors();
            best = box.first();
        }
        return best;
    }

    /** Avalia a frente {@code front}; devolve {@code null} se os erros já alcançam {@code limit}. */
    private static @Nullable Box evaluate(int cx, int cy, int cz, Face front, Probe probe, int limit) {
        Part[] parts = new Part[CELLS];
        int errors = 0;
        Broken first = null;
        for (int lx = 0; lx < SIZE; lx++) {
            for (int ly = 0; ly < SIZE; ly++) {
                for (int lz = 0; lz < SIZE; lz++) {
                    int wx = worldX(front, cx, lx, lz);
                    int wy = cy + ly;
                    int wz = worldZ(front, cz, lx, lz);
                    Part have = probe.at(wx, wy, wz);
                    parts[(lx * SIZE + ly) * SIZE + lz] = have;
                    Problem wrong = problem(expected(lx, ly, lz), have, wx == cx && wy == cy && wz == cz);
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
        return new Box(0, null, formed(cx, cy, cz, front, parts));
    }

    /** O problema de uma casa, ou {@code null} se o bloco serve. A casa do Sintonizador aceita também a Laje. */
    private static @Nullable Problem problem(Part want, Part have, boolean thisCore) {
        if (have == Part.CORE && !thisCore) {
            return Problem.EXTRA_CORE;
        }
        return switch (want) {
            case AIR -> have == Part.AIR ? null : Problem.INTERIOR_NOT_EMPTY;
            case TUNER -> have == Part.TUNER || have == Part.CASING ? null : Problem.WRONG_BLOCK;
            case ENERGY_PORT -> have == Part.ENERGY_PORT ? null : Problem.MISSING_ENERGY_PORT;
            case CRYSTAL_PORT -> have == Part.CRYSTAL_PORT ? null : Problem.MISSING_CRYSTAL_PORT;
            default -> have == want ? null : Problem.WRONG_BLOCK;
        };
    }

    /** Sem erro: monta a lista dos 35 membros e acha o canto mínimo da caixa. */
    private static Formed formed(int cx, int cy, int cz, Face front, Part[] parts) {
        List<Member> members = new ArrayList<>(35);
        int minX = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        for (int lx = 0; lx < SIZE; lx++) {
            for (int ly = 0; ly < SIZE; ly++) {
                for (int lz = 0; lz < SIZE; lz++) {
                    int wx = worldX(front, cx, lx, lz);
                    int wz = worldZ(front, cz, lx, lz);
                    minX = Math.min(minX, wx);
                    minZ = Math.min(minZ, wz);
                    Part part = parts[(lx * SIZE + ly) * SIZE + lz];
                    if (part != Part.AIR) {
                        members.add(member(front, wx, cy + ly, wz, lx, ly, lz, part));
                    }
                }
            }
        }
        return new Formed(minX, cy, minZ, front, members);
    }

    private static Member member(Face front, int wx, int wy, int wz, int lx, int ly, int lz, Part part) {
        return switch (part) {
            case CASING -> new Member(wx, wy, wz, part, "p" + lx + lz, front, null);
            case CORE -> new Member(wx, wy, wz, part, null, front, null);
            case ENERGY_PORT -> new Member(wx, wy, wz, part, null, toWorld(front, Face.EAST), null);
            case CRYSTAL_PORT -> new Member(wx, wy, wz, part, null, toWorld(front, Face.WEST), null);
            case TUNER -> new Member(wx, wy, wz, part, null, toWorld(front, Face.NORTH), null);
            case COLUMN -> new Member(wx, wy, wz, part, ly == 1 ? "lower" : "upper", null,
                    corner(rotateX(front, lx - MID, lz - MID), rotateZ(front, lx - MID, lz - MID)));
            case CRADLE -> new Member(wx, wy, wz, part, ly == 1 ? "lower" : "upper", null, null);
            default -> throw new IllegalStateException("peça fora da estrutura: " + part);
        };
    }

    /** O canto da Coluna pelos sinais do deslocamento no mundo: leste e sul são positivos. */
    private static String corner(int dx, int dz) {
        if (dx > 0) {
            return dz > 0 ? "se" : "ne";
        }
        return dz > 0 ? "sw" : "nw";
    }
}
