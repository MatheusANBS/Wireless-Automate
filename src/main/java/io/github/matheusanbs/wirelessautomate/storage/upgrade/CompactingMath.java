package io.github.matheusanbs.wirelessautomate.storage.upgrade;

/**
 * A conta da Compactação de uma cadeia, degrau a degrau: abaixo do alvo, cada forma passa para a de cima o
 * que fecha o fator (a sobra fica); acima do alvo, as formas descem para o alvo, a mais barata primeiro,
 * até onde o espaço livre deixa. Nunca soma tudo em unidades base: 8,6 bilhões de itens × 9^11 estouraria o
 * {@code long}. Puro, com JUnit.
 */
public final class CompactingMath {
    /**
     * @param counts quanto há de cada forma, da base para cima (não é alterado)
     * @param factors {@code factors[i]} = quantos de {@code i-1} fazem 1 de {@code i}; {@code factors[0]} é ignorado
     * @param target o nível alvo
     * @param room o espaço livre do Baú, em itens ({@code Long.MAX_VALUE} = sem limite)
     * @return as contagens novas
     */
    public static long[] convert(long[] counts, int[] factors, int target, long room) {
        long[] out = counts.clone();
        long free = room;
        // 1. Abaixo do alvo: só diminui o total, então libera espaço para o passo 2.
        for (int i = 0; i < target; i++) {
            long carry = out[i] / factors[i + 1];
            if (carry > 0) {
                out[i] -= carry * factors[i + 1];
                out[i + 1] += carry;
                free = saturatedAdd(free, carry * (factors[i + 1] - 1));
            }
        }
        // 2. Acima do alvo: a forma mais próxima do alvo primeiro (a que gasta menos espaço por item).
        long ratio = 1;
        for (int i = target + 1; i < out.length && ratio != Long.MAX_VALUE; i++) {
            ratio = saturatedMul(ratio, factors[i]);
            if (out[i] == 0 || ratio == Long.MAX_VALUE) {
                continue;
            }
            long perItem = ratio - 1;
            long byRoom = free == Long.MAX_VALUE ? Long.MAX_VALUE : free / perItem;
            long byLong = (Long.MAX_VALUE - out[target]) / ratio;
            long moved = Math.min(out[i], Math.min(byRoom, byLong));
            if (moved > 0) {
                out[i] -= moved;
                out[target] += moved * ratio;
                if (free != Long.MAX_VALUE) {
                    free -= moved * perItem;
                }
            }
        }
        return out;
    }

    /**
     * Quanto a cadeia vale em itens da forma {@code level}: as formas de cima multiplicadas pelo fator até ela,
     * e as de baixo levadas para cima degrau a degrau (a sobra que não fecha um fator não conta). Satura em
     * {@code Long.MAX_VALUE}. É a contagem do limite de estoque num Baú com a Compactação.
     */
    public static long equivalent(long[] counts, int[] factors, int level) {
        long carry = 0;
        for (int i = 0; i < level; i++) {
            carry = (counts[i] + carry) / factors[i + 1];
        }
        long total = saturatedAdd(counts[level], carry);
        long ratio = 1;
        for (int i = level + 1; i < counts.length && total != Long.MAX_VALUE; i++) {
            ratio = saturatedMul(ratio, factors[i]);
            total = saturatedAdd(total, saturatedMul(counts[i], ratio));
        }
        return total;
    }

    private static long saturatedAdd(long a, long b) {
        long sum = a + b;
        return ((a ^ sum) & (b ^ sum)) < 0 ? Long.MAX_VALUE : sum;
    }

    private static long saturatedMul(long a, long b) {
        long high = Math.multiplyHigh(a, b);
        long low = a * b;
        return high != 0 || low < 0 ? Long.MAX_VALUE : low;
    }

    private CompactingMath() {
    }
}
