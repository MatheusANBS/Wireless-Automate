package io.github.matheusanbs.wirelessautomate.client.tv.editor;

import org.jetbrains.annotations.Nullable;

/**
 * O salvar que saiu e espera o servidor: guarda o que foi mandado e conta os ticks sem resposta. Nunca fica preso: a
 * resposta ({@code TvLayoutAckPayload}) ou o prazo o solta, e só quem está pendente toma uma revisão nova como
 * "a nossa". Puro, com JUnit.
 *
 * @param <T> o layout
 */
final class TvPendingSave<T> {
    /** Quantos ticks sem resposta valem como salvar perdido (o canal faltou ou o pacote se perdeu): 3 s. */
    static final int TIMEOUT_TICKS = 60;

    private @Nullable T sent;
    private int waited;

    /** O salvar saiu com este layout. */
    void begin(T layout) {
        sent = layout;
        waited = 0;
    }

    boolean pending() {
        return sent != null;
    }

    /** O layout que espera a resposta, ou nulo. */
    @Nullable T sent() {
        return sent;
    }

    /** Solta o pendente (a resposta chegou ou a revisão que o confirma); devolve o que foi mandado. */
    @Nullable T release() {
        T layout = sent;
        sent = null;
        waited = 0;
        return layout;
    }

    /** Um tick de espera; verdadeiro quando o prazo acabou (e o pendente já foi solto). */
    boolean tick() {
        if (sent == null) {
            return false;
        }
        if (++waited < TIMEOUT_TICKS) {
            return false;
        }
        release();
        return true;
    }
}
