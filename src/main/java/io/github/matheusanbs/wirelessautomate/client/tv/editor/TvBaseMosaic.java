package io.github.matheusanbs.wirelessautomate.client.tv.editor;

import io.github.matheusanbs.wirelessautomate.client.GuiPaint;
import io.github.matheusanbs.wirelessautomate.tv.TvBackground;
import io.github.matheusanbs.wirelessautomate.tv.TvLayout;
import io.github.matheusanbs.wirelessautomate.tv.TvPixelArt;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * O fundo em mosaico na base da Tela (decisão 7 da spec da etapa 4) e o uso do teto de pixels: com "Mosaico" no ‹ ›
 * do Fundo, a Arte (‹ as artes que o layout já guarda e depois as da biblioteca que ele não tem ›; escolher uma da
 * biblioteca a copia para o layout), "Atualizar da biblioteca", a Escala (1× a 8×) e a Opacidade. Toda troca passa
 * pelo teto {@code tv.maxImagePixels} do {@link TvImageOps}; a linha "n / teto px" mostra o que as artes usadas somam.
 */
final class TvBaseMosaic {
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
        form.wideCycle("base.mosaic.art", () -> artName(ctx), delta -> stepArt(ctx, delta));
        form.add(new TvControls.Button(form.font, "base.mosaic.refresh", 0, form.y(), TvForm.W,
                TvPropsCtx.key("image.refresh"), () -> TvImageOps.refresh(ctx.doc(), ctx.actions().maxImagePixels(),
                        id(ctx))).enabledWhen(() -> TvImageOps.newer(ctx.doc().draft(), id(ctx)) != null)
                .offTip(() -> TvImageOps.whyNoRefresh(id(ctx))));
        form.endRow(TvControls.ROW - 1);
        form.cycle("base.mosaic.scale", TvPropsCtx.key("image.scale"), () -> TvPropsCtx.key("image.scale.times",
                background(ctx).scale()), delta -> set(ctx, "base.mosaic.scale", background(ctx).scale() + delta,
                        background(ctx).opacity()));
        int y = form.inline("base.mosaic.opacity", TvPropsCtx.key("style.opacity"));
        form.add(new TvSlider(form.font, "base.mosaic.opacity", TvForm.LABEL_W, y, TvForm.W - TvForm.LABEL_W, 0, 100,
                5, "%", () -> background(ctx).opacity(), v -> set(ctx, "base.mosaic.opacity", background(ctx).scale(),
                        v)));
        form.endRow(TvTextField.H);
    }

    /**
     * O ‹ › do Fundo chegou ao mosaico: põe a primeira arte que cabe no teto, na escala e na opacidade de padrão.
     * Devolve {@code false} sem nenhuma arte (o ‹ › pula o mosaico); sem nenhuma que caiba, avisa e nada muda.
     */
    static boolean start(TvPropsCtx ctx) {
        TvLayout now = ctx.doc().draft();
        List<TvPixelArt> choices = choices(now);
        if (choices.isEmpty()) {
            return false;
        }
        int max = ctx.actions().maxImagePixels();
        for (TvPixelArt art : choices) {
            TvLayout next = with(now, art, TvBackground.DEFAULT.scale(), TvBackground.DEFAULT.opacity());
            if (TvImageOps.fits(now, next, max)) {
                ctx.doc().changeLayout(layout -> next, "background");
                return true;
            }
        }
        ctx.doc().fail(TvEditorDoc.Failure.IMAGES_FULL);
        return true;
    }

    /** As artes do ‹ ›: as cópias do layout e as da biblioteca que ele não tem, nessa ordem. */
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

    private static Component artName(TvPropsCtx ctx) {
        UUID id = id(ctx);
        TvPixelArt art = id == null ? null : ctx.doc().draft().image(id);
        return art == null ? Component.literal("—")
                : TvPropsCtx.key("image.art.line", art.name(), art.width(), art.height());
    }

    private static void stepArt(TvPropsCtx ctx, int delta) {
        TvLayout now = ctx.doc().draft();
        List<TvPixelArt> choices = choices(now);
        if (choices.isEmpty()) {
            return;
        }
        UUID id = id(ctx);
        int at = -1;
        for (int i = 0; i < choices.size(); i++) {
            if (choices.get(i).id().equals(id)) {
                at = i;
            }
        }
        TvPixelArt art = choices.get(Math.floorMod(at + delta, choices.size()));
        TvBackground bg = now.background();
        TvImageOps.change(ctx.doc(), ctx.actions().maxImagePixels(), layout -> with(layout, art, bg.scale(),
                bg.opacity()), "background.art");
    }

    /** A escala (presa de 1 a 8 pelo {@link TvBackground}) e a opacidade do mosaico. */
    private static void set(TvPropsCtx ctx, String key, int scale, int opacity) {
        UUID id = id(ctx);
        if (id != null) {
            ctx.doc().changeLayout(layout -> layout.withBackground(TvBackground.tiled(id, scale, opacity)), key);
        }
    }
}
