package io.github.matheusanbs.wirelessautomate.client.filter;

import static io.github.matheusanbs.wirelessautomate.client.filter.FilterLayout.FIELD_H;
import static io.github.matheusanbs.wirelessautomate.client.filter.FilterLayout.IW;
import static io.github.matheusanbs.wirelessautomate.client.filter.FilterLayout.SUGGEST_MORE_H;
import static io.github.matheusanbs.wirelessautomate.client.filter.FilterLayout.SUGGEST_ROW;
import static io.github.matheusanbs.wirelessautomate.client.filter.FilterLayout.SUGGEST_ROWS;
import static io.github.matheusanbs.wirelessautomate.client.filter.FilterText.tr;

import io.github.matheusanbs.wirelessautomate.client.GuiPaint;
import io.github.matheusanbs.wirelessautomate.client.GuiText;
import io.github.matheusanbs.wirelessautomate.client.filter.FilterScreen.Tab;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

/**
 * A lista de sugestões de um campo de texto livre da aba Regra ("Só em" e "Componente"), no mesmo desenho e
 * com as mesmas teclas da do encantamento ({@link EnchantPicker}), só que o texto do campo continua sendo o
 * que o jogador digita: escolher uma sugestão troca o texto, e a {@link Source} decide o que listar a partir
 * dele. Aberta por cima das linhas de baixo, ou das de cima quando não cabe na janela.
 */
final class TextSuggest {
    /** Uma sugestão: o texto que vai para o campo, o que se lê na linha, a nota em cinza e se a escolha já reabre a lista. */
    record Suggestion(String insert, String label, @Nullable String note, boolean reopen) {}

    interface Source {
        List<Suggestion> find(String text);
    }

    private final FilterScreen screen;
    private final FilterFrame frame;
    private final Supplier<EditBox> boxSupplier;
    private final Source source;

    private boolean suggesting;
    private List<Suggestion> suggestions = List.of();
    private int selected;
    private int top;
    /** Texto posto pela própria lista no campo: não reabre nem refaz a lista. */
    private boolean quiet;

    TextSuggest(FilterScreen screen, Supplier<EditBox> box, Source source) {
        this.screen = screen;
        this.frame = screen.frame();
        this.boxSupplier = box;
        this.source = source;
    }

    private static Font font() {
        return Minecraft.getInstance().font;
    }

    private EditBox box() {
        return boxSupplier.get();
    }

    /** O jogador digitou: refaz a lista pelo texto. */
    void typed() {
        if (!quiet) {
            open();
        }
    }

    private void open() {
        EditBox box = box();
        if (box == null || !box.visible) {
            suggesting = false;
            return;
        }
        suggestions = source.find(box.getValue());
        selected = 0;
        top = 0;
        suggesting = !suggestions.isEmpty();
    }

    void close() {
        suggesting = false;
    }

    boolean shown() {
        EditBox box = box();
        return suggesting && screen.tab() == Tab.RULE && box != null && box.visible && !suggestions.isEmpty();
    }

    private void pick(Suggestion s) {
        quiet = true;
        try {
            box().setValue(s.insert());
        } finally {
            quiet = false;
        }
        suggesting = false;
        if (s.reopen()) {
            open();
        }
    }

    private void move(int direction) {
        selected = Math.max(0, Math.min(suggestions.size() - 1, selected + direction));
        if (selected < top) {
            top = selected;
        } else if (selected >= top + SUGGEST_ROWS) {
            top = selected - SUGGEST_ROWS + 1;
        }
    }

    // geometria

    private int x() {
        return frame.left() + frame.ix();
    }

    private int visible() {
        return Math.min(SUGGEST_ROWS, suggestions.size());
    }

    private int height() {
        return visible() * SUGGEST_ROW + 2 + (suggestions.size() > SUGGEST_ROWS ? SUGGEST_MORE_H : 0);
    }

    private int y() {
        int below = box().getY() - 2 + FIELD_H - 1;
        return below + height() > screen.height - 2 ? box().getY() - 1 - height() : below;
    }

    private boolean over(double mx, double my) {
        return shown() && mx >= x() && mx < x() + IW && my >= y() && my < y() + height();
    }

    private int at(double mx, double my) {
        if (!over(mx, my)) {
            return -1;
        }
        int row = (int) Math.floor((my - y() - 1) / SUGGEST_ROW);
        return row >= 0 && row < visible() ? top + row : -1;
    }

    // eventos

    void focusChanged(@Nullable GuiEventListener before, @Nullable GuiEventListener listener) {
        if (before != null && before == box()) {
            close();
        }
        if (listener != null && listener == box()) {
            box().moveCursorToEnd(false);
            box().setHighlightPos(0);
            open();
        }
    }

    boolean mouseClicked(double mx, double my, int button) {
        if (shown()) {
            if (over(mx, my)) {
                int index = at(mx, my);
                if (index >= 0 && button == 0) {
                    pick(suggestions.get(index));
                }
                return true;
            }
            if (!box().isMouseOver(mx, my)) {
                close();
            }
        }
        return false;
    }

    /** Clicar no campo já focado reabre a lista. */
    void reopenOnClick(double mx, double my) {
        if (screen.getFocused() == box() && box().isMouseOver(mx, my) && !suggesting) {
            open();
        }
    }

    boolean keyPressed(EditBox focused, int keyCode, boolean enter) {
        if (focused != box()) {
            return false;
        }
        boolean arrow = keyCode == GLFW.GLFW_KEY_UP || keyCode == GLFW.GLFW_KEY_DOWN;
        if (shown()) {
            if (arrow) {
                move(keyCode == GLFW.GLFW_KEY_DOWN ? 1 : -1);
                return true;
            }
            if (enter || keyCode == GLFW.GLFW_KEY_TAB) {
                pick(suggestions.get(selected));
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                close();
                return true;
            }
        } else if (arrow) {
            open();
            return true;
        }
        return false;
    }

    boolean scroll(double mx, double my, int step) {
        if (over(mx, my)) {
            top = Math.max(0, Math.min(suggestions.size() - SUGGEST_ROWS, top + step));
            return true;
        }
        return false;
    }

    boolean tooltip(int mx, int my) {
        if (!over(mx, my)) {
            return false;
        }
        int index = at(mx, my);
        if (index >= 0) {
            Suggestion s = suggestions.get(index);
            List<net.minecraft.util.FormattedCharSequence> lines = new java.util.ArrayList<>();
            lines.add(Component.literal(s.label()).getVisualOrderText());
            if (s.note() != null) {
                lines.add(Component.literal(s.note()).withColor(GuiPaint.DISABLED).getVisualOrderText());
            }
            screen.setTooltipForNextRenderPass(lines);
        }
        return true;
    }

    // desenho

    void render(GuiGraphics g, int mx, int my) {
        if (!shown()) {
            return;
        }
        int x = x();
        int y = y();
        int h = height();
        int hover = at(mx, my);
        g.pose().pushPose();
        g.pose().translate(0, 0, 400);
        g.fill(x + 1, y + h, x + IW + 1, y + h + 2, GuiPaint.BEVEL_DARK);
        GuiPaint.box(g, x, y, IW, h, GuiPaint.INSET, GuiPaint.ACCENT);
        Font font = font();
        for (int i = 0; i < visible(); i++) {
            int index = top + i;
            Suggestion s = suggestions.get(index);
            int ry = y + 1 + i * SUGGEST_ROW;
            if (index == selected) {
                g.fill(x + 1, ry, x + IW - 1, ry + SUGGEST_ROW, GuiPaint.mix(GuiPaint.INSET, GuiPaint.ACCENT, 0.22f));
            } else if (index == hover) {
                g.fill(x + 1, ry, x + IW - 1, ry + SUGGEST_ROW, GuiPaint.ROW_HOVER);
            }
            int left = x + 4;
            int right = x + IW - 4;
            int noteW = s.note() == null ? 0 : Math.min(font.width(s.note()), (right - left) / 2);
            int labelW = right - left - noteW - (noteW > 0 ? 6 : 0);
            GuiText.draw(g, font, Component.literal(s.label()), left, ry + 2, labelW, GuiPaint.FG);
            if (noteW > 0) {
                GuiText.draw(g, font, Component.literal(s.note()), right - noteW, ry + 2, noteW, GuiPaint.DISABLED);
            }
        }
        int more = suggestions.size() - visible();
        if (more > 0) {
            GuiText.draw(g, font, tr("rule.enchantment.more", more), x + 4, y + 1 + visible() * SUGGEST_ROW + 1,
                    IW - 8, GuiPaint.DISABLED);
        }
        g.pose().popPose();
    }
}
