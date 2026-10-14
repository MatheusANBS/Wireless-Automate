package io.github.matheusanbs.wirelessautomate;

/**
 * Tema das telas do mod, escolhido pelo jogador ({@link ClientConfig#THEME}). Fica no pacote base,
 * sem nada do cliente, porque a classe da config é carregada também no servidor dedicado.
 */
public enum GuiTheme {
    LIGHT,
    DARK;

    /** O outro tema: o que o botão do cabeçalho troca. */
    public GuiTheme other() {
        return this == LIGHT ? DARK : LIGHT;
    }
}
