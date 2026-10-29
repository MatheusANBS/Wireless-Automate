package io.github.matheusanbs.wirelessautomate.client.tv.editor;

import io.github.matheusanbs.wirelessautomate.client.GuiPaint;
import io.github.matheusanbs.wirelessautomate.client.GuiText;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * Uma escolha de lista: fechado, o texto escolhido (cortado com a dica do mouse se não couber) e um ▾ à direita; o
 * clique abre a lista ({@link TvDropdownPopup}) por cima de tudo, pelo {@link TvPopups} do editor. A roda não troca o
 * valor: passa para o painel, que rola. As opções, o nome de cada uma e a escolhida vêm de fornecedores lidos só no
 * {@link #refresh}; os nomes são refeitos só quando a lista muda. A escolha vai pelo {@code pick}, que entra no desfazer
 * como qualquer mudança do painel; escolher a opção que já vale não muda nada.
 */
final class TvDropdown<T> extends TvControl {
    /** A largura da área do ▾, à direita. */
    private static final int ARROW_W = 10;
    private static final Component NONE = Component.literal("—");

    private final Font font;
    private final TvPopups popups;
    private final Supplier<? extends List<T>> values;
    private final Function<T, Component> name;
    private final Supplier<@Nullable T> current;
    private final Consumer<T> pick;
    private @Nullable Function<T, Component> detail;
    private BooleanSupplier enabled = () -> true;
    private Component none = NONE;
    private List<T> list = List.of();
    private Component[] names = new Component[0];
    private Component @Nullable [] details;
    private int selected = -1;
    private boolean on = true;
    private @Nullable Component last;
    private GuiText.Fitted shown;

    TvDropdown(Font font, String id, int x, int y, int w, TvPopups popups, Supplier<? extends List<T>> values,
            Function<T, Component> name, Supplier<@Nullable T> current, Consumer<T> pick) {
        super(id, x, y, w, TvControls.ROW - 1);
        this.font = font;
        this.popups = popups;
        this.values = values;
        this.name = name;
        this.current = current;
        this.pick = pick;
        refresh();
    }

    /** O texto à direita de cada linha da lista aberta (a prévia da fonte); fechado, não aparece. */
    TvDropdown<T> detail(Function<T, Component> detail) {
        this.detail = detail;
        this.list = List.of();
        refresh();
        return this;
    }

    /** O texto do controle quando o valor de agora não está na lista (uma paleta editada à mão, "sem ligação"). */
    TvDropdown<T> none(Component text) {
        this.none = text;
        this.last = null;
        refresh();
        return this;
    }

    /** Liga o controle a uma condição, lida só no refresh; desligado, fica apagado e não abre. */
    TvDropdown<T> enabledWhen(BooleanSupplier condition) {
        this.enabled = condition;
        refresh();
        return this;
    }

    @Override
    void refresh() {
        List<T> now = values.get();
        if (!now.equals(list)) {
            list = Collections.unmodifiableList(new ArrayList<>(now));
            names = new Component[list.size()];
            details = detail == null ? null : new Component[list.size()];
            for (int i = 0; i < names.length; i++) {
                names[i] = name.apply(list.get(i));
                if (details != null) {
                    details[i] = detail.apply(list.get(i));
                }
            }
            last = null;
        }
        selected = list.indexOf(current.get());
        on = enabled.getAsBoolean() && !list.isEmpty();
        Component text = selected >= 0 ? names[selected] : none;
        if (!text.equals(last)) {
            last = text;
            shown = GuiText.fit(font, text, w - ARROW_W - 5);
        }
    }

    private boolean isOpen() {
        return popups.current() instanceof TvDropdownPopup popup && popup.owner() == this;
    }

    @Override
    void render(GuiGraphics g, int mouseX, int mouseY) {
        boolean open = isOpen();
        boolean hovered = on && contains(mouseX, mouseY);
        GuiPaint.box(g, ax, ay, w, h, on ? GuiPaint.BUTTON : GuiPaint.INSET, open ? GuiPaint.ACCENT
                : hovered ? GuiPaint.BUTTON_HOVER_BORDER : GuiPaint.BUTTON_BORDER);
        GuiText.draw(g, font, shown, ax + 4, ay + 2, on ? GuiPaint.FG : GuiPaint.DISABLED);
        arrow(g, ax + w - ARROW_W, ay + 4, open, !on ? GuiPaint.DISABLED : hovered || open ? GuiPaint.FG
                : GuiPaint.MUTED);
    }

    /** O ▾ em pixels (7 × 4), virado para cima com a lista aberta. */
    private static void arrow(GuiGraphics g, int x, int y, boolean up, int color) {
        for (int row = 0; row < 4; row++) {
            int line = up ? 3 - row : row;
            g.fill(x + line, y + row, x + 7 - line, y + row + 1, color);
        }
    }

    @Override
    boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!contains(mouseX, mouseY)) {
            return false;
        }
        if (on && button == 0) {
            List<T> options = list;
            int chosen = selected;
            popups.open(new TvDropdownPopup(this, font, new TvRect(ax, ay, w, h), popups.bounds(), names, details,
                    chosen, index -> {
                        popups.close();
                        if (index != chosen) {
                            pick.accept(options.get(index));
                        }
                    }));
        }
        return true;
    }
}
