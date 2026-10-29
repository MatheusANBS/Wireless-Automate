package io.github.matheusanbs.wirelessautomate.client.tv.editor;

import io.github.matheusanbs.wirelessautomate.client.GuiPaint;
import io.github.matheusanbs.wirelessautomate.client.GuiText;
import io.github.matheusanbs.wirelessautomate.tv.TvPieceType;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import org.jetbrains.annotations.Nullable;

/**
 * A aba Peças da coluna da esquerda: Valor, Texto, Forma, Ícone, Imagem, Barra, Medidor, Gráfico e Seta. O clique
 * insere uma nova no primeiro lugar livre e a escolhe; arrastar o botão até a miniatura mostra um fantasma do tamanho
 * da peça e soltar põe a peça ali, com o centro no ponto (soltar fora da miniatura cancela). A Imagem nasce com a arte
 * escolhida na aba Pixel art, na escala que cabe ({@link TvImageOps}, e o fantasma é a própria arte); sem arte
 * escolhida, nasce vazia e a seção Peça pede uma. Os textos são medidos no {@link #layout}; o tamanho do fantasma, ao
 * apertar.
 */
final class TvPalettePanel {
    private static final List<TvPieceType> TYPES = List.of(TvPieceType.VALUE, TvPieceType.TEXT, TvPieceType.SHAPE,
            TvPieceType.ICON, TvPieceType.IMAGE, TvPieceType.BAR, TvPieceType.GAUGE, TvPieceType.GRAPH, TvPieceType.ARROW);
    private static final int DRAG_START = 4;

    private final TvEditorDoc doc;
    private final TvEditorCanvas canvas;
    private final TvEditorActions actions;
    private final Supplier<TvImageSource> chosenArt;
    private TvEditorLayout layout;
    private Font font;
    private final GuiText.Fitted[] names = new GuiText.Fitted[TYPES.size()];
    private String defaultText = "";
    private @Nullable TvPieceType pressed;
    /** A arte da Imagem apertada, ou {@code null} (outra peça, ou nenhuma arte escolhida). */
    private @Nullable TvImageSource pressedArt;
    /** O tamanho da peça apertada, em px de desenho. */
    private int pressedW;
    private int pressedH;
    private double pressX;
    private double pressY;
    private boolean dragging;

    TvPalettePanel(TvEditorDoc doc, TvEditorCanvas canvas, TvEditorActions actions,
            Supplier<TvImageSource> chosenArt) {
        this.doc = doc;
        this.canvas = canvas;
        this.actions = actions;
        this.chosenArt = chosenArt;
    }

    void layout(Font font, TvEditorLayout layout) {
        this.font = font;
        this.layout = layout;
        for (int i = 0; i < names.length; i++) {
            names[i] = GuiText.fit(font, TvEditorText.typeName(TYPES.get(i)), layout.pieceButtons()[i].w() - TvIcons.LABEL_OFFSET - 2);
        }
        defaultText = TvEditorText.key("default_text").getString();
    }

    void render(GuiGraphics g, int mouseX, int mouseY) {
        for (int i = 0; i < TYPES.size(); i++) {
            TvRect r = layout.pieceButtons()[i];
            GuiPaint.box(g, r.x(), r.y(), r.w(), r.h(), GuiPaint.BUTTON,
                    r.contains(mouseX, mouseY) ? GuiPaint.BUTTON_HOVER_BORDER : GuiPaint.BUTTON_BORDER);
            TvIcons.draw(g, TvIcons.of(TYPES.get(i)), r.x() + TvIcons.PAD, r.y() + (r.h() - TvIcons.SIZE) / 2);
            GuiText.draw(g, font, names[i], r.x() + TvIcons.LABEL_OFFSET, r.y() + (r.h() - 8) / 2, GuiPaint.FG);
        }
    }

    /** O fantasma da peça arrastada, por cima de tudo: um retângulo do tamanho dela na escala da miniatura. */
    void renderGhost(GuiGraphics g, int mouseX, int mouseY) {
        if (!dragging || pressed == null) {
            return;
        }
        int[] spot = canvas.imagePoint(mouseX, mouseY);
        float scale = canvas.guiScale();
        int w = Math.max(4, Math.round(pressedW * scale));
        int h = Math.max(4, Math.round(pressedH * scale));
        int color = spot == null ? GuiPaint.DISABLED : GuiPaint.ACCENT;
        g.pose().pushPose();
        g.pose().translate(0, 0, 400);
        if (pressedArt != null) {
            pressedArt.draw(g, mouseX - w / 2, mouseY - h / 2, w, h);
        } else {
            g.fill(mouseX - w / 2, mouseY - h / 2, mouseX - w / 2 + w, mouseY - h / 2 + h,
                    GuiPaint.alpha(color, 0x50));
        }
        GuiPaint.outline(g, mouseX - w / 2, mouseY - h / 2, w, h, color);
        g.pose().popPose();
    }

    /** Aperta o botão de um tipo: ainda não insere, para o arrasto poder levar a peça ao canvas. */
    boolean mouseClicked(double mouseX, double mouseY) {
        for (int i = 0; i < TYPES.size(); i++) {
            if (layout.pieceButtons()[i].contains(mouseX, mouseY)) {
                press(TYPES.get(i));
                pressX = mouseX;
                pressY = mouseY;
                dragging = false;
                return true;
            }
        }
        return false;
    }

    /** Guarda o tipo, a arte (só da Imagem) e o tamanho da peça que vai nascer. */
    private void press(TvPieceType type) {
        pressed = type;
        pressedArt = type == TvPieceType.IMAGE ? chosenArt.get() : null;
        int[] size = TvPieceAccess.defaultSize(type);
        if (pressedArt != null) {
            int areaW = doc.area().pixelWidth();
            int areaH = doc.area().pixelHeight();
            size = TvImageFit.box(pressedArt.width(), pressedArt.height(), TvImageFit.scale(pressedArt.width(),
                    pressedArt.height(), areaW, areaH), areaW, areaH, 0, 0);
            size = new int[] {size[2], size[3]};
        }
        pressedW = size[0];
        pressedH = size[1];
    }

    boolean mouseDragged(double mouseX, double mouseY) {
        if (pressed == null) {
            return false;
        }
        dragging = dragging || Math.abs(mouseX - pressX) + Math.abs(mouseY - pressY) >= DRAG_START;
        return true;
    }

    /** Solta: sem arrasto insere no primeiro lugar livre; com arrasto, na miniatura, no ponto; fora dela, nada. */
    boolean mouseReleased(double mouseX, double mouseY) {
        TvPieceType type = pressed;
        if (type == null) {
            return false;
        }
        TvImageSource art = pressedArt;
        pressed = null;
        pressedArt = null;
        int[] spot = dragging ? canvas.imagePoint(mouseX, mouseY) : null;
        if (art != null && !dragging) {
            TvImageOps.insertFree(doc, actions.maxImagePixels(), art);
        } else if (art != null && spot != null) {
            TvImageOps.insert(doc, actions.maxImagePixels(), art, spot[0], spot[1]);
        } else if (!dragging) {
            doc.insert(type, defaultText);
        } else if (spot != null) {
            doc.insertAt(type, defaultText, spot[0], spot[1]);
        }
        dragging = false;
        return true;
    }

    /** O centro do botão de um tipo de peça (gancho do e2e). */
    int[] center(TvPieceType type) {
        return layout.pieceButtons()[TYPES.indexOf(type)].center();
    }
}
