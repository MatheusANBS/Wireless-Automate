package io.github.matheusanbs.wirelessautomate.resonator;

/**
 * A interferência que os vizinhos fazem num Núcleo, guardada pelo {@link ResonatorManager} nos eventos de montar,
 * desmontar, carregar e descarregar; a geração só lê o fator, em cache. Fora do NBT: a carga refaz a conta.
 */
public final class CoreInterference {
    private int lossPermille;
    private int nearest = ResonatorInterference.NONE;
    private double factor = 1;

    /** A perda da eficiência em milésimos (0 sem vizinho). */
    public int lossPermille() {
        return lossPermille;
    }

    /** A distância de Chebyshev do vizinho mais perto que conta, ou {@link ResonatorInterference#NONE}. */
    public int nearest() {
        return nearest;
    }

    /** O fator que multiplica a eficiência, depois do teto do Sintonizador. */
    public double factor() {
        return factor;
    }

    /** Guarda a conta nova e devolve se o fator mudou (e com ele a geração do Núcleo). */
    boolean set(int lossPermille, int nearest) {
        this.lossPermille = lossPermille;
        this.nearest = nearest;
        double before = factor;
        factor = ResonatorInterference.factor(lossPermille);
        return factor != before;
    }
}
