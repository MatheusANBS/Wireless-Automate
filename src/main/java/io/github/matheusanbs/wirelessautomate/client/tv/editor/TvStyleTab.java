package io.github.matheusanbs.wirelessautomate.client.tv.editor;

import io.github.matheusanbs.wirelessautomate.client.GuiPaint;
import io.github.matheusanbs.wirelessautomate.client.ResourceStyle;
import io.github.matheusanbs.wirelessautomate.menu.TvMenu;
import io.github.matheusanbs.wirelessautomate.tv.TvAlign;
import io.github.matheusanbs.wirelessautomate.tv.TvArea;
import io.github.matheusanbs.wirelessautomate.tv.TvBinding;
import io.github.matheusanbs.wirelessautomate.tv.TvFont;
import io.github.matheusanbs.wirelessautomate.tv.TvPiece;
import io.github.matheusanbs.wirelessautomate.tv.TvPieceType;
import io.github.matheusanbs.wirelessautomate.tv.TvStyle;
import java.util.List;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import java.util.function.UnaryOperator;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/**
 * A aba Estilo (decisão 12 da spec) em seções que recolhem (o estado fica na sessão, {@link TvSections}): Conteúdo (o
 * texto ou o resumo do dado), Texto (fonte, tamanho, caber no quadro e alinhamento), Cores (uma linha por cor, cada
 * uma abrindo o seletor), Caixa (borda, cantos e opacidade) e Posição e tamanho (recolhida por padrão). Com várias
 * peças só vale o que é de todas: a "Cor de tudo" e a opacidade.
 */
final class TvStyleTab {
    private static final List<TvIcons.Icon> ALIGN_ICONS = List.of(TvIcons.Icon.ALIGN_LEFT,
            TvIcons.Icon.ALIGN_CENTER, TvIcons.Icon.ALIGN_RIGHT);

    private TvStyleTab() {
    }

    static void build(TvForm form, TvPropsCtx ctx) {
        if (ctx.doc().selection().size() > 1) {
            buildMany(form, ctx);
            return;
        }
        TvPieceType type = ctx.piece().type();
        boolean texty = type != TvPieceType.SHAPE;
        form.text("style.name", TvPropsCtx.key("style.name"), TvPiece.MAX_NAME, null,
                TvPropsCtx.key("style.name.hint"), () -> ctx.piece().name(),
                text -> ctx.change("style.name", p -> p.withName(text)));
        form.skip(2);
        if (texty && form.section("content", TvPropsCtx.key("section.content"), true)) {
            content(form, ctx, type);
        }
        if (texty && form.section("text", TvPropsCtx.key("section.text"), true)) {
            text(form, ctx);
        }
        if (form.section("colors", TvPropsCtx.key("section.colors"), true)) {
            colors(form, ctx, texty);
        }
        if (form.section("box", TvPropsCtx.key("section.box"), true)) {
            box(form, ctx);
        }
        if (form.section("position", TvPropsCtx.key("section.position"), false)) {
            position(form, ctx);
        }
    }

    private static void content(TvForm form, TvPropsCtx ctx, TvPieceType type) {
        if (type == TvPieceType.TEXT) {
            form.text("style.text", TvPropsCtx.key("style.text"), TvStyle.MAX_TEXT, null,
                    TvPropsCtx.key("style.text.hint"), () -> ctx.piece().style().text(),
                    text -> style(ctx, "style.text", s -> s.withText(text)));
        } else {
            form.hint("style.summary", summary(ctx));
        }
    }

    /** "Itens · Fábrica · entra": o dado do Valor numa linha, ou "Sem dado". */
    private static Component summary(TvPropsCtx ctx) {
        TvBinding binding = ctx.piece().binding().orElse(null);
        if (binding == null) {
            return TvPropsCtx.key("rules.nodata");
        }
        String network = "?";
        for (TvMenu.NetworkChoice choice : ctx.networks()) {
            if (choice.id().equals(binding.network())) {
                network = choice.name();
            }
        }
        return Component.empty().append(ResourceStyle.name(binding.type())).append(" · " + network + " · ")
                .append(TvDataTab.metricName(binding.metric()));
    }

    private static void text(TvForm form, TvPropsCtx ctx) {
        int y = form.inline("style.font", TvPropsCtx.key("style.font"));
        form.add(new TvFontMenu.Field(form.font, "style.font", TvForm.LABEL_W, y, TvForm.W - TvForm.LABEL_W, ctx,
                () -> ctx.piece().style().font(), f -> style(ctx, "style.font", s -> s.withFont(f))));
        form.endRow(TvControls.ROW - 1);
        form.add(new TvControls.Label(form.font, "style.size.label", 0, form.y() + 2, TvForm.LABEL_W - 2,
                GuiPaint.MUTED, () -> TvPropsCtx.key(ctx.piece().style().fit() ? "style.size.max" : "style.size")));
        form.add(new TvNumberField(form.font, "style.size", TvForm.LABEL_W, form.y(), TvForm.W - TvForm.LABEL_W, 1,
                TvStyle.MAX_SIZE, 1, () -> ctx.piece().style().size(),
                v -> style(ctx, "style.size", s -> s.withSize(v))));
        form.endRow(TvTextField.H);
        form.toggle("style.fit", TvPropsCtx.key("style.fit"), () -> ctx.piece().style().fit(),
                on -> style(ctx, "style.fit", s -> s.withFit(on)));
        int alignY = form.inline("style.align", TvPropsCtx.key("style.align"));
        form.add(new TvIconControls.IconSegment("style.align", TvForm.LABEL_W, alignY, TvForm.W - TvForm.LABEL_W,
                ALIGN_ICONS, List.of(TvPropsCtx.key("align.left.tip"), TvPropsCtx.key("align.center.tip"),
                        TvPropsCtx.key("align.right.tip")),
                () -> ctx.piece().style().align().ordinal(),
                index -> style(ctx, "style.align", s -> s.withAlign(TvAlign.values()[index]))));
        form.endRow(TvControls.ROW - 1);
    }

    private static void colors(TvForm form, TvPropsCtx ctx, boolean texty) {
        if (texty) {
            TvColorPicker.add(form, ctx, "style.color", TvPropsCtx.key("color.text.short"),
                    () -> ctx.piece().style().color(), c -> style(ctx, "style.color", s -> s.withColor(c)), false,
                    true);
        }
        TvColorPicker.add(form, ctx, "style.background", TvPropsCtx.key("color.fill"),
                () -> ctx.piece().style().background(), c -> style(ctx, "style.background", s -> s.withBackground(c)),
                true, true);
        TvColorPicker.add(form, ctx, "style.border", TvPropsCtx.key("color.border"),
                () -> ctx.piece().style().border(),
                c -> style(ctx, "style.border", s -> s.withBorder(c, s.borderWidth())), true, true);
    }

    private static void box(TvForm form, TvPropsCtx ctx) {
        form.number("style.border_width", TvPropsCtx.key("style.border_width"), 0, TvStyle.MAX_BORDER, 1,
                () -> ctx.piece().style().borderWidth(),
                v -> style(ctx, "style.border_width", s -> s.withBorder(s.border(), v)));
        form.number("style.radius", TvPropsCtx.key("style.radius"), 0, TvStyle.MAX_RADIUS, 1,
                () -> ctx.piece().style().radius(), v -> style(ctx, "style.radius", s -> s.withRadius(v)));
        opacity(form, ctx);
    }

    /** X, Y, largura e altura em duas colunas: um rótulo curto e o número, dois por linha. */
    private static void position(TvForm form, TvPropsCtx ctx) {
        TvEditorDoc doc = ctx.doc();
        TvArea area = doc.area();
        int half = (TvForm.W - 4) / 2;
        pair(form, half, new Cell("style.x", "x", 0, area.pixelWidth() - 1, () -> ctx.piece().x(),
                v -> ctx.change("style.x", p -> p.withBox(Mth.clamp(v, 0, doc.area().pixelWidth() - p.width()),
                        p.y(), p.width(), p.height()))),
                new Cell("style.y", "y", 0, area.pixelHeight() - 1, () -> ctx.piece().y(),
                        v -> ctx.change("style.y", p -> p.withBox(p.x(), Mth.clamp(v, 0,
                                doc.area().pixelHeight() - p.height()), p.width(), p.height()))));
        pair(form, half, new Cell("style.width", "w", 1, area.pixelWidth(), () -> ctx.piece().width(),
                v -> ctx.change("style.width", p -> p.withBox(p.x(), p.y(),
                        Mth.clamp(v, 1, doc.area().pixelWidth() - p.x()), p.height()))),
                new Cell("style.height", "h", 1, area.pixelHeight(), () -> ctx.piece().height(),
                        v -> ctx.change("style.height", p -> p.withBox(p.x(), p.y(), p.width(),
                                Mth.clamp(v, 1, doc.area().pixelHeight() - p.y())))));
    }

    /** Um número da seção de posição: o id, a chave do rótulo curto, os limites e a leitura e a escrita. */
    private record Cell(String id, String key, int min, int max, IntSupplier get, IntConsumer set) {
    }

    private static void pair(TvForm form, int half, Cell a, Cell b) {
        int y = form.y();
        int fieldW = half - 10;
        form.add(new TvControls.Label(form.font, a.id() + ".label", 0, y + 2, 9, GuiPaint.MUTED,
                () -> TvPropsCtx.key("pos." + a.key())));
        form.add(new TvNumberField(form.font, a.id(), 10, y, fieldW, a.min(), a.max(), 1, a.get(), a.set()));
        form.add(new TvControls.Label(form.font, b.id() + ".label", half + 4, y + 2, 9, GuiPaint.MUTED,
                () -> TvPropsCtx.key("pos." + b.key())));
        form.add(new TvNumberField(form.font, b.id(), half + 14, y, fieldW, b.min(), b.max(), 1, b.get(), b.set()));
        form.endRow(TvTextField.H);
    }

    /** Várias peças: a cor do texto de todas e a opacidade, só. */
    private static void buildMany(TvForm form, TvPropsCtx ctx) {
        if (form.section("colors", TvPropsCtx.key("section.colors"), true)) {
            TvColorPicker.add(form, ctx, "style.all", TvPropsCtx.key("color.all"), () -> ctx.piece().style().color(),
                    c -> style(ctx, "style.all", s -> s.withColor(c)), false, true);
        }
        if (form.section("box", TvPropsCtx.key("section.box"), true)) {
            opacity(form, ctx);
        }
    }

    private static void opacity(TvForm form, TvPropsCtx ctx) {
        int y = form.inline("style.opacity", TvPropsCtx.key("style.opacity"));
        form.add(new TvSlider(form.font, "style.opacity", TvForm.LABEL_W, y, TvForm.W - TvForm.LABEL_W, 0, 100, 5,
                "%", () -> ctx.piece().style().opacity(), v -> style(ctx, "style.opacity", s -> s.withOpacity(v))));
        form.endRow(TvTextField.H);
    }

    private static void style(TvPropsCtx ctx, String key, UnaryOperator<TvStyle> change) {
        ctx.change(key, p -> p.withStyle(change.apply(p.style())));
    }

    static Component fontName(TvFont font) {
        return TvPropsCtx.key("font." + font.getSerializedName());
    }
}
