package io.github.matheusanbs.wirelessautomate.client.tv.editor;

import io.github.matheusanbs.wirelessautomate.client.GuiPaint;
import net.minecraft.client.gui.GuiGraphics;
import org.jetbrains.annotations.Nullable;

/**
 * Arrastar uma arte (ou uma imagem enviada, {@link TvImageSource}) da aba Artes até a miniatura: depois de
 * {@link #DRAG_START} px, a arte vai sob o mouse no tamanho que a Imagem terá (a escala do {@link TvImageFit} vezes a
 * escala da miniatura), com a borda de destaque sobre a miniatura e apagada fora dela; soltar na miniatura cria a
 * Imagem pelo {@link TvImageOps} (uma mudança do desfazer), soltar fora não faz nada. O desenho é um quadrilátero da
 * textura da arte, sem nada medido por quadro.
 */
final class TvArtDrag {
    private static final int DRAG_START = 4;

    private final TvEditorDoc doc;
    private final TvEditorCanvas canvas;
    private final TvEditorActions actions;
    private @Nullable TvImageSource pressed;
    private double pressX;
    private double pressY;
    private boolean dragging;
    /** O tamanho da Imagem que vai nascer, em px de desenho, medido ao apertar. */
    private int boxW;
    private int boxH;

    TvArtDrag(TvEditorDoc doc, TvEditorCanvas canvas, TvEditorActions actions) {
        this.doc = doc;
        this.canvas = canvas;
        this.actions = actions;
    }

    /** O botão apertou numa linha: o arrasto pode começar. */
    void press(TvImageSource art, double mouseX, double mouseY) {
        pressed = art;
        pressX = mouseX;
        pressY = mouseY;
        dragging = false;
        int areaW = doc.area().pixelWidth();
        int areaH = doc.area().pixelHeight();
        int[] box = TvImageFit.box(art.width(), art.height(),
                TvImageFit.scale(art.width(), art.height(), areaW, areaH), areaW, areaH, 0, 0);
        boxW = box[2];
        boxH = box[3];
    }

    boolean mouseDragged(double mouseX, double mouseY) {
        if (pressed == null) {
            return false;
        }
        dragging = dragging || Math.abs(mouseX - pressX) + Math.abs(mouseY - pressY) >= DRAG_START;
        return true;
    }

    /** Solta: com arrasto e na miniatura, a Imagem nasce no ponto. Devolve se havia uma arte apertada. */
    boolean mouseReleased(double mouseX, double mouseY) {
        TvImageSource art = pressed;
        pressed = null;
        if (art == null) {
            return false;
        }
        int[] spot = dragging ? canvas.imagePoint(mouseX, mouseY) : null;
        dragging = false;
        if (spot != null) {
            TvImageOps.insert(doc, actions.maxImagePixels(), art, spot[0], spot[1]);
        }
        return true;
    }

    /** A arte arrastada, por cima de tudo, no tamanho que terá na miniatura. */
    void renderGhost(GuiGraphics g, int mouseX, int mouseY) {
        TvImageSource art = pressed;
        if (!dragging || art == null) {
            return;
        }
        boolean over = canvas.imagePoint(mouseX, mouseY) != null;
        float gui = canvas.guiScale();
        int w = Math.max(4, Math.round(boxW * gui));
        int h = Math.max(4, Math.round(boxH * gui));
        int x = mouseX - w / 2;
        int y = mouseY - h / 2;
        g.pose().pushPose();
        g.pose().translate(0, 0, 400);
        art.draw(g, x, y, w, h);
        GuiPaint.outline(g, x - 1, y - 1, w + 2, h + 2, over ? GuiPaint.ACCENT : GuiPaint.DISABLED);
        g.pose().popPose();
    }
}
