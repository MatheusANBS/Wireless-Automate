package io.github.matheusanbs.wirelessautomate.network;

import java.util.List;
import java.util.function.ToIntFunction;

/**
 * Ordem de entrega: prioridade maior primeiro e, empatando, round-robin. Lógica pura, testada por JUnit.
 * TODO(contrato): implementar.
 */
public final class RoundRobinOrder<T> {
    public RoundRobinOrder(List<T> destinations, ToIntFunction<T> priority) {
        throw new UnsupportedOperationException("TODO");
    }

    /**
     * A ordem desta passada: grupos de prioridade decrescente; dentro de cada grupo começa pelo
     * cursor do grupo e dá a volta. Empates mantêm a ordem da lista original.
     */
    public List<T> pass() {
        throw new UnsupportedOperationException("TODO");
    }

    /** Registra uma entrega: a próxima passada do grupo de {@code destination} começa depois dele. */
    public void delivered(T destination) {
        throw new UnsupportedOperationException("TODO");
    }

    public int size() {
        throw new UnsupportedOperationException("TODO");
    }
}
