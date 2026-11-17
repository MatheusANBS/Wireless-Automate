package io.github.matheusanbs.wirelessautomate.resonator;

/**
 * O que o Núcleo gerou desde o último lote, para o desgaste dos cristais: a energia gerada de fato e a que teria
 * gerado sem o corte do buffer. O lote converte a razão entre as duas em ticks gastos ({@link #take}), com a regra
 * de sempre: {@code ceil(ticks × gerado / cheio)}, e zera. Dois {@code double}, uma soma por tick; a soma de valores
 * iguais nos dois lados dá a razão exata 1 quando o buffer nunca cortou.
 */
final class CoreWear {
    private double generated;
    private double full;

    /** Soma um tick: {@code generated} entrou no buffer de um {@code full} possível (com {@code generated <= full}). */
    void add(long generated, long full) {
        this.generated += generated;
        this.full += full;
    }

    /** Houve geração possível desde o último lote. */
    boolean any() {
        return full > 0;
    }

    /**
     * Os ticks de desgaste para um lote de {@code ticks} e zera as somas: {@code ticks} se nada foi cortado, a parte
     * proporcional (arredondada para cima) se o buffer cortou, 0 sem geração.
     */
    long take(int ticks) {
        double generated = this.generated;
        double full = this.full;
        this.generated = 0;
        this.full = 0;
        return spent(ticks, generated, full);
    }

    /** A regra do desgaste, pura: {@code ceil(ticks × gerado / cheio)}, e {@code ticks} direto se nada foi cortado. */
    static long spent(int ticks, double generated, double full) {
        if (generated <= 0 || full <= 0) {
            return 0;
        }
        return generated >= full ? ticks : (long) Math.ceil(ticks * generated / full);
    }
}
