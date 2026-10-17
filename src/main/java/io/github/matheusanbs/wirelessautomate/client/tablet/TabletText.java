package io.github.matheusanbs.wirelessautomate.client.tablet;

import io.github.matheusanbs.wirelessautomate.client.GuiPaint;
import io.github.matheusanbs.wirelessautomate.client.ResourceStyle;
import io.github.matheusanbs.wirelessautomate.menu.TabletSnapshot.NodeStatus;
import io.github.matheusanbs.wirelessautomate.network.ResourceType;
import java.util.Locale;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/** Os textos curtos do Tablet: as chaves de tradução, o plural, o status do nó e os números com vírgula. */
final class TabletText {
    private TabletText() {
    }

    static Component tr(String key, Object... args) {
        return Component.translatable("gui.wirelessautomate.tablet." + key, args);
    }

    /** {@code key} com o número, ou {@code key.one} quando é 1. */
    static Component plural(String key, long count) {
        return tr(count == 1 ? key + ".one" : key, count);
    }

    static int statusColor(NodeStatus status) {
        return switch (status) {
            case ACTIVE -> GuiPaint.OK;
            case IDLE -> GuiPaint.MUTED;
            case FULL -> GuiPaint.WARN;
            case UNLOADED -> GuiPaint.DANGER;
            case PAUSED -> GuiPaint.PAUSED;
            case NO_NETWORK -> GuiPaint.DISABLED;
        };
    }

    static Component statusName(NodeStatus status) {
        return tr("status." + status.name().toLowerCase(Locale.ROOT));
    }

    static Component typeWord(ResourceType type) {
        return tr("type." + type.key());
    }

    static Component typeName(ResourceType type) {
        return ResourceStyle.name(type);
    }

    static String decimal(double value) {
        return String.format(Locale.ROOT, "%.2f", value).replace(".", tr("decimal").getString());
    }

    static Component ms(long nanos) {
        return tr("ms", decimal(nanos / 1_000_000.0));
    }

    /** Junta uma parte à linha, separada por um ponto. */
    static void appendPart(MutableComponent line, Component part) {
        if (!line.getSiblings().isEmpty()) {
            line.append(Component.literal(" · ").withColor(GuiPaint.MUTED));
        }
        line.append(part);
    }
}
