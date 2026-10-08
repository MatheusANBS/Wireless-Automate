package io.github.matheusanbs.wirelessautomate.block;

import java.util.Arrays;
import java.util.List;
import net.minecraft.util.StringRepresentable;
import net.neoforged.fml.ModList;
import org.jetbrains.annotations.Nullable;

/**
 * Tiers do roteador e dos armazenamentos, em ordem. Alcance e dimensão padrão vêm da tabela de tiers da
 * especificação; a vazão padrão fica em {@code ResourceType.defaultRate} e a capacidade em
 * {@code StorageKind}, os dois pela posição aqui. 0 = sem limite.
 *
 * <p>Os três tiers do Allthemodium existem sempre (o bloco num deles continua valendo se o mod sair), mas só
 * entram na escada com o mod: {@link #next()} e {@link #previous()} pulam os tiers cujo mod não está
 * carregado ({@link TierLadder}). O tier é gravado pelo nome; a posição só vale dentro de uma versão.
 */
public enum RouterTier implements StringRepresentable {
    BASIC("basic", 64, false, null),
    ADVANCED("advanced", 512, false, null),
    ELITE("elite", 0, false, null),
    EMERALD("emerald", 0, true, null),
    ALLTHEMODIUM("allthemodium", 0, true, RouterTier.ATM),
    VIBRANIUM("vibranium", 0, true, RouterTier.ATM),
    UNOBTAINIUM("unobtainium", 0, true, RouterTier.ATM),
    ULTIMATE("ultimate", 0, true, null);

    /** Id do mod Allthemodium. */
    public static final String ATM = "allthemodium";

    private static final List<@Nullable String> REQUIRED_MODS =
            Arrays.stream(values()).map(tier -> tier.requiredMod).toList();

    private final String name;
    public final int defaultRange;
    public final boolean defaultCrossDimension;
    /** O mod sem o qual o tier fica fora da escada, da aba criativa e das receitas, ou {@code null}. */
    public final @Nullable String requiredMod;

    RouterTier(String name, int range, boolean crossDimension, @Nullable String requiredMod) {
        this.name = name;
        this.defaultRange = range;
        this.defaultCrossDimension = crossDimension;
        this.requiredMod = requiredMod;
    }

    @Override
    public String getSerializedName() {
        return name;
    }

    /** O mod que o tier exige está carregado (sempre, nos tiers sem mod). */
    public boolean loaded() {
        return TierLadder.loaded(requiredMod, RouterTier::modLoaded);
    }

    /** O tier seguinte na escada desta instância, ou {@code null} no Ultimate. */
    public @Nullable RouterTier next() {
        int i = TierLadder.next(REQUIRED_MODS, ordinal(), RouterTier::modLoaded);
        return i < 0 ? null : values()[i];
    }

    /** O tier anterior na escada desta instância, ou {@code null} no Básico. */
    public @Nullable RouterTier previous() {
        int i = TierLadder.previous(REQUIRED_MODS, ordinal(), RouterTier::modLoaded);
        return i < 0 ? null : values()[i];
    }

    public String translationKey() {
        return "tier.wirelessautomate." + name;
    }

    private static boolean modLoaded(String mod) {
        return ModList.get() != null && ModList.get().isLoaded(mod);
    }
}
