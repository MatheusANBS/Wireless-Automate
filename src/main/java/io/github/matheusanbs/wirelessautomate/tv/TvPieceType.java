package io.github.matheusanbs.wirelessautomate.tv;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;

/** O tipo de uma peça do layout da Tela de Sinal. */
public enum TvPieceType implements StringRepresentable {
    VALUE("value"),
    TEXT("text"),
    SHAPE("shape"),
    ICON("icon"),
    BAR("bar"),
    GAUGE("gauge"),
    GRAPH("graph"),
    ARROW("arrow");

    public static final Codec<TvPieceType> CODEC = StringRepresentable.fromEnum(TvPieceType::values);

    private final String id;

    /** Se a peça lê um dado (a ligação): o Valor e as peças da etapa 3, menos o Ícone, que só mostra um item. */
    public boolean bindable() {
        return this != TEXT && this != SHAPE && this != ICON;
    }

    TvPieceType(String id) {
        this.id = id;
    }

    @Override
    public String getSerializedName() {
        return id;
    }
}
