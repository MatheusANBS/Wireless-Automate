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
 * ferro acaba. Uma máquina que muda sem avisar ({@code setChanged}) não deixa a lista para sempre: ela vence
 * no teto do sono do destino, contado da primeira recusa ({@link #refused(ItemStack, long, int)}).</li>
 * <li><b>Aprendidos:</b> os últimos itens que o destino recebeu. Uma origem que é um Baú do mod procura
 * esses itens direto pela chave ({@link ItemBulkTransfer}), antes de varrer os tipos dela: com milhares de
 * tipos, a fornalha que já recebeu ferro não espera a varredura chegar no ferro.</li>
 * </ul>
 *
 * <p>As duas listas são anéis de tamanho fixo com cópias de 1 do item; copiar só acontece quando o item entra
 * na lista, nunca por entrega.
 *
 * <p>Com mais de {@link #SIZE} tipos recusados a lista não segura todos, e uma origem grande re-simularia os mesmos
 * itens a cada visita. Por isso o destino guarda também o "tem lugar para outros" já lido ({@link #roomForOthers}),
 * e conta as recusas seguidas na visita ({@link #refusedInRow}): na {@link #SIZE}-ésima, ele dorme
 * ({@link ItemTransfer#refusedByTarget}). Uma entrega ({@link #learn}) ou uma mudança na máquina
 * ({@link #clearRefused}) zera os dois.
 */
final class ItemHints {
    static final int SIZE = 8;

    private final ItemStack[] refused = new ItemStack[SIZE];
    private int refusedSize;
    private int refusedNext;
    /** O tick da primeira recusa da lista de agora (vale só com {@code refusedSize > 0}). */
    private long refusedSince;
    private final ItemStack[] learned = new ItemStack[SIZE];
    private int learnedSize;
    private int learnedNext;
    /** O destino tem pilha de outro item com espaço, lido desde a última mudança ou entrega. */
    private boolean roomForOthers;
    /** A visita ({@link ItemTransfer#visit}) das recusas seguidas em {@link #streak}. */
    private long streakVisit = -1;
    private int streak;

    /**
     * O destino recusou {@code stack} desde a última mudança dele. Passados {@code maxTicks} desde a primeira
     * recusa da lista (o teto do sono do destino), a lista vence e é apagada: o item volta a ser oferecido.
     */
    boolean refused(ItemStack stack, long now, int maxTicks) {
        if (refusedSize > 0 && now - refusedSince > maxTicks) {
            clearRefused();
            return false;
        }
        return refused(stack);
    }

    private boolean refused(ItemStack stack) {
        for (int i = 0; i < refusedSize; i++) {
            if (ItemStack.isSameItemSameComponents(refused[i], stack)) {
                return true;
            }
        }
        return false;
    }

    /** Anota a recusa no tick {@code now}; com a lista cheia, sai a mais antiga. */
    void refuse(ItemStack stack, long now) {
        if (refused(stack)) {
            return;
        }
        if (refusedSize == 0) {
            refusedSince = now;
        }
        refused[refusedNext] = stack.copyWithCount(1);
        refusedNext = (refusedNext + 1) % SIZE;
        if (refusedSize < SIZE) {
            refusedSize++;
        }
    }

    /**
     * Mais uma recusa de um item só na visita {@code visit}, tendo lugar para outros; devolve quantas seguidas
     * nela, sem entrega no meio.
     */
    int refusedInRow(long visit) {
        if (streakVisit != visit) {
            streakVisit = visit;
            streak = 0;
        }
        return ++streak;
    }

    /** O "tem lugar para outros" lido ({@link ItemInsertPlan#roomForOthers}) ainda vale. */
    boolean roomForOthers() {
        return roomForOthers;
    }

    void sawRoomForOthers() {
        roomForOthers = true;
    }

    /** A máquina mudou: as recusas e o lugar lido não valem mais. */
    void clearRefused() {
        roomForOthers = false;
        streak = 0;
        if (refusedSize > 0) {
            Arrays.fill(refused, null);
            refusedSize = 0;
            refusedNext = 0;
        }
    }

    /**
     * O destino recebeu {@code stack}: entra nos aprendidos, se ainda não está. A entrega encheu algum slot: o
     * lugar lido e as recusas seguidas recomeçam.
     */
    void learn(ItemStack stack) {
        roomForOthers = false;
        streak = 0;
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
