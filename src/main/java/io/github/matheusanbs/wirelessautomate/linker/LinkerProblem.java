package io.github.matheusanbs.wirelessautomate.linker;

import java.util.Locale;

/** Por que o Vinculador não pode vincular a área agora; {@link #NONE} se pode. */
public enum LinkerProblem {
    NONE,
    /** Nenhum canto marcado. */
    NO_AREA,
    /** Só o canto 1. */
    INCOMPLETE,
    /** A área foi marcada noutra dimensão. */
    OTHER_DIMENSION,
    /** Volume acima do {@code maxAreaVolume} da config. */
    TOO_BIG,
    /** Jogador longe da área (acima do {@code maxDistance} da config). */
    TOO_FAR,
    /** A rede ativa é de outro dono e o jogador não é operador. */
    FOREIGN_NETWORK;

    /** Sufixo da chave de tradução ({@code gui.wirelessautomate.linker.problem.<chave>}). */
    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }
}
