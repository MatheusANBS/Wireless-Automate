package io.github.matheusanbs.wirelessautomate.preset;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.matheusanbs.wirelessautomate.network.NodeIndex.NodeKey;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

/**
 * De onde veio o perfil guardado no Tablet (componente {@code wirelessautomate:profile_source}): o nome
 * do nó, o bloco da máquina e a posição do nó, que o colar no grupo deixa de fora.
 *
 * @param nodeName nome do nó na hora de copiar
 * @param machine  bloco da máquina a que o nó estava preso
 * @param origin   dimensão e posição do nó
 */
public record ProfileSource(String nodeName, ResourceLocation machine, NodeKey origin) {
    /** Maior nome guardado (o nome de um roteador já é curto; o corte protege o pacote). */
    public static final int MAX_NAME = 64;

    /** O nome entra cortado a {@link #MAX_NAME}: um nome longo vindo do NBT (criativo, {@code /give}) derrubaria a conexão no pacote. */
    public ProfileSource {
        nodeName = clip(nodeName);
    }

    /** {@code name} cortado a {@link #MAX_NAME} caracteres. */
    public static String clip(String name) {
        return name.length() > MAX_NAME ? name.substring(0, MAX_NAME) : name;
    }

    public static final Codec<ProfileSource> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("name").forGetter(ProfileSource::nodeName),
            ResourceLocation.CODEC.fieldOf("machine").forGetter(ProfileSource::machine),
            Level.RESOURCE_KEY_CODEC.fieldOf("dimension").forGetter(source -> source.origin.dimension()),
            BlockPos.CODEC.fieldOf("pos").forGetter(source -> source.origin.pos())
    ).apply(instance, (name, machine, dimension, pos) -> new ProfileSource(name, machine, new NodeKey(dimension, pos))));

    public static final StreamCodec<RegistryFriendlyByteBuf, ProfileSource> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.stringUtf8(MAX_NAME), ProfileSource::nodeName,
            ResourceLocation.STREAM_CODEC, ProfileSource::machine,
            ResourceKey.streamCodec(Registries.DIMENSION), source -> source.origin.dimension(),
            BlockPos.STREAM_CODEC, source -> source.origin.pos(),
            (name, machine, dimension, pos) -> new ProfileSource(name, machine, new NodeKey(dimension, pos)));
}
