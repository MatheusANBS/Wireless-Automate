package io.github.matheusanbs.wirelessautomate.linker;

import com.mojang.serialization.Codec;
import java.util.Locale;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.StringRepresentable;
import net.neoforged.neoforge.network.codec.NeoForgeStreamCodecs;

/** Modo do Vinculador: um roteador por clique ou todos de uma área. */
public enum LinkerMode implements StringRepresentable {
    SINGLE,
    AREA;

    public static final Codec<LinkerMode> CODEC = StringRepresentable.fromEnum(LinkerMode::values);
    public static final StreamCodec<RegistryFriendlyByteBuf, LinkerMode> STREAM_CODEC =
            NeoForgeStreamCodecs.enumCodec(LinkerMode.class);

    @Override
    public String getSerializedName() {
        return name().toLowerCase(Locale.ROOT);
    }

    public LinkerMode toggled() {
        return this == SINGLE ? AREA : SINGLE;
    }
}
