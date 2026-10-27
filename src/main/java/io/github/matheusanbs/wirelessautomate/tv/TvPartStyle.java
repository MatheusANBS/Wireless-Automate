package io.github.matheusanbs.wirelessautomate.tv;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.StringRepresentable;

/**
 * Os campos de estilo das peças Ícone, Barra, Medidor, Gráfico e Seta (etapa 3), todos opcionais com padrão e só
 * gravados quando diferem dele: um layout das etapas 1 e 2 lê igual, e uma peça que não usa um campo não o leva no NBT.
 * As cores são ARGB ou papéis do tema ({@link TvColor}).
 *
 * @param orientation a Barra deitada ou em pé (chave {@code direction})
 * @param reverse     a Barra cresce da direita para a esquerda (ou de cima para baixo) e a Seta troca ▲ e ▼
 * @param min         o valor que a Barra e o Medidor mostram vazio
 * @param max         o valor cheio; 0 é "do dado": a fração usa 0 a 1 e os outros o maior número visto na janela
 * @param track       a cor da trilha da Barra e do Medidor
 * @param sweep       a abertura do arco do Medidor, de 90 a 360 graus
 * @param thickness   a espessura do arco do Medidor, em pixels de desenho
 * @param window      a janela do Gráfico, em minutos
 * @param mode        o Gráfico em linha ou em colunas (chave {@code graphMode})
 * @param area        o Gráfico com a área sob a linha preenchida
 * @param deadZone    a Seta fica parada quando o dado está a até isto de zero
 * @param upColor     a cor da Seta que sobe
 * @param downColor   a cor da Seta que desce
 * @param flatColor   a cor da Seta parada
 * @param item        o item do Ícone
 */
public record TvPartStyle(Orientation orientation, boolean reverse, double min, double max, int track, int sweep,
        int thickness, int window, GraphMode mode, boolean area, double deadZone, int upColor, int downColor,
        int flatColor, Optional<ResourceLocation> item) {
    public static final int MIN_SWEEP = 90;
    public static final int MAX_SWEEP = 360;
    public static final int MAX_THICKNESS = 32;
    public static final int MIN_WINDOW = 1;
    public static final int MAX_WINDOW = 10;

    /** A Barra deitada ou em pé. */
    public enum Orientation implements StringRepresentable {
        HORIZONTAL("horizontal"),
        VERTICAL("vertical");

        public static final Codec<Orientation> CODEC = StringRepresentable.fromEnum(Orientation::values);

        private final String id;

        Orientation(String id) {
            this.id = id;
        }

        @Override
        public String getSerializedName() {
            return id;
        }
    }

    /** O Gráfico em linha ou em colunas. */
    public enum GraphMode implements StringRepresentable {
        LINE("line"),
        COLUMNS("columns");

        public static final Codec<GraphMode> CODEC = StringRepresentable.fromEnum(GraphMode::values);

        private final String id;

        GraphMode(String id) {
            this.id = id;
        }

        @Override
        public String getSerializedName() {
            return id;
        }
    }

    public static final TvPartStyle DEFAULT = new TvPartStyle(Orientation.HORIZONTAL, false, 0, 0, 0x66808090, 270, 5,
            1, GraphMode.LINE, true, 0, TvColor.theme(TvColor.HIGH), TvColor.theme(TvColor.LOW),
            TvColor.theme(TvColor.MUTED), Optional.empty());

    /** Lê o campo se há, grava só se difere do padrão; um valor ilegível cai no padrão, sem derrubar a peça. */
    private static <T> MapCodec<T> field(Codec<T> codec, String name, T fallback) {
        return codec.lenientOptionalFieldOf(name).xmap(read -> read.orElse(fallback),
                value -> value.equals(fallback) ? Optional.empty() : Optional.of(value));
    }

    public static final MapCodec<TvPartStyle> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            field(Orientation.CODEC, "direction", DEFAULT.orientation).forGetter(TvPartStyle::orientation),
            field(Codec.BOOL, "reverse", DEFAULT.reverse).forGetter(TvPartStyle::reverse),
            field(Codec.DOUBLE, "min", DEFAULT.min).forGetter(TvPartStyle::min),
            field(Codec.DOUBLE, "max", DEFAULT.max).forGetter(TvPartStyle::max),
            field(Codec.INT, "track", DEFAULT.track).forGetter(TvPartStyle::track),
            field(Codec.INT, "sweep", DEFAULT.sweep).forGetter(TvPartStyle::sweep),
            field(Codec.INT, "thickness", DEFAULT.thickness).forGetter(TvPartStyle::thickness),
            field(Codec.INT, "window", DEFAULT.window).forGetter(TvPartStyle::window),
            field(GraphMode.CODEC, "graphMode", DEFAULT.mode).forGetter(TvPartStyle::mode),
            field(Codec.BOOL, "area", DEFAULT.area).forGetter(TvPartStyle::area),
            field(Codec.DOUBLE, "deadZone", DEFAULT.deadZone).forGetter(TvPartStyle::deadZone),
            field(Codec.INT, "upColor", DEFAULT.upColor).forGetter(TvPartStyle::upColor),
            field(Codec.INT, "downColor", DEFAULT.downColor).forGetter(TvPartStyle::downColor),
            field(Codec.INT, "flatColor", DEFAULT.flatColor).forGetter(TvPartStyle::flatColor),
            ResourceLocation.CODEC.lenientOptionalFieldOf("item").forGetter(TvPartStyle::item))
            .apply(i, TvPartStyle::new));

    public TvPartStyle {
        if (!Double.isFinite(min)) {
            min = 0;
        }
        if (!Double.isFinite(max)) {
            max = 0;
        }
        if (!Double.isFinite(deadZone) || deadZone < 0) {
            deadZone = 0;
        }
        sweep = TvText.clamp(sweep, MIN_SWEEP, MAX_SWEEP);
        thickness = TvText.clamp(thickness, 1, MAX_THICKNESS);
        window = TvText.clamp(window, MIN_WINDOW, MAX_WINDOW);
    }

    public TvPartStyle withOrientation(Orientation orientation) {
        return new TvPartStyle(orientation, reverse, min, max, track, sweep, thickness, window, mode, area, deadZone,
                upColor, downColor, flatColor, item);
    }

    public TvPartStyle withReverse(boolean reverse) {
        return new TvPartStyle(orientation, reverse, min, max, track, sweep, thickness, window, mode, area, deadZone,
                upColor, downColor, flatColor, item);
    }

    public TvPartStyle withRange(double min, double max) {
        return new TvPartStyle(orientation, reverse, min, max, track, sweep, thickness, window, mode, area, deadZone,
                upColor, downColor, flatColor, item);
    }

    public TvPartStyle withTrack(int track) {
        return new TvPartStyle(orientation, reverse, min, max, track, sweep, thickness, window, mode, area, deadZone,
                upColor, downColor, flatColor, item);
    }

    public TvPartStyle withSweep(int sweep) {
        return new TvPartStyle(orientation, reverse, min, max, track, sweep, thickness, window, mode, area, deadZone,
                upColor, downColor, flatColor, item);
    }

    public TvPartStyle withThickness(int thickness) {
        return new TvPartStyle(orientation, reverse, min, max, track, sweep, thickness, window, mode, area, deadZone,
                upColor, downColor, flatColor, item);
    }

    public TvPartStyle withWindow(int window) {
        return new TvPartStyle(orientation, reverse, min, max, track, sweep, thickness, window, mode, area, deadZone,
                upColor, downColor, flatColor, item);
    }

    public TvPartStyle withMode(GraphMode mode) {
        return new TvPartStyle(orientation, reverse, min, max, track, sweep, thickness, window, mode, area, deadZone,
                upColor, downColor, flatColor, item);
    }

    public TvPartStyle withArea(boolean area) {
        return new TvPartStyle(orientation, reverse, min, max, track, sweep, thickness, window, mode, area, deadZone,
                upColor, downColor, flatColor, item);
    }

    public TvPartStyle withDeadZone(double deadZone) {
        return new TvPartStyle(orientation, reverse, min, max, track, sweep, thickness, window, mode, area, deadZone,
                upColor, downColor, flatColor, item);
    }

    public TvPartStyle withArrowColors(int up, int down, int flat) {
        return new TvPartStyle(orientation, reverse, min, max, track, sweep, thickness, window, mode, area, deadZone,
                up, down, flat, item);
    }

    public TvPartStyle withItem(Optional<ResourceLocation> item) {
        return new TvPartStyle(orientation, reverse, min, max, track, sweep, thickness, window, mode, area, deadZone,
                upColor, downColor, flatColor, item);
    }
}
