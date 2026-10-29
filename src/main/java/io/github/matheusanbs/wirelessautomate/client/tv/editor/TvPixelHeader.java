package io.github.matheusanbs.wirelessautomate.client.tv.editor;

import io.github.matheusanbs.wirelessautomate.client.GuiPaint;
import io.github.matheusanbs.wirelessautomate.client.GuiText;
import io.github.matheusanbs.wirelessautomate.client.tv.pixel.TvPixelSession;
import io.github.matheusanbs.wirelessautomate.tv.TvPixelArt;
import io.github.matheusanbs.wirelessautomate.tv.TvPixels;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * O cabeçalho do editor de pixel art, no lugar do título e das abas da tela: "Pixel art", o campo do nome (até
 * {@link TvPixelArt#MAX_NAME}) e o tamanho, largura × altura de {@link TvPixels#MIN_SIDE} a {@link TvPixels#MAX_SIDE}.
 * O tamanho pedido só vale quando o campo para (o foco sai, ou o botão do mouse solta depois do ‹ ›): digitar "32"
 * passaria por "3", que corta a arte. Cada mudança de tamanho é um passo do desfazer.
 */
final class TvPixelHeader {
    private static final int NUMBER_W = 40;
    private static final int TIMES_W = 9;
    private static final Component TIMES = Component.literal("×");

    private final TvPixelSession session;
    private final String[] name;
    private Font font;
    private TvRect box = TvRect.NONE;
    private GuiText.Fitted title;
    private GuiText.Fitted sizeLabel;
    private int sizeLabelX;
    private int timesX;
    private TvTextField nameField;
    private TvNumberField widthField;
    private TvNumberField heightField;
    private int wantedW;
    private int wantedH;
    private int shownW;
    private int shownH;
    private boolean pressed;

    /** {@code name} é a caixa do nome, que o editor lê ao salvar. */
    TvPixelHeader(TvPixelSession session, String[] name) {
        this.session = session;
        this.name = name;
        this.wantedW = session.canvas().width();
        this.wantedH = session.canvas().height();
    }

    void layout(Font font, TvRect box) {
        this.font = font;
        this.box = box;
        title = GuiText.fit(font, TvEditorText.key("pixel.title"), 60);
        int titleW = font.width(title.shown());
        int right = box.right();
        int heightX = right - NUMBER_W;
        timesX = heightX - TIMES_W;
        int widthX = timesX - NUMBER_W;
        sizeLabel = GuiText.fit(font, TvEditorText.key("pixel.size"), 60);
        sizeLabelX = widthX - 4 - font.width(sizeLabel.shown());
        int nameX = box.x() + titleW + 6;
        int nameW = Math.max(40, Math.min(170, sizeLabelX - 8 - nameX));
        nameField = new TvTextField(font, "pixel.name", 0, 0, nameW, TvPixelArt.MAX_NAME, null,
                TvEditorText.key("pixel.name"), () -> name[0], value -> name[0] = value);
        nameField.place(nameX, box.y());
        widthField = new TvNumberField(font, "pixel.width", 0, 0, NUMBER_W, TvPixels.MIN_SIDE, TvPixels.MAX_SIDE, 1,
                () -> wantedW, value -> wantedW = value);
        widthField.place(widthX, box.y());
        heightField = new TvNumberField(font, "pixel.height", 0, 0, NUMBER_W, TvPixels.MIN_SIDE, TvPixels.MAX_SIDE, 1,
                () -> wantedH, value -> wantedH = value);
        heightField.place(heightX, box.y());
    }

    void render(GuiGraphics g, int mouseX, int mouseY) {
        int w = session.canvas().width();
        int h = session.canvas().height();
        if (w != shownW || h != shownH) {
            shownW = w;
            shownH = h;
            if (!widthField.wantsKeys() && !heightField.wantsKeys() && !pressed) {
                wantedW = w;
                wantedH = h;
                widthField.refresh();
                heightField.refresh();
            }
        }
        GuiText.draw(g, font, title, box.x(), box.y() + 2, GuiPaint.FG);
        nameField.render(g, mouseX, mouseY);
        GuiText.draw(g, font, sizeLabel, sizeLabelX, box.y() + 2, GuiPaint.MUTED);
        widthField.render(g, mouseX, mouseY);
        GuiPaint.text(g, font, TIMES, timesX + 2, box.y() + 2, GuiPaint.MUTED);
        heightField.render(g, mouseX, mouseY);
    }

    boolean mouseClicked(double mouseX, double mouseY) {
        boolean name = nameField.mouseClicked(mouseX, mouseY, 0);
        if (!name) {
            nameField.blur();
        }
        boolean width = widthField.mouseClicked(mouseX, mouseY, 0);
        if (!width) {
            widthField.blur();
        }
        boolean height = heightField.mouseClicked(mouseX, mouseY, 0);
        if (!height) {
            heightField.blur();
        }
        pressed = width || height;
        applyIfIdle();
        return name || width || height;
    }

    boolean mouseDragged(double mouseX, double mouseY) {
        if (!pressed) {
            return false;
        }
        widthField.mouseDragged(mouseX, mouseY);
        heightField.mouseDragged(mouseX, mouseY);
        return true;
    }

    boolean mouseReleased(double mouseX, double mouseY) {
        if (!pressed) {
            return false;
        }
        widthField.mouseReleased(mouseX, mouseY);
        heightField.mouseReleased(mouseX, mouseY);
        pressed = false;
        applyIfIdle();
        return true;
    }

    boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        boolean used = widthField.mouseScrolled(mouseX, mouseY, delta) || heightField.mouseScrolled(mouseX, mouseY,
                delta);
        applyIfIdle();
        return used;
    }

    /** Um campo com o teclado: a tecla é dele. */
    boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        boolean used = nameField.keyPressed(keyCode, scanCode, modifiers)
                || widthField.keyPressed(keyCode, scanCode, modifiers)
                || heightField.keyPressed(keyCode, scanCode, modifiers);
        applyIfIdle();
        return used;
    }

    boolean charTyped(char c, int modifiers) {
        return nameField.charTyped(c, modifiers) || widthField.charTyped(c, modifiers)
                || heightField.charTyped(c, modifiers);
    }

    boolean textFocused() {
        return nameField.wantsKeys() || widthField.wantsKeys() || heightField.wantsKeys();
    }

    /** Solta o foco dos campos (o tamanho digitado vale). */
    void blur() {
        nameField.blur();
        widthField.blur();
        heightField.blur();
        applyIfIdle();
    }

    /** O tamanho pedido vira o da arte, se nenhum campo está no meio de uma mudança. */
    private void applyIfIdle() {
        if (pressed || widthField.wantsKeys() || heightField.wantsKeys()) {
            return;
        }
        if (wantedW != session.canvas().width() || wantedH != session.canvas().height()) {
            session.resize(wantedW, wantedH);
        }
    }

    // ------------------------------------------------------------------ ganchos do e2e

    void focusName() {
        nameField.focus();
    }

    int[] widthArrow(boolean right) {
        return widthField.arrowCenter(right);
    }

    int[] heightArrow(boolean right) {
        return heightField.arrowCenter(right);
    }
}
