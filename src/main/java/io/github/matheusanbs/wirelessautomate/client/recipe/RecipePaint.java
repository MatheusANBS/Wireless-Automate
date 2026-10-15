package io.github.matheusanbs.wirelessautomate.client.recipe;

import io.github.matheusanbs.wirelessautomate.client.FlatButton;
import io.github.matheusanbs.wirelessautomate.client.GuiPaint;
import io.github.matheusanbs.wirelessautomate.recipe.edit.RecipeEditor;
import io.github.matheusanbs.wirelessautomate.registry.ModItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/** O desenho dos botões do editor de receitas e das marcas em pixel art da grade (cadeado, tag, seta). */
final class RecipePaint {
    private RecipePaint() {
    }

    static int stateColor(RecipeEditor.State state) {
        return switch (state) {
            case DEFAULT -> GuiPaint.OK;
            case EDITED -> GuiPaint.ACCENT;
            case DISABLED -> GuiPaint.DISABLED;
        };
    }

    /** Uma camada translúcida por cima de um item (desenhado mais perto da tela que ele). */
    static void overlay(GuiGraphics g, int x, int y, int w, int h, int color, int alpha) {
        g.pose().pushPose();
        g.pose().translate(0, 0, 200);
        g.fill(x, y, x + w, y + h, GuiPaint.alpha(color, alpha));
        g.pose().popPose();
    }

    /**
     * O centro travado do anel: o slot afundado com um roteador apagado (o que vai no centro) e um cadeado
     * de 5×6 px no canto.
     */
    static void lockedCenter(GuiGraphics g, int x, int y, boolean off) {
        g.fill(x, y, x + 18, y + 18, GuiPaint.BEVEL_DARK);
        g.fill(x + 1, y + 1, x + 18, y + 18, GuiPaint.INSET);
        g.renderItem(new ItemStack(ModItems.ROUTER.get()), x + 1, y + 1);
        overlay(g, x + 1, y + 1, 16, 16, GuiPaint.INSET, off ? 0xC0 : 0x90);
        g.pose().pushPose();
        g.pose().translate(0, 0, 210);
        int c = off ? GuiPaint.DISABLED : GuiPaint.MUTED;
        int lx = x + 12;
        int ly = y + 11;
        g.fill(lx + 1, ly, lx + 4, ly + 1, c);
        g.fill(lx, ly + 1, lx + 1, ly + 3, c);
        g.fill(lx + 4, ly + 1, lx + 5, ly + 3, c);
        g.fill(lx, ly + 3, lx + 5, ly + 6, c);
        g.pose().popPose();
    }

    /** O {@code #} coral no canto de um slot de tag: 7×7 px, em pixel art. */
    static void tagMark(GuiGraphics g, int x, int y, boolean off) {
        g.pose().pushPose();
        g.pose().translate(0, 0, 210);
        g.fill(x, y, x + 7, y + 7, off ? GuiPaint.DISABLED : GuiPaint.ACCENT);
        int c = GuiPaint.BEVEL_LIGHT;
        g.fill(x + 2, y + 1, x + 3, y + 6, c);
        g.fill(x + 4, y + 1, x + 5, y + 6, c);
        g.fill(x + 1, y + 2, x + 6, y + 3, c);
        g.fill(x + 1, y + 4, x + 6, y + 5, c);
        g.pose().popPose();
    }

    /** Seta de 20×13 px para a direita (haste de 3 px, cabeça de 11 px). */
    static void arrow(GuiGraphics g, int x, int y, int color) {
        g.fill(x + 1, y + 5, x + 13, y + 8, color);
        for (int i = 0; i < 6; i++) {
            g.fill(x + 13 + i, y + 1 + i, x + 14 + i, y + 12 - i, color);
        }
    }

    static void paintButton(GuiGraphics g, FlatButton b, boolean hovered, Component text, boolean primary) {
        int fill;
        int border;
        int ink;
        if (!b.active) {
            fill = GuiPaint.PANEL;
            border = GuiPaint.DISABLED;
            ink = GuiPaint.DISABLED;
        } else if (primary) {
            fill = GuiPaint.ACCENT;
            border = hovered ? GuiPaint.FG : GuiPaint.ACCENT_DEEP;
            ink = GuiPaint.BEVEL_LIGHT;
        } else {
            fill = GuiPaint.BUTTON;
            border = hovered ? GuiPaint.BUTTON_HOVER_BORDER : GuiPaint.BUTTON_BORDER;
            ink = GuiPaint.FG;
        }
        GuiPaint.box(g, b.getX(), b.getY(), b.getWidth(), b.getHeight(), fill, border);
        GuiPaint.textCentered(g, Minecraft.getInstance().font, text, b.getX() + b.getWidth() / 2 + 1,
                b.getY() + (b.getHeight() - 8) / 2, ink);
    }

    /** Item ou Tag: escolhido (grafite), disponível ou apagado. */
    static void paintChoice(GuiGraphics g, FlatButton b, boolean hovered, Component text, boolean on) {
        if (on) {
            GuiPaint.box(g, b.getX(), b.getY(), b.getWidth(), b.getHeight(), GuiPaint.SELECTED, GuiPaint.SELECTED);
            GuiPaint.textCentered(g, Minecraft.getInstance().font, text, b.getX() + b.getWidth() / 2 + 1,
                    b.getY() + (b.getHeight() - 8) / 2, GuiPaint.SELECTED_TEXT);
            return;
        }
        paintButton(g, b, hovered, text, false);
    }

    static void paintArrow(GuiGraphics g, FlatButton b, boolean hovered, boolean right) {
        GuiPaint.box(g, b.getX(), b.getY(), b.getWidth(), b.getHeight(), GuiPaint.BUTTON,
                hovered ? GuiPaint.BUTTON_HOVER_BORDER : GuiPaint.BUTTON_BORDER);
        int ax = b.getX() + (b.getWidth() - 3) / 2;
        int ay = b.getY() + (b.getHeight() - 5) / 2;
        if (right) {
            GuiPaint.arrowRight(g, ax, ay, GuiPaint.FG);
        } else {
            g.fill(ax + 2, ay, ax + 3, ay + 5, GuiPaint.FG);
            g.fill(ax + 1, ay + 1, ax + 2, ay + 4, GuiPaint.FG);
            g.fill(ax, ay + 2, ax + 1, ay + 3, GuiPaint.FG);
        }
    }
}
