package io.github.matheusanbs.wirelessautomate.client.tv.editor;

import io.github.matheusanbs.wirelessautomate.client.GuiPaint;
import io.github.matheusanbs.wirelessautomate.client.GuiText;
import io.github.matheusanbs.wirelessautomate.tv.TvLibraryPicture;
import io.github.matheusanbs.wirelessautomate.tv.TvPixelArt;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * A aba Artes da coluna da esquerda: uma divisão no alto, "Pixel art" e "Imagens", e embaixo a parte escolhida, a
 * {@link TvArtsPanel} (as pixel arts, que se desenham no editor) ou a {@link TvPicturesPanel} (as imagens enviadas,
 * que só se enviam, renomeiam e removem). As duas ficam na mesma aba porque fazem o mesmo papel na Tela: entram na
 * peça Imagem e no mosaico pela mesma grade e pelo mesmo arrastar ({@link TvArtDrag}, um só para as duas). A Imagem
 * que a paleta de Peças cria nasce com o que está escolhido na parte aberta ({@link #chosen}). Medida no
 * {@link #layout}, nunca por quadro.
 */
final class TvLibraryTab {
    /** As partes da aba. */
    enum Part { ARTS, PICTURES }

    private static final int SEGMENT_H = 13;
    private static final int GAP = 3;

    private final TvArtsPanel arts;
    private final TvPicturesPanel pictures;
    private final TvArtDrag drag;
    private final TvRect[] segments = {TvRect.NONE, TvRect.NONE};
    private final GuiText.Fitted[] names = new GuiText.Fitted[2];
    private Font font;
    private Part part = Part.ARTS;

    TvLibraryTab(TvArtsPanel arts, TvPicturesPanel pictures, TvArtDrag drag) {
        this.arts = arts;
        this.pictures = pictures;
        this.drag = drag;
    }

    void layout(Font font, TvRect box) {
        this.font = font;
        int half = (box.w() - 1) / 2;
        segments[0] = new TvRect(box.x(), box.y(), half, SEGMENT_H);
        segments[1] = new TvRect(box.right() - half, box.y(), half, SEGMENT_H);
        names[0] = GuiText.fit(font, TvEditorText.key("arts.part.arts"), half - 4);
        names[1] = GuiText.fit(font, TvEditorText.key("arts.part.pictures"), half - 4);
        TvRect body = new TvRect(box.x(), box.y() + SEGMENT_H + GAP, box.w(), box.h() - SEGMENT_H - GAP);
        arts.layout(font, body);
        pictures.layout(font, body);
    }

    Part part() {
        return part;
    }

    void select(Part chosen) {
        part = chosen;
    }

    /** O que a Imagem nova da paleta de Peças mostra: a arte ou a imagem escolhida na parte aberta, ou nada. */
    @Nullable TvImageSource chosen() {
        if (part == Part.PICTURES) {
            TvLibraryPicture entry = pictures.chosen();
            return entry == null ? null : TvImageSource.of(entry.picture());
        }
        TvPixelArt art = arts.chosenArt();
        return art == null ? null : TvImageSource.of(art);
    }

    void render(GuiGraphics g, int mouseX, int mouseY) {
        for (Part each : Part.values()) {
            TvRect r = segments[each.ordinal()];
            boolean active = each == part;
            boolean hovered = r.contains(mouseX, mouseY);
            GuiPaint.tab(g, r.x(), r.y(), r.w(), r.h(), active, hovered);
            GuiText.Fitted name = names[each.ordinal()];
            GuiText.draw(g, font, name, r.x() + (r.w() - name.width()) / 2, r.y() + (r.h() - 8) / 2,
                    GuiPaint.tabText(active, hovered));
        }
        if (part == Part.ARTS) {
            arts.render(g, mouseX, mouseY);
        } else {
            pictures.render(g, mouseX, mouseY);
        }
    }

    /** O que está sendo arrastado até a miniatura, por cima de tudo. */
    void renderGhost(GuiGraphics g, int mouseX, int mouseY) {
        drag.renderGhost(g, mouseX, mouseY);
    }

    boolean mouseClicked(double mouseX, double mouseY) {
        for (Part each : Part.values()) {
            if (segments[each.ordinal()].contains(mouseX, mouseY)) {
                part = each;
                return true;
            }
        }
        return part == Part.ARTS ? arts.mouseClicked(mouseX, mouseY) : pictures.mouseClicked(mouseX, mouseY);
    }

    boolean mouseDragged(double mouseX, double mouseY) {
        return drag.mouseDragged(mouseX, mouseY);
    }

    boolean mouseReleased(double mouseX, double mouseY) {
        return drag.mouseReleased(mouseX, mouseY);
    }

    boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        return part == Part.ARTS ? arts.mouseScrolled(mouseX, mouseY, delta)
                : pictures.mouseScrolled(mouseX, mouseY, delta);
    }

    boolean keyPressed(int keyCode) {
        return part == Part.PICTURES && pictures.keyPressed(keyCode);
    }

    boolean charTyped(char c, int modifiers) {
        return part == Part.PICTURES && pictures.charTyped(c, modifiers);
    }

    boolean textFocused() {
        return part == Part.PICTURES && pictures.textFocused();
    }

    @Nullable Component tooltip(double mouseX, double mouseY) {
        return part == Part.ARTS ? arts.tooltip(mouseX, mouseY) : pictures.tooltip(mouseX, mouseY);
    }

    TvArtsPanel arts() {
        return arts;
    }

    TvPicturesPanel pictures() {
        return pictures;
    }

    /** O centro de uma parte da divisão (os ganchos do e2e). */
    int[] segmentCenter(Part wanted) {
        return segments[wanted.ordinal()].center();
    }
}
