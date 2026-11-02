package io.github.matheusanbs.wirelessautomate.network;

import java.util.Arrays;
import net.minecraft.world.item.ItemStack;

/**
 * O que um destino de itens seletivo (uma máquina que recusa parte dos itens) já mostrou que quer e que
 * não quer. Só existe na porta que recusou algum item tendo lugar para outros ({@link Port#hints}): baú
 * comum, que aceita tudo, nunca cria.
 *
 * <ul>
 * <li><b>Recusados:</b> os itens que o destino recusou desde a última mudança dele. A visita pula o destino
 * para esses itens só comparando, sem simular a inserção, e sem pôr o destino para dormir: recusar o ouro
 * não diz nada sobre o ferro. Qualquer mudança na máquina ({@link #clearRefused}, pelo aviso que acorda a
 * porta) apaga a lista, então o ouro recusado com a entrada cheia de ferro volta a ser oferecido quando o
 * ferro acaba.</li>
 * <li><b>Aprendidos:</b> os últimos itens que o destino recebeu. Uma origem que é um Baú do mod procura
 * esses itens direto pela chave ({@link ItemBulkTransfer}), antes de varrer os tipos dela: com milhares de
 * tipos, a fornalha que já recebeu ferro não espera a varredura chegar no ferro.</li>
 * </ul>
 *
 * <p>As duas listas são anéis de tamanho fixo com cópias de 1 do item; copiar só acontece quando o item entra
 * na lista, nunca por entrega.
 */
final class ItemHints {
    static final int SIZE = 8;

    private final ItemStack[] refused = new ItemStack[SIZE];
    private int refusedSize;
    private int refusedNext;
    private final ItemStack[] learned = new ItemStack[SIZE];
    private int learnedSize;
    private int learnedNext;

    /** O destino recusou {@code stack} desde a última mudança dele. */
    boolean refused(ItemStack stack) {
        for (int i = 0; i < refusedSize; i++) {
            if (ItemStack.isSameItemSameComponents(refused[i], stack)) {
                return true;
            }
        }
        return false;
    }

    /** Anota a recusa; com a lista cheia, sai a mais antiga. */
    void refuse(ItemStack stack) {
        if (refused(stack)) {
            return;
        }
        refused[refusedNext] = stack.copyWithCount(1);
        refusedNext = (refusedNext + 1) % SIZE;
        if (refusedSize < SIZE) {
            refusedSize++;
        }
    }

    /** A máquina mudou: as recusas não valem mais. */
    void clearRefused() {
        if (refusedSize > 0) {
            Arrays.fill(refused, null);
            refusedSize = 0;
            refusedNext = 0;
        }
    }

    /** O destino recebeu {@code stack}: entra nos aprendidos, se ainda não está. */
    void learn(ItemStack stack) {
        for (int i = 0; i < learnedSize; i++) {
            if (ItemStack.isSameItemSameComponents(learned[i], stack)) {
                return;
            }
        }
        learned[learnedNext] = stack.copyWithCount(1);
        learnedNext = (learnedNext + 1) % SIZE;
        if (learnedSize < SIZE) {
            learnedSize++;
        }
    }

    int learnedSize() {
        return learnedSize;
    }

    /** O aprendido na posição {@code i} (pilha de 1; não alterar). */
    ItemStack learned(int i) {
        return learned[i];
    }
}
