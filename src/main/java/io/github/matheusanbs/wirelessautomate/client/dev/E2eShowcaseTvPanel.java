package io.github.matheusanbs.wirelessautomate.client.dev;

import io.github.matheusanbs.wirelessautomate.network.ResourceType;
import io.github.matheusanbs.wirelessautomate.tv.TvAlign;
import io.github.matheusanbs.wirelessautomate.tv.TvArea;
import io.github.matheusanbs.wirelessautomate.tv.TvBackground;
import io.github.matheusanbs.wirelessautomate.tv.TvBinding;
import io.github.matheusanbs.wirelessautomate.tv.TvClockMetric;
import io.github.matheusanbs.wirelessautomate.tv.TvColor;
import io.github.matheusanbs.wirelessautomate.tv.TvFont;
import io.github.matheusanbs.wirelessautomate.tv.TvFormat;
import io.github.matheusanbs.wirelessautomate.tv.TvLayout;
import io.github.matheusanbs.wirelessautomate.tv.TvMetric;
import io.github.matheusanbs.wirelessautomate.tv.TvPartStyle;
import io.github.matheusanbs.wirelessautomate.tv.TvPicture;
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
import java.util.UUID;
import net.minecraft.core.GlobalPos;
import org.jetbrains.annotations.Nullable;

/**
 * Os layouts das três Telas da vitrine ({@link E2eShowcaseTv}): o painel ao vivo num tema noturno que combina com a
 * pixel art (o ícone animado, o título, o relógio do mundo, o medidor da Bateria, quatro valores e o gráfico
 * do que a Bateria guarda numa área própria), a arte em escala inteira e o GIF esticado.
 */
final class E2eShowcaseTvPanel {
    /** O tema do painel, com as cores da paleta da arte: o azul da noite, o ciano das ondas e o coral do LED. */
    private static final TvTheme NIGHT = new TvTheme("Night", 0xFF0A0C1C, 0xFF151B3A, 0xFFF6EED4, 0xFF8A97CF,
            0xFF7AF0E6, 0xFFFF6A4D);

    private E2eShowcaseTvPanel() {
    }

    /** A margem de fora, a mesma nos quatro lados, e a de dentro dos cartões. */
    private static final int M = 6;
    private static final int PAD = 5;
    private static final int W = 224;
    private static final int H = 128;

    /**
     * O painel de 224 × 128 (7 × 4 blocos), com a margem de {@value #M} px nos quatro lados: no alto o ícone, "Base
     * Monitor" e a hora; à esquerda, num cartão, o medidor da Bateria com a porcentagem; à direita, noutro, os rótulos
     * numa coluna e os valores alinhados à direita noutra (o que sai da Bateria, o que ela guarda, os itens do Baú e a
     * vazão da linha de minério) e embaixo, numa área própria com legenda, o gráfico do que a Bateria guarda.
     */
    static TvLayout layout(TvPixelArt icon, GlobalPos battery, GlobalPos chest, @Nullable UUID ores) {
        List<TvPiece> pieces = new ArrayList<>();
        int top = 31;
        int bottom = H - M;
        int leftW = 66;
        int rightX = M + leftW + M;
        int rightW = W - M - rightX;
        pieces.add(shape(M, top, leftW, bottom - top, TvColor.CARD, 4));
        pieces.add(shape(rightX, top, rightW, bottom - top, TvColor.CARD, 4));
        pieces.add(new TvPiece(TvPieceType.IMAGE, M, M, 16, 16, 0, "", Optional.empty(), TvFormat.DEFAULT,
                TvStyle.DEFAULT.withPart(TvPartStyle.DEFAULT.withImage(Optional.of(icon.id()), 1,
                        TvPartStyle.VAlign.MIDDLE))));
        pieces.add(text(M + 21, M + 1, 120, 14, "BASE MONITOR", TvColor.TEXT, 13, TvAlign.LEFT, TvFont.PIXEL));
        pieces.add(piece(TvPieceType.VALUE, W - M - 50, M + 2, 50, 12, Optional.of(TvBinding.clock(TvClockMetric.TIME)),
                TvFormat.DEFAULT, style(TvColor.MUTED, 10, TvAlign.RIGHT)));
        TvPiece divider = shape(M, 26, W - 2 * M, 1, TvColor.MUTED, 0);
        pieces.add(divider.withStyle(divider.style().withOpacity(45)));
        Optional<TvBinding> fill = storage(battery, ResourceType.ENERGY, TvStorageMetric.FILL);
        int gauge = 54;
        int gx = M + (leftW - gauge) / 2;
        int gy = top + PAD;
        pieces.add(piece(TvPieceType.GAUGE, gx, gy, gauge, gauge, fill, TvFormat.DEFAULT,
                style(TvColor.HIGH, 8, TvAlign.CENTER).withPart(TvPartStyle.DEFAULT.withThickness(6))));
        pieces.add(piece(TvPieceType.VALUE, gx + 7, gy + 20, gauge - 14, 14, fill,
                new TvFormat(0, false, false, "", "", TvUnit.NONE), style(TvColor.TEXT, 14, TvAlign.CENTER)));
        pieces.add(text(M, bottom - PAD - 21, leftW, 10, "Battery", TvColor.MUTED, 9, TvAlign.CENTER, TvFont.MINECRAFT));
        pieces.add(text(M, bottom - PAD - 10, leftW, 10, "Emerald", TvColor.LOW, 8, TvAlign.CENTER, TvFont.MINECRAFT));
        String[] labels = {"Out", "Stored", "Items", "Ores"};
        List<Optional<TvBinding>> values = List.of(storage(battery, ResourceType.ENERGY, TvStorageMetric.OUT),
                storage(battery, ResourceType.ENERGY, TvStorageMetric.STORED),
                storage(chest, ResourceType.ITEM, TvStorageMetric.STORED),
                ores == null ? Optional.empty() : Optional.of(new TvBinding(ores, ResourceType.ITEM, TvMetric.IN)));
        TvFormat[] formats = {new TvFormat(1, true, false, "", "", TvUnit.TICK),
                new TvFormat(1, true, false, "", " FE", TvUnit.NONE),
                new TvFormat(1, true, false, "", "", TvUnit.NONE), new TvFormat(1, true, false, "", "", TvUnit.SECOND)};
        int innerX = rightX + PAD;
        int innerW = rightW - 2 * PAD;
        int labelW = 38;
        for (int i = 0; i < labels.length; i++) {
            int y = top + PAD + i * 11;
            pieces.add(text(innerX, y, labelW, 10, labels[i], TvColor.MUTED, 9, TvAlign.LEFT, TvFont.MINECRAFT));
            pieces.add(piece(TvPieceType.VALUE, innerX + labelW + 4, y, innerW - labelW - 4, 10, values.get(i),
                    formats[i], style(i == 0 ? TvColor.LOW : TvColor.HIGH, 9, TvAlign.RIGHT)));
        }
        int captionY = top + PAD + 4 * 11 + 2;
        pieces.add(text(innerX, captionY, innerW, 8, "Battery, last minute", TvColor.MUTED, 7, TvAlign.LEFT,
                TvFont.MINECRAFT));
        int graphY = captionY + 10;
        int graphH = bottom - PAD - graphY;
        pieces.add(shape(innerX, graphY, innerW, graphH, TvColor.BG, 2));
        pieces.add(piece(TvPieceType.GRAPH, innerX + 3, graphY + 3, innerW - 6, graphH - 6, values.get(1),
                TvFormat.DEFAULT, style(TvColor.HIGH, 8, TvAlign.LEFT).withPart(TvPartStyle.DEFAULT.withArea(true))));
        return TvLayout.of(pieces, NIGHT, new TvBackground(TvBackground.Kind.GRADIENT, 0xFF1A2046, 0xFF07081A), false,
                List.of(icon));
    }

    /** A arte numa Imagem da Tela inteira, na maior escala inteira que cabe, centrada. */
    static TvLayout art(TvPixelArt art, int width, int height) {
        TvArea area = TvArea.grown(width, height, TvArea.Grow.UP_RIGHT);
        int scale = Math.max(1, Math.min(area.pixelWidth() / art.width(), area.pixelHeight() / art.height()));
        TvPiece image = new TvPiece(TvPieceType.IMAGE, 0, 0, area.pixelWidth(), area.pixelHeight(), 0, "",
                Optional.empty(), TvFormat.DEFAULT, TvStyle.DEFAULT.withAlign(TvAlign.CENTER).withPart(
                TvPartStyle.DEFAULT.withImage(Optional.of(art.id()), scale, TvPartStyle.VAlign.MIDDLE)));
        return TvLayout.of(List.of(image), TvTheme.GRAPHITE, TvBackground.DEFAULT, false, List.of(art));
    }

    /** O GIF esticado na Tela inteira. */
    static TvLayout gif(TvPicture gif, int width, int height) {
        TvArea area = TvArea.grown(width, height, TvArea.Grow.UP_RIGHT);
        TvPiece image = new TvPiece(TvPieceType.IMAGE, 0, 0, area.pixelWidth(), area.pixelHeight(), 0, "",
                Optional.empty(), TvFormat.DEFAULT, TvStyle.DEFAULT.withAlign(TvAlign.CENTER).withPart(
                TvPartStyle.DEFAULT.withImage(Optional.of(gif.id()), 0, TvPartStyle.VAlign.MIDDLE)));
        return TvLayout.of(List.of(image), TvTheme.GRAPHITE, TvBackground.DEFAULT, false, List.of(), List.of(gif));
    }

    private static Optional<TvBinding> storage(GlobalPos pos, ResourceType type, TvStorageMetric metric) {
        return Optional.of(TvBinding.storage(type, pos, metric, Optional.empty()));
    }

    private static TvPiece shape(int x, int y, int w, int h, int role, int radius) {
        return piece(TvPieceType.SHAPE, x, y, w, h, Optional.empty(), TvFormat.DEFAULT,
                new TvStyle(TvColor.theme(TvColor.TEXT), TvColor.theme(role), 0, 0, 8, TvAlign.LEFT, true, "", radius,
                        100, TvFont.MINECRAFT));
    }

    private static TvPiece text(int x, int y, int w, int h, String text, int role, int size, TvAlign align,
            TvFont font) {
        return piece(TvPieceType.TEXT, x, y, w, h, Optional.empty(), TvFormat.DEFAULT,
                style(role, size, align).withText(text).withFont(font));
    }

    private static TvStyle style(int role, int size, TvAlign align) {
        return new TvStyle(TvColor.theme(role), 0, 0, 0, size, align, true, "");
    }

    private static TvPiece piece(TvPieceType type, int x, int y, int w, int h, Optional<TvBinding> binding,
            TvFormat format, TvStyle style) {
        return new TvPiece(type, x, y, w, h, 0, "", binding, format, style);
    }
}
