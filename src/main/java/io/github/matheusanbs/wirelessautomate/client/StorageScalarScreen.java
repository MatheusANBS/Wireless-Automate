package io.github.matheusanbs.wirelessautomate.client;

import io.github.matheusanbs.wirelessautomate.network.RateMath;
import io.github.matheusanbs.wirelessautomate.block.RouterTier;
import io.github.matheusanbs.wirelessautomate.item.TierCoreItem;
import io.github.matheusanbs.wirelessautomate.menu.StorageListMenu;
import io.github.matheusanbs.wirelessautomate.menu.StorageScalarMenu;
import io.github.matheusanbs.wirelessautomate.network.ResourceType;
import io.github.matheusanbs.wirelessautomate.storage.StorageKind;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.player.Inventory;

/**
 * Tela de um valor só (Bateria e Tanque de Source): o tier, uma barra grande com o que está guardado,
 * o valor e a porcentagem, e a variação (por tick na Bateria, por segundo no Tanque de Source;
 * enchendo, esvaziando ou parado), medida entre dois estados do servidor. O tanque ainda traz uma
 * dica. Sem slots: o conteúdo entra e sai pelos roteadores e pelos cabos de outros mods.
 */
public class StorageScalarScreen extends AbstractContainerScreen<StorageScalarMenu> {
    /** Cabe "Tanque de Source Wireless" com a pílula do maior tier ("Allthemodium"), sem cortar. */
    private static final int W = 240;
    private static final int H = 104;
    private static final int HINT_H = 34;
    private static final int HINT_LINES = 3;
    private static final int X0 = 10;
    private static final int HEAD_Y = 8;
    private static final int BAR_Y = 30;
    private static final int BAR_H = 22;
    private static final int CHARGING = GuiPaint.OK;
    private static final int DRAINING = GuiPaint.WARN;
    /** Materiais {@code energia} e {@code source} de {@code identidade.py} (tons 2, 1 e 3). */
    private static final int ENERGY = 0xFFFFD042;
    private static final int ENERGY_DARK = 0xFFD9931A;
    private static final int ENERGY_LIGHT = 0xFFFFF1B3;
    private static final int SOURCE = 0xFFD08AF0;
    private static final int SOURCE_DARK = 0xFF9B4DC6;
    private static final int SOURCE_LIGHT = 0xFFF0C8FA;

    public StorageScalarScreen(StorageScalarMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = W;
        this.imageHeight = heightFor(menu.kind());
    }

    private boolean source() {
        return menu.kind() == StorageKind.SOURCE_TANK;
    }

    private MutableComponent tr(String key, Object... args) {
        return Component.translatable("gui.wirelessautomate." + (source() ? "source_tank." : "battery.") + key, args);
    }

    /** O tanque leva a dica em até 3 linhas (a frase inteira cabe em 3 nos dois idiomas, com 220 px). */
    private static int heightFor(StorageKind kind) {
        return kind == StorageKind.SOURCE_TANK ? H + HINT_H : H;
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
        GuiPaint.panel(g, x, y, W, imageHeight, trim);

        // cabeçalho: nome e tier (o nome cortado com reticências se não couber)
        Component tierName = Component.translatable(tier.translationKey());
        int pillW = font.width(tierName) + 8;
        int titleW = Math.min(font.width(title), W - 2 * X0 - pillW - 6);
        GuiText.draw(g, font, title, x + X0, y + HEAD_Y + 3, titleW, GuiPaint.FG);
        int pillX = x + X0 + titleW + 6;
        GuiPaint.pill(g, pillX, y + HEAD_Y + 1, pillW, 11, GuiPaint.PANEL, trim);
        GuiPaint.text(g, font, tierName, pillX + 4, y + HEAD_Y + 3, GuiPaint.FG);

        // barra: fundo afundado, energia com brilho em cima e marcas a cada 10%
        int barX = x + X0;
        int barW = W - 2 * X0;
        int barY = y + BAR_Y;
        GuiPaint.box(g, barX - 1, barY - 1, barW + 2, BAR_H + 2, GuiPaint.INSET, GuiPaint.LINE);
        int bar = source() ? SOURCE : ENERGY;
        int barDark = source() ? SOURCE_DARK : ENERGY_DARK;
        int barLight = source() ? SOURCE_LIGHT : ENERGY_LIGHT;
        long stored = menu.stored();
        long capacity = menu.capacity();
        int filled = capacity <= 0 ? (stored > 0 ? barW : 0)
                : (int) Math.min(barW, Math.round((double) barW * stored / capacity));
        if (filled > 0) {
            g.fill(barX, barY, barX + filled, barY + BAR_H, barDark);
            g.fill(barX, barY, barX + filled, barY + BAR_H - 4, bar);
            g.fill(barX, barY, barX + filled, barY + 2, barLight);
        }
        for (int i = 1; i < 10; i++) {
            int tx = barX + barW * i / 10;
            g.fill(tx, barY + BAR_H - 3, tx + 1, barY + BAR_H, GuiPaint.BEVEL_DARK);
        }

        // valor e porcentagem
        Component amount = !menu.received() ? tr("loading")
                : capacity <= 0 ? tr("amount.unlimited", RateFormat.abbreviate(stored))
                : tr("amount", RateFormat.abbreviate(stored), RateFormat.abbreviate(capacity));
        GuiText.draw(g, font, amount, barX, barY + BAR_H + 6, barW - 40, GuiPaint.FG);
        if (capacity > 0 && menu.received()) {
            GuiPaint.textRight(g, font, Component.literal(percent(stored, capacity)), barX + barW, barY + BAR_H + 6,
                    GuiPaint.FG);
        }

        // variação: por tick na Bateria, por segundo no Tanque de Source
        long rate = source() ? RateMath.perSecond(menu.rate()) : menu.rate();
        Component flow = rate > 0 ? tr("charging", RateFormat.abbreviate(rate))
                : rate < 0 ? tr("draining", RateFormat.abbreviate(-rate))
                : tr("idle");
        int color = rate > 0 ? CHARGING : rate < 0 ? DRAINING : GuiPaint.MUTED;
        GuiPaint.dot(g, barX, barY + BAR_H + 20, color);
        GuiText.draw(g, font, flow, barX + 9, barY + BAR_H + 19, barW - 10, GuiPaint.MUTED);

        // dica do tanque: um filete e o texto em até três linhas, com o ícone da Source
        if (source()) {
            int ruleY = y + H - 12;
            g.fill(barX, ruleY, barX + barW, ruleY + 1, GuiPaint.LINE);
            ResourceStyle.drawIcon(g, ResourceType.SOURCE, barX, ruleY + 6);
            GuiText.wrap(g, font, tr("hint"), barX + 13, ruleY + 6, barW - 13, HINT_LINES, GuiPaint.MUTED);
        }
    }

    private static String percent(long stored, long capacity) {
        double value = 100.0 * stored / capacity;
        if (stored > 0 && value < 1) {
            return "<1%";
        }
        return Math.min(100, Math.round(value)) + "%";
    }
}
