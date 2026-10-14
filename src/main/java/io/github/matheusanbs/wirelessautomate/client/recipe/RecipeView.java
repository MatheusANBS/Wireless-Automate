package io.github.matheusanbs.wirelessautomate.client.recipe;

import static io.github.matheusanbs.wirelessautomate.client.recipe.RecipeEditorScreen.tr;
import static io.github.matheusanbs.wirelessautomate.client.recipe.RecipeItems.item;
import static io.github.matheusanbs.wirelessautomate.client.recipe.RecipeItems.itemName;
import static io.github.matheusanbs.wirelessautomate.client.recipe.RecipeItems.resultStack;
import static io.github.matheusanbs.wirelessautomate.client.recipe.RecipeLayout.ARROW_X;
import static io.github.matheusanbs.wirelessautomate.client.recipe.RecipeLayout.ARROW_Y;
import static io.github.matheusanbs.wirelessautomate.client.recipe.RecipeLayout.GRID_X;
import static io.github.matheusanbs.wirelessautomate.client.recipe.RecipeLayout.GRID_Y;
import static io.github.matheusanbs.wirelessautomate.client.recipe.RecipeLayout.INSPECT_H;
import static io.github.matheusanbs.wirelessautomate.client.recipe.RecipeLayout.INSPECT_Y;
import static io.github.matheusanbs.wirelessautomate.client.recipe.RecipeLayout.OUT_SIZE;
import static io.github.matheusanbs.wirelessautomate.client.recipe.RecipeLayout.OUT_X;
import static io.github.matheusanbs.wirelessautomate.client.recipe.RecipeLayout.OUT_Y;
import static io.github.matheusanbs.wirelessautomate.client.recipe.RecipeLayout.RW;
import static io.github.matheusanbs.wirelessautomate.client.recipe.RecipeLayout.STEP_X;
import static io.github.matheusanbs.wirelessautomate.client.recipe.RecipeLayout.X0;

import io.github.matheusanbs.wirelessautomate.client.GuiPaint;
import io.github.matheusanbs.wirelessautomate.client.GuiText;
import io.github.matheusanbs.wirelessautomate.menu.RecipeEditorSnapshot.Row;
import io.github.matheusanbs.wirelessautomate.recipe.edit.RecipeDraft;
import io.github.matheusanbs.wirelessautomate.recipe.edit.RecipeSlot;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

/**
 * A receita escolhida, na coluna da direita: nome, estado, forma e id; a grade 3×3 de slots fantasmas;
 * a seta, o resultado e a quantidade; o inspetor do slot escolhido (ou o aviso de desativada); e as dicas
 * da grade e do resultado.
 */
final class RecipeView {
    private final RecipeEditorScreen screen;
    private final RecipeEdit edit;

    RecipeView(RecipeEditorScreen screen, RecipeEdit edit) {
        this.screen = screen;
        this.edit = edit;
    }

    private static Font font() {
        return Minecraft.getInstance().font;
    }

    void render(GuiGraphics g, int x, int y, Row row, int mouseX, int mouseY) {
        RecipeDraft draft = edit.draft();
        boolean off = draft.disabled();
        renderHeader(g, x, y, row, draft);
        renderGrid(g, x, y, draft, off, mouseX, mouseY);
        renderResult(g, x, y, row, draft, off);

        // inspetor do slot escolhido, ou o aviso de desativada
        if (off) {
            GuiText.wrap(g, font(), tr("disabled_hint"), x + X0 + 4, y + INSPECT_Y + 2, RW - 8, 3, GuiPaint.MUTED);
            return;
        }
        renderInspector(g, x, y, draft);
    }

    /** Nome, pílula de estado, forma e id. */
    private void renderHeader(GuiGraphics g, int x, int y, Row row, RecipeDraft draft) {
        Font font = font();
        boolean dirty = edit.dirty();
        Component state = dirty ? tr("state.unsaved") : tr("state." + row.state().name().toLowerCase(Locale.ROOT));
        int stateColor = dirty ? GuiPaint.WARN : RecipePaint.stateColor(row.state());
        int pillW = font.width(state) + 8;
        int pillX = x + X0 + RW - pillW;
        GuiPaint.pill(g, pillX, y + 20, pillW, 11, GuiPaint.BEVEL_LIGHT, stateColor);
        GuiPaint.text(g, font, state, pillX + 4, y + 22, stateColor);
        GuiText.draw(g, font, resultStack(row).getHoverName(), x + X0, y + 22, pillX - 4 - (x + X0), GuiPaint.FG);
        Component shape = tr(switch (draft.shape()) {
            case SHAPED -> "shaped";
            case SHAPELESS -> "shapeless";
            case RING -> "ring";
        });
        GuiText.draw(g, font, shape.copy().append(" · " + row.id().getPath()), x + X0, y + 32, RW, GuiPaint.MUTED);
        if (row.divergent()) {
            GuiText.wrap(g, font, tr("divergent"), x + X0, y + 42, RW, 2, GuiPaint.WARN);
        }
    }

    private void renderGrid(GuiGraphics g, int x, int y, RecipeDraft draft, boolean off, int mouseX, int mouseY) {
        int hover = screen.gridSlotAt(mouseX, mouseY);
        for (int i = 0; i < RecipeDraft.SLOTS; i++) {
            int sx = x + GRID_X + (i % 3) * 18;
            int sy = y + GRID_Y + (i / 3) * 18;
            boolean selected = i == edit.selectedSlot() && !off;
            if (draft.locked(i)) {
                RecipePaint.lockedCenter(g, sx, sy, off);
                continue;
            }
            if (selected) {
                g.fill(sx, sy, sx + 18, sy + 18, GuiPaint.BEVEL_LIGHT);
                GuiPaint.outline(g, sx, sy, 18, 18, GuiPaint.ACCENT);
            } else if (hover == i && !off) {
                g.fill(sx, sy, sx + 18, sy + 18, GuiPaint.ROW_HOVER);
                g.fill(sx, sy, sx + 18, sy + 1, GuiPaint.BEVEL_DARK);
                g.fill(sx, sy, sx + 1, sy + 18, GuiPaint.BEVEL_DARK);
            } else {
                GuiPaint.slot(g, sx, sy);
            }
            RecipeSlot slot = draft.slots().get(i);
            Item item = edit.shownItem(i, slot);
            if (item != Items.AIR) {
                g.renderItem(new ItemStack(item), sx + 1, sy + 1);
                // fantasma: o item apagado; desativada, mais ainda
                RecipePaint.overlay(g, sx + 1, sy + 1, 16, 16, selected ? GuiPaint.BEVEL_LIGHT : GuiPaint.INSET,
                        off ? 0xB0 : 0x60);
            }
            if (slot.kind() == RecipeSlot.Kind.TAG) {
                RecipePaint.tagMark(g, sx, sy, off);
            }
        }
    }

    /** Seta, resultado e quantidade. */
    private void renderResult(GuiGraphics g, int x, int y, Row row, RecipeDraft draft, boolean off) {
        Font font = font();
        RecipePaint.arrow(g, x + ARROW_X, y + ARROW_Y, off ? GuiPaint.DISABLED : GuiPaint.MUTED);
        int ox = x + OUT_X;
        int oy = y + OUT_Y;
        GuiPaint.inset(g, ox, oy, OUT_SIZE, OUT_SIZE);
        ItemStack result = resultStack(row);
        g.renderItem(result, ox + 5, oy + 5);
        if (off) {
            RecipePaint.overlay(g, ox + 5, oy + 5, 16, 16, GuiPaint.INSET, 0xB0);
        } else if (draft.count() > 1) {
            // a quantidade em tinta de grafite no canto (o branco do jogo some na porcelana)
            Component qty = Component.literal(String.valueOf(draft.count()));
            g.pose().pushPose();
            g.pose().translate(0, 0, 200);
            GuiPaint.text(g, font, qty, ox + OUT_SIZE - 2 - font.width(qty), oy + OUT_SIZE - 9, GuiPaint.FG);
            g.pose().popPose();
        }
        if (edit.ring()) {
            // sem quantidade: o aviso do centro travado ao lado do resultado
            GuiText.wrap(g, font, tr("ring.center"), x + STEP_X - 4, y + OUT_Y - 4, x + X0 + RW - (x + STEP_X - 4), 4,
                    off ? GuiPaint.DISABLED : GuiPaint.MUTED);
        } else {
            Component count = draft.count() == 1 ? tr("count.one") : tr("count", draft.count());
            GuiText.draw(g, font, count, x + STEP_X + 16, y + OUT_Y + 9, x + X0 + RW - (x + STEP_X + 16),
                    off ? GuiPaint.DISABLED : GuiPaint.MUTED);
        }
    }

    private void renderInspector(GuiGraphics g, int x, int y, RecipeDraft draft) {
        Font font = font();
        int selectedSlot = edit.selectedSlot();
        GuiPaint.inset(g, x + X0, y + INSPECT_Y, RW, INSPECT_H);
        RecipeSlot slot = draft.slots().get(selectedSlot);
        MutableComponent value = switch (slot.kind()) {
            case EMPTY -> tr("slot.empty").copy().withColor(GuiPaint.MUTED);
            case ITEM -> itemName(item(slot.id())).copy().withColor(GuiPaint.FG);
            case TAG -> Component.literal("#" + slot.id()).withColor(GuiPaint.FG);
        };
        GuiText.draw(g, font, tr("slot", selectedSlot + 1, value), x + X0 + 5, y + INSPECT_Y + 4, RW - 10, GuiPaint.FG);
        List<String> tags = edit.slotTags();
        if (slot.kind() == RecipeSlot.Kind.TAG && tags.size() > 1) {
            GuiPaint.text(g, font, edit.tagIndexText(tags), x + X0 + 86, y + INSPECT_Y + 19, GuiPaint.MUTED);
        } else if (slot.kind() == RecipeSlot.Kind.ITEM && tags.isEmpty()) {
            GuiText.draw(g, font, tr("no_tags"), x + X0 + 71, y + INSPECT_Y + 19,
                    screen.clearButtonX() - 4 - (x + X0 + 71), GuiPaint.DISABLED);
        }
    }

    /** As linhas da dica de um slot da grade ou do resultado; {@code null} fora deles. */
    @Nullable List<Component> tooltipAt(int mx, int my) {
        RecipeDraft draft = edit.draft();
        if (draft == null) {
            return null;
        }
        Row row = edit.selectedRow();
        int grid = screen.gridSlotAt(mx, my);
        if (grid >= 0) {
            if (draft.locked(grid)) {
                return List.of(tr("ring.center").copy().withColor(0xFFFFB39E));
            }
            List<Component> lines = new ArrayList<>();
            RecipeSlot slot = draft.slots().get(grid);
            switch (slot.kind()) {
                case EMPTY -> {
                    String empty = tr("slot.empty").getString();
                    lines.add(Component.literal(empty.isEmpty() ? empty
                            : empty.substring(0, 1).toUpperCase(Locale.ROOT) + empty.substring(1))
                            .withColor(GuiPaint.TOOLTIP_MUTED));
                    if (edit.canEditGrid()) {
                        lines.add(tr("tooltip.pick").copy().withColor(0xFFFFB39E));
                    }
                }
                case ITEM -> {
                    lines.add(itemName(item(slot.id())));
                    lines.add(Component.literal(slot.id()).withColor(GuiPaint.TOOLTIP_MUTED));
                }
                case TAG -> {
                    lines.add(tr("tooltip.any_of").copy().withColor(0xFFFFB39E));
                    lines.add(Component.literal("#" + slot.id()));
                }
            }
            if (edit.canEditGrid() && !slot.isEmpty()) {
                lines.add(tr("tooltip.clear").copy().withColor(GuiPaint.TOOLTIP_MUTED));
            }
            return lines;
        }
        if (row != null && screen.overResult(mx, my)) {
            return List.of(resultStack(row).getHoverName().copy().append(" ×" + draft.count()),
                    Component.literal(row.resultItem().toString()).withColor(GuiPaint.TOOLTIP_MUTED));
        }
        return null;
    }
}
