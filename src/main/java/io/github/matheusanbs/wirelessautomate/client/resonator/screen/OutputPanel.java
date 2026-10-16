package io.github.matheusanbs.wirelessautomate.client.resonator.screen;

import static io.github.matheusanbs.wirelessautomate.client.resonator.screen.CoreScreenLayout.EFFICIENCY;
import static io.github.matheusanbs.wirelessautomate.client.resonator.screen.CoreScreenLayout.EFFICIENCY_BAR;
import static io.github.matheusanbs.wirelessautomate.client.resonator.screen.CoreScreenLayout.ENERGY;
import static io.github.matheusanbs.wirelessautomate.client.resonator.screen.CoreScreenLayout.ENERGY_BAR;
import static io.github.matheusanbs.wirelessautomate.client.resonator.screen.CoreScreenLayout.ENERGY_HINT;
import static io.github.matheusanbs.wirelessautomate.client.resonator.screen.CoreScreenLayout.GENERATION;

import io.github.matheusanbs.wirelessautomate.client.GuiPaint;
import io.github.matheusanbs.wirelessautomate.client.GuiText;
import io.github.matheusanbs.wirelessautomate.client.resonator.screen.CoreScreenLayout.Rect;
import io.github.matheusanbs.wirelessautomate.item.TierCoreItem;
import io.github.matheusanbs.wirelessautomate.menu.ResonatorMenu;
import io.github.matheusanbs.wirelessautomate.packet.ResonatorStatePayload;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * A saída: a eficiência com a barra e a marca do teto do Sintonizador, a geração, a energia guardada com a barra e
 * a legenda do buffer.
 */
final class OutputPanel {
    /** A marca do teto sai 2 px acima e abaixo da barra. */
    private static final int MARK_OUT = 2;

    private final ResonatorMenu menu;
    private int left;
    private int top;

    OutputPanel(ResonatorMenu menu) {
        this.menu = menu;
    }

    void layout(int x, int y) {
        left = x;
        top = y;
    }

    void render(GuiGraphics g, Font font, int mouseX, int mouseY, float partialTick) {
        ResonatorStatePayload state = menu.state();
        Component efficiency = state == null ? null : Component.literal(ResonatorText.percent(state.efficiencyPermille()));
        row(g, font, EFFICIENCY, ResonatorText.key("efficiency"), efficiency);
        double e = state == null ? 0 : clamp01(state.efficiencyPermille() / 1000.0);
        int fill = e >= 0.95 ? GuiPaint.OK : e >= 0.75 ? GuiPaint.WARN : GuiPaint.ACCENT;
        bar(g, EFFICIENCY_BAR, e, fill);
        if (state != null) {
            int inner = EFFICIENCY_BAR.w() - 2;
            int mark = left + EFFICIENCY_BAR.x() + 1
                    + (int) Math.round(inner * clamp01(state.ceilingPermille() / 1000.0));
            int y = top + EFFICIENCY_BAR.y();
            g.fill(mark, y - MARK_OUT, mark + 1, y + EFFICIENCY_BAR.h() + MARK_OUT, GuiPaint.FG);
        }
        Component generation = state == null ? null
                : ResonatorText.key("generation.value", ResonatorText.fe(state.generation()));
        row(g, font, GENERATION, ResonatorText.key("generation"), generation);
        Component energy = state == null ? null
                : ResonatorText.key("energy.value", ResonatorText.fe(state.energy()), ResonatorText.fe(state.capacity()));
        row(g, font, ENERGY, ResonatorText.key("energy"), energy);
        bar(g, ENERGY_BAR, state == null ? 0 : fraction(state.energy(), state.capacity()), GuiPaint.WARN);
        GuiText.draw(g, font, ResonatorText.key("energy.hint"), left + ENERGY_HINT.x(), top + ENERGY_HINT.y(),
                ENERGY_HINT.w(), GuiPaint.MUTED);
    }

    @Nullable List<Component> tooltip(int mouseX, int mouseY) {
        ResonatorStatePayload state = menu.state();
        if (state == null) {
            return null;
        }
        Rect bar = EFFICIENCY_BAR;
        if (mouseX >= left + bar.x() && mouseX < left + bar.x() + bar.w() && mouseY >= top + bar.y() - MARK_OUT
                && mouseY < top + bar.y() + bar.h() + MARK_OUT) {
            return ResonatorText.tip(ResonatorText.key("efficiency.tooltip"),
                    ResonatorText.key("efficiency.tooltip.ceiling"));
        }
        if (GENERATION.contains(left, top, mouseX, mouseY)) {
            return ResonatorText.tip(ResonatorText.key("generation.tooltip", TierCoreItem.grouped(state.generation())),
                    ResonatorText.key("generation.tooltip.max", TierCoreItem.grouped(state.maxGeneration())));
        }
        if (ENERGY.contains(left, top, mouseX, mouseY) || ENERGY_BAR.contains(left, top, mouseX, mouseY)) {
            int percent = (int) Math.round(100 * fraction(state.energy(), state.capacity()));
            return ResonatorText.tip(ResonatorText.key("energy.tooltip", TierCoreItem.grouped(state.energy()),
                    TierCoreItem.grouped(state.capacity())), ResonatorText.key("energy.tooltip.port", percent));
        }
        return null;
    }

    /** O rótulo à esquerda e o valor à direita; o valor que não cabe é cortado pelo {@link GuiText}. */
    private void row(GuiGraphics g, Font font, Rect rect, Component label, @Nullable Component value) {
        int x = left + rect.x();
        int y = top + rect.y();
        int labelW = Math.min(font.width(label), rect.w());
        GuiText.draw(g, font, label, x, y, rect.w(), GuiPaint.MUTED);
        if (value == null) {
            return;
        }
        int room = rect.w() - labelW - 6;
        if (font.width(value) <= room) {
            GuiPaint.textRight(g, font, value, x + rect.w(), y, GuiPaint.FG);
        } else {
            GuiText.draw(g, font, value, x + labelW + 6, y, room, GuiPaint.FG);
        }
    }

    /** A barra afundada com o preenchimento de 1 px para dentro da borda. */
    private void bar(GuiGraphics g, Rect rect, double fraction, int color) {
        int x = left + rect.x();
        int y = top + rect.y();
        GuiPaint.box(g, x, y, rect.w(), rect.h(), GuiPaint.INSET, GuiPaint.LINE);
        int filled = (int) Math.round((rect.w() - 2) * clamp01(fraction));
        if (filled > 0) {
            g.fill(x + 1, y + 1, x + 1 + filled, y + rect.h() - 1, color);
        }
    }

    private static double fraction(long value, long total) {
        return total <= 0 ? 0 : clamp01((double) value / total);
    }

    private static double clamp01(double value) {
        return Math.max(0, Math.min(1, value));
    }
}
