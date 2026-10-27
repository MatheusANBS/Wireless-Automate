package io.github.matheusanbs.wirelessautomate.client.tv.render;

import io.github.matheusanbs.wirelessautomate.tv.TvBackground;
import io.github.matheusanbs.wirelessautomate.tv.TvColor;
import io.github.matheusanbs.wirelessautomate.tv.TvLayout;
import net.minecraft.client.gui.GuiGraphics;

/** O fundo da Tela inteira (do tema, fixo ou em degradê vertical) e as linhas de varredura por cima de tudo. */
final class TvPaintBackground {
    /** Preto a 20%, como o {@code .scanl} do mockup. */
    private static final int SCANLINE = 0x33000000;

    private TvPaintBackground() {
    }

    /** Pinta o fundo na área {@code width} × {@code height} (pixels de desenho). */
    static void fill(GuiGraphics g, TvLayout layout, int width, int height) {
        TvBackground background = layout.background();
        switch (background.kind()) {
            case THEME -> g.fill(0, 0, width, height, layout.theme().bg());
            case SOLID -> g.fill(0, 0, width, height, TvColor.resolve(background.top(), layout.theme()));
            case GRADIENT -> g.fillGradient(0, 0, width, height, TvColor.resolve(background.top(), layout.theme()),
                    TvColor.resolve(background.bottom(), layout.theme()));
        }
    }

    /** Uma linha de 1 px de desenho a cada 2 (as ímpares), se o layout liga as linhas de varredura. */
    static void scanlines(GuiGraphics g, TvLayout layout, int width, int height) {
        if (!layout.scanlines()) {
            return;
        }
        for (int y = 1; y < height; y += 2) {
            g.fill(0, y, width, y + 1, SCANLINE);
        }
    }
}
