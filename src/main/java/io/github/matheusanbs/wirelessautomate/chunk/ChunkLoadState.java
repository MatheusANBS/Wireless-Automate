package io.github.matheusanbs.wirelessautomate.chunk;

/** Estado do Upgrade de chunk loading de um roteador, como a tela mostra. */
public enum ChunkLoadState {
    /** Sem upgrade no slot. */
    NONE,
    /** Com upgrade, forçando o chunk do roteador (e o da máquina). */
    ACTIVE,
    /** Com upgrade, mas o dono já está no limite de chunks forçados da config. */
    LIMIT,
    /** Com upgrade, mas o servidor desligou o upgrade na config. */
    DISABLED
}
