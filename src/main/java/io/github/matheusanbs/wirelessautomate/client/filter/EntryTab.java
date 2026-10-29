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
import java.util.function.BooleanSupplier;
import java.util.function.LongConsumer;
import java.util.function.LongSupplier;
import java.util.function.ToLongFunction;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;

/**
 * A aba Entrada: a entrada escolhida na lista, com o que ela pega, os limites (campo, menos e mais),
 * Remover, e Ver tags (item ou fluido) ou Editar (regra); ou, no armazenamento, a linha fixa com o limite por tipo.
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

    /** Altura de um campo de limite com o rótulo em cima. */
    private static final int ROW_PITCH = 28;

    /**
     * Um limite: o campo, o menos e o mais. {@code value} é o valor salvo, {@code apply} manda um novo e
     * {@code available} diz se há onde aplicar (uma entrada escolhida, ou a linha fixa do limite por tipo).
     */
    private final class Limit {
        private final String key;
        private final LongSupplier value;
        private final LongConsumer apply;
        private final BooleanSupplier available;
        private String draft = "";
        private FlatButton minus;
        private FlatButton plus;
        private EditBox box;

        Limit(String key, LongSupplier value, LongConsumer apply, BooleanSupplier available) {
            this.key = key;
            this.value = value;
            this.apply = apply;
            this.available = available;
        }

        void addWidgets() {
            minus = screen.add(new FlatButton(0, 0, 14, BTN_H, tr("stock.decrease"),
                    (g, b, hovered) -> paintTextButton(g, b, hovered, Component.literal("-"), false),
                    () -> step(-1)));
            plus = screen.add(new FlatButton(0, 0, 14, BTN_H, tr("stock.increase"),
                    (g, b, hovered) -> paintTextButton(g, b, hovered, Component.literal("+"), false),
                    () -> step(1)));
            box = screen.box(tr(key), 15, draft, v -> draft = v);
            box.setFilter(t -> t.chars().allMatch(Character::isDigit));
        }

        long current() {
            return value.getAsLong();
        }

        String text() {
            long current = current();
            return current == 0 ? "" : Long.toString(current);
        }

        void refresh(int px, int y, boolean entry, boolean canStock) {
            minus.visible = plus.visible = box.visible = entry;
            minus.active = entry && current() > 0;
            plus.active = canStock;
            minus.setPosition(px, y);
            plus.setPosition(px + IW - 14, y);
            box.setPosition(px + 20, y + 3);
            box.setWidth(IW - 40 - font().width(screen.unit()) - 2);
        }

        private long parse() {
            try {
                return draft.isEmpty() ? 0 : Long.parseLong(draft);
            } catch (NumberFormatException e) {
                return Long.MAX_VALUE;
            }
        }

        private void step(int direction) {
            if (!available.getAsBoolean()) {
                return;
            }
            long next = Math.max(0, parse() + direction * stockStep());
            box.setValue(next == 0 ? "" : Long.toString(next));
            if (screen.getFocused() == box) {
                screen.unfocus();
            }
            commit();
        }

        /**
         * A roda do mouse sobre a linha do campo (do − ao +): para cima soma, para baixo tira, o passo dos
         * botões. Devolve se o mouse estava na linha (a roda é dela, mesmo com o botão do sentido desligado).
         */
        boolean scroll(double mouseX, double mouseY, int direction) {
            if (!box.visible || mouseX < minus.getX() || mouseX >= plus.getX() + plus.getWidth()
                    || mouseY < minus.getY() || mouseY >= minus.getY() + BTN_H) {
                return false;
            }
            if (direction > 0 ? plus.active : minus.active) {
                step(direction);
            }
            return true;
        }

        /** Manda o valor digitado se ele mudou. */
        void commit() {
            if (!available.getAsBoolean()) {
                return;
            }
            long next = parse();
            if (next != value.getAsLong()) {
                apply.accept(next);
            }
        }

        void paint(GuiGraphics g, int x, int rowY, boolean white, Component label) {
            Font font = font();
            GuiPaint.text(g, font, GuiPaint.ellipsize(font, white ? label : tr("stock.blacklist"), IW), x, rowY - 11,
                    GuiPaint.MUTED);
            GuiPaint.box(g, x + 16, rowY, IW - 32, BTN_H, GuiPaint.PANEL, box.isFocused() ? GuiPaint.ACCENT : GuiPaint.LINE);
            GuiPaint.textRight(g, font, screen.unit(), x + IW - 20, rowY + 3, GuiPaint.MUTED);
        }
    }

    private final Limit accept = new Limit("stock", () -> entryValue(FilterEntry::stock),
            v -> sendEntry(Op.SET_STOCK, v), this::entryChosen);
    private final Limit keep = new Limit("keep", () -> entryValue(FilterEntry::keep),
            v -> sendEntry(Op.SET_KEEP, v), this::entryChosen);
    /** O limite por tipo do armazenamento, na linha fixa da lista. */
    private final Limit global = new Limit("stock.storage.global", this::typeLimit, this::sendTypeLimit,
            this::globalChosen);
    private final Limit[] limits = {accept, keep, global};
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

    private boolean entryChosen() {
        return screen.selection().selected() != null && screen.selection().selectedIndex() >= 0;
    }

    private long entryValue(ToLongFunction<FilterEntry> limit) {
        FilterEntry selected = screen.selection().selected();
        return selected == null ? 0 : limit.applyAsLong(selected);
    }

    private void sendEntry(Op op, long value) {
        actions.send(op, screen.selection().selectedIndex(), value, "");
    }

    private boolean globalChosen() {
        return screen.selection().global();
    }

    private long typeLimit() {
        return screen.filter().typeLimit();
    }

    private void sendTypeLimit(long value) {
        actions.send(Op.SET_TYPE_LIMIT, 0, value, "");
    }

    void addWidgets() {
        for (Limit limit : limits) {
            limit.addWidgets();
        }
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

    /** O campo "Aceitar até". */
    EditBox stockBox() {
        return accept.box;
    }

    /** O campo "Manter na origem". */
    EditBox keepBox() {
        return keep.box;
    }

    /** O campo do limite por tipo (a linha fixa do armazenamento). */
    EditBox globalBox() {
        return global.box;
    }

    boolean isLimitBox(Object box) {
        for (Limit limit : limits) {
            if (box == limit.box) {
                return true;
            }
        }
        return false;
    }

    boolean overLimitBox(double mouseX, double mouseY) {
        for (Limit limit : limits) {
            if (limit.box.isMouseOver(mouseX, mouseY)) {
                return true;
            }
        }
        return false;
    }

    /** Põe de volta, em todos os campos, os valores salvos. */
    void resetLimitBoxes() {
        for (Limit limit : limits) {
            FilterScreen.show(limit.box, limit.text());
        }
    }

    /** Só os campos que não estão sendo digitados. */
    void syncLimitBoxes() {
        for (Limit limit : limits) {
            if (!limit.box.isFocused()) {
                FilterScreen.show(limit.box, limit.text());
            }
        }
    }

    /** O campo {@code box} volta ao valor salvo (Esc). */
    void resetLimitBox(EditBox box) {
        for (Limit limit : limits) {
            if (box == limit.box) {
                FilterScreen.show(limit.box, limit.text());
            }
        }
    }

    void refresh(int px, int bottom, int half) {
        FilterEntry selected = screen.selection().selected();
        boolean entry = screen.tab() == FilterScreen.Tab.ENTRY && selected != null;
        removeButton.visible = entry;
        seeTagsButton.visible = entry && selected instanceof ItemEntry || entry && selected instanceof FluidEntry;
        editButton.visible = entry && selected instanceof RuleEntry;
        boolean storage = screen.view().isStorage();
        boolean canStock = storage || screen.filter().listMode() == Filter.ListMode.WHITELIST;
        int keepY = bottom - BTN_H - 4 - BTN_H;
        keep.refresh(px, keepY, entry && !storage, canStock);
        // Armazenamento: só um limite, no lugar de baixo.
        accept.refresh(px, storage ? keepY : keepY - ROW_PITCH, entry, canStock);
        global.refresh(px, keepY, screen.tab() == FilterScreen.Tab.ENTRY && screen.selection().global(), true);
        boolean secondButton = seeTagsButton.visible || editButton.visible;
        removeButton.setPosition(px, bottom - BTN_H);
        removeButton.setWidth(secondButton ? half : IW);
        seeTagsButton.setPosition(px + IW - half, bottom - BTN_H);
        seeTagsButton.setWidth(half);
        editButton.setPosition(px + IW - half, bottom - BTN_H);
        editButton.setWidth(half);
    }

    /** A roda do mouse sobre um campo de limite: {@code direction} 1 soma, −1 tira. */
    boolean mouseScrolled(double mouseX, double mouseY, int direction) {
        for (Limit limit : limits) {
            if (limit.scroll(mouseX, mouseY, direction)) {
                return true;
            }
        }
        return false;
    }

    /** Manda os limites digitados, os que mudaram. */
    void commitStock() {
        for (Limit limit : limits) {
            limit.commit();
        }
    }

    private long stockStep() {
        return screen.inMb() ? (Screen.hasShiftDown() ? 10_000 : 1_000) : (Screen.hasShiftDown() ? 64 : 1);
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
        if (screen.selection().global()) {
            renderGlobal(g, x, y);
            return;
        }
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
        int keepRowY = y + frame.innerBottom() - BTN_H - 4 - BTN_H;
        boolean storage = screen.view().isStorage();
        int stockLabelY = (storage ? keepRowY : keepRowY - ROW_PITCH) - 11;
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
        // limites: aceitar até (destino) e manter na origem
        boolean white = storage || screen.filter().listMode() == Filter.ListMode.WHITELIST;
        if (storage) {
            accept.paint(g, x, keepRowY, white, tr("stock.storage"));
        } else {
            accept.paint(g, x, keepRowY - ROW_PITCH, white, tr("stock"));
            keep.paint(g, x, keepRowY, white, tr("keep"));
        }
    }

    /** A linha fixa escolhida: o que ela é e o limite por tipo. */
    private void renderGlobal(GuiGraphics g, int x, int y) {
        GuiPaint.slot(g, x - 1, y + BOX_Y + 5);
        g.renderItem(screen.storageIcon(), x, y + BOX_Y + 6);
        int lines = wrapped(g, tr("global.name"), x + 21, y + BOX_Y + 6, IW - 21, 2, GuiPaint.FG);
        int ty = y + BOX_Y + 6 + Math.max(2, lines) * 10;
        wrapped(g, tr("global.desc"), x, ty, IW, 5, GuiPaint.MUTED);
        global.paint(g, x, y + frame.innerBottom() - BTN_H - 4 - BTN_H, true, tr("stock.storage.global"));
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
