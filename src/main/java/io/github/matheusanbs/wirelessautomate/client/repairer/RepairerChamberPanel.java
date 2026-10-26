package io.github.matheusanbs.wirelessautomate.client.repairer;

import static io.github.matheusanbs.wirelessautomate.client.repairer.RepairerLayout.CHAMBER;

import io.github.matheusanbs.wirelessautomate.client.GuiPaint;
import io.github.matheusanbs.wirelessautomate.client.GuiText;
import io.github.matheusanbs.wirelessautomate.menu.RepairerMenu;
import io.github.matheusanbs.wirelessautomate.packet.RepairerStatePayload;
import io.github.matheusanbs.wirelessautomate.repairer.RepairMath;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;

/**
 * A câmara do Reparador, estática (a animada é da etapa 3): o vidro escuro em gradiente, o anel de runas no chão, o
 * núcleo de forja no meio e os 8 pedestais em anel, cada um com o ícone do cristal da linha e a barra do progresso.
 * Sem energia para o próximo reparo, um véu escurece a câmara com o aviso. A geometria é fixa e calculada uma vez.
 */
final class RepairerChamberPanel {
    private static final int CX = CHAMBER.x() + CHAMBER.w() / 2;
    private static final int FLOOR_Y = CHAMBER.y() + 82;
    private static final int ORB_Y = CHAMBER.y() + 48;
    private static final double RX = 71;
    private static final double RY = 32;
    private static final int RING_POINTS = 96;
    /** O anel de runas (contínuo, por fora) e o tracejado (por dentro), ponto a ponto. */
    private static final int[] OUTER_X = new int[RING_POINTS];
    private static final int[] OUTER_Y = new int[RING_POINTS];
    private static final int[] INNER_X = new int[RING_POINTS / 2];
    private static final int[] INNER_Y = new int[RING_POINTS / 2];
    /** O pé de cada pedestal, por linha, e a ordem de desenho de trás para a frente. */
    private static final int[] FOOT_X = new int[RepairMath.LINES];
    private static final int[] FOOT_Y = new int[RepairMath.LINES];
    private static final int[] BACK_TO_FRONT = new int[RepairMath.LINES];
    /** As meias larguras do pedestal, linha a linha de cima para baixo (uma elipse de 19 × 5). */
    private static final int[] PEDESTAL = {5, 8, 9, 8, 5};
    private static final int BAR_W = 16;

    static {
        for (int i = 0; i < RING_POINTS; i++) {
            double a = i * Math.PI * 2 / RING_POINTS;
            OUTER_X[i] = (int) Math.round(CX + RX * 1.12 * Math.cos(a));
            OUTER_Y[i] = (int) Math.round(FLOOR_Y + RY * 1.12 * Math.sin(a));
        }
        for (int i = 0; i < INNER_X.length; i++) {
            double a = i * Math.PI * 4 / RING_POINTS;
            INNER_X[i] = (int) Math.round(CX + RX * 0.62 * Math.cos(a));
            INNER_Y[i] = (int) Math.round(FLOOR_Y + RY * 0.62 * Math.sin(a));
        }
        for (int i = 0; i < RepairMath.LINES; i++) {
            double a = i * Math.PI * 2 / RepairMath.LINES - Math.PI / 2 + Math.PI / RepairMath.LINES;
            FOOT_X[i] = (int) Math.round(CX + RX * Math.cos(a));
            FOOT_Y[i] = (int) Math.round(FLOOR_Y + RY * Math.sin(a));
            BACK_TO_FRONT[i] = i;
        }
        for (int i = 1; i < RepairMath.LINES; i++) {
            for (int j = i; j > 0 && FOOT_Y[BACK_TO_FRONT[j]] < FOOT_Y[BACK_TO_FRONT[j - 1]]; j--) {
                int swap = BACK_TO_FRONT[j];
                BACK_TO_FRONT[j] = BACK_TO_FRONT[j - 1];
                BACK_TO_FRONT[j - 1] = swap;
            }
        }
    }

    private final RepairerMenu menu;
    private int left;
    private int top;

    RepairerChamberPanel(RepairerMenu menu) {
        this.menu = menu;
    }

    void layout(int x, int y) {
        left = x;
        top = y;
    }

    void render(GuiGraphics g, Font font, long gameTime) {
        int x0 = left + CHAMBER.x();
        int y0 = top + CHAMBER.y();
        GuiPaint.inset(g, x0 - 1, y0 - 1, CHAMBER.w() + 2, CHAMBER.h() + 2);
        g.fillGradient(x0, y0, x0 + CHAMBER.w(), y0 + CHAMBER.h(), GuiPaint.CHAMBER_TOP, GuiPaint.CHAMBER_BOTTOM);
        boolean powerless = RepairerText.powerless(menu);
        int rim = powerless ? GuiPaint.CHAMBER_RIM_OFF : GuiPaint.CHAMBER_RIM;
        for (int i = 0; i < RING_POINTS; i++) {
            pixel(g, OUTER_X[i], OUTER_Y[i], rim);
        }
        for (int i = 0; i < INNER_X.length; i += 2) {
            pixel(g, INNER_X[i], INNER_Y[i], GuiPaint.alpha(rim, 0x40));
        }
        orb(g, powerless);
        for (int k = 0; k < RepairMath.LINES; k++) {
            pedestal(g, BACK_TO_FRONT[k], rim, gameTime, powerless);
        }
        if (powerless) {
            g.pose().pushPose();
            g.pose().translate(0, 0, 250);
            g.fill(x0, y0, x0 + CHAMBER.w(), y0 + CHAMBER.h(), GuiPaint.CHAMBER_VEIL);
            GuiText.wrapCentered(g, font, RepairerText.key("chamber.no_energy"), x0 + CHAMBER.w() / 2,
                    y0 + CHAMBER.h() - 12, CHAMBER.w() - 8, 1, GuiPaint.CHAMBER_WARNING);
            g.pose().popPose();
        }
    }

    private void pixel(GuiGraphics g, int x, int y, int color) {
        g.fill(left + x, top + y, left + x + 1, top + y + 1, color);
    }

    /** O núcleo de forja: discos concêntricos, coral com energia e lilás apagado sem. */
    private void orb(GuiGraphics g, boolean powerless) {
        int glow = powerless ? GuiPaint.CHAMBER_GLOW_OFF : GuiPaint.CHAMBER_GLOW;
        disc(g, 15, GuiPaint.alpha(glow, 0x22));
        disc(g, 10, GuiPaint.alpha(glow, 0x48));
        disc(g, 6, GuiPaint.alpha(glow, 0xA0));
        if (!powerless) {
            disc(g, 3, GuiPaint.CHAMBER_CORE);
        }
    }

    private void disc(GuiGraphics g, int r, int color) {
        int cx = left + CX;
        int cy = top + ORB_Y;
        for (int dy = -r; dy <= r; dy++) {
            int half = (int) Math.round(Math.sqrt(r * r - dy * dy));
            g.fill(cx - half, cy + dy, cx + half + 1, cy + dy + 1, color);
        }
    }

    /** O pedestal da linha, o cristal dela em cima (o ícone do item) e a barra do progresso embaixo. */
    private void pedestal(GuiGraphics g, int line, int rim, long gameTime, boolean powerless) {
        int x = left + FOOT_X[line];
        int y = top + FOOT_Y[line];
        for (int row = 0; row < PEDESTAL.length; row++) {
            int half = PEDESTAL[row];
            boolean edge = row == 0 || row == PEDESTAL.length - 1;
            g.fill(x - half, y - 2 + row, x + half + 1, y - 1 + row, edge ? rim : GuiPaint.CHAMBER_SHADE);
            if (!edge) {
                g.fill(x - half, y - 2 + row, x - half + 1, y - 1 + row, rim);
                g.fill(x + half, y - 2 + row, x + half + 1, y - 1 + row, rim);
            }
        }
        ItemStack crystal = menu.getSlot(line).getItem();
        if (crystal.isEmpty()) {
            return;
        }
        g.renderItem(crystal, x - 8, y - 17);
        RepairerStatePayload state = menu.state();
        if (state == null || state.ends()[line] <= 0) {
            return;
        }
        int filled = Math.round(BAR_W * menu.progress(line, gameTime) / 1000f);
        int color = state.blocked()[line] ? GuiPaint.WARN : powerless ? GuiPaint.MUTED : GuiPaint.ACCENT;
        g.pose().pushPose();
        g.pose().translate(0, 0, 200);
        g.fill(x - 8, y + 4, x + 8, y + 6, GuiPaint.CHAMBER_SHADE);
        g.fill(x - 8, y + 4, x - 8 + filled, y + 6, color);
        g.pose().popPose();
    }
}
