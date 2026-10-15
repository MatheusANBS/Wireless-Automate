package io.github.matheusanbs.wirelessautomate.client.resonator.screen;

import static io.github.matheusanbs.wirelessautomate.client.resonator.screen.CoreScreenLayout.AUTO_W;
import static io.github.matheusanbs.wirelessautomate.client.resonator.screen.CoreScreenLayout.AUTO_X;
import static io.github.matheusanbs.wirelessautomate.client.resonator.screen.CoreScreenLayout.BUTTON;
import static io.github.matheusanbs.wirelessautomate.client.resonator.screen.CoreScreenLayout.DIAL;
import static io.github.matheusanbs.wirelessautomate.client.resonator.screen.CoreScreenLayout.DIAL_BUTTONS;
import static io.github.matheusanbs.wirelessautomate.client.resonator.screen.CoreScreenLayout.FIELD_W;
import static io.github.matheusanbs.wirelessautomate.client.resonator.screen.CoreScreenLayout.FIELD_X;
import static io.github.matheusanbs.wirelessautomate.client.resonator.screen.CoreScreenLayout.MANUAL_W;
import static io.github.matheusanbs.wirelessautomate.client.resonator.screen.CoreScreenLayout.MODE;

import io.github.matheusanbs.wirelessautomate.client.GuiPaint;
import io.github.matheusanbs.wirelessautomate.client.GuiText;
import io.github.matheusanbs.wirelessautomate.menu.ResonatorMenu;
import io.github.matheusanbs.wirelessautomate.packet.ResonatorStatePayload;
import io.github.matheusanbs.wirelessautomate.resonator.Tuning;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import org.jetbrains.annotations.Nullable;

/**
 * O dial (« ‹ campo › ») e a linha do modo (Manual e Sintonizador). Os botões do dial e a roda sobre o campo só
 * valem no Manual; o Sintonizador só com a peça na estrutura. O valor mostrado é sempre o do servidor.
 */
final class DialRow {
    private static final String[] GLYPHS = {"«", "‹", "›", "»"};
    private static final double[] DELTAS = {-1, -Tuning.DIAL_STEP, Tuning.DIAL_STEP, 1};
    private static final String[] TIPS = {"dial.minus_one", "dial.minus_step", "dial.plus_step", "dial.plus_one"};

    private final ResonatorMenu menu;
    private final ResonatorScreenActions actions;
    private int left;
    private int top;
    /**
     * O último dial mandado e o dial do estado na hora: cliques seguidos antes do estado seguinte somam a partir do
     * mandado (só a base do próximo clique; a tela mostra o do servidor).
     */
    private double sentDial = Double.NaN;
    private double sentFrom = Double.NaN;

    DialRow(ResonatorMenu menu, ResonatorScreenActions actions) {
        this.menu = menu;
        this.actions = actions;
    }

    void layout(int x, int y) {
        left = x;
        top = y;
    }

    void render(GuiGraphics g, Font font, int mouseX, int mouseY, float partialTick) {
        ResonatorStatePayload state = menu.state();
        boolean manual = state != null && !state.auto();
        int y = top + DIAL.y();
        for (int i = 0; i < GLYPHS.length; i++) {
            int x = left + DIAL_BUTTONS[i];
            button(g, font, x, y, BUTTON, Component.literal(GLYPHS[i]), false, !manual, hovered(x, y, BUTTON, mouseX, mouseY));
        }
        int fieldX = left + FIELD_X;
        GuiPaint.inset(g, fieldX, y, FIELD_W, BUTTON);
        if (state != null) {
            centered(g, font, ResonatorText.mhz(state.dial()), fieldX, y + 3, FIELD_W,
                    state.auto() ? GuiPaint.MUTED : GuiPaint.FG);
        }
        int modeY = top + MODE.y();
        int manualX = left + MODE.x();
        int autoX = left + AUTO_X;
        boolean auto = state != null && state.auto();
        boolean tuner = state != null && state.tuner();
        button(g, font, manualX, modeY, MANUAL_W, ResonatorText.key("mode.manual"), manual, state == null,
                hovered(manualX, modeY, MANUAL_W, mouseX, mouseY));
        button(g, font, autoX, modeY, AUTO_W, ResonatorText.key("mode.auto"), auto, !tuner,
                hovered(autoX, modeY, AUTO_W, mouseX, mouseY));
    }

    @Nullable List<Component> tooltip(int mouseX, int mouseY) {
        int y = top + DIAL.y();
        for (int i = 0; i < GLYPHS.length; i++) {
            if (hovered(left + DIAL_BUTTONS[i], y, BUTTON, mouseX, mouseY)) {
                return List.of(ResonatorText.key(TIPS[i]));
            }
        }
        if (hovered(left + FIELD_X, y, FIELD_W, mouseX, mouseY)) {
            return ResonatorText.tip(ResonatorText.key("dial.tooltip"), ResonatorText.key("dial.tooltip.wheel"));
        }
        int modeY = top + MODE.y();
        if (hovered(left + MODE.x(), modeY, MANUAL_W, mouseX, mouseY)) {
            return ResonatorText.tip(ResonatorText.key("mode.manual"), ResonatorText.key("mode.manual.tooltip"));
        }
        if (hovered(left + AUTO_X, modeY, AUTO_W, mouseX, mouseY)) {
            ResonatorStatePayload state = menu.state();
            Component title = ResonatorText.key("mode.auto");
            if (state == null) {
                return ResonatorText.tip(title, ResonatorText.key("mode.auto.tooltip"));
            }
            if (!state.tuner()) {
                return ResonatorText.warning(title, ResonatorText.key("mode.auto.missing"));
            }
            return ResonatorText.tip(title, ResonatorText.key("mode.auto.tooltip"), ResonatorText.key("mode.auto.ceiling",
                    Component.translatable(menu.tier().translationKey()), Math.round(state.ceilingPermille() / 10.0)));
        }
        return null;
    }

    boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) {
            return false;
        }
        ResonatorStatePayload state = menu.state();
        int y = top + DIAL.y();
        for (int i = 0; i < GLYPHS.length; i++) {
            if (hovered(left + DIAL_BUTTONS[i], y, BUTTON, mouseX, mouseY)) {
                if (state != null && !state.auto()) {
                    click();
                    turn(state, DELTAS[i]);
                }
                return true;
            }
        }
        int modeY = top + MODE.y();
        if (hovered(left + MODE.x(), modeY, MANUAL_W, mouseX, mouseY)) {
            if (state != null && state.auto()) {
                click();
                actions.setAuto(false);
            }
            return true;
        }
        if (hovered(left + AUTO_X, modeY, AUTO_W, mouseX, mouseY)) {
            if (state != null && state.tuner() && !state.auto()) {
                click();
                actions.setAuto(true);
            }
            return true;
        }
        return false;
    }

    /** A roda sobre o campo gira o dial um passo (só no Manual); sobre o campo, a roda é sempre dele. */
    boolean mouseScrolled(double mouseX, double mouseY, double scrollY) {
        if (!hovered(left + FIELD_X, top + DIAL.y(), FIELD_W, mouseX, mouseY)) {
            return false;
        }
        ResonatorStatePayload state = menu.state();
        if (state != null && !state.auto() && scrollY != 0) {
            turn(state, Math.signum(scrollY) * Tuning.DIAL_STEP);
        }
        return true;
    }

    private void turn(ResonatorStatePayload state, double delta) {
        double base = state.dial() == sentFrom && !Double.isNaN(sentDial) ? sentDial : state.dial();
        double next = Tuning.clampDial(base + delta);
        sentFrom = state.dial();
        sentDial = next;
        actions.setDial(next);
    }

    int[] buttonCenter(int index) {
        return new int[] {left + DIAL_BUTTONS[index] + BUTTON / 2, top + DIAL.y() + BUTTON / 2};
    }

    int[] fieldCenter() {
        return new int[] {left + FIELD_X + FIELD_W / 2, top + DIAL.y() + BUTTON / 2};
    }

    int[] manualCenter() {
        return new int[] {left + MODE.x() + MANUAL_W / 2, top + MODE.y() + BUTTON / 2};
    }

    int[] autoCenter() {
        return new int[] {left + AUTO_X + AUTO_W / 2, top + MODE.y() + BUTTON / 2};
    }

    private static boolean hovered(int x, int y, int w, double mouseX, double mouseY) {
        return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + BUTTON;
    }

    private static void click() {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1f));
    }

    /**
     * Um botão no estilo dos Extrair e Inserir do roteador: o escolhido preenchido com a tinta, o desabilitado
     * apagado sobre o painel, o comum com a borda coral sob o mouse.
     */
    private static void button(GuiGraphics g, Font font, int x, int y, int w, Component text, boolean selected,
            boolean disabled, boolean hover) {
        int textColor;
        if (selected) {
            GuiPaint.box(g, x, y, w, BUTTON, GuiPaint.SELECTED, GuiPaint.SELECTED);
            textColor = GuiPaint.SELECTED_TEXT;
        } else if (disabled) {
            GuiPaint.box(g, x, y, w, BUTTON, GuiPaint.PANEL, GuiPaint.DISABLED);
            textColor = GuiPaint.DISABLED;
        } else {
            GuiPaint.box(g, x, y, w, BUTTON, GuiPaint.BUTTON, hover ? GuiPaint.BUTTON_HOVER_BORDER : GuiPaint.BUTTON_BORDER);
            textColor = GuiPaint.FG;
        }
        centered(g, font, text, x + 1, y + 3, w - 2, textColor);
    }

    /** Centrado em {@code width}; se não couber, cortado pelo {@link GuiText} a partir da esquerda. */
    private static void centered(GuiGraphics g, Font font, Component text, int x, int y, int width, int color) {
        int textW = font.width(text);
        if (textW <= width) {
            GuiPaint.text(g, font, text, x + (width - textW + 1) / 2, y, color);
        } else {
            GuiText.draw(g, font, text, x, y, width, color);
        }
    }
}
