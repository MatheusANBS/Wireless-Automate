package io.github.matheusanbs.wirelessautomate.client.tv.editor;

import io.github.matheusanbs.wirelessautomate.client.GuiPaint;
import io.github.matheusanbs.wirelessautomate.client.GuiText;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * A faixa de abas da coluna da esquerda: Peças, Componentes e Camadas. A aba aberta mostra o nome e as outras duas só
 * o símbolo (com o nome na dica), para as três caberem na coluna; a aba aberta é a única que desenha e recebe o
 * mouse. Medida no {@link #layout} e de novo ao trocar de aba, nunca por quadro.
 */
final class TvLeftTabs {
    /** As abas da coluna. */
    enum Tab { PIECES, COMPONENTS, LAYERS }

    private static final String[] NAMES = {"pieces", "components", "layers.tab"};
    private static final String[] TIPS = {"pieces", "components", "layers"};
    private static final String[] GLYPHS = {"■", "⊞", "≡"};
    private static final Tab[] TABS = Tab.values();
    private static final int SMALL_W = 16;
    private static final int GAP = 1;

    private final TvRect[] rects = new TvRect[TABS.length];
    private final GuiText.Fitted[] names = new GuiText.Fitted[TABS.length];
    private final int[] nameWidths = new int[TABS.length];
    private final Component[] glyphs = new Component[TABS.length];
    private final int[] glyphWidths = new int[TABS.length];
    private final Component[] tips = new Component[TABS.length];
    private Font font;
    private TvRect strip = TvRect.NONE;
    private Tab tab = Tab.PIECES;

    void layout(Font font, TvRect strip) {
        this.font = font;
        this.strip = strip;
        int open = strip.w() - 2 * SMALL_W - 2 * GAP;
        for (Tab each : TABS) {
            int i = each.ordinal();
            names[i] = GuiText.fit(font, TvEditorText.key(NAMES[i]), open - 6);
            nameWidths[i] = font.width(names[i].shown());
            glyphs[i] = Component.literal(GLYPHS[i]);
            glyphWidths[i] = font.width(glyphs[i]);
            tips[i] = TvEditorText.key(TIPS[i]);
        }
        place();
    }

    Tab tab() {
        return tab;
    }

    void select(Tab chosen) {
        if (chosen != tab) {
            tab = chosen;
            place();
        }
    }

    private void place() {
        int x = strip.x();
        for (Tab each : TABS) {
            int w = each == tab ? strip.w() - 2 * SMALL_W - 2 * GAP : SMALL_W;
            rects[each.ordinal()] = new TvRect(x, strip.y(), w, strip.h());
            x += w + GAP;
        }
    }

    void render(GuiGraphics g, int mouseX, int mouseY) {
        for (Tab each : TABS) {
            int i = each.ordinal();
            TvRect r = rects[i];
            boolean active = each == tab;
            boolean hovered = r.contains(mouseX, mouseY);
            GuiPaint.tab(g, r.x(), r.y(), r.w(), r.h(), active, hovered);
            int color = GuiPaint.tabText(active, hovered);
            if (active) {
                GuiText.draw(g, font, names[i], r.x() + (r.w() - nameWidths[i]) / 2, r.y() + 3, color);
            } else {
                GuiPaint.text(g, font, glyphs[i], r.x() + (r.w() - glyphWidths[i]) / 2, r.y() + 3, color);
            }
        }
    }

    /** A aba sob o mouse, ou {@code null}. */
    @Nullable Tab at(double mouseX, double mouseY) {
        for (Tab each : TABS) {
            if (rects[each.ordinal()].contains(mouseX, mouseY)) {
                return each;
            }
        }
        return null;
    }

    /** O nome da aba que está só com o símbolo, na dica. */
    @Nullable Component tooltip(double mouseX, double mouseY) {
        Tab hovered = at(mouseX, mouseY);
        return hovered != null && hovered != tab ? tips[hovered.ordinal()] : null;
    }

    int[] center(Tab wanted) {
        return rects[wanted.ordinal()].center();
    }
}
