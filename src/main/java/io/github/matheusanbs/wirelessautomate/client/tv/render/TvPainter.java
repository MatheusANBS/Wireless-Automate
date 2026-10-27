package io.github.matheusanbs.wirelessautomate.client.tv.render;

import com.mojang.blaze3d.systems.RenderSystem;
import io.github.matheusanbs.wirelessautomate.client.tv.TvFit;
import io.github.matheusanbs.wirelessautomate.client.tv.TvNumbers;
import io.github.matheusanbs.wirelessautomate.client.tv.TvUnits;
import io.github.matheusanbs.wirelessautomate.tv.TvArea;
import io.github.matheusanbs.wirelessautomate.tv.TvBinding;
import io.github.matheusanbs.wirelessautomate.tv.TvClockMetric;
import io.github.matheusanbs.wirelessautomate.tv.TvColor;
import io.github.matheusanbs.wirelessautomate.tv.TvGraphHistory;
import io.github.matheusanbs.wirelessautomate.tv.TvFormat;
import io.github.matheusanbs.wirelessautomate.tv.TvLayout;
import io.github.matheusanbs.wirelessautomate.tv.TvPartStyle;
import io.github.matheusanbs.wirelessautomate.tv.TvPiece;
import io.github.matheusanbs.wirelessautomate.tv.TvStyle;
import io.github.matheusanbs.wirelessautomate.tv.TvTheme;
import io.github.matheusanbs.wirelessautomate.tv.TvUnit;
import net.minecraft.client.Minecraft;
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
    /** O resultado de {@link #paint}: a Tela precisa do redesenho do piscar, do segundo (Gráfico) ou do minuto (relógio). */
    public static final int FLAG_BLINK = 1;
    public static final int FLAG_GRAPH = 2;
    public static final int FLAG_CLOCK = 4;
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
     *                     existiam quando a prévia não tem o número
     * @param area         a área da Tela, para o fundo e as linhas
     * @param blinkOff     a fase apagada do piscar no instante do desenho
     * @param scale        pixels da textura por pixel de desenho (o mesmo do {@code pose})
     * @param targetHeight a altura da textura em pixels, para o recorte (o GL conta de baixo para cima)
     * @param preview      se é a miniatura do editor: os números vêm primeiro da prévia do rascunho
     * @return as {@code FLAG_*} do que o desenho tem e pede redesenho por tempo: alguma regra que bate manda piscar,
     *         um Gráfico, um número do relógio
     */
    public static int paint(GuiGraphics g, Font font, TvLayout layout, TvLayout values, TvArea area, long pos,
            int revision,
            char decimalSep, char groupSep, boolean blinkOff, float scale, int targetHeight, boolean preview) {
        TvPaintBackground.fill(g, layout, area.pixelWidth(), area.pixelHeight());
        TvPaintStyle resolved = STYLE;
        int flags = 0;
        long gameTime = Minecraft.getInstance().level == null ? 0 : Minecraft.getInstance().level.getGameTime();
        TvPaintNumbers.clockRead = false;
        for (TvPiece piece : layout.pieces()) {
            if (piece.hidden()) {
                continue;
            }
            resolved.resolve(piece, layout, values, pos, revision, blinkOff, preview);
            if (resolved.blinks) {
                flags |= FLAG_BLINK;
            }
            if (resolved.skip) {
                continue;
            }
            TvStyle style = piece.style();
            TvPaintBox.paint(g, piece, style, resolved.background, resolved.border);
            String text = switch (piece.type()) {
                case TEXT -> style.text();
                case VALUE -> valueText(piece, values, pos, revision, decimalSep, groupSep, preview);
                default -> "";
            };
            if (text.isEmpty()) {
                flags |= part(g, piece, resolved, layout, values, pos, revision, preview, gameTime);
            }
            if (!text.isEmpty() && resolved.color >>> 24 >= TvPaintMath.MIN_TEXT_ALPHA) {
                text(g, font, piece, style, resolved.color, text, scale, targetHeight);
            }
        }
        TvPaintBackground.scanlines(g, layout, area.pixelWidth(), area.pixelHeight());
        return TvPaintNumbers.clockRead ? flags | FLAG_CLOCK : flags;
    }

    /** Desenha o corpo de uma peça da etapa 3; devolve {@link #FLAG_GRAPH} se é um Gráfico, senão 0. */
    private static int part(GuiGraphics g, TvPiece piece, TvPaintStyle resolved, TvLayout layout, TvLayout values,
            long pos, int revision, boolean preview, long gameTime) {
        TvPartStyle part = piece.style().part();
        int opacity = piece.style().opacity();
        TvTheme theme = layout.theme();
        switch (piece.type()) {
            case ICON -> TvPaintIcon.paint(g, piece, part);
            case BAR -> TvPaintBar.paint(g, piece, part, piece.style().radius(),
                    TvPaintFill.fraction(piece, part, resolved.value, values, pos, revision, preview, gameTime),
                    resolved.color, TvPaintMath.withOpacity(TvColor.resolve(part.track(), theme), opacity));
            case GAUGE -> TvPaintGauge.paint(g, piece, part,
                    TvPaintFill.fraction(piece, part, resolved.value, values, pos, revision, preview, gameTime),
                    resolved.color, TvPaintMath.withOpacity(TvColor.resolve(part.track(), theme), opacity));
            case ARROW -> {
                int sign = TvPaintArrow.sign(resolved.value, part.deadZone(), part.reverse());
                int color = resolved.ruleColor ? resolved.color : TvPaintMath.withOpacity(TvColor.resolve(
                        sign > 0 ? part.upColor() : sign < 0 ? part.downColor() : part.flatColor(), theme), opacity);
                TvPaintArrow.paint(g, piece.x(), piece.y(), piece.width(), piece.height(), sign, color);
            }
            case GRAPH -> {
                if (piece.binding().isPresent()) {
                    TvGraphHistory history = TvHistories.wanted(pos, preview, piece.binding().get(), values, revision,
                            gameTime);
                    TvPaintGraph.paint(g, piece, part, history, resolved.color);
                }
                return FLAG_GRAPH;
            }
            default -> {
            }
        }
        return 0;
    }

    /**
     * Prefixo, número, unidade e sufixo. A unidade só numa taxa (métrica de taxa e unidade de tempo) e só sem
     * sufixo; uma fração (o enchimento) vira porcentagem, com o "%" no lugar da unidade; sem número ({@code NaN}), o traço sem unidade.
     */
    static String valueText(TvPiece piece, TvLayout values, long pos, int revision, char decimalSep, char groupSep,
            boolean preview) {
        TvFormat format = piece.format();
        TvBinding binding = piece.binding().orElse(null);
        double value = binding == null ? Double.NaN
                : TvPaintNumbers.of(binding, values, pos, revision, preview);
        StringBuilder out = new StringBuilder(format.prefix());
        if (Double.isNaN(value)) {
            return out.append(NO_DATA).append(format.suffix()).toString();
        }
        if (binding.clock() == TvClockMetric.TIME) {
            int minute = (int) value;
            out.append(minute / 60 < 10 ? "0" : "").append(minute / 60).append(':').append(minute % 60 < 10 ? "0" : "")
                    .append(minute % 60);
            return out.append(format.suffix()).toString();
        }
        TvUnit unit = format.unit();
        boolean rate = binding.rate() && unit != TvUnit.NONE;
        boolean percent = binding.fraction();
        double shown = percent ? value * 100 : rate ? value * unit.perSecond() : value;
        out.append(TvNumbers.format(shown, format.decimals(), format.abbreviate(), format.sign(), decimalSep, groupSep));
        if (percent && format.suffix().isEmpty()) {
            out.append('%');
        } else if (rate && format.suffix().isEmpty()) {
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
