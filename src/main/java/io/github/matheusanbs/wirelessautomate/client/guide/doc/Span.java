package io.github.matheusanbs.wirelessautomate.client.guide.doc;

/** Um trecho de texto em linha: o que cabe dentro de um parágrafo, item de lista, célula de tabela ou anotação. */
public sealed interface Span {
    record Text(String text) implements Span {}

    record Bold(String text) implements Span {}

    record Code(String text) implements Span {}

    /** {@code page} é o destino como está no texto (normalmente {@code outra-pagina.md}); quem resolve é a tela. */
    record Link(String page, String text) implements Span {}

    /** O ícone de um item no meio do texto (o {@code <ItemImage>} em linha, nas tabelas). */
    record ItemIcon(String id, double scale, String components) implements Span {}

    /** O ícone de um item que leva à página dele (o {@code <ItemLink>}). */
    record ItemLink(String id) implements Span {}

    /** {@code path} é relativo a {@code assets/wirelessautomate/guide/}, como está no texto. */
    record InlineImage(String path, String alt) implements Span {}
}
