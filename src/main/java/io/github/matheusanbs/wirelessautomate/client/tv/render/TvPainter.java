package io.github.matheusanbs.wirelessautomate.client.tv.render;

import com.mojang.blaze3d.systems.RenderSystem;
import io.github.matheusanbs.wirelessautomate.client.tv.TvClientValues;
import io.github.matheusanbs.wirelessautomate.client.tv.TvFit;
import io.github.matheusanbs.wirelessautomate.client.tv.TvNumbers;
import io.github.matheusanbs.wirelessautomate.client.tv.TvUnits;
import io.github.matheusanbs.wirelessautomate.tv.TvBinding;
import io.github.matheusanbs.wirelessautomate.tv.TvFormat;
import io.github.matheusanbs.wirelessautomate.tv.TvLayout;
import io.github.matheusanbs.wirelessautomate.tv.TvPiece;
import io.github.matheusanbs.wirelessautomate.tv.TvPieceType;
import io.github.matheusanbs.wirelessautomate.tv.TvStyle;
import io.github.matheusanbs.wirelessautomate.tv.TvUnit;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/**
 * O desenho do layout de uma Tela na textura dela, só no redesenho (nunca por quadro). As peças vão em ordem, em
 * pixels de desenho: o chamador já escalou o {@code pose} para os pixels da textura. Fundo e borda valem para
 * qualquer peça; Texto e Valor escrevem o texto com o tamanho do {@link TvFit}, centrado na altura da caixa e
 * alinhado pela {@code TvAlign}. O {@code TvFit} nunca desce de 1 px: numa caixa menor que isso o texto passa um
 * pouco dela, sem recorte (o recorte só existe com o ajuste desligado, e então pela caixa).
 */
public final class TvPainter {
    private static final float LINE = 9f;
    /** A altura das letras sem a folga de baixo da linha, para centrar na caixa. */
    private static final float GLYPH = 8f;
    private static final String NO_DATA = "—";

    private TvPainter() {
    }

    /**
     * @param scale        pixels da textura por pixel de desenho (o mesmo do {@code pose})
     * @param targetHeight a altura da textura em pixels, para o recorte (o GL conta de baixo para cima)
     */
    public static void paint(GuiGraphics g, Font font, TvLayout layout, long pos, int revision, char decimalSep,
            char groupSep, float scale, int targetHeight) {
        for (TvPiece piece : layout.pieces()) {
            TvStyle style = piece.style();
            box(g, piece, style);
            String text = switch (piece.type()) {
                case TEXT -> style.text();
                case VALUE -> valueText(piece, layout, pos, revision, decimalSep, groupSep);
                case SHAPE -> "";
            };
            if (!text.isEmpty()) {
                text(g, font, piece, style, text, scale, targetHeight);
            }
        }
    }

    private static void box(GuiGraphics g, TvPiece piece, TvStyle style) {
        int x0 = piece.x();
        int y0 = piece.y();
        int x1 = x0 + piece.width();
        int y1 = y0 + piece.height();
        if (style.background() >>> 24 != 0) {
            g.fill(x0, y0, x1, y1, style.background());
        }
        int b = Math.min(style.borderWidth(), Math.min(piece.width(), piece.height()) / 2);
        if (b > 0 && style.border() >>> 24 != 0) {
            int color = style.border();
            g.fill(x0, y0, x1, y0 + b, color);
            g.fill(x0, y1 - b, x1, y1, color);
            g.fill(x0, y0 + b, x0 + b, y1 - b, color);
            g.fill(x1 - b, y0 + b, x1, y1 - b, color);
        }
    }

    /**
     * Prefixo, número, unidade e sufixo. A unidade só numa taxa (métrica de taxa e unidade de tempo) e só sem
     * sufixo; sem número ({@code NaN}), o traço sem unidade.
     */
    static String valueText(TvPiece piece, TvLayout layout, long pos, int revision, char decimalSep, char groupSep) {
        TvFormat format = piece.format();
        TvBinding binding = piece.binding().orElse(null);
        double value = binding == null ? Double.NaN
                : TvClientValues.value(pos, revision, layout.bindingIndex(binding));
        StringBuilder out = new StringBuilder(format.prefix());
        if (Double.isNaN(value)) {
            return out.append(NO_DATA).append(format.suffix()).toString();
        }
        TvUnit unit = format.unit();
        boolean rate = binding.metric().rate() && unit != TvUnit.NONE;
        double shown = rate ? value * unit.perSecond() : value;
        out.append(TvNumbers.format(shown, format.decimals(), format.abbreviate(), format.sign(), decimalSep, groupSep));
        if (rate && format.suffix().isEmpty()) {
            String label = TvUnits.label(binding.type(), unit);
            if (!label.isEmpty() && label.charAt(0) != '/') {
                out.append(' ');
            }
            out.append(label);
        }
        return out.append(format.suffix()).toString();
    }

    private static void text(GuiGraphics g, Font font, TvPiece piece, TvStyle style, String text, float scale,
            int targetHeight) {
        int width = font.width(text);
        float size = TvFit.size(style.size(), piece.width(), piece.height(), width / LINE, style.fit());
        float k = size / LINE;
        float shown = width * k;
        float x = switch (style.align()) {
            case LEFT -> piece.x();
            case CENTER -> piece.x() + (piece.width() - shown) / 2f;
            case RIGHT -> piece.x() + piece.width() - shown;
        };
        float y = piece.y() + (piece.height() - GLYPH * k) / 2f;
        boolean clip = !style.fit();
        if (clip) {
            int sx = Math.round(piece.x() * scale);
            int sy = Math.round(targetHeight - (piece.y() + piece.height()) * scale);
            RenderSystem.enableScissor(sx, sy, Math.round(piece.width() * scale), Math.round(piece.height() * scale));
        }
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(k, k, 1);
        g.drawString(font, text, 0, 0, style.color(), false);
        g.pose().popPose();
        if (clip) {
            RenderSystem.disableScissor();
        }
    }
}
