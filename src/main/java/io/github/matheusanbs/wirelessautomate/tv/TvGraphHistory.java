package io.github.matheusanbs.wirelessautomate.tv;

/**
 * O histórico de um Gráfico: um número por segundo num buffer circular de {@link #CAPACITY} posições (10 minutos), do
 * mais velho ao mais novo. Puro, sem alocar depois de criado; um segundo sem número entra como {@code NaN} e a linha
 * quebra ali. Existe só no cliente, por ligação usada por algum Gráfico à vista.
 */
public final class TvGraphHistory {
    public static final int CAPACITY = 600;

    private final double[] samples = new double[CAPACITY];
    private int head;
    private int count;

    /** Guarda o número do segundo que passou, descartando o mais velho quando o buffer está cheio. */
    public void push(double value) {
        samples[head] = value;
        head = (head + 1) % CAPACITY;
        if (count < CAPACITY) {
            count++;
        }
    }

    /** Quantos segundos há guardados (até {@link #CAPACITY}). */
    public int size() {
        return count;
    }

    /** O número de {@code age} segundos atrás: 0 é o mais novo. {@code NaN} fora do que há. */
    public double ago(int age) {
        if (age < 0 || age >= count) {
            return Double.NaN;
        }
        return samples[Math.floorMod(head - 1 - age, CAPACITY)];
    }

    /** Quantos segundos a janela de {@code minutes} minutos cobre, no máximo {@link #CAPACITY}. */
    public static int seconds(int minutes) {
        return Math.min(CAPACITY, Math.max(1, minutes) * 60);
    }

    /** O menor número da janela dos últimos {@code seconds} segundos, ou {@code NaN} sem nenhum. */
    public double min(int seconds) {
        double min = Double.NaN;
        int n = Math.min(seconds, count);
        for (int age = 0; age < n; age++) {
            double v = ago(age);
            if (!Double.isNaN(v) && (Double.isNaN(min) || v < min)) {
                min = v;
            }
        }
        return min;
    }

    /** O maior número da janela dos últimos {@code seconds} segundos, ou {@code NaN} sem nenhum. */
    public double max(int seconds) {
        double max = Double.NaN;
        int n = Math.min(seconds, count);
        for (int age = 0; age < n; age++) {
            double v = ago(age);
            if (!Double.isNaN(v) && (Double.isNaN(max) || v > max)) {
                max = v;
            }
        }
        return max;
    }

    /**
     * A escala vertical de uma janela: o par {@code [min, max]} a mapear de baixo para cima. Automática quando
     * {@code fixedMax <= fixedMin}: o mínimo e o máximo da janela, abertos em 1 para cada lado se são iguais (a
     * linha parada fica no meio); sem nenhum número, {@code [0, 1]}. Com {@code fixedMax > fixedMin}, esses dois.
     */
    public double[] scale(int seconds, double fixedMin, double fixedMax) {
        if (fixedMax > fixedMin) {
            return new double[] {fixedMin, fixedMax};
        }
        double min = min(seconds);
        double max = max(seconds);
        if (Double.isNaN(min)) {
            return new double[] {0, 1};
        }
        if (max - min < 1e-9) {
            return new double[] {min - 1, max + 1};
        }
        return new double[] {min, max};
    }
}
