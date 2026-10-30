package io.github.matheusanbs.wirelessautomate.client.guide.layout;

/** O estilo de um texto na folha; a tela escolhe fonte e cor por ele (a paginação só mede). */
public enum TextStyle {
    NORMAL, BOLD, CODE, LINK, TITLE1, TITLE2, TITLE3;

    boolean isTitle() {
        return this == TITLE1 || this == TITLE2 || this == TITLE3;
    }
}
