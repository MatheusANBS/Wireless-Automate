package io.github.matheusanbs.wirelessautomate.client.tv.editor;

import io.github.matheusanbs.wirelessautomate.client.tv.render.TvArtCache;
import io.github.matheusanbs.wirelessautomate.tv.TvBackground;
import io.github.matheusanbs.wirelessautomate.tv.TvLayout;
import io.github.matheusanbs.wirelessautomate.tv.TvPartStyle;
import io.github.matheusanbs.wirelessautomate.tv.TvPiece;
import io.github.matheusanbs.wirelessautomate.tv.TvPieceType;
import io.github.matheusanbs.wirelessautomate.tv.TvPixelArt;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.UnaryOperator;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * As pixel arts e as imagens enviadas no rascunho do editor (decisões 4 a 7 da spec da etapa 4): a Tela guarda uma
 * cópia de cada arte que usa ({@link TvLayout#images()}, sem repetir o id), feita quando a arte vira Imagem ou mosaico
 * e trocada só pelo "Atualizar da biblioteca", e a referência de cada imagem enviada ({@link TvLayout#pictures()}).
 * Toda mudança passa pelo teto {@code tv.maxImagePixels} das artes e pelo de imagens distintas por Tela
 * ({@code tv.images.maxPerScreen}, {@link #pictureLimit}): o que contaria acima deles não entra, e o editor avisa
 * ({@link TvEditorDoc.Failure#IMAGES_FULL}, {@link TvEditorDoc.Failure#PICTURES_FULL}); nada vai ao servidor. Só no
 * clique, nunca por quadro. A arte e a imagem chegam aqui como {@link TvImageSource}.
 */
final class TvImageOps {
    /** O teto de imagens enviadas distintas por Tela, do servidor ({@code tv.images.maxPerScreen}), posto ao abrir. */
    static int pictureLimit = 8;

    private TvImageOps() {
    }

    /**
     * O layout com a cópia da arte. Se ele já tem uma do mesmo id, ela fica quando alguma peça ou o fundo a usa (a
     * Tela só muda pelo Atualizar); a que ninguém usa mais é trocada pela versão de agora.
     */
    static TvLayout withArt(TvLayout layout, TvPixelArt art) {
        TvPixelArt copy = layout.image(art.id());
        if (copy == null) {
            List<TvPixelArt> images = new ArrayList<>(layout.images());
            images.add(art);
            return layout.withImages(images);
        }
        return copy.equals(art) || layout.prunedImages().image(art.id()) != null ? layout : refreshed(layout, art);
    }

    /** O layout com a cópia de mesmo id trocada por {@code art}. */
    static TvLayout refreshed(TvLayout layout, TvPixelArt art) {
        List<TvPixelArt> images = new ArrayList<>(layout.images());
        images.replaceAll(image -> image.id().equals(art.id()) ? art : image);
        return layout.withImages(images);
    }

    /** Os pixels que contam no teto: os das artes que alguma peça ou o fundo usam (as soltas saem ao salvar). */
    static int usedPixels(TvLayout layout) {
        return layout.prunedImages().imagePixels();
    }

    /** A arte da biblioteca do jogador com o id, ou {@code null}. */
    static @Nullable TvPixelArt inLibrary(UUID id) {
        for (TvPixelArt art : TvLibraryClient.arts()) {
            if (art.id().equals(id)) {
                return art;
            }
        }
        return null;
    }

    /** A versão da biblioteca da arte {@code id} quando ela difere da cópia do layout, ou {@code null}. */
    static @Nullable TvPixelArt newer(TvLayout layout, @Nullable UUID id) {
        TvPixelArt library = id == null ? null : inLibrary(id);
        return library == null || library.equals(layout.image(id)) ? null : library;
    }

    /** A dica do "Atualizar da biblioteca" desligado: a arte saiu da biblioteca ou a cópia já é igual. */
    static Component whyNoRefresh(@Nullable UUID id) {
        if (id != null && id.version() == 8) {
            // O id de uma imagem enviada (TvImageHash.idOf): ela não muda, não há o que atualizar.
            return TvEditorText.key("image.refresh.picture");
        }
        return TvEditorText.key(id != null && inLibrary(id) != null ? "image.refresh.same" : "image.refresh.gone");
    }

    /** "Atualizar da biblioteca": troca a cópia da arte {@code id} pela versão da biblioteca, se há uma mais nova. */
    static void refresh(TvEditorDoc doc, int max, @Nullable UUID id) {
        TvPixelArt library = newer(doc.draft(), id);
        if (library != null) {
            change(doc, max, layout -> refreshed(layout, library), "image.refresh");
        }
    }

    /**
     * Se o layout {@code next} cabe no teto de pixels e no de artes distintas ({@link TvLayout#MAX_IMAGES}); um que
     * não aumenta o uso sempre vale (o teto pode ter baixado).
     */
    static boolean fits(TvLayout now, TvLayout next, int max) {
        int used = usedPixels(next);
        int arts = next.referencedImageCount();
        return (used <= max || used <= usedPixels(now))
                && (arts <= TvLayout.MAX_IMAGES || arts <= now.referencedImageCount()) && picturesFit(now, next);
    }

    /** Se o layout {@code next} cabe no teto de imagens enviadas; um que não aumenta o número sempre vale. */
    static boolean picturesFit(TvLayout now, TvLayout next) {
        int pictures = next.referencedPictureCount();
        return pictures <= pictureLimit || pictures <= now.referencedPictureCount();
    }

    /** O aviso de quando {@code next} não coube: o das imagens enviadas ou o dos pixels das artes. */
    private static void failFor(TvEditorDoc doc, TvLayout now, TvLayout next) {
        doc.fail(picturesFit(now, next) ? TvEditorDoc.Failure.IMAGES_FULL : TvEditorDoc.Failure.PICTURES_FULL);
    }

    /** As cópias das artes que as Imagens de {@code pieces} apontam e que {@code layout} tem (o copiar as leva). */
    static List<TvPixelArt> artsOf(TvLayout layout, List<TvPiece> pieces) {
        List<TvPixelArt> out = new ArrayList<>();
        for (TvPiece piece : pieces) {
            if (piece.type() != TvPieceType.IMAGE || piece.style().part().image().isEmpty()) {
                continue;
            }
            TvPixelArt art = layout.image(piece.style().part().image().get());
            if (art != null && !out.contains(art)) {
                out.add(art);
            }
        }
        return out;
    }

    /**
     * O layout com as artes que as Imagens e o mosaico apontam e que ele não tem (peças coladas de outra Tela, um
     * componente, um layout salvo), achadas nesta ordem: {@code from} (as cópias que vieram com as peças), as artes
     * das Telas por perto ({@link TvArtCache}) e a biblioteca do jogador. A que não está em lugar nenhum fica
     * faltando: a Imagem aparece sem arte e o servidor tira o id dela ao salvar. Devolve o próprio layout quando nada
     * falta.
     */
    static TvLayout withMissingArts(TvLayout layout, List<TvPixelArt> from) {
        Set<UUID> missing = layout.missingImages();
        if (missing.isEmpty()) {
            return layout;
        }
        List<TvPixelArt> images = new ArrayList<>(layout.images());
        for (UUID id : missing) {
            TvPixelArt art = null;
            for (TvPixelArt copy : from) {
                if (copy.id().equals(id)) {
                    art = copy;
                    break;
                }
            }
            if (art == null) {
                art = TvArtCache.byId(id);
            }
            if (art == null) {
                art = inLibrary(id);
            }
            if (art != null) {
                images.add(art);
            }
        }
        return images.size() == layout.images().size() ? layout : layout.withImages(images);
    }

    /**
     * Troca o rascunho por {@code next} com as artes que faltam completadas ({@link #withMissingArts}), numa mudança
     * só do desfazer; acima do teto nada muda e o aviso aparece. Devolve se mudou.
     */
    static boolean commitWithArts(TvEditorDoc doc, int max, TvLayout next, List<TvPixelArt> from,
            TvSelection selection) {
        return commit(doc, max, doc.draft(), withMissingArts(next, from), selection);
    }

    /**
     * Muda o rascunho por {@code change} (o fundo em mosaico, o Atualizar), mantendo a escolha; acima do teto, nada
     * muda e o aviso aparece. Devolve se mudou.
     */
    static boolean change(TvEditorDoc doc, int max, UnaryOperator<TvLayout> change, Object key) {
        TvLayout now = doc.draft();
        TvLayout next = change.apply(now);
        if (!fits(now, next, max)) {
            failFor(doc, now, next);
            return false;
        }
        doc.changeLayout(layout -> next, key);
        return true;
    }

    /**
     * A arte solta na miniatura (ou o botão da paleta de Peças arrastado) vira uma Imagem nova, por cima das outras
     * peças, com a escala inteira que cabe ({@link TvImageFit}) e o centro em ({@code cx}, {@code cy}); fica
     * escolhida. Acima do teto, nada muda e o aviso aparece.
     */
    static boolean insert(TvEditorDoc doc, int max, TvImageSource art, int cx, int cy) {
        TvPiece piece = imagePiece(doc, art, cx, cy);
        TvLayout now = doc.draft();
        List<TvPiece> pieces = new ArrayList<>(now.pieces());
        pieces.add(piece);
        return commit(doc, max, now, art.addTo(now).withPieces(pieces), TvSelection.of(pieces.size() - 1));
    }

    /** O clique no botão Imagem da paleta com uma arte escolhida: a Imagem nova no primeiro lugar livre. */
    static boolean insertFree(TvEditorDoc doc, int max, TvImageSource art) {
        TvLayout now = doc.draft();
        TvEditOps.Edit<TvPiece> edit = TvEditOps.insert(TvPieceAccess.INSTANCE, now.pieces(),
                imagePiece(doc, art, 0, 0), doc.area().pixelWidth(), doc.area().pixelHeight());
        return commit(doc, max, now, art.addTo(now).withPieces(edit.pieces()), edit.selection());
    }

    /**
     * A Imagem da arte (ou da imagem enviada) na escala do {@link TvImageFit}, alinhada ao meio, com a caixa centrada
     * em ({@code cx}, {@code cy}).
     */
    private static TvPiece imagePiece(TvEditorDoc doc, TvImageSource art, int cx, int cy) {
        int areaW = doc.area().pixelWidth();
        int areaH = doc.area().pixelHeight();
        int scale = TvImageFit.scale(art.width(), art.height(), areaW, areaH);
        int[] box = TvImageFit.box(art.width(), art.height(), scale, areaW, areaH, cx, cy);
        TvPiece piece = TvPieceAccess.create(TvPieceType.IMAGE, "");
        return piece.withBox(box[0], box[1], box[2], box[3]).withStyle(piece.style().withPart(piece.style().part()
                .withImage(Optional.of(art.id()), scale, TvPartStyle.VAlign.MIDDLE)));
    }

    /**
     * A grade de artes da seção Peça: as Imagens escolhidas passam a mostrar {@code art}, copiada para o layout; a
     * cópia antiga que nada mais usa sai. Uma Imagem que estava sem arte ganha a escala que cabe na caixa dela; as
     * outras ficam com a escala que tinham. Uma mudança do desfazer; acima do teto, nada muda e o aviso aparece.
     */
    static boolean choose(TvEditorDoc doc, int max, TvImageSource art) {
        TvLayout now = doc.draft();
        TvSelection selection = doc.selection();
        List<TvPiece> pieces = new ArrayList<>(now.pieces());
        boolean changed = false;
        for (int k = 0; k < selection.size(); k++) {
            int i = selection.get(k);
            TvPiece piece = i < pieces.size() ? pieces.get(i) : null;
            if (piece == null || piece.type() != TvPieceType.IMAGE
                    || piece.style().part().image().filter(art.id()::equals).isPresent()) {
                continue;
            }
            TvPartStyle part = piece.style().part();
            int scale = part.image().isPresent() ? part.scale()
                    : TvImageFit.scale(art.width(), art.height(), piece.width(), piece.height());
            pieces.set(i, piece.withStyle(piece.style().withPart(part.withImage(Optional.of(art.id()), scale,
                    part.valign()))));
            changed = true;
        }
        return changed && commit(doc, max, now, art.addTo(now).withPieces(pieces).withoutUnusedImages(),
                selection);
    }

    /** A grade de artes do fundo em mosaico: troca a arte, mantendo a escala e a opacidade; a cópia antiga que nada mais usa sai. */
    static boolean chooseMosaic(TvEditorDoc doc, int max, TvImageSource art) {
        TvLayout now = doc.draft();
        TvBackground bg = now.background();
        if (bg.image().filter(art.id()::equals).isPresent()) {
            return false;
        }
        TvLayout next = art.addTo(now).withBackground(TvBackground.tiled(art.id(), bg.scale(), bg.opacity()))
                .withoutUnusedImages();
        return commit(doc, max, now, next, doc.selection());
    }

    /** Troca o rascunho por {@code next} numa mudança só do desfazer, se cabe no teto; senão avisa. */
    private static boolean commit(TvEditorDoc doc, int max, TvLayout now, TvLayout next, TvSelection selection) {
        if (!fits(now, next, max)) {
            failFor(doc, now, next);
            return false;
        }
        doc.replace(next, selection);
        return true;
    }
}
