package io.github.matheusanbs.wirelessautomate.client.resonator.screen;

/**
 * O dial que a tela mandou e o servidor ainda não confirmou: base dos cliques seguidos e valor mostrado, até o
 * estado do servidor chegar a ele. Se não chegar (recusado, ou o servidor arredondou diferente), expira e volta a
 * valer o do servidor. Lógica pura.
 */
final class PendingDial {
    /** O estado vem a cada 5 ticks: um segundo é folga para a confirmação. */
    static final long TIMEOUT_MS = 1000;
    private static final double EPSILON = 1e-6;

    private double sent = Double.NaN;
    private long sentAt;

    /** O dial a usar agora: o último mandado, enquanto não confirmado nem expirado; senão o do servidor. */
    double base(double serverDial, long nowMs) {
        if (!Double.isNaN(sent) && (Math.abs(serverDial - sent) < EPSILON || nowMs - sentAt > TIMEOUT_MS)) {
            sent = Double.NaN;
        }
        return Double.isNaN(sent) ? serverDial : sent;
    }

    void sent(double dial, long nowMs) {
        sent = dial;
        sentAt = nowMs;
    }
}
