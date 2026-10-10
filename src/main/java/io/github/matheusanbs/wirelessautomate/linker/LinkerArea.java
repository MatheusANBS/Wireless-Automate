package io.github.matheusanbs.wirelessautomate.linker;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import io.github.matheusanbs.wirelessautomate.net.RegistryFriendlyByteBuf;
import io.github.matheusanbs.wirelessautomate.net.ByteBufCodecs;
import io.github.matheusanbs.wirelessautomate.net.GameCodecs;
import io.github.matheusanbs.wirelessautomate.net.StreamCodec;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * Cantos marcados com o Vinculador em modo Área (componente {@code wirelessautomate:linker_area}):
 * a dimensão, o canto 1 e, depois do segundo clique, o canto 2.
 */
public record LinkerArea(ResourceKey<Level> dimension, BlockPos first, Optional<BlockPos> second) {
    public static final Codec<LinkerArea> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Level.RESOURCE_KEY_CODEC.fieldOf("dimension").forGetter(LinkerArea::dimension),
            BlockPos.CODEC.fieldOf("first").forGetter(LinkerArea::first),
            BlockPos.CODEC.optionalFieldOf("second").forGetter(LinkerArea::second))
            .apply(instance, LinkerArea::new));
    public static final StreamCodec<RegistryFriendlyByteBuf, LinkerArea> STREAM_CODEC = StreamCodec.composite(
            GameCodecs.RESOURCE_LOCATION.map(id -> ResourceKey.create(Registries.DIMENSION, id), ResourceKey::location),
            LinkerArea::dimension,
            GameCodecs.BLOCK_POS, LinkerArea::first,
            ByteBufCodecs.optional(GameCodecs.BLOCK_POS), LinkerArea::second,
            LinkerArea::new);

    public LinkerArea {
        first = first.immutable();
        second = second.map(BlockPos::immutable);
    }

    public static LinkerArea firstCorner(ResourceKey<Level> dimension, BlockPos pos) {
        return new LinkerArea(dimension, pos, Optional.empty());
    }

    public LinkerArea withSecond(BlockPos pos) {
        return new LinkerArea(dimension, first, Optional.of(pos));
    }

    public boolean complete() {
        return second.isPresent();
    }

    /** A caixa entre os dois cantos; {@code null} se falta o canto 2. */
    public @Nullable LinkerBox box() {
        return second.map(s -> LinkerBox.of(first.getX(), first.getY(), first.getZ(), s.getX(), s.getY(), s.getZ()))
                .orElse(null);
    }
}
