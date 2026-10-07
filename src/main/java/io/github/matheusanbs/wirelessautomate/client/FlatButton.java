package io.github.matheusanbs.wirelessautomate.client;

import java.util.function.Supplier;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * Botão plano desenhado por quem o cria. O desenho e a dica leem o estado da tela a cada quadro,
 * então um snapshot novo só precisa de {@code refresh()} para posição e visibilidade, nunca de
 * recriar os widgets (e a tela não perde aba nem face selecionadas).
 */
public class FlatButton extends AbstractButton {
    @FunctionalInterface
    public interface Painter {
        void paint(GuiGraphics g, FlatButton button, boolean hovered);
    }

    private final Painter painter;
    private final Runnable action;
    private Supplier<Component> tooltip = () -> null;

    public FlatButton(int x, int y, int width, int height, Component message, Painter painter, Runnable action) {
        super(x, y, width, height, message);
        this.painter = painter;
        this.action = action;
    }

    public FlatButton tooltip(Supplier<Component> tooltip) {
        this.tooltip = tooltip;
        return this;
    }

    /** Dica do quadro atual; a tela a mostra quando o mouse está em cima, mesmo com o botão desativado. */
    public @Nullable Component currentTooltip() {
        return tooltip.get();
    }

    @Override
    public void onPress() {
        action.run();
    }

    @Override
    protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        painter.paint(g, this, active && isHoveredOrFocused());
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }
}
