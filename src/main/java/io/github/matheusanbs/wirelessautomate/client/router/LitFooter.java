package io.github.matheusanbs.wirelessautomate.client.router;

import io.github.matheusanbs.wirelessautomate.client.GuiPaint;
import io.github.matheusanbs.wirelessautomate.client.GuiText;
import io.github.matheusanbs.wirelessautomate.client.highlight.HighlightRenderer;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * A linha embaixo da lista das máquinas ({@link RangeList}) com alguma máquina acesa no mundo: "2 máquinas
 * acesas no mundo · Apagar todas", ou "2 acesas" quando a lista é estreita. O Apagar todas apaga as acesas de
 * qualquer roteador. Os textos e as larguras são montados quando as acesas ou a largura mudam. Guarda também
 * os textos do destaque comuns à lista e ao painel ({@link #tr}, {@link #eyeTooltip}).
 */
final class LitFooter {
    static final int H = 12;
    private static final Component SEPARATOR = Component.literal(" · ");

    private int x;
    private int y;
    private int w;
    private int version = Integer.MIN_VALUE;
    private int preparedW = -1;
    private Component count = Component.empty();
    private Component clear = Component.empty();
    private int clearW;
    private int countW;
    private int separatorW;

    static Component tr(String key, Object... args) {
        return Component.translatable("gui.wirelessautomate.highlight." + key, args);
    }

    /** A dica do Olho da linha e do "No mundo" do painel: acender ou apagar a máquina no mundo. */
    static Component eyeTooltip(boolean lit) {
        return tr(lit ? "eye.off" : "eye.on").copy().append("\n")
                .append(tr(lit ? "eye.off.tooltip" : "eye.on.tooltip").copy().withColor(GuiPaint.TOOLTIP_MUTED));
    }

    /** A linha aparece: alguma máquina está acesa. */
    static boolean shown() {
        return HighlightRenderer.count() > 0;
    }

    /** Põe a linha no lugar e monta os textos que mudaram; a tela chama antes do desenho e dos cliques. */
    void place(Font font, int x, int y, int w) {
        this.x = x;
        this.y = y;
        this.w = w;
        prepare(font);
    }

    private void prepare(Font font) {
        if (version == HighlightRenderer.version() && preparedW == w) {
            return;
        }
        version = HighlightRenderer.version();
        preparedW = w;
        int n = HighlightRenderer.count();
        clear = tr("clear_all");
        clearW = font.width(clear);
        separatorW = font.width(SEPARATOR);
        Component full = n == 1 ? tr("lit.one") : tr("lit", n);
        count = font.width(full) <= w - clearW - separatorW - 4 ? full : n == 1 ? tr("lit.short.one") : tr("lit.short", n);
        countW = font.width(count);
    }

    /** O Apagar todas logo depois da contagem e do separador; numa lista estreita demais, preso à direita. */
    private int clearX() {
        return Math.min(x + 2 + countW + separatorW, x + w - clearW - 2);
    }

    private boolean overClear(double mx, double my) {
        return mx >= clearX() - 1 && mx < clearX() + clearW + 1 && my >= y && my < y + H;
    }

    void render(GuiGraphics g, Font font, int mx, int my) {
        int cx = clearX();
        GuiText.draw(g, font, count, x + 2, y + 2, cx - separatorW - x - 2, GuiPaint.MUTED);
        GuiPaint.text(g, font, SEPARATOR, cx - separatorW, y + 2, GuiPaint.MUTED);
        boolean over = overClear(mx, my);
        GuiPaint.text(g, font, clear, cx, y + 2, over ? GuiPaint.ACCENT : GuiPaint.FG);
        g.fill(cx, y + 11, cx + clearW, y + 12, GuiPaint.ACCENT);
    }

    /** Clique no Apagar todas: apaga todas as acesas. */
    boolean click(double mx, double my) {
        if (!overClear(mx, my)) {
            return false;
        }
        HighlightRenderer.clear();
        return true;
    }

    @Nullable Component tooltip(double mx, double my) {
        return overClear(mx, my) ? tr("clear_all.tooltip") : null;
    }

    /** O centro do Apagar todas (gancho do e2e). */
    int[] clearCenter() {
        return new int[] {clearX() + clearW / 2, y + H / 2};
    }
}
