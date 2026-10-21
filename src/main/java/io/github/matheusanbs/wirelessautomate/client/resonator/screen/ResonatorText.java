package io.github.matheusanbs.wirelessautomate.client.resonator.screen;

import io.github.matheusanbs.wirelessautomate.client.GuiPaint;
import io.github.matheusanbs.wirelessautomate.client.RateFormat;
import io.github.matheusanbs.wirelessautomate.resonator.TuneSignal;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/** Os textos da tela do Núcleo: chaves, números e as linhas das dicas. */
final class ResonatorText {
    private static final String PREFIX = "gui.wirelessautomate.resonator.";

    private ResonatorText() {
    }

    static MutableComponent key(String key, Object... args) {
        return Component.translatable(PREFIX + key, args);
    }

    /**
     * O dial com duas casas e o separador decimal do idioma do jogo ({@code 94,00 MHz} em português, {@code 94.00 MHz}
     * em inglês): pelo código do idioma escolhido no jogo, não pelo {@code Locale} do sistema.
     */
    static Component mhz(double mhz) {
        return key("dial.value", decimal(mhz, separator()));
    }

    static String decimal(double value, char separator) {
        return String.format(Locale.ROOT, "%.2f", value).replace('.', separator);
    }

    private static char separator() {
        String code = Minecraft.getInstance().getLanguageManager().getSelected();
        return DecimalFormatSymbols.getInstance(Locale.forLanguageTag(code.replace('_', '-'))).getDecimalSeparator();
    }

    /** Milésimos em porcentagem inteira, arredondada. */
    static String percent(int permille) {
        return Math.round(permille / 10.0) + "%";
    }

    /** Milésimos em porcentagem com no máximo uma casa, sem a casa quando é zero: 150 é "15", 125 é "12,5". */
    static String loss(int permille) {
        if (permille % 10 == 0) {
            return Integer.toString(permille / 10);
        }
        return String.format(Locale.ROOT, "%.1f", permille / 10.0).replace('.', separator());
    }

    static String fe(long value) {
        return RateFormat.abbreviate(value);
    }

    /** O rótulo do osciloscópio: ESTAÇÃO no platô, QUASE LÁ com sinal, CHIADO sem. */
    static Component scope(int steps, boolean tuned) {
        if (steps == TuneSignal.NO_SIGNAL) {
            return key("scope.static");
        }
        return key(tuned ? "scope.station" : "scope.close");
    }

    /** Uma dica: a primeira linha comum, as outras em {@link GuiPaint#TOOLTIP_MUTED}. */
    static List<Component> tip(Component title, Component... rest) {
        List<Component> lines = new ArrayList<>();
        lines.add(title);
        for (Component line : rest) {
            lines.add(muted(line));
        }
        return lines;
    }

    /** Uma dica com a linha de aviso por último, em {@link GuiPaint#TOOLTIP_ACCENT}. */
    static List<Component> warning(Component title, Component warning) {
        List<Component> lines = new ArrayList<>();
        lines.add(title);
        lines.add(accent(warning));
        return lines;
    }

    static Component muted(Component line) {
        return line.copy().withColor(GuiPaint.TOOLTIP_MUTED);
    }

    static Component accent(Component line) {
        return line.copy().withColor(GuiPaint.TOOLTIP_ACCENT);
    }
}
