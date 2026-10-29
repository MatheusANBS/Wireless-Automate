package io.github.matheusanbs.wirelessautomate.client.tv.editor;

import io.github.matheusanbs.wirelessautomate.client.GuiPaint;
import io.github.matheusanbs.wirelessautomate.client.tv.pixel.TvPixelSession;
import io.github.matheusanbs.wirelessautomate.client.tv.pixel.TvPixelTool;
import io.github.matheusanbs.wirelessautomate.client.tv.pixel.TvPixelTransform;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * A coluna de ferramentas do editor de pixel art, em duas colunas de botões com ícone: as dez ferramentas, as ajudas
 * (espelho horizontal, vertical e pixel perfect) e a arte inteira (virar e girar; o Limpar tudo é um botão de texto da
 * {@link TvPixelSide}). O retângulo e a elipse mostram o ícone cheio quando estão no modo cheio. O nome e a tecla de
 * cada um ficam na dica.
 */
final class TvPixelToolbar {
    /** Um botão da coluna. */
    enum Button {
        PEN, ERASE, FILL, PICK, LINE, RECT, ELLIPSE, SHADE, DITHER, SELECT,
        MIRROR_H, MIRROR_V, PERFECT,
        FLIP_H, FLIP_V, ROTATE;

        static final Button[] ALL = values();
        static final TvPixelTool[] TOOLS = TvPixelTool.values();

        @Nullable TvPixelTool tool() {
            return ordinal() < TOOLS.length ? TOOLS[ordinal()] : null;
        }
    }

    private static final TvIcons.Icon[] ICONS = {
        TvIcons.Icon.PIXEL_PEN, TvIcons.Icon.PIXEL_ERASE, TvIcons.Icon.PIXEL_FILL, TvIcons.Icon.PIXEL_PICK,
        TvIcons.Icon.PIXEL_LINE, TvIcons.Icon.PIXEL_RECT, TvIcons.Icon.PIXEL_ELLIPSE, TvIcons.Icon.PIXEL_SHADE,
        TvIcons.Icon.PIXEL_DITHER, TvIcons.Icon.PIXEL_SELECT, TvIcons.Icon.PIXEL_MIRROR_H, TvIcons.Icon.PIXEL_MIRROR_V,
        TvIcons.Icon.PIXEL_PERFECT, TvIcons.Icon.PIXEL_FLIP_H, TvIcons.Icon.PIXEL_FLIP_V, TvIcons.Icon.PIXEL_ROTATE};
    /** Os grupos: as ferramentas, as ajudas e a arte inteira começam numa linha nova, com um vão. */
    private static final int HELPERS = Button.MIRROR_H.ordinal();
    private static final int WHOLE = Button.FLIP_H.ordinal();
    private static final int GROUP_GAP = 6;

    private final TvPixelSession session;
    private final TvRect[] rects = new TvRect[Button.ALL.length];
    private final Component[] tips = new Component[Button.ALL.length];

    TvPixelToolbar(TvPixelSession session) {
        this.session = session;
    }

    void layout(TvRect column) {
        int y = column.y();
        int col = 0;
        for (Button button : Button.ALL) {
            int i = button.ordinal();
            if ((i == HELPERS || i == WHOLE) && col != 0) {
                col = 0;
                y += TvPixelLayout.BUTTON + 2;
            }
            if (i == HELPERS || i == WHOLE) {
                y += GROUP_GAP;
            }
            rects[i] = new TvRect(column.x() + col * (TvPixelLayout.BUTTON + 2), y, TvPixelLayout.BUTTON,
                    TvPixelLayout.BUTTON);
            tips[i] = TvEditorText.key("pixel.button." + button.name().toLowerCase(Locale.ROOT));
            col++;
            if (col == 2) {
                col = 0;
                y += TvPixelLayout.BUTTON + 2;
            }
        }
    }

    private boolean on(Button button) {
        TvPixelTool tool = button.tool();
        if (tool != null) {
            return session.tool() == tool;
        }
        return switch (button) {
            case MIRROR_H -> session.mirrorH();
            case MIRROR_V -> session.mirrorV();
            case PERFECT -> session.perfect();
            default -> false;
        };
    }

    private TvIcons.Icon icon(Button button) {
        if (button == Button.RECT && session.rectFilled()) {
            return TvIcons.Icon.PIXEL_RECT_FILLED;
        }
        if (button == Button.ELLIPSE && session.ellipseFilled()) {
            return TvIcons.Icon.PIXEL_ELLIPSE_FILLED;
        }
        return ICONS[button.ordinal()];
    }

    void render(GuiGraphics g, int mouseX, int mouseY) {
        for (Button button : Button.ALL) {
            TvRect r = rects[button.ordinal()];
            boolean hovered = r.contains(mouseX, mouseY);
            boolean lit = on(button);
            GuiPaint.box(g, r.x(), r.y(), r.w(), r.h(), GuiPaint.BUTTON,
                    lit ? GuiPaint.ACCENT : hovered ? GuiPaint.BUTTON_HOVER_BORDER : GuiPaint.BUTTON_BORDER);
            if (lit) {
                g.fill(r.x() + 1, r.y() + 1, r.right() - 1, r.bottom() - 1, GuiPaint.alpha(GuiPaint.ACCENT, 0x40));
            }
            boolean enabled = button != Button.PERFECT || session.brush() == 1;
            TvIcons.draw(g, icon(button), r.x() + 1, r.y() + 1, enabled);
        }
    }

    /** O clique num botão; devolve se foi na coluna. */
    boolean mouseClicked(double mouseX, double mouseY) {
        for (Button button : Button.ALL) {
            if (rects[button.ordinal()].contains(mouseX, mouseY)) {
                press(button);
                return true;
            }
        }
        return false;
    }

    /** Aperta um botão, pelo mouse ou pela tecla. */
    void press(Button button) {
        TvPixelTool tool = button.tool();
        if (tool != null) {
            session.pickTool(tool);
            return;
        }
        switch (button) {
            case MIRROR_H -> session.toggleMirrorH();
            case MIRROR_V -> session.toggleMirrorV();
            case PERFECT -> session.togglePerfect();
            case FLIP_H -> session.transform(TvPixelTransform.FLIP_H);
            case FLIP_V -> session.transform(TvPixelTransform.FLIP_V);
            case ROTATE -> session.transform(TvPixelTransform.ROTATE);
            default -> {
            }
        }
    }

    @Nullable Component tooltip(double mouseX, double mouseY) {
        for (Button button : Button.ALL) {
            if (rects[button.ordinal()].contains(mouseX, mouseY)) {
                return tips[button.ordinal()];
            }
        }
        return null;
    }

    int[] center(Button button) {
        return rects[button.ordinal()].center();
    }
}
