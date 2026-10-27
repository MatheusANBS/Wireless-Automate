package io.github.matheusanbs.wirelessautomate.client.tv.editor;

import io.github.matheusanbs.wirelessautomate.client.GuiPaint;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/**
 * Um controle deslizante inteiro de {@code min} a {@code max} em passos: clicar ou arrastar põe o valor, a roda anda
 * um passo. O número aparece sobre a barra (ex.: {@code 85%}); o texto é refeito só quando o valor muda.
 */
final class TvSlider extends TvControl {
    private final Font font;
    private final int min;
    private final int max;
    private final int step;
    private final String suffix;
    private final IntSupplier get;
    private final IntConsumer set;
    private int current = Integer.MIN_VALUE;
    private Component shown = Component.empty();
    private int shownWidth;

    TvSlider(Font font, String id, int x, int y, int w, int min, int max, int step, String suffix, IntSupplier get,
            IntConsumer set) {
        super(id, x, y, w, TvTextField.H);
        this.font = font;
        this.min = min;
        this.max = max;
        this.step = step;
        this.suffix = suffix;
        this.get = get;
        this.set = set;
        refresh();
    }

    @Override
    void refresh() {
        int now = get.getAsInt();
        if (now != current) {
            current = now;
            shown = Component.literal(now + suffix);
            shownWidth = font.width(shown);
        }
    }

    @Override
    void render(GuiGraphics g, int mouseX, int mouseY) {
        GuiPaint.box(g, ax, ay, w, h, GuiPaint.INSET, contains(mouseX, mouseY) ? GuiPaint.BUTTON_HOVER_BORDER
                : GuiPaint.BUTTON_BORDER);
        int fill = (w - 2) * (current - min) / Math.max(1, max - min);
        g.fill(ax + 1, ay + 1, ax + 1 + fill, ay + h - 1, GuiPaint.alpha(GuiPaint.ACCENT, 0x90));
        GuiPaint.text(g, font, shown, ax + (w - shownWidth) / 2, ay + 2, GuiPaint.FG);
    }

    private void pick(double mouseX) {
        double fraction = (mouseX - ax - 1) / Math.max(1, w - 2);
        int value = min + (int) Math.round(Mth.clamp(fraction, 0, 1) * (max - min) / step) * step;
        value = Mth.clamp(value, min, max);
        if (value != current) {
            set.accept(value);
        }
    }

    @Override
    boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!contains(mouseX, mouseY)) {
            return false;
        }
        pick(mouseX);
        return true;
    }

    @Override
    void mouseDragged(double mouseX, double mouseY) {
        pick(mouseX);
    }

    @Override
    boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (!contains(mouseX, mouseY)) {
            return false;
        }
        int value = Mth.clamp(current + (delta > 0 ? step : -step), min, max);
        if (value != current) {
            set.accept(value);
        }
        return true;
    }
}
