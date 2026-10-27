package io.github.matheusanbs.wirelessautomate.client.tv.render;

import java.util.List;

/**
 * As fatias da moldura da Tela no atlas {@code textures/block/signal_screen/signal_screen.png} (256 × 256, 2 px por
 * unidade do modelo), copiadas da constante {@code FATIAS} do {@code scripts/textures/tela_modelo.py}, que as confere
 * opacas ao gerar; o {@code TvFrameTest} confere de novo no PNG. E as medidas do modelo da Tela 1 × 1 que o desenho
 * da Tela maior repete: as faixas da moldura (2,5 unidades em cima e dos lados, 3,5 embaixo) e as profundidades (a
 * tela olha para o norte, z cresce para a parede). Lógica pura, sem classes do Minecraft.
 */
public final class TvFrame {
    public static final int ATLAS = 256;
    /** Pixels do atlas por unidade do modelo (1/16 de bloco). */
    public static final float PIXELS_PER_UNIT = 2f;

    /** Um retângulo do atlas, em pixels: {@code x} e {@code y} do canto de cima à esquerda. */
    public record Slice(String name, int x, int y, int width, int height) {
        public float u(float pixels) {
            return (x + pixels) / ATLAS;
        }

        public float v(float pixels) {
            return (y + pixels) / ATLAS;
        }

        public float u0() {
            return u(0);
        }

        public float u1() {
            return u(width);
        }

        public float v0() {
            return v(0);
        }

        public float v1() {
            return v(height);
        }

        /** O comprimento da fatia, em unidades do modelo, na horizontal. */
        public float unitsWide() {
            return width / PIXELS_PER_UNIT;
        }
    }

    public static final Slice CORNER_TL = new Slice("corner_tl", 115, 35, 5, 5);
    public static final Slice CORNER_TR = new Slice("corner_tr", 122, 35, 5, 5);
    public static final Slice CORNER_BL = new Slice("corner_bl", 77, 35, 5, 7);
    public static final Slice CORNER_BR = new Slice("corner_br", 84, 35, 5, 7);
    public static final Slice EDGE_TOP = new Slice("edge_top", 91, 35, 22, 5);
    public static final Slice EDGE_LEFT = new Slice("edge_left", 219, 1, 5, 20);
    public static final Slice EDGE_RIGHT = new Slice("edge_right", 226, 1, 5, 20);
    public static final Slice EDGE_BOTTOM = new Slice("edge_bottom", 53, 35, 22, 7);
    public static final Slice CHIN = new Slice("chin", 29, 35, 22, 7);
    /** O lado: coluna 0 na frente (z 14), coluna 3 na parede (z 16); 32 px ao longo, um bloco. */
    public static final Slice SIDE = new Slice("side", 1, 1, 4, 32);
    public static final Slice MIDDLE = new Slice("middle", 195, 1, 22, 20);

    public static final List<Slice> ALL = List.of(CORNER_TL, CORNER_TR, CORNER_BL, CORNER_BR, EDGE_TOP, EDGE_LEFT,
            EDGE_RIGHT, EDGE_BOTTOM, CHIN, SIDE, MIDDLE);

    /** A faixa da moldura em cima e dos lados, em unidades. */
    public static final float BAND = 2.5f;
    /** A faixa de baixo, onde fica o queixo, em unidades. */
    public static final float BAND_BOTTOM = 3.5f;
    /** A frente da moldura. */
    public static final float FRONT_Z = 14f;
    /** A frente apagada da tela, o miolo. */
    public static final float SCREEN_Z = 15f;
    /** A imagem: atrás do vidro da Tela 1 × 1 (z 14,7) e à frente do miolo. */
    public static final float IMAGE_Z = 14.8f;
    /** O fundo, contra a parede. */
    public static final float WALL_Z = 16f;
    public static final float UNITS_PER_BLOCK = 16f;

    private TvFrame() {
    }
}
