package io.github.matheusanbs.wirelessautomate.client.guide.doc;

import java.util.List;

/**
 * Lê uma página do livro (markdown com algumas tags) para um {@link GuideDoc}.
 * Tolerante: o que não entende vira um aviso em {@link GuideDoc#warnings()} e é pulado. Sem classes do Minecraft;
 * roda uma vez por página e idioma, numa passada pelas linhas.
 */
public final class GuideParser {
    private GuideParser() {}

    public static GuideDoc parse(String text) {
        Source src = new Source(text);
        GuideHeader header = HeaderParser.parse(src);
        List<Block> blocks = BlockParser.parse(src);
        return new GuideDoc(header, blocks, src.warnings());
    }
}
