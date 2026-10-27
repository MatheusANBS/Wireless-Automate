package io.github.matheusanbs.wirelessautomate.client.tv.editor;

import io.github.matheusanbs.wirelessautomate.client.GuiPaint;
import io.github.matheusanbs.wirelessautomate.client.GuiText;
import io.github.matheusanbs.wirelessautomate.tv.TvColor;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * A cor na coluna de propriedades: uma linha "rótulo + amostra" (a amostra mostra a cor, e por cima o nome do papel do
 * tema, o hexadecimal ou "Nenhuma"); o clique abre o {@link TvColorPopup}. Uma cor do tema vale {@code 0x0000000N}
 * (ver {@link TvColor}); a fixa, ARGB. {@code alpha} diz se o jogador pode pôr transparência; {@code allowNone}, se a
 * cor pode faltar.
 */
final class TvColorPicker {
    private static final String[] ROLES = {"bg", "card", "text", "muted", "high", "low"};

    private TvColorPicker() {
    }

    /** Uma linha com o rótulo à esquerda e a amostra clicável. */
    static TvColorField add(TvForm form, TvPropsCtx ctx, String id, Component title, IntSupplier get, IntConsumer set,
            boolean allowNone, boolean alpha) {
        int y = form.inline(id + ".title", title);
        TvColorField field = form.add(new TvColorField(form.font, id, TvForm.LABEL_W, y, TvForm.W - TvForm.LABEL_W,
                ctx, get, set, allowNone, alpha));
        form.endRow(TvTextField.H);
        return field;
    }

    /** Só a amostra, numa posição dada (as frases das regras). */
    static TvColorField chip(TvForm form, TvPropsCtx ctx, String id, int x, int y, int w, IntSupplier get,
            IntConsumer set) {
        return form.add(new TvColorField(form.font, id, x, y, w, ctx, get, set, false, false));
    }

    /** A amostra da cor atual. */
    static final class TvColorField extends TvControl {
        private final Font font;
        private final TvPropsCtx ctx;
        private final IntSupplier get;
        private final IntConsumer set;
        private final boolean allowNone;
        private final boolean alpha;
        private int value = Integer.MIN_VALUE;
        private int resolved;
        private GuiText.Fitted text;

        TvColorField(Font font, String id, int x, int y, int w, TvPropsCtx ctx, IntSupplier get, IntConsumer set,
                boolean allowNone, boolean alpha) {
            super(id, x, y, w, TvTextField.H);
            this.font = font;
            this.ctx = ctx;
            this.get = get;
            this.set = set;
            this.allowNone = allowNone;
            this.alpha = alpha;
            refresh();
        }

        @Override
        void refresh() {
            int now = get.getAsInt();
            int shown = TvColor.resolve(now, ctx.theme());
            if (now == value && shown == resolved && text != null) {
                return;
            }
            value = now;
            resolved = shown;
            Component label = now == 0 ? TvEditorText.key("color.none") : TvColor.isTheme(now)
                    ? TvEditorText.key("base.role." + ROLES[now - 1]) : Component.literal(TvColor.hex(now));
            text = w < 40 ? GuiText.fit(font, Component.empty(), w) : GuiText.fit(font, label, w - 6);
        }

        @Override
        void render(GuiGraphics g, int mouseX, int mouseY) {
            g.fill(ax + 1, ay + 1, ax + w - 1, ay + h - 1, 0xFFFFFFFF);
            for (int col = 0; col * 4 < w - 2; col++) {
                for (int row = col & 1; row * 4 < h - 2; row += 2) {
                    g.fill(ax + 1 + col * 4, ay + 1 + row * 4, Math.min(ax + w - 1, ax + 5 + col * 4),
                            Math.min(ay + h - 1, ay + 5 + row * 4), 0xFFB5B0A6);
                }
            }
            if (value != 0) {
                g.fill(ax + 1, ay + 1, ax + w - 1, ay + h - 1, resolved);
            }
            GuiPaint.outline(g, ax, ay, w, h, contains(mouseX, mouseY) ? GuiPaint.BUTTON_HOVER_BORDER
                    : GuiPaint.BUTTON_BORDER);
            int ink = value == 0 || (resolved >>> 24) < 0x80 ? 0xFF303030 : luminance(resolved) > 140 ? 0xFF101010
                    : 0xFFF4F1EA;
            GuiText.draw(g, font, text, ax + 3, ay + 2, ink);
        }

        private static int luminance(int argb) {
            return ((argb >> 16 & 0xFF) * 3 + (argb >> 8 & 0xFF) * 6 + (argb & 0xFF)) / 10;
        }

        @Override
        boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (!contains(mouseX, mouseY)) {
                return false;
            }
            TvRect anchor = new TvRect(ax, ay, w, h);
            ctx.popups().open(new TvColorPopup(font, anchor, ctx.popups().bounds(), get.getAsInt(), allowNone, alpha,
                    ctx.theme(), set));
            return true;
        }
    }
}
