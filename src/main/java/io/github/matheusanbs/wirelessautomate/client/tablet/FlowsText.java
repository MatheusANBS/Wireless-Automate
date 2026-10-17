package io.github.matheusanbs.wirelessautomate.client.tablet;

import static io.github.matheusanbs.wirelessautomate.client.tablet.TabletText.appendPart;
import static io.github.matheusanbs.wirelessautomate.client.tablet.TabletText.ms;
import static io.github.matheusanbs.wirelessautomate.client.tablet.TabletText.plural;
import static io.github.matheusanbs.wirelessautomate.client.tablet.TabletText.tr;

import io.github.matheusanbs.wirelessautomate.client.GuiPaint;
import io.github.matheusanbs.wirelessautomate.client.ResourceStyle;
import io.github.matheusanbs.wirelessautomate.client.tablet.FlowLines.Net;
import io.github.matheusanbs.wirelessautomate.client.tablet.FlowLines.StatePart;
import io.github.matheusanbs.wirelessautomate.client.tablet.FlowLines.Totals;
import io.github.matheusanbs.wirelessautomate.network.FlowStats.States;
import io.github.matheusanbs.wirelessautomate.network.ResourceType;
import java.util.List;
import java.util.Locale;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.jetbrains.annotations.Nullable;

/** Os textos da aba Fluxos: "entra → sai" na unidade do tipo, o saldo, o texto curto dos estados e as dicas. */
final class FlowsText {
    private FlowsText() {
    }

    /** Um número por segundo do tipo, já na unidade da tela e curto. */
    static String amount(ResourceType type, double perSecond) {
        return FlowLines.amount(FlowLines.screenUnit(type, perSecond));
    }

    /** "entra → sai", com a unidade do tipo ou sem ela; "medindo…" antes do primeiro segundo. */
    static Component pair(ResourceType type, Totals totals, boolean unit) {
        if (totals.measuring()) {
            return tr("flows.measuring");
        }
        String number = tr("flows.pair", amount(type, totals.produced()), amount(type, totals.consumed())).getString();
        return unit ? ResourceStyle.rateText(type, number) : Component.literal(number);
    }

    /** O saldo dos Armazéns ("+16.5k", ou "+16.5k enchendo" com a palavra), ou {@code null} quando é zero. */
    static @Nullable Component balance(ResourceType type, Totals totals, boolean word) {
        double value = FlowLines.screenUnit(type, totals.balance());
        if (totals.measuring() || FlowLines.zero(value)) {
            return null;
        }
        String number = FlowLines.signed(value);
        Component text = word ? tr(value > 0 ? "flows.filling" : "flows.draining", number) : Component.literal(number);
        return text.copy().withColor(value > 0 ? GuiPaint.OK : GuiPaint.WARN);
    }

    /** O texto curto dos estados ("3 esperando · 1 cheio"), vazio quando está tudo bem. */
    static MutableComponent states(States states, int withoutNetwork) {
        MutableComponent line = Component.empty();
        for (StatePart part : FlowLines.shortStates(states, withoutNetwork)) {
            String key = "flows.short." + part.kind().name().toLowerCase(Locale.ROOT);
            appendPart(line, plural(key, part.count()).copy().withColor(GuiPaint.WARN));
        }
        return line;
    }

    /** O lado direito de uma linha: o saldo e os estados, ou "medindo…" enquanto a janela não enche. */
    static Component right(@Nullable ResourceType type, Totals totals, int withoutNetwork, boolean word) {
        if (!totals.filled()) {
            return totals.measuring() ? Component.empty() : tr("flows.measuring").copy().withColor(GuiPaint.MUTED);
        }
        MutableComponent line = Component.empty();
        Component balance = type == null ? null : balance(type, totals, word);
        if (balance != null) {
            line.append(balance);
        }
        MutableComponent states = states(totals.states(), withoutNetwork);
        if (!states.getSiblings().isEmpty()) {
            appendPart(line, states);
        }
        return line;
    }

    // ------------------------------------------------------------------ dicas

    /** A dica de um tipo (Por tipo): o total, os estados completos e o clique. */
    static Component typeTip(ResourceType type, Totals totals, int withoutNetwork, boolean expandable) {
        MutableComponent tip = ResourceStyle.name(type).copy();
        body(tip, type, totals);
        tip.append("\n").append(muted(tr("flows.tip.no_network", withoutNetwork)));
        if (expandable) {
            tip.append("\n").append(muted(tr("flows.tip.open")));
        }
        return click(tip);
    }

    /** A dica de um tipo numa rede (as duas vistas): o nome da rede, o tempo do mod nela e os estados. */
    static Component partTip(Net net, ResourceType type, Totals totals) {
        MutableComponent tip = Component.literal(net.name()).append(muted(Component.literal(" · ")))
                .append(ResourceStyle.name(type));
        tip.append("\n").append(muted(tr("flows.tip.time", ms(net.nanos()))));
        body(tip, type, totals);
        return click(tip);
    }

    /** A dica do cabeçalho de uma rede (Por rede): o tempo do mod nela e os estados de todos os tipos. */
    static Component netTip(Net net, Totals totals) {
        MutableComponent tip = Component.literal(net.name()).copy();
        if (net.paused()) {
            tip.append("\n").append(tr("flows.tip.paused").copy().withColor(GuiPaint.PAUSED));
        } else {
            tip.append("\n").append(muted(tr("flows.tip.time", ms(net.nanos()))));
            states(tip, totals.states());
        }
        return click(tip);
    }

    /** A dica de uma rede pausada (Por tipo). */
    static Component pausedTip(Net net) {
        MutableComponent tip = Component.literal(net.name()).copy();
        tip.append("\n").append(tr("flows.tip.paused").copy().withColor(GuiPaint.PAUSED));
        return click(tip);
    }

    private static void body(MutableComponent tip, ResourceType type, Totals totals) {
        if (!totals.filled()) {
            tip.append("\n").append(muted(tr("flows.tip.measuring")));
        }
        if (!FlowLines.zero(totals.stored()) || !FlowLines.zero(totals.released())) {
            tip.append("\n").append(muted(tr("flows.tip.storage",
                    ResourceStyle.rateText(type, amount(type, totals.stored())),
                    ResourceStyle.rateText(type, amount(type, totals.released())))));
        }
        states(tip, totals.states());
    }

    private static void states(MutableComponent tip, States s) {
        tip.append("\n").append(muted(tr("flows.tip.sources", s.sourcesMoving(), s.sourcesEmpty(), s.sourcesWaiting())));
        tip.append("\n").append(muted(tr("flows.tip.destinations", s.destinationsMoving(), s.destinationsFull())));
        tip.append("\n").append(muted(tr("flows.tip.redstone", s.redstoneStopped())));
    }

    private static Component click(MutableComponent tip) {
        return tip.append("\n").append(muted(tr("flows.tip.list")));
    }

    private static Component muted(Component text) {
        return text.copy().withColor(GuiPaint.TOOLTIP_MUTED);
    }

    /** O tempo do mod por tick e o teto, em ms ("0.42 de 1.00 ms"). */
    static Component time(long modNanos, long budgetNanos) {
        return tr("flows.time.value", TabletText.decimal(modNanos / 1e6), TabletText.decimal(budgetNanos / 1e6));
    }

    /** As listas de linhas vazias: nenhuma rede, ainda sem amostra, ou nada em uso. */
    static Component empty(boolean noNetworks, boolean waiting) {
        return noNetworks ? tr("networks.empty") : waiting ? tr("flows.measuring") : tr("flows.none");
    }

    static List<Component> modeLabels() {
        return List.of(tr("flows.by_type"), tr("flows.by_network"));
    }
}
