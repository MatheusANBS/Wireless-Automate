package io.github.matheusanbs.wirelessautomate.client.tv.editor;

import io.github.matheusanbs.wirelessautomate.client.tv.image.TvImageDisk;
import io.github.matheusanbs.wirelessautomate.client.tv.render.TvArtCache;
import io.github.matheusanbs.wirelessautomate.packet.TvLayoutPayload;
import io.github.matheusanbs.wirelessautomate.tv.TvArea;
import io.github.matheusanbs.wirelessautomate.tv.TvArtRef;
import io.github.matheusanbs.wirelessautomate.tv.TvBinding;
import io.github.matheusanbs.wirelessautomate.tv.TvBlockEntity;
import io.github.matheusanbs.wirelessautomate.tv.TvCodes;
import io.github.matheusanbs.wirelessautomate.tv.TvComponent;
import io.github.matheusanbs.wirelessautomate.tv.TvLayout;
import io.github.matheusanbs.wirelessautomate.tv.TvLibraryPicture;
import io.github.matheusanbs.wirelessautomate.tv.TvPicture;
import io.github.matheusanbs.wirelessautomate.tv.TvPiece;
import io.github.matheusanbs.wirelessautomate.tv.TvPixelArt;
import io.github.matheusanbs.wirelessautomate.tv.TvSavedLayouts;
import io.github.matheusanbs.wirelessautomate.tv.TvTheme;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * Exportar e importar layouts por código de texto ({@link TvCodes}), na base, embaixo da lista de layouts. Exportar
 * (o código comprimido) e Copiar como texto (o legível, em JSON) põem na área de transferência o layout do jogador
 * escolhido na lista ou, com um pronto escolhido, o desta Tela, com as pixel arts que ele usa: as do rascunho, as que
 * já chegaram ao {@link TvArtCache} e as da biblioteca, nessa ordem; se falta alguma, avisa e não copia. As imagens
 * enviadas vão com os bytes do cache em disco (lidos fora da thread de render) se couberem no teto do texto; senão só
 * com os resumos, e o aviso diz que o layout só funciona onde as imagens já existem. Importar lê a área de
 * transferência (as duas formas; um código {@code WA-IMAGEM1} vira um envio de imagem, {@link TvPictureSend}), envia
 * as imagens que vieram com bytes e não estão na biblioteca, e aplica o layout no rascunho como uma mudança desfazível
 * ({@code layout.import}), com as peças escaladas da área de origem para esta e sem as ligações a redes e
 * armazenamentos que este jogador e esta Tela não têm (com o aviso de quantas saíram), só se ele cabe nos tetos que o
 * cliente conhece ({@code maxLayoutBytes} e {@code maxImagePixels}); o servidor valida no Salvar, como sempre. Tudo
 * só no clique; os avisos vão pelo {@link TvCodeNotice}.
 */
final class TvLayoutCode {
    private TvLayoutCode() {
    }

    static void add(TvForm form, TvPropsCtx ctx) {
        int half = (TvForm.W - 2) / 2;
        form.add(new CodeButton(form.font, "base.layout.export", 0, form.y(), half, () -> copy(ctx, false)));
        form.add(new CodeButton(form.font, "base.layout.import", TvForm.W - half, form.y(), half, () -> paste(ctx)));
        form.endRow(TvControls.ROW - 1);
        form.add(new CodeButton(form.font, "base.layout.text", 0, form.y(), TvForm.W, () -> copy(ctx, true)));
        form.endRow(TvControls.ROW - 1);
    }

    /** Se as chaves do código legível saem em inglês: só com o jogo num idioma que não é o português. */
    static boolean english() {
        return !Minecraft.getInstance().getLanguageManager().getSelected().startsWith("pt");
    }

    private static void copy(TvPropsCtx ctx, boolean text) {
        TvEditorDoc doc = ctx.doc();
        TvComponent saved = TvLayoutPicker.mine(ctx);
        boolean fromSaved = saved != null && saved.layout().isPresent();
        TvLayout layout = doc.draft();
        int w = doc.area().pixelWidth();
        int h = doc.area().pixelHeight();
        if (fromSaved) {
            TvComponent.Layout from = saved.layout().get();
            layout = TvLayout.of(saved.pieces(), TvTheme.DEFAULT, from.background(), from.scanlines(), List.of(),
                    saved.pictures());
            w = from.width();
            h = from.height();
        }
        TvLayout full = withArts(layout.withoutUnusedImages(), doc);
        if (full == null) {
            TvCodeNotice.show(TvCodeNotice.Kind.MISSING_ART);
            return;
        }
        int fw = w;
        int fh = h;
        if (full.pictures().isEmpty()) {
            finishCopy(full, fw, fh, fromSaved, text, Map.of(), false);
            return;
        }
        List<TvPicture> pictures = full.pictures();
        List<CompletableFuture<byte[]>> reads = new ArrayList<>(pictures.size());
        for (TvPicture picture : pictures) {
            reads.add(TvImageDisk.read(picture.hash()));
        }
        CompletableFuture.allOf(reads.toArray(new CompletableFuture<?>[0])).whenComplete((done, error) ->
                Minecraft.getInstance().execute(() -> {
                    Map<String, byte[]> data = new LinkedHashMap<>();
                    for (int i = 0; i < pictures.size(); i++) {
                        byte[] bytes = reads.get(i).getNow(null);
                        if (bytes != null) {
                            data.put(pictures.get(i).hash(), bytes);
                        }
                    }
                    finishCopy(full, fw, fh, fromSaved, text, data, data.size() < pictures.size());
                }));
    }

    /**
     * Põe o código na área de transferência: com os bytes das imagens se todas estão aqui e cabem; senão só com os
     * resumos, avisando.
     */
    private static void finishCopy(TvLayout layout, int w, int h, boolean fromSaved, boolean text,
            Map<String, byte[]> data, boolean missing) {
        String code = missing ? null : text ? TvCodes.layoutText(layout, w, h, fromSaved, english(), data)
                : TvCodes.layout(layout, w, h, fromSaved, data);
        boolean byReference = code == null && !layout.pictures().isEmpty();
        if (code == null) {
            code = text ? TvCodes.layoutText(layout, w, h, fromSaved, english()) : TvCodes.layout(layout, w, h,
                    fromSaved);
        }
        if (code == null) {
            TvCodeNotice.show(TvCodeNotice.Kind.TOO_BIG);
            return;
        }
        Minecraft.getInstance().keyboardHandler.setClipboard(code);
        TvCodeNotice.show(byReference ? TvCodeNotice.Kind.PICTURES_BY_REFERENCE : TvCodeNotice.Kind.COPIED);
    }

    /** O layout com as artes que ele aponta e não tem, achadas no rascunho, no cache ou na biblioteca; ou nulo. */
    private static @Nullable TvLayout withArts(TvLayout layout, TvEditorDoc doc) {
        Set<UUID> missing = layout.missingImages();
        if (missing.isEmpty()) {
            return layout;
        }
        List<TvArtRef> refs = Minecraft.getInstance().level != null
                && Minecraft.getInstance().level.getBlockEntity(doc.pos()) instanceof TvBlockEntity tv
                ? tv.artRefs() : List.of();
        List<TvPixelArt> images = new ArrayList<>(layout.images());
        for (UUID id : missing) {
            TvPixelArt art = doc.draft().image(id);
            for (int i = 0; art == null && i < refs.size(); i++) {
                if (refs.get(i).id().equals(id)) {
                    art = TvArtCache.find(refs.get(i));
                }
            }
            if (art == null) {
                art = TvImageOps.inLibrary(id);
            }
            if (art == null) {
                return null;
            }
            images.add(art);
        }
        return layout.withImages(images);
    }

    private static void paste(TvPropsCtx ctx) {
        String clipboard = Minecraft.getInstance().keyboardHandler.getClipboard();
        if (TvPictureSend.pasteIfImage(ctx.actions(), clipboard)) {
            return;
        }
        TvCodes.Read<TvCodes.Layout> result = TvCodes.readLayout(clipboard);
        TvCodes.Layout code = result.value();
        if (code == null) {
            TvCodeNotice.failed(result);
            return;
        }
        TvEditorDoc doc = ctx.doc();
        TvArea area = doc.area();
        TvLayout read = code.layout();
        List<TvPiece> pieces = code.needsScale(area.pixelWidth(), area.pixelHeight())
                ? TvSavedLayouts.scale(read.pieces(), code.width(), code.height(), area.pixelWidth(),
                        area.pixelHeight())
                : read.pieces();
        TvTheme theme = code.keepTheme() ? doc.draft().theme() : read.theme();
        TvCodes.Pruned pruned = TvCodes.withoutBindings(TvLayout.of(pieces, theme, read.background(),
                read.scanlines(), read.images(), read.pictures()), binding -> available(binding, ctx));
        TvLayout next = pruned.layout().clampedTo(area).prunedImages();
        if (!fits(next, ctx.actions())) {
            TvCodeNotice.show(TvCodeNotice.Kind.TOO_BIG);
            return;
        }
        // Cada importar é uma mudança própria no desfazer: importar de novo depois do erro e desfazer volta ao primeiro.
        doc.changeLayout(layout -> next, null);
        doc.select(TvSelection.EMPTY, 0);
        sendPictures(ctx.actions(), code.pictureData());
        int dropped = code.dropped() + pruned.removed();
        if (dropped > 0) {
            TvCodeNotice.show(TvCodeNotice.Kind.BINDINGS_DROPPED, dropped);
        }
    }

    /**
     * As imagens que vieram com bytes no código e não estão na biblioteca do jogador vão ao servidor (o que já tem o
     * resumo só registra o uso), para o layout colado funcionar aqui.
     */
    private static void sendPictures(TvEditorActions actions, Map<String, byte[]> data) {
        if (data.isEmpty() || !actions.images().canUpload()) {
            return;
        }
        for (Map.Entry<String, byte[]> entry : data.entrySet()) {
            boolean have = false;
            for (TvLibraryPicture picture : TvLibraryClient.pictures()) {
                have |= picture.picture().hash().equals(entry.getKey());
            }
            if (!have) {
                TvPictureSend.send(actions, entry.getValue(), "");
            }
        }
    }

    /** Se a ligação aponta para o que o jogador e a Tela têm: uma rede dele, um armazenamento ligado, o relógio. */
    private static boolean available(TvBinding binding, TvPropsCtx ctx) {
        return switch (binding.source()) {
            case NETWORK -> ctx.networks().stream().anyMatch(n -> n.id().equals(binding.network()));
            case STORAGE -> ctx.data().links().stream().anyMatch(l -> l.pos().equals(binding.storage().pos()));
            case CLOCK -> true;
        };
    }

    /** Se o layout cabe nos tetos que o cliente conhece: pixels das artes, bytes sem os pixels e o pacote de salvar. */
    private static boolean fits(TvLayout layout, TvEditorActions actions) {
        return layout.imagePixels() <= actions.maxImagePixels()
                && layout.referencedImageCount() <= TvLayout.MAX_IMAGES
                && layout.referencedPictureCount() <= actions.images().maxPerScreen()
                && TvLayoutPayload.clientSize(layout) <= actions.maxLayoutBytes()
                && TvLayoutPayload.encodedSize(layout) <= TvLayoutPayload.MAX_BYTES;
    }

    /** Um botão de texto com a dica do que ele faz ({@code id + ".tip"}). */
    private static final class CodeButton extends TvControl {
        private final Font font;
        private final TvEditorPaint.Label label;
        private final Component tip;
        private final Runnable action;

        CodeButton(Font font, String id, int x, int y, int w, Runnable action) {
            super(id, x, y, w, TvControls.ROW - 1);
            this.font = font;
            this.label = TvEditorPaint.label(font, TvPropsCtx.key(id), w - 4);
            this.tip = TvPropsCtx.key(id + ".tip");
            this.action = action;
        }

        @Override
        void render(GuiGraphics g, int mouseX, int mouseY) {
            TvEditorPaint.button(g, font, ax, ay, w, h, label, contains(mouseX, mouseY), true);
        }

        @Override
        @Nullable Component tooltip(double mouseX, double mouseY) {
            return contains(mouseX, mouseY) ? tip : null;
        }

        @Override
        boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (!contains(mouseX, mouseY)) {
                return false;
            }
            action.run();
            return true;
        }
    }
}
