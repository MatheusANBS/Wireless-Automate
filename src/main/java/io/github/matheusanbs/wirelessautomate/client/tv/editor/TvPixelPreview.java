package io.github.matheusanbs.wirelessautomate.client.tv.editor;

import io.github.matheusanbs.wirelessautomate.client.GuiPaint;
import io.github.matheusanbs.wirelessautomate.client.tv.pixel.TvPixelCanvas;
import net.minecraft.client.gui.GuiGraphics;

/**
 * A prévia do editor de pixel art (decisão 1h da spec da etapa 4): a arte em 1×, em 2× e repetida 3 × 3, como fica no
 * mosaico, lado a lado na área que sobra na coluna da direita. Cada uma é um quadrilátero da mesma textura do quadro
 * (o mosaico pela repetição da textura). Quando uma não cabe inteira, mostra o meio dela (no mosaico, as emendas
 * continuam à vista); o 3 × 3 vai para baixo das outras se não sobrar largura ao lado. Clicar numa leva a vista do
 * quadro até aquele pixel. As caixas são refeitas só quando o tamanho da arte muda.
 */
final class TvPixelPreview {
    private static final int GAP = 4;
    private static final int MIN_TILE = 16;
    private static final int CHECKER = 4;
    /** Os três: 1×, 2× e o mosaico. */
    private static final int[] SCALES = {1, 2, 1};
    private static final int[] TILES = {1, 1, 3};

    private final TvPixelTexture texture;
    private final TvRect[] slots = new TvRect[3];
    private TvRect area = TvRect.NONE;
    private int builtW = -1;
    private int builtH = -1;

    TvPixelPreview(TvPixelTexture texture) {
        this.texture = texture;
    }

    void layout(TvRect area) {
        this.area = area;
        builtW = -1;
    }

    private void place(int aw, int ah) {
        builtW = aw;
        builtH = ah;
        int x = area.x();
        int y = area.y();
        slots[0] = new TvRect(x, y, Math.min(aw, area.w()), Math.min(ah, area.h()));
        int x2 = slots[0].right() + GAP;
        slots[1] = new TvRect(x2, y, Math.max(0, Math.min(2 * aw, area.right() - x2)), Math.min(2 * ah, area.h()));
        int x3 = slots[1].right() + GAP;
        if (area.right() - x3 >= MIN_TILE) {
            slots[2] = new TvRect(x3, y, Math.min(3 * aw, area.right() - x3), Math.min(3 * ah, area.h()));
        } else {
            int y3 = y + Math.max(slots[0].h(), slots[1].h()) + GAP;
            int h3 = Math.min(3 * ah, area.bottom() - y3);
            slots[2] = h3 >= MIN_TILE ? new TvRect(x, y3, Math.min(3 * aw, area.w()), h3) : TvRect.NONE;
        }
    }

    void render(GuiGraphics g, TvPixelCanvas canvas) {
        int aw = canvas.width();
        int ah = canvas.height();
        if (aw != builtW || ah != builtH) {
            place(aw, ah);
        }
        for (int i = 0; i < slots.length; i++) {
            TvRect r = slots[i];
            if (r.w() <= 0 || r.h() <= 0) {
                continue;
            }
            GuiPaint.outline(g, r.x() - 1, r.y() - 1, r.w() + 2, r.h() + 2, GuiPaint.BUTTON_BORDER);
            texture.drawChecker(g, r.x(), r.y(), r.right(), r.bottom(), r.x(), r.y(), CHECKER);
            float fullW = aw * SCALES[i] * TILES[i];
            float fullH = ah * SCALES[i] * TILES[i];
            float u0 = (fullW - r.w()) / 2f / (aw * SCALES[i]);
            float v0 = (fullH - r.h()) / 2f / (ah * SCALES[i]);
            texture.drawArt(g, r.x(), r.y(), r.right(), r.bottom(), u0, v0, u0 + r.w() / (float) (aw * SCALES[i]),
                    v0 + r.h() / (float) (ah * SCALES[i]));
        }
    }

    /** O pixel da arte sob o clique numa prévia, como {x, y}, ou {@code null} fora delas. */
    int[] cellAt(double mouseX, double mouseY, TvPixelCanvas canvas) {
        int aw = canvas.width();
        int ah = canvas.height();
        for (int i = 0; i < slots.length; i++) {
            TvRect r = slots[i];
            if (r == null || !r.contains(mouseX, mouseY)) {
                continue;
            }
            double fullW = aw * SCALES[i] * TILES[i];
            double fullH = ah * SCALES[i] * TILES[i];
            double px = ((fullW - r.w()) / 2 + mouseX - r.x()) / SCALES[i];
            double py = ((fullH - r.h()) / 2 + mouseY - r.y()) / SCALES[i];
            return new int[] {Math.floorMod((int) Math.floor(px), aw), Math.floorMod((int) Math.floor(py), ah)};
        }
        return null;
    }

    /** O centro de uma prévia (0 a 1×, 1 a 2×, 2 o mosaico), gancho do e2e. */
    int[] center(int which) {
        return slots[which].center();
    }
}
