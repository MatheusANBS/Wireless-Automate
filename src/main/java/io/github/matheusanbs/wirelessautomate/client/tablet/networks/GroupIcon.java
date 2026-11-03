package io.github.matheusanbs.wirelessautomate.client.tablet.networks;

import org.jetbrains.annotations.Nullable;

/**
 * A regra do ícone de um grupo: o escolhido pelo jogador, senão o da primeira máquina das redes dele, senão o de
 * reserva (o Tablet). {@code null} é "não tem" (ou um id que não existe mais). Pura, com JUnit.
 */
final class GroupIcon {
    private GroupIcon() {
    }

    static <T> T pick(@Nullable T chosen, @Nullable T machine, T fallback) {
        if (chosen != null) {
            return chosen;
        }
        return machine != null ? machine : fallback;
    }
}
