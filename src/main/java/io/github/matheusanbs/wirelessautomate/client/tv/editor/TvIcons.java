package io.github.matheusanbs.wirelessautomate.client.tv.editor;

import io.github.matheusanbs.wirelessautomate.GuiTheme;
import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.client.GuiThemes;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

/**
 * O único ponto de desenho dos ícones do editor da Tela de Sinal: cada ícone é um item de {@link Icon}, um sprite de
 * {@value #SIZE} × {@value #SIZE} px em {@code textures/gui/tv/} (gerados pelo {@code tela_icones.py}), numa variante
 * para cada tema. Sempre em escala inteira, sem reduzir.
 */
public final class TvIcons {
    public static final int SIZE = 16;
    /** A margem antes do ícone e o vão entre ele e o rótulo, iguais em todo botão com ícone e texto. */
    public static final int PAD = 2;
    public static final int LABEL_GAP = 4;
    /** Onde começa o rótulo de um botão com ícone que começa em {@code x}. */
    public static final int LABEL_OFFSET = PAD + SIZE + LABEL_GAP;
    private static final float DISABLED_ALPHA = 0.4f;

    /** Os ícones e onde aparecem. */
    public enum Icon {
        /** A peça Valor: paleta e Camadas. */
        VALUE("value"),
        /** A peça Texto: paleta e Camadas. */
        TEXT("text"),
        /** A peça Forma: paleta e Camadas. */
        SHAPE("shape"),
        /** A aba Peças da coluna. */
        PIECES("pieces"),
        /** A aba Componentes da coluna. */
        COMPONENTS("components"),
        /** A aba Camadas da coluna. */
        LAYERS("layers"),
        /** Desfazer: barra de botões. */
        UNDO("undo"),
        /** Refazer: barra de botões. */
        REDO("redo"),
        /** Agrupar: barra de botões e menu da miniatura. */
        GROUP("group"),
        /** Desagrupar: barra de botões e menu da miniatura. */
        UNGROUP("ungroup"),
        /** Duplicar: barra de botões e menu da miniatura. */
        DUPLICATE("duplicate"),
        /** Remover: barra de botões, menu, componentes e regras. */
        REMOVE("remove"),
        /** Salvar como componente: barra de botões. */
        SAVE_COMPONENT("save_component"),
        /** Caber: o botão do zoom da miniatura. */
        FIT("fit"),
        /** Trazer para frente: menu da miniatura. */
        TO_FRONT("to_front"),
        /** Enviar para trás: menu da miniatura. */
        TO_BACK("to_back"),
        /** Peça à vista: Camadas. */
        EYE("eye"),
        /** Peça escondida: Camadas e menu da miniatura. */
        EYE_CLOSED("eye_closed"),
        /** Renomear: componentes do jogador. */
        RENAME("rename"),
        /** Alinhar à esquerda: aba Estilo, seção Texto. */
        ALIGN_LEFT("align_left"),
        /** Alinhar ao centro: aba Estilo, seção Texto. */
        ALIGN_CENTER("align_center"),
        /** Alinhar à direita: aba Estilo, seção Texto. */
        ALIGN_RIGHT("align_right"),
        /** O conta-gotas: janela do seletor de cor. */
        EYEDROPPER("eyedropper"),
        /** A seta da seção aberta: cabeçalho das seções e das Camadas. */
        SECTION_OPEN("section_open"),
        /** A seta da seção recolhida: cabeçalho das seções e das Camadas. */
        SECTION_CLOSED("section_closed"),
        /** Subir a regra: botões de cada regra da aba Regras. */
        MOVE_UP("move_up"),
        /** Descer a regra: botões de cada regra da aba Regras. */
        MOVE_DOWN("move_down"),
        /** A Tela cresce para cima e para a direita: aba Tamanho da tela do bloco. */
        GROW_UP_RIGHT("grow_up_right"),
        /** A Tela cresce para cima e para a esquerda: aba Tamanho da tela do bloco. */
        GROW_UP_LEFT("grow_up_left"),
        /** A Tela cresce para os dois lados: aba Tamanho da tela do bloco. */
        GROW_CENTER("grow_center"),
        /** Passo para menos: os campos de número do editor e a Largura e a Altura da aba Tamanho. */
        STEP_LEFT("step_left"),
        /** Passo para mais: os campos de número do editor e a Largura e a Altura da aba Tamanho. */
        STEP_RIGHT("step_right");

        private final ResourceLocation light;
        private final ResourceLocation dark;

        Icon(String name) {
            this.light = ResourceLocation.fromNamespaceAndPath(WirelessAutomate.MODID, "textures/gui/tv/" + name + ".png");
            this.dark = ResourceLocation.fromNamespaceAndPath(WirelessAutomate.MODID,
                    "textures/gui/tv/" + name + "_dark.png");
        }
    }

    private TvIcons() {
    }

    public static void draw(GuiGraphics g, Icon icon, int x, int y) {
        draw(g, icon, x, y, true);
    }

    /** Desenha o sprite do tema atual com o canto em ({@code x}, {@code y}); desligado, apagado. */
    public static void draw(GuiGraphics g, Icon icon, int x, int y, boolean enabled) {
        ResourceLocation sprite = GuiThemes.current() == GuiTheme.DARK ? icon.dark : icon.light;
        if (!enabled) {
            g.setColor(1f, 1f, 1f, DISABLED_ALPHA);
        }
        g.blit(sprite, x, y, 0, 0, SIZE, SIZE, SIZE, SIZE);
        if (!enabled) {
            g.setColor(1f, 1f, 1f, 1f);
        }
    }

    /** O ícone do tipo de peça, na paleta e nas Camadas. */
    static Icon of(io.github.matheusanbs.wirelessautomate.tv.TvPieceType type) {
        return switch (type) {
            case VALUE -> Icon.VALUE;
            case TEXT -> Icon.TEXT;
            case SHAPE -> Icon.SHAPE;
        };
    }
}
