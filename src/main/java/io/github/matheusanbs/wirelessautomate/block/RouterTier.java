package io.github.matheusanbs.wirelessautomate.block;

import net.minecraft.util.StringRepresentable;

/** Tiers do roteador. Alcance e dimensão padrão vêm da tabela de tiers da especificação; a vazão padrão fica em
 * {@code ResourceType.defaultRate}. 0 = sem limite. */
public enum RouterTier implements StringRepresentable {
    BASIC("basic", 128, false),
    ADVANCED("advanced", 1_024, false),
    ELITE("elite", 0, false),
    ULTIMATE("ultimate", 0, true);

    private final String name;
    public final int defaultRange;
    public final boolean defaultCrossDimension;

    RouterTier(String name, int range, boolean crossDimension) {
        this.name = name;
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
