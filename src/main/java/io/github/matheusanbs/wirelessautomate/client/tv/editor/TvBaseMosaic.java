package io.github.matheusanbs.wirelessautomate.client.tv.editor;

import io.github.matheusanbs.wirelessautomate.client.GuiPaint;
import io.github.matheusanbs.wirelessautomate.tv.TvBackground;
import io.github.matheusanbs.wirelessautomate.tv.TvLayout;
import io.github.matheusanbs.wirelessautomate.tv.TvPixelArt;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;
import org.jetbrains.annotations.Nullable;

/**
 * O fundo em mosaico na base da Tela (decisão 7 da spec da etapa 4) e o uso do teto de pixels: com "Mosaico" na lista
 * do Fundo, a Arte (na {@link TvArtGrid}: as da biblioteca e as que só a Tela tem; escolher uma a copia para o
 * layout), "Atualizar da biblioteca", a Escala (1× a 8×) e a Opacidade. Toda troca passa
 * pelo teto {@code tv.maxImagePixels} do {@link TvImageOps}; a linha "n / teto px" mostra o que as artes usadas somam.
 */
final class TvBaseMosaic {
    /** As escalas da lista, as que o {@link TvBackground} aceita (1× a 8×). */
    private static final List<Integer> SCALES = IntStream.rangeClosed(TvBackground.MIN_SCALE, TvBackground.MAX_SCALE)
            .boxed().toList();

    private TvBaseMosaic() {
    }

    /** A linha do uso do teto de pixels, sempre na base. */
    static void usage(TvForm form, TvPropsCtx ctx) {
        form.add(new TvControls.Label(form.font, "base.pixels", 0, form.y(), TvForm.W, TvControls.MUTED,
                () -> TvPropsCtx.key("base.pixels", TvImageOps.usedPixels(ctx.doc().draft()),
                        ctx.actions().maxImagePixels())));
        form.endRow(8);
    }

    static void build(TvForm form, TvPropsCtx ctx) {
        form.label("base.mosaic.art.title", TvPropsCtx.key("base.mosaic.art"));
        form.add(new TvArtGrid(form.font, "base.mosaic.arts", 0, form.y(), ctx.doc(), () -> id(ctx),
                art -> TvImageOps.chooseMosaic(ctx.doc(), ctx.actions().maxImagePixels(), art)));
        form.endRow(TvArtGrid.HEIGHT);
        form.add(new TvControls.Button(form.font, "base.mosaic.refresh", 0, form.y(), TvForm.W,
                TvPropsCtx.key("image.refresh"), () -> TvImageOps.refresh(ctx.doc(), ctx.actions().maxImagePixels(),
                        id(ctx))).enabledWhen(() -> TvImageOps.newer(ctx.doc().draft(), id(ctx)) != null)
                .offTip(() -> TvImageOps.whyNoRefresh(id(ctx))));
        form.endRow(TvControls.ROW - 1);
        form.dropdown("base.mosaic.scale", TvPropsCtx.key("image.scale"), () -> SCALES,
                scale -> TvPropsCtx.key("image.scale.times", scale), () -> background(ctx).scale(),
                scale -> set(ctx, "base.mosaic.scale", scale, background(ctx).opacity()));
        int y = form.inline("base.mosaic.opacity", TvPropsCtx.key("style.opacity"));
        form.add(new TvSlider(form.font, "base.mosaic.opacity", TvForm.LABEL_W, y, TvForm.W - TvForm.LABEL_W, 0, 100,
                5, "%", () -> background(ctx).opacity(), v -> set(ctx, "base.mosaic.opacity", background(ctx).scale(),
                        v)));
        form.endRow(TvTextField.H);
    }

    /** Se o Fundo pode ser o mosaico: há ao menos uma arte, no layout ou na biblioteca. */
    static boolean available(TvLayout layout) {
        return !layout.images().isEmpty() || !TvLibraryClient.arts().isEmpty();
    }

    /**
     * O mosaico escolhido na lista do Fundo: põe a primeira arte que cabe no teto, na escala e na opacidade de padrão.
     * Sem nenhuma arte, ou sem nenhuma que caiba (aí com o aviso), nada muda.
     */
    static void start(TvPropsCtx ctx) {
        TvLayout now = ctx.doc().draft();
        List<TvPixelArt> choices = choices(now);
        if (choices.isEmpty()) {
            return;
        }
        int max = ctx.actions().maxImagePixels();
        for (TvPixelArt art : choices) {
            TvLayout next = with(now, art, TvBackground.DEFAULT.scale(), TvBackground.DEFAULT.opacity());
            if (TvImageOps.fits(now, next, max)) {
                ctx.doc().changeLayout(layout -> next, "background");
                return;
            }
        }
        ctx.doc().fail(TvEditorDoc.Failure.IMAGES_FULL);
    }

    /** As artes que o mosaico pode pegar: as cópias do layout e as da biblioteca que ele não tem, nessa ordem. */
    private static List<TvPixelArt> choices(TvLayout layout) {
        List<TvPixelArt> library = TvLibraryClient.arts();
        List<TvPixelArt> out = new ArrayList<>(layout.images().size() + library.size());
        out.addAll(layout.images());
        for (TvPixelArt art : library) {
            if (layout.image(art.id()) == null) {
                out.add(art);
            }
        }
        return out;
    }

    private static TvLayout with(TvLayout layout, TvPixelArt art, int scale, int opacity) {
        return TvImageOps.withArt(layout, art).withBackground(TvBackground.tiled(art.id(), scale, opacity));
    }

    private static TvBackground background(TvPropsCtx ctx) {
        return ctx.doc().draft().background();
    }

    private static @Nullable UUID id(TvPropsCtx ctx) {
        return background(ctx).image().orElse(null);
    }

    /** A escala (presa de 1 a 8 pelo {@link TvBackground}) e a opacidade do mosaico. */
    private static void set(TvPropsCtx ctx, String key, int scale, int opacity) {
        UUID id = id(ctx);
        if (id != null) {
            ctx.doc().changeLayout(layout -> layout.withBackground(TvBackground.tiled(id, scale, opacity)), key);
        }
    }
}
