package io.github.matheusanbs.wirelessautomate.client.color;

import io.github.matheusanbs.wirelessautomate.client.GuiPaint;
import io.github.matheusanbs.wirelessautomate.client.GuiThemes;
import io.github.matheusanbs.wirelessautomate.client.PanelPaint;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/**
 * O campo hexadecimal do {@link ColorPopup}: um {@link EditBox} sem borda dentro da moldura do mod (não é widget da
 * tela: a janela repassa mouse, teclas e letras). Cada letra válida já chama {@code change}; Enter, Esc e o clique fora
 * soltam o foco e refazem o texto a partir do valor, que sem foco só é relido no {@link #refresh}.
 */
final class HexField {
    private final EditBox box;
    private final int x;
    private final int y;
    private final int w;
    private final int h;
    private final boolean framed;
    private final Component hint = Component.literal("#RRGGBB");
    private final Supplier<String> get;
    private boolean syncing;
    private int inkVersion = -1;

    HexField(Font font, int x, int y, int w, int h, boolean framed, Supplier<String> get, Consumer<String> change) {
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
        this.framed = framed;
        this.get = get;
        box = new EditBox(font, 0, 0, w - (framed ? 8 : 6), 8, hint);
        box.setBordered(false);
        box.setMaxLength(9);
        box.setTextShadow(false);
        box.setFilter(text -> text.matches("#?[0-9a-fA-F]*"));
        box.setResponder(text -> {
            if (!syncing) {
                change.accept(text);
            }
        });
        syncNow();
    }

    private void syncNow() {
        String value = get.get();
        if (!value.equals(box.getValue())) {
            syncing = true;
            box.setValue(value);
            syncing = false;
        }
    }

    boolean contains(double mx, double my) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    void render(GuiGraphics g, int mouseX, int mouseY) {
        if (framed) {
            PanelPaint.field(g, x, y, w, h, box.isFocused());
            box.setX(x + 4);
            box.setY(y + 3);
        } else {
            GuiPaint.box(g, x, y, w, h, GuiPaint.INSET, box.isFocused() ? GuiPaint.ACCENT : GuiPaint.BUTTON_BORDER);
            box.setX(x + 3);
            box.setY(y + 2);
        }
        if (inkVersion != GuiThemes.version()) {
            inkVersion = GuiThemes.version();
            GuiPaint.ink(box, hint);
        }
        box.render(g, mouseX, mouseY, 0f);
    }

    void mouseClicked(double mx, double my, int button) {
        box.setFocused(true);
        box.mouseClicked(mx, my, button);
    }

    boolean wantsKeys() {
        return box.isFocused();
    }

    void blur() {
        box.setFocused(false);
        syncNow();
    }

    /** Relê o texto do valor, se o campo não estiver com o foco (quem digita não é interrompido). */
    void refresh() {
        if (!box.isFocused()) {
            syncNow();
        }
    }

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

    boolean charTyped(char c, int modifiers) {
        if (!box.isFocused()) {
            return false;
        }
        box.charTyped(c, modifiers);
        return true;
    }

    /** Dá o foco ao campo e leva o cursor ao fim (o gancho do e2e). */
    void focus() {
        box.setFocused(true);
        box.moveCursorToEnd(false);
    }

    int centerX() {
        return x + w / 2;
    }

    int centerY() {
        return y + h / 2;
    }
}
