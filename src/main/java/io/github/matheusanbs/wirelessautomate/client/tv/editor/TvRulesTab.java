package io.github.matheusanbs.wirelessautomate.client.tv.editor;

import io.github.matheusanbs.wirelessautomate.client.GuiPaint;
import io.github.matheusanbs.wirelessautomate.client.ResourceStyle;
import io.github.matheusanbs.wirelessautomate.menu.TvMenu;
import io.github.matheusanbs.wirelessautomate.network.LoadedTypes;
import io.github.matheusanbs.wirelessautomate.tv.TvBinding;
import io.github.matheusanbs.wirelessautomate.tv.TvColor;
import io.github.matheusanbs.wirelessautomate.tv.TvMetric;
import io.github.matheusanbs.wirelessautomate.tv.TvPieceType;
import io.github.matheusanbs.wirelessautomate.tv.TvRule;
import io.github.matheusanbs.wirelessautomate.tv.TvRuleOp;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.UnaryOperator;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * A aba Regras (decisão 13 da spec): até {@link TvRule#MAX_PER_PIECE} regras por peça, lidas de cima para baixo (vale a
 * primeira que bate), cada uma escrita como frase, com cada parte clicável ("Se o valor for [menor que] [25] → [cor do texto] [■]"):
 * a comparação (menor, maior ou igual a um número), o efeito (trocar a cor do texto,
 * trocar o fundo, piscar ou esconder), a ordem (↑ ↓) e o ✕. No Valor a regra compara com o dado da própria peça; no
 * Texto e na Forma cada regra tem a ligação própria (rede, tipo e métrica), que vem depois do efeito. A estrutura
 * muda com o efeito e a ligação; {@link #structure} resume isso para o painel refazer a aba só quando precisa.
 */
final class TvRulesTab {
    /** O efeito que a ficha da regra mostra. */
    enum Effect { COLOR, BACKGROUND, BLINK, HIDE }

    /** A largura dos botões da regra: o sprite de 16 px tem 2 px de folga de cada lado. */
    private static final int SMALL = 14;

    private TvRulesTab() {
    }

    static void build(TvForm form, TvPropsCtx ctx) {
        boolean value = ctx.piece().type() == TvPieceType.VALUE;
        form.hint("rules.hint", TvPropsCtx.key(value ? "rules.hint.value" : "rules.hint.own"));
        form.add(new TvControls.Button(form.font, "rules.add", 0, form.y(), TvForm.W, TvPropsCtx.key("rules.add"),
                () -> ctx.change("rules.add", p -> p.withRules(with(p.rules(), new TvRule(TvRuleOp.LT, 0,
                        Optional.empty(), Optional.of(TvColor.theme(TvColor.LOW)), Optional.empty(), false, false)))))
                .enabledWhen(() -> ctx.piece().rules().size() < TvRule.MAX_PER_PIECE));
        form.endRow(TvControls.ROW - 1);
        int count = ctx.piece().rules().size();
        for (int i = 0; i < count; i++) {
            buildRule(form, ctx, i, value);
        }
    }

    /**
     * Uma regra como frase, cada parte clicável: "Se o valor for [menor que] [25]" e, na linha de baixo, "→ [cor do
     * texto] [■]" e "[✓] piscar". Na linha do "Se" ficam os botões de subir, descer e tirar a regra. Texto e Forma
     * trazem antes os três seletores do dado da própria regra.
     */
    private static void buildRule(TvForm form, TvPropsCtx ctx, int i, boolean value) {
        String id = "rule" + i;
        TvRule rule = ctx.piece().rules().get(i);
        int y = form.y();
        form.add(new TvControls.Label(form.font, id + ".if", 0, y + 4, TvForm.W - 3 * SMALL - 6, TvControls.MUTED,
                () -> TvPropsCtx.key(value ? "rules.if.value" : "rules.if.own")));
        form.add(new TvIconControls.IconButton(id + ".up", TvForm.W - 3 * SMALL - 2, y, SMALL, TvIcons.Icon.MOVE_UP,
                TvPropsCtx.key("rules.up"), () -> ctx.change("rules.move", p -> p.withRules(swap(p.rules(), i, i - 1))))
                .enabledWhen(() -> i > 0));
        form.add(new TvIconControls.IconButton(id + ".down", TvForm.W - 2 * SMALL - 1, y, SMALL, TvIcons.Icon.MOVE_DOWN,
                TvPropsCtx.key("rules.down"), () -> ctx.change("rules.move", p -> p.withRules(swap(p.rules(), i,
                        i + 1)))).enabledWhen(() -> i < ctx.piece().rules().size() - 1));
        form.add(new TvIconControls.IconButton(id + ".remove", TvForm.W - SMALL, y, SMALL, TvIcons.Icon.REMOVE,
                TvPropsCtx.key("rules.remove"), () -> ctx.change("rules.remove", p -> p.withRules(without(p.rules(),
                        i)))));
        form.endRow(TvIcons.SIZE);
        if (!value) {
            own(form, ctx, i, rule);
            form.label(id + ".for", TvPropsCtx.key("rules.for"));
        }
        y = form.y();
        form.add(new TvControls.Cycle(form.font, id + ".op", 0, y, 84, () -> TvPropsCtx.key("rules.op."
                + rule(ctx, i).op().name().toLowerCase(java.util.Locale.ROOT)), delta -> edit(ctx, i,
                        r -> copy(r, TvDataTab.step(List.of(TvRuleOp.values()), r.op(), delta), null, null, null,
                                null, null, null))));
        form.add(new TvTextField(form.font, id + ".value", 88, y, TvForm.W - 88, 12,
                text -> text.matches("-?[0-9]*\\.?[0-9]*"), Component.literal("0"),
                () -> number(rule(ctx, i).value()), text -> {
                    try {
                        edit(ctx, i, r -> copy(r, null, Double.parseDouble(text), null, null, null, null, null));
                    } catch (NumberFormatException e) {
                        // "-" ou "." sozinhos: o valor fica até o texto virar um número
                    }
                }));
        form.endRow(TvControls.ROW - 1);
        Effect effect = effect(rule);
        boolean colored = effect == Effect.COLOR || effect == Effect.BACKGROUND;
        y = form.y();
        form.add(new TvControls.Label(form.font, id + ".then", 0, y + 2, 9, TvControls.MUTED,
                () -> TvPropsCtx.key("rules.then")));
        form.add(new TvControls.Cycle(form.font, id + ".effect", 10, y, TvForm.W - 10 - (colored ? 30 : 0),
                () -> effectName(effect(rule(ctx, i))), delta -> edit(ctx, i, r -> withEffect(r,
                        TvDataTab.step(List.of(Effect.values()), effect(r), delta)))));
        if (effect == Effect.COLOR) {
            TvColorPicker.chip(form, ctx, id + ".color", TvForm.W - 28, y, 28, () -> rule(ctx, i).color().orElse(0),
                    c -> edit(ctx, i, r -> copy(r, null, null, null, Optional.of(c), null, null, null)));
        } else if (effect == Effect.BACKGROUND) {
            TvColorPicker.chip(form, ctx, id + ".background", TvForm.W - 28, y, 28,
                    () -> rule(ctx, i).background().orElse(0),
                    c -> edit(ctx, i, r -> copy(r, null, null, null, null, Optional.of(c), null, null)));
        }
        form.endRow(TvControls.ROW - 1);
        if (colored) {
            form.add(new TvControls.Toggle(form.font, id + ".blink", 10, form.y(), TvForm.W - 10,
                    TvPropsCtx.key("rules.blink"), () -> rule(ctx, i).blink(),
                    on -> edit(ctx, i, r -> copy(r, null, null, null, null, null, on, null))));
            form.endRow(11);
        }
        form.skip(3);
    }

    /** A ligação própria da regra: a rede (ou "Sem dado") e, com ela, o tipo e a métrica. */
    private static void own(TvForm form, TvPropsCtx ctx, int i, TvRule rule) {
        String id = "rule" + i;
        form.wideCycle(id + ".network", () -> networkName(ctx, rule(ctx, i)),
                delta -> edit(ctx, i, r -> copy(r, null, null, Optional.ofNullable(nextNetwork(ctx, r, delta)), null,
                        null, null, null)));
        if (rule.bind().isEmpty()) {
            return;
        }
        form.wideCycle(id + ".type",
                () -> rule(ctx, i).bind().map(b -> ResourceStyle.name(b.type())).orElse(Component.empty()),
                delta -> edit(ctx, i, r -> copy(r, null, null, r.bind().map(b -> new TvBinding(b.network(),
                        TvDataTab.step(LoadedTypes.LIST, b.type(), delta), b.metric())), null, null, null, null)));
        form.wideCycle(id + ".metric",
                () -> rule(ctx, i).bind().map(b -> TvDataTab.metricName(b.metric())).orElse(Component.empty()),
                delta -> edit(ctx, i, r -> copy(r, null, null, r.bind().map(b -> new TvBinding(b.network(), b.type(),
                        TvDataTab.step(List.of(TvMetric.values()), b.metric(), delta))), null, null, null, null)));
    }

    /** O resumo da estrutura das regras da peça: muda quando uma regra entra, sai, troca de efeito ou de ligação. */
    static int structure(List<TvRule> rules) {
        int key = rules.size();
        for (TvRule rule : rules) {
            key = key * 31 + effect(rule).ordinal() * 2 + (rule.bind().isPresent() ? 1 : 0);
        }
        return key;
    }

    static Effect effect(TvRule rule) {
        if (rule.hide()) {
            return Effect.HIDE;
        }
        if (rule.color().isPresent()) {
            return Effect.COLOR;
        }
        if (rule.background().isPresent()) {
            return Effect.BACKGROUND;
        }
        return rule.blink() ? Effect.BLINK : Effect.COLOR;
    }

    /** A regra com o efeito trocado: cada efeito limpa os outros e traz uma cor de partida quando precisa. */
    static TvRule withEffect(TvRule r, Effect effect) {
        return switch (effect) {
            case COLOR -> new TvRule(r.op(), r.value(), r.bind(), Optional.of(r.color().orElse(
                    TvColor.theme(TvColor.LOW))), Optional.empty(), r.blink(), false);
            case BACKGROUND -> new TvRule(r.op(), r.value(), r.bind(), Optional.empty(), Optional.of(r.background()
                    .orElse(TvColor.theme(TvColor.LOW))), r.blink(), false);
            case BLINK -> new TvRule(r.op(), r.value(), r.bind(), Optional.empty(), Optional.empty(), true, false);
            case HIDE -> new TvRule(r.op(), r.value(), r.bind(), Optional.empty(), Optional.empty(), false, true);
        };
    }

    static Component effectName(Effect effect) {
        return TvPropsCtx.key("rules.effect." + effect.name().toLowerCase(java.util.Locale.ROOT));
    }

    /** A regra {@code i} da peça escolhida agora (a lista pode ter mudado desde que a aba foi montada). */
    private static TvRule rule(TvPropsCtx ctx, int i) {
        List<TvRule> rules = ctx.piece().rules();
        return rules.get(Math.min(i, rules.size() - 1));
    }

    private static void edit(TvPropsCtx ctx, int i, UnaryOperator<TvRule> change) {
        ctx.change("rule" + i, p -> {
            if (i >= p.rules().size()) {
                return p;
            }
            List<TvRule> rules = new ArrayList<>(p.rules());
            rules.set(i, change.apply(rules.get(i)));
            return p.withRules(rules);
        });
    }

    /** A regra com os campos que não são nulos trocados. */
    private static TvRule copy(TvRule r, @Nullable TvRuleOp op, @Nullable Double value,
            @Nullable Optional<TvBinding> bind, @Nullable Optional<Integer> color,
            @Nullable Optional<Integer> background, @Nullable Boolean blink, @Nullable Boolean hide) {
        return new TvRule(op != null ? op : r.op(), value != null ? value : r.value(), bind != null ? bind : r.bind(),
                color != null ? color : r.color(), background != null ? background : r.background(),
                blink != null ? blink : r.blink(), hide != null ? hide : r.hide());
    }

    private static List<TvRule> with(List<TvRule> rules, TvRule rule) {
        List<TvRule> out = new ArrayList<>(rules);
        out.add(rule);
        return out;
    }

    private static List<TvRule> without(List<TvRule> rules, int i) {
        List<TvRule> out = new ArrayList<>(rules);
        if (i < out.size()) {
            out.remove(i);
        }
        return out;
    }

    private static List<TvRule> swap(List<TvRule> rules, int i, int j) {
        if (i < 0 || j < 0 || i >= rules.size() || j >= rules.size()) {
            return rules;
        }
        List<TvRule> out = new ArrayList<>(rules);
        out.set(i, rules.get(j));
        out.set(j, rules.get(i));
        return out;
    }

    private static String number(double value) {
        return value == Math.rint(value) && Math.abs(value) < 1e9 ? Long.toString((long) value)
                : Double.toString(value);
    }

    /** O nome da rede da ligação da regra, ou "Sem dado". */
    private static Component networkName(TvPropsCtx ctx, TvRule rule) {
        if (rule.bind().isEmpty()) {
            return TvPropsCtx.key("rules.nodata");
        }
        for (TvMenu.NetworkChoice choice : ctx.networks()) {
            if (choice.id().equals(rule.bind().get().network())) {
                return Component.literal(choice.name());
            }
        }
        return Component.literal("?");
    }

    /**
     * A ligação depois de {@code delta} passos na lista "Sem dado" e as redes: da posição da rede de agora; "Sem dado"
     * dá {@code null} (sem ligação).
     */
    private static @Nullable TvBinding nextNetwork(TvPropsCtx ctx, TvRule rule, int delta) {
        List<TvMenu.NetworkChoice> networks = ctx.networks();
        int at = -1;
        for (int k = 0; k < networks.size(); k++) {
            if (rule.bind().isPresent() && networks.get(k).id().equals(rule.bind().get().network())) {
                at = k;
            }
        }
        int next = Math.floorMod(at + 1 + delta, networks.size() + 1) - 1;
        if (next < 0) {
            return null;
        }
        return TvDataTab.withNetwork(rule.bind().orElse(null), networks.get(next).id());
    }
}
