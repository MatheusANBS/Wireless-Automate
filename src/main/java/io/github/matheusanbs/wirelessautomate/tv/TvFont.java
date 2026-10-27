package io.github.matheusanbs.wirelessautomate.tv;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;

/** A fonte do texto de uma peça. */
public enum TvFont implements StringRepresentable {
    MINECRAFT("minecraft"),
    PIXEL("pixel"),
    TERMINAL("terminal"),
    MONO("mono"),
    SANS("sans");

    public static final Codec<TvFont> CODEC = StringRepresentable.fromEnum(TvFont::values);

    private final String id;

    TvFont(String id) {
        this.id = id;
    }

    @Override
    public String getSerializedName() {
        return id;
    }
}
