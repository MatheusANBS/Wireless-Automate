package io.github.matheusanbs.wirelessautomate.client.tv.editor;

import io.github.matheusanbs.wirelessautomate.client.ResourceStyle;
import io.github.matheusanbs.wirelessautomate.network.LoadedTypes;
import io.github.matheusanbs.wirelessautomate.tv.TvBinding;
import io.github.matheusanbs.wirelessautomate.tv.TvMetric;
import io.github.matheusanbs.wirelessautomate.tv.TvPieceType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * A aba Dado (decisão 10 da spec): para o Valor, a lista das redes com busca, o tipo de recurso (só os carregados) e
 * a métrica; as outras peças não usam dado e a aba só avisa. Sem ligação o Valor mostra "—", e uma ligação nova só
 * ganha números depois de salvar. A escolha da rede, do tipo e da métrica monta o {@link TvBinding} e vai pelo
 * {@link TvPropsCtx#change}.
 */
final class TvDataTab {
    private TvDataTab() {
    }

    static void build(TvForm form, TvPropsCtx ctx) {
        if (ctx.piece().type() != TvPieceType.VALUE) {
            form.hint("data.none", TvPropsCtx.key("data.none"));
            return;
        }
        form.label("data.network.title", TvPropsCtx.key("data.network"));
        form.add(new TvNetworkList(form.font, "data.network", 0, form.y(), ctx.networks(), () -> bound(ctx),
                network -> ctx.change("bind", p -> p.withBinding(Optional.of(withNetwork(p.binding().orElse(null),
                        network))))));
        form.skip(TvNetworkList.HEIGHT + TvForm.GAP);
        form.label("data.type.title", TvPropsCtx.key("data.type"));
        form.wideCycle("data.type",
                () -> ctx.piece().binding().map(b -> ResourceStyle.name(b.type())).orElse(NO_LINK),
                delta -> ctx.change("bind", p -> p.binding().map(b -> p.withBinding(Optional.of(
                        new TvBinding(b.network(), step(LoadedTypes.LIST, b.type(), delta), b.metric()))))
                        .orElse(p)));
        form.label("data.metric.title", TvPropsCtx.key("data.metric"));
        form.wideCycle("data.metric",
                () -> ctx.piece().binding().map(b -> metricName(b.metric())).orElse(NO_LINK),
                delta -> ctx.change("bind", p -> p.binding().map(b -> p.withBinding(Optional.of(
                        new TvBinding(b.network(), b.type(), step(List.of(TvMetric.values()), b.metric(), delta)))))
                        .orElse(p)));
        form.add(new TvControls.Button(form.font, "data.clear", 0, form.y(), TvForm.W, TvPropsCtx.key("data.clear"),
                () -> ctx.change("bind", p -> p.withBinding(Optional.empty())))
                .enabledWhen(() -> ctx.piece().binding().isPresent()));
        form.endRow(TvControls.ROW - 1);
        form.hint("data.hint", TvPropsCtx.key("data.hint"));
    }

    private static final Component NO_LINK = Component.literal("—");

    /** A rede da ligação da peça escolhida, ou {@code null}. */
    private static @Nullable UUID bound(TvPropsCtx ctx) {
        return ctx.piece().binding().map(TvBinding::network).orElse(null);
    }

    /** A ligação trocando a rede; sem ligação, nasce com o primeiro tipo carregado (Itens) e a métrica Entra. */
    static TvBinding withNetwork(@Nullable TvBinding old, UUID network) {
        if (old == null) {
            return new TvBinding(network, LoadedTypes.LIST.get(0), TvMetric.IN);
        }
        return new TvBinding(network, old.type(), old.metric());
    }

    /** O elemento {@code delta} passos depois de {@code current} na lista, dando a volta. */
    static <T> T step(List<T> list, T current, int delta) {
        int at = list.indexOf(current);
        return list.get(Math.floorMod((at < 0 ? 0 : at) + delta, list.size()));
    }

    static Component metricName(TvMetric metric) {
        return TvPropsCtx.key("metric." + metric.getSerializedName());
    }
}
