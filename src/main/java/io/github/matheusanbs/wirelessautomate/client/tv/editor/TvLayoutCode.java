package io.github.matheusanbs.wirelessautomate.client.tv.editor;

import io.github.matheusanbs.wirelessautomate.client.tv.render.TvArtCache;
import io.github.matheusanbs.wirelessautomate.packet.TvLayoutPayload;
import io.github.matheusanbs.wirelessautomate.tv.TvArea;
import io.github.matheusanbs.wirelessautomate.tv.TvArtRef;
import io.github.matheusanbs.wirelessautomate.tv.TvBinding;
import io.github.matheusanbs.wirelessautomate.tv.TvBlockEntity;
import io.github.matheusanbs.wirelessautomate.tv.TvCodes;
import io.github.matheusanbs.wirelessautomate.tv.TvComponent;
import io.github.matheusanbs.wirelessautomate.tv.TvLayout;
import io.github.matheusanbs.wirelessautomate.tv.TvPiece;
import io.github.matheusanbs.wirelessautomate.tv.TvPixelArt;
import io.github.matheusanbs.wirelessautomate.tv.TvSavedLayouts;
import io.github.matheusanbs.wirelessautomate.tv.TvTheme;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * Exportar e importar layouts por código de texto ({@link TvCodes}), na base, embaixo da lista de layouts. Exportar
 * (o código comprimido) e Copiar como texto (o legível, em JSON) põem na área de transferência o layout do jogador
 * escolhido na lista ou, com um pronto escolhido, o desta Tela, com as pixel arts que ele usa: as do rascunho, as que
 * já chegaram ao {@link TvArtCache} e as da biblioteca, nessa ordem; se falta alguma, avisa e não copia. Importar lê
 * a área de transferência (as duas formas) e aplica o layout no rascunho como uma mudança desfazível
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
            layout = TvLayout.of(saved.pieces(), TvTheme.DEFAULT, from.background(), from.scanlines());
            w = from.width();
            h = from.height();
        }
        TvLayout full = withArts(layout.withoutUnusedImages(), doc);
        if (full == null) {
            TvCodeNotice.show(TvCodeNotice.Kind.MISSING_ART);
            return;
        }
        String code = text ? TvCodes.layoutText(full, w, h, fromSaved, english())
                : TvCodes.layout(full, w, h, fromSaved);
        if (code == null) {
            TvCodeNotice.show(TvCodeNotice.Kind.TOO_BIG);
            return;
        }
        Minecraft.getInstance().keyboardHandler.setClipboard(code);
        TvCodeNotice.show(TvCodeNotice.Kind.COPIED);
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
        TvCodes.Read<TvCodes.Layout> result = TvCodes.readLayout(Minecraft.getInstance().keyboardHandler
                .getClipboard());
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
                read.scanlines(), read.images()), binding -> available(binding, ctx));
        TvLayout next = pruned.layout().clampedTo(area).prunedImages();
        if (!fits(next, ctx.actions())) {
            TvCodeNotice.show(TvCodeNotice.Kind.TOO_BIG);
            return;
        }
        // Cada importar é uma mudança própria no desfazer: importar de novo depois do erro e desfazer volta ao primeiro.
        doc.changeLayout(layout -> next, null);
        doc.select(TvSelection.EMPTY, 0);
        int dropped = code.dropped() + pruned.removed();
        if (dropped > 0) {
            TvCodeNotice.show(TvCodeNotice.Kind.BINDINGS_DROPPED, dropped);
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
