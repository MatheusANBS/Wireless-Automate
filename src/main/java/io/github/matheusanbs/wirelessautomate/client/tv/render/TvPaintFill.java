package io.github.matheusanbs.wirelessautomate.client.tv.render;

import io.github.matheusanbs.wirelessautomate.tv.TvBinding;
import io.github.matheusanbs.wirelessautomate.tv.TvGraphHistory;
import io.github.matheusanbs.wirelessautomate.tv.TvLayout;
import io.github.matheusanbs.wirelessautomate.tv.TvPartStyle;
import io.github.matheusanbs.wirelessautomate.tv.TvPiece;

/**
 * A fração de enchimento (0 a 1) da Barra e do Medidor: de {@code min} a {@code max} da peça. Sem máximo ({@code max}
 * não passa de {@code min}), uma fração do dado (o enchimento) vai de {@code min} a 1 e os outros números de
 * {@code min} ao maior visto no último minuto ({@link TvHistories}). Sem número, 0.
 */
final class TvPaintFill {
    private TvPaintFill() {
    }

    static double fraction(TvPiece piece, TvPartStyle part, double value, TvLayout values, long pos, int revision,
            boolean preview, long gameTime) {
        if (Double.isNaN(value)) {
            return 0;
        }
        double max = part.max();
        if (max <= part.min()) {
            TvBinding binding = piece.binding().orElse(null);
            if (binding != null && binding.fraction()) {
                max = 1;
            } else if (binding != null) {
                TvGraphHistory history = TvHistories.wanted(pos, preview, binding, values, revision, gameTime);
                max = Math.max(value, history.max(TvGraphHistory.seconds(1)));
            }
        }
        if (max <= part.min()) {
            return 0;
        }
        return Math.max(0, Math.min(1, (value - part.min()) / (max - part.min())));
    }
}
