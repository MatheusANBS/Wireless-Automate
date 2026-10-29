package io.github.matheusanbs.wirelessautomate.client.tv.editor;

import io.github.matheusanbs.wirelessautomate.client.GuiPaint;
import io.github.matheusanbs.wirelessautomate.client.GuiText;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * A faixa de abas da coluna da esquerda: Peças, Componentes, Camadas e Pixel art. A aba aberta mostra o símbolo e o
 * nome e as outras só o símbolo (com o nome na dica), para as quatro caberem na coluna; quando o nome não cabe ao lado
 * do símbolo, a aberta mostra só o nome e, se nem ele cabe, só o símbolo (com a dica), em vez de cortar. A aba aberta
 * é a única que desenha e recebe o mouse. Medida no {@link #layout} e de novo ao trocar de aba, nunca por quadro.
 */
final class TvLeftTabs {
    /** As abas da coluna. */
    enum Tab { PIECES, COMPONENTS, LAYERS, ARTS }

    private static final String[] NAMES = {"pieces", "components", "layers.tab", "arts.tab"};
    private static final String[] TIPS = {"pieces", "components", "layers", "arts.tab"};
    private static final TvIcons.Icon[] ICONS = {TvIcons.Icon.PIECES, TvIcons.Icon.COMPONENTS, TvIcons.Icon.LAYERS,
        TvIcons.Icon.PIXEL_ART};
    /** Como a aba aberta mostra o nome. */
    private enum Show { ICON_AND_NAME, NAME, ICON }
    private static final Tab[] TABS = Tab.values();
    /** A aba fechada: o ícone de 16 px tem 2 px de folga de cada lado, então cabe em 14. */
    private static final int SMALL_W = 13;
    /** O que o ícone ocupa de fato (a parte desenhada) mais 1 px até o nome. */
    private static final int ICON_W = TvIcons.SIZE + 2;
    private static final int GAP = 1;

    private final TvRect[] rects = new TvRect[TABS.length];
    private final GuiText.Fitted[] names = new GuiText.Fitted[TABS.length];
    private final int[] nameWidths = new int[TABS.length];
    private final Show[] shows = new Show[TABS.length];
    private final Component[] tips = new Component[TABS.length];
    private Font font;
    private TvRect strip = TvRect.NONE;
    private Tab tab = Tab.PIECES;

    void layout(Font font, TvRect strip) {
        this.font = font;
        this.strip = strip;
        int open = openWidth();
        for (Tab each : TABS) {
            int i = each.ordinal();
            Component name = TvEditorText.key(NAMES[i]);
            int width = font.width(name);
            shows[i] = width <= open - ICON_W - 4 ? Show.ICON_AND_NAME : width <= open - 4 ? Show.NAME : Show.ICON;
            names[i] = GuiText.fit(font, name, open - 4);
            nameWidths[i] = width;
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

    /** A largura da aba aberta: a faixa menos as fechadas. */
    private int openWidth() {
        return strip.w() - (TABS.length - 1) * (SMALL_W + GAP);
    }

    private void place() {
        int x = strip.x();
        for (Tab each : TABS) {
            int w = each == tab ? openWidth() : SMALL_W;
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
            if (active && shows[i] == Show.ICON_AND_NAME) {
                int left = r.x() + (r.w() - ICON_W - nameWidths[i]) / 2;
                TvIcons.draw(g, ICONS[i], left, r.y() + (r.h() - TvIcons.SIZE) / 2);
                GuiText.draw(g, font, names[i], left + ICON_W, r.y() + (r.h() - 8) / 2, color);
            } else if (active && shows[i] == Show.NAME) {
                GuiText.draw(g, font, names[i], r.x() + (r.w() - nameWidths[i]) / 2, r.y() + (r.h() - 8) / 2, color);
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
        return hovered != null && (hovered != tab || shows[hovered.ordinal()] == Show.ICON) ? tips[hovered.ordinal()]
                : null;
    }

    int[] center(Tab wanted) {
        return rects[wanted.ordinal()].center();
    }
}
