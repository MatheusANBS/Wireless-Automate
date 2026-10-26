package io.github.matheusanbs.wirelessautomate.client.repairer;

import static io.github.matheusanbs.wirelessautomate.client.repairer.RepairerLayout.CHAMBER;

import io.github.matheusanbs.wirelessautomate.client.GuiPaint;
import io.github.matheusanbs.wirelessautomate.client.GuiText;
import io.github.matheusanbs.wirelessautomate.client.repairer.render.RepairTimeline;
import io.github.matheusanbs.wirelessautomate.menu.RepairerMenu;
import io.github.matheusanbs.wirelessautomate.packet.RepairerStatePayload;
import io.github.matheusanbs.wirelessautomate.repairer.RepairMath;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;

/**
 * A câmara do Reparador: o vidro escuro em gradiente, o anel de runas no chão, o
 * núcleo de forja no meio e os 8 pedestais em anel, cada um com o ícone do cristal da linha e a barra do progresso.
 * Cada linha que repara liga o núcleo ao cristal dela por um tentáculo ondulante com um pacote de luz correndo (a
 * força vem da {@link RepairTimeline#beam}, pelo progresso do servidor), e o núcleo pulsa com o relógio do mundo. Sem
 * energia para o próximo reparo, um véu escurece a câmara com o aviso. A geometria é fixa e calculada uma vez.
 */
final class RepairerChamberPanel {
    private static final int CX = CHAMBER.x() + CHAMBER.w() / 2;
    private static final int FLOOR_Y = CHAMBER.y() + 80;
    private static final int ORB_Y = CHAMBER.y() + 46;
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
        boolean working = working(gameTime);
        orb(g, powerless, working, gameTime);
        for (int k = 0; k < RepairMath.LINES; k++) {
            tendril(g, BACK_TO_FRONT[k], gameTime, powerless);
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

    /** Se alguma linha está reparando. */
    private boolean working(long gameTime) {
        RepairerStatePayload state = menu.state();
        if (state == null) {
            return false;
        }
        for (int line = 0; line < RepairMath.LINES; line++) {
            if (state.ends()[line] > 0 && !state.blocked()[line]) {
                return true;
            }
        }
        return false;
    }

    /** O núcleo de forja: discos concêntricos, coral com energia e lilás apagado sem; pulsa devagar e mais forte trabalhando. */
    private void orb(GuiGraphics g, boolean powerless, boolean working, long gameTime) {
        int glow = powerless ? GuiPaint.CHAMBER_GLOW_OFF : GuiPaint.CHAMBER_GLOW;
        double pulse = 0.5 + 0.5 * Math.sin(gameTime * (working ? 0.25 : 0.06));
        int swell = powerless ? 0 : (int) Math.round(pulse * (working ? 2 : 1));
        disc(g, 15 + swell, GuiPaint.alpha(glow, 0x22));
        disc(g, 10 + swell, GuiPaint.alpha(glow, 0x48));
        disc(g, 6, GuiPaint.alpha(glow, 0xA0));
        if (!powerless) {
            disc(g, 3, GuiPaint.CHAMBER_CORE);
        }
    }

    /**
     * O tentáculo do núcleo ao cristal da linha que repara: uma curva que ondula (a amplitude é zero nas pontas) com
     * um pacote de luz correndo para o cristal. Só enquanto a {@link RepairTimeline#beam} da linha é maior que zero.
     */
    private void tendril(GuiGraphics g, int line, long gameTime, boolean powerless) {
        RepairerStatePayload state = menu.state();
        if (powerless || state == null || state.ends()[line] <= 0 || state.blocked()[line]) {
            return;
        }
        double s = RepairTimeline.TOTAL * menu.progress(line, gameTime) / 1000.0;
        double strength = RepairTimeline.beam(s);
        if (strength <= 0.02) {
            return;
        }
        int x1 = FOOT_X[line];
        int y1 = FOOT_Y[line] - 12;
        double dx = x1 - CX;
        double dy = y1 - ORB_Y;
        double len = Math.max(1, Math.sqrt(dx * dx + dy * dy));
        double nx = -dy / len;
        double ny = dx / len;
        double time = gameTime * 0.18 + line * 1.7;
        int body = GuiPaint.alpha(GuiPaint.CHAMBER_GLOW, (int) Math.round(0xC0 * strength));
        double packet = (gameTime * 0.045 + line * 0.31) % 1.0;
        int steps = (int) Math.ceil(len);
        g.pose().pushPose();
        g.pose().translate(0, 0, 150);
        for (int i = 0; i <= steps; i++) {
            double t = (double) i / steps;
            double wave = Math.sin(t * Math.PI) * (3.5 * Math.sin(t * 9 - time) + 1.5 * Math.sin(t * 17 + time * 1.3));
            int px = (int) Math.round(CX + dx * t + nx * wave);
            int py = (int) Math.round(ORB_Y + dy * t + ny * wave);
            double near = Math.abs(t - packet);
            boolean lit = near < 0.05;
            pixel(g, px, py, lit ? GuiPaint.CHAMBER_CORE : body);
            if (lit) {
                pixel(g, px + 1, py, GuiPaint.alpha(GuiPaint.CHAMBER_GLOW, 0x80));
                pixel(g, px, py + 1, GuiPaint.alpha(GuiPaint.CHAMBER_GLOW, 0x80));
            }
        }
        g.pose().popPose();
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
