package io.github.matheusanbs.wirelessautomate.client.resonator.render;

/**
 * Se um loop da Armilar ainda vale como tocando, em lógica pura. O {@code SoundManager} pode derrubar a instância sem
 * chamar {@code stop()} (F3+T, o dispositivo de áudio que reinicia, o pool de canais cheio): então {@code isStopped}
 * continua falso e só o {@code isActive} acusa. Logo depois de pedido o {@code play} ainda pode não constar como
 * ativo, e por isso há {@link #GRACE_TICKS} de tolerância antes de dar o loop por morto e recriá-lo.
 */
final class ArmilarLoopLife {
    static final long GRACE_TICKS = 3;

    private ArmilarLoopLife() {
    }

    /** Vale como tocando: não parou, e está ativa no engine ou foi pedida há no máximo {@link #GRACE_TICKS} ticks. */
    static boolean alive(boolean stopped, boolean activeInEngine, long now, long startedAt) {
        return !stopped && (activeInEngine || now - startedAt <= GRACE_TICKS);
    }
}
