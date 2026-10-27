package io.github.matheusanbs.wirelessautomate.tv;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;

/** O tipo de uma peça do layout da Tela de Sinal. */
public enum TvPieceType implements StringRepresentable {
    VALUE("value"),
    TEXT("text"),
    SHAPE("shape");

    public static final Codec<TvPieceType> CODEC = StringRepresentable.fromEnum(TvPieceType::values);

    private final String id;

    TvPieceType(String id) {
        this.id = id;
    }

    @Override
    public String getSerializedName() {
        return id;
    }
}
