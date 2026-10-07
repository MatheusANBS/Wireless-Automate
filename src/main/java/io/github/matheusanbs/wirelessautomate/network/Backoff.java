package io.github.matheusanbs.wirelessautomate.network;

/**
 * Destino dormindo: depois de recusar, só é tentado de novo após um intervalo que dobra a cada
 * recusa, de 1 tick até {@code maxTicks}. Uma mudança no vizinho o acorda na hora. Lógica pura.
 * TODO(contrato): implementar.
 */
public final class Backoff {
    public Backoff(int maxTicks) {
        throw new UnsupportedOperationException("TODO");
    }

    public boolean isAwake(long gameTime) {
        throw new UnsupportedOperationException("TODO");
    }

    /** O destino recusou: dorme pelo intervalo atual e dobra o próximo. */
    public void sleep(long gameTime) {
        throw new UnsupportedOperationException("TODO");
    }

    /** O destino aceitou ou o vizinho mudou: acorda e volta ao intervalo mínimo. */
    public void wake() {
        throw new UnsupportedOperationException("TODO");
    }

    public boolean isSleeping(long gameTime) {
        return !isAwake(gameTime);
    }
}
