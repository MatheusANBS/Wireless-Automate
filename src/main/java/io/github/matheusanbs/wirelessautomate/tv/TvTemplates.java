package io.github.matheusanbs.wirelessautomate.tv;

import io.github.matheusanbs.wirelessautomate.network.ResourceType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Os modelos prontos de layout da Tela de Sinal. Lógica pura, sem classes do jogo além do tipo de recurso. */
public final class TvTemplates {
    private static final int BACKGROUND = 0xFF1A1C24;
    private static final int MUTED = 0xFF8D8577;
    private static final int IN_COLOR = 0xFF3DB56E;
    private static final int OUT_COLOR = 0xFFF26A48;

    private TvTemplates() {
    }

    /**
     * O "Painel de rede", proporcional à área: o nome da rede em cima, o que entra e o que sai da rede no tipo dado
     * e, embaixo, quantas portas movem. Todas as medidas em pixels de desenho, divisões inteiras.
     */
    public static TvLayout networkPanel(String networkName, int networkColor, UUID network, ResourceType type,
            TvArea area) {
        int w = area.pixelWidth();
        int h = area.pixelHeight();
        int m = Math.max(2, Math.min(w, h) / 16);
        int inner = w - 2 * m;
        TvUnit rate = type.ratePerTick() ? TvUnit.TICK : TvUnit.SECOND;
        int row = h / 4;

        TvPiece shape = new TvPiece(TvPieceType.SHAPE, 0, 0, w, h, 0, "", Optional.empty(), TvFormat.DEFAULT,
                new TvStyle(TvStyle.DEFAULT.color(), BACKGROUND, networkColor | 0xFF000000, 1, TvStyle.DEFAULT.size(),
                        TvAlign.LEFT, false, ""));
        TvPiece title = new TvPiece(TvPieceType.TEXT, m, m, inner, h / 6, 0, "", Optional.empty(), TvFormat.DEFAULT,
                new TvStyle(MUTED, 0, 0, 0, h / 6, TvAlign.LEFT, true, networkName));
        TvPiece in = value(m, m + h / 6, inner, row, "▲ ", IN_COLOR, rate,
                new TvBinding(network, type, TvMetric.IN));
        TvPiece out = value(m, m + h / 6 + h / 4, inner, row, "▼ ", OUT_COLOR, rate,
                new TvBinding(network, type, TvMetric.OUT));
        TvPiece moving = new TvPiece(TvPieceType.VALUE, m, h - m - h / 8, inner, h / 8, 0, "",
                Optional.of(new TvBinding(network, type, TvMetric.MOVING)),
                new TvFormat(0, false, false, "⇄ ", "", TvUnit.NONE),
                new TvStyle(MUTED, 0, 0, 0, h / 8, TvAlign.LEFT, true, ""));
        return TvLayout.of(List.of(shape, title, in, out, moving));
    }

    private static TvPiece value(int x, int y, int width, int height, String prefix, int color, TvUnit unit,
            TvBinding binding) {
        return new TvPiece(TvPieceType.VALUE, x, y, width, height, 0, "", Optional.of(binding),
                new TvFormat(TvFormat.DEFAULT.decimals(), true, false, prefix, "", unit),
                new TvStyle(color, 0, 0, 0, height, TvAlign.LEFT, true, ""));
    }
}
