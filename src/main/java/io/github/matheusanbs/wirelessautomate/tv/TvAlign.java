package io.github.matheusanbs.wirelessautomate.tv;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;

/** O alinhamento do texto de uma peça dentro da caixa dela. */
public enum TvAlign implements StringRepresentable {
    LEFT("left"),
    CENTER("center"),
    RIGHT("right");

    public static final Codec<TvAlign> CODEC = StringRepresentable.fromEnum(TvAlign::values);

    private final String id;

    TvAlign(String id) {
        this.id = id;
    }

    @Override
    public String getSerializedName() {
        return id;
    }
}
