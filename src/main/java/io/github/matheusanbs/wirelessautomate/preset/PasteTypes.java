package io.github.matheusanbs.wirelessautomate.preset;

import io.github.matheusanbs.wirelessautomate.network.ResourceType;
import java.util.List;
import org.jetbrains.annotations.Nullable;

/**
 * Seletor de tipo do Configurador (lógica pura): qual aba o colar aplica. {@code null} é Todos.
 * A ordem é Todos e depois os tipos disponíveis, na ordem do registro.
 */
public final class PasteTypes {
    private PasteTypes() {
    }

    /** Posições do seletor, em ordem ({@code null} = Todos). Cópia: pode mexer. */
    public static ResourceType[] cycle(List<ResourceType> available) {
        ResourceType[] cycle = new ResourceType[available.size() + 1];
        for (int i = 0; i < available.size(); i++) {
            cycle[i + 1] = available.get(i);
        }
        return cycle;
    }

    /**
     * Avança ({@code direction > 0}) ou volta ({@code direction < 0}) uma posição, dando a volta.
     * Um tipo que não está disponível conta como Todos. Direção 0 fica.
     */
    public static @Nullable ResourceType next(@Nullable ResourceType current, int direction,
            List<ResourceType> available) {
        ResourceType[] cycle = cycle(available);
        int index = 0;
        for (int i = 0; i < cycle.length; i++) {
            if (cycle[i] == current) {
                index = i;
                break;
            }
        }
        return cycle[Math.floorMod(index + Integer.signum(direction), cycle.length)];
    }
}
