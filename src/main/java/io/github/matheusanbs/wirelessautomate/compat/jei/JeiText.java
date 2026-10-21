package io.github.matheusanbs.wirelessautomate.compat.jei;

import java.text.DecimalFormatSymbols;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

/** Os números e o texto centrado das categorias do Ressonador no JEI. */
final class JeiText {
    static final int COLOR = 0xFF404040;

    private JeiText() {
    }

    /**
     * {@code value} sem casas quando inteiro, senão com uma casa e o separador decimal do idioma do jogo (não o
     * {@code Locale} do sistema), como o dial da tela do Núcleo.
     */
    static String decimal(double value) {
        if (value == Math.rint(value)) {
            return Long.toString((long) value);
        }
        String code = Minecraft.getInstance().getLanguageManager().getSelected();
        char separator = DecimalFormatSymbols.getInstance(Locale.forLanguageTag(code.replace('_', '-')))
                .getDecimalSeparator();
        return String.format(Locale.ROOT, "%.1f", value).replace('.', separator);
    }

    /** O texto quebrado na largura {@code width}, para guardar e desenhar sem quebrar de novo a cada quadro. */
    static List<FormattedCharSequence> split(Component text, int width) {
        return Minecraft.getInstance().font.split(text, width);
    }

    /** As linhas centradas em {@code width} a partir de {@code y}; devolve o y depois da última. */
    static int drawCentered(GuiGraphics graphics, List<FormattedCharSequence> lines, int width, int y) {
        Font font = Minecraft.getInstance().font;
        for (FormattedCharSequence line : lines) {
            graphics.drawString(font, line, (width - font.width(line)) / 2, y, COLOR, false);
            y += font.lineHeight + 1;
        }
        return y;
    }

    /** O idioma do jogo, para as categorias refazerem o texto guardado quando ele muda. */
    static String language() {
        return Minecraft.getInstance().getLanguageManager().getSelected();
    }
}
