package io.github.matheusanbs.wirelessautomate.client.tv.editor;

import io.github.matheusanbs.wirelessautomate.client.GuiPaint;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;

/**
 * Um campo numérico com limites: ‹ e › passam o valor de {@code step} em {@code step} (com Shift, quatro vezes), a
 * roda do mouse sobre o campo também, e dá para digitar (só dígitos, e o sinal quando o mínimo é negativo). O valor
 * digitado é limitado a {@code min}..{@code max} e já vai para o rascunho a cada letra; soltar o foco refaz o texto.
 * O número vem do {@code get}, lido só no {@link #refresh}.
 */
final class TvNumberField extends TvControl {
    static final int ARROW = 10;
    private static final Component LEFT = Component.literal("‹");
    private static final Component RIGHT = Component.literal("›");

    private final Font font;
    private final TvTextField text;
    private final int min;
    private final int max;
    private final int step;
    private final IntSupplier get;
    private final IntConsumer set;

    TvNumberField(Font font, String id, int x, int y, int w, int min, int max, int step, IntSupplier get,
            IntConsumer set) {
        super(id, x, y, w, TvTextField.H);
        this.font = font;
        this.min = min;
        this.max = max;
        this.step = step;
        this.get = get;
        this.set = set;
        int digits = Integer.toString(Math.max(Math.abs(min), Math.abs(max))).length() + (min < 0 ? 1 : 0);
        this.text = new TvTextField(font, id + ".text", x + ARROW, y, w - 2 * ARROW, digits,
                value -> value.matches(min < 0 ? "-?\\d*" : "\\d*"), Component.empty(),
                () -> Integer.toString(get.getAsInt()), this::typed);
    }

    private void typed(String value) {
        if (value.isEmpty() || value.equals("-")) {
            return;
        }
        try {
            int parsed = Mth.clamp(Integer.parseInt(value), min, max);
            if (parsed != get.getAsInt()) {
                set.accept(parsed);
            }
        } catch (NumberFormatException e) {
            // um número grande demais: o campo guarda o texto, o valor fica
        }
    }

    @Override
    void place(int originX, int originY) {
        super.place(originX, originY);
        text.place(originX, originY);
    }

    private void change(int delta, boolean big) {
        int next = Mth.clamp(get.getAsInt() + delta * step * (big ? 4 : 1), min, max);
        if (next != get.getAsInt()) {
            set.accept(next);
        }
        text.syncNow();
    }

    @Override
    void refresh() {
        text.refresh();
    }

    @Override
    void render(GuiGraphics g, int mouseX, int mouseY) {
        arrow(g, ax, LEFT, mouseX, mouseY);
        arrow(g, ax + w - ARROW, RIGHT, mouseX, mouseY);
        text.render(g, mouseX, mouseY);
    }

    private void arrow(GuiGraphics g, int left, Component glyph, int mouseX, int mouseY) {
        boolean hovered = mouseX >= left && mouseX < left + ARROW && mouseY >= ay && mouseY < ay + h;
        GuiPaint.box(g, left, ay, ARROW, h, GuiPaint.BUTTON,
                hovered ? GuiPaint.BUTTON_HOVER_BORDER : GuiPaint.BUTTON_BORDER);
        GuiPaint.text(g, font, glyph, left + (ARROW - font.width(glyph)) / 2, ay + 2, GuiPaint.FG);
    }

    @Override
    boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!contains(mouseX, mouseY)) {
            return false;
        }
        boolean big = Screen.hasShiftDown();
        if (mouseX < ax + ARROW) {
            change(-1, big);
        } else if (mouseX >= ax + w - ARROW) {
            change(1, big);
        } else {
            text.mouseClicked(mouseX, mouseY, button);
        }
        return true;
    }

    @Override
    boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (!contains(mouseX, mouseY)) {
            return false;
        }
        change(delta > 0 ? 1 : -1, Screen.hasShiftDown());
        return true;
    }

    @Override
    boolean wantsKeys() {
        return text.wantsKeys();
    }

    @Override
    void blur() {
        text.blur();
    }

    @Override
    boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (text.wantsKeys() && (keyCode == GLFW.GLFW_KEY_UP || keyCode == GLFW.GLFW_KEY_DOWN)) {
            change(keyCode == GLFW.GLFW_KEY_UP ? 1 : -1, (modifiers & GLFW.GLFW_MOD_SHIFT) != 0);
            return true;
        }
        return text.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    boolean charTyped(char c, int modifiers) {
        return text.charTyped(c, modifiers);
    }

    /** O centro do ‹ ou do › (ganchos do e2e). */
    int[] arrowCenter(boolean right) {
        return new int[] {right ? ax + w - ARROW / 2 : ax + ARROW / 2, centerY()};
    }

    void focus() {
        text.focus();
    }

    String value() {
        return text.value();
    }
}
