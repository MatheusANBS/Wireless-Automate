package io.github.matheusanbs.wirelessautomate.client;

/**
 * Um botão que pede dois cliques, como o Remover do Tablet e o Desvincular de uma máquina do alcance: o
 * primeiro arma e o segundo, dentro do prazo, confirma. O tempo vem de quem chama, em ms, para a regra
 * ser testada sem relógio.
 */
public final class ArmedClick {
    /** Quanto tempo o botão fica armado esperando o segundo clique. */
    public static final long WINDOW_MS = 3000;

    private long armedUntil = Long.MIN_VALUE;

    public void arm(long nowMs) {
        armedUntil = nowMs + WINDOW_MS;
    }

    public boolean armed(long nowMs) {
        return nowMs < armedUntil;
    }

    public void disarm() {
        armedUntil = Long.MIN_VALUE;
    }

    /** Um clique: arma se estava desarmado e devolve {@code false}; confirma (e desarma) se estava armado. */
    public boolean click(long nowMs) {
        if (armed(nowMs)) {
            disarm();
            return true;
        }
        arm(nowMs);
        return false;
    }
}
