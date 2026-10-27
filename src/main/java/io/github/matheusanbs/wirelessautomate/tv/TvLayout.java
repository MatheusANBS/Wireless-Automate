package io.github.matheusanbs.wirelessautomate.tv;

import com.mojang.serialization.Codec;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.minecraft.nbt.NbtOps;

/**
 * O layout de uma Tela de Sinal: as peças na ordem de desenho, o tema, o fundo, as linhas de varredura e, derivadas
 * das peças, as ligações distintas: primeiro as das peças, depois as das regras, na ordem em que aparecem. As
 * ligações são calculadas uma vez no construtor, não por amostra; o NBT não as leva.
 */
public record TvLayout(List<TvPiece> pieces, TvTheme theme, TvBackground background, boolean scanlines,
        List<TvBinding> bindings) {
    public static final int HARD_CAP = 512;

    /** Lê cada peça à parte: uma que não decodifica é largada, e as que passam de {@link #HARD_CAP} também. */
    private static final Codec<List<TvPiece>> LENIENT_PIECES = Codec.PASSTHROUGH.listOf().xmap(
            dynamics -> {
                List<TvPiece> read = new ArrayList<>(Math.min(dynamics.size(), HARD_CAP));
                for (Dynamic<?> dynamic : dynamics) {
                    if (read.size() >= HARD_CAP) {
                        break;
                    }
                    Optional<TvPiece> piece = TvPiece.CODEC.parse(dynamic).result();
                    piece.ifPresent(read::add);
                }
                int dropped = dynamics.size() - read.size();
                if (dropped > 0) {
                    WirelessAutomate.LOGGER.debug("Tela de Sinal: {} peças largadas ao ler o layout", dropped);
                }
                return read;
            },
            TvLayout::dynamics);

    public static final Codec<TvLayout> CODEC = RecordCodecBuilder.create(i -> i.group(
            LENIENT_PIECES.optionalFieldOf("pieces", List.of()).forGetter(TvLayout::pieces),
            TvCodecs.THEME.optionalFieldOf("theme", TvTheme.DEFAULT).forGetter(TvLayout::theme),
            TvBackground.CODEC.optionalFieldOf("background", TvBackground.DEFAULT).forGetter(TvLayout::background),
            Codec.BOOL.optionalFieldOf("scanlines", false).forGetter(TvLayout::scanlines)).apply(i, TvLayout::of));

    public static final TvLayout EMPTY = of(List.of());

    public TvLayout {
        pieces = List.copyOf(pieces);
        Set<TvBinding> distinct = new LinkedHashSet<>();
        for (TvPiece piece : pieces) {
            piece.binding().ifPresent(distinct::add);
        }
        for (TvPiece piece : pieces) {
            for (TvRule rule : piece.rules()) {
                rule.bind().ifPresent(distinct::add);
            }
        }
        bindings = List.copyOf(distinct);
    }

    private static List<Dynamic<?>> dynamics(List<TvPiece> pieces) {
        List<Dynamic<?>> out = new ArrayList<>(pieces.size());
        for (TvPiece piece : pieces) {
            out.add(new Dynamic<>(NbtOps.INSTANCE, TvPiece.CODEC.encodeStart(NbtOps.INSTANCE, piece).getOrThrow()));
        }
        return out;
    }

    public static TvLayout of(List<TvPiece> pieces) {
        return of(pieces, TvTheme.DEFAULT, TvBackground.DEFAULT, false);
    }

    public static TvLayout of(List<TvPiece> pieces, TvTheme theme, TvBackground background, boolean scanlines) {
        return new TvLayout(pieces, theme, background, scanlines, List.of());
    }

    public TvLayout withPieces(List<TvPiece> pieces) {
        return of(pieces, theme, background, scanlines);
    }

    public TvLayout withTheme(TvTheme theme) {
        return of(pieces, theme, background, scanlines);
    }

    public TvLayout withBackground(TvBackground background) {
        return of(pieces, theme, background, scanlines);
    }

    public TvLayout withScanlines(boolean scanlines) {
        return of(pieces, theme, background, scanlines);
    }

    /** A posição da ligação em {@link #bindings()}, ou -1 se nenhuma peça a usa. */
    public int bindingIndex(TvBinding binding) {
        return bindings.indexOf(binding);
    }

    /** Cada peça encolhida para caber na área e empurrada para dentro dela. */
    public TvLayout clampedTo(TvArea area) {
        int maxW = area.pixelWidth();
        int maxH = area.pixelHeight();
        List<TvPiece> clamped = new ArrayList<>(pieces.size());
        for (TvPiece piece : pieces) {
            int w = Math.min(piece.width(), maxW);
            int h = Math.min(piece.height(), maxH);
            clamped.add(piece.withBox(TvText.clamp(piece.x(), 0, maxW - w), TvText.clamp(piece.y(), 0, maxH - h), w, h));
        }
        return of(clamped, theme, background, scanlines);
    }
}
