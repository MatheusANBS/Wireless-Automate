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

    /**
     * O cartão do degrau {@code to} sobe um bloco do degrau {@code from}: {@code to} está acima e o mod dele
     * está carregado. O cartão de um tier já consome os anteriores na receita, então pular degraus não
     * barateia nada. O mod de {@code from} não importa (um bloco que ficou no Vibranium sobe para o Ultimate).
     */
    public static boolean canUpgrade(List<@Nullable String> requiredMods, int from, int to, Predicate<String> modLoaded) {
        return to > from && loaded(requiredMods.get(to), modLoaded);
    }

    public static boolean loaded(@Nullable String requiredMod, Predicate<String> modLoaded) {
        return requiredMod == null || modLoaded.test(requiredMod);
    }
}
