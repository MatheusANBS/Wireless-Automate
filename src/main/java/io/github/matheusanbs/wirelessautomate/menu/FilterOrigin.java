package io.github.matheusanbs.wirelessautomate.menu;

/**
 * De onde o jogador abriu o filtro de uma face: define até onde o filtro vale ({@link RouterFaceFilterTarget#stillValid})
 * e para onde o Voltar leva.
 */
public enum FilterOrigin {
    /** A tela do roteador, com o jogador a até 8 blocos. */
    NEAR,
    /** A tela do roteador aberta à distância pelo Tablet. */
    REMOTE_ROUTER,
    /** O painel do Tablet. */
    TABLET
}
