package io.github.matheusanbs.wirelessautomate.client;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import org.jetbrains.annotations.Nullable;

/**
 * Texto variável que nunca passa do espaço: abrevia com reticências (ou quebra linha) e guarda o
 * texto inteiro para o tooltip. A tela chama {@link #beginFrame()} no começo do {@code render} e
 * mostra {@link #clipAt} no fim, se nenhum outro tooltip apareceu.
 */
public final class GuiText {
    /** Um texto cortado: a área dele e o texto inteiro. */
    public record Clip(int x, int y, int width, int height, Component full) {
        boolean contains(double mx, double my) {
            return mx >= x && mx < x + width && my >= y && my < y + height;
        }
    }

    private static final List<Clip> CLIPS = new ArrayList<>();

    private GuiText() {
    }

    public static void beginFrame() {
        CLIPS.clear();
    }

    /** Uma linha em ({@code x}, {@code y}), cortada em {@code width}. */
    public static void draw(GuiGraphics g, Font font, Component text, int x, int y, int width, int color) {
        if (width <= 0) {
            return;
        }
        if (font.width(text) <= width) {
            GuiPaint.text(g, font, text, x, y, color);
            return;
        }
        GuiPaint.text(g, font, GuiPaint.ellipsize(font, text, width), x, y, color);
        CLIPS.add(new Clip(x, y - 1, width, font.lineHeight + 1, text));
    }

    /** Até {@code maxLines} linhas de 10 px; a última é abreviada se sobrar texto. Devolve a altura usada. */
    public static int wrap(GuiGraphics g, Font font, Component text, int x, int y, int width, int maxLines, int color) {
        if (width <= 0 || maxLines <= 0) {
            return 0;
        }
        List<FormattedCharSequence> lines = font.split(text, width);
        int shown = Math.min(lines.size(), maxLines);
        for (int i = 0; i < shown; i++) {
            if (i == shown - 1 && lines.size() > maxLines) {
                break;
            }
            GuiPaint.text(g, font, lines.get(i), x, y + i * 10, color);
        }
        if (lines.size() > maxLines) {
            int last = maxLines - 1;
            Component rest = Component.literal(remainder(font, text, width, last));
            GuiPaint.text(g, font, GuiPaint.ellipsize(font, rest, width), x, y + last * 10, color);
            CLIPS.add(new Clip(x, y - 1, width, maxLines * 10 + 1, text));
        }
        return shown * 10;
    }

    /** Como {@link #wrap}, com cada linha centrada em {@code centerX}. Devolve a altura usada. */
    public static int wrapCentered(GuiGraphics g, Font font, Component text, int centerX, int y, int width, int maxLines,
            int color) {
        if (width <= 0 || maxLines <= 0) {
            return 0;
        }
        List<FormattedCharSequence> lines = font.split(text, width);
        int shown = Math.min(lines.size(), maxLines);
        boolean cut = lines.size() > maxLines;
        for (int i = 0; i < shown; i++) {
            FormattedCharSequence line = cut && i == shown - 1
                    ? GuiPaint.ellipsize(font, Component.literal(remainder(font, text, width, i)), width)
                    : lines.get(i);
            GuiPaint.text(g, font, line, centerX - font.width(line) / 2, y + i * 10, color);
        }
        if (cut) {
            CLIPS.add(new Clip(centerX - width / 2, y - 1, width, maxLines * 10 + 1, text));
        }
        return shown * 10;
    }

    /** Quantas linhas o texto ocupa na quebra em {@code width}, no máximo {@code maxLines}. */
    public static int lineCount(Font font, Component text, int width, int maxLines) {
        return width <= 0 ? 0 : Math.min(font.split(text, width).size(), maxLines);
    }

    /** O texto a partir da linha {@code line} da quebra (para abreviar a última linha visível). */
    private static String remainder(Font font, Component text, int width, int line) {
        String plain = text.getString();
        List<FormattedText> parts = font.getSplitter().splitLines(plain, width, Style.EMPTY);
        StringBuilder rest = new StringBuilder();
        for (int i = line; i < parts.size(); i++) {
            if (!rest.isEmpty()) {
                rest.append(' ');
            }
            rest.append(parts.get(i).getString().strip());
        }
        return rest.toString();
    }

    public static @Nullable Component clipAt(double mx, double my) {
        for (int i = CLIPS.size() - 1; i >= 0; i--) {
            if (CLIPS.get(i).contains(mx, my)) {
                return CLIPS.get(i).full();
            }
        }
        return null;
    }

    /** Textos cortados no último frame (o e2e confere que cada um tem tooltip). */
    public static int clipCount() {
        return CLIPS.size();
    }

    /** O centro do primeiro texto cortado, ou {@code null}. */
    public static int @Nullable [] firstClipCenter() {
        if (CLIPS.isEmpty()) {
            return null;
        }
        Clip c = CLIPS.get(0);
        return new int[] {c.x() + c.width() / 2, c.y() + c.height() / 2};
    }

    /** Os centros de todos os textos cortados no último frame (gancho do e2e). */
    public static List<int[]> clipCenters() {
        List<int[]> centers = new ArrayList<>();
        for (Clip c : CLIPS) {
            centers.add(new int[] {c.x() + c.width() / 2, c.y() + c.height() / 2});
        }
        return centers;
    }
}
