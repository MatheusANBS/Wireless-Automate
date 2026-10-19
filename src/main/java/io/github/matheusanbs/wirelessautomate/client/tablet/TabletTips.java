package io.github.matheusanbs.wirelessautomate.client.tablet;

import io.github.matheusanbs.wirelessautomate.client.FlatButton;
import io.github.matheusanbs.wirelessautomate.client.GuiText;
import io.github.matheusanbs.wirelessautomate.client.tablet.TabletScreen.Tab;
import java.util.List;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * A dica do quadro na tela do Tablet, fora a do botão do tema (que a tela desenha à parte), nesta ordem: a alça
 * de redimensionar, o botão fixo sob o mouse, a da aba aberta e o texto abreviado sob o mouse, inteiro.
 */
final class TabletTips {
    private final TabletResize resize;
    private final List<FlatButton> buttons;
    private final TabletBody body;

    TabletTips(TabletResize resize, List<FlatButton> buttons, TabletBody body) {
        this.resize = resize;
        this.buttons = buttons;
        this.body = body;
    }

    /** A dica do ponto, ou {@code null} (também sobre um botão fixo sem dica). */
    @Nullable Component at(Tab tab, int mouseX, int mouseY) {
        if (resize.hover(mouseX, mouseY) != null) {
            return Component.translatable("gui.wirelessautomate.resize.tooltip");
        }
        for (FlatButton button : buttons) {
            if (button.visible && button.isHovered()) {
                return button.currentTooltip();
            }
        }
        Component tooltip = body.tooltip(tab, mouseX, mouseY);
        if (tooltip != null) {
            return tooltip;
        }
        // texto abreviado: a dica traz o texto inteiro
        return GuiText.clipAt(mouseX, mouseY);
    }
}
