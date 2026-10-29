package io.github.matheusanbs.wirelessautomate.client.tv.editor;

import io.github.matheusanbs.wirelessautomate.client.GuiPaint;
import io.github.matheusanbs.wirelessautomate.client.tv.pixel.TvPixelCanvas;
import net.minecraft.client.gui.GuiGraphics;

/**
 * A prévia do editor de pixel art (decisão 1h da spec da etapa 4): a arte em 1×, em 2× e repetida 3 × 3, como fica no
 * mosaico, lado a lado na área que sobra na coluna da direita. Cada uma é um quadrilátero da mesma textura do quadro
 * (o mosaico pela repetição da textura). Quando uma não cabe inteira, mostra o meio dela; o 3 × 3 vai para baixo das
 * outras se não sobrar largura ao lado. Se nem assim o 3 × 3 cabe inteiro (uma arte grande, até 128 × 128, ou a
 * janela pequena), a área se divide em duas: o 1× à esquerda e o 3 × 3 inteiro, reduzido, à direita, sem o 2×, para
 * as emendas do mosaico continuarem à vista; e se o 1× não cabe inteiro na metade, só o 3 × 3 fica, reduzido na área
 * toda (um 1× cortado não mostra o tamanho de verdade). O rótulo acompanha o que aparece ({@link #labelKey}). Clicar
 * numa leva a vista do quadro até aquele pixel. As caixas são refeitas só quando o tamanho da arte muda. A arte
 * animada toca nas três ({@link io.github.matheusanbs.wirelessautomate.client.tv.pixel.TvPixelFrames#previewFrame}).
 */
final class TvPixelPreview {
    private static final int GAP = 4;
    private static final int MIN_TILE = 16;
    private static final int CHECKER = 4;
    /** Os três: 1×, 2× e o mosaico; a escala do mosaico cai abaixo de 1 quando ele é reduzido para caber. */
    private final float[] scales = {1, 2, 1};
    private static final int[] TILES = {1, 1, 3};
    private static final String LABEL_ALL = "pixel.preview";
    private static final String LABEL_NO_DOUBLE = "pixel.preview.no_double";
    private static final String LABEL_TILED = "pixel.preview.tiled";

    private final TvPixelTexture texture;
    private final TvRect[] slots = new TvRect[3];
    private TvRect area = TvRect.NONE;
    private int builtW = -1;
    private int builtH = -1;
    private String labelKey = LABEL_ALL;

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
        scales[1] = 2;
        scales[2] = 1;
        if (area.right() - x3 >= MIN_TILE) {
            slots[2] = new TvRect(x3, y, Math.min(3 * aw, area.right() - x3), Math.min(3 * ah, area.h()));
        } else {
            int y3 = y + Math.max(slots[0].h(), slots[1].h()) + GAP;
            int h3 = Math.min(3 * ah, area.bottom() - y3);
            slots[2] = h3 >= MIN_TILE ? new TvRect(x, y3, Math.min(3 * aw, area.w()), h3) : TvRect.NONE;
        }
        if (slots[2].w() < 3 * aw || slots[2].h() < 3 * ah) {
            split(aw, ah);
        }
        labelKey = !visible(slots[0]) ? LABEL_TILED : visible(slots[1]) ? LABEL_ALL : LABEL_NO_DOUBLE;
    }

    private static boolean visible(TvRect r) {
        return r.w() > 0 && r.h() > 0;
    }

    /**
     * O 1× na metade da esquerda e o 3 × 3 inteiro, reduzido, na da direita; o 2× fica de fora. Um 1× que não cabe
     * inteiro na metade sai também, e o 3 × 3 ocupa a área toda.
     */
    private void split(int aw, int ah) {
        int half = (area.w() - GAP) / 2;
        slots[1] = TvRect.NONE;
        if (aw <= half && ah <= area.h()) {
            slots[0] = new TvRect(area.x(), area.y(), aw, ah);
            mosaic(area.x() + half + GAP, half, aw, ah);
        } else {
            slots[0] = TvRect.NONE;
            mosaic(area.x(), area.w(), aw, ah);
        }
    }

    /** O 3 × 3 inteiro em ({@code x}, topo da área), reduzido para caber em {@code w} de largura e na altura. */
    private void mosaic(int x, int w, int aw, int ah) {
        float scale = Math.min(1f, Math.min(w / (3f * aw), area.h() / (3f * ah)));
        scales[2] = scale;
        slots[2] = new TvRect(x, area.y(), Math.max(0, (int) (3 * aw * scale)), Math.max(0, (int) (3 * ah * scale)));
    }

    /** A chave do rótulo para o que aparece com a arte deste tamanho: os três, sem o 2×, ou só o mosaico. */
    String labelKey(TvPixelCanvas canvas) {
        if (canvas.width() != builtW || canvas.height() != builtH) {
            place(canvas.width(), canvas.height());
        }
        return labelKey;
    }

    /** Desenha as prévias com o quadro {@code frame} da arte. */
    void render(GuiGraphics g, TvPixelCanvas canvas, int frame) {
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
            float fullW = aw * scales[i] * TILES[i];
            float fullH = ah * scales[i] * TILES[i];
            float u0 = (fullW - r.w()) / 2f / (aw * scales[i]);
            float v0 = (fullH - r.h()) / 2f / (ah * scales[i]);
            texture.drawFrame(g, canvas, frame, 1, r.x(), r.y(), r.right(), r.bottom(), u0, v0,
                    u0 + r.w() / (aw * scales[i]), v0 + r.h() / (ah * scales[i]));
        }
    }

    /** O pixel da arte sob o clique numa prévia, como {x, y}, ou {@code null} fora delas. */
    int[] cellAt(double mouseX, double mouseY, TvPixelCanvas canvas) {
        int aw = canvas.width();
        int ah = canvas.height();
        for (int i = 0; i < slots.length; i++) {
            TvRect r = slots[i];
            if (r == null || r.w() <= 0 || !r.contains(mouseX, mouseY)) {
                continue;
            }
            double fullW = aw * scales[i] * TILES[i];
            double fullH = ah * scales[i] * TILES[i];
            double px = ((fullW - r.w()) / 2 + mouseX - r.x()) / scales[i];
            double py = ((fullH - r.h()) / 2 + mouseY - r.y()) / scales[i];
            return new int[] {Math.floorMod((int) Math.floor(px), aw), Math.floorMod((int) Math.floor(py), ah)};
        }
        return null;
    }

    /** O centro de uma prévia (0 a 1×, 1 a 2×, 2 o mosaico), gancho do e2e. */
    int[] center(int which) {
        return slots[which] == null ? TvRect.NONE.center() : slots[which].center();
    }
}
