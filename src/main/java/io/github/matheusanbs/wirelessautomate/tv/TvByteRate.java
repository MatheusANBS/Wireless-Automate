package io.github.matheusanbs.wirelessautomate.tv;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;

/**
 * Os bytes que cada um gastou no último minuto (puro, com JUnit), numa janela que anda: cada gasto é guardado com o
 * instante e sai depois de {@link #WINDOW_MS}. O envio de imagens reserva o tamanho do arquivo inteiro no começo do
 * envio, então um jogador nunca passa do teto por minuto, nem com envios cortados. Memória: um par por envio aceito no
 * minuto, e quem não gastou nada não fica no mapa.
 */
public final class TvByteRate<K> {
    public static final long WINDOW_MS = 60_000L;
    private final Map<K, ArrayDeque<long[]>> spent = new HashMap<>();

    /** Os bytes gastos por {@code key} na janela até {@code now}. */
    public long used(K key, long now) {
        ArrayDeque<long[]> list = spent.get(key);
        if (list == null) {
            return 0;
        }
        expire(list, now);
        long sum = 0;
        for (long[] entry : list) {
            sum += entry[1];
        }
        if (list.isEmpty()) {
            spent.remove(key);
        }
        return sum;
    }

    /** Gasta {@code bytes} se cabem no {@code limit} da janela; falso (e nada gasto) se não cabem. */
    public boolean tryUse(K key, long bytes, long limit, long now) {
        if (used(key, now) + bytes > limit) {
            return false;
        }
        spent.computeIfAbsent(key, unused -> new ArrayDeque<>()).add(new long[] {now, bytes});
        return true;
    }

    private static void expire(ArrayDeque<long[]> list, long now) {
        while (!list.isEmpty() && now - list.peekFirst()[0] >= WINDOW_MS) {
            list.pollFirst();
        }
    }

    /** Tira de todos o que saiu da janela, e quem ficou sem nada sai do mapa. */
    public void sweep(long now) {
        spent.values().removeIf(list -> {
            expire(list, now);
            return list.isEmpty();
        });
    }

    public void forget(K key) {
        spent.remove(key);
    }

    public void clear() {
        spent.clear();
    }
}
