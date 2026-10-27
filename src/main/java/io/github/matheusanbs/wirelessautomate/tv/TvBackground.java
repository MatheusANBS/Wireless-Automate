package io.github.matheusanbs.wirelessautomate.tv;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.StringRepresentable;

/** O fundo da Tela: o do tema, uma cor fixa ({@code top}) ou um degradê vertical de {@code top} a {@code bottom}. */
public record TvBackground(Kind kind, int top, int bottom) {
    public enum Kind implements StringRepresentable {
        THEME("theme"),
        SOLID("solid"),
        GRADIENT("gradient");

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

    public static final TvBackground DEFAULT = new TvBackground(Kind.THEME, 0, 0);

    public static final Codec<TvBackground> CODEC = RecordCodecBuilder.create(i -> i.group(
            Kind.CODEC.lenientOptionalFieldOf("kind", DEFAULT.kind).forGetter(TvBackground::kind),
            Codec.INT.lenientOptionalFieldOf("top", DEFAULT.top).forGetter(TvBackground::top),
            Codec.INT.lenientOptionalFieldOf("bottom", DEFAULT.bottom).forGetter(TvBackground::bottom))
            .apply(i, TvBackground::new));
}
