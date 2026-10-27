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
        return TvLayout.of(panelPieces(networkName, networkColor, network, type, area, 0));
    }

    /** O mesmo painel como peças; sem rede ({@code network} nulo) as peças ficam sem ligação. */
    private static List<TvPiece> panelPieces(String networkName, int networkColor, UUID network, ResourceType type,
            TvArea area, int group) {
        int w = area.pixelWidth();
        int h = area.pixelHeight();
        int m = Math.max(2, Math.min(w, h) / 16);
        int inner = w - 2 * m;
        TvUnit rate = type.ratePerTick() ? TvUnit.TICK : TvUnit.SECOND;
        int row = h / 4;

        TvPiece shape = new TvPiece(TvPieceType.SHAPE, 0, 0, w, h, group, "", Optional.empty(), TvFormat.DEFAULT,
                new TvStyle(TvStyle.DEFAULT.color(), BACKGROUND, networkColor | 0xFF000000, 1, TvStyle.DEFAULT.size(),
                        TvAlign.LEFT, false, ""));
        TvPiece title = new TvPiece(TvPieceType.TEXT, m, m, inner, h / 6, group, "", Optional.empty(), TvFormat.DEFAULT,
                new TvStyle(MUTED, 0, 0, 0, h / 6, TvAlign.LEFT, true, networkName));
        TvPiece in = value(m, m + h / 6, inner, row, "▲ ", IN_COLOR, rate, group, bind(network, type, TvMetric.IN));
        TvPiece out = value(m, m + h / 6 + h / 4, inner, row, "▼ ", OUT_COLOR, rate, group,
                bind(network, type, TvMetric.OUT));
        TvPiece moving = new TvPiece(TvPieceType.VALUE, m, h - m - h / 8, inner, h / 8, group, "",
                bind(network, type, TvMetric.MOVING),
                new TvFormat(0, false, false, "⇄ ", "", TvUnit.NONE),
                new TvStyle(MUTED, 0, 0, 0, h / 8, TvAlign.LEFT, true, ""));
        return List.of(shape, title, in, out, moving);
    }

    /**
     * O contrato dos componentes prontos: o {@link TvComponent#name()} guardado é a chave {@code tv.component.*} e o
     * lang tem {@code gui.wirelessautomate.} mais a chave. Os textos {@link #LABEL_TEXT} e {@link #TITLE_TEXT} vão
     * fixos em português no dado (o layout é salvo no servidor e vale para todos); quem insere o componente troca
     * esses dois pelo texto do idioma dele ({@code tv.component.number_card.label} e {@code tv.component.title.text}).
     */
    public static final String LABEL_TEXT = "Rótulo";
    public static final String TITLE_TEXT = "Título";

    /** O nome (chave de tradução) do componente pronto "Painel de rede". */
    public static final String NETWORK_PANEL = "tv.component.network_panel";
    public static final String NUMBER_CARD = "tv.component.number_card";
    public static final String TITLE = "tv.component.title";

    /**
     * Os componentes prontos, na ordem da lista do editor: o Painel de rede (o da etapa 1, sem rede, em 3 por 2
     * blocos), o Cartão de número e o Título. Cada um é um grupo (grupo 1) só com Valor, Texto e Forma, com as cores
     * do tema e as caixas relativas ao canto do componente.
     */
    public static List<TvComponent> components() {
        return List.of(
                new TvComponent(NETWORK_PANEL, panelPieces("", 0xFF8D8577, null, ResourceType.ITEM,
                        new TvArea(3, 2, 0, 0), 1)),
                new TvComponent(NUMBER_CARD, List.of(
                        new TvPiece(TvPieceType.SHAPE, 0, 0, 64, 32, 1, "", Optional.empty(), TvFormat.DEFAULT,
                                new TvStyle(TvColor.theme(TvColor.TEXT), TvColor.theme(TvColor.CARD), 0, 0, 8,
                                        TvAlign.LEFT, true, "", 2, 100, TvFont.MINECRAFT)),
                        new TvPiece(TvPieceType.TEXT, 4, 3, 56, 8, 1, "", Optional.empty(), TvFormat.DEFAULT,
                                new TvStyle(TvColor.theme(TvColor.MUTED), 0, 0, 0, 8, TvAlign.LEFT, true, LABEL_TEXT)),
                        new TvPiece(TvPieceType.VALUE, 4, 14, 56, 14, 1, "", Optional.empty(), TvFormat.DEFAULT,
                                new TvStyle(TvColor.theme(TvColor.TEXT), 0, 0, 0, 14, TvAlign.LEFT, true, "")))),
                new TvComponent(TITLE, List.of(
                        new TvPiece(TvPieceType.TEXT, 0, 0, 96, 16, 1, "", Optional.empty(), TvFormat.DEFAULT,
                                new TvStyle(TvColor.theme(TvColor.TEXT), 0, 0, 0, 16, TvAlign.LEFT, true, TITLE_TEXT)),
                        new TvPiece(TvPieceType.SHAPE, 0, 18, 96, 2, 1, "", Optional.empty(), TvFormat.DEFAULT,
                                new TvStyle(TvColor.theme(TvColor.HIGH), TvColor.theme(TvColor.HIGH), 0, 0, 8,
                                        TvAlign.LEFT, true, "")))));
    }

    private static Optional<TvBinding> bind(UUID network, ResourceType type, TvMetric metric) {
        return network == null ? Optional.empty() : Optional.of(new TvBinding(network, type, metric));
    }

    private static TvPiece value(int x, int y, int width, int height, String prefix, int color, TvUnit unit,
            int group, Optional<TvBinding> binding) {
        return new TvPiece(TvPieceType.VALUE, x, y, width, height, group, "", binding,
                new TvFormat(TvFormat.DEFAULT.decimals(), true, false, prefix, "", unit),
                new TvStyle(color, 0, 0, 0, height, TvAlign.LEFT, true, ""));
    }
}
