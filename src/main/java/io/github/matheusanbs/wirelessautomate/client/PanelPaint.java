package io.github.matheusanbs.wirelessautomate.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * O desenho do Tablet novo e do seletor de cor no visual dele (mockup {@code docs/preview/tablet-do-zero}, as funções
 * de mesmo papel no {@code gerar.py}): relevo, botões, campo, slot, quadradinho de cor, janela com sombra, véu, link,
 * seta, chevron, lápis, ponto e a linha escolhida. Só {@code fill} com os tokens do {@link GuiPaint}, sem alocar.
 */
public final class PanelPaint {
    /** A tinta do texto no botão primário, igual nos dois temas. */
    public static final int PRIMARY_INK = 0xFFFFFFFF;
    /** A opacidade do véu atrás de uma janela (de 255). */
    private static final int SCRIM_ALPHA = 150;
    private static final int SHADOW = 0x46000000;

    private PanelPaint() {
    }

    /** A cor com os canais multiplicados por {@code f} (a sombra de um quadradinho, o relevo do primário). */
    public static int shade(int argb, float f) {
        return GuiPalette.shade(argb, f);
    }

    /** Relevo: moldura {@link GuiPaint#FRAME}, miolo {@code face}, luz em cima e à esquerda, sombra embaixo e à direita. */
    public static void relief(GuiGraphics g, int x, int y, int w, int h, int face, int light, int dark) {
        g.fill(x, y, x + w, y + h, GuiPaint.FRAME);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, face);
        g.fill(x + 1, y + 1, x + w - 1, y + 2, light);
        g.fill(x + 1, y + 1, x + 2, y + h - 1, light);
        g.fill(x + 1, y + h - 2, x + w - 1, y + h - 1, dark);
        g.fill(x + w - 2, y + 1, x + w - 1, y + h - 1, dark);
    }

    /** O relevo de um botão ou seletor comum. */
    public static void reliefFace(GuiGraphics g, int x, int y, int w, int h, boolean hovered) {
        relief(g, x, y, w, h, hovered ? GuiPaint.ROW_HOVER : GuiPaint.FACE, GuiPaint.RELIEF_LIGHT, GuiPaint.RELIEF_DARK);
    }

    /** Botão com o texto centrado; o primário em {@link GuiPaint#PRIMARY} com tinta branca. Desativado: texto apagado. */
    public static void button(GuiGraphics g, Font font, int x, int y, int w, int h, Component text, boolean primary,
            boolean hovered, boolean enabled) {
        if (primary) {
            int face = hovered && enabled ? GuiPaint.mix(GuiPaint.PRIMARY, 0xFFFFFFFF, 0.12f) : GuiPaint.PRIMARY;
            relief(g, x, y, w, h, face, GuiPaint.mix(face, 0xFFFFFFFF, 0.3f), shade(face, 0.75f));
        } else {
            reliefFace(g, x, y, w, h, hovered && enabled);
        }
        int ink = !enabled ? GuiPaint.DISABLED : primary ? PRIMARY_INK : GuiPaint.FG;
        GuiPaint.text(g, font, text, x + (w - font.width(text)) / 2, y + (h - 8) / 2 + 1, ink);
    }

    /** Campo de texto: borda {@code FG} com foco ({@code FRAME} sem), miolo afundado. */
    public static void field(GuiGraphics g, int x, int y, int w, int h, boolean focused) {
        g.fill(x, y, x + w, y + h, focused ? GuiPaint.FG : GuiPaint.FRAME);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, GuiPaint.INSET);
        g.fill(x + 1, y + 1, x + w - 1, y + 2, GuiPaint.SLOT_DARK);
        g.fill(x + 1, y + 1, x + 2, y + h - 1, GuiPaint.SLOT_DARK);
    }

    /** Slot de item {@code n} × {@code n}; o escolhido com a borda {@code FG} por dentro. */
    public static void slot(GuiGraphics g, int x, int y, int n, boolean chosen) {
        g.fill(x, y, x + n, y + n, GuiPaint.SLOT);
        g.fill(x, y, x + n - 1, y + 1, GuiPaint.SLOT_DARK);
        g.fill(x, y, x + 1, y + n - 1, GuiPaint.SLOT_DARK);
        g.fill(x + 1, y + n - 1, x + n, y + n, GuiPaint.SLOT_LIGHT);
        g.fill(x + n - 1, y + 1, x + n, y + n, GuiPaint.SLOT_LIGHT);
        if (chosen) {
            GuiPaint.outline(g, x + 1, y + 1, n - 2, n - 2, GuiPaint.FG);
        }
    }

    /** Quadradinho de cor {@code n} × {@code n} (ARGB opaco), com a borda na própria cor escurecida. */
    public static void swatch(GuiGraphics g, int x, int y, int n, int argb) {
        g.fill(x, y, x + n, y + n, argb);
        GuiPaint.outline(g, x, y, n, n, shade(argb, 0.7f));
    }

    /** Janela solta: sombra de 2 px e relevo claro. */
    public static void window(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x + 2, y + 2, x + w + 2, y + h + 2, SHADOW);
        relief(g, x, y, w, h, GuiPaint.BEVEL_LIGHT, GuiPaint.RELIEF_LIGHT, GuiPaint.RELIEF_DARK);
    }

    /** O véu que apaga o que está atrás de uma janela. */
    public static void scrim(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, GuiPaint.alpha(GuiPaint.PANEL, SCRIM_ALPHA));
    }

    /** Link: texto {@code FG} sublinhado. Devolve onde o texto termina. */
    public static int link(GuiGraphics g, Font font, Component text, int x, int y) {
        int w = font.width(text);
        GuiPaint.text(g, font, text, x, y, GuiPaint.FG);
        g.fill(x, y + 9, x + w - 1, y + 10, GuiPaint.FG);
        return x + w;
    }

    /** A seta de um seletor que abre para baixo, 5 × 3. */
    public static void caret(GuiGraphics g, int x, int y, int color) {
        for (int i = 0; i < 3; i++) {
            g.fill(x + i, y + i, x + 5 - i, y + i + 1, color);
        }
    }

    /** Chevron 4 × 7 (fechado, para a direita) ou 7 × 4 (aberto, para baixo). */
    public static void chevron(GuiGraphics g, int x, int y, boolean open, int color) {
        for (int i = 0; i < 4; i++) {
            g.fill(x + i, y + i, x + i + 1, y + i + 1, color);
            if (open) {
                g.fill(x + 6 - i, y + i, x + 7 - i, y + i + 1, color);
            } else {
                g.fill(x + i, y + 6 - i, x + i + 1, y + 7 - i, color);
            }
        }
    }

    /** O lápis de "dá para renomear", 7 × 7. */
    public static void pencil(GuiGraphics g, int x, int y, int color) {
        for (int i = 0; i < 6; i++) {
            g.fill(x + 5 - i, y + i, x + 7 - i, y + i + 1, color);
        }
        g.fill(x, y + 6, x + 2, y + 7, color);
    }

    /** Ponto de estado, quadrado 5 × 5. */
    public static void square(GuiGraphics g, int x, int y, int color) {
        g.fill(x, y, x + 5, y + 5, color);
    }

    /** A linha escolhida de uma lista: fundo claro e a faixa de 2 px em {@code FG}. */
    public static void chosenRow(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, GuiPaint.BEVEL_LIGHT);
        g.fill(x, y, x + 2, y + h, GuiPaint.FG);
    }

    /** Linha divisória de 1 px. */
    public static void rule(GuiGraphics g, int x, int y, int w) {
        g.fill(x, y, x + w, y + 1, GuiPaint.INSET);
    }
}
