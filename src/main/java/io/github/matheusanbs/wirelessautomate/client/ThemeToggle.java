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
            moon(g, x + 3, y + 3, GuiPaint.FG);
        } else {
            sun(g, x + 3, y + 3, GuiPaint.FG);
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

    /** Lua crescente, 5 × 5 px. */
    private static void moon(GuiGraphics g, int x, int y, int color) {
        g.fill(x + 1, y, x + 3, y + 1, color);
        g.fill(x, y + 1, x + 2, y + 4, color);
        g.fill(x + 1, y + 4, x + 3, y + 5, color);
        g.fill(x + 3, y + 3, x + 5, y + 4, color);
        g.fill(x + 3, y + 4, x + 4, y + 5, color);
    }

    /** Sol: miolo 3 × 3 e quatro raios, 5 × 5 px. */
    private static void sun(GuiGraphics g, int x, int y, int color) {
        g.fill(x + 1, y + 1, x + 4, y + 4, color);
        g.fill(x + 2, y, x + 3, y + 1, color);
        g.fill(x + 2, y + 4, x + 3, y + 5, color);
        g.fill(x, y + 2, x + 1, y + 3, color);
        g.fill(x + 4, y + 2, x + 5, y + 3, color);
    }
}
