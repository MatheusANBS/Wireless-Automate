package io.github.matheusanbs.wirelessautomate.client.resonator.screen;

import static io.github.matheusanbs.wirelessautomate.client.resonator.screen.CoreScreenLayout.ECO_BOX;

import io.github.matheusanbs.wirelessautomate.Config;
import io.github.matheusanbs.wirelessautomate.client.GuiPaint;
import io.github.matheusanbs.wirelessautomate.client.GuiText;
import io.github.matheusanbs.wirelessautomate.menu.ResonatorMenu;
import io.github.matheusanbs.wirelessautomate.packet.ResonatorStatePayload;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.Slot;
import org.jetbrains.annotations.Nullable;

/**
 * A caixa do Eco Ressonante, só no Ultimate, abaixo da Estrutura: o slot do Eco (sempre vazio, o Afinado Ultimate
 * rachado posto nele é queimado na hora, com o tracejado), "Eco 10 · +50%" e a dica curta; a dica inteira ao passar
 * o mouse na caixa.
 */
final class EcoBox {
    /** O texto começa 3 px depois do fundo do slot. */
    private static final int TEXT_GAP = 3;

    private final ResonatorMenu menu;
    private int left;
    private int top;

    EcoBox(ResonatorMenu menu) {
        this.menu = menu;
    }

    void layout(int x, int y) {
        left = x;
        top = y;
    }

    void render(GuiGraphics g, Font font) {
        if (menu.ecoSlot() < 0) {
            return;
        }
        GuiPaint.box(g, left + ECO_BOX.x(), top + ECO_BOX.y(), ECO_BOX.w(), ECO_BOX.h(), GuiPaint.INSET, GuiPaint.LINE);
        Slot slot = menu.getSlot(menu.ecoSlot());
        int x = left + slot.x - 1;
        int y = top + slot.y - 1;
        GuiPaint.slot(g, x, y);
        CrystalSlots.dashed(g, x + 4, y + 4, 10, GuiPaint.alpha(GuiPaint.LINE, 160));
        int textX = x + 18 + TEXT_GAP;
        int textW = left + ECO_BOX.x() + ECO_BOX.w() - 3 - textX;
        ResonatorStatePayload state = menu.state();
        Component value = state == null ? ResonatorText.key("eco.label")
                : ResonatorText.key("eco.value", ResonatorText.fe(state.eco()),
                        ResonatorText.fe(Math.round(state.ecoBonusPermille() / 10.0)));
        GuiText.draw(g, font, value, textX, y + 2, textW, GuiPaint.FG);
        GuiText.draw(g, font, ResonatorText.key("eco.hint"), textX, y + 11, textW, GuiPaint.MUTED);
    }

    @Nullable List<Component> tooltip(int mouseX, int mouseY) {
        if (menu.ecoSlot() < 0 || !menu.getCarried().isEmpty() || !ECO_BOX.contains(left, top, mouseX, mouseY)) {
            return null;
        }
        Component bonus = ResonatorText.key("eco.tooltip.bonus",
                ResonatorText.loss((int) Math.round(Config.ecoBonus() * 1000)));
        long max = Config.ecoMax();
        if (max > 0) {
            return ResonatorText.tip(ResonatorText.key("eco.tooltip"), ResonatorText.key("eco.tooltip.burn"), bonus,
                    ResonatorText.key("eco.tooltip.max", ResonatorText.fe(max)), ResonatorText.key("eco.tooltip.kept"));
        }
        return ResonatorText.tip(ResonatorText.key("eco.tooltip"), ResonatorText.key("eco.tooltip.burn"), bonus,
                ResonatorText.key("eco.tooltip.kept"));
    }

    int[] slotCenter() {
        Slot slot = menu.getSlot(menu.ecoSlot());
        return new int[] {left + slot.x + 8, top + slot.y + 8};
    }
}
