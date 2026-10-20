package io.github.matheusanbs.wirelessautomate.preset;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.matheusanbs.wirelessautomate.network.NodeIndex.NodeKey;
import java.util.Optional;
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
 * do nó, o bloco da máquina, a posição do nó, que o colar no grupo deixa de fora, e o quadro da presa de origem
 * ({@link ProfileSides#frame}), que converte os lados para colar nas máquinas do alcance.
 *
 * @param nodeName nome do nó na hora de copiar
 * @param machine  bloco da máquina a que o nó estava preso
 * @param origin   dimensão e posição do nó
 * @param frame    a frente da presa e o cima do mundo nos lados do roteador de origem ({@link ProfileSides#frame});
 *                 {@link ProfileSides#NONE} num perfil copiado antes da etapa 3b, que só cola na presa
 */
public record ProfileSource(String nodeName, ResourceLocation machine, NodeKey origin, int frame) {
    /** Maior nome guardado (o nome de um roteador já é curto; o corte protege o pacote). */
    public static final int MAX_NAME = 64;

    /** O nome entra cortado a {@link #MAX_NAME}: um nome longo vindo do NBT (criativo, {@code /give}) derrubaria a conexão no pacote. */
    public ProfileSource {
        nodeName = clip(nodeName);
        if (!ProfileSides.valid(frame)) {
            frame = ProfileSides.NONE;
        }
    }

    /** Sem o quadro: o perfil só cola na presa. */
    public ProfileSource(String nodeName, ResourceLocation machine, NodeKey origin) {
        this(nodeName, machine, origin, ProfileSides.NONE);
    }

    /** {@code name} cortado a {@link #MAX_NAME} caracteres. */
    public static String clip(String name) {
        return name.length() > MAX_NAME ? name.substring(0, MAX_NAME) : name;
    }

    /** O perfil cola em máquinas e grupos do alcance (tem o quadro da presa de origem). */
    public boolean pastesOnMachines() {
        return frame != ProfileSides.NONE;
    }

    public static final Codec<ProfileSource> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("name").forGetter(ProfileSource::nodeName),
            ResourceLocation.CODEC.fieldOf("machine").forGetter(ProfileSource::machine),
            Level.RESOURCE_KEY_CODEC.fieldOf("dimension").forGetter(source -> source.origin.dimension()),
            BlockPos.CODEC.fieldOf("pos").forGetter(source -> source.origin.pos()),
            Codec.INT.optionalFieldOf("frame").forGetter(source -> source.pastesOnMachines()
                    ? Optional.of(source.frame) : Optional.empty())
    ).apply(instance, (name, machine, dimension, pos, frame) -> new ProfileSource(name, machine,
            new NodeKey(dimension, pos), frame.orElse(ProfileSides.NONE))));

    public static final StreamCodec<RegistryFriendlyByteBuf, ProfileSource> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.stringUtf8(MAX_NAME), ProfileSource::nodeName,
            ResourceLocation.STREAM_CODEC, ProfileSource::machine,
            ResourceKey.streamCodec(Registries.DIMENSION), source -> source.origin.dimension(),
            BlockPos.STREAM_CODEC, source -> source.origin.pos(),
            ByteBufCodecs.BYTE, source -> (byte) source.frame,
            (name, machine, dimension, pos, frame) -> new ProfileSource(name, machine, new NodeKey(dimension, pos),
                    frame));
}
