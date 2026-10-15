package io.github.matheusanbs.wirelessautomate.client;

import io.github.matheusanbs.wirelessautomate.GuiTheme;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

/**
 * O botão do tema, 11 × 11 px no canto direito do cabeçalho de cada tela do mod: uma lua no claro
 * (clicar escurece), um sol no escuro. Desenhado por {@code fill}, sem textura nem caractere de fonte.
 * Cada tela chama {@link #render}, {@link #mouseClicked} e {@link #tooltip} na mesma posição.
 */
public final class ThemeToggle {
    public static final int SIZE = 11;

    private ThemeToggle() {
    }

    public static boolean hovered(int x, int y, double mouseX, double mouseY) {
        return mouseX >= x && mouseX < x + SIZE && mouseY >= y && mouseY < y + SIZE;
    }

    public static void render(GuiGraphics g, int x, int y, int mouseX, int mouseY) {
        boolean hover = hovered(x, y, mouseX, mouseY);
        GuiPaint.box(g, x, y, SIZE, SIZE, GuiPaint.BUTTON, hover ? GuiPaint.BUTTON_HOVER_BORDER : GuiPaint.BUTTON_BORDER);
        if (GuiThemes.current() == GuiTheme.LIGHT) {
            moon(g, x + 2, y + 2, GuiPaint.FG);
        } else {
            sun(g, x + 2, y + 2, GuiPaint.FG);
        }
    }

    /** Clique esquerdo no botão: troca o tema, com o som de botão. Devolve se o clique foi dele. */
    public static boolean mouseClicked(int x, int y, double mouseX, double mouseY, int button) {
        if (button != 0 || !hovered(x, y, mouseX, mouseY)) {
            return false;
        }
        GuiThemes.toggle();
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1f));
        return true;
    }

    public static List<Component> tooltip() {
        String key = GuiThemes.current() == GuiTheme.LIGHT ? "dark" : "light";
        return List.of(Component.translatable("gui.wirelessautomate.theme." + key),
                Component.translatable("gui.wirelessautomate.theme.hint").withColor(GuiPaint.TOOLTIP_MUTED));
    }

    /** Lua crescente inclinada com uma estrela, 7 × 7 px. */
    private static void moon(GuiGraphics g, int x, int y, int color) {
        pattern(g, x, y, color,
                "..X....",
                ".XX..X.",
                "XX.....",
                "XX.....",
                "XXX....",
                ".XXX...",
                "..XXX..");
    }

    /** Sol: miolo 3 × 3, quatro raios e quatro diagonais, 7 × 7 px. */
    private static void sun(GuiGraphics g, int x, int y, int color) {
        pattern(g, x, y, color,
                "...X...",
                ".X...X.",
                "..XXX..",
                "X.XXX.X",
                "..XXX..",
                ".X...X.",
                "...X...");
    }

    /** Pinta um pixel por {@code X}, uma linha de texto por linha da tela. */
    private static void pattern(GuiGraphics g, int x, int y, int color, String... rows) {
        for (int row = 0; row < rows.length; row++) {
            for (int col = 0; col < rows[row].length(); col++) {
                if (rows[row].charAt(col) == 'X') {
                    g.fill(x + col, y + row, x + col + 1, y + row + 1, color);
                }
            }
        }
    }
}
