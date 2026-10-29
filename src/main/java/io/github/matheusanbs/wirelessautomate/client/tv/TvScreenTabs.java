package io.github.matheusanbs.wirelessautomate.client.tv;

import static io.github.matheusanbs.wirelessautomate.client.tv.TvScreenLayout.ACCESS_TAB;
import static io.github.matheusanbs.wirelessautomate.client.tv.TvScreenLayout.EDIT_TAB;
import static io.github.matheusanbs.wirelessautomate.client.tv.TvScreenLayout.SIZE_TAB;
import static io.github.matheusanbs.wirelessautomate.client.tv.TvScreenLayout.TITLE;

import io.github.matheusanbs.wirelessautomate.client.GuiPaint;
import io.github.matheusanbs.wirelessautomate.client.GuiText;
import io.github.matheusanbs.wirelessautomate.client.tv.TvButtons.Label;
import io.github.matheusanbs.wirelessautomate.client.tv.TvScreen.Tab;
import io.github.matheusanbs.wirelessautomate.client.tv.TvScreenLayout.Rect;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * O título e as abas do cabeçalho da tela da Tela de Sinal: Tamanho, Editar e, só para o dono e os operadores
 * ({@code access}), Acesso. Os textos são medidos no {@link #layout}, não por quadro.
 */
final class TvScreenTabs {
    private final boolean access;
    private @Nullable GuiText.Fitted title;
    private @Nullable Label sizeLabel;
    private @Nullable Label editLabel;
    private @Nullable Label accessLabel;

    TvScreenTabs(boolean access) {
        this.access = access;
    }

    void layout(Font font, Component text) {
        title = GuiText.fit(font, text, TITLE.w());
        sizeLabel = TvButtons.label(font, TvScreenText.key("tab.size"), SIZE_TAB.w() - 6);
        editLabel = TvButtons.label(font, TvScreenText.key("tab.edit"), EDIT_TAB.w() - 6);
        accessLabel = TvButtons.label(font, TvScreenText.key("tab.access"), ACCESS_TAB.w() - 6);
    }

    static Rect rect(Tab tab) {
        return switch (tab) {
            case SIZE -> SIZE_TAB;
            case EDIT -> EDIT_TAB;
            case ACCESS -> ACCESS_TAB;
        };
    }

    /** A aba sob o mouse, ou {@code null}; a Acesso só existe para quem gerencia. */
    @Nullable Tab at(int left, int top, double mouseX, double mouseY) {
        for (Tab tab : TvScreen.TABS) {
            if ((tab != Tab.ACCESS || access) && rect(tab).contains(left, top, mouseX, mouseY)) {
                return tab;
            }
        }
        return null;
    }

    void render(GuiGraphics g, Font font, int left, int top, Tab current, int mouseX, int mouseY) {
        GuiText.draw(g, font, title, left + TITLE.x(), top + TITLE.y(), GuiPaint.FG);
        renderTab(g, font, left, top, SIZE_TAB, sizeLabel, current == Tab.SIZE, mouseX, mouseY);
        renderTab(g, font, left, top, EDIT_TAB, editLabel, current == Tab.EDIT, mouseX, mouseY);
        if (access) {
            renderTab(g, font, left, top, ACCESS_TAB, accessLabel, current == Tab.ACCESS, mouseX, mouseY);
        }
    }

    private static void renderTab(GuiGraphics g, Font font, int left, int top, Rect r, Label label, boolean active,
            int mouseX, int mouseY) {
        boolean hovered = r.contains(left, top, mouseX, mouseY);
        int x = left + r.x();
        int y = top + r.y();
        GuiPaint.tab(g, x, y, r.w(), r.h(), active, hovered);
        GuiText.draw(g, font, label.text(), x + (r.w() - label.width()) / 2, y + 3, GuiPaint.tabText(active, hovered));
    }
}
