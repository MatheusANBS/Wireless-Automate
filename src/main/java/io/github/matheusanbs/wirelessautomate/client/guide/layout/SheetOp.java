package io.github.matheusanbs.wirelessautomate.client.guide.layout;

import io.github.matheusanbs.wirelessautomate.client.guide.doc.Block;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Uma operação de desenho posicionada, em unidades de folha a partir do canto da área de conteúdo. Todas têm o
 * retângulo ({@code x, y, w, h}); as áreas de clique são operações próprias, para a tela testar o mouse sem refazer contas.
 */
public sealed interface SheetOp {
    int x();

    int y();

    int w();

    int h();

    /** Texto de uma linha no estilo e na escala dados; {@code w} e {@code h} já incluem a escala. */
    record Text(int x, int y, int w, int h, String text, TextStyle style, double scale) implements SheetOp {}

    /** A área de clique de um link: leva à página {@code page}, como está escrita no texto. */
    record LinkArea(int x, int y, int w, int h, String page) implements SheetOp {}

    /** Um ícone de item (o {@code scale} já está em {@code w} e {@code h}); a tela vê se o item tem página. */
    record Icon(int x, int y, int w, int h, String id, double scale, String components) implements SheetOp {}

    /** Um bloco desenhado como ícone, com as propriedades de estado. */
    record BlockIcon(int x, int y, int w, int h, String id, Map<String, String> props) implements SheetOp {
        public BlockIcon {
            props = Collections.unmodifiableMap(new LinkedHashMap<>(props));
        }
    }

    /** Um retângulo cheio fino: as linhas da tabela. */
    record Rule(int x, int y, int w, int h) implements SheetOp {}

    /** Uma imagem; {@code path} relativo a {@code assets/wirelessautomate/guide/}. */
    record Image(int x, int y, int w, int h, String path) implements SheetOp {}

    /** A moldura de receita: uma delas é nula ({@code itemId} para as receitas do item, {@code recipeId} para uma só). */
    record RecipeFrame(int x, int y, int w, int h, String itemId, String recipeId) implements SheetOp {}

    record SceneFrame(int x, int y, int w, int h, Block.Scene scene) implements SheetOp {}

    /** O espaço da lista de subpáginas; a tela decide o que desenhar. */
    record SubPagesMarker(int x, int y, int w, int h, boolean icons) implements SheetOp {}
}
