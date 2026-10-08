package io.github.matheusanbs.wirelessautomate.network;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import org.jetbrains.annotations.Nullable;

/**
 * Tipos de recurso que uma face transporta, e o registro do que cada um é. Cada um tem modo, filtro
 * e redstone próprios por face. A ordem das constantes é a ordem das abas, dos chips do Vinculador,
 * dos cartões do Tablet e da Shift + roda; o {@code ordinal} indexa arrays e payloads, então um tipo
 * novo entra sempre no fim.
 *
 * <p>Lógica pura (sem classes do Minecraft), testada por JUnit. Quais tipos existem nesta instância
 * fica em {@link LoadedTypes}; cor, ícone e textos, no cliente ({@code client.ResourceStyle}).
 */
public enum ResourceType {
    ITEM("item", true, true, "itemsPerSecond", false, null, 32L, 256L, 2_048L, 16_384L, 131_072L, 1_048_576L, 8_388_608L, 0L),
    FLUID("fluid", true, true, "fluidPerSecond", false, null, 2_000L, 16_000L, 128_000L, 1_024_000L, 8_192_000L, 65_536_000L, 524_288_000L, 0L),
    ENERGY("energy", false, false, "energyPerTick", true, null, 1_000L, 8_000L, 64_000L, 512_000L, 4_096_000L, 32_768_000L, 262_144_000L, 0L),
    /** Só existe com o Mekanism instalado. Divide a vazão com os fluidos e não tem cartões. */
    CHEMICAL("chemical", true, false, "fluidPerSecond", false, "mekanism", 2_000L, 16_000L, 128_000L, 1_024_000L, 8_192_000L, 65_536_000L, 524_288_000L, 0L),
    /** Só existe com o Ars Nouveau instalado. Um valor só, como a energia: sem filtro e sem cartões. */
    SOURCE("source", false, false, "sourcePerSecond", false, "ars_nouveau", 100L, 800L, 6_400L, 51_200L, 409_600L, 3_276_800L, 26_214_400L, 0L);

    private final String key;
    private final boolean filtered;
    private final boolean cards;
    private final String rateKey;
    private final boolean ratePerTick;
    private final @Nullable String requiredMod;
    private final long[] defaultRate;

    ResourceType(String key, boolean filtered, boolean cards, String rateKey, boolean ratePerTick,
            @Nullable String requiredMod, long... defaultRate) {
        this.key = key;
        this.filtered = filtered;
        this.cards = cards;
        this.rateKey = rateKey;
        this.ratePerTick = ratePerTick;
        this.requiredMod = requiredMod;
        this.defaultRate = defaultRate;
    }

    /** Chave estável em NBT, componentes, comandos e traduções. */
    public String key() {
        return key;
    }

    /** Tem tipos dentro: filtro e estoque na face. */
    public boolean filtered() {
        return filtered;
    }

    /** Tem slots de Cartão de Filtro na face. */
    public boolean cards() {
        return cards;
    }

    /** Chave da vazão dentro de {@code tiers.<tier>} na config (dois tipos podem dividir a mesma). */
    public String rateKey() {
        return rateKey;
    }

    /** A vazão da config é por tick (energia); senão, por segundo. */
    public boolean ratePerTick() {
        return ratePerTick;
    }

    /** O mod sem o qual o tipo não existe, ou {@code null}. */
    public @Nullable String requiredMod() {
        return requiredMod;
    }

    /** Vazão padrão do tier ({@code RouterTier.ordinal()}); 0 = sem limite. */
    public long defaultRate(int tier) {
        return defaultRate[tier];
    }

    public static @Nullable ResourceType byKey(String key) {
        for (ResourceType type : values()) {
            if (type.key.equals(key)) {
                return type;
            }
        }
        return null;
    }

    /** Os tipos cujo mod está carregado, na ordem do registro. */
    public static List<ResourceType> available(Predicate<String> modLoaded) {
        List<ResourceType> list = new ArrayList<>();
        for (ResourceType type : values()) {
            if (type.requiredMod == null || modLoaded.test(type.requiredMod)) {
                list.add(type);
            }
        }
        return List.copyOf(list);
    }
}
