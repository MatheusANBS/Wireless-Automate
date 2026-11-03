package io.github.matheusanbs.wirelessautomate.client.tablet.kit;

import io.github.matheusanbs.wirelessautomate.client.GuiPaint;
import io.github.matheusanbs.wirelessautomate.client.PanelPaint;
import io.github.matheusanbs.wirelessautomate.command.WaCommand;
import java.util.function.Consumer;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * O nome editável sobre o título do cabeçalho: um {@link EditBox} sem borda, criado como widget da tela e invisível
 * fora da edição. {@link #start} o põe sobre o título com o texto todo selecionado (digitar troca); {@link #finish}
 * o esconde e, só com {@code commit}, entrega o texto a quem agirá.
 */
public final class TitleEditor {
    private final Consumer<String> onCommit;
    private @Nullable EditBox box;
    private int x;
    private int y;
    private int w;

    public TitleEditor(Consumer<String> onCommit) {
        this.onCommit = onCommit;
    }

    /** O campo, refeito a cada {@code init}; a tela o põe entre os widgets dela. */
    public EditBox createBox(Font font) {
        EditBox created = new EditBox(font, 0, 0, 10, 9, Component.empty());
        created.setBordered(false);
        created.setTextShadow(false);
        created.setMaxLength(WaCommand.MAX_NAME_LENGTH);
        created.setVisible(false);
        created.active = false;
        box = created;
        return created;
    }

    public void start(String text, int x, int y, int w) {
        if (box == null) {
            return;
        }
        this.x = x;
        this.y = y;
        this.w = w;
        box.setX(x);
        box.setY(y);
        box.setWidth(w);
        box.setValue(text);
        box.moveCursorToEnd(false);
        box.setHighlightPos(0);
        GuiPaint.ink(box);
        box.setVisible(true);
        box.active = true;
        box.setFocused(true);
    }

    public void finish(boolean commit) {
        if (box == null || !box.visible) {
            return;
        }
        String text = box.getValue();
        box.setVisible(false);
        box.active = false;
        box.setFocused(false);
        if (commit) {
            onCommit.accept(text);
        }
    }

    public boolean editing() {
        return box != null && box.visible;
    }

    public @Nullable EditBox box() {
        return box;
    }

    /** A moldura do campo em edição; a tela a chama antes de desenhar os widgets. */
    public void renderFrame(GuiGraphics g) {
        if (editing()) {
            PanelPaint.field(g, x - 3, y - 3, w + 6, 14, true);
        }
    }
}
