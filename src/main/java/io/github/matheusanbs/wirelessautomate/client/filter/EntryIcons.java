package io.github.matheusanbs.wirelessautomate.client.filter;

import io.github.matheusanbs.wirelessautomate.client.GuiPaint;
import io.github.matheusanbs.wirelessautomate.filter.FilterEntry;
import io.github.matheusanbs.wirelessautomate.filter.FilterEntry.ChemicalEntry;
import io.github.matheusanbs.wirelessautomate.filter.FilterEntry.FluidEntry;
import io.github.matheusanbs.wirelessautomate.filter.FilterEntry.ItemEntry;
import io.github.matheusanbs.wirelessautomate.filter.FilterEntry.ModEntry;
import io.github.matheusanbs.wirelessautomate.filter.FilterEntry.RuleEntry;
import io.github.matheusanbs.wirelessautomate.filter.FilterEntry.TagEntry;
import java.util.List;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;

/** O ícone de uma entrada: o item, o fluido, o químico, um membro da tag ou do mod com a marca, ou o losango da regra. */
final class EntryIcons {
    /** Roxo da regra por propriedade (a lente do Ultimate). */
    private static final int RULE_COLOR = 0xFF7B4FE0;

    private final FilterScreen screen;
    private final FilterMembers members;

    EntryIcons(FilterScreen screen, FilterMembers members) {
        this.screen = screen;
        this.members = members;
    }

    void renderEntryIcon(GuiGraphics g, FilterEntry entry, int x, int y) {
        switch (entry) {
            case ItemEntry e -> g.renderItem(e.stack(), x, y);
            case FluidEntry e -> GuiPaint.fluid(g, e.stack(), x, y);
            case TagEntry e -> renderMark(g, e, "#", x, y);
            case ModEntry e -> renderMark(g, e, "@", x, y);
            case ChemicalEntry e -> GuiPaint.chemical(g, Minecraft.getInstance().font, e.chemical(), x, y, screen.trim());
            case RuleEntry e -> renderRuleIcon(g, x, y);
        }
    }

    /** Losango roxo da regra por propriedade. */
    private static void renderRuleIcon(GuiGraphics g, int x, int y) {
        int cx = x + 8;
        for (int i = 0; i < 6; i++) {
            g.fill(cx - i - 1, y + 2 + i, cx + i + 1, y + 3 + i, RULE_COLOR);
            g.fill(cx - i - 1, y + 13 - i, cx + i + 1, y + 14 - i, GuiPaint.mix(RULE_COLOR, GuiPaint.RING, 0.25f));
        }
        g.fill(cx - 3, y + 5, cx - 1, y + 7, GuiPaint.BEVEL_LIGHT);
    }

    /** Tag ou mod: um membro (trocando a cada segundo) com a marca {@code #}/{@code @}, ou só a marca. */
    private void renderMark(GuiGraphics g, FilterEntry entry, String mark, int x, int y) {
        int count;
        if (screen.isChemical()) {
            count = 0;
        } else if (screen.isFluid()) {
            List<FluidStack> list = members.fluidMembers(entry);
            count = list.size();
            if (count > 0) {
                GuiPaint.fluid(g, list.get((int) (Util.getMillis() / 1000 % count)), x, y);
            }
        } else {
            List<ItemStack> list = members.itemMembers(entry);
            count = list.size();
            if (count > 0) {
                g.renderItem(list.get((int) (Util.getMillis() / 1000 % count)), x, y);
            }
        }
        int color = mark.equals("@") ? GuiPaint.BRASS : GuiPaint.ACCENT;
        g.pose().pushPose();
        g.pose().translate(0, 0, 200);
        if (count == 0) {
            g.fill(x, y, x + 16, y + 16, GuiPaint.mix(GuiPaint.INSET, color, 0.18f));
            GuiPaint.textCentered(g, Minecraft.getInstance().font, Component.literal(mark), x + 8, y + 4, color);
        } else {
            g.fill(x - 1, y - 1, x + 6, y + 8, GuiPaint.BEVEL_LIGHT);
            g.drawString(Minecraft.getInstance().font, mark, x, y, color, false);
        }
        g.pose().popPose();
    }
}
