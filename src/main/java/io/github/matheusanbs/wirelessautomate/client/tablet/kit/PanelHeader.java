package io.github.matheusanbs.wirelessautomate.client.tablet.kit;

import io.github.matheusanbs.wirelessautomate.client.GuiPaint;
import io.github.matheusanbs.wirelessautomate.client.GuiText;
import io.github.matheusanbs.wirelessautomate.client.PanelPaint;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * A faixa de cima do painel direito (mockup {@code cabecalho}): título em negrito, subtítulo, o estado com o ponto à
 * direita e a linha embaixo. Tudo medido no {@link #set} (quando chega o snapshot); o {@link #render} só desenha. O
 * ícone ou o slot quem desenha é a aba, porque muda entre rede e grupo.
 */
public final class PanelHeader {
    private GuiText.Fitted title;
    /** A largura do título como aparece (o {@code Fitted} guarda o espaço, não o texto). */
    private int titleW;
    private GuiText.Fitted subtitle;
    private @Nullable Component state;
    private int stateW;
    private int stateColor;
    private boolean editable;

    public void set(Font font, Component title, Component subtitle, @Nullable Component state, int stateColor,
            boolean editable, int maxTitleW) {
        this.title = GuiText.fit(font, title.copy().withStyle(ChatFormatting.BOLD), maxTitleW);
        this.titleW = font.width(this.title.shown());
        this.subtitle = GuiText.fit(font, subtitle, maxTitleW);
        this.state = state;
        this.stateW = state == null ? 0 : font.width(state);
        this.stateColor = stateColor;
        this.editable = editable;
    }

    /** {@code left} e {@code top} são o canto da tela; {@code x0} e {@code x1} as bordas do painel, como no mockup. */
    public void render(GuiGraphics g, Font font, int left, int top, int x0, int x1) {
        if (title == null) {
            return;
        }
        int tx = left + x0 + 21;
        GuiText.draw(g, font, title, tx, top + 30, GuiPaint.FG);
        if (editable) {
            PanelPaint.pencil(g, tx + titleW + 4, top + 31, GuiPaint.MUTED);
        }
        GuiText.draw(g, font, subtitle, tx, top + 40, GuiPaint.MUTED);
        if (state != null) {
            PanelPaint.square(g, left + x1 - stateW - 7, top + 32, stateColor);
            GuiPaint.text(g, font, state, left + x1 - stateW, top + 30, GuiPaint.MUTED);
        }
        PanelPaint.rule(g, left + x0, top + 53, x1 - x0 + 1);
    }

    /** Se o mouse está no título (e no lápis), a área que vira o campo de renomear. */
    public boolean overTitle(double mx, double my, int left, int top, int x0) {
        if (title == null) {
            return false;
        }
        int tx = left + x0 + 21;
        return mx >= tx && mx < tx + titleW + (editable ? 12 : 0) && my >= top + 29 && my < top + 40;
    }
}
