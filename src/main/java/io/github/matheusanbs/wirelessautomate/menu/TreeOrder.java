package io.github.matheusanbs.wirelessautomate.menu;

import java.util.function.IntPredicate;

/**
 * A ordem das linhas de um roteador aberto na árvore do Tablet, sem classes do jogo: a máquina presa, depois cada
 * grupo na ordem do roteador com as máquinas dele na ordem de vínculo (até {@link #FEW}, ou todas com "Mostrar
 * todas") e um "mais N" quando corta. Um grupo recolhido mostra só a linha dele. Para no teto de linhas.
 */
public final class TreeOrder {
    /** Máquinas mostradas num grupo sem "Mostrar todas". */
    public static final int FEW = 8;

    private TreeOrder() {
    }

    /** As máquinas e os grupos de um nó, na ordem do roteador. */
    public interface Machines {
        /** Máquinas guardadas (as do índice, até o teto dele). */
        int size();

        /** O id do grupo da máquina {@code i}. */
        int machineGroup(int i);

        int groupCount();

        /** O id do grupo na posição {@code g}. */
        int groupId(int g);

        /** Quantas máquinas o grupo na posição {@code g} tem, inclusive as de fora das guardadas. */
        int groupTotal(int g);
    }

    /** Quem recebe as linhas, na ordem da tela. */
    public interface Rows {
        void attached();

        /** A linha do grupo na posição {@code g}. */
        void group(int g);

        /** A máquina {@code i}, dentro do grupo da última linha de grupo. */
        void machine(int i);

        /** "… mais {@code hidden}" no fim do grupo na posição {@code g}. */
        void more(int g, int hidden);
    }

    /**
     * Manda a {@code out} as linhas do nó aberto, no máximo {@code budget}; devolve quantas mandou.
     *
     * @param attached  o nó tem máquina presa
     * @param collapsed o grupo de id dado está recolhido
     * @param showAll   o grupo de id dado mostra todas as máquinas
     */
    public static int plan(boolean attached, Machines machines, IntPredicate collapsed, IntPredicate showAll, int budget,
            Rows out) {
        int emitted = 0;
        if (attached) {
            if (emitted >= budget) {
                return emitted;
            }
            out.attached();
            emitted++;
        }
        int size = machines.size();
        for (int g = 0, groups = machines.groupCount(); g < groups; g++) {
            if (emitted >= budget) {
                return emitted;
            }
            int id = machines.groupId(g);
            out.group(g);
            emitted++;
            if (collapsed.test(id)) {
                continue;
            }
            int limit = showAll.test(id) ? Integer.MAX_VALUE : FEW;
            int shown = 0;
            for (int i = 0; i < size && shown < limit; i++) {
                if (machines.machineGroup(i) != id) {
                    continue;
                }
                if (emitted >= budget) {
                    return emitted;
                }
                out.machine(i);
                emitted++;
                shown++;
            }
            int hidden = machines.groupTotal(g) - shown;
            if (hidden > 0) {
                if (emitted >= budget) {
                    return emitted;
                }
                out.more(g, hidden);
                emitted++;
            }
        }
        return emitted;
    }
}
