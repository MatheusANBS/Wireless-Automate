package io.github.matheusanbs.wirelessautomate.client;

/**
 * A chave suja de um cálculo de tela que vale por um quadro: o layout feito no desenho serve à dica, ao realce
 * e ao que vem por cima no mesmo quadro, sem refazer as medidas de texto. Vale enquanto o quadro e o
 * {@code token} (comparado por identidade, como o aviso do painel) são os mesmos e ninguém chamou
 * {@link #invalidate}: um evento de mouse ou teclado e as medidas novas invalidam, porque mudam o estado entre
 * dois quadros. Lógica pura, com JUnit.
 */
public final class FrameCache {
    private long frame = Long.MIN_VALUE;
    private Object token;
    private boolean dirty = true;

    /**
     * Diz se o cálculo do quadro {@code frame} com o {@code token} já foi feito e ainda vale; se não, marca como
     * feito agora (quem chama refaz o cálculo em seguida).
     */
    public boolean reuse(long frame, Object token) {
        if (!dirty && frame == this.frame && token == this.token) {
            return true;
        }
        this.frame = frame;
        this.token = token;
        dirty = false;
        return false;
    }

    /** O próximo {@link #reuse} refaz o cálculo, mesmo no mesmo quadro. */
    public void invalidate() {
        dirty = true;
    }
}
