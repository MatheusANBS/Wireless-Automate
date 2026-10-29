package io.github.matheusanbs.wirelessautomate.tv;

import java.util.ArrayDeque;
import java.util.Iterator;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;

/**
 * Uma fila de envios com orçamento de bytes por tick (pura, com JUnit): a cada tick sai da frente o que cabe no
 * orçamento, na ordem em que entrou, e o resto espera o tick seguinte. O primeiro item de um tick sempre sai, mesmo
 * maior que o orçamento, para um item grande nunca travar a fila. Um item que venceu (tamanho negativo) sai sem contar.
 */
public final class TvSendQueue<T> {
    private final ArrayDeque<T> items = new ArrayDeque<>();

    public void add(T item) {
        items.add(item);
    }

    public boolean isEmpty() {
        return items.isEmpty();
    }

    public int size() {
        return items.size();
    }

    /** Tira os itens que casam; verdadeiro se tirou algum. */
    public boolean removeIf(Predicate<T> which) {
        boolean removed = false;
        Iterator<T> it = items.iterator();
        while (it.hasNext()) {
            if (which.test(it.next())) {
                it.remove();
                removed = true;
            }
        }
        return removed;
    }

    /**
     * O envio de um tick: entrega a {@code send} os itens da frente enquanto a soma dos tamanhos ({@code size}, em
     * bytes; negativo é um item vencido, que sai sem ir) cabe em {@code budget}, com o primeiro sempre entregue.
     * Devolve os bytes entregues.
     */
    public int drain(int budget, ToIntFunction<T> size, Consumer<T> send) {
        int spent = 0;
        boolean first = true;
        while (!items.isEmpty()) {
            T head = items.peekFirst();
            int bytes = size.applyAsInt(head);
            if (bytes < 0) {
                items.pollFirst();
                continue;
            }
            if (!first && spent + bytes > budget) {
                break;
            }
            items.pollFirst();
            send.accept(head);
            spent += bytes;
            first = false;
        }
        return spent;
    }
}
