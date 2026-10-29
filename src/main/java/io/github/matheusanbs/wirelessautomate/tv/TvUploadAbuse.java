package io.github.matheusanbs.wirelessautomate.tv;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A trava automática do envio de imagens (pura, com JUnit): quem bate no limite por minuto {@link #STRIKES} vezes em
 * {@link #WINDOW_MS} fica um tempo sem enviar (o {@code tv.images.abuseCooldownMinutes}; 0 desliga a trava). Conta
 * também as recusas por jogador desde a subida do servidor, que a lista {@code /wa tela imagens} mostra. Nunca expulsa
 * nem bane a conta. Fica na memória: a trava não sobrevive a um reinício do servidor.
 */
public final class TvUploadAbuse<K> {
    public static final int STRIKES = 3;
    public static final long WINDOW_MS = 10 * 60_000L;
    private final Map<K, ArrayDeque<Long>> strikes = new HashMap<>();
    private final Map<K, Long> until = new HashMap<>();
    private final Map<K, Integer> refusals = new LinkedHashMap<>();

    /**
     * Anota uma batida no limite em {@code now}; verdadeiro se ela travou o jogador agora (a terceira na janela, com
     * {@code cooldownMs} maior que 0). A trava zera as batidas.
     */
    public boolean strike(K key, long now, long cooldownMs) {
        refused(key);
        if (cooldownMs <= 0) {
            return false;
        }
        ArrayDeque<Long> list = strikes.computeIfAbsent(key, unused -> new ArrayDeque<>());
        while (!list.isEmpty() && now - list.peekFirst() >= WINDOW_MS) {
            list.pollFirst();
        }
        list.add(now);
        if (list.size() < STRIKES) {
            return false;
        }
        strikes.remove(key);
        until.put(key, now + cooldownMs);
        return true;
    }

    /** Conta uma recusa na lista (a trava conta as dela também). */
    public void refused(K key) {
        refusals.merge(key, 1, Integer::sum);
    }

    /** Os milissegundos que faltam da trava de {@code key} em {@code now} (0 sem trava). */
    public long cooldownLeft(K key, long now) {
        Long end = until.get(key);
        if (end == null) {
            return 0;
        }
        if (now >= end) {
            until.remove(key);
            return 0;
        }
        return end - now;
    }

    /** As recusas por jogador, na ordem da primeira; só leitura. */
    public Map<K, Integer> refusals() {
        return Collections.unmodifiableMap(refusals);
    }

    public void clear() {
        strikes.clear();
        until.clear();
        refusals.clear();
    }
}
