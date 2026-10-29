package io.github.matheusanbs.wirelessautomate.client.tv.editor;

import io.github.matheusanbs.wirelessautomate.client.GuiPaint;
import io.github.matheusanbs.wirelessautomate.tv.TvAlign;
import io.github.matheusanbs.wirelessautomate.tv.TvPartStyle;
import java.util.UUID;
import java.util.function.UnaryOperator;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * A seção "Peça" da aba Estilo para a Imagem (decisão 6 da spec da etapa 4): a arte, escolhida na {@link TvArtGrid}
 * (a Imagem sem arte diz "Escolha a arte"; o clique copia a arte para o layout pelo {@link TvImageOps}), "Atualizar
 * da biblioteca" (troca a cópia pela versão de agora da biblioteca do jogador, pelo
 * {@link TvImageOps}; desligado, com o porquê na dica, se a arte saiu da biblioteca ou a cópia já é igual), a escala
 * (‹ Esticar, 1×, 2×... ›), o alinhamento em 9 posições ({@link TvAlignGrid}) e a opacidade. A caixa da peça não muda
 * com a escala: a arte fica alinhada dentro dela e cortada pela borda.
 */
final class TvImageTab {
    private TvImageTab() {
    }

    static void build(TvForm form, TvPropsCtx ctx) {
        // sem arte o rótulo pede uma, em destaque; o estado é lido no refresh, junto do texto
        boolean[] pick = new boolean[1];
        form.add(new TvControls.Label(form.font, "image.art.title", 0, form.y(), TvForm.W,
                () -> pick[0] ? GuiPaint.ACCENT : GuiPaint.MUTED, () -> {
                    pick[0] = id(ctx) == null;
                    return TvPropsCtx.key(pick[0] ? "image.art.pick" : "image.art");
                }));
        form.skip(10);
        form.add(new TvArtGrid(form.font, "image.arts", 0, form.y(), ctx.doc(), () -> id(ctx),
                art -> TvImageOps.choose(ctx.doc(), ctx.actions().maxImagePixels(), art)));
        form.endRow(TvArtGrid.HEIGHT);
        form.add(new TvControls.Button(form.font, "image.refresh", 0, form.y(), TvForm.W,
                TvPropsCtx.key("image.refresh"), () -> TvImageOps.refresh(ctx.doc(), ctx.actions().maxImagePixels(),
                        id(ctx))).enabledWhen(() -> TvImageOps.newer(ctx.doc().draft(), id(ctx)) != null)
                .offTip(() -> TvImageOps.whyNoRefresh(id(ctx))));
        form.endRow(TvControls.ROW - 1);
        form.cycle("image.scale", TvPropsCtx.key("image.scale"), () -> scaleName(part(ctx).scale()),
                delta -> change(ctx, "image.scale", p -> p.withImage(p.image(),
                        Math.max(0, Math.min(TvPartStyle.MAX_SCALE, p.scale() + delta)), p.valign())));
        int y = form.inline("image.align", TvPropsCtx.key("image.align"), (TvAlignGrid.H - 8) / 2);
        form.add(new TvAlignGrid("image.align", TvForm.LABEL_W, y, TvPropsCtx.key("image.align.tip"),
                () -> part(ctx).valign().ordinal() * 3 + ctx.piece().style().align().ordinal(), index -> ctx.change(
                        "image.align", p -> p.withStyle(p.style().withAlign(TvAlign.values()[index % 3])
                                .withPart(p.style().part().withImage(p.style().part().image(), p.style().part()
                                        .scale(), TvPartStyle.VAlign.values()[index / 3]))))));
        form.endRow(TvAlignGrid.H);
        TvStyleTab.opacity(form, ctx);
    }

    private static TvPartStyle part(TvPropsCtx ctx) {
        return ctx.piece().style().part();
    }

    private static void change(TvPropsCtx ctx, String key, UnaryOperator<TvPartStyle> change) {
        ctx.change(key, p -> p.withStyle(p.style().withPart(change.apply(p.style().part()))));
    }

    private static Component scaleName(int scale) {
        return scale == 0 ? TvPropsCtx.key("image.scale.stretch") : TvPropsCtx.key("image.scale.times", scale);
    }

    private static @Nullable UUID id(TvPropsCtx ctx) {
        return part(ctx).image().orElse(null);
    }
}
