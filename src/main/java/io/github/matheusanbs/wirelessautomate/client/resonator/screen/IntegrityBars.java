package io.github.matheusanbs.wirelessautomate.client.resonator.screen;

import static io.github.matheusanbs.wirelessautomate.client.resonator.screen.CoreScreenLayout.INTEGRITY_DY;
import static io.github.matheusanbs.wirelessautomate.client.resonator.screen.CoreScreenLayout.INTEGRITY_H;
import static io.github.matheusanbs.wirelessautomate.client.resonator.screen.CoreScreenLayout.INTEGRITY_W;

import io.github.matheusanbs.wirelessautomate.Config;
import io.github.matheusanbs.wirelessautomate.client.GuiPaint;
import io.github.matheusanbs.wirelessautomate.menu.ResonatorMenu;
import io.github.matheusanbs.wirelessautomate.resonator.CoreEcoState;
import io.github.matheusanbs.wirelessautomate.resonator.SignalCrystalItem;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * A integridade dos cristais: uma barra fina sob cada câmara ocupada, entre ela e o slot do catalisador, proporcional
 * à integridade do próprio {@code ItemStack} (o máximo é o da config do servidor, que chega ao cliente) e vermelha
 * inteira no rachado; e as linhas da dica da câmara.
 */
final class IntegrityBars {
    private final ResonatorMenu menu;
    private int left;
    private int top;

    IntegrityBars(ResonatorMenu menu) {
        this.menu = menu;
    }

    void layout(int x, int y) {
        left = x;
        top = y;
    }

    void render(GuiGraphics g) {
        int max = Config.crystalIntegrity();
        for (int i = 0; i < menu.chambers(); i++) {
            Slot slot = menu.getSlot(i);
            ItemStack stack = slot.getItem();
            if (!(stack.getItem() instanceof SignalCrystalItem)) {
                continue;
            }
            int x = left + slot.x;
            int y = top + slot.y + INTEGRITY_DY;
            if (SignalCrystalItem.isCracked(stack)) {
                g.fill(x, y, x + INTEGRITY_W, y + INTEGRITY_H, GuiPaint.DANGER);
                continue;
            }
            int integrity = SignalCrystalItem.integrity(stack);
            g.fill(x, y, x + INTEGRITY_W, y + INTEGRITY_H, GuiPaint.alpha(GuiPaint.MUTED, 110));
            int filled = max <= 0 ? INTEGRITY_W : Math.round((float) INTEGRITY_W * integrity / max);
            g.fill(x, y, x + filled, y + INTEGRITY_H, 2 * integrity > max ? GuiPaint.OK : GuiPaint.WARN);
        }
    }

    /**
     * As linhas da integridade na dica de uma câmara: "Integridade 5 de 8", ou o rachado com a receita de refazer (e,
     * no Ultimate, o Eco para o Afinado Ultimate). Devolve se o cristal está rachado.
     */
    boolean addLines(List<Component> lines, ItemStack stack) {
        if (SignalCrystalItem.isCracked(stack)) {
            lines.add(ResonatorText.accent(ResonatorText.key("chamber.cracked", Config.recastCrystals())));
            if (menu.ecoSlot() >= 0 && CoreEcoState.isFuel(stack)) {
                lines.add(ResonatorText.muted(ResonatorText.key("chamber.cracked.eco")));
            }
            return true;
        }
        lines.add(ResonatorText.muted(ResonatorText.key("chamber.integrity", SignalCrystalItem.integrity(stack),
                Config.crystalIntegrity())));
        return false;
    }
}
