package io.github.matheusanbs.wirelessautomate.client.tv.editor;

import io.github.matheusanbs.wirelessautomate.client.GuiPaint;
import io.github.matheusanbs.wirelessautomate.client.GuiText;
import io.github.matheusanbs.wirelessautomate.client.tv.render.TvFonts;
import io.github.matheusanbs.wirelessautomate.tv.TvFont;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/**
 * A escolha da fonte do texto: um botão com o nome da fonte atual escrito nela mesma e, no clique, uma lista com todas
 * as fontes, cada uma com o nome e a prévia ("Aa 0123") na própria fonte. As prévias são montadas uma vez.
 */
final class TvFontMenu {
    private static final Component[] PREVIEWS = new Component[TvFont.values().length];

    private TvFontMenu() {
    }

    private static Component preview(TvFont font) {
        Component cached = PREVIEWS[font.ordinal()];
        if (cached == null) {
            cached = TvFonts.text(font, "Aa 0123");
            PREVIEWS[font.ordinal()] = cached;
        }
        return cached;
    }

    /** O botão da linha "Fonte". */
    static final class Field extends TvControl {
        private final Font font;
        private final TvPropsCtx ctx;
        private final Supplier<TvFont> get;
        private final Consumer<TvFont> set;
        private TvFont current;
        private GuiText.Fitted name;

        Field(Font font, String id, int x, int y, int w, TvPropsCtx ctx, Supplier<TvFont> get, Consumer<TvFont> set) {
            super(id, x, y, w, TvIcons.SIZE);
            this.font = font;
            this.ctx = ctx;
            this.get = get;
            this.set = set;
            refresh();
        }

        @Override
        void refresh() {
            TvFont now = get.get();
            if (now != current) {
                current = now;
                name = GuiText.fit(font, TvStyleTab.fontName(now), w - 14);
            }
        }

        @Override
        void render(GuiGraphics g, int mouseX, int mouseY) {
            GuiPaint.box(g, ax, ay, w, h, GuiPaint.BUTTON, contains(mouseX, mouseY) ? GuiPaint.BUTTON_HOVER_BORDER
                    : GuiPaint.BUTTON_BORDER);
            GuiText.draw(g, font, name, ax + 4, ay + 4, GuiPaint.FG);
            TvIcons.draw(g, TvIcons.Icon.SECTION_OPEN, ax + w - TvIcons.SIZE, ay);
        }

        @Override
        boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (!contains(mouseX, mouseY)) {
                return false;
            }
            ctx.popups().open(new Popup(font, new TvRect(ax, ay, w, h), ctx.popups().bounds(), current, picked -> {
                set.accept(picked);
                ctx.popups().close();
            }));
            return true;
        }
    }

    /** A lista das fontes. */
    static final class Popup implements TvPopup {
        private static final int ROW_H = 15;
        private static final int WIDTH = 112;
        private final Font font;
        private final int x;
        private final int y;
        private final int height;
        private final TvFont selected;
        private final Consumer<TvFont> set;
        private final GuiText.Fitted[] names;

        Popup(Font font, TvRect anchor, TvRect bounds, TvFont selected, Consumer<TvFont> set) {
            this.font = font;
            this.selected = selected;
            this.set = set;
            TvFont[] fonts = TvFont.values();
            height = fonts.length * ROW_H + 4;
            int left = anchor.x() - WIDTH - 4;
            x = Mth.clamp(left < bounds.x() ? anchor.right() + 4 : left, bounds.x(),
                    Math.max(bounds.x(), bounds.right() - WIDTH));
            y = Mth.clamp(anchor.y(), bounds.y(), Math.max(bounds.y(), bounds.bottom() - height));
            names = new GuiText.Fitted[fonts.length];
            for (int i = 0; i < fonts.length; i++) {
                names[i] = GuiText.fit(font, TvStyleTab.fontName(fonts[i]), 48);
            }
        }

        @Override
        public boolean contains(double mouseX, double mouseY) {
            return mouseX >= x && mouseX < x + WIDTH && mouseY >= y && mouseY < y + height;
        }

        @Override
        public void render(GuiGraphics g, int mouseX, int mouseY) {
            g.pose().pushPose();
            g.pose().translate(0, 0, 400);
            GuiPaint.box(g, x, y, WIDTH, height, GuiPaint.BUTTON, GuiPaint.BUTTON_BORDER);
            TvFont[] fonts = TvFont.values();
            for (int i = 0; i < fonts.length; i++) {
                int top = y + 2 + i * ROW_H;
                if (contains(mouseX, mouseY) && mouseY >= top && mouseY < top + ROW_H) {
                    g.fill(x + 1, top, x + WIDTH - 1, top + ROW_H, GuiPaint.ROW_HOVER);
                }
                boolean on = fonts[i] == selected;
                GuiText.draw(g, font, names[i], x + 5, top + 4, on ? GuiPaint.ACCENT : GuiPaint.FG);
                GuiPaint.text(g, font, preview(fonts[i]), x + 58, top + 4, GuiPaint.FG);
            }
            g.pose().popPose();
        }

        @Override
        public void mouseClicked(double mouseX, double mouseY, int button) {
            int row = (int) (mouseY - y - 2) / ROW_H;
            if (row >= 0 && row < TvFont.values().length) {
                set.accept(TvFont.values()[row]);
            }
        }

        /** O centro da linha da fonte (ganchos do e2e). */
        int[] center(TvFont wanted) {
            return new int[] {x + WIDTH / 2, y + 2 + wanted.ordinal() * ROW_H + ROW_H / 2};
        }
    }
}
