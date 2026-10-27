package io.github.matheusanbs.wirelessautomate.tv;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;

/** A unidade de tempo de uma taxa mostrada na Tela: o fator que multiplica o valor por segundo. */
public enum TvUnit implements StringRepresentable {
    NONE("none", 0),
    SECOND("second", 1),
    TICK("tick", 1.0 / 20),
    MINUTE("minute", 60);

    public static final Codec<TvUnit> CODEC = StringRepresentable.fromEnum(TvUnit::values);

    private final String id;
    private final double perSecond;

    TvUnit(String id, double perSecond) {
        this.id = id;
        this.perSecond = perSecond;
    }

    /** O fator que multiplica o valor por segundo (0 em {@link #NONE}, que não é uma taxa). */
    public double perSecond() {
        return perSecond;
    }

    @Override
    public String getSerializedName() {
        return id;
    }
}
