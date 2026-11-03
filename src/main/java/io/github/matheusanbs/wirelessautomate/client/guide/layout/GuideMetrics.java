package io.github.matheusanbs.wirelessautomate.client.guide.layout;

/**
 * As medidas da folha, em unidades de folha (um pixel da fonte do jogo = uma unidade = meio pixel da textura do livro).
 * A área de conteúdo de cada folha vem da spec do livro: 116 × 140 px da textura, ou 232 × 280 unidades.
 */
public final class GuideMetrics {
    private GuideMetrics() {}

    /** Largura da área de conteúdo de uma folha. */
    public static final int SHEET_W = 232;
    /** Altura da área de conteúdo de uma folha (31 linhas de texto). */
    public static final int SHEET_H = 280;

    /** Altura da linha da fonte do jogo, para quem não tem um {@code Font} (o JUnit). */
    public static final int LINE_H = 9;
    /** Lado de um ícone de item na escala 1. */
    public static final int ICON = 16;

    /** Espaço vertical entre blocos. */
    public static final int BLOCK_GAP = 4;
    /** Espaço entre um título e o bloco seguinte. */
    public static final int HEADING_GAP = 2;
    /** Altura do ornamento embaixo do título de nível 1 (com o espaço até o bloco seguinte). */
    public static final int ORNAMENT_H = 10;
    /** Espaço entre itens de lista. */
    public static final int LIST_ITEM_GAP = 2;
    /** Recuo do texto de um item de lista (cabe o marcador "10."). */
    public static final int LIST_INDENT = 16;
    /** Espaço entre um ícone flutuante e o texto ao lado. */
    public static final int FLOAT_GAP = 4;

    /** A moldura de receita de bancada: 3 × 3 slots de 18, seta e resultado, com a moldura e 1 de folga. */
    public static final int RECIPE_W = 120;
    public static final int RECIPE_H = 64;
    /** A moldura de cena: a largura toda por esta altura. */
    public static final int SCENE_H = 110;
    /** O espaço reservado à lista de subpáginas (a tela decide o que desenhar dentro). */
    public static final int SUBPAGES_H = 5 * LINE_H;

    /** Margem horizontal e vertical dentro das células de tabela. */
    public static final int CELL_PAD_X = 3;
    public static final int CELL_PAD_Y = 2;
    /** Espessura das regras da tabela. */
    public static final int RULE = 1;
    /** Largura mínima de uma coluna de tabela ao encolher. */
    public static final int MIN_COL = 12;

    /** Escala do título de nível 1 (os outros níveis ficam na escala 1). */
    public static final double H1_SCALE = 2.0;
}
