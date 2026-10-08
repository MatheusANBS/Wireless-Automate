package io.github.matheusanbs.wirelessautomate.storage;

import io.github.matheusanbs.wirelessautomate.block.RouterTier;
import io.github.matheusanbs.wirelessautomate.network.LoadedTypes;
import io.github.matheusanbs.wirelessautomate.network.ResourceType;
import java.util.Locale;
import net.minecraft.util.StringRepresentable;

/**
 * Os armazenamentos do mod: o que guardam, a unidade e a capacidade padrão por tier (a ordem
 * do {@link RouterTier}; 0 = sem limite). A capacidade de verdade vem da config do servidor.
 */
public enum StorageKind implements StringRepresentable {
    /** Itens por tipo. */
    CHEST("storage_chest", "chestCapacity", ResourceType.ITEM, "items",
            new long[] {262_144L, 16_777_216L, 1_073_741_824L, 0L}),
    /** Fluidos por tipo, em mB. */
    TANK("storage_tank", "tankCapacity", ResourceType.FLUID, "mb",
            new long[] {1_000_000L, 64_000_000L, 4_000_000_000L, 0L}),
    /** Energia, em FE. */
    BATTERY("storage_battery", "batteryCapacity", ResourceType.ENERGY, "fe",
            new long[] {16_000_000L, 1_000_000_000L, 64_000_000_000L, 0L}),
    /** Químicos do Mekanism por tipo, em mB. */
    CHEMICAL_TANK("storage_chemical_tank", "chemicalTankCapacity", ResourceType.CHEMICAL, "mb",
            new long[] {1_000_000L, 64_000_000L, 4_000_000_000L, 0L}),
    /** Source do Ars Nouveau. Só aparece (aba, JEI, receita) com o Ars; o bloco existe sempre. */
    SOURCE_TANK("storage_source_tank", "sourceTankCapacity", ResourceType.SOURCE, "source",
            new long[] {160_000L, 2_560_000L, 40_960_000L, 0L});

    /** Id do bloco e do item. */
    public final String id;
    /** Chave na seção {@code storage} da config. */
    public final String configKey;
    public final ResourceType resource;
    /** Unidade: {@code items}, {@code mb}, {@code fe} ou {@code source} (chaves {@code gui.wirelessautomate.unit.*}). */
    public final String unit;
    private final long[] defaultCapacity;

    StorageKind(String id, String configKey, ResourceType resource, String unit, long[] defaultCapacity) {
        this.id = id;
        this.configKey = configKey;
        this.resource = resource;
        this.unit = unit;
        this.defaultCapacity = defaultCapacity;
    }

    public long defaultCapacity(RouterTier tier) {
        return defaultCapacity[tier.ordinal()];
    }

    /** Guarda vários tipos (lista na tela e filtro de entrada): o Baú e os Tanques de fluido e químico; a Bateria e o Tanque de Source não. */
    public boolean hasTypes() {
        return resource.filtered();
    }

    /** O mod que o recurso exige está carregado (o Tanque Químico com o Mekanism, o de Source com o Ars). */
    public boolean loaded() {
        return LoadedTypes.contains(resource);
    }

    @Override
    public String getSerializedName() {
        return name().toLowerCase(Locale.ROOT);
    }
}
