package io.github.matheusanbs.wirelessautomate.network;

/**
 * Divisão de energia entre destinos num único passe. Lógica pura, testada por JUnit.
 *
 * <p>Os destinos chegam na ordem de entrega (prioridade decrescente, já girada pelo round-robin).
 * Posições seguidas com a mesma prioridade formam um grupo. O grupo de prioridade maior é atendido
 * por inteiro antes do seguinte. Dentro do grupo a divisão é por igual: quem pede menos que a parte
 * justa leva o que pede e a sobra é redividida entre os outros; o resto da divisão inteira vai, uma
 * unidade para cada, aos primeiros da ordem. As contas são em {@code long}.
 */
public final class EnergySplit {
    /**
     * Divide {@code offered} entre os {@code count} primeiros destinos.
     *
     * @param wants quanto cada destino aceita (negativo conta como 0)
     * @param priorities prioridade de cada destino, em ordem decrescente
     * @param out recebe quanto vai para cada destino; só as {@code count} primeiras posições são escritas
     * @return o total dividido, nunca mais que {@code offered} nem que a soma de {@code wants}
     */
    public static long split(long offered, long[] wants, int[] priorities, int count, long[] out) {
        for (int i = 0; i < count; i++) {
            out[i] = 0;
        }
        long left = Math.max(0, offered);
        int start = 0;
        while (start < count && left > 0) {
            int end = start + 1;
            while (end < count && priorities[end] == priorities[start]) {
                end++;
            }
            left -= fillGroup(left, wants, start, end, out);
            start = end;
        }
        return Math.max(0, offered) - left;
    }

    private static long fillGroup(long budget, long[] wants, int start, int end, long[] out) {
        long left = budget;
        int pending = 0;
        for (int i = start; i < end; i++) {
            if (wants[i] > 0) {
                pending++;
            }
        }
        while (pending > 0 && left > 0) {
            long share = left / pending;
            if (share == 0) {
                // Sobra menor que o número de destinos: uma unidade para cada, na ordem.
                for (int i = start; i < end && left > 0; i++) {
                    if (wants[i] > out[i]) {
                        out[i]++;
                        left--;
                    }
                }
                break;
            }
            boolean satisfied = false;
            for (int i = start; i < end; i++) {
                long need = wants[i] - out[i];
                if (need > 0 && need <= share) {
                    out[i] += need;
                    left -= need;
                    pending--;
                    satisfied = true;
                }
            }
            if (!satisfied) {
                // Todos querem mais que a parte justa: cada um leva a parte e só sobra o resto.
                for (int i = start; i < end; i++) {
                    if (wants[i] > out[i]) {
                        out[i] += share;
                        left -= share;
                    }
                }
            }
        }
        return budget - left;
    }

    private EnergySplit() {
    }
}
