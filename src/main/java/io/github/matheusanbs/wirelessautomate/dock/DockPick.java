package io.github.matheusanbs.wirelessautomate.dock;

import java.util.function.IntPredicate;

/**
 * Escolha da máquina que recebe um pedido: percorre as máquinas do lado em círculo a partir do
 * cursor, uma volta no máximo, e para na primeira que é usável e aceita. Só as usáveis gastam
 * tentativas, para um pedido nunca custar mais que {@code maxTries} consultas ao outro mod.
 *
 * <p>Também a face mais seletiva de cada ingrediente ({@link #loads} e {@link #selectiveFace}): numa máquina com
 * duas entradas, cada ingrediente vai para a face Insere que aceita menos ingredientes do pedido, para o que só cabe
 * numa face (o minério na de cima da fornalha) não perder o lugar para o que cabe em várias (o carvão).
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

    /**
     * Quantos ingredientes cada face aceita. {@code masks[i]} tem um bit por face (bit {@code f} = a face de índice
     * {@code f} na ordem do perfil) com as faces que aceitam o ingrediente {@code i}; só os {@code count} primeiros
     * valem. Escreve em {@code loads[f]}, para as {@code loads.length} faces.
     */
    public static void loads(int[] masks, int count, int[] loads) {
        loads(masks, 0, count, loads);
    }

    /**
     * O {@link #loads} dos ingredientes {@code from} a {@code from + count - 1}: num pedido com mais de um tipo, as
     * máscaras de cada tipo ficam em sequência no mesmo rascunho e a carga de uma face conta só os do tipo dela.
     */
    public static void loads(int[] masks, int from, int count, int[] loads) {
        for (int f = 0; f < loads.length; f++) {
            int bit = 1 << f;
            int load = 0;
            for (int i = from; i < from + count; i++) {
                if ((masks[i] & bit) != 0) {
                    load++;
                }
            }
            loads[f] = load;
        }
    }

    /**
     * A face mais seletiva entre as de {@code mask}: a de menor {@code loads[f]} (a que aceita menos ingredientes do
     * pedido); no empate, a de menor índice, a primeira na ordem do perfil. Devolve −1 com {@code mask} vazia.
     */
    public static int selectiveFace(int mask, int[] loads) {
        int best = -1;
        for (int f = 0; f < loads.length; f++) {
            if ((mask & (1 << f)) != 0 && (best < 0 || loads[f] < loads[best])) {
                best = f;
            }
        }
        return best;
    }
}
