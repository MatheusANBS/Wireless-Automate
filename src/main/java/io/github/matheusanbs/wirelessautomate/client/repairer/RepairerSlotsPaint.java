package io.github.matheusanbs.wirelessautomate.client.repairer;

import static io.github.matheusanbs.wirelessautomate.client.repairer.RepairerLayout.ARROW_X;
import static io.github.matheusanbs.wirelessautomate.client.repairer.RepairerLayout.ARROW_Y;
import static io.github.matheusanbs.wirelessautomate.client.repairer.RepairerLayout.CRACKED_LABEL;
import static io.github.matheusanbs.wirelessautomate.client.repairer.RepairerLayout.FEED_LABEL;
import static io.github.matheusanbs.wirelessautomate.client.repairer.RepairerLayout.FEED_PER;
import static io.github.matheusanbs.wirelessautomate.client.repairer.RepairerLayout.REPAIRED_LABEL;
import static io.github.matheusanbs.wirelessautomate.client.repairer.RepairerLayout.SLOT;

import io.github.matheusanbs.wirelessautomate.client.GuiPaint;
import io.github.matheusanbs.wirelessautomate.client.GuiText;
import io.github.matheusanbs.wirelessautomate.client.GuiText.Fitted;
import io.github.matheusanbs.wirelessautomate.menu.RepairerMenu;
import io.github.matheusanbs.wirelessautomate.packet.RepairerStatePayload;
import io.github.matheusanbs.wirelessautomate.repairer.RepairerSlots;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.inventory.Slot;
import org.jetbrains.annotations.Nullable;

/**
 * Os 17 slots do Reparador: o fundo de cada um, os rótulos RACHADOS, CRISTAIS CHEIOS (com o "4 por reparo", em
 * laranja quando o estoque não paga um reparo) e REPARADOS, a moldura da entrada que trabalha e a barra do progresso
 * sobre o item dela. Os itens são do vanilla.
 */
final class RepairerSlotsPaint {
    private static final int BAR_W = 16;

    private final RepairerMenu menu;
    private int left;
    private int top;
    private @Nullable Fitted cracked;
    private @Nullable Fitted feed;
    private @Nullable Fitted repaired;
    private @Nullable Fitted per;
    private int shownNeeded = -1;

    RepairerSlotsPaint(RepairerMenu menu) {
        this.menu = menu;
    }

    void layout(int x, int y, Font font) {
        left = x;
        top = y;
        cracked = GuiText.fit(font, RepairerText.key("cracked"), CRACKED_LABEL.w());
        feed = GuiText.fit(font, RepairerText.key("feed"), FEED_LABEL.w());
        repaired = GuiText.fit(font, RepairerText.key("repaired"), REPAIRED_LABEL.w());
    }

    void render(GuiGraphics g, Font font) {
        GuiText.draw(g, font, cracked, left + CRACKED_LABEL.x(), top + CRACKED_LABEL.y(), GuiPaint.MUTED);
        GuiText.draw(g, font, feed, left + FEED_LABEL.x(), top + FEED_LABEL.y(), GuiPaint.MUTED);
        GuiText.draw(g, font, repaired, left + REPAIRED_LABEL.x(), top + REPAIRED_LABEL.y(), GuiPaint.MUTED);
        RepairerStatePayload state = menu.state();
        boolean powerless = RepairerText.powerless(menu);
        for (int i = 0; i < RepairerSlots.TOTAL; i++) {
            Slot slot = menu.getSlot(i);
            int x = left + slot.x - 1;
            int y = top + slot.y - 1;
            GuiPaint.slot(g, x, y);
            if (RepairerSlots.isInput(i) && state != null && state.ends()[i] > 0 && !state.blocked()[i]) {
                GuiPaint.outline(g, x, y, SLOT, SLOT, powerless ? GuiPaint.MUTED : GuiPaint.ACCENT);
            }
        }
        int needed = state == null ? 0 : state.needed();
        if (per == null || needed != shownNeeded) {
            shownNeeded = needed;
            per = GuiText.fit(font, RepairerText.key("feed.per", needed), FEED_PER.w());
        }
        boolean missing = state != null && menu.getSlot(RepairerSlots.FEED).getItem().getCount() < needed;
        GuiText.draw(g, font, per, left + FEED_PER.x(), top + FEED_PER.y(), missing ? GuiPaint.WARN : GuiPaint.MUTED);
        GuiPaint.arrowDown(g, left + ARROW_X, top + ARROW_Y, GuiPaint.MUTED);
    }

    /**
     * A barra do progresso sobre a entrada que trabalha, chamada pela anfitriã logo depois de o vanilla desenhar o item
     * do slot (em coordenadas do slot), para ficar por cima dele como a barra de um item.
     */
    void renderBar(GuiGraphics g, Slot slot, long gameTime) {
        RepairerStatePayload state = menu.state();
        if (state == null || slot.container != menu.getSlot(0).container || !RepairerSlots.isInput(slot.index)
                || state.ends()[slot.index] <= 0) {
            return;
        }
        int filled = Math.round(BAR_W * menu.progress(slot.index, gameTime) / 1000f);
        int color = state.blocked()[slot.index] ? GuiPaint.WARN
                : RepairerText.powerless(menu) ? GuiPaint.MUTED : GuiPaint.ACCENT;
        g.pose().pushPose();
        g.pose().translate(0, 0, 300);
        g.fill(slot.x, slot.y + 14, slot.x + BAR_W, slot.y + 16, GuiPaint.RING);
        g.fill(slot.x, slot.y + 14, slot.x + filled, slot.y + 16, color);
        g.pose().popPose();
    }

    /** O índice (0 a 16) do slot do Reparador sob o mouse, ou -1. */
    int slotAt(double mouseX, double mouseY) {
        for (int i = 0; i < RepairerSlots.TOTAL; i++) {
            Slot slot = menu.getSlot(i);
            int x = left + slot.x - 1;
            int y = top + slot.y - 1;
            if (mouseX >= x && mouseX < x + SLOT && mouseY >= y && mouseY < y + SLOT) {
                return i;
            }
        }
        return -1;
    }

    int[] slotCenter(int index) {
        Slot slot = menu.getSlot(index);
        return new int[] {left + slot.x + 8, top + slot.y + 8};
    }
}
