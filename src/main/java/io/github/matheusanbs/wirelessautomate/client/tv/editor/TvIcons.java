package io.github.matheusanbs.wirelessautomate.client.tv.editor;

import io.github.matheusanbs.wirelessautomate.client.GuiPaint;
import net.minecraft.client.gui.GuiGraphics;

/**
 * O único ponto de desenho dos ícones do editor da Tela de Sinal: cada ícone é um item de {@link Icon}, desenhado numa
 * caixa de {@link #SIZE} px. Hoje são traços com {@code fill}; trocar por sprites é mexer só aqui, sem tocar nos
 * painéis.
 */
final class TvIcons {
    static final int SIZE = 9;

    /** Os ícones e onde aparecem. */
    enum Icon {
        /** Alinhar à esquerda: aba Estilo, seção Texto. */
        ALIGN_LEFT,
        /** Alinhar ao centro: aba Estilo, seção Texto. */
        ALIGN_CENTER,
        /** Alinhar à direita: aba Estilo, seção Texto. */
        ALIGN_RIGHT,
        /** O conta-gotas: janela do seletor de cor. */
        EYEDROPPER,
        /** A seta da seção aberta: cabeçalho das seções da aba Estilo. */
        SECTION_OPEN,
        /** A seta da seção recolhida: cabeçalho das seções da aba Estilo. */
        SECTION_CLOSED,
        /** Subir a regra: botões de cada regra da aba Regras. */
        MOVE_UP,
        /** Descer a regra: botões de cada regra da aba Regras. */
        MOVE_DOWN,
        /** Tirar a regra: botões de cada regra da aba Regras. */
        REMOVE
    }

    private TvIcons() {
    }

    static void draw(GuiGraphics g, Icon icon, int x, int y) {
        draw(g, icon, x, y, GuiPaint.FG);
    }

    static void draw(GuiGraphics g, Icon icon, int x, int y, int color) {
        switch (icon) {
            case ALIGN_LEFT -> lines(g, x, y, color, 0);
            case ALIGN_CENTER -> lines(g, x, y, color, 1);
            case ALIGN_RIGHT -> lines(g, x, y, color, 2);
            case EYEDROPPER -> {
                g.fill(x + 5, y, x + 9, y + 4, color);
                g.fill(x + 3, y + 3, x + 6, y + 6, color);
                g.fill(x + 1, y + 5, x + 4, y + 8, color);
                g.fill(x, y + 7, x + 2, y + 9, color);
            }
            case SECTION_OPEN -> {
                g.fill(x + 1, y + 3, x + 8, y + 4, color);
                g.fill(x + 2, y + 4, x + 7, y + 5, color);
                g.fill(x + 3, y + 5, x + 6, y + 6, color);
                g.fill(x + 4, y + 6, x + 5, y + 7, color);
            }
            case SECTION_CLOSED -> {
                g.fill(x + 3, y + 1, x + 4, y + 8, color);
                g.fill(x + 4, y + 2, x + 5, y + 7, color);
                g.fill(x + 5, y + 3, x + 6, y + 6, color);
                g.fill(x + 6, y + 4, x + 7, y + 5, color);
            }
            case MOVE_UP -> {
                g.fill(x + 4, y + 1, x + 5, y + 8, color);
                g.fill(x + 3, y + 2, x + 6, y + 3, color);
                g.fill(x + 2, y + 3, x + 7, y + 4, color);
            }
            case MOVE_DOWN -> {
                g.fill(x + 4, y + 1, x + 5, y + 8, color);
                g.fill(x + 3, y + 6, x + 6, y + 7, color);
                g.fill(x + 2, y + 5, x + 7, y + 6, color);
            }
            case REMOVE -> {
                for (int i = 1; i < 8; i++) {
                    g.fill(x + i, y + i, x + i + 1, y + i + 1, color);
                    g.fill(x + 8 - i, y + i, x + 9 - i, y + i + 1, color);
                }
            }
        }
    }

    /** Quatro traços de largura alternada, colados à esquerda, ao centro ou à direita. */
    private static void lines(GuiGraphics g, int x, int y, int color, int align) {
        int[] widths = {9, 5, 9, 5};
        for (int i = 0; i < widths.length; i++) {
            int left = align == 0 ? 0 : align == 1 ? (SIZE - widths[i]) / 2 : SIZE - widths[i];
            g.fill(x + left, y + i * 2 + 1, x + left + widths[i], y + i * 2 + 2, color);
        }
    }
}
