package io.github.matheusanbs.wirelessautomate.client.tv.editor;

import io.github.matheusanbs.wirelessautomate.tv.TvPartStyle;
import io.github.matheusanbs.wirelessautomate.tv.TvPieceType;
import java.util.List;
import java.util.function.UnaryOperator;

/**
 * A seção "Peça" da aba Estilo para as peças da etapa 3 (os campos de {@link TvPartStyle}): a Barra (deitada ou em
 * pé, invertida, mínimo, máximo e trilha), o Medidor (abertura, espessura, mínimo, máximo e trilha), o Gráfico (linha
 * ou colunas, janela, área e a escala fixa) e a Seta (invertida, zona morta e as três cores). Os números são inteiros
 * na tela; zero no máximo é "do dado". O Ícone escolhe o item na aba Dado.
 */
final class TvPartTab {
    private TvPartTab() {
    }

    /** Se a peça tem controles nesta seção. */
    static boolean has(TvPieceType type) {
        return switch (type) {
            case BAR, GAUGE, GRAPH, ARROW -> true;
            default -> false;
        };
    }

    static void build(TvForm form, TvPropsCtx ctx, TvPieceType type) {
        switch (type) {
            case BAR -> bar(form, ctx);
            case GAUGE -> gauge(form, ctx);
            case GRAPH -> graph(form, ctx);
            case ARROW -> arrow(form, ctx);
            default -> {
            }
        }
    }

    private static void bar(TvForm form, TvPropsCtx ctx) {
        int y = form.inline("part.direction", TvPropsCtx.key("part.direction"));
        form.add(new TvControls.Segment(form.font, "part.direction", TvForm.LABEL_W, y, TvForm.W - TvForm.LABEL_W,
                List.of(TvPropsCtx.key("part.direction.horizontal"), TvPropsCtx.key("part.direction.vertical")),
                () -> part(ctx).orientation().ordinal(),
                index -> change(ctx, "part.direction", s -> s.withOrientation(
                        TvPartStyle.Orientation.values()[index]))));
        form.endRow(TvControls.ROW - 1);
        form.toggle("part.reverse", TvPropsCtx.key("part.reverse.bar"), () -> part(ctx).reverse(),
                on -> change(ctx, "part.reverse", s -> s.withReverse(on)));
        range(form, ctx);
        track(form, ctx);
    }

    private static void gauge(TvForm form, TvPropsCtx ctx) {
        form.number("part.sweep", TvPropsCtx.key("part.sweep"), TvPartStyle.MIN_SWEEP, TvPartStyle.MAX_SWEEP, 15,
                () -> part(ctx).sweep(), v -> change(ctx, "part.sweep", s -> s.withSweep(v)));
        form.number("part.thickness", TvPropsCtx.key("part.thickness"), 1, TvPartStyle.MAX_THICKNESS, 1,
                () -> part(ctx).thickness(), v -> change(ctx, "part.thickness", s -> s.withThickness(v)));
        range(form, ctx);
        track(form, ctx);
    }

    private static void graph(TvForm form, TvPropsCtx ctx) {
        int y = form.inline("part.mode", TvPropsCtx.key("part.mode"));
        form.add(new TvControls.Segment(form.font, "part.mode", TvForm.LABEL_W, y, TvForm.W - TvForm.LABEL_W,
                List.of(TvPropsCtx.key("part.mode.line"), TvPropsCtx.key("part.mode.columns")),
                () -> part(ctx).mode().ordinal(),
                index -> change(ctx, "part.mode", s -> s.withMode(TvPartStyle.GraphMode.values()[index]))));
        form.endRow(TvControls.ROW - 1);
        form.number("part.window", TvPropsCtx.key("part.window"), TvPartStyle.MIN_WINDOW, TvPartStyle.MAX_WINDOW, 1,
                () -> part(ctx).window(), v -> change(ctx, "part.window", s -> s.withWindow(v)));
        form.toggle("part.area", TvPropsCtx.key("part.area"), () -> part(ctx).area(),
                on -> change(ctx, "part.area", s -> s.withArea(on)));
        range(form, ctx);
        form.hint("part.scale.hint", TvPropsCtx.key("part.scale.hint"));
    }

    private static void arrow(TvForm form, TvPropsCtx ctx) {
        form.toggle("part.reverse", TvPropsCtx.key("part.reverse.arrow"), () -> part(ctx).reverse(),
                on -> change(ctx, "part.reverse", s -> s.withReverse(on)));
        form.number("part.dead", TvPropsCtx.key("part.dead"), 0, Integer.MAX_VALUE, 1,
                () -> (int) Math.min(Integer.MAX_VALUE, part(ctx).deadZone()),
                v -> change(ctx, "part.dead", s -> s.withDeadZone(v)));
        TvColorPicker.add(form, ctx, "part.up", TvPropsCtx.key("part.up"), () -> part(ctx).upColor(),
                c -> change(ctx, "part.up", s -> s.withArrowColors(c, s.downColor(), s.flatColor())), false, true);
        TvColorPicker.add(form, ctx, "part.down", TvPropsCtx.key("part.down"), () -> part(ctx).downColor(),
                c -> change(ctx, "part.down", s -> s.withArrowColors(s.upColor(), c, s.flatColor())), false, true);
        TvColorPicker.add(form, ctx, "part.flat", TvPropsCtx.key("part.flat"), () -> part(ctx).flatColor(),
                c -> change(ctx, "part.flat", s -> s.withArrowColors(s.upColor(), s.downColor(), c)), false, true);
    }

    /** O mínimo e o máximo; zero no máximo é "do dado". */
    private static void range(TvForm form, TvPropsCtx ctx) {
        form.number("part.min", TvPropsCtx.key("part.min"), -Integer.MAX_VALUE, Integer.MAX_VALUE, 1,
                () -> (int) Math.max(-Integer.MAX_VALUE, Math.min(Integer.MAX_VALUE, part(ctx).min())),
                v -> change(ctx, "part.min", s -> s.withRange(v, s.max())));
        form.number("part.max", TvPropsCtx.key("part.max"), -Integer.MAX_VALUE, Integer.MAX_VALUE, 1,
                () -> (int) Math.max(-Integer.MAX_VALUE, Math.min(Integer.MAX_VALUE, part(ctx).max())),
                v -> change(ctx, "part.max", s -> s.withRange(s.min(), v)));
    }

    private static void track(TvForm form, TvPropsCtx ctx) {
        TvColorPicker.add(form, ctx, "part.track", TvPropsCtx.key("part.track"), () -> part(ctx).track(),
                c -> change(ctx, "part.track", s -> s.withTrack(c)), true, true);
    }

    private static TvPartStyle part(TvPropsCtx ctx) {
        return ctx.piece().style().part();
    }

    private static void change(TvPropsCtx ctx, String key, UnaryOperator<TvPartStyle> change) {
        ctx.change(key, p -> p.withStyle(p.style().withPart(change.apply(p.style().part()))));
    }
}
