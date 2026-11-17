package io.github.matheusanbs.wirelessautomate.resonator;

import java.util.Locale;
import net.minecraft.util.StringRepresentable;

/** Onde uma peça empilhável está na estrutura: solta, na casa de baixo ou na de cima. */
public enum StackPart implements StringRepresentable {
    LOOSE,
    LOWER,
    UPPER;

    private static final StackPart[] VALUES = values();

    private final String serialized = name().toLowerCase(Locale.ROOT);

    @Override
    public String getSerializedName() {
        return serialized;
    }

    /** A parte pelo nome serializado ({@code "upper"}); um nome desconhecido cai em {@link #LOOSE}. */
    public static StackPart byName(String name) {
        for (StackPart part : VALUES) {
            if (part.serialized.equals(name)) {
                return part;
            }
        }
        return LOOSE;
    }
}
