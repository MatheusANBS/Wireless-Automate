package io.github.matheusanbs.wirelessautomate.storage.upgrade;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;

/** O que o Upgrade de Compactação faz por padrão com as cadeias que não têm regra própria. */
public enum CompactingMode implements StringRepresentable {
    OFF("off"),
    COMPRESS("compress"),
    DECOMPRESS("decompress");

    public static final Codec<CompactingMode> CODEC = StringRepresentable.fromEnum(CompactingMode::values);
    /** {@code values()} clona o array a cada chamada; o decode da rede usa este. */
    public static final CompactingMode[] ALL = values();

    private final String name;

    CompactingMode(String name) {
        this.name = name;
    }

    @Override
    public String getSerializedName() {
        return name;
    }
}
