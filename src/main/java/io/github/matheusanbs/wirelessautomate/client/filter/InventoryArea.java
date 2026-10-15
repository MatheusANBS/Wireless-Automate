package io.github.matheusanbs.wirelessautomate.client.filter;

import static io.github.matheusanbs.wirelessautomate.client.filter.FilterLayout.X0;

import io.github.matheusanbs.wirelessautomate.client.GuiPaint;
import io.github.matheusanbs.wirelessautomate.filter.Filter;
import io.github.matheusanbs.wirelessautomate.filter.FilterEntry;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * O inventário do jogador embaixo da lista: põe os slots do menu no lugar para o tamanho atual, desenha a
 * grade com a linha de como adicionar, e acende o que a regra em montagem (ou a entrada escolhida) pega.
 */
final class InventoryArea {
    private final FilterScreen screen;
    private final FilterFrame frame;
    private final FilterText text;
    private final RuleTab rule;

    private @Nullable FilterEntry highlightFor;
    private Predicate<ItemStack> highlight = stack -> false;

    InventoryArea(FilterScreen screen, FilterText text, RuleTab rule) {
        this.screen = screen;
        this.frame = screen.frame();
        this.text = text;
        this.rule = rule;
    }

    /** Os slots do inventário acompanham a tela. */
    void place() {
        screen.getMenu().placeInventory(X0 + 1, frame.invTop() + 1);
    }

    /** O inventário acompanhou a tela: o primeiro slot da mochila no lugar certo. */
    boolean follows() {
        Slot first = screen.getMenu().slots.get(0);
        return first.x == X0 + 1 && first.y == frame.invTop() + 1;
    }

    /** O que acende no inventário: a regra em montagem (aba Regra) ou a entrada selecionada (itens). */
    Predicate<ItemStack> currentHighlight() {
        if (screen.tab() == FilterScreen.Tab.RULE) {
            return rule.draftPredicate();
        }
        FilterEntry selected = screen.selection().selected();
        if (screen.tab() == FilterScreen.Tab.ENTRY && selected != null && screen.isItem()) {
            if (!selected.equals(highlightFor)) {
                highlightFor = selected;
                Filter single = new Filter(Filter.ListMode.WHITELIST, screen.filter().matchComponents(),
                        List.of(selected.withStock(0)));
                highlight = single::testItem;
            }
            return highlight;
        }
        return stack -> false;
    }

    /** Quantos slots do inventário do jogador têm algo que o teste pega. */
    static int matches(FilterScreen screen, Predicate<ItemStack> test) {
        int n = 0;
        for (Slot slot : screen.getMenu().slots) {
            if (slot.hasItem() && test.test(slot.getItem())) {
                n++;
            }
        }
        return n;
    }

    void render(GuiGraphics g) {
        Font font = Minecraft.getInstance().font;
        int x = frame.left();
        int y = frame.top();
        int inv = y + frame.invTop();
        GuiPaint.text(g, font, GuiPaint.ellipsize(font, text.inventoryHint(), frame.listW()), x + X0, inv - 10, GuiPaint.MUTED);
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                GuiPaint.slot(g, x + X0 + col * 18, inv + row * 18);
            }
        }
        for (int col = 0; col < 9; col++) {
            GuiPaint.slot(g, x + X0 + col * 18, inv + 58);
        }
        // o que a regra (ou a entrada selecionada) pega acende, embaixo do item
        Predicate<ItemStack> test = currentHighlight();
        for (Slot slot : screen.getMenu().slots) {
            if (slot.hasItem() && test.test(slot.getItem())) {
                int sx = x + slot.x - 1;
                int sy = y + slot.y - 1;
                g.fill(sx + 1, sy + 1, sx + 17, sy + 17, GuiPaint.mix(GuiPaint.INSET, GuiPaint.ACCENT, 0.35f));
                GuiPaint.outline(g, sx, sy, 18, 18, GuiPaint.ACCENT);
            }
        }
    }
}
