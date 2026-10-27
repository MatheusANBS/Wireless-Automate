package io.github.matheusanbs.wirelessautomate.client.tv.editor;

import io.github.matheusanbs.wirelessautomate.client.GuiPaint;
import io.github.matheusanbs.wirelessautomate.client.GuiText;
import io.github.matheusanbs.wirelessautomate.tv.TvArea;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/**
 * A barra da base, em cima da miniatura: o tamanho da Tela ("6×3 · 192×96 px") e, à direita, "Não salvo" enquanto o
 * rascunho difere do salvo. No lugar dela, os avisos: o layout grande demais para salvar e a Tela que mudou no
 * servidor, este com o Recarregar. Os textos são medidos no {@link #layout} e o do tamanho quando a área muda.
 */
final class TvBaseLine {
    private static final int RELOAD_PAD = 10;

    private final TvEditorDoc doc;
    private TvRect rect = TvRect.NONE;
    private TvRect reload = TvRect.NONE;
    private Font font;
    private TvArea sizedArea;
    private GuiText.Fitted size;
    private int sizeWidth;
    private GuiText.Fitted unsaved;
    private int unsavedWidth;
    private GuiText.Fitted changed;
    private GuiText.Fitted tooBig;
    private TvEditorPaint.Label reloadLabel;
    private boolean tooBigShown;

    TvBaseLine(TvEditorDoc doc) {
        this.doc = doc;
    }

    void layout(Font font, TvRect rect) {
        this.font = font;
        this.rect = rect;
        sizedArea = null;
        unsaved = GuiText.fit(font, TvEditorText.key("unsaved"), rect.w() / 3);
        unsavedWidth = font.width(unsaved.shown());
        reloadLabel = TvEditorPaint.label(font, TvEditorText.key("reload"), rect.w() / 3);
        int reloadW = reloadLabel.width() + RELOAD_PAD;
        reload = new TvRect(rect.right() - reloadW, rect.y(), reloadW, rect.h());
        changed = GuiText.fit(font, TvEditorText.key("changed"), rect.w() - reloadW - 4);
        tooBig = GuiText.fit(font, TvEditorText.key("too_big"), rect.w());
    }

    /** O layout passou do teto do pacote: o aviso fica até a próxima mudança do rascunho. */
    void showTooBig(boolean shown) {
        tooBigShown = shown;
    }

    void render(GuiGraphics g, int mouseX, int mouseY) {
        if (doc.changedOnServer()) {
            GuiText.draw(g, font, changed, rect.x(), rect.y() + 2, GuiPaint.WARN);
            TvEditorPaint.button(g, font, reload, reloadLabel, reload.contains(mouseX, mouseY), true);
            return;
        }
        if (tooBigShown) {
            GuiText.draw(g, font, tooBig, rect.x(), rect.y() + 2, GuiPaint.DANGER);
            return;
        }
        TvArea area = doc.area();
        if (!area.equals(sizedArea)) {
            sizedArea = area;
            size = GuiText.fit(font, TvEditorText.key("size", area.width(), area.height(), area.pixelWidth(),
                    area.pixelHeight()), rect.w() - unsavedWidth - 16);
            sizeWidth = font.width(size.shown());
        }
        GuiPaint.pill(g, rect.x(), rect.y(), sizeWidth + 8, rect.h(), GuiPaint.BUTTON, GuiPaint.BUTTON_BORDER);
        GuiText.draw(g, font, size, rect.x() + 4, rect.y() + 2, GuiPaint.FG);
        if (doc.model().dirty()) {
            GuiText.draw(g, font, unsaved, rect.right() - unsavedWidth, rect.y() + 2, GuiPaint.ACCENT);
        }
    }

    boolean mouseClicked(double mouseX, double mouseY) {
        if (doc.changedOnServer() && reload.contains(mouseX, mouseY)) {
            doc.reload();
            return true;
        }
        return false;
    }
}
