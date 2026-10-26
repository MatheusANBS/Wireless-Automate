package io.github.matheusanbs.wirelessautomate.client;

import com.mojang.blaze3d.systems.RenderSystem;
import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.block.RouterTier;
import io.github.matheusanbs.wirelessautomate.network.Chemicals;
import io.github.matheusanbs.wirelessautomate.network.PortMode;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
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
 * As cores vêm do tema ({@link GuiPalette}, claro ou escuro); o claro é o de sempre, o de
 * {@code scripts/textures/identidade.py}.
 */
public final class GuiPaint {
    // -- porcelana (tons 4 → 0 de identidade.py: luz → sombra)
    /** Fundo do painel: {@code porcelana.3}. */
    public static int PANEL;
    /** Luz: {@code porcelana.4}; fundo dos botões, do papel milimetrado e das linhas escolhidas. */
    public static int BEVEL_LIGHT;
    /** Sombra: {@code porcelana.1}; a sombra do painel, a linha de sombra das áreas afundadas e os filetes. */
    public static int BEVEL_DARK;
    /** Áreas afundadas (listas, slots): {@code porcelana.2}. */
    public static int INSET;
    /** Papel milimetrado do visor 3D e dos mapas: o fundo ({@code porcelana.4}). */
    public static int VIEW_PAPER;
    /** Papel milimetrado: as linhas ({@code porcelana.2}). */
    public static int VIEW_GRID;
    /** Papel milimetrado: a linha mais forte, a cada quatro ({@code porcelana.1}). */
    public static int VIEW_GRID_STRONG;
    /** Filetes e separadores: {@code porcelana.1}. */
    public static int LINE;
    /** Linha de lista escolhida ({@code porcelana.4}) e sob o mouse (entre {@code porcelana.3} e {@code .4}). */
    public static int ROW_SELECTED;
    public static int ROW_HOVER;

    // -- grafite (a tinta)
    /** Fundo dos botões: {@code porcelana.4}. */
    public static int BUTTON;
    /** Borda dos botões: {@code grafite.2}. */
    public static int BUTTON_BORDER;
    /** Borda dos botões com o mouse em cima: coral. */
    public static int BUTTON_HOVER_BORDER;
    /** Botão ou aba escolhida: fundo {@code grafite.2}, texto {@code porcelana.4}. */
    public static int SELECTED;
    public static int SELECTED_TEXT;
    /** Texto comum: {@code #2A2730}, sem sombra. */
    public static int FG;
    /** Texto secundário. */
    public static int MUTED;
    /** Texto desativado. */
    public static int DISABLED;
    /** Texto secundário dentro das dicas do jogo, que têm fundo escuro: porcelana apagada. */
    public static int TOOLTIP_MUTED;
    /** Destaque dentro das dicas do jogo (fundo escuro nos dois temas): coral claro. */
    public static int TOOLTIP_ACCENT;
    /** Abas inativas: só o texto, em {@code grafite.4}. */
    public static int TAB_IDLE;
    /** Texto sobre uma cor clara (tier, recurso, modo): {@code grafite.0}. */
    public static int DARK_TEXT;
    /** Anel do Olho e contornos finos de ícones: {@code grafite.1}. */
    public static int RING;

    // -- coral (a cor de ação) e os estados
    public static int ACCENT;
    /** Botão principal: {@code coral.1} de fundo, mais escuro para o texto de porcelana ler bem. */
    public static int ACCENT_DEEP;
    public static int OK;
    public static int WARN;
    public static int DANGER;
    public static int PAUSED;
    /** Latão: o destaque dos mods ({@code @}) nas listas de filtro. */
    public static int BRASS;

    /** Texto das regras na lista do filtro (roxo da regra, legível no painel). */
    public static int RULE_TEXT;

    // -- o osciloscópio do Núcleo de Ressonância: fora da paleta, iguais nos dois temas, porque o visor é um
    // instrumento de fundo escuro tanto no claro quanto no escuro (o único fundo escuro da tela)
    public static final int SCOPE_BG = 0xFF1B1920;
    public static final int SCOPE_GRID = 0xFF2F2C35;
    public static final int SCOPE_LINE = 0xFF8EE8E1;
    // -- a câmara do Reparador de Cristais: também fora da paleta, um vidro escuro nos dois temas
    public static final int CHAMBER_TOP = 0xFF120F18;
    public static final int CHAMBER_BOTTOM = 0xFF2B1D2D;
    /** A borda dos pedestais e o anel do chão com energia (coral) e sem (cinza). */
    public static final int CHAMBER_RIM = 0x99FF966E;
    public static final int CHAMBER_RIM_OFF = 0x59968CAA;
    /** O fundo dos pedestais, das barras e o véu da câmara sem energia. */
    public static final int CHAMBER_SHADE = 0xB30A080E;
    public static final int CHAMBER_VEIL = 0x730A080E;
    /** O núcleo de forja: coral com energia, lilás apagado sem. */
    public static final int CHAMBER_GLOW = 0xFFFF7A50;
    public static final int CHAMBER_GLOW_OFF = 0xFF7A6E8C;
    public static final int CHAMBER_CORE = 0xFFFFF8E1;
    /** O aviso "Sem energia" sobre a câmara. */
    public static final int CHAMBER_WARNING = 0xFFFF8A7A;
    /** O ciano do destaque no mundo, o do Olho aceso: fora da paleta, igual ao do contorno desenhado no mundo. */
    public static final int HIGHLIGHT = 0xFF3FC9BE;

    static {
        apply(GuiPalette.LIGHT);
    }

    /**
     * Passa as telas para a paleta dada ({@link GuiThemes} chama na carga da config e no botão do
     * tema). As telas leem os tokens a cada quadro, então a troca vale na hora.
     */
    public static void apply(GuiPalette p) {
        PANEL = p.panel();
        BEVEL_LIGHT = p.bevelLight();
        BEVEL_DARK = p.bevelDark();
        INSET = p.inset();
        VIEW_PAPER = BEVEL_LIGHT;
        VIEW_GRID = INSET;
        VIEW_GRID_STRONG = BEVEL_DARK;
        LINE = BEVEL_DARK;
        ROW_SELECTED = BEVEL_LIGHT;
        ROW_HOVER = p.rowHover();
        BUTTON = BEVEL_LIGHT;
        BUTTON_BORDER = p.fg();
        BUTTON_HOVER_BORDER = p.accent();
        SELECTED = BUTTON_BORDER;
        SELECTED_TEXT = BEVEL_LIGHT;
        FG = p.fg();
        MUTED = p.muted();
        DISABLED = p.disabled();
        TOOLTIP_MUTED = p.tooltipMuted();
        TOOLTIP_ACCENT = p.tooltipAccent();
        TAB_IDLE = p.tabIdle();
        DARK_TEXT = p.darkText();
        RING = p.ring();
        ACCENT = p.accent();
        ACCENT_DEEP = p.accentDeep();
        OK = p.ok();
        WARN = p.warn();
        DANGER = p.danger();
        PAUSED = p.paused();
        BRASS = p.brass();
        RULE_TEXT = p.ruleText();
    }

    /** A cor com outra transparência ({@code alpha} de 0 a 255): os véus seguem o tema. */
    public static int alpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | alpha << 24;
    }

    /**
     * Tinta de um campo de texto: o EditBox guarda a cor no próprio widget, então a tela reaplica
     * isto quando o tema muda ({@link GuiThemes#version()}), não só no init.
     */
    public static void ink(EditBox box) {
        box.setTextColor(FG);
    }

    /** A tinta do campo, com a dica (o texto de cor apagada de quando ele está vazio). */
    public static void ink(EditBox box, Component hint) {
        ink(box);
        box.setHint(hint.copy().withColor(DISABLED));
    }

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
