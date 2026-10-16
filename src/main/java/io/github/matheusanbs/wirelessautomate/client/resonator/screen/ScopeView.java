package io.github.matheusanbs.wirelessautomate.client.resonator.screen;

import static io.github.matheusanbs.wirelessautomate.client.resonator.screen.CoreScreenLayout.SCOPE;

import io.github.matheusanbs.wirelessautomate.client.GuiPaint;
import io.github.matheusanbs.wirelessautomate.client.GuiText;
import io.github.matheusanbs.wirelessautomate.menu.ResonatorMenu;
import io.github.matheusanbs.wirelessautomate.packet.ResonatorStatePayload;
import io.github.matheusanbs.wirelessautomate.resonator.TuneSignal;
import net.minecraft.Util;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/**
 * O osciloscópio: uma senoide limpa em sintonia, chiado longe dela e só chiado sem sinal, como o {@code drawScope}
 * do mockup. Sem estado: a amplitude vem da eficiência do último estado e a fase do relógio do cliente. O fundo, a
 * grade e o traço são as cores fixas do {@link GuiPaint} (iguais nos dois temas).
 */
final class ScopeView {
    /** Seis períodos na largura. */
    private static final double PERIODS = 6;
    /** Radianos por segundo da fase. */
    private static final double PHASE_SPEED = 6;
    /**
     * O canvas do mockup tem 4 px por pixel de interface: as frequências do chiado (0,23 e 0,57 por px do canvas)
     * valem por esse fator em cada coluna da tela.
     */
    private static final double CANVAS_PX = 4;

    private final ResonatorMenu menu;
    private int left;
    private int top;

    ScopeView(ResonatorMenu menu) {
        this.menu = menu;
    }

    void layout(int x, int y) {
        left = x;
        top = y;
    }

    void render(GuiGraphics g, Font font, int mouseX, int mouseY, float partialTick) {
        int x = left + SCOPE.x();
        int y = top + SCOPE.y();
        int w = SCOPE.w();
        int h = SCOPE.h();
        g.fill(x, y, x + w, y + h, GuiPaint.SCOPE_BG);
        for (int i = 1; i < 10; i++) {
            int gx = x + Math.round(w * i / 10f);
            g.fill(gx, y, gx + 1, y + h, GuiPaint.SCOPE_GRID);
        }
        for (int i = 1; i < 5; i++) {
            int gy = y + Math.round(h * i / 5f);
            g.fill(x, gy, x + w, gy + 1, GuiPaint.SCOPE_GRID);
        }
        GuiPaint.outline(g, x, y, w, h, GuiPaint.DARK_TEXT);
        ResonatorStatePayload state = menu.state();
        if (state == null) {
            GuiText.draw(g, font, ResonatorText.key("loading"), x + 4, y + 4, w - 8, GuiPaint.SCOPE_LINE);
            return;
        }
        boolean signal = state.deviationSteps() != TuneSignal.NO_SIGNAL;
        double e = signal ? Math.max(0, Math.min(1, state.efficiencyPermille() / 1000.0)) : 0;
        long millis = Util.getMillis();
        wave(g, x, y, w, h, e, millis / 1000.0 * PHASE_SPEED, millis / 50);
        GuiText.draw(g, font, ResonatorText.scope(state.deviationSteps(), state.tuned()), x + 4, y + 4, w - 8, GuiPaint.SCOPE_LINE);
    }

    /** O traço, uma coluna por pixel, ligando as vizinhas por um traço vertical. */
    private static void wave(GuiGraphics g, int x, int y, int w, int h, double e, double phase, long frame) {
        double amplitude = 0.34 * h * e;
        double noise = 1 - e;
        int previous = -1;
        for (int c = 1; c < w - 1; c++) {
            double t = (double) c / w * Math.PI * PERIODS + phase;
            double px = c * CANVAS_PX;
            double n = noise * (Math.sin(px * 0.23 + phase * 7) * 0.45 + Math.sin(px * 0.57 - phase * 13) * 0.3
                    + (random(c, frame) - 0.5) * 0.7);
            double value = h / 2.0 - (Math.sin(t) * amplitude + n * h * 0.26);
            int row = Math.max(1, Math.min(h - 2, (int) Math.round(value)));
            int from = previous < 0 ? row : Math.min(previous, row);
            int to = previous < 0 ? row : Math.max(previous, row);
            g.fill(x + c, y + from, x + c + 1, y + to + 1, GuiPaint.SCOPE_LINE);
            previous = row;
        }
    }

    /** Um número de 0 a 1 por coluna e quadro de 50 ms: o chiado muda a cada tick, sem objeto aleatório. */
    private static double random(int column, long frame) {
        long h = column * 0x9E3779B97F4A7C15L + frame * 0xC2B2AE3D27D4EB4FL;
        h ^= h >>> 31;
        h *= 0xBF58476D1CE4E5B9L;
        h ^= h >>> 29;
        return (h >>> 11) * 0x1.0p-53;
    }
}
