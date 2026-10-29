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
 * que a Imagem e o fundo em mosaico usam (cópias, sem repetir o id: a Tela não depende da biblioteca de quem a fez),
 * as imagens enviadas que elas usam ({@code pictures}: só o resumo e o tamanho, os bytes ficam no
 * {@link TvImageStore}; a peça aponta uma arte ou uma imagem pelo mesmo campo de id) e, derivadas, as ligações
 * distintas (primeiro as das peças, depois as das regras, na ordem em que aparecem) e as artes e as imagens pelo id.
 * As derivadas são calculadas uma vez no construtor, não por amostra nem por desenho; o NBT não as leva.
 */
public record TvLayout(List<TvPiece> pieces, TvTheme theme, TvBackground background, boolean scanlines,
        List<TvPixelArt> images, List<TvPicture> pictures, List<TvBinding> bindings, Map<UUID, TvPixelArt> imagesById,
        Map<UUID, TvPicture> picturesById) {
    public static final int HARD_CAP = 512;
    /**
     * O teto de artes distintas de um layout: a leitura não passa dele, e o salvar recusa o layout que aponta mais
     * ({@link #referencedImageCount}). O {@code tv.maxImagePixels} costuma pesar antes.
     */
    public static final int MAX_IMAGES = 256;
    /**
     * O teto de imagens enviadas na leitura de um layout, o máximo da config {@code tv.images.maxPerScreen}; o salvar
     * recusa acima da config.
     */
    public static final int MAX_PICTURES = 64;

    /** Lê cada imagem à parte: uma que não decodifica (resumo, formato ou tamanho inválidos) é largada. */
    private static final Codec<List<TvPicture>> LENIENT_PICTURES = Codec.PASSTHROUGH.listOf().xmap(
            dynamics -> {
                List<TvPicture> read = new ArrayList<>(Math.min(dynamics.size(), MAX_PICTURES));
                for (Dynamic<?> dynamic : dynamics) {
                    if (read.size() >= MAX_PICTURES) {
                        break;
                    }
                    TvPicture.CODEC.parse(dynamic).result().ifPresent(read::add);
                }
                return read;
            },
            pictures -> {
                List<Dynamic<?>> out = new ArrayList<>(pictures.size());
                for (TvPicture picture : pictures) {
                    out.add(new Dynamic<>(NbtOps.INSTANCE, TvPicture.CODEC.encodeStart(NbtOps.INSTANCE, picture)
                            .getOrThrow()));
                }
                return out;
            });

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
            LENIENT_IMAGES.lenientOptionalFieldOf("images", List.of()).forGetter(TvLayout::images),
            LENIENT_PICTURES.lenientOptionalFieldOf("pictures", List.of()).forGetter(TvLayout::pictures))
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
        Map<UUID, TvPicture> picturesByHash = new LinkedHashMap<>();
        for (TvPicture picture : pictures) {
            picturesByHash.putIfAbsent(picture.id(), picture);
        }
        pictures = List.copyOf(picturesByHash.values());
        picturesById = Map.copyOf(picturesByHash);
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
        return of(pieces, theme, background, scanlines, images, List.of());
    }

    public static TvLayout of(List<TvPiece> pieces, TvTheme theme, TvBackground background, boolean scanlines,
            List<TvPixelArt> images, List<TvPicture> pictures) {
        return new TvLayout(pieces, theme, background, scanlines, images, pictures, List.of(), Map.of(), Map.of());
    }

    public TvLayout withPieces(List<TvPiece> pieces) {
        return of(pieces, theme, background, scanlines, images, pictures);
    }

    public TvLayout withTheme(TvTheme theme) {
        return of(pieces, theme, background, scanlines, images, pictures);
    }

    public TvLayout withBackground(TvBackground background) {
        return of(pieces, theme, background, scanlines, images, pictures);
    }

    public TvLayout withScanlines(boolean scanlines) {
        return of(pieces, theme, background, scanlines, images, pictures);
    }

    public TvLayout withImages(List<TvPixelArt> images) {
        return of(pieces, theme, background, scanlines, images, pictures);
    }

    public TvLayout withPictures(List<TvPicture> pictures) {
        return of(pieces, theme, background, scanlines, images, pictures);
    }

    /** A arte do id, ou {@code null} se o layout não a tem. */
    public @Nullable TvPixelArt image(UUID id) {
        return imagesById.get(id);
    }

    /** A imagem enviada do id ({@link TvPicture#id}), ou {@code null} se o layout não a tem. */
    public @Nullable TvPicture picture(UUID id) {
        return picturesById.get(id);
    }

    /** Os resumos das imagens enviadas do layout, na ordem dele. */
    public List<String> pictureHashes() {
        if (pictures.isEmpty()) {
            return List.of();
        }
        List<String> out = new ArrayList<>(pictures.size());
        for (TvPicture picture : pictures) {
            out.add(picture.hash());
        }
        return out;
    }

    /** Se o id é de uma arte ou de uma imagem que o layout tem. */
    private boolean has(UUID id) {
        return imagesById.containsKey(id) || picturesById.containsKey(id);
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
     * Quantas artes distintas as Imagens e o fundo apontam, existam no layout ou não; acima de {@link #MAX_IMAGES} o
     * salvar recusa (a leitura cortaria as artes a mais e as Imagens delas ficariam sem arte, caladas).
     */
    public int referencedImageCount() {
        return referencedImages().size();
    }

    /** Os ids das artes que as Imagens e o fundo em mosaico apontam e que o layout não tem (nem como imagem). */
    public Set<UUID> missingImages() {
        Set<UUID> missing = referencedImages();
        missing.removeAll(imagesById.keySet());
        missing.removeAll(picturesById.keySet());
        return missing;
    }

    /** Quantas imagens enviadas distintas as Imagens e o fundo apontam (o {@code tv.images.maxPerScreen}). */
    public int referencedPictureCount() {
        int count = 0;
        for (UUID id : referencedImages()) {
            if (picturesById.containsKey(id)) {
                count++;
            }
        }
        return count;
    }

    /**
     * O layout com as artes que as peças e o fundo apontam e que ele não tem, tiradas de {@code from} (o layout que a
     * Tela já tem, ao salvar: o editor pode mandar uma Imagem cuja arte ainda não chegou a ele, e a cópia da Tela
     * fica). Devolve o próprio layout quando nada falta ou {@code from} não tem o que falta.
     */
    public TvLayout withMissingImagesFrom(TvLayout from) {
        if (from.images.isEmpty() && from.pictures.isEmpty()) {
            return this;
        }
        List<TvPixelArt> added = null;
        List<TvPicture> addedPictures = null;
        for (UUID id : referencedImages()) {
            if (has(id)) {
                continue;
            }
            TvPixelArt art = from.imagesById.get(id);
            if (art != null) {
                if (added == null) {
                    added = new ArrayList<>(images);
                }
                added.add(art);
            }
            TvPicture picture = from.picturesById.get(id);
            if (picture != null) {
                if (addedPictures == null) {
                    addedPictures = new ArrayList<>(pictures);
                }
                addedPictures.add(picture);
            }
        }
        if (added == null && addedPictures == null) {
            return this;
        }
        return of(pieces, theme, background, scanlines, added == null ? images : added,
                addedPictures == null ? pictures : addedPictures);
    }

    /**
     * O layout sem as artes que nenhuma Imagem nem o fundo apontam; diferente do {@link #prunedImages}, as peças e o
     * fundo ficam mesmo sem a arte (o servidor a completa pela Tela antes de resolver). Devolve o próprio layout
     * quando nada sai.
     */
    public TvLayout withoutUnusedImages() {
        if (images.isEmpty() && pictures.isEmpty()) {
            return this;
        }
        Set<UUID> referenced = referencedImages();
        List<TvPixelArt> kept = new ArrayList<>(images.size());
        for (TvPixelArt image : images) {
            if (referenced.contains(image.id())) {
                kept.add(image);
            }
        }
        List<TvPicture> keptPictures = usedPictures(referenced);
        if (kept.size() == images.size() && keptPictures.size() == pictures.size()) {
            return this;
        }
        return of(pieces, theme, background, scanlines, kept, keptPictures);
    }

    /** As imagens enviadas cujos ids estão em {@code used}, na ordem do layout. */
    private List<TvPicture> usedPictures(Set<UUID> used) {
        if (pictures.isEmpty()) {
            return pictures;
        }
        List<TvPicture> kept = new ArrayList<>(pictures.size());
        for (TvPicture picture : pictures) {
            if (used.contains(picture.id())) {
                kept.add(picture);
            }
        }
        return kept;
    }

    /**
     * O layout com as imagens resolvidas: a Imagem que aponta uma arte que o layout não tem perde o id e vira a Imagem
     * sem arte (a mesma que o editor cria pela paleta antes de escolher a arte: fica e não desenha), o fundo em
     * mosaico sem arte existente volta ao do tema, e as artes que nenhuma peça nem o fundo usam saem. Nenhuma peça sai.
     * Devolve o próprio layout quando nada muda.
     */
    public TvLayout prunedImages() {
        Set<UUID> used = new HashSet<>();
        List<TvPiece> kept = null;
        for (int i = 0; i < pieces.size(); i++) {
            TvPiece piece = pieces.get(i);
            if (piece.type() != TvPieceType.IMAGE) {
                continue;
            }
            Optional<UUID> id = piece.style().part().image();
            if (id.isEmpty()) {
                continue;
            }
            if (has(id.get())) {
                used.add(id.get());
                continue;
            }
            if (kept == null) {
                kept = new ArrayList<>(pieces);
            }
            TvPartStyle part = piece.style().part();
            kept.set(i, piece.withStyle(piece.style().withPart(part.withImage(Optional.empty(), part.scale(),
                    part.valign()))));
        }
        TvBackground bg = background;
        if (bg.kind() == TvBackground.Kind.TILED) {
            if (bg.image().isPresent() && has(bg.image().get())) {
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
        List<TvPicture> keptPictures = usedPictures(used);
        if (kept == null && bg == background && usedImages.size() == images.size()
                && keptPictures.size() == pictures.size()) {
            return this;
        }
        return of(kept == null ? pieces : kept, theme, bg, scanlines, usedImages, keptPictures);
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
        return of(clamped, theme, background, scanlines, images, pictures);
    }
}
