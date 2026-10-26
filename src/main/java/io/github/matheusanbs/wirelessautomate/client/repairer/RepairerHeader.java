package io.github.matheusanbs.wirelessautomate.client.repairer;

import static io.github.matheusanbs.wirelessautomate.client.repairer.RepairerLayout.ENERGY_BAR;
import static io.github.matheusanbs.wirelessautomate.client.repairer.RepairerLayout.TITLE;

import io.github.matheusanbs.wirelessautomate.client.GuiPaint;
import io.github.matheusanbs.wirelessautomate.client.GuiText;
import io.github.matheusanbs.wirelessautomate.client.GuiText.Fitted;
import io.github.matheusanbs.wirelessautomate.client.RateFormat;
import io.github.matheusanbs.wirelessautomate.menu.RepairerMenu;
import io.github.matheusanbs.wirelessautomate.packet.RepairerStatePayload;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * O cabeçalho: o título e o medidor de energia ({@code energia / capacidade FE}, abreviados, com a barra coral; os
 * números inteiros vão na dica). O texto do medidor só é medido de novo quando a energia muda.
 */
final class RepairerHeader {
    private final RepairerMenu menu;
    private final Component title;
    private int left;
    private int top;
    private @Nullable Fitted titleText;
    private @Nullable Fitted energyText;
    private long shownEnergy = -1;
    private long shownCapacity = -1;
    private int energyWidth;

    RepairerHeader(RepairerMenu menu, Component title) {
        this.menu = menu;
        this.title = title;
    }

    void layout(int x, int y, Font font) {
        left = x;
        top = y;
        titleText = GuiText.fit(font, title, TITLE.w());
    }

    void render(GuiGraphics g, Font font) {
        if (titleText != null) {
            GuiText.draw(g, font, titleText, left + TITLE.x(), top + TITLE.y(), GuiPaint.FG);
        }
        RepairerStatePayload state = menu.state();
        int x = left + ENERGY_BAR.x();
        int y = top + ENERGY_BAR.y();
        GuiPaint.box(g, x, y, ENERGY_BAR.w(), ENERGY_BAR.h(), GuiPaint.INSET, GuiPaint.LINE);
        if (state == null) {
            return;
        }
        double fraction = state.capacity() <= 0 ? 0 : Math.min(1, (double) state.energy() / state.capacity());
        int filled = (int) Math.round((ENERGY_BAR.w() - 2) * fraction);
        if (filled > 0) {
            int color = RepairerText.powerless(menu) ? GuiPaint.MUTED : GuiPaint.ACCENT;
            g.fill(x + 1, y + 1, x + 1 + filled, y + ENERGY_BAR.h() - 1, color);
            g.fill(x + 1, y + 1, x + 1 + filled, y + 4, GuiPaint.alpha(0xFFFFFFFF, 56));
        }
        if (energyText == null || shownEnergy != state.energy() || shownCapacity != state.capacity()) {
            shownEnergy = state.energy();
            shownCapacity = state.capacity();
            energyText = GuiText.fit(font, RepairerText.key("energy", RateFormat.abbreviate(state.energy()),
                    RateFormat.abbreviate(state.capacity())), ENERGY_BAR.w() - 6);
            energyWidth = Math.min(energyText.width(), font.width(energyText.shown()));
        }
        int textX = x + (ENERGY_BAR.w() - energyWidth) / 2;
        GuiText.draw(g, font, energyText, textX, y + 2, GuiPaint.FG);
    }

    boolean energyHovered(double mouseX, double mouseY) {
        return ENERGY_BAR.contains(left, top, mouseX, mouseY);
    }
}
