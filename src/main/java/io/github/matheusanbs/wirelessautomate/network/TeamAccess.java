package io.github.matheusanbs.wirelessautomate.network;

import java.util.UUID;

/**
 * Ponte para os times de outro mod (o FTB Teams) sem carregar classes dele quando não está instalado. O provedor
 * real entra junto do pacote {@code compat}; sem ele nenhum jogador é membro de time nenhum.
 */
public final class TeamAccess {
    /** Há um provedor de times carregado: a seção de times existe nas telas. */
    public static final boolean LOADED = false;

    /** O jogador é membro do time. */
    public static boolean isMember(UUID team, UUID player) {
        return false;
    }

    private TeamAccess() {
    }
}
