package io.github.matheusanbs.wirelessautomate.client;

import com.mojang.blaze3d.systems.RenderSystem;
import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.block.RouterTier;
import io.github.matheusanbs.wirelessautomate.network.Chemicals;
import io.github.matheusanbs.wirelessautomate.network.PortMode;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.inventory.InventoryMenu;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.fluids.FluidStack;

/**
 * Paleta e primitivas de desenho das telas, pela identidade "Porcelana e Sinal"
 * ({@code docs/identidade-visual.md}, seção "A interface"): painel claro de porcelana com tinta de
 * grafite, uma só cor de ação (coral), a cor do tier no Olho do cabeçalho e numa listra, e as cores
 * dos recursos nos ícones, abas e números. Tudo com {@link GuiGraphics#fill}, sem textura de fundo.
 * As cores são as de {@code scripts/textures/identidade.py}.
 */
public final class GuiPaint {
    // -- porcelana (tons 4 → 0 de identidade.py: luz → sombra)
    /** Fundo do painel: {@code porcelana.3}. */
    public static final int PANEL = 0xFFF2EBDD;
    /** Luz: {@code porcelana.4}; fundo dos botões, do papel milimetrado e das linhas escolhidas. */
    public static final int BEVEL_LIGHT = 0xFFFFFBF3;
    /** Sombra: {@code porcelana.1}; a sombra do painel, a linha de sombra das áreas afundadas e os filetes. */
    public static final int BEVEL_DARK = 0xFFBDB19A;
    /** Áreas afundadas (listas, slots): {@code porcelana.2}. */
    public static final int INSET = 0xFFDDD3BF;
    /** Papel milimetrado do visor 3D e dos mapas: o fundo ({@code porcelana.4}). */
    public static final int VIEW_PAPER = BEVEL_LIGHT;
    /** Papel milimetrado: as linhas ({@code porcelana.2}). */
    public static final int VIEW_GRID = INSET;
    /** Papel milimetrado: a linha mais forte, a cada quatro ({@code porcelana.1}). */
    public static final int VIEW_GRID_STRONG = BEVEL_DARK;
    /** Filetes e separadores: {@code porcelana.1}. */
    public static final int LINE = BEVEL_DARK;
    /** Linha de lista escolhida ({@code porcelana.4}) e sob o mouse (entre {@code porcelana.3} e {@code .4}). */
    public static final int ROW_SELECTED = BEVEL_LIGHT;
    public static final int ROW_HOVER = 0xFFF8F3E8;

    // -- grafite (a tinta)
    /** Fundo dos botões: {@code porcelana.4}. */
    public static final int BUTTON = BEVEL_LIGHT;
    /** Borda dos botões: {@code grafite.2}. */
    public static final int BUTTON_BORDER = 0xFF2A2730;
    /** Borda dos botões com o mouse em cima: coral. */
    public static final int BUTTON_HOVER_BORDER = 0xFFF0603E;
    /** Botão ou aba escolhida: fundo {@code grafite.2}, texto {@code porcelana.4}. */
    public static final int SELECTED = BUTTON_BORDER;
    public static final int SELECTED_TEXT = BEVEL_LIGHT;
    /** Texto comum: {@code #2A2730}, sem sombra. */
    public static final int FG = 0xFF2A2730;
    /** Texto secundário. */
    public static final int MUTED = 0xFF7A7366;
    /** Texto desativado. */
    public static final int DISABLED = 0xFFB5AC9D;
    /** Texto secundário dentro das dicas do jogo, que têm fundo escuro: porcelana apagada. */
    public static final int TOOLTIP_MUTED = 0xFFBDB19A;
    /** Abas inativas: só o texto, em {@code grafite.4}. */
    public static final int TAB_IDLE = 0xFF5A5660;
    /** Texto sobre uma cor clara (tier, recurso, modo): {@code grafite.0}. */
    public static final int DARK_TEXT = 0xFF0F0E12;
    /** Anel do Olho e contornos finos de ícones: {@code grafite.1}. */
    public static final int RING = 0xFF1B1920;

    // -- coral (a cor de ação) e os estados
    public static final int ACCENT = 0xFFF0603E;
    /** Botão principal: {@code coral.1} de fundo, mais escuro para o texto de porcelana ler bem. */
    public static final int ACCENT_DEEP = 0xFFC2442A;
    public static final int OK = 0xFF2F9D5C;
    public static final int WARN = 0xFFD88A1A;
    public static final int DANGER = 0xFFD5453A;
    public static final int PAUSED = 0xFF6B6FD0;
    /** Latão: o destaque dos mods ({@code @}) nas listas de filtro. */
    public static final int BRASS = 0xFFB48E2E;

    private static final ResourceLocation PORT_NONE = WirelessAutomate.id("textures/gui/port_none.png");
    private static final ResourceLocation PORT_EXTRACT = WirelessAutomate.id("textures/gui/port_extract.png");
    private static final ResourceLocation PORT_INSERT = WirelessAutomate.id("textures/gui/port_insert.png");
    private static final ResourceLocation PORT_BOTH = WirelessAutomate.id("textures/gui/port_both.png");

    private GuiPaint() {
    }

    /** Cor do tier: a lente do Olho (coluna "Lente" de {@code identidade.py}). */
    public static int tierColor(RouterTier tier) {
        return switch (tier) {
            case BASIC -> 0xFF6E7480;
            case ADVANCED -> 0xFFD59A1E;
            case ELITE -> 0xFF1FA9A0;
            case EMERALD -> 0xFF22A84E;
            case ALLTHEMODIUM -> 0xFFE8740A;
            case VIBRANIUM -> 0xFF18B57A;
            case UNOBTAINIUM -> 0xFFB23FD0;
            case ULTIMATE -> 0xFF7B4FE0;
        };
    }

    /** Cor do modo, a mesma dos sprites das portas ({@code MODO} em {@code identidade.py}). */
    public static int modeColor(PortMode mode) {
        return switch (mode) {
            case EXTRACT -> 0xFF2F6FD6;
            case INSERT -> 0xFFE8742B;
            case BOTH -> 0xFF2F9D5C;
            case NONE -> 0xFF948870;
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

    /**
     * Painel principal: fundo de porcelana, contorno de 1 px de grafite, sombra deslocada de 2 px
     * para baixo e para a direita e, no topo, logo abaixo do contorno, uma listra de 3 px na cor
     * {@code trim} (o tier). A sombra fica fora de {@code (x, y, w, h)}, como a dos cartões.
     */
    public static void panel(GuiGraphics g, int x, int y, int w, int h, int trim) {
        g.fill(x + 2, y + h, x + w + 2, y + h + 2, BEVEL_DARK);
        g.fill(x + w, y + 2, x + w + 2, y + h, BEVEL_DARK);
        g.fill(x, y, x + w, y + h, PANEL);
        outline(g, x, y, w, h, BUTTON_BORDER);
        g.fill(x + 1, y + 1, x + w - 1, y + 4, trim);
    }

    /**
     * Papel milimetrado: fundo claro com linhas a cada 8 px (uma mais forte a cada 4), alinhadas à
     * origem do retângulo, para o visor 3D e os mapas.
     */
    public static void paper(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, VIEW_PAPER);
        for (int i = 8, n = 1; i < w; i += 8, n++) {
            g.fill(x + i, y, x + i + 1, y + h, n % 4 == 0 ? VIEW_GRID_STRONG : VIEW_GRID);
        }
        for (int i = 8, n = 1; i < h; i += 8, n++) {
            g.fill(x, y + i, x + w, y + i + 1, n % 4 == 0 ? VIEW_GRID_STRONG : VIEW_GRID);
        }
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

    /**
     * O Olho, 5×5 px em ({@code x}, {@code y}): lente na cor com o brilho de 1 px em cima à esquerda,
     * dentro de um anel de grafite. Marca o tier e a rede no cabeçalho e a aba ativa.
     */
    public static void eye(GuiGraphics g, int x, int y, int color) {
        g.fill(x + 1, y, x + 4, y + 5, RING);
        g.fill(x, y + 1, x + 5, y + 4, RING);
        g.fill(x + 1, y + 1, x + 4, y + 4, color);
        g.fill(x + 2, y + 2, x + 4, y + 4, mix(color, RING, 0.3f));
        g.fill(x + 1, y + 1, x + 2, y + 2, mix(color, BEVEL_LIGHT, 0.6f));
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

    /** Slot 18×18 afundado: {@code porcelana.2} com a linha de sombra em cima e à esquerda. */
    public static void slot(GuiGraphics g, int x, int y) {
        g.fill(x, y, x + 18, y + 18, INSET);
        g.fill(x, y, x + 18, y + 1, BEVEL_DARK);
        g.fill(x, y, x + 1, y + 18, BEVEL_DARK);
    }

    /** Área afundada de qualquer tamanho: {@code porcelana.2} com a linha de sombra em cima e à esquerda. */
    public static void inset(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, INSET);
        g.fill(x, y, x + w, y + 1, BEVEL_DARK);
        g.fill(x, y, x + 1, y + h, BEVEL_DARK);
    }

    /**
     * Aba, como uma etiqueta de arquivo: a ativa com fundo claro, borda de grafite e um sublinhado
     * coral de 2 px; inativa, só o texto (com o mouse em cima, a moldura de botão com a borda coral).
     * O texto vai em {@link #tabText}.
     */
    public static void tab(GuiGraphics g, int x, int y, int w, int h, boolean active, boolean hovered) {
        if (active) {
            box(g, x, y, w, h, BUTTON, BUTTON_BORDER);
            g.fill(x + 1, y + h - 3, x + w - 1, y + h - 1, ACCENT);
        } else if (hovered) {
            box(g, x, y, w, h, BUTTON, BUTTON_HOVER_BORDER);
        }
    }

    /** Cor do texto de uma aba: grafite na ativa e sob o mouse, {@code grafite.4} nas outras. */
    public static int tabText(boolean active, boolean hovered) {
        return active || hovered ? FG : TAB_IDLE;
    }

    /** Caixa de marcar 9×9 px; marcada, com o miolo na cor dada. */
    public static void checkbox(GuiGraphics g, int x, int y, boolean checked, int color) {
        box(g, x, y, 9, 9, BUTTON, checked ? color : BUTTON_BORDER);
        if (checked) {
            g.fill(x + 2, y + 2, x + 7, y + 7, color);
        }
    }

    /** Fluido 16×16: a textura parada dele, tingida, como os tanques mostram. */
    public static void fluid(GuiGraphics g, FluidStack stack, int x, int y) {
        if (stack.isEmpty()) {
            return;
        }
        IClientFluidTypeExtensions ext = IClientFluidTypeExtensions.of(stack.getFluid());
        TextureAtlasSprite sprite = Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS)
                .apply(ext.getStillTexture(stack));
        int tint = ext.getTintColor(stack);
        RenderSystem.enableBlend();
        g.blit(x, y, 0, 16, 16, sprite, ((tint >> 16) & 0xFF) / 255f, ((tint >> 8) & 0xFF) / 255f, (tint & 0xFF) / 255f,
                ((tint >>> 24) & 0xFF) / 255f);
        RenderSystem.disableBlend();
    }

    /** Químico do Mekanism 16×16: a textura dele tingida, como os fluidos; "?" se ele não existe mais. */
    public static void chemical(GuiGraphics g, Font font, ResourceLocation id, int x, int y, int trim) {
        ResourceLocation icon = Chemicals.icon(id);
        if (icon == null) {
            g.fill(x, y, x + 16, y + 16, mix(INSET, trim, 0.18f));
            textCentered(g, font, Component.literal("?"), x + 8, y + 4, trim);
            return;
        }
        TextureAtlasSprite sprite = Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(icon);
        int tint = Chemicals.tint(id);
        RenderSystem.enableBlend();
        g.blit(x, y, 0, 16, 16, sprite, ((tint >> 16) & 0xFF) / 255f, ((tint >> 8) & 0xFF) / 255f, (tint & 0xFF) / 255f, 1f);
        RenderSystem.disableBlend();
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

    /** Texto sem sombra (a tinta sobre a porcelana). */
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
