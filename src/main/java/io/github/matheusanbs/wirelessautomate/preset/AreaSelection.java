package io.github.matheusanbs.wirelessautomate.preset;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.jetbrains.annotations.Nullable;

/**
 * Estado de área do Configurador (componente {@code wirelessautomate:configurator_area}): o que os
 * cliques no mundo fazem ({@link Mode}), os dois cantos marcados e o ponto onde colar a cópia de área.
 * Imutável. Sem o componente, a varinha é pincel e não tem nada marcado.
 *
 * <p>Cada posição leva a dimensão: a área vale só no mundo em que foi marcada.
 */
public record AreaSelection(Mode mode, Optional<GlobalPos> corner1, Optional<GlobalPos> corner2,
        Optional<GlobalPos> anchor) {
    public static final AreaSelection EMPTY =
            new AreaSelection(Mode.BRUSH, Optional.empty(), Optional.empty(), Optional.empty());

    /** O que fazem os cliques da varinha num bloco. */
    public enum Mode implements StringRepresentable {
        /** Shift + clique copia de um roteador, clique cola. */
        BRUSH("brush"),
        /** Shift + clique marca os cantos; clique escolhe onde colar a cópia de área (e cola no segundo clique). */
        AREA("area");

        public static final Codec<Mode> CODEC = StringRepresentable.fromEnum(Mode::values);

        private final String name;

        Mode(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return name;
        }
    }

    public static final Codec<AreaSelection> CODEC = RecordCodecBuilder.create(i -> i.group(
            Mode.CODEC.lenientOptionalFieldOf("mode", Mode.BRUSH).forGetter(AreaSelection::mode),
            GlobalPos.CODEC.lenientOptionalFieldOf("corner1").forGetter(AreaSelection::corner1),
            GlobalPos.CODEC.lenientOptionalFieldOf("corner2").forGetter(AreaSelection::corner2),
            GlobalPos.CODEC.lenientOptionalFieldOf("anchor").forGetter(AreaSelection::anchor))
            .apply(i, AreaSelection::new));

    public static final StreamCodec<ByteBuf, AreaSelection> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL.map(area -> area ? Mode.AREA : Mode.BRUSH, mode -> mode == Mode.AREA),
            AreaSelection::mode,
            ByteBufCodecs.optional(GlobalPos.STREAM_CODEC), AreaSelection::corner1,
            ByteBufCodecs.optional(GlobalPos.STREAM_CODEC), AreaSelection::corner2,
            ByteBufCodecs.optional(GlobalPos.STREAM_CODEC), AreaSelection::anchor,
            AreaSelection::new);

    public AreaSelection withMode(Mode mode) {
        return new AreaSelection(mode, corner1, corner2, anchor);
    }

    /**
     * Marca um canto: o primeiro, o segundo, e um terceiro clique recomeça pelo primeiro. Um canto
     * noutra dimensão também recomeça.
     */
    public AreaSelection mark(GlobalPos pos) {
        if (corner1.isEmpty() || corner2.isPresent() || !corner1.get().dimension().equals(pos.dimension())) {
            return new AreaSelection(mode, Optional.of(pos), Optional.empty(), anchor);
        }
        return new AreaSelection(mode, corner1, Optional.of(pos), anchor);
    }

    public AreaSelection withoutCorners() {
        return new AreaSelection(mode, Optional.empty(), Optional.empty(), anchor);
    }

    public AreaSelection withAnchor(@Nullable GlobalPos anchor) {
        return new AreaSelection(mode, corner1, corner2, Optional.ofNullable(anchor));
    }

    /** Os dois cantos na mesma dimensão. */
    public boolean complete() {
        return corner1.isPresent() && corner2.isPresent()
                && corner1.get().dimension().equals(corner2.get().dimension());
    }

    /** A caixa dos dois cantos (inclusiva), ou {@code null} se faltar um. */
    public @Nullable BoundingBox box() {
        if (!complete()) {
            return null;
        }
        return BoundingBox.fromCorners(corner1.get().pos(), corner2.get().pos());
    }

    public static long volume(BoundingBox box) {
        return (long) box.getXSpan() * box.getYSpan() * box.getZSpan();
    }

    /** Origem das posições relativas da cópia: o primeiro canto. */
    public @Nullable BlockPos origin() {
        return corner1.map(GlobalPos::pos).orElse(null);
    }
}
