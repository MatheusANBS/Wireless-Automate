package io.github.matheusanbs.wirelessautomate.client.tv.editor;

import java.util.HashSet;
import java.util.Set;

/**
 * Quais seções recolhíveis da coluna de propriedades estão abertas, lembradas na sessão. Guarda só as que o jogador
 * virou em relação ao padrão de cada uma; {@link #version} muda a cada virada, para o painel refazer a aba.
 */
final class TvSections {
    private static final Set<String> FLIPPED = new HashSet<>();
    private static int version;

    private TvSections() {
    }

    static boolean open(String id, boolean openByDefault) {
        return openByDefault != FLIPPED.contains(id);
    }

    static void toggle(String id) {
        if (!FLIPPED.remove(id)) {
            FLIPPED.add(id);
        }
        version++;
    }

    static int version() {
        return version;
    }
}
