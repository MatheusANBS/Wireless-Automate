package io.github.matheusanbs.wirelessautomate.client.resonator;

import io.github.matheusanbs.wirelessautomate.resonator.ProjectionDiff;
import io.github.matheusanbs.wirelessautomate.resonator.ResonatorBlueprint.Layout;
import io.github.matheusanbs.wirelessautomate.resonator.ResonatorLayout.Part;
import java.util.Locale;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/** Os textos da projeção na action bar: o que falta por peça, os blocos errados, a dica e o layout. */
final class ProjectionText {
    private static final String KEY = "block.wirelessautomate.resonance_core.projection.";
    static final String HINT = KEY + "hint";
    private static final String SEPARATOR = " · ";
    /** A ordem da contagem na action bar. */
    private static final Part[] ORDER = {Part.CASING, Part.GLASS, Part.ENERGY_PORT, Part.CRYSTAL_PORT, Part.TUNER};

    private ProjectionText() {
    }

    /** "Faltam 42 Carcaças · 9 Vidros · 1 bloco errado": só os tipos que faltam, com o plural de cada um. */
    static Component status(ProjectionDiff.Result result) {
        MutableComponent list = Component.empty();
        int total = 0;
        for (Part part : ORDER) {
            int count = result.missing().getOrDefault(part, 0);
            if (count > 0) {
                if (total > 0) {
                    list.append(SEPARATOR);
                }
                list.append(Component.translatable(KEY + "part." + part.name().toLowerCase(Locale.ROOT) + plural(count),
                        count));
                total += count;
            }
        }
        MutableComponent line = total > 0 ? Component.translatable(KEY + "missing" + plural(total), list)
                : Component.empty();
        if (result.wrong() > 0) {
            if (total > 0) {
                line.append(SEPARATOR);
            }
            line.append(Component.translatable(KEY + "wrong" + plural(result.wrong()), result.wrong()));
        } else if (total == 0) {
            line = Component.translatable(KEY + "complete");
        }
        return line;
    }

    static Component layout(Layout layout) {
        return Component.translatable(KEY + "layout",
                Component.translatable(KEY + "layout." + layout.name().toLowerCase(Locale.ROOT)));
    }

    private static String plural(int count) {
        return count == 1 ? ".one" : ".many";
    }
}
