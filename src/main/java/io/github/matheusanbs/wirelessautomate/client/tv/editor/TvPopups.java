package io.github.matheusanbs.wirelessautomate.client.tv.editor;

import org.jetbrains.annotations.Nullable;

/** A janela solta aberta agora no editor, se houver (uma por vez), e o retângulo em que ela pode aparecer. */
final class TvPopups {
    private @Nullable TvPopup current;
    private TvRect bounds = TvRect.NONE;

    void bounds(TvRect bounds) {
        this.bounds = bounds;
    }

    TvRect bounds() {
        return bounds;
    }

    void open(TvPopup popup) {
        close();
        current = popup;
    }

    void close() {
        TvPopup old = current;
        current = null;
        if (old != null) {
            old.closed();
        }
    }

    boolean isOpen() {
        return current != null;
    }

    @Nullable TvPopup current() {
        return current;
    }
}
