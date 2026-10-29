package io.github.matheusanbs.wirelessautomate.client.tv.editor;

import io.github.matheusanbs.wirelessautomate.client.GuiPaint;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/** Os controles de ícone da coluna de propriedades: o botão e as fichas lado a lado, cada um com a dica. */
final class TvIconControls {
    private TvIconControls() {
    }

    /** Um botão quadrado com um ícone e uma dica; desligado, não responde. */
    static final class IconButton extends TvControl {
        private final TvIcons.Icon icon;
        private final Component tip;
        private final Runnable action;
        private BooleanSupplier enabled = () -> true;
        private boolean on = true;

        IconButton(String id, int x, int y, int size, TvIcons.Icon icon, Component tip, Runnable action) {
            super(id, x, y, size, TvIcons.SIZE);
            this.icon = icon;
            this.tip = tip;
            this.action = action;
        }

        IconButton enabledWhen(BooleanSupplier condition) {
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
            if (on) {
                GuiPaint.box(g, ax, ay, w, h, GuiPaint.BUTTON, contains(mouseX, mouseY)
                        ? GuiPaint.BUTTON_HOVER_BORDER : GuiPaint.BUTTON_BORDER);
            } else {
                GuiPaint.box(g, ax, ay, w, h, GuiPaint.INSET, GuiPaint.LINE);
            }
            TvIcons.draw(g, icon, ax + (w - TvIcons.SIZE) / 2, ay + (h - TvIcons.SIZE) / 2, on);
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

        @Override
        @Nullable Component tooltip(double mouseX, double mouseY) {
            return contains(mouseX, mouseY) ? tip : null;
        }
    }

    /** Fichas de ícone lado a lado, uma marcada (o alinhamento do texto). */
    static final class IconSegment extends TvControl {
        private final List<TvIcons.Icon> icons;
        private final List<Component> tips;
        private final IntSupplier get;
        private final IntConsumer set;
        private int current;

        IconSegment(String id, int x, int y, int w, List<TvIcons.Icon> icons, List<Component> tips, IntSupplier get,
                IntConsumer set) {
            super(id, x, y, w, TvIcons.SIZE);
            this.icons = icons;
            this.tips = tips;
            this.get = get;
            this.set = set;
            refresh();
        }

        private int chip() {
            return (w - (icons.size() - 1)) / icons.size();
        }

        private int indexAt(double mouseX) {
            return Math.min(icons.size() - 1, Math.max(0, (int) (mouseX - ax) / (chip() + 1)));
        }

        @Override
        void refresh() {
            current = get.getAsInt();
        }

        @Override
        void render(GuiGraphics g, int mouseX, int mouseY) {
            int each = chip();
            for (int i = 0; i < icons.size(); i++) {
                int left = ax + i * (each + 1);
                boolean on = i == current;
                boolean over = mouseX >= left && mouseX < left + each && mouseY >= ay && mouseY < ay + h;
                GuiPaint.box(g, left, ay, each, h, GuiPaint.BUTTON,
                        on ? GuiPaint.ACCENT : over ? GuiPaint.BUTTON_HOVER_BORDER : GuiPaint.BUTTON_BORDER);
                if (on) {
                    // o realce vem do coral sobre o botão do tema: o SELECTED é porcelana nos dois temas e some o ícone
                    g.fill(left + 1, ay + 1, left + each - 1, ay + h - 1, GuiPaint.alpha(GuiPaint.ACCENT, 0x40));
                }
                TvIcons.draw(g, icons.get(i), left + (each - TvIcons.SIZE) / 2, ay + (h - TvIcons.SIZE) / 2);
            }
        }

        @Override
        boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (!contains(mouseX, mouseY)) {
                return false;
            }
            int index = indexAt(mouseX);
            if (index != current) {
                set.accept(index);
            }
            return true;
        }

        @Override
        @Nullable Component tooltip(double mouseX, double mouseY) {
            return contains(mouseX, mouseY) ? tips.get(indexAt(mouseX)) : null;
        }

        @Override
        int[] chipCenter(int index) {
            int each = chip();
            return new int[] {ax + index * (each + 1) + each / 2, centerY()};
        }
    }
}
