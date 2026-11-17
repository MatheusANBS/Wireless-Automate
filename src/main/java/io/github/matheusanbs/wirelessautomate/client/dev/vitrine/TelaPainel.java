package io.github.matheusanbs.wirelessautomate.client.dev.vitrine;

import io.github.matheusanbs.wirelessautomate.network.ResourceType;
import io.github.matheusanbs.wirelessautomate.tv.TvAlign;
import io.github.matheusanbs.wirelessautomate.tv.TvArea;
import io.github.matheusanbs.wirelessautomate.tv.TvBackground;
import io.github.matheusanbs.wirelessautomate.tv.TvBinding;
import io.github.matheusanbs.wirelessautomate.tv.TvClockMetric;
import io.github.matheusanbs.wirelessautomate.tv.TvColor;
import io.github.matheusanbs.wirelessautomate.tv.TvComponent;
import io.github.matheusanbs.wirelessautomate.tv.TvFont;
import io.github.matheusanbs.wirelessautomate.tv.TvFormat;
import io.github.matheusanbs.wirelessautomate.tv.TvLayout;
import io.github.matheusanbs.wirelessautomate.tv.TvPartStyle;
import io.github.matheusanbs.wirelessautomate.tv.TvPiece;
import io.github.matheusanbs.wirelessautomate.tv.TvPieceType;
import io.github.matheusanbs.wirelessautomate.tv.TvPixelArt;
import io.github.matheusanbs.wirelessautomate.tv.TvStorageMetric;
import io.github.matheusanbs.wirelessautomate.tv.TvStyle;
import io.github.matheusanbs.wirelessautomate.tv.TvTheme;
import io.github.matheusanbs.wirelessautomate.tv.TvUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.GlobalPos;

/**
 * Os layouts das Telas da vitrine: o painel ao vivo da Bateria (7 × 4 blocos, 224 × 128 px) num tema noturno que combina
 * com a pixel art, em duas partes (a metade esquerda é o layout; o título, o relógio e o medidor, e a direita, com dois
 * valores e o gráfico, é um componente que o jogador insere no editor) e a arte animada numa Tela inteira, na maior
 * escala inteira que cabe.
 */
final class TelaPainel {
    private static final TvTheme NOITE = new TvTheme("Night", 0xFF0A0C1C, 0xFF151B3A, 0xFFF6EED4, 0xFF8A97CF,
            0xFF7AF0E6, 0xFFFF6A4D);
    private static final int M = 6;

    private TelaPainel() {
    }

    /** O nome do componente com as estatísticas da bateria, o que o jogador insere no editor. */
    static final String ESTATISTICAS = "Battery stats";
    /** Onde o componente das estatísticas fica no painel pronto (o canto de cima à esquerda do cartão da direita). */
    static final int ESTATISTICAS_X = M + 76;
    static final int ESTATISTICAS_Y = 30;

    /** A metade esquerda do painel da {@code bateria}: título com o ícone, hora, medidor do enchimento e o rótulo. */
    static TvLayout painel(TvPixelArt icone, GlobalPos bateria) {
        List<TvPiece> p = new ArrayList<>();
        p.add(forma(M, 30, 70, 92, TvColor.CARD));
        p.add(peca(TvPieceType.IMAGE, M, M, 16, 16, Optional.empty(), TvFormat.DEFAULT, TvStyle.DEFAULT.withPart(
                TvPartStyle.DEFAULT.withImage(Optional.of(icone.id()), 1, TvPartStyle.VAlign.MIDDLE))));
        p.add(peca(TvPieceType.TEXT, M + 21, M + 1, 120, 14, Optional.empty(), TvFormat.DEFAULT,
                estilo(TvColor.TEXT, 13, TvAlign.LEFT).withText("BASE MONITOR").withFont(TvFont.PIXEL)));
        p.add(peca(TvPieceType.VALUE, 224 - M - 50, M + 2, 50, 12, Optional.of(TvBinding.clock(TvClockMetric.TIME)),
                TvFormat.DEFAULT, estilo(TvColor.MUTED, 10, TvAlign.RIGHT)));
        Optional<TvBinding> enchimento = bateria(bateria, TvStorageMetric.FILL);
        p.add(peca(TvPieceType.GAUGE, M + 8, 38, 54, 54, enchimento, TvFormat.DEFAULT,
                estilo(TvColor.HIGH, 8, TvAlign.CENTER).withPart(TvPartStyle.DEFAULT.withThickness(6))));
        p.add(peca(TvPieceType.VALUE, M + 15, 58, 40, 14, enchimento, new TvFormat(0, false, false, "", "", TvUnit.NONE),
                estilo(TvColor.TEXT, 14, TvAlign.CENTER)));
        p.add(texto(M, 100, 70, 10, "Battery", TvColor.MUTED));
        return TvLayout.of(p, NOITE, new TvBackground(TvBackground.Kind.GRADIENT, 0xFF1A2046, 0xFF07081A), false,
                List.of(icone));
    }

    /** As estatísticas da {@code bateria} (o que sai, o que guarda e o gráfico), como componente com a ligação junto. */
    static TvComponent estatisticas(GlobalPos bateria) {
        List<TvPiece> p = new ArrayList<>();
        p.add(forma(ESTATISTICAS_X, ESTATISTICAS_Y, 224 - 2 * M - 76, 92, TvColor.CARD));
        String[] rotulos = {"Out", "Stored"};
        TvStorageMetric[] metricas = {TvStorageMetric.OUT, TvStorageMetric.STORED};
        TvFormat[] formatos = {new TvFormat(1, true, false, "", "", TvUnit.TICK),
                new TvFormat(1, true, false, "", " FE", TvUnit.NONE)};
        for (int i = 0; i < 2; i++) {
            p.add(texto(M + 82, 36 + i * 12, 38, 10, rotulos[i], TvColor.MUTED));
            p.add(peca(TvPieceType.VALUE, M + 124, 36 + i * 12, 224 - 2 * M - 124 - 6, 10, bateria(bateria, metricas[i]),
                    formatos[i], estilo(i == 0 ? TvColor.LOW : TvColor.HIGH, 9, TvAlign.RIGHT)));
        }
        p.add(forma(M + 82, 64, 224 - 2 * M - 88, 52, TvColor.BG));
        p.add(peca(TvPieceType.GRAPH, M + 85, 67, 224 - 2 * M - 94, 46, bateria(bateria, TvStorageMetric.STORED),
                TvFormat.DEFAULT, estilo(TvColor.HIGH, 8, TvAlign.LEFT).withPart(TvPartStyle.DEFAULT.withArea(true))));
        // as caixas do componente são relativas ao canto de cima à esquerda dele
        return new TvComponent(ESTATISTICAS, p.stream().map(q -> q.withBox(q.x() - ESTATISTICAS_X, q.y() - ESTATISTICAS_Y,
                q.width(), q.height()).withGroup(1)).toList());
    }

    /** A arte numa Imagem da Tela inteira de {@code largura} × {@code altura} blocos. */
    static TvLayout arte(TvPixelArt arte, int largura, int altura) {
        TvArea area = TvArea.grown(largura, altura, TvArea.Grow.UP_RIGHT);
        int escala = Math.max(1, Math.min(area.pixelWidth() / arte.width(), area.pixelHeight() / arte.height()));
        TvPiece imagem = new TvPiece(TvPieceType.IMAGE, 0, 0, area.pixelWidth(), area.pixelHeight(), 0, "",
                Optional.empty(), TvFormat.DEFAULT, TvStyle.DEFAULT.withAlign(TvAlign.CENTER).withPart(
                TvPartStyle.DEFAULT.withImage(Optional.of(arte.id()), escala, TvPartStyle.VAlign.MIDDLE)));
        return TvLayout.of(List.of(imagem), TvTheme.GRAPHITE, TvBackground.DEFAULT, false, List.of(arte));
    }

    private static Optional<TvBinding> bateria(GlobalPos pos, TvStorageMetric metrica) {
        return Optional.of(TvBinding.storage(ResourceType.ENERGY, pos, metrica, Optional.empty()));
    }

    private static TvPiece forma(int x, int y, int w, int h, int papel) {
        return peca(TvPieceType.SHAPE, x, y, w, h, Optional.empty(), TvFormat.DEFAULT,
                new TvStyle(TvColor.theme(TvColor.TEXT), TvColor.theme(papel), 0, 0, 8, TvAlign.LEFT, true, "", 4, 100,
                        TvFont.MINECRAFT));
    }

    private static TvPiece texto(int x, int y, int w, int h, String texto, int papel) {
        return peca(TvPieceType.TEXT, x, y, w, h, Optional.empty(), TvFormat.DEFAULT,
                estilo(papel, 9, TvAlign.LEFT).withText(texto).withFont(TvFont.MINECRAFT));
    }

    private static TvStyle estilo(int papel, int tamanho, TvAlign alinhamento) {
        return new TvStyle(TvColor.theme(papel), 0, 0, 0, tamanho, alinhamento, true, "");
    }

    private static TvPiece peca(TvPieceType tipo, int x, int y, int w, int h, Optional<TvBinding> ligacao, TvFormat formato,
            TvStyle estilo) {
        return new TvPiece(tipo, x, y, w, h, 0, "", ligacao, formato, estilo);
    }
}
