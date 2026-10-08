package io.github.matheusanbs.wirelessautomate.storage;

import io.github.matheusanbs.wirelessautomate.block.RouterTier;
import io.github.matheusanbs.wirelessautomate.network.ResourceType;
import java.util.Locale;
import net.minecraft.util.StringRepresentable;

/**
 * Os quatro armazenamentos do mod: o que guardam, a unidade e a capacidade padrão por tier (a ordem
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
            new long[] {1_000_000L, 64_000_000L, 4_000_000_000L, 0L});

    /** Id do bloco e do item. */
    public final String id;
    /** Chave na seção {@code storage} da config. */
    public final String configKey;
    public final ResourceType resource;
    /** Unidade: {@code items}, {@code mb} ou {@code fe} (chaves {@code gui.wirelessautomate.unit.*}). */
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

    /** Guarda vários tipos (tem lista na tela e filtro de entrada); a Bateria não. */
    public boolean hasTypes() {
        return this != BATTERY;
    }

    @Override
    public String getSerializedName() {
        return name().toLowerCase(Locale.ROOT);
    }
}
