package io.github.matheusanbs.wirelessautomate.tv;

import com.mojang.serialization.Codec;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.nbt.NbtOps;
import org.jetbrains.annotations.Nullable;

/**
 * O layout de uma Tela de Sinal: as peças na ordem de desenho, o tema, o fundo, as linhas de varredura, as pixel arts
 * que a Imagem e o fundo em mosaico usam (cópias, sem repetir o id: a Tela não depende da biblioteca de quem a fez)
 * e, derivadas, as ligações distintas (primeiro as das peças, depois as das regras, na ordem em que aparecem) e as
 * artes pelo id. As duas são calculadas uma vez no construtor, não por amostra nem por desenho; o NBT não as leva.
 */
public record TvLayout(List<TvPiece> pieces, TvTheme theme, TvBackground background, boolean scanlines,
        List<TvPixelArt> images, List<TvBinding> bindings, Map<UUID, TvPixelArt> imagesById) {
    public static final int HARD_CAP = 512;
    /** O teto de artes lidas de um layout; o que vale de verdade é o {@code tv.maxImagePixels} ao salvar. */
    public static final int MAX_IMAGES = 256;

    /** Lê cada arte à parte: uma que não decodifica (tamanho, paleta ou pixels inválidos) é largada. */
    private static final Codec<List<TvPixelArt>> LENIENT_IMAGES = Codec.PASSTHROUGH.listOf().xmap(
            dynamics -> {
                List<TvPixelArt> read = new ArrayList<>(Math.min(dynamics.size(), MAX_IMAGES));
                for (Dynamic<?> dynamic : dynamics) {
                    if (read.size() >= MAX_IMAGES) {
                        break;
                    }
                    TvPixelArt.CODEC.parse(dynamic).result().ifPresent(read::add);
                }
                return read;
            },
            images -> {
                List<Dynamic<?>> out = new ArrayList<>(images.size());
                for (TvPixelArt image : images) {
                    out.add(new Dynamic<>(NbtOps.INSTANCE, TvPixelArt.CODEC.encodeStart(NbtOps.INSTANCE, image)
                            .getOrThrow()));
                }
                return out;
            });

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

    /**
     * As chaves da etapa 2 (tema, fundo e linhas) leem de forma tolerante: um valor que esta versão não conhece (um
     * fundo novo, um tema com campo a mais, de um jar mais novo que voltou de versão) cai no padrão em vez de derrubar
     * o layout inteiro, que o próximo save gravaria vazio.
     */
    public static final Codec<TvLayout> CODEC = RecordCodecBuilder.create(i -> i.group(
            LENIENT_PIECES.optionalFieldOf("pieces", List.of()).forGetter(TvLayout::pieces),
            TvCodecs.THEME.lenientOptionalFieldOf("theme", TvTheme.DEFAULT).forGetter(TvLayout::theme),
            TvBackground.CODEC.lenientOptionalFieldOf("background", TvBackground.DEFAULT).forGetter(TvLayout::background),
            Codec.BOOL.lenientOptionalFieldOf("scanlines", false).forGetter(TvLayout::scanlines),
            LENIENT_IMAGES.lenientOptionalFieldOf("images", List.of()).forGetter(TvLayout::images))
            .apply(i, TvLayout::of));

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
        Map<UUID, TvPixelArt> byId = new LinkedHashMap<>();
        for (TvPixelArt image : images) {
            byId.putIfAbsent(image.id(), image);
        }
        images = List.copyOf(byId.values());
        imagesById = Map.copyOf(byId);
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
        return of(pieces, theme, background, scanlines, List.of());
    }

    public static TvLayout of(List<TvPiece> pieces, TvTheme theme, TvBackground background, boolean scanlines,
            List<TvPixelArt> images) {
        return new TvLayout(pieces, theme, background, scanlines, images, List.of(), Map.of());
    }

    public TvLayout withPieces(List<TvPiece> pieces) {
        return of(pieces, theme, background, scanlines, images);
    }

    public TvLayout withTheme(TvTheme theme) {
        return of(pieces, theme, background, scanlines, images);
    }

    public TvLayout withBackground(TvBackground background) {
        return of(pieces, theme, background, scanlines, images);
    }

    public TvLayout withScanlines(boolean scanlines) {
        return of(pieces, theme, background, scanlines, images);
    }

    public TvLayout withImages(List<TvPixelArt> images) {
        return of(pieces, theme, background, scanlines, images);
    }

    /** A arte do id, ou {@code null} se o layout não a tem. */
    public @Nullable TvPixelArt image(UUID id) {
        return imagesById.get(id);
    }

    /** Os pixels somados das artes do layout (cada id uma vez), o que conta no {@code tv.maxImagePixels}. */
    public int imagePixels() {
        int sum = 0;
        for (TvPixelArt image : images) {
            sum += image.pixelCount();
        }
        return sum;
    }

    /** Os ids das artes que as Imagens e o fundo em mosaico apontam, existam no layout ou não. */
    private Set<UUID> referencedImages() {
        Set<UUID> ids = new HashSet<>();
        for (TvPiece piece : pieces) {
            if (piece.type() == TvPieceType.IMAGE) {
                piece.style().part().image().ifPresent(ids::add);
            }
        }
        if (background.kind() == TvBackground.Kind.TILED) {
            background.image().ifPresent(ids::add);
        }
        return ids;
    }

    /**
     * O layout com as artes que as peças e o fundo apontam e que ele não tem, tiradas de {@code from} (o layout que a
     * Tela já tem, ao salvar: o editor pode mandar uma Imagem cuja arte ainda não chegou a ele, e a cópia da Tela
     * fica). Devolve o próprio layout quando nada falta ou {@code from} não tem o que falta.
     */
    public TvLayout withMissingImagesFrom(TvLayout from) {
        if (from.images.isEmpty()) {
            return this;
        }
        List<TvPixelArt> added = null;
        for (UUID id : referencedImages()) {
            TvPixelArt art = from.imagesById.get(id);
            if (art != null && !imagesById.containsKey(id)) {
                if (added == null) {
                    added = new ArrayList<>(images);
                }
                added.add(art);
            }
        }
        return added == null ? this : withImages(added);
    }

    /**
     * O layout sem as artes que nenhuma Imagem nem o fundo apontam; diferente do {@link #prunedImages}, as peças e o
     * fundo ficam mesmo sem a arte (o servidor a completa pela Tela antes de resolver). Devolve o próprio layout
     * quando nada sai.
     */
    public TvLayout withoutUnusedImages() {
        if (images.isEmpty()) {
            return this;
        }
        Set<UUID> referenced = referencedImages();
        List<TvPixelArt> kept = new ArrayList<>(images.size());
        for (TvPixelArt image : images) {
            if (referenced.contains(image.id())) {
                kept.add(image);
            }
        }
        return kept.size() == images.size() ? this : withImages(kept);
    }

    /**
     * O layout com as imagens resolvidas: a Imagem sem arte ou com uma arte que o layout não tem sai, o fundo em
     * mosaico sem arte existente volta ao do tema, e as artes que nenhuma peça nem o fundo usam saem. Devolve o
     * próprio layout quando nada muda.
     */
    public TvLayout prunedImages() {
        Set<UUID> used = new HashSet<>();
        List<TvPiece> kept = new ArrayList<>(pieces.size());
        for (TvPiece piece : pieces) {
            if (piece.type() == TvPieceType.IMAGE) {
                Optional<UUID> id = piece.style().part().image();
                if (id.isEmpty() || !imagesById.containsKey(id.get())) {
                    continue;
                }
                used.add(id.get());
            }
            kept.add(piece);
        }
        TvBackground bg = background;
        if (bg.kind() == TvBackground.Kind.TILED) {
            if (bg.image().isPresent() && imagesById.containsKey(bg.image().get())) {
                used.add(bg.image().get());
            } else {
                bg = TvBackground.DEFAULT;
            }
        }
        List<TvPixelArt> usedImages = new ArrayList<>(used.size());
        for (TvPixelArt image : images) {
            if (used.contains(image.id())) {
                usedImages.add(image);
            }
        }
        if (kept.size() == pieces.size() && bg == background && usedImages.size() == images.size()) {
            return this;
        }
        return of(kept, theme, bg, scanlines, usedImages);
    }

    /**
     * Se o layout é o vazio da etapa 1 ({@link #EMPTY}: sem peças, com o tema, o fundo e as linhas de padrão): a
     * Tela não desenha nada e mostra o vidro apagado. Com qualquer outra coisa (inclusive só um tema ou um fundo
     * escolhido no editor) ela desenha. Barato: a primeira comparação já decide numa Tela com peças.
     */
    public boolean blank() {
        return pieces.isEmpty() && !scanlines && theme.equals(TvTheme.DEFAULT)
                && background.equals(TvBackground.DEFAULT);
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
        return of(clamped, theme, background, scanlines, images);
    }
}
