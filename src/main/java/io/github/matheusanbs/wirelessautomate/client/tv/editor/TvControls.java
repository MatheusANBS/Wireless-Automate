package io.github.matheusanbs.wirelessautomate.client.tv.editor;

import io.github.matheusanbs.wirelessautomate.client.GuiPaint;
import io.github.matheusanbs.wirelessautomate.client.GuiText;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.IntSupplier;
import java.util.function.Supplier;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.jetbrains.annotations.Nullable;

/**
 * Os controles simples da coluna de propriedades (desenhados, sem widget): o rótulo, o texto de ajuda de várias
 * linhas, a caixa de marcar e o botão; a escolha de uma lista é o {@link TvDropdown}. Os textos são medidos uma vez e
 * relidos só quando o rascunho muda.
 */
final class TvControls {
    static final int ROW = 12;
    /** As cores dos rótulos, lidas a cada desenho: o tema pode trocar sem o formulário ser refeito. */
    static final IntSupplier MUTED = () -> GuiPaint.MUTED;
    static final IntSupplier FG = () -> GuiPaint.FG;

    private TvControls() {
    }

    /** Um rótulo de uma linha; o texto pode mudar (lido no refresh) e é refeito só se mudou. */
    static final class Label extends TvControl {
        private final Font font;
        private final Supplier<Component> text;
        private final IntSupplier color;
        private Component last;
        private GuiText.Fitted fitted;

        Label(Font font, String id, int x, int y, int w, IntSupplier color, Supplier<Component> text) {
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
            GuiText.draw(g, font, fitted, ax, ay, color.getAsInt());
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

    /** Um botão com texto (ou símbolo), que roda {@code action}; desligado, não responde e mostra o porquê na dica. */
    static final class Button extends TvControl {
        private final Font font;
        private final TvEditorPaint.Label label;
        private final Runnable action;
        private BooleanSupplier enabled = () -> true;
        private Supplier<Component> offTip = () -> null;
        private boolean on = true;
        private @Nullable Component tip;

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

        /** A dica do botão desligado (o porquê), lida no refresh; nula, sem dica. */
        Button offTip(Supplier<Component> why) {
            this.offTip = why;
            this.tip = on ? null : why.get();
            return this;
        }

        @Override
        void refresh() {
            on = enabled.getAsBoolean();
            tip = on ? null : offTip.get();
        }

        @Override
        @Nullable Component tooltip(double mouseX, double mouseY) {
            return contains(mouseX, mouseY) ? tip : null;
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
