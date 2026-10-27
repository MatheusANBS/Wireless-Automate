package io.github.matheusanbs.wirelessautomate.client.tv.editor;

import io.github.matheusanbs.wirelessautomate.client.GuiPaint;
import io.github.matheusanbs.wirelessautomate.tv.TvColor;
import io.github.matheusanbs.wirelessautomate.tv.TvTheme;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import java.util.function.Supplier;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * O seletor de cor da coluna de propriedades: uma fileira com os seis papéis do tema (as amostras redondas, que
 * acompanham a troca de tema), as amostras fixas (quadradas) e, quando a cor pode faltar, a "nenhuma" xadrez; e abaixo
 * o campo {@code #RRGGBB} ou {@code #RRGGBBAA}. Uma cor do tema vale {@code 0x0000000N} (ver {@link TvColor}); a fixa,
 * ARGB. Montado pelas abas com {@link #add}.
 */
final class TvColorPicker {
    private static final int[] FIXED = {0xFF4FD1C5, 0xFFE39A2E, 0xFF8A8EE8, 0xFFFFFFFF};
    private static final int SIZE = 10;
    private static final int STEP = SIZE + 1;

    private TvColorPicker() {
    }

    /**
     * Põe o título, as amostras e (com {@code hex}) o campo no formulário. {@code theme} dá a paleta do layout para
     * pintar as amostras dos papéis.
     */
    static void add(TvForm form, String id, Component title, IntSupplier get, IntConsumer set, boolean allowNone,
            boolean hex, Supplier<TvTheme> theme) {
        form.label(id + ".title", title);
        form.add(new Swatches(id + ".sw", form.y(), get, set, allowNone, theme));
        form.skip(SIZE + 2);
        if (hex) {
            form.add(new TvTextField(form.font, id + ".hex", 0, form.y(), TvForm.W, 9,
                    text -> text.matches("#?[0-9a-fA-F]*"), Component.literal("#RRGGBB"),
                    () -> TvColor.hex(get.getAsInt()), text -> {
                        Integer parsed = TvColor.fromHex(text);
                        if (parsed != null && (parsed != 0 || allowNone)) {
                            set.accept(parsed);
                        }
                    }));
            form.skip(TvTextField.H + 2);
        }
        form.skip(TvForm.GAP);
    }

    /** A fileira de amostras. */
    static final class Swatches extends TvControl {
        private final IntSupplier get;
        private final IntConsumer set;
        private final int[] values;
        private final Supplier<TvTheme> theme;
        private final int[] shown;
        private int current;

        Swatches(String id, int y, IntSupplier get, IntConsumer set, boolean allowNone, Supplier<TvTheme> theme) {
            super(id, 0, y, TvForm.W, SIZE);
            this.get = get;
            this.set = set;
            this.theme = theme;
            values = new int[6 + FIXED.length + (allowNone ? 1 : 0)];
            for (int i = 0; i < 6; i++) {
                values[i] = i + 1;
            }
            System.arraycopy(FIXED, 0, values, 6, FIXED.length);
            shown = new int[values.length];
            refresh();
        }

        @Override
        void refresh() {
            current = get.getAsInt();
            TvTheme t = theme.get();
            for (int i = 0; i < values.length; i++) {
                shown[i] = i < 6 ? t.color(values[i]) | 0xFF000000 : values[i];
            }
        }

        @Override
        void render(GuiGraphics g, int mouseX, int mouseY) {
            for (int i = 0; i < values.length; i++) {
                int left = ax + i * STEP;
                boolean on = values[i] == current;
                boolean over = mouseX >= left && mouseX < left + SIZE && mouseY >= ay && mouseY < ay + SIZE;
                int border = on ? GuiPaint.ACCENT : over ? GuiPaint.BUTTON_HOVER_BORDER : GuiPaint.BUTTON_BORDER;
                if (values[i] == 0) {
                    g.fill(left + 1, ay + 1, left + SIZE - 1, ay + SIZE - 1, 0xFFFFFFFF);
                    g.fill(left + 1, ay + 1, left + 5, ay + 5, 0xFFB5B0A6);
                    g.fill(left + 5, ay + 5, left + SIZE - 1, ay + SIZE - 1, 0xFFB5B0A6);
                } else {
                    g.fill(left + 1, ay + 1, left + SIZE - 1, ay + SIZE - 1, shown[i]);
                }
                int corner = i < 6 ? 1 : 0;
                g.fill(left + corner, ay, left + SIZE - corner, ay + 1, border);
                g.fill(left + corner, ay + SIZE - 1, left + SIZE - corner, ay + SIZE, border);
                g.fill(left, ay + corner, left + 1, ay + SIZE - corner, border);
                g.fill(left + SIZE - 1, ay + corner, left + SIZE, ay + SIZE - corner, border);
            }
        }

        @Override
        boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (!contains(mouseX, mouseY)) {
                return false;
            }
            int index = (int) (mouseX - ax) / STEP;
            if (index < values.length && mouseX - ax - index * STEP < SIZE) {
                set.accept(values[index]);
            }
            return true;
        }

        /** O centro da amostra de um papel (1 a 6) ou da cor fixa {@code value} (ganchos do e2e), ou nulo. */
        int @Nullable [] centerOf(int value) {
            for (int i = 0; i < values.length; i++) {
                if (values[i] == value) {
                    return new int[] {ax + i * STEP + SIZE / 2, ay + SIZE / 2};
                }
            }
            return null;
        }
    }
}
