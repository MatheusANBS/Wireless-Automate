package io.github.matheusanbs.wirelessautomate.dock;

import java.util.function.IntPredicate;

/**
 * Escolha da máquina que recebe um pedido: percorre as máquinas do lado em círculo a partir do
 * cursor, uma volta no máximo, e para na primeira que é usável e aceita. Só as usáveis gastam
 * tentativas, para um pedido nunca custar mais que {@code maxTries} consultas ao outro mod.
 *
 * <p>Lógica pura, sem classes do Minecraft e sem alocação, para ser testada com JUnit.
 */
public final class DockPick {
    private DockPick() {
    }

    /**
     * @param usable a máquina pode ser tentada (acordada, carregada e no alcance)
     * @param accepts a máquina aceita o pedido; só é chamado nas usáveis
     * @return o índice escolhido, ou -1 se nenhuma serve
     */
    public static int pick(int count, int cursor, IntPredicate usable, IntPredicate accepts, int maxTries) {
        if (count <= 0) {
            return -1;
        }
        int start = Math.floorMod(cursor, count);
        int tries = 0;
        for (int step = 0; step < count && tries < maxTries; step++) {
            int index = start + step;
            if (index >= count) {
                index -= count;
            }
            if (!usable.test(index)) {
                continue;
            }
            tries++;
            if (accepts.test(index)) {
                return index;
            }
        }
        return -1;
    }
}
