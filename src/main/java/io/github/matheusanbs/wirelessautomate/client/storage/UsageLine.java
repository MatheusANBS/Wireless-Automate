package io.github.matheusanbs.wirelessautomate.client.storage;

import static io.github.matheusanbs.wirelessautomate.client.storage.StorageListLayout.GRID_X;

import io.github.matheusanbs.wirelessautomate.client.GuiPaint;
import io.github.matheusanbs.wirelessautomate.client.RateFormat;
import io.github.matheusanbs.wirelessautomate.item.TierCoreItem;
import io.github.matheusanbs.wirelessautomate.menu.StorageListView;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/** A ocupação embaixo da grade: total, capacidade e tipos, a porcentagem e a barra colorida pelo estado. */
final class UsageLine {
    private UsageLine() {
    }

    /**
     * Desenha a linha no topo {@code y + usageY}, da grade até o fim da barra de rolagem ({@code barX}).
     *
     * @param items Baú (quantidades sem unidade) ou tanque (em mB)
     */
    static void render(GuiGraphics g, Font font, StorageListView<?> view, boolean items, int x, int y, int usageY,
            int barX) {
        StorageListView.Header header = view.header();
        Component usage = header.capacity() <= 0
                ? StorageListScreen.tr("usage.unlimited", abbreviated(header.total(), items),
                        TierCoreItem.grouped(view.types()))
                : StorageListScreen.tr("usage", abbreviated(header.total(), items),
                        abbreviated(header.capacity(), items), TierCoreItem.grouped(view.types()));
        // Cortada com reticências antes da porcentagem, se não couber.
        int usageMax = barX + 4 - GRID_X + 1 - (header.capacity() > 0 ? 30 : 0);
        GuiPaint.text(g, font, GuiPaint.ellipsize(font, usage, usageMax), x + GRID_X - 1, y + usageY, GuiPaint.MUTED);
        if (header.capacity() > 0) {
            Component percent = Component.literal(percent(header.total(), header.capacity()));
            GuiPaint.textRight(g, font, percent, x + barX + 4, y + usageY, GuiPaint.FG);
            int lineX = x + GRID_X - 1;
            int lineW = barX + 4 - GRID_X + 1;
            g.fill(lineX, y + usageY + 10, lineX + lineW, y + usageY + 12, GuiPaint.INSET);
            int filled = (int) Math.min(lineW, Math.round((double) lineW * header.total() / header.capacity()));
            g.fill(lineX, y + usageY + 10, lineX + filled, y + usageY + 12, usageColor(header.total(), header.capacity()));
        }
    }

    /** Quantidade abreviada com a unidade: "12.6M" nos itens, "12.6M mB" nos tanques. */
    private static String abbreviated(long value, boolean items) {
        return items ? RateFormat.abbreviateAmount(value) : RateFormat.abbreviateAmount(value) + " mB";
    }

    /** Cor da barra de ocupação pelo estado: ok até 60%, atenção até 90%, erro acima. */
    private static int usageColor(long total, long capacity) {
        double used = (double) total / capacity;
        return used < 0.6 ? GuiPaint.OK : used < 0.9 ? GuiPaint.WARN : GuiPaint.DANGER;
    }

    private static String percent(long total, long capacity) {
        double value = 100.0 * total / capacity;
        if (total > 0 && value < 1) {
            return "<1%";
        }
        return Math.min(100, Math.round(value)) + "%";
    }
}
