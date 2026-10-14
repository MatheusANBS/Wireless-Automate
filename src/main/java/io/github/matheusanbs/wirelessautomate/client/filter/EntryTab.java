package io.github.matheusanbs.wirelessautomate.client.filter;

import static io.github.matheusanbs.wirelessautomate.client.filter.FilterLayout.BOX_Y;
import static io.github.matheusanbs.wirelessautomate.client.filter.FilterLayout.BTN_H;
import static io.github.matheusanbs.wirelessautomate.client.filter.FilterLayout.IW;
import static io.github.matheusanbs.wirelessautomate.client.filter.FilterPaint.paintDangerButton;
import static io.github.matheusanbs.wirelessautomate.client.filter.FilterPaint.paintTextButton;
import static io.github.matheusanbs.wirelessautomate.client.filter.FilterPaint.wrapped;
import static io.github.matheusanbs.wirelessautomate.client.filter.FilterText.tr;

import io.github.matheusanbs.wirelessautomate.client.FlatButton;
import io.github.matheusanbs.wirelessautomate.client.GuiPaint;
import io.github.matheusanbs.wirelessautomate.filter.Filter;
import io.github.matheusanbs.wirelessautomate.filter.FilterEntry;
import io.github.matheusanbs.wirelessautomate.filter.FilterEntry.FluidEntry;
import io.github.matheusanbs.wirelessautomate.filter.FilterEntry.ItemEntry;
import io.github.matheusanbs.wirelessautomate.filter.FilterEntry.ModEntry;
import io.github.matheusanbs.wirelessautomate.filter.FilterEntry.RuleEntry;
import io.github.matheusanbs.wirelessautomate.filter.FilterEntry.TagEntry;
import io.github.matheusanbs.wirelessautomate.packet.EditFilterPayload.Op;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;

/**
 * A aba Entrada: a entrada escolhida na lista, com o que ela pega, o estoque (campo, menos e mais),
 * Remover, e Ver tags (item ou fluido) ou Editar (regra).
 */
final class EntryTab {
    private final FilterScreen screen;
    private final FilterFrame frame;
    private final FilterText text;
    private final EntryIcons icons;
    private final FilterMembers members;
    private final FilterActions actions;
    private final InventoryArea inventory;
    private final TagsTab tags;
    private final RuleTab rule;

    private String stockDraft = "";
    private FlatButton minusButton;
    private FlatButton plusButton;
    private EditBox stockBox;
    private FlatButton removeButton;
    private FlatButton seeTagsButton;
    private FlatButton editButton;

    EntryTab(FilterScreen screen, FilterText text, EntryIcons icons, FilterMembers members, FilterActions actions,
            InventoryArea inventory, TagsTab tags, RuleTab rule) {
        this.screen = screen;
        this.frame = screen.frame();
        this.text = text;
        this.icons = icons;
        this.members = members;
        this.actions = actions;
        this.inventory = inventory;
        this.tags = tags;
        this.rule = rule;
    }

    private static Font font() {
        return Minecraft.getInstance().font;
    }

    void addWidgets() {
        minusButton = screen.add(new FlatButton(0, 0, 14, BTN_H, tr("stock.decrease"),
                (g, b, hovered) -> paintTextButton(g, b, hovered, Component.literal("-"), false),
                () -> stepStock(-1)).tooltip(() -> stepTooltip("stock.decrease.tooltip")));
        plusButton = screen.add(new FlatButton(0, 0, 14, BTN_H, tr("stock.increase"),
                (g, b, hovered) -> paintTextButton(g, b, hovered, Component.literal("+"), false),
                () -> stepStock(1)).tooltip(() -> stepTooltip("stock.increase.tooltip")));
        stockBox = screen.box(tr("stock"), 15, stockDraft, value -> stockDraft = value);
        stockBox.setFilter(s -> s.chars().allMatch(Character::isDigit));
        removeButton = screen.add(new FlatButton(0, 0, 40, BTN_H, tr("remove"),
                (g, b, hovered) -> paintDangerButton(g, b, hovered, tr("remove"), false), this::removeSelected)
                .tooltip(() -> tr("remove.tooltip")));
        seeTagsButton = screen.add(new FlatButton(0, 0, 40, BTN_H, tr("see_tags"),
                (g, b, hovered) -> paintTextButton(g, b, hovered, tr("see_tags"), false), this::seeTags)
                .tooltip(() -> tr("see_tags.tooltip")));
        editButton = screen.add(new FlatButton(0, 0, 40, BTN_H, tr("edit"),
                (g, b, hovered) -> paintTextButton(g, b, hovered, tr("edit"), false), this::editSelected)
                .tooltip(() -> tr("edit.tooltip")));
    }

    EditBox stockBox() {
        return stockBox;
    }

    void refresh(int px, int bottom, int half) {
        FilterEntry selected = screen.selection().selected();
        boolean entry = screen.tab() == FilterScreen.Tab.ENTRY && selected != null;
        minusButton.visible = plusButton.visible = stockBox.visible = removeButton.visible = entry;
        seeTagsButton.visible = entry && selected instanceof ItemEntry || entry && selected instanceof FluidEntry;
        editButton.visible = entry && selected instanceof RuleEntry;
        boolean canStock = screen.filter().listMode() == Filter.ListMode.WHITELIST;
        minusButton.active = entry && selected.stock() > 0;
        plusButton.active = canStock;
        int stockY = bottom - BTN_H - 4 - BTN_H;
        minusButton.setPosition(px, stockY);
        plusButton.setPosition(px + IW - 14, stockY);
        stockBox.setPosition(px + 20, stockY + 3);
        stockBox.setWidth(IW - 40 - (screen.inMb() ? font().width(tr("unit.mb")) + 2 : 0));
        boolean secondButton = seeTagsButton.visible || editButton.visible;
        removeButton.setPosition(px, bottom - BTN_H);
        removeButton.setWidth(secondButton ? half : IW);
        seeTagsButton.setPosition(px + IW - half, bottom - BTN_H);
        seeTagsButton.setWidth(half);
        editButton.setPosition(px + IW - half, bottom - BTN_H);
        editButton.setWidth(half);
    }

    String stockText() {
        FilterEntry selected = screen.selection().selected();
        return selected == null || selected.stock() == 0 ? "" : Long.toString(selected.stock());
    }

    private long stockStep() {
        return screen.inMb() ? (Screen.hasShiftDown() ? 10_000 : 1_000) : (Screen.hasShiftDown() ? 64 : 1);
    }

    private Component stepTooltip(String key) {
        return screen.inMb() ? tr(key, tr("unit.buckets", 1), tr("unit.buckets", 10)) : tr(key, 1, 64);
    }

    private void stepStock(int direction) {
        if (screen.selection().selected() == null) {
            return;
        }
        long value = Math.max(0, parseStock() + direction * stockStep());
        stockBox.setValue(value == 0 ? "" : Long.toString(value));
        if (screen.getFocused() == stockBox) {
            screen.unfocus();
        }
        commitStock();
    }

    private long parseStock() {
        try {
            return stockDraft.isEmpty() ? 0 : Long.parseLong(stockDraft);
        } catch (NumberFormatException e) {
            return Long.MAX_VALUE;
        }
    }

    /** Manda o estoque digitado se ele mudou. */
    void commitStock() {
        FilterEntry selected = screen.selection().selected();
        if (selected == null || screen.selection().selectedIndex() < 0) {
            return;
        }
        long value = parseStock();
        if (value != selected.stock()) {
            actions.send(Op.SET_STOCK, screen.selection().selectedIndex(), value, "");
        }
    }

    private void removeSelected() {
        if (screen.selection().selected() != null && screen.selection().selectedIndex() >= 0) {
            int index = screen.selection().selectedIndex();
            screen.selection().clear();
            actions.send(Op.REMOVE, index, 0, "");
        }
    }

    /** Item (ou fluido) selecionado vai para o inspetor, na aba Tags. */
    private void seeTags() {
        switch (screen.selection().selected()) {
            case ItemEntry e -> tags.inspect(e.stack());
            case FluidEntry e -> tags.inspectFluid(e.stack());
            case null, default -> {
                return;
            }
        }
        screen.openTab(FilterScreen.Tab.TAGS);
    }

    /** Regra selecionada vai para o montador, para editar. */
    private void editSelected() {
        if (screen.selection().selected() instanceof RuleEntry entry) {
            rule.edit(entry.rule(), screen.selection().selectedIndex());
        }
    }

    void render(GuiGraphics g) {
        Font font = font();
        FilterEntry selected = screen.selection().selected();
        int x = frame.left() + frame.ix();
        int y = frame.top();
        if (selected == null) {
            Component hint = !screen.filter().isEmpty() ? tr("select.hint")
                    : FilterText.JEI ? tr("select.empty.jei") : tr("select.empty");
            wrapped(g, hint, x, y + BOX_Y + 6, IW, 10, GuiPaint.MUTED);
            return;
        }
        GuiPaint.slot(g, x - 1, y + BOX_Y + 5);
        icons.renderEntryIcon(g, selected, x, y + BOX_Y + 6);
        int lines = wrapped(g, text.entryName(selected), x + 21, y + BOX_Y + 6, IW - 21, 2,
                selected instanceof RuleEntry ? GuiPaint.RULE_TEXT : GuiPaint.FG);
        int ty = y + BOX_Y + 6 + Math.max(2, lines) * 10;
        GuiPaint.text(g, font, GuiPaint.ellipsize(font, Component.literal(FilterText.entryId(selected)), IW), x, ty,
                GuiPaint.DISABLED);
        ty += 12;
        int stockLabelY = y + frame.innerBottom() - BTN_H - 4 - BTN_H - 11;
        switch (selected) {
            case TagEntry e -> ty = renderMembers(g, e, x, ty, stockLabelY);
            case ModEntry e -> ty = renderMembers(g, e, x, ty, stockLabelY);
            case RuleEntry e -> {
                int n = InventoryArea.matches(screen, inventory.currentHighlight());
                wrapped(g, tr("rule.inventory", n), x, ty, IW, 2, GuiPaint.ACCENT);
            }
            case ItemEntry e -> {
                long count = e.stack().getTags().count();
                wrapped(g, tr("entry.item", count), x, ty, IW, 3, GuiPaint.MUTED);
            }
            default -> {
            }
        }
        // estoque
        boolean white = screen.filter().listMode() == Filter.ListMode.WHITELIST;
        GuiPaint.text(g, font, GuiPaint.ellipsize(font, white ? tr("stock") : tr("stock.blacklist"), IW), x, stockLabelY,
                GuiPaint.MUTED);
        int fy = y + frame.innerBottom() - BTN_H - 4 - BTN_H;
        GuiPaint.box(g, x + 16, fy, IW - 32, BTN_H, GuiPaint.PANEL, stockBox.isFocused() ? GuiPaint.ACCENT : GuiPaint.LINE);
        if (screen.inMb()) {
            GuiPaint.textRight(g, font, tr("unit.mb"), x + IW - 20, fy + 3, GuiPaint.MUTED);
        }
    }

    /** "Pega 38 itens" e os ícones dos primeiros, até {@code limit}; devolve onde parou. */
    private int renderMembers(GuiGraphics g, FilterEntry entry, int x, int y, int limit) {
        Font font = font();
        if (screen.isChemical()) {
            GuiPaint.text(g, font, tr(entry instanceof ModEntry ? "entry.mod" : "entry.tag"), x, y, GuiPaint.MUTED);
            return y + 10;
        }
        int count;
        List<ItemStack> items = List.of();
        List<FluidStack> fluids = List.of();
        if (screen.isFluid()) {
            fluids = members.fluidMembers(entry);
            count = fluids.size();
        } else {
            items = members.itemMembers(entry);
            count = items.size();
        }
        GuiPaint.text(g, font, tr(count >= 64 ? "entry.members.many" : "entry.members", count), x, y, GuiPaint.MUTED);
        y += 11;
        int perRow = IW / 18;
        int rows = Math.max(0, (limit - y - 2) / 18);
        int shownCount = Math.min(count, perRow * rows);
        for (int i = 0; i < shownCount; i++) {
            int cx = x + (i % perRow) * 18;
            int cy = y + (i / perRow) * 18;
            if (screen.isFluid()) {
                GuiPaint.fluid(g, fluids.get(i), cx, cy);
            } else {
                g.renderItem(items.get(i), cx, cy);
            }
        }
        return y + (shownCount + perRow - 1) / perRow * 18;
    }
}
