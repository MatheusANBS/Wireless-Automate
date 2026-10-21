package io.github.matheusanbs.wirelessautomate.client.resonator.screen;

import static io.github.matheusanbs.wirelessautomate.client.resonator.screen.CoreScreenLayout.INTERFERENCE;

import io.github.matheusanbs.wirelessautomate.Config;
import io.github.matheusanbs.wirelessautomate.client.GuiPaint;
import io.github.matheusanbs.wirelessautomate.client.GuiText;
import io.github.matheusanbs.wirelessautomate.menu.ResonatorMenu;
import io.github.matheusanbs.wirelessautomate.packet.ResonatorStatePayload;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * A linha da interferência, entre a legenda do buffer e a Estrutura: "Interferência: nenhuma" ou, em laranja, a perda
 * e a distância do Núcleo mais perto ("−15% · Núcleo a 24 blocos"). A dica traz a regra pela config do servidor.
 */
final class InterferenceLine {
    private final ResonatorMenu menu;
    private int left;
    private int top;

    InterferenceLine(ResonatorMenu menu) {
        this.menu = menu;
    }

    void layout(int x, int y) {
        left = x;
        top = y;
    }

    void render(GuiGraphics g, Font font) {
        ResonatorStatePayload state = menu.state();
        if (state == null) {
            return;
        }
        boolean loss = state.interferencePermille() > 0;
        Component text = loss
                ? ResonatorText.key("interference.loss", ResonatorText.loss(state.interferencePermille()), state.nearest())
                : ResonatorText.key("interference.none");
        GuiText.draw(g, font, text, left + INTERFERENCE.x(), top + INTERFERENCE.y(), INTERFERENCE.w(),
                loss ? GuiPaint.WARN : GuiPaint.MUTED);
    }

    @Nullable List<Component> tooltip(int mouseX, int mouseY) {
        ResonatorStatePayload state = menu.state();
        if (state == null || !INTERFERENCE.contains(left, top, mouseX, mouseY)) {
            return null;
        }
        Component rule = Config.interferenceRadius() <= 0 ? ResonatorText.key("interference.tooltip.off")
                : ResonatorText.key("interference.tooltip.rule", Config.interferenceRadius(),
                        ResonatorText.loss((int) Math.round(Config.interferencePenalty() * 1000)),
                        ResonatorText.loss((int) Math.round(Config.interferenceMax() * 1000)));
        if (state.interferencePermille() <= 0) {
            return ResonatorText.tip(ResonatorText.key("interference.tooltip"), rule);
        }
        return ResonatorText.tip(ResonatorText.key("interference.tooltip"), rule,
                ResonatorText.key("interference.tooltip.nearest", state.nearest()));
    }
}
