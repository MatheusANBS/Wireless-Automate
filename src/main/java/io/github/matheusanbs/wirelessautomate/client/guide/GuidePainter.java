package io.github.matheusanbs.wirelessautomate.client.guide;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.matheusanbs.wirelessautomate.client.guide.GuideInk.Ink;
import io.github.matheusanbs.wirelessautomate.client.guide.layout.GuideMetrics;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Desenha a tinta de uma folha ({@link GuideInk}) em unidades de folha, com a {@code PoseStack} já no canto da área de
 * conteúdo e na escala da folha; acha o link ou o item sob o mouse. Por quadro só percorre a lista: nada de medida,
 * quebra ou alocação além do que o próprio desenho de item do jogo faz.
 */
final class GuidePainter {
    private GuidePainter() {
    }

    static void draw(GuiGraphics g, Font font, List<Ink> inks) {
        for (int i = 0; i < inks.size(); i++) {
            switch (inks.get(i)) {
                case GuideInk.Txt t -> text(g, font, t);
                case GuideInk.Item it -> item(g, it.stack(), it.x(), it.y(), it.size());
                case GuideInk.Img im -> g.blit(im.texture(), im.x(), im.y(), im.w(), im.h(), 0, 0, im.texW(), im.texH(),
                        im.texW(), im.texH());
                case GuideInk.Fill f -> g.fill(f.x(), f.y(), f.x() + f.w(), f.y() + f.h(), f.color());
                case GuideInk.Ornament o -> ornament(g, o.x(), o.y() + (o.h() - 5) / 2, o.w());
                case GuideInk.Frame f -> frame(g, font, f);
                case GuideInk.Link l -> {
                }
            }
        }
    }

    private static void text(GuiGraphics g, Font font, GuideInk.Txt t) {
        if (t.codeW() > 0) {
            g.fill(t.x() - 1, t.y() - 1, t.x() + t.codeW() + 1, t.y() + font.lineHeight - 1, GuideInk.LEAF);
        }
        if (t.scale() == 1f) {
            g.drawString(font, t.text(), t.x(), t.y(), t.color(), false);
            return;
        }
        PoseStack pose = g.pose();
        pose.pushPose();
        pose.translate(t.x(), t.y(), 0);
        pose.scale(t.scale(), t.scale(), 1);
        g.drawString(font, t.text(), 0, 0, t.color(), false);
        pose.popPose();
    }

    /** Um item em {@code size} unidades (16 é o tamanho normal). */
    static void item(GuiGraphics g, ItemStack stack, int x, int y, int size) {
        if (stack.isEmpty()) {
            return;
        }
        if (size == GuideMetrics.ICON) {
            g.renderItem(stack, x, y);
            return;
        }
        PoseStack pose = g.pose();
        pose.pushPose();
        pose.translate(x, y, 0);
        float s = size / (float) GuideMetrics.ICON;
        pose.scale(s, s, s);
        g.renderItem(stack, 0, 0);
        pose.popPose();
    }

    /** O ornamento: duas linhas de porcelana e três losangos de latão no meio, com 5 unidades de altura. */
    static void ornament(GuiGraphics g, int x, int y, int w) {
        int mid = x + w / 2;
        int half = 14;
        g.fill(x, y + 2, mid - half, y + 3, GuideInk.LEAF);
        g.fill(mid + half, y + 2, x + w, y + 3, GuideInk.LEAF);
        for (int d = -8; d <= 8; d += 8) {
            int cx = mid + d;
            g.fill(cx, y, cx + 1, y + 5, GuideInk.BRASS);
            g.fill(cx - 1, y + 1, cx + 2, y + 4, GuideInk.BRASS);
            g.fill(cx - 2, y + 2, cx + 3, y + 3, GuideInk.BRASS);
        }
    }

    /** A moldura: fio de porcelana por fora, latão por dentro, fundo claro, o ícone e o rótulo centrados. */
    private static void frame(GuiGraphics g, Font font, GuideInk.Frame f) {
        int x0 = f.x();
        int y0 = f.y();
        int x1 = x0 + f.w();
        int y1 = y0 + f.h();
        ring(g, x0, y0, x1, y1, 1, GuideInk.LEAF);
        ring(g, x0 + 2, y0 + 2, x1 - 2, y1 - 2, 2, GuideInk.BRASS);
        g.fill(x0 + 4, y0 + 4, x1 - 4, y1 - 4, GuideInk.FRAME_FILL);
        int y = f.contentY();
        if (!f.stack().isEmpty()) {
            item(g, f.stack(), x0 + (f.w() - GuideMetrics.ICON) / 2, y, GuideMetrics.ICON);
            y += GuideMetrics.ICON + 4;
        }
        List<FormattedCharSequence> lines = f.lines();
        for (int i = 0; i < lines.size(); i++) {
            g.drawString(font, lines.get(i), f.lineX()[i], y, GuideInk.FAINT, false);
            y += font.lineHeight;
        }
    }

    /** Um contorno de espessura {@code t} por dentro do retângulo. */
    static void ring(GuiGraphics g, int x0, int y0, int x1, int y1, int t, int color) {
        g.fill(x0, y0, x1, y0 + t, color);
        g.fill(x0, y1 - t, x1, y1, color);
        g.fill(x0, y0 + t, x0 + t, y1 - t, color);
        g.fill(x1 - t, y0 + t, x1, y1 - t, color);
    }

    /** O link ou o item sob o ponto ({@code ux}, {@code uy}) em unidades de folha, ou {@code null}. */
    static @Nullable Ink hit(List<Ink> inks, double ux, double uy) {
        for (int i = 0; i < inks.size(); i++) {
            Ink ink = inks.get(i);
            if (ink instanceof GuideInk.Link l && inside(ux, uy, l.x(), l.y(), l.w(), l.h())) {
                return l;
            }
            if (ink instanceof GuideInk.Item it && !it.stack().isEmpty()
                    && inside(ux, uy, it.x(), it.y(), it.size(), it.size())) {
                return it;
            }
        }
        return null;
    }

    static boolean inside(double px, double py, int x, int y, int w, int h) {
        return px >= x && px < x + w && py >= y && py < y + h;
    }
}
