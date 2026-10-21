package io.github.matheusanbs.wirelessautomate.client.router;

import io.github.matheusanbs.wirelessautomate.network.PortMode;

/**
 * A ordem em que o clique no botão de uma face troca o modo (lógica pura): Extrai, Insere, Armazém, Nenhum e de
 * volta ao Extrai. O clique direito anda ao contrário.
 */
public final class ModeCycle {
    private static final PortMode[] ORDER = {PortMode.EXTRACT, PortMode.INSERT, PortMode.BOTH, PortMode.NONE};

    private ModeCycle() {
    }

    /** O modo depois de {@code mode}. */
    public static PortMode next(PortMode mode) {
        return ORDER[(indexOf(mode) + 1) % ORDER.length];
    }

    /** O modo antes de {@code mode}. */
    public static PortMode previous(PortMode mode) {
        return ORDER[(indexOf(mode) + ORDER.length - 1) % ORDER.length];
    }

    private static int indexOf(PortMode mode) {
        for (int i = 0; i < ORDER.length; i++) {
            if (ORDER[i] == mode) {
                return i;
            }
        }
        return ORDER.length - 1;
    }
}
