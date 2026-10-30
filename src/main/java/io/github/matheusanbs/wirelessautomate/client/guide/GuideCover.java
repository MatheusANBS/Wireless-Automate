package io.github.matheusanbs.wirelessautomate.client.guide;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.matheusanbs.wirelessautomate.client.guide.layout.GuideMetrics;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;

/**
 * A folha de rosto, à esquerda na abertura: o título, o subtítulo, o selo com o roteador desenhado grande, o
 * ornamento, o lugar da busca (só o desenho; a busca vem depois) e a dica da tecla G. Os textos são quebrados e
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

    private final List<FormattedCharSequence> title;
    private final int[] titleX;
    private final FormattedCharSequence subtitle;
    private final int subtitleX;
    private final FormattedCharSequence search;
    private final List<FormattedCharSequence> hint;
    private final ItemStack seal;

    GuideCover(Font font) {
        title = font.split(Component.translatable("gui.wirelessautomate.guide.cover.title"), W / TITLE_SCALE);
        titleX = new int[title.size()];
        for (int i = 0; i < title.size(); i++) {
            titleX[i] = (W - font.width(title.get(i)) * TITLE_SCALE) / 2 / TITLE_SCALE;
        }
        Component sub = Component.translatable("gui.wirelessautomate.guide.cover.subtitle");
        List<FormattedCharSequence> subLines = font.split(sub, W);
        subtitle = subLines.isEmpty() ? FormattedCharSequence.EMPTY : subLines.get(0);
        subtitleX = (W - font.width(subtitle)) / 2;
        List<FormattedCharSequence> searchLines = font.split(
                Component.translatable("gui.wirelessautomate.guide.cover.search"), W - 8);
        search = searchLines.isEmpty() ? FormattedCharSequence.EMPTY : searchLines.get(0);
        hint = font.split(Component.translatable("gui.wirelessautomate.guide.cover.hint"), W);
        seal = GuideInk.stack("wirelessautomate:router", "");
    }

    void draw(GuiGraphics g, Font font) {
        PoseStack pose = g.pose();
        pose.pushPose();
        pose.scale(TITLE_SCALE, TITLE_SCALE, 1);
        int y = 2;
        for (int i = 0; i < title.size(); i++) {
            g.drawString(font, title.get(i), titleX[i], y, GuideInk.TEXT, false);
            y += font.lineHeight;
        }
        pose.popPose();
        int below = 2 * TITLE_SCALE + title.size() * font.lineHeight * TITLE_SCALE + 4;
        g.drawString(font, subtitle, subtitleX, below, GuideInk.FAINT, false);
        drawRing(g, LEAF_RING, GuideInk.LEAF);
        drawRing(g, BRASS_RING, GuideInk.BRASS);
        GuidePainter.item(g, seal, SEAL_CX - 24, SEAL_CY - 24, 48);
        GuidePainter.ornament(g, 0, 176, W);
        g.drawString(font, search, 4, 192, GuideInk.FAINT, false);
        g.fill(0, 203, W, 205, GuideInk.LEAF);
        int hy = 212;
        for (int i = 0; i < hint.size() && hy + font.lineHeight <= GuideMetrics.SHEET_H; i++) {
            g.drawString(font, hint.get(i), 0, hy, GuideInk.FAINT, false);
            hy += font.lineHeight + 1;
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
