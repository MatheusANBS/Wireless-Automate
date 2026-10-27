package io.github.matheusanbs.wirelessautomate.client.tv.editor;

import io.github.matheusanbs.wirelessautomate.packet.TvLayoutAckPayload;
import java.util.function.Consumer;
import org.jetbrains.annotations.Nullable;

/**
 * Entrega a resposta do servidor ao salvar ({@link TvLayoutAckPayload}) ao editor aberto, pelo {@code containerId}.
 * Uma resposta atrasada, de um menu que já fechou, é ignorada.
 */
public final class TvEditorAck {
    private static int containerId = -1;
    private static @Nullable Consumer<TvLayoutAckPayload.Result> listener;

    private TvEditorAck() {
    }

    /** O editor do menu {@code container} passa a receber as respostas. */
    static void listen(int container, Consumer<TvLayoutAckPayload.Result> onAck) {
        containerId = container;
        listener = onAck;
    }

    /** O editor fechou. */
    static void stop() {
        listener = null;
        containerId = -1;
    }

    /** Se o editor do menu {@code container} está aberto. */
    static boolean isOpen(int container) {
        return listener != null && containerId == container;
    }

    /** O gancho do payload (preenchido no {@code ClientSetup}). */
    public static void receive(TvLayoutAckPayload payload) {
        if (listener != null && payload.containerId() == containerId) {
            listener.accept(payload.result());
        }
    }
}
