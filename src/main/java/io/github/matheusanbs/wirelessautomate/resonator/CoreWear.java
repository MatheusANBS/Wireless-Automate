package io.github.matheusanbs.wirelessautomate.resonator;

/**
 * O que o Núcleo gerou desde o último lote, para o desgaste dos cristais: a energia gerada de fato, a que teria
 * gerado sem o corte do buffer e quantos ticks tiveram geração possível. O lote converte isso em ticks gastos
 * ({@link #take}): {@code ceil(ticksQueGeraram × gerado / cheio)}, e zera. Contar os ticks que geraram (e não o
 * tamanho nominal do lote) evita gastar o cristal por ticks de uma janela parcial (cristal posto, Núcleo montado ou
 * chunk carregado no meio dela). Dois {@code double}, uma soma por tick; a soma de valores
 * iguais nos dois lados dá a razão exata 1 quando o buffer nunca cortou.
 */
final class CoreWear {
    private double generated;
    private double full;
    private int ticks;

    /** Soma um tick: {@code generated} entrou no buffer de um {@code full} possível (com {@code generated <= full}). */
    void add(long generated, long full) {
        this.generated += generated;
        this.full += full;
        ticks++;
    }

    /** Houve geração possível desde o último lote. */
    boolean any() {
        return full > 0;
    }

    /**
     * Os ticks de desgaste da janela e zera as somas: os ticks que tiveram geração possível se nada foi cortado, a
     * parte proporcional (arredondada para cima) se o buffer cortou, 0 sem geração.
     */
    long take() {
        double generated = this.generated;
        double full = this.full;
        int ticks = this.ticks;
        this.generated = 0;
        this.full = 0;
        this.ticks = 0;
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
