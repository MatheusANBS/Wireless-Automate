package io.github.matheusanbs.wirelessautomate.client.tv.editor;

import io.github.matheusanbs.wirelessautomate.client.GuiPaint;
import io.github.matheusanbs.wirelessautomate.client.GuiThemes;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

/**
 * Um campo de texto da coluna de propriedades: um {@link EditBox} sem borda dentro da moldura do mod (não é widget da
 * tela: o painel repassa mouse, teclas e letras). Cada letra digitada já chama {@code change}, para a miniatura
 * acompanhar; Enter, Esc e o clique fora soltam o foco e refazem o texto a partir do valor. Sem o foco, o texto só é
 * relido no {@link #refresh} (o rascunho mudou), para não brigar com quem digita.
 */
class TvTextField extends TvControl {
    static final int H = 11;

    private final EditBox box;
    private final Supplier<String> get;
    private final Consumer<String> change;
    private final Component hint;
    private boolean syncing;
    private int inkVersion = -1;

    TvTextField(Font font, String id, int x, int y, int w, int maxLength, @Nullable Predicate<String> filter,
            Component hint, Supplier<String> get, Consumer<String> change) {
        super(id, x, y, w, H);
        this.get = get;
        this.change = change;
        this.hint = hint;
        box = new EditBox(font, 0, 0, w - 6, 8, hint);
        box.setBordered(false);
        box.setMaxLength(maxLength);
        box.setTextShadow(false);
        if (filter != null) {
            box.setFilter(filter);
        }
        box.setResponder(text -> {
            if (!syncing) {
                this.change.accept(text);
            }
        });
        syncNow();
    }

    /** O texto volta a ser o do valor, tenha o foco ou não. */
    void syncNow() {
        String value = get.get();
        if (!value.equals(box.getValue())) {
            syncing = true;
            box.setValue(value);
            syncing = false;
        }
    }

    @Override
    void refresh() {
        if (!box.isFocused()) {
            syncNow();
        }
    }

    @Override
    void render(GuiGraphics g, int mouseX, int mouseY) {
        GuiPaint.box(g, ax, ay, w, h, GuiPaint.INSET, box.isFocused() ? GuiPaint.ACCENT : GuiPaint.BUTTON_BORDER);
        if (inkVersion != GuiThemes.version()) {
            inkVersion = GuiThemes.version();
            GuiPaint.ink(box, hint);
        }
        box.setX(ax + 3);
        box.setY(ay + 2);
        box.render(g, mouseX, mouseY, 0f);
    }

    @Override
    boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!contains(mouseX, mouseY)) {
            return false;
        }
        box.setFocused(true);
        box.mouseClicked(mouseX, mouseY, button);
        return true;
    }

    @Override
    boolean wantsKeys() {
        return box.isFocused();
    }

    @Override
    void blur() {
        box.setFocused(false);
        syncNow();
    }

    @Override
    boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!box.isFocused()) {
            return false;
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE || keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            blur();
        } else {
            box.keyPressed(keyCode, scanCode, modifiers);
        }
        return true;
    }

    @Override
    boolean charTyped(char c, int modifiers) {
        if (!box.isFocused()) {
            return false;
        }
        box.charTyped(c, modifiers);
        return true;
    }

    /** Dá o foco ao campo e leva o cursor ao fim (os ganchos do e2e). */
    void focus() {
        box.setFocused(true);
        box.moveCursorToEnd(false);
    }

    String value() {
        return box.getValue();
    }
}
