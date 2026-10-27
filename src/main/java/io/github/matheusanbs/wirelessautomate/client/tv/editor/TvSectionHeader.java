package io.github.matheusanbs.wirelessautomate.client.tv.editor;

import io.github.matheusanbs.wirelessautomate.client.GuiPaint;
import io.github.matheusanbs.wirelessautomate.client.GuiText;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/** O cabeçalho de uma seção recolhível: a seta (aberta ou recolhida), o título e um filete; o clique vira a seção. */
final class TvSectionHeader extends TvControl {
    static final int HEIGHT = 11;
    private final Font font;
    private final String section;
    private final boolean open;
    private final GuiText.Fitted title;

    TvSectionHeader(Font font, String id, String section, int y, int w, Component title, boolean open) {
        super(id, 0, y, w, HEIGHT);
        this.font = font;
        this.section = section;
        this.open = open;
        this.title = GuiText.fit(font, title, w - 14);
    }

    @Override
    void render(GuiGraphics g, int mouseX, int mouseY) {
        if (contains(mouseX, mouseY)) {
            g.fill(ax, ay, ax + w, ay + h, GuiPaint.ROW_HOVER);
        }
        TvIcons.draw(g, open ? TvIcons.Icon.SECTION_OPEN : TvIcons.Icon.SECTION_CLOSED, ax + 1, ay + 1,
                GuiPaint.MUTED);
        GuiText.draw(g, font, title, ax + 12, ay + 2, GuiPaint.FG);
        g.fill(ax, ay + h - 1, ax + w, ay + h, GuiPaint.LINE);
    }

    @Override
    boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!contains(mouseX, mouseY)) {
            return false;
        }
        TvSections.toggle(section);
        return true;
    }
}
