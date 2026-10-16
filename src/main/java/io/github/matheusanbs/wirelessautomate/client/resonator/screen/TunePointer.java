package io.github.matheusanbs.wirelessautomate.client.resonator.screen;

import static io.github.matheusanbs.wirelessautomate.client.resonator.screen.CoreScreenLayout.LAMP;
import static io.github.matheusanbs.wirelessautomate.client.resonator.screen.CoreScreenLayout.POINTER;

import io.github.matheusanbs.wirelessautomate.client.GuiPaint;
import io.github.matheusanbs.wirelessautomate.client.GuiText;
import io.github.matheusanbs.wirelessautomate.menu.ResonatorMenu;
import io.github.matheusanbs.wirelessautomate.packet.ResonatorStatePayload;
import io.github.matheusanbs.wirelessautomate.resonator.TuneSignal;
import net.minecraft.Util;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * O ponteiro de sintonia e a lâmpada embaixo dele. A faixa vai de −2 a +2 MHz com a estação no centro e o platô em
 * verde; o ponteiro mostra onde o dial está. Sem sinal, ele encosta do lado do último sinal visto (ou na esquerda,
 * como a galeria do mockup), tremendo um pouco, apagado e com o convite para varrer o dial.
 */
final class TunePointer {
    private static final int BAND_H = 14;
    /** O tremor do ponteiro encostado: amplitude em MHz e período em ms, como o mockup. */
    private static final double JITTER_MHZ = 0.06;
    private static final double JITTER_MS = 90;

    private final ResonatorMenu menu;
    private int left;
    private int top;
    /** O sinal do último desvio com sinal: o lado em que o ponteiro encosta quando o sinal some. */
    private int lastSide = -1;

    TunePointer(ResonatorMenu menu) {
        this.menu = menu;
    }

    void layout(int x, int y) {
        left = x;
        top = y;
    }

    void render(GuiGraphics g, Font font, int mouseX, int mouseY, float partialTick) {
        int x = left + POINTER.x();
        int y = top + POINTER.y();
        int w = POINTER.w();
        GuiPaint.inset(g, x, y, w, BAND_H);
        int[] plateau = PointerScale.plateau(w);
        g.fill(x + plateau[0], y, x + plateau[1], y + BAND_H, GuiPaint.alpha(GuiPaint.OK, 56));
        for (int tenths = -20; tenths <= 20; tenths += 5) {
            double mhz = tenths / 10.0;
            if (!PointerScale.isTick(mhz)) {
                continue;
            }
            int tx = x + PointerScale.x(w, mhz);
            boolean major = PointerScale.isMajor(mhz);
            g.fill(tx, y + (major ? 9 : 11), tx + 1, y + BAND_H, GuiPaint.MUTED);
            if (major) {
                GuiPaint.textCentered(g, font, Component.literal(label(tenths / 10)), tx + 1, y + BAND_H + 1,
                        GuiPaint.MUTED);
            }
        }
        ResonatorStatePayload state = menu.state();
        if (state != null) {
            pointer(g, font, x, y, w, state.deviationSteps());
        }
        lamp(g, font, state);
    }

    private void pointer(GuiGraphics g, Font font, int x, int y, int w, int steps) {
        boolean signal = steps != TuneSignal.NO_SIGNAL;
        double mhz = PointerScale.pointerMhz(steps);
        if (signal) {
            if (steps != 0) {
                lastSide = steps > 0 ? -1 : 1;
            }
        } else {
            mhz = lastSide * mhz + Math.sin(Util.getMillis() / JITTER_MS) * JITTER_MHZ;
            mhz = Math.max(-PointerScale.RANGE_MHZ, Math.min(PointerScale.RANGE_MHZ, mhz));
        }
        int nx = x + PointerScale.x(w, mhz);
        g.fill(nx - 1, y + 1, nx + 1, y + BAND_H - 1, signal ? GuiPaint.ACCENT : GuiPaint.DISABLED);
        if (!signal) {
            GuiText.draw(g, font, ResonatorText.key("pointer.no_signal"), x + 4, y + 3, w - 8, GuiPaint.MUTED);
        }
    }

    /** A bolinha de 7 px com o anel e o texto: acesa no platô. */
    private void lamp(GuiGraphics g, Font font, ResonatorStatePayload state) {
        int x = left + LAMP.x();
        int y = top + LAMP.y();
        boolean on = state != null && state.tuned();
        int color = on ? GuiPaint.OK : GuiPaint.DISABLED;
        if (on) {
            disc(g, x - 1, y - 1, 9, GuiPaint.alpha(GuiPaint.OK, 64));
        }
        disc(g, x, y, 7, GuiPaint.RING);
        disc(g, x + 1, y + 1, 5, color);
        GuiText.draw(g, font, ResonatorText.key("lamp"), x + 11, y, LAMP.w() - 11, on ? GuiPaint.OK : GuiPaint.MUTED);
    }

    /** Um disco de pixel art, uma linha por vez (sem sobrepor: o halo é translúcido). */
    private static void disc(GuiGraphics g, int x, int y, int size, int color) {
        double radius = size / 2.0;
        for (int row = 0; row < size; row++) {
            double dy = row + 0.5 - radius;
            double half = Math.sqrt(Math.max(0, radius * radius - dy * dy));
            int inset = (int) Math.round(radius - half);
            g.fill(x + inset, y + row, x + size - inset, y + row + 1, color);
        }
    }

    /** O rótulo de um MHz inteiro da escala: {@code 0}, {@code +1}, {@code −2}. */
    private static String label(int mhz) {
        return mhz == 0 ? "0" : (mhz > 0 ? "+" : "−") + Math.abs(mhz);
    }
}
