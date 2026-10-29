package io.github.matheusanbs.wirelessautomate.tv;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.util.StringRepresentable;

/**
 * O fundo da Tela: o do tema, uma cor fixa ({@code top}), um degradê vertical de {@code top} a {@code bottom} ou uma
 * pixel art do layout em mosaico ({@code image}, repetida pela Tela inteira na escala inteira {@code scale} e com a
 * {@code opacity}, por cima do fundo do tema). Os campos do mosaico só são gravados quando diferem do padrão.
 */
public record TvBackground(Kind kind, int top, int bottom, Optional<UUID> image, int scale, int opacity) {
    public static final int MIN_SCALE = 1;
    public static final int MAX_SCALE = 8;

    public enum Kind implements StringRepresentable {
        THEME("theme"),
        SOLID("solid"),
        GRADIENT("gradient"),
        TILED("tiled");

        public static final Codec<Kind> CODEC = StringRepresentable.fromEnum(Kind::values);

        private final String id;

        Kind(String id) {
            this.id = id;
        }

        @Override
        public String getSerializedName() {
            return id;
        }
    }

    public static final TvBackground DEFAULT = new TvBackground(Kind.THEME, 0, 0, Optional.empty(), 2, 100);

    public static final Codec<TvBackground> CODEC = RecordCodecBuilder.create(i -> i.group(
            Kind.CODEC.lenientOptionalFieldOf("kind", DEFAULT.kind).forGetter(TvBackground::kind),
            Codec.INT.lenientOptionalFieldOf("top", DEFAULT.top).forGetter(TvBackground::top),
            Codec.INT.lenientOptionalFieldOf("bottom", DEFAULT.bottom).forGetter(TvBackground::bottom),
            UUIDUtil.CODEC.lenientOptionalFieldOf("image").forGetter(TvBackground::image),
            Codec.INT.lenientOptionalFieldOf("scale", DEFAULT.scale).forGetter(TvBackground::scale),
            Codec.INT.lenientOptionalFieldOf("opacity", DEFAULT.opacity).forGetter(TvBackground::opacity))
            .apply(i, TvBackground::new));

    public TvBackground {
        scale = TvText.clamp(scale, MIN_SCALE, MAX_SCALE);
        opacity = TvText.clamp(opacity, 0, 100);
    }

    /** Um fundo do tema, fixo ou em degradê (sem mosaico). */
    public TvBackground(Kind kind, int top, int bottom) {
        this(kind, top, bottom, Optional.empty(), 2, 100);
    }

    /** O mosaico da arte {@code image}. */
    public static TvBackground tiled(UUID image, int scale, int opacity) {
        return new TvBackground(Kind.TILED, 0, 0, Optional.of(image), scale, opacity);
    }
}
