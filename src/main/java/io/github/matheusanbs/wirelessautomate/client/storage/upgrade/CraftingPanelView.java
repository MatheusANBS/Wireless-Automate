package io.github.matheusanbs.wirelessautomate.client.storage.upgrade;

import io.github.matheusanbs.wirelessautomate.client.FlatButton;
import io.github.matheusanbs.wirelessautomate.client.GuiPaint;
import io.github.matheusanbs.wirelessautomate.client.GuiText;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.jetbrains.annotations.Nullable;

/**
 * O painel da Mesa de Trabalho, à direita do Baú: o título, a grade 3×3 e o resultado (slots do menu, que a tela
 * desenha por cima deste fundo), a seta, o botão Devolver e a dica. Os textos são medidos no {@link #layout}; o
 * Devolver chama o anfitrião, que manda o pedido ao servidor.
 */
final class CraftingPanelView {
    static final int WIDTH = 96;
    static final int HEIGHT = 118;
    private static final int PAD = 4;
    private static final int GRID_TOP = 19;
    private static final int RESULT_X = 67;
    private static final int RESULT_SIZE = 26;
    private static final int BUTTON_Y = 77;
    private static final int HINT_Y = 94;
    private static final int HINT_LINES = 2;

    private final Font font;
    private final FlatButton returnButton;
    private int x;
    private int y;
    private GuiText.Fitted title;
    private Component hint = Component.empty();
    private List<FormattedCharSequence> hintLines = List.of();
    private int labelX;

    CraftingPanelView(Font font, Runnable onReturn) {
        this.font = font;
        this.title = GuiText.fit(font, Component.empty(), 0);
        Component label = UpgradePanels.tr("crafting.return");
        this.returnButton = new FlatButton(0, 0, WIDTH - 2 * PAD, 13, label, (g, b, hovered) -> {
            GuiPaint.box(g, b.getX(), b.getY(), b.getWidth(), b.getHeight(), GuiPaint.BUTTON,
                    hovered ? GuiPaint.BUTTON_HOVER_BORDER : GuiPaint.BUTTON_BORDER);
            GuiPaint.text(g, font, label, b.getX() + labelX, b.getY() + 3, GuiPaint.FG);
        }, onReturn).tooltip(() -> UpgradePanels.tr("crafting.return.tooltip"));
    }

    /** Põe o painel com o canto interno em ({@code x}, {@code y}) e mede os textos. */
    void layout(int x, int y) {
        this.x = x;
        this.y = y;
        title = GuiText.fit(font, UpgradePanels.tr("crafting.title"), WIDTH - PAD - 3);
        hint = UpgradePanels.tr("crafting.hint");
        hintLines = font.split(hint, WIDTH - 2 * PAD);
        returnButton.setPosition(x + PAD, y + BUTTON_Y);
        labelX = (returnButton.getWidth() - font.width(returnButton.getMessage())) / 2;
    }

    /** Canto do item do primeiro slot da grade, na tela. */
    int gridItemX() {
        return x + PAD + 1;
    }

    int gridItemY() {
        return y + GRID_TOP + 1;
    }

    int resultItemX() {
        return x + RESULT_X + (RESULT_SIZE - 16) / 2;
    }

    int resultItemY() {
        return resultBoxY() + (RESULT_SIZE - 16) / 2;
    }

    private int resultBoxY() {
        return y + GRID_TOP + (3 * 18 - RESULT_SIZE) / 2;
    }

    void render(GuiGraphics g, int mouseX, int mouseY, int trim) {
        GuiPaint.panel(g, x - 1, y, WIDTH + 1, HEIGHT, trim);
        GuiText.draw(g, font, title, x + PAD, y + 7, GuiPaint.FG);
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                GuiPaint.slot(g, x + PAD + col * 18, y + GRID_TOP + row * 18);
            }
        }
        int arrowY = y + GRID_TOP + 27;
        g.fill(x + 59, arrowY - 1, x + 64, arrowY, GuiPaint.MUTED);
        GuiPaint.arrowRight(g, x + 64, arrowY - 3, GuiPaint.MUTED);
        GuiPaint.inset(g, x + RESULT_X, resultBoxY(), RESULT_SIZE, RESULT_SIZE);
        returnButton.render(g, mouseX, mouseY, 0);
        if (hintLines.size() > HINT_LINES) {
            GuiText.wrap(g, font, hint, x + PAD, y + HINT_Y, WIDTH - 2 * PAD, HINT_LINES, GuiPaint.MUTED);
        } else {
            for (int i = 0; i < hintLines.size(); i++) {
                GuiPaint.text(g, font, hintLines.get(i), x + PAD, y + HINT_Y + i * 10, GuiPaint.MUTED);
            }
        }
    }

    boolean mouseClicked(double mouseX, double mouseY, int button) {
        return returnButton.mouseClicked(mouseX, mouseY, button);
    }

    boolean isInside(double mouseX, double mouseY) {
        return mouseX >= x - 1 && mouseX < x + WIDTH + 2 && mouseY >= y && mouseY < y + HEIGHT + 2;
    }

    /** A dica do botão Devolver sob o mouse, ou {@code null}. */
    @Nullable Component tooltip() {
        return returnButton.isHovered() ? returnButton.currentTooltip() : null;
    }

    /** Centro do botão Devolver (gancho do e2e). */
    int[] returnCenter() {
        return new int[] {returnButton.getX() + returnButton.getWidth() / 2, returnButton.getY() + 6};
    }
}
