package io.github.matheusanbs.wirelessautomate.client.tablet;

import java.util.List;

/**
 * As linhas da Lista do Tablet na visão por máquinas (lógica pura): o cabeçalho de cada bloco, as máquinas dele e o
 * "mais N", na ordem do servidor, com o número de cada máquina e o que as setas fazem. ↑ ↓ andam por todas as linhas,
 * cabeçalhos inclusive (um bloco recolhido é só o cabeçalho); ← fecha o bloco aberto ou sobe da máquina para o
 * cabeçalho; → abre o bloco recolhido, desce do cabeçalho aberto para a primeira máquina e, no "mais N", mostra todas.
 */
public final class MachineLines {
    /** O tipo de uma linha. */
    public enum Kind { BLOCK, MACHINE, MORE }

    /** O que uma seta faz. */
    public enum Step {
        NONE,
        OPEN_BLOCK,
        CLOSE_BLOCK,
        /** Mostra todas as máquinas do bloco do "mais N" da linha. */
        SHOW_ALL,
        /** Passa as setas para a linha. */
        MOVE_TO
    }

    /** O que a seta faz e em que linha (a do cabeçalho, do "mais N" ou a de destino). */
    public record Arrow(Step step, int line) {
        static final Arrow NONE = new Arrow(Step.NONE, -1);
    }

    private final Kind[] kinds;
    private final boolean[] collapsed;
    private final int[] numbers;

    /**
     * @param kinds     o tipo de cada linha
     * @param collapsed num cabeçalho, se o bloco está recolhido
     * @param routers   numa máquina, o roteador dela (qualquer número que o identifique na página)
     * @param attached  numa máquina, se é a presa do roteador
     */
    public MachineLines(List<Kind> kinds, boolean[] collapsed, int[] routers, boolean[] attached) {
        this.kinds = kinds.toArray(new Kind[0]);
        this.collapsed = collapsed;
        this.numbers = new int[this.kinds.length];
        int router = -1;
        int number = 0;
        for (int i = 0; i < this.kinds.length; i++) {
            if (this.kinds[i] != Kind.MACHINE) {
                router = -1;
                continue;
            }
            if (routers[i] != router) {
                router = routers[i];
                number = 0;
            }
            if (!attached[i]) {
                numbers[i] = ++number;
            }
        }
    }

    public int size() {
        return kinds.length;
    }

    public Kind kind(int line) {
        return kinds[line];
    }

    /**
     * O "#N" da máquina vinculada: a posição dela entre as do mesmo roteador no bloco, a partir de 1, como a
     * numeração do grupo na tela do roteador; 0 na presa e nas outras linhas.
     */
    public int number(int line) {
        return line >= 0 && line < numbers.length ? numbers[line] : 0;
    }

    /** O cabeçalho do bloco da linha, ou -1. */
    public int header(int line) {
        for (int at = Math.min(line, kinds.length - 1); at >= 0; at--) {
            if (kinds[at] == Kind.BLOCK) {
                return at;
            }
        }
        return -1;
    }

    /**
     * A linha seguinte ({@code direction > 0}) ou anterior; sem linha ({@code -1}), a primeira ou a última. Na borda,
     * fica na mesma ({@code -1} se não havia nenhuma).
     */
    public int next(int line, int direction) {
        if (kinds.length == 0) {
            return -1;
        }
        if (line < 0 || line >= kinds.length) {
            return direction > 0 ? 0 : kinds.length - 1;
        }
        int at = line + (direction > 0 ? 1 : -1);
        return at < 0 || at >= kinds.length ? line : at;
    }

    /** ← na linha: fecha o bloco aberto; da máquina e do "mais N", sobe para o cabeçalho. */
    public Arrow left(int line) {
        if (line < 0 || line >= kinds.length) {
            return Arrow.NONE;
        }
        return switch (kinds[line]) {
            case BLOCK -> collapsed[line] ? Arrow.NONE : new Arrow(Step.CLOSE_BLOCK, line);
            case MACHINE, MORE -> {
                int header = header(line);
                yield header < 0 ? Arrow.NONE : new Arrow(Step.MOVE_TO, header);
            }
        };
    }

    /** → na linha: abre o bloco recolhido, desce do aberto para a primeira máquina; no "mais N", mostra todas. */
    public Arrow right(int line) {
        if (line < 0 || line >= kinds.length) {
            return Arrow.NONE;
        }
        return switch (kinds[line]) {
            case BLOCK -> collapsed[line] ? new Arrow(Step.OPEN_BLOCK, line)
                    : line + 1 < kinds.length && kinds[line + 1] != Kind.BLOCK ? new Arrow(Step.MOVE_TO, line + 1)
                    : Arrow.NONE;
            case MORE -> new Arrow(Step.SHOW_ALL, line);
            case MACHINE -> Arrow.NONE;
        };
    }
}
