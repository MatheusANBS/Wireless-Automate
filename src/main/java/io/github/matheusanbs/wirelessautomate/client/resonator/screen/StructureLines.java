package io.github.matheusanbs.wirelessautomate.client.resonator.screen;

import static io.github.matheusanbs.wirelessautomate.client.resonator.screen.CoreScreenLayout.STRUCTURE;
import static io.github.matheusanbs.wirelessautomate.client.resonator.screen.CoreScreenLayout.STRUCTURE_LINES;

import io.github.matheusanbs.wirelessautomate.client.GuiPaint;
import io.github.matheusanbs.wirelessautomate.client.GuiText;
import io.github.matheusanbs.wirelessautomate.menu.ResonatorMenu;
import io.github.matheusanbs.wirelessautomate.packet.ResonatorStatePayload;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/** O bloco Estrutura: o Sintonizador, as Portas de cada tipo e a montagem reconhecida. */
final class StructureLines {
    private static final String[] LABELS = {"structure.tuner", "structure.energy_ports", "structure.crystal_ports",
            "structure.layout"};
    private static final String[] LAYOUTS = {"layout.custom", "layout.vitrine", "layout.faixa"};

    private final ResonatorMenu menu;
    private int left;
    private int top;

    StructureLines(ResonatorMenu menu) {
        this.menu = menu;
    }

    void layout(int x, int y) {
        left = x;
        top = y;
    }

    void render(GuiGraphics g, Font font, int mouseX, int mouseY, float partialTick) {
        int x = left + STRUCTURE.x();
        int w = STRUCTURE.w();
        GuiText.draw(g, font, ResonatorText.key("structure"), x, top + STRUCTURE.y(), w, GuiPaint.MUTED);
        ResonatorStatePayload state = menu.state();
        for (int i = 0; i < LABELS.length; i++) {
            int y = top + STRUCTURE_LINES[i];
            Component label = ResonatorText.key(LABELS[i]);
            int labelW = Math.min(font.width(label), w);
            GuiText.draw(g, font, label, x, y, w, GuiPaint.MUTED);
            if (state == null) {
                continue;
            }
            Component value = value(state, i);
            int room = w - labelW - 6;
            if (font.width(value) <= room) {
                GuiPaint.textRight(g, font, value, x + w, y, GuiPaint.FG);
            } else {
                GuiText.draw(g, font, value, x + labelW + 6, y, room, GuiPaint.FG);
            }
        }
    }

    private static Component value(ResonatorStatePayload state, int line) {
        return switch (line) {
            case 0 -> ResonatorText.key(state.tuner() ? "yes" : "no");
            case 1 -> Component.literal(Integer.toString(state.energyPorts()));
            case 2 -> Component.literal(Integer.toString(state.crystalPorts()));
            default -> ResonatorText.key(LAYOUTS[Math.max(0, Math.min(LAYOUTS.length - 1, state.layout()))]);
        };
    }
}
