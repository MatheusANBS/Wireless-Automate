package io.github.matheusanbs.wirelessautomate.resonator;

import java.util.Locale;
import net.minecraft.util.StringRepresentable;

/** A face da estrutura em que um Vidro de Ressonância está (para a textura conectada), ou solto. */
public enum GlassFace implements StringRepresentable {
    LOOSE, DOWN, UP, NORTH, SOUTH, WEST, EAST;

    private final String serialized = name().toLowerCase(Locale.ROOT);

    @Override
    public String getSerializedName() {
        return serialized;
    }

    public static GlassFace of(ResonatorLayout.Face face) {
        return switch (face) {
            case DOWN -> DOWN;
            case UP -> UP;
            case NORTH -> NORTH;
            case SOUTH -> SOUTH;
            case WEST -> WEST;
            case EAST -> EAST;
        };
    }
}
