package io.github.matheusanbs.wirelessautomate.block;

import net.minecraft.util.StringRepresentable;

/** Tiers do roteador. Os valores padrão vêm da tabela de tiers da especificação; 0 = sem limite. */
public enum RouterTier implements StringRepresentable {
    BASIC("basic", 512L, 32_000L, 16_000L, 128, false),
    ADVANCED("advanced", 8_192L, 512_000L, 256_000L, 1_024, false),
    ELITE("elite", 131_072L, 8_000_000L, 4_000_000L, 0, false),
    ULTIMATE("ultimate", 0L, 0L, 0L, 0, true);

    private final String name;
    public final long defaultItemsPerSecond;
    public final long defaultFluidPerSecond;
    public final long defaultEnergyPerTick;
    public final int defaultRange;
    public final boolean defaultCrossDimension;

    RouterTier(String name, long items, long fluid, long energy, int range, boolean crossDimension) {
        this.name = name;
        this.defaultItemsPerSecond = items;
        this.defaultFluidPerSecond = fluid;
        this.defaultEnergyPerTick = energy;
        this.defaultRange = range;
        this.defaultCrossDimension = crossDimension;
    }

    @Override
    public String getSerializedName() {
        return name;
    }

    /** O tier seguinte, ou {@code null} no Ultimate. */
    public RouterTier next() {
        int i = ordinal() + 1;
        return i < values().length ? values()[i] : null;
    }

    public String translationKey() {
        return "tier.wirelessautomate." + name;
    }
}
