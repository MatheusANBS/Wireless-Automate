package io.github.matheusanbs.wirelessautomate.client;

import io.github.matheusanbs.wirelessautomate.packet.PanelNoticePayload;
import org.jetbrains.annotations.Nullable;

/**
 * O último aviso do painel do nó, guardado com o instante em que chegou. As telas que hospedam o
 * painel desenham {@link #current()} e, no Desfazer, agem pelo {@link PanelNoticePayload#undo()}.
 */
public final class PanelNotices {
    /** Por quanto tempo o aviso aparece. */
    public static final long DURATION_MS = 5000;

    private static @Nullable PanelNoticePayload last;
    private static long shownAt;

    /** Guarda {@code notice} como o aviso atual, agora. */
    public static void show(PanelNoticePayload notice) {
        last = notice;
        shownAt = System.currentTimeMillis();
    }

    /** O aviso dos últimos {@link #DURATION_MS} ms, ou {@code null}. */
    public static @Nullable PanelNoticePayload current() {
        return current(System.currentTimeMillis());
    }

    static @Nullable PanelNoticePayload current(long nowMillis) {
        if (last != null && nowMillis - shownAt >= DURATION_MS) {
            last = null;
        }
        return last;
    }

    /** Some com o aviso (depois do Desfazer ou ao fechar a tela). */
    public static void clear() {
        last = null;
    }

    private PanelNotices() {
    }
}
