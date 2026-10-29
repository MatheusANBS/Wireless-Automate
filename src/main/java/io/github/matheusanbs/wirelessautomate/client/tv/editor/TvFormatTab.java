package io.github.matheusanbs.wirelessautomate.client.tv.editor;

import io.github.matheusanbs.wirelessautomate.tv.TvFormat;
import io.github.matheusanbs.wirelessautomate.tv.TvPieceType;
import io.github.matheusanbs.wirelessautomate.tv.TvUnit;
import java.util.List;
import net.minecraft.network.chat.Component;

/**
 * A aba Formato (decisão 11 da spec): para o Valor, as casas (0 a 3), abreviar (k, M, G), o sinal, o prefixo, o sufixo e
 * a unidade ({@link TvUnit}), como o modelo da etapa 1. As outras peças não têm formato.
 */
final class TvFormatTab {
    private static final List<TvUnit> UNITS = List.of(TvUnit.values());

    private TvFormatTab() {
    }

    static void build(TvForm form, TvPropsCtx ctx) {
        if (ctx.piece().type() != TvPieceType.VALUE) {
            form.hint("format.none", TvPropsCtx.key("format.none"));
            return;
        }
        int y = form.inline("format.decimals", TvPropsCtx.key("format.decimals"));
        form.add(new TvNumberField(form.font, "format.decimals", TvForm.LABEL_W, y, TvForm.W - TvForm.LABEL_W, 0,
                TvFormat.MAX_DECIMALS, 1, () -> ctx.piece().format().decimals(),
                value -> ctx.change("format.decimals", p -> p.withFormat(with(p.format(), value, null, null, null,
                        null, null)))));
        form.endRow(TvTextField.H);
        form.add(new TvControls.Toggle(form.font, "format.abbreviate", 0, form.y(), TvForm.W,
                TvPropsCtx.key("format.abbreviate"), () -> ctx.piece().format().abbreviate(),
                on -> ctx.change("format.abbreviate", p -> p.withFormat(with(p.format(), null, on, null, null, null,
                        null)))));
        form.endRow(11);
        form.add(new TvControls.Toggle(form.font, "format.sign", 0, form.y(), TvForm.W, TvPropsCtx.key("format.sign"),
                () -> ctx.piece().format().sign(),
                on -> ctx.change("format.sign", p -> p.withFormat(with(p.format(), null, null, on, null, null,
                        null)))));
        form.endRow(11);
        y = form.inline("format.prefix", TvPropsCtx.key("format.prefix"));
        form.add(new TvTextField(form.font, "format.prefix", TvForm.LABEL_W, y, TvForm.W - TvForm.LABEL_W,
                TvFormat.MAX_AFFIX, null, Component.empty(), () -> ctx.piece().format().prefix(),
                text -> ctx.change("format.prefix", p -> p.withFormat(with(p.format(), null, null, null, text, null,
                        null)))));
        form.endRow(TvTextField.H);
        y = form.inline("format.suffix", TvPropsCtx.key("format.suffix"));
        form.add(new TvTextField(form.font, "format.suffix", TvForm.LABEL_W, y, TvForm.W - TvForm.LABEL_W,
                TvFormat.MAX_AFFIX, null, Component.empty(), () -> ctx.piece().format().suffix(),
                text -> ctx.change("format.suffix", p -> p.withFormat(with(p.format(), null, null, null, null, text,
                        null)))));
        form.endRow(TvTextField.H);
        form.dropdown("format.unit", TvPropsCtx.key("format.unit"), () -> UNITS, TvFormatTab::unitName,
                () -> ctx.piece().format().unit(), unit -> ctx.change("format.unit", p -> p.withFormat(with(
                        p.format(), null, null, null, null, null, unit))));
    }

    static Component unitName(TvUnit unit) {
        return TvPropsCtx.key("unit." + unit.getSerializedName());
    }

    /** O formato com os campos que não são nulos trocados. */
    private static TvFormat with(TvFormat f, Integer decimals, Boolean abbreviate, Boolean sign, String prefix,
            String suffix, TvUnit unit) {
        return new TvFormat(decimals != null ? decimals : f.decimals(), abbreviate != null ? abbreviate
                : f.abbreviate(), sign != null ? sign : f.sign(), prefix != null ? prefix : f.prefix(),
                suffix != null ? suffix : f.suffix(), unit != null ? unit : f.unit());
    }
}
