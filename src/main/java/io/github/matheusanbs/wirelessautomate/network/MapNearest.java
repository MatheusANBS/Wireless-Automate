package io.github.matheusanbs.wirelessautomate.network;

import java.util.Arrays;

/**
 * Quais pontos o Mapa do Tablet manda quando há mais que o teto: os {@code cap} mais perto do jogador, devolvidos na
 * ordem original (os pontos que ficam não trocam de lugar só porque outro entrou). Lógica pura, testada por JUnit.
 */
public final class MapNearest {
    private MapNearest() {
    }

    /**
     * Os índices dos {@code cap} menores valores de {@code distances} (empate: o índice menor), em ordem crescente de
     * índice. Com {@code cap} maior ou igual ao tamanho, todos.
     */
    public static int[] pick(long[] distances, int cap) {
        int n = distances.length;
        if (cap >= n) {
            int[] all = new int[n];
            for (int i = 0; i < n; i++) {
                all[i] = i;
            }
            return all;
        }
        // só roda com mais pontos que o teto, uma vez por amostra do Mapa aberto: o Integer[] não pesa
        Integer[] order = new Integer[n];
        for (int i = 0; i < n; i++) {
            order[i] = i;
        }
        Arrays.sort(order, (a, b) -> {
            int byDistance = Long.compare(distances[a], distances[b]);
            return byDistance != 0 ? byDistance : Integer.compare(a, b);
        });
        int[] kept = new int[Math.max(0, cap)];
        for (int i = 0; i < kept.length; i++) {
            kept[i] = order[i];
        }
        Arrays.sort(kept);
        return kept;
    }
}
