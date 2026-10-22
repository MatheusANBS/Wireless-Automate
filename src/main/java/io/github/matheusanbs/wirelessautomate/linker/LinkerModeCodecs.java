package io.github.matheusanbs.wirelessautomate.linker;

import com.mojang.serialization.Codec;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.neoforged.neoforge.network.codec.NeoForgeStreamCodecs;

/** Os codecs do componente {@code linker_mode}: o NBT pelo nome (lê os modos antigos) e a rede pelo ordinal. */
public final class LinkerModeCodecs {
    public static final Codec<LinkerMode> CODEC = Codec.STRING.xmap(LinkerMode::byName, LinkerMode::getSerializedName);
    public static final StreamCodec<RegistryFriendlyByteBuf, LinkerMode> STREAM_CODEC =
            NeoForgeStreamCodecs.enumCodec(LinkerMode.class);

    private LinkerModeCodecs() {
    }
}
