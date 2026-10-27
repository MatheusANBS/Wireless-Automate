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
 * O layout de uma Tela de Sinal: as peças na ordem de desenho e, derivadas delas, as ligações distintas, na ordem
 * da primeira peça que usa cada uma. As ligações são calculadas uma vez no construtor, não por amostra; o NBT só
 * leva as peças.
 */
public record TvLayout(List<TvPiece> pieces, List<TvBinding> bindings) {
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
            LENIENT_PIECES.optionalFieldOf("pieces", List.of()).forGetter(TvLayout::pieces)).apply(i, TvLayout::of));

    public static final TvLayout EMPTY = of(List.of());

    public TvLayout {
        pieces = List.copyOf(pieces);
        Set<TvBinding> distinct = new LinkedHashSet<>();
        for (TvPiece piece : pieces) {
            piece.binding().ifPresent(distinct::add);
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
        return new TvLayout(pieces, List.of());
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
        return of(clamped);
    }
}
