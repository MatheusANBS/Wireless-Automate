package io.github.matheusanbs.wirelessautomate.client.tv.render;

import com.mojang.blaze3d.systems.RenderSystem;
import io.github.matheusanbs.wirelessautomate.client.tv.TvClientValues;
import io.github.matheusanbs.wirelessautomate.client.tv.TvFit;
import io.github.matheusanbs.wirelessautomate.client.tv.TvNumbers;
import io.github.matheusanbs.wirelessautomate.client.tv.TvUnits;
import io.github.matheusanbs.wirelessautomate.tv.TvArea;
import io.github.matheusanbs.wirelessautomate.tv.TvBinding;
import io.github.matheusanbs.wirelessautomate.tv.TvFormat;
import io.github.matheusanbs.wirelessautomate.tv.TvLayout;
import io.github.matheusanbs.wirelessautomate.tv.TvPiece;
import io.github.matheusanbs.wirelessautomate.tv.TvPieceType;
import io.github.matheusanbs.wirelessautomate.tv.TvStyle;
import io.github.matheusanbs.wirelessautomate.tv.TvUnit;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * O desenho do layout de uma Tela na textura dela, só no redesenho (nunca por quadro). O fundo da Tela e as linhas
 * de varredura ({@link TvPaintBackground}) vão em volta das peças, que vão em ordem, em pixels de desenho: o
 * chamador já escalou o {@code pose} para os pixels da textura. Fundo e borda ({@link TvPaintBox}, com os cantos)
 * valem para qualquer peça; Texto e Valor escrevem o texto com o tamanho do {@link TvFit}, centrado na altura da caixa e
 * alinhado pela {@code TvAlign}. O {@code TvFit} nunca desce de 1 px: numa caixa menor que isso o texto passa um
 * pouco dela, sem recorte (o recorte só existe com o ajuste desligado, e então pela caixa).
 */
public final class TvPainter {
    private static final float LINE = 9f;
    /** A altura das letras sem a folga de baixo da linha, para centrar na caixa. */
    private static final float GLYPH = 8f;
    private static final String NO_DATA = "—";
    /** O estado da peça da vez, reusado (só a thread de render desenha). */
    private static final TvPaintStyle STYLE = new TvPaintStyle();

    private TvPainter() {
    }

    /**
     * Desenha o fundo, as peças em ordem e as linhas de varredura. Cada peça resolve a regra que bate, as cores pelo
     * tema e a opacidade ({@link TvPaintStyle}); a escondida, e a que pisca na fase apagada, não é desenhada.
     *
     * @param values       o layout cujas ligações indexam os números que chegaram do servidor: na parede, o próprio
     *                     {@code layout}; no editor, o salvo, para o rascunho mostrar os números das ligações que já
     *                     existiam e "—" nas novas
     * @param area         a área da Tela, para o fundo e as linhas
     * @param blinkOff     a fase apagada do piscar no instante do desenho
     * @param scale        pixels da textura por pixel de desenho (o mesmo do {@code pose})
     * @param targetHeight a altura da textura em pixels, para o recorte (o GL conta de baixo para cima)
     * @return se alguma regra que bate manda piscar (a Tela precisa do redesenho do piscar)
     */
    public static boolean paint(GuiGraphics g, Font font, TvLayout layout, TvLayout values, TvArea area, long pos,
            int revision,
            char decimalSep, char groupSep, boolean blinkOff, float scale, int targetHeight) {
        TvPaintBackground.fill(g, layout, area.pixelWidth(), area.pixelHeight());
        TvPaintStyle resolved = STYLE;
        boolean blinks = false;
        for (TvPiece piece : layout.pieces()) {
            if (piece.hidden()) {
                continue;
            }
            resolved.resolve(piece, layout, values, pos, revision, blinkOff);
            blinks |= resolved.blinks;
            if (resolved.skip) {
                continue;
            }
            TvStyle style = piece.style();
            TvPaintBox.paint(g, piece, style, resolved.background, resolved.border);
            String text = switch (piece.type()) {
                case TEXT -> style.text();
                case VALUE -> valueText(piece, values, pos, revision, decimalSep, groupSep);
                case SHAPE -> "";
            };
            if (!text.isEmpty() && resolved.color >>> 24 >= TvPaintMath.MIN_TEXT_ALPHA) {
                text(g, font, piece, style, resolved.color, text, scale, targetHeight);
            }
        }
        TvPaintBackground.scanlines(g, layout, area.pixelWidth(), area.pixelHeight());
        return blinks;
    }

    /**
     * Prefixo, número, unidade e sufixo. A unidade só numa taxa (métrica de taxa e unidade de tempo) e só sem
     * sufixo; sem número ({@code NaN}), o traço sem unidade.
     */
    static String valueText(TvPiece piece, TvLayout values, long pos, int revision, char decimalSep, char groupSep) {
        TvFormat format = piece.format();
        TvBinding binding = piece.binding().orElse(null);
        double value = binding == null ? Double.NaN
                : TvClientValues.value(pos, revision, values.bindingIndex(binding));
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

    private static void text(GuiGraphics g, Font font, TvPiece piece, TvStyle style, int color, String text,
            float scale, int targetHeight) {
        Component line = TvFonts.text(style.font(), text);
        int width = font.width(line);
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
        g.drawString(font, line, 0, 0, color, false);
        g.pose().popPose();
        if (clip) {
            RenderSystem.disableScissor();
        }
    }
}
