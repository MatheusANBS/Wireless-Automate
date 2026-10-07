package io.github.matheusanbs.wirelessautomate.client;

import com.mojang.blaze3d.systems.RenderSystem;
import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.block.RouterTier;
import io.github.matheusanbs.wirelessautomate.network.PortMode;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;

/**
 * Paleta e primitivas de desenho das telas, tiradas do rascunho visual
 * ({@code docs/preview/rascunho-visual.html}): painel escuro com chanfro, linhas finas, pílulas e
 * botões planos. Tudo com {@link GuiGraphics#fill}, sem textura de fundo.
 */
public final class GuiPaint {
    public static final int PANEL = 0xFF1B2129;
    public static final int BEVEL_LIGHT = 0xFF3A4452;
    public static final int BEVEL_DARK = 0xFF0C0F13;
    public static final int INSET = 0xFF11151B;
    public static final int VIEW_TOP = 0xFF1F2630;
    public static final int VIEW_BOTTOM = 0xFF12161C;
    public static final int LINE = 0xFF2A3340;
    public static final int BUTTON = 0xFF1C242D;
    public static final int BUTTON_BORDER = 0xFF2A3542;
    public static final int BUTTON_HOVER_BORDER = 0xFF56606F;
    public static final int FG = 0xFFE8EDF2;
    public static final int MUTED = 0xFF93A0AE;
    public static final int DISABLED = 0xFF5B6675;
    public static final int DARK_TEXT = 0xFF0D1116;

    private static final ResourceLocation PORT_NONE = WirelessAutomate.id("textures/gui/port_none.png");
    private static final ResourceLocation PORT_EXTRACT = WirelessAutomate.id("textures/gui/port_extract.png");
    private static final ResourceLocation PORT_INSERT = WirelessAutomate.id("textures/gui/port_insert.png");
    private static final ResourceLocation PORT_BOTH = WirelessAutomate.id("textures/gui/port_both.png");

    private GuiPaint() {
    }

    /** Cor de destaque do tier (a do núcleo, como no rascunho). */
    public static int tierColor(RouterTier tier) {
        return switch (tier) {
            case BASIC -> 0xFFC9D2DB;
            case ADVANCED -> 0xFFF2C04A;
            case ELITE -> 0xFF45D6CC;
            case ULTIMATE -> 0xFFA46CFF;
        };
    }

    /** Cor do modo, a mesma dos sprites das portas. */
    public static int modeColor(PortMode mode) {
        return switch (mode) {
            case EXTRACT -> 0xFF3D8BFF;
            case INSERT -> 0xFFFF9A3C;
            case BOTH -> 0xFF41C96B;
            case NONE -> 0xFF5A6270;
        };
    }

    public static ResourceLocation portSprite(PortMode mode) {
        return switch (mode) {
            case EXTRACT -> PORT_EXTRACT;
            case INSERT -> PORT_INSERT;
            case BOTH -> PORT_BOTH;
            case NONE -> PORT_NONE;
        };
    }

    /** Desenha o sprite 16×16 de uma porta, opcionalmente apagado. */
    public static void port(GuiGraphics g, PortMode mode, int x, int y, float alpha) {
        g.setColor(1f, 1f, 1f, alpha);
        RenderSystem.enableBlend();
        g.blit(portSprite(mode), x, y, 0, 0, 16, 16, 16, 16);
        RenderSystem.disableBlend();
        g.setColor(1f, 1f, 1f, 1f);
    }

    /** Mistura {@code a} com {@code b}: {@code t = 0} é só {@code a}, {@code t = 1} é só {@code b}. */
    public static int mix(int a, int b, float t) {
        int r = Math.round(((a >> 16) & 0xFF) * (1 - t) + ((b >> 16) & 0xFF) * t);
        int gr = Math.round(((a >> 8) & 0xFF) * (1 - t) + ((b >> 8) & 0xFF) * t);
        int bl = Math.round((a & 0xFF) * (1 - t) + (b & 0xFF) * t);
        return 0xFF000000 | r << 16 | gr << 8 | bl;
    }

    /** Painel principal: chanfro de 2 px (claro em cima e à esquerda), contorno na cor do tier e fundo. */
    public static void panel(GuiGraphics g, int x, int y, int w, int h, int trim) {
        g.fill(x, y, x + w, y + h, BEVEL_DARK);
        g.fill(x, y, x + w - 2, y + 2, BEVEL_LIGHT);
        g.fill(x, y, x + 2, y + h - 2, BEVEL_LIGHT);
        outline(g, x + 2, y + 2, w - 4, h - 4, trim);
        g.fill(x + 3, y + 3, x + w - 3, y + h - 3, PANEL);
    }

    public static void outline(GuiGraphics g, int x, int y, int w, int h, int color) {
        g.fill(x, y, x + w, y + 1, color);
        g.fill(x, y + h - 1, x + w, y + h, color);
        g.fill(x, y + 1, x + 1, y + h - 1, color);
        g.fill(x + w - 1, y + 1, x + w, y + h - 1, color);
    }

    /** Caixa com cantos de 1 px cortados, como um botão arredondado em pixel art. */
    public static void box(GuiGraphics g, int x, int y, int w, int h, int fill, int border) {
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, fill);
        g.fill(x + 1, y, x + w - 1, y + 1, border);
        g.fill(x + 1, y + h - 1, x + w - 1, y + h, border);
        g.fill(x, y + 1, x + 1, y + h - 1, border);
        g.fill(x + w - 1, y + 1, x + w, y + h - 1, border);
    }

    /** Pílula: caixa com cantos mais redondos, para a rede e o tier. */
    public static void pill(GuiGraphics g, int x, int y, int w, int h, int fill, int border) {
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, fill);
        g.fill(x + 2, y, x + w - 2, y + 1, border);
        g.fill(x + 2, y + h - 1, x + w - 2, y + h, border);
        g.fill(x, y + 2, x + 1, y + h - 2, border);
        g.fill(x + w - 1, y + 2, x + w, y + h - 2, border);
        g.fill(x + 1, y + 1, x + 2, y + 2, border);
        g.fill(x + w - 2, y + 1, x + w - 1, y + 2, border);
        g.fill(x + 1, y + h - 2, x + 2, y + h - 1, border);
        g.fill(x + w - 2, y + h - 2, x + w - 1, y + h - 1, border);
    }

    /** Bolinha de 5×5 px. */
    public static void dot(GuiGraphics g, int x, int y, int color) {
        g.fill(x + 1, y, x + 4, y + 5, color);
        g.fill(x, y + 1, x + 5, y + 4, color);
    }

    /** Seta para a direita (recolhido), 3×5 px. */
    public static void arrowRight(GuiGraphics g, int x, int y, int color) {
        g.fill(x, y, x + 1, y + 5, color);
        g.fill(x + 1, y + 1, x + 2, y + 4, color);
        g.fill(x + 2, y + 2, x + 3, y + 3, color);
    }

    /** Seta para baixo (aberto), 5×3 px. */
    public static void arrowDown(GuiGraphics g, int x, int y, int color) {
        g.fill(x, y, x + 5, y + 1, color);
        g.fill(x + 1, y + 1, x + 4, y + 2, color);
        g.fill(x + 2, y + 2, x + 3, y + 3, color);
    }

    /** Slot 18×18 afundado, como os do rascunho: escuro em cima e à esquerda, claro embaixo e à direita. */
    public static void slot(GuiGraphics g, int x, int y) {
        g.fill(x, y, x + 18, y + 18, BEVEL_LIGHT);
        g.fill(x, y, x + 17, y + 17, BEVEL_DARK);
        g.fill(x + 1, y + 1, x + 17, y + 17, INSET);
    }

    /** Caixa de marcar 9×9 px; marcada, com o miolo na cor dada. */
    public static void checkbox(GuiGraphics g, int x, int y, boolean checked, int color) {
        box(g, x, y, 9, 9, INSET, checked ? color : BUTTON_HOVER_BORDER);
        if (checked) {
            g.fill(x + 2, y + 2, x + 7, y + 7, color);
        }
    }

    /** Texto cortado com reticências para caber em {@code maxWidth}. */
    public static FormattedCharSequence ellipsize(Font font, Component text, int maxWidth) {
        if (font.width(text) <= maxWidth) {
            return text.getVisualOrderText();
        }
        FormattedText ellipsis = FormattedText.of("…");
        FormattedText head = font.substrByWidth(text, Math.max(0, maxWidth - font.width(ellipsis)));
        return Language.getInstance().getVisualOrder(FormattedText.composite(head, ellipsis));
    }

    /** Texto sem sombra, como no rascunho. */
    public static void text(GuiGraphics g, Font font, Component text, int x, int y, int color) {
        g.drawString(font, text, x, y, color, false);
    }

    public static void text(GuiGraphics g, Font font, FormattedCharSequence text, int x, int y, int color) {
        g.drawString(font, text, x, y, color, false);
    }

    public static void textRight(GuiGraphics g, Font font, Component text, int right, int y, int color) {
        g.drawString(font, text, right - font.width(text), y, color, false);
    }

    public static void textCentered(GuiGraphics g, Font font, Component text, int centerX, int y, int color) {
        g.drawString(font, text, centerX - font.width(text) / 2, y, color, false);
    }
}
