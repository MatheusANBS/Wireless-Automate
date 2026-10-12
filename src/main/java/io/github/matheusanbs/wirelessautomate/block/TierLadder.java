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
     * Regra do cartão antigo, sem origem: o cartão do degrau {@code to} sobe um bloco do degrau {@code from}
     * se {@code to} está acima e o mod dele está carregado. Vale para os cartões fabricados antes de o cartão
     * ter origem, que continuam subindo de qualquer tier abaixo. O mod de {@code from} não importa (um bloco
     * que ficou no Vibranium sobe para o Ultimate).
     */
    public static boolean canUpgrade(List<@Nullable String> requiredMods, int from, int to, Predicate<String> modLoaded) {
        return to > from && loaded(requiredMods.get(to), modLoaded);
    }

    /**
     * O cartão de origem {@code origin} e destino {@code to} sobe um bloco do degrau {@code block}. Com origem,
     * só um bloco exatamente da origem: o cartão "k → N" custa os mesmos anéis que a bancada cobraria para
     * levar o bloco de k a N, então valer de outro tier daria um caminho mais barato (ou mais caro) que o da
     * bancada. Sem origem ({@code origin < 0}, cartão antigo), a regra de {@link #canUpgrade}.
     *
     * <p>A escada não fica gravada no cartão: um cartão feito sem o Allthemodium (por exemplo Esmeralda →
     * Ultimate, só com o anel do Ultimate) continua valendo depois que o mod entra no pack e pula os anéis do
     * Allthemodium, do Vibranium e do Unobtainium. Só acontece quando o pack muda; fica assim de propósito.
     */
    public static boolean cardApplies(List<@Nullable String> requiredMods, int origin, int to, int block,
            Predicate<String> modLoaded) {
        if (origin < 0) {
            return canUpgrade(requiredMods, block, to, modLoaded);
        }
        return block == origin && canUpgrade(requiredMods, origin, to, modLoaded);
    }

    /**
     * {@code to} é o degrau imediatamente acima de {@code from} na escada desta instância (e, portanto,
     * carregado). É o "estender um degrau": o cartão "k → N−1" no centro do anel de N vira "k → N", e o bloco
     * de N−1 no centro do anel de N vira o bloco de N. Cada anel cobra um degrau só, e assim subir de k a N
     * custa a soma dos anéis no meio, pela bancada ou pelo cartão.
     */
    public static boolean oneStep(List<@Nullable String> requiredMods, int from, int to, Predicate<String> modLoaded) {
        return to >= 0 && next(requiredMods, from, modLoaded) == to;
    }

    public static boolean loaded(@Nullable String requiredMod, Predicate<String> modLoaded) {
        return requiredMod == null || modLoaded.test(requiredMod);
    }
}
