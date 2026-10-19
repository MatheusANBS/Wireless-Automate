package io.github.matheusanbs.wirelessautomate.client.tablet;

import static io.github.matheusanbs.wirelessautomate.client.tablet.TabletText.tr;

import io.github.matheusanbs.wirelessautomate.client.GuiPaint;
import io.github.matheusanbs.wirelessautomate.client.ResourceStyle;
import io.github.matheusanbs.wirelessautomate.client.tablet.FlowLines.Net;
import io.github.matheusanbs.wirelessautomate.client.tablet.FlowLines.Totals;
import io.github.matheusanbs.wirelessautomate.network.ResourceType;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * Uma linha da aba Fluxos ({@link FlowsTab}), com os textos já montados: o nome, o "entra → sai" e o lado direito; {@code expandable}
 * só num tipo com redes.
 */
record FlowRow(Kind kind, @Nullable ResourceType type, @Nullable Net net, Totals totals, int withoutNetwork,
        boolean expandable, Component name, Component mid, Component right) {
    static FlowRow of(Kind kind, @Nullable ResourceType type, @Nullable Net net, Totals totals, int withoutNetwork,
            boolean expandable) {
        Component name = kind == Kind.TYPE || kind == Kind.NET_TYPE ? ResourceStyle.name(type)
                : Component.literal(net.name());
        Component mid;
        Component right;
        switch (kind) {
            case TYPE -> {
                mid = FlowsText.pair(type, totals, true);
                right = FlowsText.right(type, totals, withoutNetwork, true);
            }
            case TYPE_NET -> {
                mid = FlowsText.pair(type, totals, false);
                right = FlowsText.right(type, totals, 0, false);
            }
            case NET_TYPE -> {
                mid = FlowsText.pair(type, totals, true);
                right = FlowsText.right(type, totals, 0, true);
            }
            case PAUSED -> {
                mid = tr("flows.paused.out").copy().withColor(GuiPaint.DISABLED);
                right = Component.empty();
            }
            default -> {
                mid = Component.empty();
                right = net.paused() ? tr("flows.paused").copy().withColor(GuiPaint.PAUSED)
                        : FlowsText.right(null, totals, 0, false);
            }
        }
        return new FlowRow(kind, type, net, totals, withoutNetwork, expandable, name, mid, right);
    }

    /** Tipo (Por tipo), rede no tipo, rede pausada, rede (Por rede) e tipo na rede. */
    enum Kind { TYPE, TYPE_NET, PAUSED, NET, NET_TYPE }
}
