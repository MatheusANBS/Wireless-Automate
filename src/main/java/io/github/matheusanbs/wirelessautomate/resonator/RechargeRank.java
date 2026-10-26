package io.github.matheusanbs.wirelessautomate.resonator;

/**
 * Quais Opacos o Núcleo recarrega no mesmo ciclo: todos os recarregáveis que o catalisador paga, na ordem das
 * câmaras (um catalisador por cristal). O servidor decide pelo catalisador do Núcleo, o cliente da câmara pelo
 * {@link ChamberView#rechargers()} e a tela pela pilha do slot do catalisador. Lógica pura, sem classes do Minecraft.
 */
public final class RechargeRank {
    private RechargeRank() {
    }

    /** Quantos Opacos recarregam no ciclo: um por catalisador, até o total de recarregáveis; nunca negativo. */
    public static int size(int rechargeable, int catalyst) {
        return Math.max(0, Math.min(rechargeable, catalyst));
    }

    /**
     * Se o Opaco de posto {@code rank} (0 é o primeiro recarregável na ordem das câmaras, -1 uma câmara que não é
     * recarregável) está entre os {@code size} que recarregam.
     */
    public static boolean includes(int rank, int size) {
        return rank >= 0 && rank < size;
    }
}
