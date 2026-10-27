package io.github.matheusanbs.wirelessautomate.client.tv.editor;

import io.github.matheusanbs.wirelessautomate.client.ResourceStyle;
import io.github.matheusanbs.wirelessautomate.network.LoadedTypes;
import io.github.matheusanbs.wirelessautomate.storage.StorageKind;
import io.github.matheusanbs.wirelessautomate.client.tv.TvIcons;
import io.github.matheusanbs.wirelessautomate.tv.TvBinding;
import io.github.matheusanbs.wirelessautomate.tv.TvClockMetric;
import io.github.matheusanbs.wirelessautomate.tv.TvMetric;
import io.github.matheusanbs.wirelessautomate.tv.TvPartStyle;
import io.github.matheusanbs.wirelessautomate.tv.TvPieceType;
import io.github.matheusanbs.wirelessautomate.tv.TvStorageMetric;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

/**
 * A aba Dado (decisão 10 da spec; fontes na decisão 2 da etapa 3). Para o Valor e as peças de dado (Barra, Medidor,
 * Gráfico, Seta), um seletor ‹ › de fonte (Rede, Armazenamento, Relógio) e os controles da fonte: a rede (lista com
 * busca, tipo de recurso e métrica), o armazenamento ligado à Tela (a lista dos ligados, a métrica e, no Baú, a grade
 * de itens) ou o relógio do jogo (hora ou dia). O Ícone escolhe o item (pelo id, pela grade do Baú ou arrastando do
 * JEI); Texto e Forma não usam dado. Sem ligação o Valor mostra "—", e uma ligação nova só ganha números depois de
 * salvar. As escolhas montam o {@link TvBinding} e vão pelo {@link TvPropsCtx#change}.
 */
final class TvDataTab {
    private static final List<TvBinding.Source> SOURCES = List.of(TvBinding.Source.values());

    private TvDataTab() {
    }

    static void build(TvForm form, TvPropsCtx ctx) {
        TvPieceType type = ctx.piece().type();
        if (type == TvPieceType.ICON) {
            icon(form, ctx);
            return;
        }
        if (!type.bindable()) {
            form.hint("data.none", TvPropsCtx.key("data.none"));
            return;
        }
        TvDataState state = ctx.data();
        form.label("data.source.title", TvPropsCtx.key("data.source"));
        form.wideCycle("data.source", () -> TvPropsCtx.key("data.source."
                + state.source(ctx.piece().binding().orElse(null)).getSerializedName()),
                delta -> chooseSource(ctx, step(SOURCES, state.source(ctx.piece().binding().orElse(null)), delta)));
        switch (state.source(ctx.piece().binding().orElse(null))) {
            case NETWORK -> network(form, ctx);
            case STORAGE -> storage(form, ctx);
            case CLOCK -> clock(form, ctx);
        }
        form.add(new TvControls.Button(form.font, "data.clear", 0, form.y(), TvForm.W, TvPropsCtx.key("data.clear"),
                () -> ctx.change("bind", p -> p.withBinding(Optional.empty())))
                .enabledWhen(() -> ctx.piece().binding().isPresent()));
        form.endRow(TvControls.ROW - 1);
        form.hint("data.hint", TvPropsCtx.key("data.hint"));
    }

    /** Troca a fonte: o relógio liga na hora; rede e armazenamento largam a ligação até a escolha na lista. */
    private static void chooseSource(TvPropsCtx ctx, TvBinding.Source source) {
        ctx.data().view(source);
        ctx.change("bind", p -> p.withBinding(source == TvBinding.Source.CLOCK
                ? Optional.of(TvBinding.clock(TvClockMetric.TIME)) : Optional.empty()));
    }

    // ------------------------------------------------------------------ rede

    private static void network(TvForm form, TvPropsCtx ctx) {
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
    }

    private static final Component NO_LINK = Component.literal("—");

    /** A rede da ligação da peça escolhida, ou {@code null}. */
    private static @Nullable UUID bound(TvPropsCtx ctx) {
        return ctx.piece().binding().map(TvBinding::network).orElse(null);
    }

    /** A ligação trocando a rede; sem ligação, nasce com o primeiro tipo carregado (Itens) e a métrica Entra. */
    static TvBinding withNetwork(@Nullable TvBinding old, UUID network) {
        if (old == null || old.network() == null) {
            return new TvBinding(network, LoadedTypes.LIST.get(0), TvMetric.IN);
        }
        return new TvBinding(network, old.type(), old.metric());
    }

    // ------------------------------------------------------------------ armazenamento

    private static void storage(TvForm form, TvPropsCtx ctx) {
        TvDataState state = ctx.data();
        form.label("data.storage.title", TvPropsCtx.key("data.storage"));
        if (state.links().isEmpty()) {
            form.hint("data.storage.none", TvPropsCtx.key("data.storage.none"));
            return;
        }
        TvLinkList list = form.add(new TvLinkList(form.font, "data.storage", 0, form.y(), state.links(), state,
                () -> storagePos(ctx), pos -> pickStorage(ctx, pos)));
        form.skip(list.h + TvForm.GAP);
        if (ctx.piece().binding().map(b -> b.storage() == null).orElse(true)) {
            return;
        }
        form.label("data.storage.metric.title", TvPropsCtx.key("data.storage.metric"));
        form.wideCycle("data.storage.metric", () -> ctx.piece().binding().map(TvDataTab::storageMetricName)
                .orElse(NO_LINK), delta -> ctx.change("bind", p -> p.binding().map(b -> p.withBinding(Optional.of(
                        withStorageMetric(b, step(List.of(TvStorageMetric.values()), b.storage().metric(), delta)))))
                        .orElse(p)));
        GlobalPos pos = storagePos(ctx);
        if (pos != null && state.kindOf(pos) == StorageKind.CHEST) {
            requestOnce(ctx, pos);
            form.label("data.storage.item.title", TvPropsCtx.key("data.storage.item"));
            form.add(new TvItemGrid(form.font, "data.storage.items", 0, form.y(), () -> storagePos(ctx),
                    () -> ctx.piece().binding().map(b -> b.storage().item()).orElse(Optional.empty()),
                    item -> ctx.change("bind", p -> p.binding().map(b -> p.withBinding(Optional.of(
                            TvBinding.storage(b.type(), b.storage().pos(), b.storage().metric(), item))))
                            .orElse(p)),
                    state::query, text -> searchItems(ctx, pos, text)));
            form.skip(TvItemGrid.HEIGHT + TvForm.GAP);
            form.add(new TvControls.Button(form.font, "data.storage.item.all", 0, form.y(), TvForm.W,
                    TvPropsCtx.key("data.storage.item.all"), () -> ctx.change("bind", p -> p.binding()
                            .map(b -> p.withBinding(Optional.of(TvBinding.storage(b.type(), b.storage().pos(),
                                    b.storage().metric(), Optional.empty())))).orElse(p)))
                    .enabledWhen(() -> ctx.piece().binding().map(b -> b.storage() != null
                            && b.storage().item().isPresent()).orElse(false)));
            form.endRow(TvControls.ROW - 1);
        }
    }

    private static @Nullable GlobalPos storagePos(TvPropsCtx ctx) {
        return ctx.piece().binding().map(b -> b.storage() == null ? null : b.storage().pos()).orElse(null);
    }

    /** Escolhe o armazenamento: nasce com a quantidade e, no mesmo armazenamento, mantém a métrica e o item. */
    private static void pickStorage(TvPropsCtx ctx, GlobalPos pos) {
        TvDataState state = ctx.data();
        state.browse(pos);
        ctx.change("bind", p -> {
            TvBinding old = p.binding().orElse(null);
            boolean same = old != null && old.storage() != null && old.storage().pos().equals(pos);
            TvStorageMetric metric = old != null && old.storage() != null ? old.storage().metric()
                    : TvStorageMetric.STORED;
            Optional<ResourceLocation> item = same ? old.storage().item() : Optional.empty();
            return p.withBinding(Optional.of(TvBinding.storage(state.typeOf(pos), pos, metric, item)));
        });
    }

    private static TvBinding withStorageMetric(TvBinding b, TvStorageMetric metric) {
        return TvBinding.storage(b.type(), b.storage().pos(), metric, b.storage().item());
    }

    /** Pede os itens do Baú ao servidor, uma vez por escolha de peça e Baú. */
    private static void requestOnce(TvPropsCtx ctx, GlobalPos pos) {
        if (ctx.data().shouldRequest(pos)) {
            ctx.actions().requestStorageContents(pos, ctx.data().query());
        }
    }

    /** A busca digitada na grade: guarda o texto e pede ao servidor os itens que casam. */
    private static void searchItems(TvPropsCtx ctx, @Nullable GlobalPos chest, String text) {
        ctx.data().query(text);
        if (chest != null) {
            ctx.actions().requestStorageContents(chest, text);
        }
    }

    static Component storageMetricName(TvBinding binding) {
        return TvPropsCtx.key("data.storage.metric." + binding.storage().metric().getSerializedName());
    }

    // ------------------------------------------------------------------ relógio

    private static void clock(TvForm form, TvPropsCtx ctx) {
        form.label("data.clock.title", TvPropsCtx.key("data.clock"));
        form.wideCycle("data.clock.metric", () -> ctx.piece().binding().filter(b -> b.clock() != null)
                .map(b -> TvPropsCtx.key("data.clock.metric." + b.clock().getSerializedName())).orElse(NO_LINK),
                delta -> ctx.change("bind", p -> Optional.ofNullable(p.binding().orElse(null))
                        .filter(b -> b.clock() != null).map(b -> p.withBinding(Optional.of(TvBinding.clock(
                                step(List.of(TvClockMetric.values()), b.clock(), delta))))).orElse(p)));
    }

    // ------------------------------------------------------------------ ícone

    private static void icon(TvForm form, TvPropsCtx ctx) {
        form.label("data.icon.title", TvPropsCtx.key("data.icon"));
        form.add(new TvItemSlot(form.font, "data.icon.slot", 0, form.y(),
                () -> ctx.piece().style().part(), () -> setIconItem(ctx, Optional.empty())));
        form.skip(26);
        form.hint("data.icon.hint", TvPropsCtx.key("data.icon.hint"));
    }

    /** Troca o item do Ícone; a grade e o campo de id compartilham a chave, para o desfazer juntar as mudanças. */
    static void setIconItem(TvPropsCtx ctx, Optional<ResourceLocation> item) {
        ctx.change("data.icon", p -> p.withStyle(p.style().withPart(p.style().part().withItem(item))));
    }

    /** Troca o Ícone por um ingrediente do JEI (item com componentes, fluido ou químico); outro tipo, nada. */
    static void setIconIngredient(TvPropsCtx ctx, Object ingredient) {
        ctx.change("data.icon", p -> {
            TvPartStyle chosen = TvIcons.choose(p.style().part(), ingredient);
            return chosen == null ? p : p.withStyle(p.style().withPart(chosen));
        });
    }

    // ------------------------------------------------------------------ comuns

    /** O elemento {@code delta} passos depois de {@code current} na lista, dando a volta. */
    static <T> T step(List<T> list, T current, int delta) {
        int at = list.indexOf(current);
        return list.get(Math.floorMod((at < 0 ? 0 : at) + delta, list.size()));
    }

    static Component metricName(TvMetric metric) {
        return TvPropsCtx.key("metric." + metric.getSerializedName());
    }
}
