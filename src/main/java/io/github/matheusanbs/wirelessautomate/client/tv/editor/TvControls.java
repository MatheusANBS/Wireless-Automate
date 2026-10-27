package io.github.matheusanbs.wirelessautomate.client.tv.editor;

import io.github.matheusanbs.wirelessautomate.client.GuiPaint;
import io.github.matheusanbs.wirelessautomate.client.GuiText;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import java.util.function.Supplier;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

/**
 * Os controles simples da coluna de propriedades (desenhados, sem widget): o rótulo, o texto de ajuda de várias
 * linhas, a caixa de marcar, o seletor ‹ › que gira por uma lista, o grupo de fichas e o botão. Os textos são
 * medidos uma vez e relidos só quando o rascunho muda.
 */
final class TvControls {
    static final int ROW = 12;
    private static final Component LEFT = Component.literal("‹");
    private static final Component RIGHT = Component.literal("›");

    private TvControls() {
    }

    /** Um rótulo de uma linha; o texto pode mudar (lido no refresh) e é refeito só se mudou. */
    static final class Label extends TvControl {
        private final Font font;
        private final Supplier<Component> text;
        private final int color;
        private Component last;
        private GuiText.Fitted fitted;

        Label(Font font, String id, int x, int y, int w, int color, Supplier<Component> text) {
            super(id, x, y, w, 9);
            this.font = font;
            this.text = text;
            this.color = color;
            refresh();
        }

        @Override
        void refresh() {
            Component now = text.get();
            if (!now.equals(last)) {
                last = now;
                fitted = GuiText.fit(font, now, w);
            }
        }

        @Override
        void render(GuiGraphics g, int mouseX, int mouseY) {
            GuiText.draw(g, font, fitted, ax, ay, color);
        }
    }

    /** Um texto de ajuda, quebrado em linhas de 10 px; a altura é a das linhas. */
    static final class Hint extends TvControl {
        private final Font font;
        private final List<FormattedCharSequence> lines;

        Hint(Font font, String id, int x, int y, int w, Component text) {
            super(id, x, y, w, font.split(text, w).size() * 10);
            this.font = font;
            this.lines = font.split(text, w);
        }

        @Override
        void render(GuiGraphics g, int mouseX, int mouseY) {
            for (int i = 0; i < lines.size(); i++) {
                GuiPaint.text(g, font, lines.get(i), ax, ay + i * 10, GuiPaint.MUTED);
            }
        }
    }

    /** Uma caixa de marcar com o rótulo ao lado; o clique em qualquer ponto da linha troca. */
    static final class Toggle extends TvControl {
        private final Font font;
        private final GuiText.Fitted label;
        private final BooleanSupplier get;
        private final Consumer<Boolean> set;
        private boolean checked;

        Toggle(Font font, String id, int x, int y, int w, Component label, BooleanSupplier get,
                Consumer<Boolean> set) {
            super(id, x, y, w, 11);
            this.font = font;
            this.label = GuiText.fit(font, label, w - 13);
            this.get = get;
            this.set = set;
            this.checked = get.getAsBoolean();
        }

        @Override
        void refresh() {
            checked = get.getAsBoolean();
        }

        @Override
        void render(GuiGraphics g, int mouseX, int mouseY) {
            GuiPaint.checkbox(g, ax, ay + 1, checked, GuiPaint.ACCENT);
            GuiText.draw(g, font, label, ax + 13, ay + 2, GuiPaint.FG);
        }

        @Override
        boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (!contains(mouseX, mouseY)) {
                return false;
            }
            checked = !checked;
            set.accept(checked);
            return true;
        }
    }

    /** Um seletor ‹ nome ›: o clique à esquerda volta, à direita (e no meio) avança, a roda gira. */
    static final class Cycle extends TvControl {
        private static final int ARROW = 8;
        private final Font font;
        private final Supplier<Component> label;
        private final IntConsumer step;
        private Component last;
        private GuiText.Fitted fitted;
        private int textWidth;
        private final int rightWidth;

        Cycle(Font font, String id, int x, int y, int w, Supplier<Component> label, IntConsumer step) {
            super(id, x, y, w, ROW - 1);
            this.font = font;
            this.label = label;
            this.step = step;
            this.rightWidth = font.width(RIGHT);
            refresh();
        }

        @Override
        void refresh() {
            Component now = label.get();
            if (!now.equals(last)) {
                last = now;
                fitted = GuiText.fit(font, now, w - 2 * ARROW - 2);
                textWidth = font.width(fitted.shown());
            }
        }

        @Override
        void render(GuiGraphics g, int mouseX, int mouseY) {
            boolean hovered = contains(mouseX, mouseY);
            GuiPaint.box(g, ax, ay, w, h, GuiPaint.BUTTON,
                    hovered ? GuiPaint.BUTTON_HOVER_BORDER : GuiPaint.BUTTON_BORDER);
            GuiPaint.text(g, font, LEFT, ax + 3, ay + 2, GuiPaint.MUTED);
            GuiPaint.text(g, font, RIGHT, ax + w - 3 - rightWidth, ay + 2, GuiPaint.MUTED);
            GuiText.draw(g, font, fitted, ax + (w - textWidth) / 2, ay + 2, GuiPaint.FG);
        }

        @Override
        boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (!contains(mouseX, mouseY)) {
                return false;
            }
            step.accept(button == 1 || mouseX < ax + ARROW ? -1 : 1);
            return true;
        }

        @Override
        boolean mouseScrolled(double mouseX, double mouseY, double delta) {
            if (!contains(mouseX, mouseY)) {
                return false;
            }
            step.accept(delta > 0 ? -1 : 1);
            return true;
        }
    }

    /** Fichas lado a lado, uma marcada (o alinhamento, o segmento da regra). */
    static final class Segment extends TvControl {
        private final Font font;
        private final GuiText.Fitted[] names;
        private final int[] widths;
        private final IntSupplier get;
        private final IntConsumer set;
        private int current;

        Segment(Font font, String id, int x, int y, int w, List<Component> labels, IntSupplier get,
                IntConsumer set) {
            super(id, x, y, w, ROW - 1);
            this.font = font;
            this.get = get;
            this.set = set;
            names = new GuiText.Fitted[labels.size()];
            widths = new int[labels.size()];
            int each = chip(labels.size());
            for (int i = 0; i < names.length; i++) {
                names[i] = GuiText.fit(font, labels.get(i), each - 4);
                widths[i] = font.width(names[i].shown());
            }
            refresh();
        }

        private int chip(int count) {
            return (w - (count - 1)) / count;
        }

        @Override
        void refresh() {
            current = get.getAsInt();
        }

        @Override
        void render(GuiGraphics g, int mouseX, int mouseY) {
            int each = chip(names.length);
            for (int i = 0; i < names.length; i++) {
                int left = ax + i * (each + 1);
                boolean on = i == current;
                GuiPaint.box(g, left, ay, each, h, GuiPaint.BUTTON,
                        on ? GuiPaint.ACCENT : mouseX >= left && mouseX < left + each && mouseY >= ay && mouseY < ay + h
                                ? GuiPaint.BUTTON_HOVER_BORDER : GuiPaint.BUTTON_BORDER);
                if (on) {
                    g.fill(left + 1, ay + 1, left + each - 1, ay + h - 1, GuiPaint.alpha(GuiPaint.ACCENT, 0x40));
                }
                GuiText.draw(g, font, names[i], left + (each - widths[i]) / 2, ay + 2, GuiPaint.FG);
            }
        }

        @Override
        boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (!contains(mouseX, mouseY)) {
                return false;
            }
            int index = Math.min(names.length - 1, (int) (mouseX - ax) / (chip(names.length) + 1));
            if (index != current) {
                set.accept(index);
            }
            return true;
        }

        /** O centro da ficha {@code index} (ganchos do e2e). */
        int[] chipCenter(int index) {
            int each = chip(names.length);
            return new int[] {ax + index * (each + 1) + each / 2, centerY()};
        }
    }

    /** Um botão com texto (ou símbolo), que roda {@code action}; desligado, não responde. */
    static final class Button extends TvControl {
        private final Font font;
        private final TvEditorPaint.Label label;
        private final Runnable action;
        private BooleanSupplier enabled = () -> true;
        private boolean on = true;

        Button(Font font, String id, int x, int y, int w, Component text, Runnable action) {
            super(id, x, y, w, ROW - 1);
            this.font = font;
            this.label = TvEditorPaint.label(font, text, w - 4);
            this.action = action;
        }

        /** Liga o botão a uma condição, lida só no refresh. */
        Button enabledWhen(BooleanSupplier condition) {
            this.enabled = condition;
            this.on = condition.getAsBoolean();
            return this;
        }

        @Override
        void refresh() {
            on = enabled.getAsBoolean();
        }

        @Override
        void render(GuiGraphics g, int mouseX, int mouseY) {
            TvEditorPaint.button(g, font, ax, ay, w, h, label, contains(mouseX, mouseY), on);
        }

        @Override
        boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (!contains(mouseX, mouseY)) {
                return false;
            }
            if (on) {
                action.run();
            }
            return true;
        }
    }
}
