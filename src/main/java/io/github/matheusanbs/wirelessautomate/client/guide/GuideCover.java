package io.github.matheusanbs.wirelessautomate.client.guide;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.matheusanbs.wirelessautomate.client.guide.layout.GuideMetrics;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.Util;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;

/**
 * A folha de rosto, à esquerda na abertura: o título, o subtítulo, o selo com o roteador desenhado grande, o
 * ornamento, o campo da busca (o texto vem da {@link GuideSearchBox}) e a dica da tecla G. Os textos são quebrados e
 * medidos na criação; o quadro só desenha. Em unidades de folha (232 × 280).
 */
final class GuideCover {
    private static final int W = GuideMetrics.SHEET_W;
    private static final int TITLE_SCALE = 3;
    private static final int SEAL_CX = W / 2;
    private static final int SEAL_CY = 122;
    /** O anel do selo em pixels da textura (cada um vale 2 unidades), o de latão e o de porcelana por fora. */
    private static final int[] BRASS_RING = ringSpans(18);
    private static final int[] LEAF_RING = ringSpans(21);

    /** O topo do anel de porcelana do selo, a linha da busca e o começo da dica, em unidades. */
    private static final int SEAL_TOP = SEAL_CY - 42;
    private static final int SEARCH_Y = 192;
    private static final int HINT_Y = 212;
    private static final int HINT_STEP = 10;
    /** A linha da busca com foco (coral.2). */
    private static final int FOCUS = 0xFFF0603E;

    private final List<FormattedCharSequence> title;
    private final int[] titleX;
    private final List<FormattedCharSequence> subtitle;
    private final int[] subtitleX;
    private final int subtitleY;
    private final FormattedCharSequence search;
    private final List<FormattedCharSequence> hint;
    private final ItemStack seal;
    /** O que não coube na folha de rosto (o e2e confere que fica vazio). */
    private final List<String> problems = new ArrayList<>();

    GuideCover(Font font) {
        title = font.split(Component.translatable("gui.wirelessautomate.guide.cover.title"), W / TITLE_SCALE);
        titleX = centered(font, title, W / TITLE_SCALE);
        subtitleY = 2 * TITLE_SCALE + title.size() * font.lineHeight * TITLE_SCALE + 4;
        subtitle = font.split(Component.translatable("gui.wirelessautomate.guide.cover.subtitle"), W);
        subtitleX = centered(font, subtitle, W);
        if (subtitleY + subtitle.size() * HINT_STEP > SEAL_TOP) {
            problems.add("o título e o subtítulo da folha de rosto chegam no selo (até " + (subtitleY
                    + subtitle.size() * HINT_STEP) + ", o selo começa em " + SEAL_TOP + ")");
        }
        Component searchText = Component.translatable("gui.wirelessautomate.guide.cover.search");
        List<FormattedCharSequence> searchLines = font.split(searchText, W - 8);
        search = searchLines.isEmpty() ? FormattedCharSequence.EMPTY : searchLines.get(0);
        if (searchLines.size() > 1) {
            problems.add("o texto da busca \"" + searchText.getString() + "\" não cabe numa linha");
        }
        Component hintText = Component.translatable("gui.wirelessautomate.guide.cover.hint");
        hint = font.split(hintText, W);
        if (HINT_Y + hint.size() * HINT_STEP > GuideMetrics.SHEET_H) {
            problems.add("a dica \"" + hintText.getString() + "\" passa do pé da folha de rosto (" + hint.size()
                    + " linhas)");
        }
        seal = GuideInk.stack("wirelessautomate:router", "");
    }

    private static int[] centered(Font font, List<FormattedCharSequence> lines, int width) {
        int[] x = new int[lines.size()];
        for (int i = 0; i < lines.size(); i++) {
            x[i] = (width - font.width(lines.get(i))) / 2;
        }
        return x;
    }

    List<String> problems() {
        return problems;
    }

    /** O ponto ({@code ux}, {@code uy}), em unidades de folha, está no campo da busca? */
    static boolean onField(double ux, double uy) {
        return ux >= 0 && ux < W && uy >= SEARCH_Y - 4 && uy < SEARCH_Y + 14;
    }

    void draw(GuiGraphics g, Font font, GuideSearchBox box) {
        PoseStack pose = g.pose();
        pose.pushPose();
        pose.scale(TITLE_SCALE, TITLE_SCALE, 1);
        int y = 2;
        for (int i = 0; i < title.size(); i++) {
            g.drawString(font, title.get(i), titleX[i], y, GuideInk.TEXT, false);
            y += font.lineHeight;
        }
        pose.popPose();
        for (int i = 0; i < subtitle.size(); i++) {
            g.drawString(font, subtitle.get(i), subtitleX[i], subtitleY + i * HINT_STEP, GuideInk.FAINT, false);
        }
        drawRing(g, LEAF_RING, GuideInk.LEAF);
        drawRing(g, BRASS_RING, GuideInk.BRASS);
        GuidePainter.item(g, seal, SEAL_CX - 24, SEAL_CY - 24, 48);
        GuidePainter.ornament(g, 0, 176, W);
        if (box.isEmpty()) {
            g.drawString(font, search, 4, SEARCH_Y, GuideInk.FAINT, false);
        } else {
            g.drawString(font, box.queryText(), 4, SEARCH_Y, GuideInk.TEXT, false);
        }
        if (box.focused() && Util.getMillis() / 500 % 2 == 0) {
            g.fill(5 + box.queryWidth(), SEARCH_Y - 1, 7 + box.queryWidth(), SEARCH_Y + 9, GuideInk.TEXT);
        }
        g.fill(0, SEARCH_Y + 11, W, SEARCH_Y + 13, box.focused() ? FOCUS : GuideInk.LEAF);
        if (box.isEmpty() || box.total() == 0) {
            for (int i = 0; i < hint.size(); i++) {
                g.drawString(font, hint.get(i), 0, HINT_Y + i * HINT_STEP, GuideInk.FAINT, false);
            }
        } else {
            g.drawString(font, box.count(), 0, HINT_Y, GuideInk.FAINT, false);
        }
    }

    /**
     * O anel desenhado em linhas: para cada linha de pixels da textura, de cima para baixo, o começo e o fim de um
     * pedaço (até dois por linha), em pixels da textura a partir do centro.
     */
    private static int[] ringSpans(int r) {
        int[] spans = new int[(2 * r + 1) * 4];
        for (int dy = -r; dy <= r; dy++) {
            int outer = (int) Math.floor(Math.sqrt((r + 0.5) * (r + 0.5) - dy * dy));
            double in = (r - 0.5) * (r - 0.5) - dy * dy;
            int inner = in < 0 ? -1 : (int) Math.ceil(Math.sqrt(in));
            int at = (dy + r) * 4;
            spans[at] = -outer;
            spans[at + 1] = inner < 0 ? outer + 1 : -inner + 1;
            spans[at + 2] = inner < 0 ? 0 : inner;
            spans[at + 3] = inner < 0 ? 0 : outer + 1;
        }
        return spans;
    }

    private static void drawRing(GuiGraphics g, int[] spans, int color) {
        int r = spans.length / 8;
        for (int row = 0; row < spans.length / 4; row++) {
            int y = SEAL_CY + (row - r) * 2;
            int at = row * 4;
            g.fill(SEAL_CX + spans[at] * 2, y, SEAL_CX + spans[at + 1] * 2, y + 2, color);
            if (spans[at + 3] > spans[at + 2]) {
                g.fill(SEAL_CX + spans[at + 2] * 2, y, SEAL_CX + spans[at + 3] * 2, y + 2, color);
            }
        }
    }
}
