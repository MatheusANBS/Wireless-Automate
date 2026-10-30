package io.github.matheusanbs.wirelessautomate.client.guide;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * O que fica em volta das folhas, em pixels da textura: o livro, as fitas dos grupos (a do grupo à vista mais
 * comprida), o rodapé (◀, o número de cada folha, ▶) e o "← Índice". O que muda com a navegação é montado em
 * {@link #update}; o quadro só desenha. Também diz o que está sob o mouse.
 */
final class GuideChrome {
    static final ResourceLocation BOOK = WirelessAutomate.id("textures/gui/guide/book.png");
    /** As fitas: a primeira em y = 15, uma a cada 21 px, 18 de altura; a curta tem 17 de largura, a comprida 22. */
    static final int RIBBON_Y = 15;
    static final int RIBBON_STEP = 21;
    static final int RIBBON_H = 18;
    /** O rodapé e as setas, nas pontas de fora das folhas. */
    static final int FOOT_Y = 166;
    static final int PREV_X = BookScale.LEFT_X;
    static final int NEXT_X = BookScale.RIGHT_X + 116 - 4;
    /** O "← Índice" no alto da folha esquerda, à direita da antena do canto. */
    static final int BACK_X = 44;
    static final int BACK_Y = 11;

    /** O que o mouse pode apertar fora das folhas. */
    enum Hit {
        PREV, NEXT, BACK, RIBBON
    }

    private final ItemStack[] ribbonIcon;
    private final Component[] ribbonName;
    private final FormattedCharSequence backText;
    private final int backW;
    private int activeGroup = -1;
    private boolean prev;
    private boolean next;
    private boolean back;
    private FormattedCharSequence leftNum = FormattedCharSequence.EMPTY;
    private FormattedCharSequence rightNum = FormattedCharSequence.EMPTY;
    private int leftNumX;
    private int rightNumX;
    /** A fita sob o mouse no último {@link #hit}. */
    private int hitGroup = -1;

    GuideChrome(Font font, GuideIndex index) {
        int n = GuideIndex.GROUPS.size();
        ribbonIcon = new ItemStack[n];
        ribbonName = index.groupName;
        for (int g = 0; g < n; g++) {
            ribbonIcon[g] = index.groupSheet[g] < 0 ? ItemStack.EMPTY : GuideInk.stack(index.groupIcon[g], "");
        }
        Component b = Component.translatable("gui.wirelessautomate.guide.back_index");
        backText = b.getVisualOrderText();
        backW = font.width(b);
    }

    /** Monta o que depende do lugar: as setas, os números (já medidos), o "← Índice" e a fita comprida. */
    void update(Font font, boolean hasPrev, boolean hasNext, boolean showBack, String left, String right, int group) {
        prev = hasPrev;
        next = hasNext;
        back = showBack;
        activeGroup = group;
        leftNum = FormattedCharSequence.forward(left, Style.EMPTY);
        rightNum = FormattedCharSequence.forward(right, Style.EMPTY);
        // em unidades (2 por pixel): o centro de cada folha
        leftNumX = (BookScale.LEFT_X + 58) * 2 - font.width(left) / 2;
        rightNumX = (BookScale.RIGHT_X + 58) * 2 - font.width(right) / 2;
    }

    /** O livro e as fitas, com a pose em pixels da textura a partir do canto do livro. */
    void drawBook(GuiGraphics g) {
        g.blit(BOOK, 0, 0, 0, 0, BookScale.TEX_W, BookScale.TEX_H, BookScale.TEX_W, BookScale.TEX_H);
        for (int i = 0; i < ribbonIcon.length; i++) {
            if (ribbonIcon[i].isEmpty()) {
                continue;
            }
            boolean on = i == activeGroup;
            int w = on ? 22 : 17;
            int y = RIBBON_Y + i * RIBBON_STEP;
            g.blit(GuideIndex.ribbon(i, on), BookScale.RIBBON_X, y, 0, 0, w, RIBBON_H, w, RIBBON_H);
        }
    }

    /** Os ícones das fitas, as setas, os números e o "← Índice" (o texto em meia escala, como nas folhas). */
    void drawFront(GuiGraphics g, Font font) {
        PoseStack pose = g.pose();
        for (int i = 0; i < ribbonIcon.length; i++) {
            if (!ribbonIcon[i].isEmpty()) {
                int x = i == activeGroup ? 301 : 298;
                pose.pushPose();
                pose.translate(x, RIBBON_Y + i * RIBBON_STEP + 5, 0);
                pose.scale(0.5f, 0.5f, 0.5f);
                g.renderItem(ribbonIcon[i], 0, 0);
                pose.popPose();
            }
        }
        arrow(g, PREV_X, true, prev ? GuideInk.TITLE : GuideInk.LEAF);
        arrow(g, NEXT_X, false, next ? GuideInk.TITLE : GuideInk.LEAF);
        pose.pushPose();
        pose.scale(0.5f, 0.5f, 1);
        g.drawString(font, leftNum, leftNumX, FOOT_Y * 2 + 3, GuideInk.FAINT, false);
        g.drawString(font, rightNum, rightNumX, FOOT_Y * 2 + 3, GuideInk.FAINT, false);
        if (back) {
            g.drawString(font, backText, BACK_X * 2, BACK_Y * 2, GuideInk.TITLE, false);
        }
        pose.popPose();
    }

    /** A seta do rodapé: um triângulo de 4 × 7 px apontando para fora. */
    private static void arrow(GuiGraphics g, int x, boolean left, int color) {
        for (int i = 0; i < 4; i++) {
            int col = left ? x + i : x + 3 - i;
            g.fill(col, FOOT_Y + 4 - i, col + 1, FOOT_Y + 5 + i, color);
        }
    }

    /** O que está sob o ponto ({@code tx}, {@code ty}) em pixels da textura, ou {@code null}. */
    @Nullable Hit hit(double tx, double ty) {
        hitGroup = -1;
        if (prev && GuidePainter.inside(tx, ty, PREV_X - 2, FOOT_Y - 1, 9, 11)) {
            return Hit.PREV;
        }
        if (next && GuidePainter.inside(tx, ty, NEXT_X - 3, FOOT_Y - 1, 9, 11)) {
            return Hit.NEXT;
        }
        if (back && GuidePainter.inside(tx, ty, BACK_X - 1, BACK_Y - 1, backW / 2 + 2, 7)) {
            return Hit.BACK;
        }
        for (int i = 0; i < ribbonIcon.length; i++) {
            int w = i == activeGroup ? 22 : 17;
            if (!ribbonIcon[i].isEmpty() && GuidePainter.inside(tx, ty, BookScale.TEX_W - 3, RIBBON_Y + i * RIBBON_STEP,
                    BookScale.RIBBON_X + w - (BookScale.TEX_W - 3), RIBBON_H)) {
                hitGroup = i;
                return Hit.RIBBON;
            }
        }
        return null;
    }

    /** A fita do último {@link #hit} que deu {@link Hit#RIBBON}. */
    int hitGroup() {
        return hitGroup;
    }

    Component groupName(int group) {
        return ribbonName[group];
    }
}
