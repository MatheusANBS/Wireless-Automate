package io.github.matheusanbs.wirelessautomate.client.recipe;

import static io.github.matheusanbs.wirelessautomate.client.recipe.RecipeEditorScreen.tr;
import static io.github.matheusanbs.wirelessautomate.client.recipe.RecipeItems.resultStack;
import static io.github.matheusanbs.wirelessautomate.client.recipe.RecipeLayout.LIST_H;
import static io.github.matheusanbs.wirelessautomate.client.recipe.RecipeLayout.LIST_W;
import static io.github.matheusanbs.wirelessautomate.client.recipe.RecipeLayout.LIST_X;
import static io.github.matheusanbs.wirelessautomate.client.recipe.RecipeLayout.LIST_Y;
import static io.github.matheusanbs.wirelessautomate.client.recipe.RecipeLayout.PENDING_Y;
import static io.github.matheusanbs.wirelessautomate.client.recipe.RecipeLayout.ROWS;
import static io.github.matheusanbs.wirelessautomate.client.recipe.RecipeLayout.ROW_H;
import static io.github.matheusanbs.wirelessautomate.client.recipe.RecipeLayout.SEARCH_Y;

import io.github.matheusanbs.wirelessautomate.client.GuiPaint;
import io.github.matheusanbs.wirelessautomate.client.GuiText;
import io.github.matheusanbs.wirelessautomate.menu.RecipeEditorMenu;
import io.github.matheusanbs.wirelessautomate.menu.RecipeEditorSnapshot;
import io.github.matheusanbs.wirelessautomate.menu.RecipeEditorSnapshot.Row;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * A coluna da esquerda do editor de receitas: a busca, a lista das receitas do mod (com a bolinha do
 * estado e a rolagem) e a linha da recarga pendente.
 */
final class RecipeList {
    private final RecipeEditorMenu menu;
    private int scroll;
    private String search = "";
    private EditBox searchBox;
    /** As linhas da busca, refeitas só quando o snapshot ou a busca mudam (não a cada quadro). */
    private List<Row> visible = List.of();
    private @Nullable RecipeEditorSnapshot visibleFor;
    private String visibleSearch = "";

    RecipeList(RecipeEditorMenu menu) {
        this.menu = menu;
    }

    EditBox searchBox() {
        return searchBox;
    }

    /** O campo da busca; a tela o põe entre os widgets. Digitar volta a lista ao topo. */
    EditBox createSearchBox(int x, int y) {
        Font font = Minecraft.getInstance().font;
        searchBox = new EditBox(font, x + LIST_X + 4, y + SEARCH_Y + 3, LIST_W - 8, 9, tr("search"));
        searchBox.setBordered(false);
        searchBox.setMaxLength(64);
        searchBox.setTextShadow(false); // tinta sobre porcelana, sem sombra
        searchBox.setValue(search);
        searchBox.setResponder(value -> {
            search = value;
            scroll = 0;
        });
        return searchBox;
    }

    List<Row> visibleRows() {
        RecipeEditorSnapshot snapshot = menu.snapshot();
        if (snapshot == visibleFor && search.equals(visibleSearch)) {
            return visible;
        }
        String q = search.trim().toLowerCase(Locale.ROOT);
        List<Row> out = new ArrayList<>();
        for (Row row : snapshot.rows()) {
            if (q.isEmpty() || resultStack(row).getHoverName().getString().toLowerCase(Locale.ROOT).contains(q)
                    || row.id().getPath().contains(q)) {
                out.add(row);
            }
        }
        visible = out;
        visibleFor = snapshot;
        visibleSearch = search;
        return out;
    }

    private int maxScroll() {
        return Math.max(0, visibleRows().size() - ROWS);
    }

    void clampScroll() {
        scroll = Math.max(0, Math.min(scroll, maxScroll()));
    }

    int rowAt(double mx, double my, int left, int top) {
        int lx = left + LIST_X + 1;
        int ly = top + LIST_Y + 2;
        if (mx < lx || mx >= lx + LIST_W - 2 || my < ly || my >= ly + ROWS * ROW_H) {
            return -1;
        }
        int index = scroll + (int) ((my - ly) / ROW_H);
        return index < visibleRows().size() ? index : -1;
    }

    boolean mouseScrolled(double mouseX, double mouseY, double scrollY, int left, int top) {
        if (mouseX >= left + LIST_X && mouseX < left + LIST_X + LIST_W && mouseY >= top + LIST_Y
                && mouseY < top + LIST_Y + LIST_H) {
            scroll = Math.max(0, Math.min(maxScroll(), scroll - (int) Math.signum(scrollY)));
            return true;
        }
        return false;
    }

    /** Rola a lista até a receita e devolve o centro da linha dela, ou {@code null} se não está na lista. */
    int @Nullable [] rowCenter(ResourceLocation id, int left, int top) {
        List<Row> rows = visibleRows();
        for (int i = 0; i < rows.size(); i++) {
            if (rows.get(i).id().equals(id)) {
                if (i < scroll || i >= scroll + ROWS) {
                    scroll = Math.max(0, Math.min(maxScroll(), i));
                }
                return new int[] {left + LIST_X + LIST_W / 2, top + LIST_Y + 2 + (i - scroll) * ROW_H + ROW_H / 2};
            }
        }
        return null;
    }

    void render(GuiGraphics g, int x, int y, int mouseX, int mouseY, @Nullable ResourceLocation selectedId) {
        Font font = Minecraft.getInstance().font;
        GuiPaint.box(g, x + LIST_X, y + SEARCH_Y, LIST_W, 13, GuiPaint.BEVEL_LIGHT,
                searchBox.isFocused() ? GuiPaint.ACCENT : GuiPaint.LINE);
        GuiPaint.inset(g, x + LIST_X, y + LIST_Y, LIST_W, LIST_H);
        List<Row> rows = visibleRows();
        if (rows.isEmpty()) {
            GuiText.draw(g, font, tr("none"), x + LIST_X + 5, y + LIST_Y + 6, LIST_W - 10, GuiPaint.MUTED);
        }
        int hover = rowAt(mouseX, mouseY, x, y);
        boolean bar = rows.size() > ROWS;
        int rx = x + LIST_X + 1;
        int rw = LIST_W - 2 - (bar ? 4 : 0);
        for (int i = 0; i < ROWS && scroll + i < rows.size(); i++) {
            Row row = rows.get(scroll + i);
            int ry = y + LIST_Y + 2 + i * ROW_H;
            boolean selected = row.id().equals(selectedId);
            if (selected || hover == scroll + i) {
                g.fill(rx, ry, rx + rw, ry + ROW_H, selected ? GuiPaint.ROW_SELECTED : GuiPaint.ROW_HOVER);
            }
            if (selected) {
                g.fill(rx, ry, rx + 2, ry + ROW_H, GuiPaint.ACCENT);
            }
            ItemStack stack = resultStack(row);
            g.renderItem(stack, rx + 3, ry + 2);
            int tx = rx + 22;
            int tw = rx + rw - 10 - tx;
            Component name = stack.getHoverName();
            if (GuiText.lineCount(font, name, tw, 3) > 1) {
                GuiText.wrap(g, font, name, tx, ry + 1, tw, 2, GuiPaint.FG);
            } else {
                GuiText.draw(g, font, name, tx, ry + 6, tw, GuiPaint.FG);
            }
            GuiPaint.dot(g, rx + rw - 8, ry + 8, RecipePaint.stateColor(row.state()));
        }
        if (bar) {
            int trackY = y + LIST_Y + 2;
            int trackH = ROWS * ROW_H;
            int thumbH = Math.max(8, trackH * ROWS / rows.size());
            int thumbY = trackY + (trackH - thumbH) * scroll / Math.max(1, maxScroll());
            g.fill(x + LIST_X + LIST_W - 4, thumbY, x + LIST_X + LIST_W - 2, thumbY + thumbH, GuiPaint.BEVEL_DARK);
        }

        int pending = menu.snapshot().pending();
        if (pending > 0) {
            Component text = pending == 1 ? tr("pending.one") : tr("pending", pending);
            GuiText.draw(g, font, text, x + LIST_X, y + PENDING_Y, LIST_W, GuiPaint.WARN);
        } else {
            GuiText.draw(g, font, tr("applied"), x + LIST_X, y + PENDING_Y, LIST_W, GuiPaint.MUTED);
        }
    }
}
