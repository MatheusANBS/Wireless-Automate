package io.github.matheusanbs.wirelessautomate.client.tv.editor;

import io.github.matheusanbs.wirelessautomate.client.GuiPaint;
import io.github.matheusanbs.wirelessautomate.tv.TvAlign;
import io.github.matheusanbs.wirelessautomate.tv.TvArea;
import io.github.matheusanbs.wirelessautomate.tv.TvFont;
import io.github.matheusanbs.wirelessautomate.tv.TvPiece;
import io.github.matheusanbs.wirelessautomate.tv.TvPieceType;
import io.github.matheusanbs.wirelessautomate.tv.TvStyle;
import java.util.List;
import java.util.function.UnaryOperator;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/**
 * A aba Estilo (decisão 12 da spec): posição e tamanho em pixels, nome, as cores (do tema ou fixas), a borda, os
 * cantos, a opacidade e, no Valor e no Texto, a fonte, o tamanho (ou o máximo, com "caber no quadro"), o alinhamento e,
 * no Texto, o texto. Com várias peças escolhidas só vale o que é de todas: a "Cor de tudo" e a opacidade.
 */
final class TvStyleTab {
    private TvStyleTab() {
    }

    static void build(TvForm form, TvPropsCtx ctx) {
        if (ctx.doc().selection().size() > 1) {
            buildMany(form, ctx);
            return;
        }
        TvPieceType type = ctx.piece().type();
        TvEditorDoc doc = ctx.doc();
        TvArea area = doc.area();
        form.number("style.x", TvPropsCtx.key("style.x"), 0, area.pixelWidth() - 1, 1, () -> ctx.piece().x(),
                v -> ctx.change("style.x", p -> p.withBox(Mth.clamp(v, 0, doc.area().pixelWidth() - p.width()),
                        p.y(), p.width(), p.height())));
        form.number("style.y", TvPropsCtx.key("style.y"), 0, area.pixelHeight() - 1, 1, () -> ctx.piece().y(),
                v -> ctx.change("style.y", p -> p.withBox(p.x(), Mth.clamp(v, 0, doc.area().pixelHeight()
                        - p.height()), p.width(), p.height())));
        form.number("style.width", TvPropsCtx.key("style.width"), 1, area.pixelWidth(), 1,
                () -> ctx.piece().width(), v -> ctx.change("style.width", p -> p.withBox(p.x(), p.y(),
                        Mth.clamp(v, 1, doc.area().pixelWidth() - p.x()), p.height())));
        form.number("style.height", TvPropsCtx.key("style.height"), 1, area.pixelHeight(), 1,
                () -> ctx.piece().height(), v -> ctx.change("style.height", p -> p.withBox(p.x(), p.y(), p.width(),
                        Mth.clamp(v, 1, doc.area().pixelHeight() - p.y()))));
        form.text("style.name", TvPropsCtx.key("style.name"), TvPiece.MAX_NAME, null,
                TvPropsCtx.key("style.name.hint"), () -> ctx.piece().name(),
                text -> ctx.change("style.name", p -> p.withName(text)));
        form.skip(2);
        boolean texty = type != TvPieceType.SHAPE;
        if (texty) {
            TvColorPicker.add(form, "style.color", TvPropsCtx.key("color.text"), () -> ctx.piece().style().color(),
                    c -> style(ctx, "style.color", s -> s.withColor(c)), false, true, ctx::theme);
        }
        TvColorPicker.add(form, "style.background", TvPropsCtx.key("color.background"),
                () -> ctx.piece().style().background(), c -> style(ctx, "style.background", s -> s.withBackground(c)),
                true, true, ctx::theme);
        TvColorPicker.add(form, "style.border", TvPropsCtx.key("color.border"), () -> ctx.piece().style().border(),
                c -> style(ctx, "style.border", s -> s.withBorder(c, s.borderWidth())), true, true, ctx::theme);
        form.number("style.border_width", TvPropsCtx.key("style.border_width"), 0, TvStyle.MAX_BORDER, 1,
                () -> ctx.piece().style().borderWidth(),
                v -> style(ctx, "style.border_width", s -> s.withBorder(s.border(), v)));
        form.number("style.radius", TvPropsCtx.key("style.radius"), 0, TvStyle.MAX_RADIUS, 1,
                () -> ctx.piece().style().radius(), v -> style(ctx, "style.radius", s -> s.withRadius(v)));
        opacity(form, ctx);
        if (texty) {
            form.cycle("style.font", TvPropsCtx.key("style.font"), () -> fontName(ctx.piece().style().font()),
                    delta -> style(ctx, "style.font", s -> s.withFont(TvDataTab.step(List.of(TvFont.values()),
                            s.font(), delta))));
            form.add(new TvControls.Label(form.font, "style.size.label", 0, form.y() + 2, TvForm.LABEL_W - 2,
                    GuiPaint.MUTED,
                    () -> TvPropsCtx.key(ctx.piece().style().fit() ? "style.size.max" : "style.size")));
            form.add(new TvNumberField(form.font, "style.size", TvForm.LABEL_W, form.y(), TvForm.W - TvForm.LABEL_W, 1,
                    TvStyle.MAX_SIZE, 1, () -> ctx.piece().style().size(),
                    v -> style(ctx, "style.size", s -> s.withSize(v))));
            form.endRow(TvTextField.H);
            form.toggle("style.fit", TvPropsCtx.key("style.fit"), () -> ctx.piece().style().fit(),
                    on -> style(ctx, "style.fit", s -> s.withFit(on)));
            form.cycle("style.align", TvPropsCtx.key("style.align"),
                    () -> TvPropsCtx.key("align." + ctx.piece().style().align().getSerializedName()),
                    delta -> style(ctx, "style.align", s -> s.withAlign(TvDataTab.step(List.of(TvAlign.values()),
                            s.align(), delta))));
        }
        if (type == TvPieceType.TEXT) {
            form.text("style.text", TvPropsCtx.key("style.text"), TvStyle.MAX_TEXT, null,
                    TvPropsCtx.key("style.text.hint"), () -> ctx.piece().style().text(),
                    text -> style(ctx, "style.text", s -> s.withText(text)));
        }
    }

    /** Várias peças: a cor do texto de todas e a opacidade, só. */
    private static void buildMany(TvForm form, TvPropsCtx ctx) {
        TvColorPicker.add(form, "style.all", TvPropsCtx.key("color.all"), () -> ctx.piece().style().color(),
                c -> style(ctx, "style.all", s -> s.withColor(c)), false, true, ctx::theme);
        opacity(form, ctx);
    }

    private static void opacity(TvForm form, TvPropsCtx ctx) {
        form.number("style.opacity", TvPropsCtx.key("style.opacity"), 0, 100, 5, () -> ctx.piece().style().opacity(),
                v -> style(ctx, "style.opacity", s -> s.withOpacity(v)));
    }

    private static void style(TvPropsCtx ctx, String key, UnaryOperator<TvStyle> change) {
        ctx.change(key, p -> p.withStyle(change.apply(p.style())));
    }

    static Component fontName(TvFont font) {
        return TvPropsCtx.key("font." + font.getSerializedName());
    }
}
