package io.github.matheusanbs.wirelessautomate.resonator;

import java.util.Locale;
import net.minecraft.util.StringRepresentable;

/** O canto da casa em que a perna de uma Coluna fica, pela bússola (sudeste, sudoeste, noroeste, nordeste). */
public enum Corner implements StringRepresentable {
    SE,
    SW,
    NW,
    NE;

    private static final Corner[] VALUES = values();

    private final String serialized = name().toLowerCase(Locale.ROOT);

    @Override
    public String getSerializedName() {
        return serialized;
    }

    /** O canto pelo nome serializado ({@code "nw"}); um nome desconhecido cai em {@link #SE}. */
    public static Corner byName(String name) {
        for (Corner corner : VALUES) {
            if (corner.serialized.equals(name)) {
                return corner;
            }
        }
        return SE;
    }
}
