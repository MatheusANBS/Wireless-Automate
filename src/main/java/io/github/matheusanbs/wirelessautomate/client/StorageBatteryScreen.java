package io.github.matheusanbs.wirelessautomate.client;

import io.github.matheusanbs.wirelessautomate.block.RouterTier;
import io.github.matheusanbs.wirelessautomate.item.TierCoreItem;
import io.github.matheusanbs.wirelessautomate.menu.StorageBatteryMenu;
import io.github.matheusanbs.wirelessautomate.menu.StorageListMenu;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Inventory;

/**
 * Tela da Bateria: o tier, uma barra grande com a energia guardada, o valor e a porcentagem, e a
 * variação por tick (carregando, descarregando ou estável), medida entre dois estados do servidor.
 * Sem slots: a energia entra e sai pelos roteadores e pelos cabos de outros mods.
 */
public class StorageBatteryScreen extends AbstractContainerScreen<StorageBatteryMenu> {
    private static final int W = 220;
    private static final int H = 104;
    private static final int X0 = 10;
    private static final int HEAD_Y = 8;
    private static final int BAR_Y = 30;
    private static final int BAR_H = 22;
    private static final int ENERGY = 0xFFFFD34D;
    private static final int ENERGY_DARK = 0xFFC8901C;
    private static final int CHARGING = 0xFF5BD47A;
    private static final int DRAINING = 0xFFE5734B;

    public StorageBatteryScreen(StorageBatteryMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = W;
        this.imageHeight = H;
    }

    private static MutableComponent tr(String key, Object... args) {
        return Component.translatable("gui.wirelessautomate.battery." + key, args);
    }

    private RouterTier tier() {
        return minecraft != null && minecraft.level != null
                ? RouterTier.values()[StorageListMenu.tierOrdinal(minecraft.level, menu.pos())]
                : RouterTier.BASIC;
    }

    private boolean onBar(double mouseX, double mouseY) {
        return mouseX >= leftPos + X0 && mouseX < leftPos + W - X0 && mouseY >= topPos + BAR_Y && mouseY < topPos + BAR_Y + BAR_H;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        if (onBar(mouseX, mouseY) && menu.received()) {
            Component exact = menu.capacity() <= 0
                    ? tr("exact.unlimited", TierCoreItem.grouped(menu.stored()))
                    : tr("exact", TierCoreItem.grouped(menu.stored()), TierCoreItem.grouped(menu.capacity()));
            g.renderComponentTooltip(font, List.of(exact), mouseX, mouseY);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        // os textos são desenhados em renderBg, em coordenadas da tela
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        int x = leftPos;
        int y = topPos;
        RouterTier tier = tier();
        int trim = GuiPaint.tierColor(tier);
        GuiPaint.panel(g, x, y, W, H, trim);

        // cabeçalho: nome e tier (o nome cortado com reticências se não couber)
        Component tierName = Component.translatable(tier.translationKey());
        int pillW = font.width(tierName) + 8;
        FormattedCharSequence titleText = GuiPaint.ellipsize(font, title, W - 2 * X0 - pillW - 6);
        GuiPaint.text(g, font, titleText, x + X0, y + HEAD_Y + 3, GuiPaint.FG);
        int pillX = x + X0 + font.width(titleText) + 6;
        GuiPaint.pill(g, pillX, y + HEAD_Y + 1, pillW, 11, GuiPaint.INSET, trim);
        GuiPaint.text(g, font, tierName, pillX + 4, y + HEAD_Y + 3, trim);

        // barra: fundo afundado, energia com brilho em cima e marcas a cada 10%
        int barX = x + X0;
        int barW = W - 2 * X0;
        int barY = y + BAR_Y;
        GuiPaint.box(g, barX - 1, barY - 1, barW + 2, BAR_H + 2, GuiPaint.INSET, trim);
        long stored = menu.stored();
        long capacity = menu.capacity();
        int filled = capacity <= 0 ? (stored > 0 ? barW : 0)
                : (int) Math.min(barW, Math.round((double) barW * stored / capacity));
        if (filled > 0) {
            g.fill(barX, barY, barX + filled, barY + BAR_H, ENERGY_DARK);
            g.fill(barX, barY, barX + filled, barY + BAR_H - 4, ENERGY);
            g.fill(barX, barY, barX + filled, barY + 2, GuiPaint.mix(ENERGY, 0xFFFFFFFF, 0.5f));
        }
        for (int i = 1; i < 10; i++) {
            int tx = barX + barW * i / 10;
            g.fill(tx, barY + BAR_H - 3, tx + 1, barY + BAR_H, GuiPaint.mix(GuiPaint.INSET, 0xFF000000, 0.4f));
        }

        // valor e porcentagem
        Component amount = !menu.received() ? tr("loading")
                : capacity <= 0 ? tr("amount.unlimited", RateFormat.abbreviate(stored))
                : tr("amount", RateFormat.abbreviate(stored), RateFormat.abbreviate(capacity));
        GuiPaint.text(g, font, GuiPaint.ellipsize(font, amount, barW - 40), barX, barY + BAR_H + 6, GuiPaint.FG);
        if (capacity > 0 && menu.received()) {
            GuiPaint.textRight(g, font, Component.literal(percent(stored, capacity)), barX + barW, barY + BAR_H + 6, trim);
        }

        // variação por tick
        long rate = menu.rate();
        Component flow = rate > 0 ? tr("charging", RateFormat.abbreviate(rate))
                : rate < 0 ? tr("draining", RateFormat.abbreviate(-rate))
                : tr("idle");
        int color = rate > 0 ? CHARGING : rate < 0 ? DRAINING : GuiPaint.MUTED;
        GuiPaint.dot(g, barX, barY + BAR_H + 20, color);
        GuiPaint.text(g, font, GuiPaint.ellipsize(font, flow, barW - 10), barX + 9, barY + BAR_H + 19, GuiPaint.MUTED);
    }

    private static String percent(long stored, long capacity) {
        double value = 100.0 * stored / capacity;
        if (stored > 0 && value < 1) {
            return "<1%";
        }
        return Math.min(100, Math.round(value)) + "%";
    }
}
