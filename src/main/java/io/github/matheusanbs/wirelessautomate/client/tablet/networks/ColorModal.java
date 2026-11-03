package io.github.matheusanbs.wirelessautomate.client.tablet.networks;

import io.github.matheusanbs.wirelessautomate.client.PanelPaint;
import io.github.matheusanbs.wirelessautomate.client.color.ColorLook;
import io.github.matheusanbs.wirelessautomate.client.color.ColorPopup;
import io.github.matheusanbs.wirelessautomate.client.tablet.TabletLayout;
import io.github.matheusanbs.wirelessautomate.client.tablet.kit.TabletModal;
import java.util.function.IntConsumer;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/**
 * O seletor de cor da rede escolhida (mockup {@code popup_cor}): o {@link ColorPopup} comum no visual
 * {@link ColorLook#TABLET}, sem alfa, sem conta-gotas e sem as cores do tema, logo abaixo do slot da cor. Por cima do
 * véu, o slot é redesenhado com a cor que está sendo escolhida. A cor vai ao servidor uma vez, ao fechar, e só se mudou.
 */
final class ColorModal implements TabletModal {
    private static final ColorPopup.Options OPTIONS = new ColorPopup.Options(ColorLook.TABLET, false, false, null, null);

    private final int slotX;
    private final int slotY;
    private final int initial;
    private final IntConsumer commit;
    private final ColorPopup popup;
    private int current;

    ColorModal(Font font, int left, int top, int rgb, IntConsumer commit) {
        this.slotX = left + TabletLayout.PANEL_X0;
        this.slotY = top + 30;
        this.initial = 0xFF000000 | rgb;
        this.current = initial;
        this.commit = commit;
        this.popup = new ColorPopup(font, slotX - 1, top + 50, initial, OPTIONS, value -> current = value);
    }

    /** A cor que o slot e a coluna mostram enquanto a janela está aberta. */
    int current() {
        return current;
    }

    @Override
    public void render(GuiGraphics g, Font font, int mouseX, int mouseY) {
        PanelPaint.slot(g, slotX - 1, slotY - 1, 18, false);
        g.fill(slotX + 1, slotY + 1, slotX + 15, slotY + 15, current);
        popup.render(g, mouseX, mouseY);
    }

    @Override
    public boolean contains(double mouseX, double mouseY) {
        return popup.contains(mouseX, mouseY);
    }

    @Override
    public void mouseClicked(double mouseX, double mouseY, int button) {
        popup.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY) {
        return popup.mouseDragged(mouseX, mouseY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY) {
        return popup.mouseReleased();
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return popup.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char c, int modifiers) {
        return popup.charTyped(c, modifiers);
    }

    @Override
    public void closed() {
        popup.closed();
        if (current != initial) {
            commit.accept(current & 0xFFFFFF);
        }
    }

    ColorPopup popup() {
        return popup;
    }
}
