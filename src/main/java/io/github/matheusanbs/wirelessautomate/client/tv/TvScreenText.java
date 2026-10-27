package io.github.matheusanbs.wirelessautomate.client.tv;

import io.github.matheusanbs.wirelessautomate.client.GuiPaint;
import io.github.matheusanbs.wirelessautomate.client.GuiText;
import io.github.matheusanbs.wirelessautomate.tv.TvArea;
import io.github.matheusanbs.wirelessautomate.tv.TvAreaCheck;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.jetbrains.annotations.Nullable;

/**
 * Os textos da tela da Tela de Sinal: as chaves do {@code lang}, a linha da área (livre ou o problema, com as mesmas
 * mensagens que o servidor manda na barra de ação) e a quebra pronta dos textos de duas linhas, medida uma vez.
 */
final class TvScreenText {
    private static final String PREFIX = "gui.wirelessautomate.tv.";

    private TvScreenText() {
    }

    static Component key(String key, Object... args) {
        return Component.translatable(PREFIX + key, args);
    }

    static Component growName(TvArea.Grow grow) {
        return key(switch (grow) {
            case UP_RIGHT -> "grow.up_right";
            case UP_LEFT -> "grow.up_left";
            case CENTER -> "grow.center";
        });
    }

    /** "Área livre: N blocos" sem problema; senão a mensagem do problema, com as coordenadas da célula. */
    static Component areaLine(TvArea area, TvAreaCheck.Problem problem, int maxWidth, int maxHeight) {
        if (problem.ok()) {
            return key("free", area.cells());
        }
        String key = "message.wirelessautomate.tv.problem." + switch (problem.kind()) {
            case TOO_BIG -> "too_big";
            case NO_WALL -> "no_wall";
            case OVERLAP -> "overlap";
            default -> "blocked";
        };
        BlockPos at = problem.pos();
        return at == null ? Component.translatable(key, maxWidth, maxHeight)
                : Component.translatable(key, at.getX() + " " + at.getY() + " " + at.getZ());
    }

    /** Um texto de até algumas linhas, quebrado uma vez; se não cabe, a primeira linha abreviada com a dica. */
    record Wrapped(@Nullable List<FormattedCharSequence> lines, GuiText.Fitted fallback) {
        void draw(GuiGraphics g, Font font, int x, int y, int color) {
            if (lines == null) {
                GuiText.draw(g, font, fallback, x, y, color);
                return;
            }
            for (int i = 0; i < lines.size(); i++) {
                GuiPaint.text(g, font, lines.get(i), x, y + i * 10, color);
            }
        }
    }

    static Wrapped wrap(Font font, Component text, int width, int maxLines) {
        List<FormattedCharSequence> lines = font.split(text, width);
        return new Wrapped(lines.size() <= maxLines ? lines : null, GuiText.fit(font, text, width));
    }
}
