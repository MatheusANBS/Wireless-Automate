package io.github.matheusanbs.wirelessautomate.client.tv.editor;

import io.github.matheusanbs.wirelessautomate.client.GuiPaint;
import io.github.matheusanbs.wirelessautomate.client.GuiText;
import io.github.matheusanbs.wirelessautomate.tv.TvArea;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/**
 * A barra da base, em cima da miniatura: o tamanho da Tela ("6×3 · 192×96 px") e, à direita, "Não salvo" enquanto o
 * rascunho difere do salvo. No lugar dela, os avisos: o salvar que não valeu (grande demais, sem permissão, rápido
 * demais ou sem resposta) e a Tela que mudou no servidor, este com o Recarregar. Os textos são medidos no {@link #layout} e o do tamanho quando a área muda.
 */
final class TvBaseLine {
    private static final int RELOAD_PAD = 10;

    private final TvEditorDoc doc;
    private final int maxKb;
    private TvRect rect = TvRect.NONE;
    private TvRect reload = TvRect.NONE;
    private Font font;
    private TvArea sizedArea;
    private GuiText.Fitted size;
    private int sizeWidth;
    private GuiText.Fitted unsaved;
    private int unsavedWidth;
    private GuiText.Fitted changed;
    private final GuiText.Fitted[] failures = new GuiText.Fitted[TvEditorDoc.Failure.values().length];
    private TvEditorPaint.Label reloadLabel;

    TvBaseLine(TvEditorDoc doc, int maxKb) {
        this.doc = doc;
        this.maxKb = maxKb;
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
        for (TvEditorDoc.Failure failure : TvEditorDoc.Failure.values()) {
            String key = switch (failure) {
                case TOO_BIG -> "too_big";
                case DENIED -> "not_saved_denied";
                case THROTTLED -> "not_saved_throttled";
                case TIMEOUT -> "not_saved_timeout";
            };
            failures[failure.ordinal()] = GuiText.fit(font, failure == TvEditorDoc.Failure.TOO_BIG
                    ? TvEditorText.key(key, maxKb) : TvEditorText.key(key), rect.w());
        }
    }

    void render(GuiGraphics g, int mouseX, int mouseY) {
        if (doc.changedOnServer()) {
            GuiText.draw(g, font, changed, rect.x(), rect.y() + 2, GuiPaint.WARN);
            TvEditorPaint.button(g, font, reload, reloadLabel, reload.contains(mouseX, mouseY), true);
            return;
        }
        TvEditorDoc.Failure failure = doc.failure();
        if (failure != null) {
            GuiText.draw(g, font, failures[failure.ordinal()], rect.x(), rect.y() + 2, GuiPaint.DANGER);
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
