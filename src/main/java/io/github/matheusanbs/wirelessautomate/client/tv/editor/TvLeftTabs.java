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
    private static final TvIcons.Icon[] ICONS = {TvIcons.Icon.PIECES, TvIcons.Icon.COMPONENTS, TvIcons.Icon.LAYERS};
    private static final Tab[] TABS = Tab.values();
    /** A aba fechada: o ícone de 16 px tem 2 px de folga de cada lado, então cabe em 14. */
    private static final int SMALL_W = 14;
    /** O que o ícone ocupa de fato (a parte desenhada) mais 1 px até o nome. */
    private static final int ICON_W = TvIcons.SIZE - 3;
    private static final int GAP = 1;

    private final TvRect[] rects = new TvRect[TABS.length];
    private final GuiText.Fitted[] names = new GuiText.Fitted[TABS.length];
    private final int[] nameWidths = new int[TABS.length];
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
            names[i] = GuiText.fit(font, TvEditorText.key(NAMES[i]), open - ICON_W - 4);
            nameWidths[i] = font.width(names[i].shown());
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
                int left = r.x() + (r.w() - ICON_W - nameWidths[i]) / 2;
                TvIcons.draw(g, ICONS[i], left - 2, r.y() + (r.h() - TvIcons.SIZE) / 2);
                GuiText.draw(g, font, names[i], left + ICON_W, r.y() + 4, color);
            } else {
                TvIcons.draw(g, ICONS[i], r.x() + (r.w() - TvIcons.SIZE) / 2, r.y() + (r.h() - TvIcons.SIZE) / 2);
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
