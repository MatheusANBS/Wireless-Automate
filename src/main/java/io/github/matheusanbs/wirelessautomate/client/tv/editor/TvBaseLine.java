package io.github.matheusanbs.wirelessautomate.client.tv.editor;

import io.github.matheusanbs.wirelessautomate.client.GuiPaint;
import io.github.matheusanbs.wirelessautomate.client.GuiText;
import io.github.matheusanbs.wirelessautomate.tv.TvArea;
import java.text.NumberFormat;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/**
 * A barra da base, em cima da miniatura: o tamanho da Tela ("6×3 · 192×96 px") e, à direita, "Não salvo" enquanto o
 * rascunho difere do salvo. No lugar dela, os avisos: o salvar que não valeu (grande demais, sem permissão, rápido
 * demais ou sem resposta), a pixel art que passaria do teto de pixels da Tela e a Tela que mudou no servidor, este com
 * o Recarregar; e, por uns segundos, o aviso do copiar e colar código ({@link TvCodeNotice}). Os textos são medidos no
 * {@link #layout}, o do tamanho quando a área muda e o da pixel art demais quando o número recusado muda (ele diz
 * quanto as artes somariam e o teto, e que os quadros das animadas somam).
 */
final class TvBaseLine {
    private static final int RELOAD_PAD = 10;

    private final TvEditorDoc doc;
    private final int maxKb;
    private final int maxImagePixels;
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
    private GuiText.Fitted notice;
    private int noticeVersion = -1;
    private TvEditorPaint.Label reloadLabel;
    private int shownRefused = -1;
    private GuiText.Fitted refused;

    TvBaseLine(TvEditorDoc doc, int maxKb, int maxImagePixels) {
        this.doc = doc;
        this.maxKb = maxKb;
        this.maxImagePixels = maxImagePixels;
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
                case IMAGES_FULL -> "images_full";
                case PICTURES_FULL -> "pictures_full";
            };
            Object limit = failure == TvEditorDoc.Failure.TOO_BIG ? maxKb
                    : failure == TvEditorDoc.Failure.PICTURES_FULL ? TvImageOps.pictureLimit : maxImagePixels;
            failures[failure.ordinal()] = GuiText.fit(font, TvEditorText.key(key, limit), rect.w());
        }
        noticeVersion = -1;
        shownRefused = -1;
    }

    void render(GuiGraphics g, int mouseX, int mouseY) {
        if (doc.changedOnServer()) {
            GuiText.draw(g, font, changed, rect.x(), rect.y() + 2, GuiPaint.WARN);
            TvEditorPaint.button(g, font, reload, reloadLabel, reload.contains(mouseX, mouseY), true);
            return;
        }
        TvCodeNotice.Kind kind = TvCodeNotice.current();
        if (kind != null) {
            if (noticeVersion != TvCodeNotice.version()) {
                noticeVersion = TvCodeNotice.version();
                notice = GuiText.fit(font, TvCodeNotice.text(), rect.w());
            }
            GuiText.draw(g, font, notice, rect.x(), rect.y() + 2, switch (kind) {
                case COPIED, PICTURE_SENT -> GuiPaint.OK;
                case PICTURE_SENDING -> GuiPaint.MUTED;
                case BINDINGS_DROPPED, PICTURES_BY_REFERENCE -> GuiPaint.WARN;
                default -> GuiPaint.DANGER;
            });
            return;
        }
        TvEditorDoc.Failure failure = doc.failure();
        if (failure == TvEditorDoc.Failure.IMAGES_FULL && TvImageOps.refusedPixels > 0) {
            if (shownRefused != TvImageOps.refusedPixels) {
                shownRefused = TvImageOps.refusedPixels;
                refused = GuiText.fit(font, TvEditorText.key("images_full.count", grouped(shownRefused),
                        grouped(maxImagePixels)), rect.w());
            }
            GuiText.draw(g, font, refused, rect.x(), rect.y() + 2, GuiPaint.DANGER);
            return;
        }
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

    /** O número com o separador de milhar do idioma do jogo ("98.304", "98,304"). */
    private static String grouped(int value) {
        String code = Minecraft.getInstance().getLanguageManager().getSelected();
        return NumberFormat.getIntegerInstance(Locale.forLanguageTag(code.replace('_', '-'))).format(value);
    }
}
