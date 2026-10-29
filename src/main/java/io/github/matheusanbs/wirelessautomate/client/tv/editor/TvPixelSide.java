package io.github.matheusanbs.wirelessautomate.client.tv.editor;

import io.github.matheusanbs.wirelessautomate.client.ArmedClick;
import io.github.matheusanbs.wirelessautomate.client.GuiPaint;
import io.github.matheusanbs.wirelessautomate.client.GuiText;
import io.github.matheusanbs.wirelessautomate.client.tv.pixel.TvPixelCanvas;
import io.github.matheusanbs.wirelessautomate.client.tv.pixel.TvPixelSession;
import io.github.matheusanbs.wirelessautomate.tv.TvColor;
import io.github.matheusanbs.wirelessautomate.tv.TvPixels;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * A coluna da direita do editor de pixel art: a paleta de 16 em 4 × 4, com um vão entre as linhas que mostra as
 * rampas (decisão 1c da spec da etapa 4) e a cor 0 em xadrez; a cor escolhida com o hexadecimal; "Editar cor" (ou dois
 * cliques numa cor), que abre o {@link TvColorPopup} sem a linha do tema, e "Criar rampa"; a {@link TvPixelPreview};
 * e embaixo Cancelar e Salvar. Também cuida do Limpar tudo, que fica no pé da coluna de ferramentas
 * ({@link TvPixelLayout#clear}). Limpar tudo e o Cancelar com mudança não salva pedem dois cliques. A amostra de cada
 * cor é o xadrez da {@link TvPixelTexture} com a cor por cima.
 */
final class TvPixelSide {
    /** Um botão da coluna. */
    enum Button { EDIT_COLOR, RAMP, CLEAR, CANCEL, SAVE }

    private static final int SWATCH_H = 12;
    private static final int ROW_GAP = 4;
    private static final int COL_GAP = 2;
    private static final int BUTTON_H = 14;
    private static final long DOUBLE_CLICK_MS = 400;

    private final TvPixelSession session;
    private final TvPixelTexture texture;
    private final TvPixelPreview preview;
    private final ArmedClick cancelClick = new ArmedClick();
    private final ArmedClick clearClick = new ArmedClick();
    private final TvRect[] swatches = new TvRect[TvPixels.COLORS];
    private final TvRect[] buttons = new TvRect[Button.values().length];
    private Font font;
    private GuiText.Fitted paletteLabel;
    private GuiText.Fitted previewLabel;
    private int paletteY;
    private int previewY;
    private TvRect current = TvRect.NONE;
    private TvEditorPaint.Label editLabel;
    private TvEditorPaint.Label rampLabel;
    private TvEditorPaint.Label clearLabel;
    private TvEditorPaint.Label cancelLabel;
    private TvEditorPaint.Label discardLabel;
    private TvEditorPaint.Label saveLabel;
    private Component rampTip = Component.empty();
    private Component clearTip = Component.empty();
    private Component clearConfirmTip = Component.empty();
    private int shownIndex = -1;
    private int shownColor;
    private GuiText.Fitted hexText;
    private int lastClicked = -1;
    private long lastClickAt;

    TvPixelSide(TvPixelSession session, TvPixelTexture texture) {
        this.session = session;
        this.texture = texture;
        this.preview = new TvPixelPreview(texture);
    }

    void layout(Font font, TvRect side, TvRect clear) {
        this.font = font;
        int x = side.x();
        int w = side.w();
        int y = side.y();
        paletteLabel = GuiText.fit(font, TvEditorText.key("pixel.palette"), w);
        paletteY = y;
        y += 11;
        int cw = (w - 3 * COL_GAP) / 4;
        for (int i = 0; i < TvPixels.COLORS; i++) {
            swatches[i] = new TvRect(x + i % 4 * (cw + COL_GAP), y + i / 4 * (SWATCH_H + ROW_GAP), cw, SWATCH_H);
        }
        y += 4 * SWATCH_H + 3 * ROW_GAP + 5;
        current = new TvRect(x, y, 14, 14);
        y += 17;
        int half = (w - 4) / 2;
        Component edit = TvEditorText.key("pixel.edit_color");
        Component ramp = TvEditorText.key("pixel.ramp");
        int editW = Math.max(30, Math.min(w - 34, (w - 4) * (font.width(edit) + 8)
                / (font.width(edit) + font.width(ramp) + 16)));
        buttons[Button.EDIT_COLOR.ordinal()] = new TvRect(x, y, editW, BUTTON_H);
        buttons[Button.RAMP.ordinal()] = new TvRect(x + editW + 4, y, w - editW - 4, BUTTON_H);
        y += BUTTON_H + 5;
        previewLabel = GuiText.fit(font, TvEditorText.key("pixel.preview"), w);
        previewY = y;
        y += 11;
        int actionsY = side.bottom() - BUTTON_H;
        buttons[Button.CLEAR.ordinal()] = clear;
        buttons[Button.CANCEL.ordinal()] = new TvRect(x, actionsY, half, BUTTON_H);
        buttons[Button.SAVE.ordinal()] = new TvRect(x + w - half, actionsY, half, BUTTON_H);
        preview.layout(new TvRect(x + 1, y + 1, w - 2, Math.max(0, actionsY - 5 - y)));
        editLabel = TvEditorPaint.label(font, edit, editW - 4);
        rampLabel = TvEditorPaint.label(font, ramp, w - editW - 8);
        clearLabel = TvEditorPaint.label(font, TvEditorText.key("pixel.clear"), clear.w() - 4);
        clearTip = TvEditorText.key("pixel.clear.tip");
        clearConfirmTip = TvEditorText.key("pixel.clear.confirm");
        cancelLabel = TvEditorPaint.label(font, TvEditorText.key("pixel.cancel"), half - 4);
        discardLabel = TvEditorPaint.label(font, TvEditorText.key("pixel.discard"), half - 4);
        saveLabel = TvEditorPaint.label(font, TvEditorText.key("pixel.save"), half - 4);
        rampTip = TvEditorText.key("pixel.ramp.tip");
        shownIndex = -1;
    }

    void render(GuiGraphics g, int mouseX, int mouseY) {
        TvPixelCanvas canvas = session.canvas();
        GuiText.draw(g, font, paletteLabel, swatches[0].x(), paletteY, GuiPaint.MUTED);
        for (int i = 0; i < TvPixels.COLORS; i++) {
            swatch(g, swatches[i], canvas.color(i));
            boolean chosen = i == session.current();
            if (chosen) {
                GuiPaint.outline(g, swatches[i].x() - 1, swatches[i].y() - 1, swatches[i].w() + 2, swatches[i].h() + 2,
                        GuiPaint.ACCENT);
                GuiPaint.outline(g, swatches[i].x() - 2, swatches[i].y() - 2, swatches[i].w() + 4, swatches[i].h() + 4,
                        GuiPaint.ACCENT);
            } else if (swatches[i].contains(mouseX, mouseY)) {
                GuiPaint.outline(g, swatches[i].x() - 1, swatches[i].y() - 1, swatches[i].w() + 2, swatches[i].h() + 2,
                        GuiPaint.BUTTON_HOVER_BORDER);
            }
        }
        int index = session.current();
        int color = canvas.color(index);
        if (index != shownIndex || color != shownColor) {
            shownIndex = index;
            shownColor = color;
            Component text = index == 0 ? TvEditorText.key("pixel.transparent")
                    : TvEditorText.key("pixel.color", index, TvColor.hex(color));
            hexText = GuiText.fit(font, text, swatches[3].right() - current.right() - 4);
        }
        swatch(g, current, color);
        GuiText.draw(g, font, hexText, current.right() + 4, current.y() + 3, GuiPaint.FG);
        TvRect edit = buttons[Button.EDIT_COLOR.ordinal()];
        TvEditorPaint.button(g, font, edit, editLabel, edit.contains(mouseX, mouseY), index != 0);
        TvRect ramp = buttons[Button.RAMP.ordinal()];
        TvEditorPaint.button(g, font, ramp, rampLabel, ramp.contains(mouseX, mouseY), index != 0);
        GuiText.draw(g, font, previewLabel, swatches[0].x(), previewY, GuiPaint.MUTED);
        preview.render(g, canvas);
        TvRect clear = buttons[Button.CLEAR.ordinal()];
        if (clearClick.armed(System.currentTimeMillis())) {
            TvEditorPaint.danger(g, font, clear, clearLabel);
        } else {
            TvEditorPaint.button(g, font, clear, clearLabel, clear.contains(mouseX, mouseY), true);
        }
        TvRect cancel = buttons[Button.CANCEL.ordinal()];
        if (cancelArmed()) {
            TvEditorPaint.danger(g, font, cancel, discardLabel);
        } else {
            TvEditorPaint.button(g, font, cancel, cancelLabel, cancel.contains(mouseX, mouseY), true);
        }
        TvRect save = buttons[Button.SAVE.ordinal()];
        TvEditorPaint.primary(g, font, save, saveLabel, save.contains(mouseX, mouseY), true, GuiPaint.ACCENT);
    }

    /** Limpar tudo: o primeiro clique arma e o segundo, dentro do prazo, devolve que pode limpar. */
    boolean clear() {
        return clearClick.click(System.currentTimeMillis());
    }

    /** Uma amostra: o xadrez e a cor por cima (a 0 fica só o xadrez), com a borda. */
    private void swatch(GuiGraphics g, TvRect r, int color) {
        texture.drawChecker(g, r.x(), r.y(), r.right(), r.bottom(), r.x(), r.y(), 3);
        if (color >>> 24 != 0) {
            g.fill(r.x(), r.y(), r.right(), r.bottom(), color);
        }
        GuiPaint.outline(g, r.x(), r.y(), r.w(), r.h(), GuiPaint.BUTTON_BORDER);
    }

    boolean cancelArmed() {
        return cancelClick.armed(System.currentTimeMillis());
    }

    /** O Cancelar (ou o Esc): sem mudança, ou no segundo clique, devolve que pode fechar. */
    boolean cancel() {
        return !session.dirty() || cancelClick.click(System.currentTimeMillis());
    }

    /**
     * O clique na coluna: a cor (dois cliques abrem o seletor), os botões ou a prévia. Devolve o botão apertado, ou
     * {@code null} se o clique foi numa cor, na prévia ou fora.
     */
    @Nullable Button mouseClicked(double mouseX, double mouseY, TvPixelBoard board) {
        for (int i = 0; i < TvPixels.COLORS; i++) {
            if (swatches[i].contains(mouseX, mouseY)) {
                long now = System.currentTimeMillis();
                boolean twice = i == lastClicked && now - lastClickAt < DOUBLE_CLICK_MS;
                lastClicked = i;
                lastClickAt = now;
                session.choose(i);
                return twice && i != 0 ? Button.EDIT_COLOR : null;
            }
        }
        int[] cell = preview.cellAt(mouseX, mouseY, session.canvas());
        if (cell != null) {
            session.view().centerOn(cell[0], cell[1], board.box().w(), board.box().h());
            return null;
        }
        for (Button button : Button.values()) {
            if (buttons[button.ordinal()].contains(mouseX, mouseY)) {
                boolean colorButton = button == Button.EDIT_COLOR || button == Button.RAMP;
                if (button != Button.CANCEL) {
                    cancelClick.disarm();
                }
                if (button != Button.CLEAR) {
                    clearClick.disarm();
                }
                return colorButton && session.current() == 0 ? null : button;
            }
        }
        return null;
    }

    /** O retângulo da paleta, onde o seletor de cor se encosta. */
    TvRect paletteBox() {
        return new TvRect(swatches[0].x(), swatches[0].y(), swatches[3].right() - swatches[0].x(),
                swatches[15].bottom() - swatches[0].y());
    }

    @Nullable Component tooltip(double mouseX, double mouseY) {
        if (buttons[Button.RAMP.ordinal()].contains(mouseX, mouseY)) {
            return rampTip;
        }
        if (buttons[Button.CLEAR.ordinal()].contains(mouseX, mouseY)) {
            return clearClick.armed(System.currentTimeMillis()) ? clearConfirmTip : clearTip;
        }
        return null;
    }

    int[] swatchCenter(int index) {
        return swatches[index].center();
    }

    int[] center(Button button) {
        return buttons[button.ordinal()].center();
    }

    int[] previewCenter(int which) {
        return preview.center(which);
    }
}
