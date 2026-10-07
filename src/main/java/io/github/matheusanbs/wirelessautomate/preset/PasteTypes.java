package io.github.matheusanbs.wirelessautomate.preset;

import io.github.matheusanbs.wirelessautomate.network.ResourceType;
import org.jetbrains.annotations.Nullable;

/**
 * Seletor de tipo do Configurador (lógica pura): qual aba o colar aplica. {@code null} é Todos.
 * A ordem é Todos → Itens → Fluidos → Energia → Químicos, e Químicos só entra com o Mekanism.
 */
public final class PasteTypes {
    private static final ResourceType[] WITH_CHEMICALS =
            {null, ResourceType.ITEM, ResourceType.FLUID, ResourceType.ENERGY, ResourceType.CHEMICAL};
    private static final ResourceType[] WITHOUT_CHEMICALS =
            {null, ResourceType.ITEM, ResourceType.FLUID, ResourceType.ENERGY};

    private PasteTypes() {
    }

    /** Posições do seletor, em ordem ({@code null} = Todos). Cópia: pode mexer. */
    public static ResourceType[] cycle(boolean chemicals) {
        return (chemicals ? WITH_CHEMICALS : WITHOUT_CHEMICALS).clone();
    }

    /**
     * Avança ({@code direction > 0}) ou volta ({@code direction < 0}) uma posição, dando a volta.
     * Um tipo que não está no seletor (Químicos sem o Mekanism) conta como Todos. Direção 0 fica.
     */
    public static @Nullable ResourceType next(@Nullable ResourceType current, int direction, boolean chemicals) {
        ResourceType[] cycle = chemicals ? WITH_CHEMICALS : WITHOUT_CHEMICALS;
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
