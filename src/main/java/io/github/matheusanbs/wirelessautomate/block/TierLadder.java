package io.github.matheusanbs.wirelessautomate.block;

import java.util.List;
import java.util.function.Predicate;
import org.jetbrains.annotations.Nullable;

/**
 * A escada de tiers sem classes do Minecraft: cada degrau diz o mod que exige ({@code null} = nenhum), e o
 * próximo e o anterior pulam os degraus cujo mod não está carregado. Sem o Allthemodium, a Esmeralda sobe
 * direto para o Ultimate; um bloco que ficou num tier do ATM depois que o mod saiu também sobe para o
 * Ultimate. Testada por JUnit; o {@link RouterTier} passa os mods dele e o {@code ModList}.
 */
public final class TierLadder {
    private TierLadder() {
    }

    /** Índice do próximo degrau carregado depois de {@code from}, ou -1 se não há. */
    public static int next(List<@Nullable String> requiredMods, int from, Predicate<String> modLoaded) {
        for (int i = from + 1; i < requiredMods.size(); i++) {
            if (loaded(requiredMods.get(i), modLoaded)) {
                return i;
            }
        }
        return -1;
    }

    /** Índice do degrau carregado antes de {@code from}, ou -1 se não há. */
    public static int previous(List<@Nullable String> requiredMods, int from, Predicate<String> modLoaded) {
        for (int i = from - 1; i >= 0; i--) {
            if (loaded(requiredMods.get(i), modLoaded)) {
                return i;
            }
        }
        return -1;
    }

    public static boolean loaded(@Nullable String requiredMod, Predicate<String> modLoaded) {
        return requiredMod == null || modLoaded.test(requiredMod);
    }
}
