package io.github.matheusanbs.wirelessautomate.client.tablet.networks;

import static io.github.matheusanbs.wirelessautomate.client.tablet.networks.NetworksText.tr;

import io.github.matheusanbs.wirelessautomate.client.GuiPaint;
import io.github.matheusanbs.wirelessautomate.client.GuiThemes;
import io.github.matheusanbs.wirelessautomate.client.PanelPaint;
import io.github.matheusanbs.wirelessautomate.client.tablet.TabletLayout;
import io.github.matheusanbs.wirelessautomate.client.tablet.kit.TabletModal;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.Consumer;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

/**
 * A escolha do ícone do grupo (mockup {@code popup_icone}): logo abaixo do slot do ícone, o campo de busca e uma grade
 * 8 × 3 de itens que a roda rola, com "ou arraste um item do JEI" embaixo. Sem texto, a grade mostra o ícone atual e os
 * itens distintos do inventário do jogador; com 2 letras ou mais, os itens do registro que casam ({@link IconSearch}),
 * 150 ms depois da última tecla. A lista, os {@code ItemStack}s e os nomes são montados aí, nunca por quadro. Clicar
 * num item o escolhe; clicar no escolhido tira a escolha (o grupo volta ao ícone da primeira máquina). Um item solto
 * do JEI na janela ({@link #accept}) também escolhe.
 */
final class IconModal implements TabletModal {
    static final int COLS = 8;
    static final int ROWS = 3;
    static final int CELL = 18;
    static final int W = COLS * CELL + 14;
    static final int H = 22 + ROWS * CELL + 20;
    private static final long DELAY_MS = 150;

    private final int slotX;
    private final int slotY;
    private final int x;
    private final int y;
    private final @Nullable ResourceLocation chosen;
    private final ItemStack shown;
    private final Inventory inventory;
    private final Consumer<@Nullable ResourceLocation> choose;
    private final EditBox box;
    private final Component hint = tr("nets.icon.search");
    private final Component jei = tr("nets.icon.jei");
    private final IconCatalog catalog = new IconCatalog();
    private ItemStack[] cells = new ItemStack[0];
    private ResourceLocation[] ids = new ResourceLocation[0];
    private Component[] names = new Component[0];
    private int chosenIndex = -1;
    private int scroll;
    /** Quando o texto mudou pela última vez (0: a grade já é a do texto). */
    private long dirtyAt;
    private int inkVersion = -1;
    private boolean done;
    private long lastSearchNanos;

    IconModal(Font font, int left, int top, @Nullable ResourceLocation chosen, ItemStack shown, Inventory inventory,
            Consumer<@Nullable ResourceLocation> choose) {
        this.slotX = left + TabletLayout.PANEL_X0 - 1;
        this.slotY = top + 29;
        this.x = slotX;
        this.y = top + 50;
        this.chosen = chosen;
        this.shown = shown;
        this.inventory = inventory;
        this.choose = choose;
        box = new EditBox(font, x + 11, y + 9, W - 22, 8, hint);
        box.setBordered(false);
        box.setMaxLength(64);
        box.setTextShadow(false);
        box.setResponder(text -> dirtyAt = System.currentTimeMillis());
        box.setFocused(true);
        rebuild();
    }

    // ------------------------------------------------------------------ a lista

    private void rebuild() {
        dirtyAt = 0;
        scroll = 0;
        String query = box.getValue();
        if (IconSearch.searches(query)) {
            long start = System.nanoTime();
            fill(catalog.find(query));
            lastSearchNanos = System.nanoTime() - start;
        } else {
            fill(inventoryItems());
        }
    }

    /** O ícone atual e os itens distintos do inventário (os 36 slots), sem tocar no registro. */
    private Item[] inventoryItems() {
        Set<Item> items = new LinkedHashSet<>();
        if (!shown.isEmpty()) {
            items.add(shown.getItem());
        }
        for (ItemStack stack : inventory.items) {
            if (!stack.isEmpty()) {
                items.add(stack.getItem());
            }
        }
        return items.toArray(new Item[0]);
    }

    private void fill(Item[] items) {
        cells = new ItemStack[items.length];
        ids = new ResourceLocation[items.length];
        names = new Component[items.length];
        chosenIndex = -1;
        for (int i = 0; i < items.length; i++) {
            cells[i] = new ItemStack(items[i]);
            ids[i] = BuiltInRegistries.ITEM.getKey(items[i]);
            names[i] = cells[i].getHoverName();
            if (ids[i].equals(chosen)) {
                chosenIndex = i;
            }
        }
    }

    private int maxScroll() {
        return Math.max(0, (cells.length + COLS - 1) / COLS - ROWS);
    }

    /** O índice na lista da célula sob o mouse, ou -1. */
    private int cellAt(double mx, double my) {
        int gx = x + 7;
        int gy = y + 24;
        if (mx < gx || my < gy || mx >= gx + COLS * CELL || my >= gy + ROWS * CELL) {
            return -1;
        }
        int i = (scroll + (int) (my - gy) / CELL) * COLS + (int) (mx - gx) / CELL;
        return i < cells.length ? i : -1;
    }

    private boolean overField(double mx, double my) {
        return mx >= x + 7 && mx < x + W - 7 && my >= y + 6 && my < y + 20;
    }

    // ------------------------------------------------------------------ desenho

    @Override
    public void render(GuiGraphics g, Font font, int mx, int my) {
        if (dirtyAt != 0 && System.currentTimeMillis() - dirtyAt >= DELAY_MS) {
            rebuild();
        }
        PanelPaint.slot(g, slotX, slotY, 18, false);
        g.renderItem(shown, slotX + 1, slotY + 1);
        PanelPaint.window(g, x, y, W, H);
        PanelPaint.field(g, x + 7, y + 6, W - 14, 14, box.isFocused());
        if (inkVersion != GuiThemes.version()) {
            inkVersion = GuiThemes.version();
            GuiPaint.ink(box);
            box.setHint(hint.copy().withColor(GuiPaint.MUTED));
        }
        box.render(g, mx, my, 0f);
        int first = scroll * COLS;
        for (int c = 0; c < COLS * ROWS; c++) {
            int cx = x + 7 + c % COLS * CELL;
            int cy = y + 24 + c / COLS * CELL;
            int i = first + c;
            PanelPaint.slot(g, cx, cy, CELL, i == chosenIndex);
            if (i < cells.length) {
                g.renderItem(cells[i], cx + 1, cy + 1);
            }
        }
        GuiPaint.text(g, font, jei, x + 7, y + H - 14, GuiPaint.MUTED);
    }

    // ------------------------------------------------------------------ entrada

    @Override
    public boolean contains(double mx, double my) {
        return mx >= x && mx < x + W && my >= y && my < y + H;
    }

    @Override
    public void mouseClicked(double mx, double my, int button) {
        if (overField(mx, my)) {
            box.setFocused(true);
            box.mouseClicked(mx, my, button);
            return;
        }
        int i = cellAt(mx, my);
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && i >= 0) {
            choose.accept(i == chosenIndex ? null : ids[i]);
            done = true;
        }
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        scroll = Math.max(0, Math.min(maxScroll(), scroll - (int) Math.signum(delta)));
        return true;
    }

    /** Esc fica para a camada (fecha); o resto é do campo, que sempre tem o foco de volta ao digitar. */
    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            return false;
        }
        box.setFocused(true);
        box.keyPressed(key, scan, mods);
        return true;
    }

    @Override
    public boolean charTyped(char c, int mods) {
        box.setFocused(true);
        return box.charTyped(c, mods);
    }

    @Override
    public @Nullable Component tooltip(double mx, double my) {
        int i = cellAt(mx, my);
        return i >= 0 ? names[i] : null;
    }

    @Override
    public boolean done() {
        return done;
    }

    /** Um item solto do JEI na janela: escolhe o item (a anfitriã fecha a janela depois). */
    void accept(ItemStack stack) {
        if (!stack.isEmpty()) {
            choose.accept(BuiltInRegistries.ITEM.getKey(stack.getItem()));
            done = true;
        }
    }

    /** A janela inteira ({@code x, y, w, h}): o JEI solta em qualquer lugar dela. */
    int[] bounds() {
        return new int[] {x, y, W, H};
    }

    // ------------------------------------------------------------------ ganchos do e2e

    /** Centro na tela da célula {@code index} da lista, ou {@code null} fora da lista ou da vista. */
    int @Nullable [] cellCenter(int index) {
        int row = index / COLS - scroll;
        if (index < 0 || index >= cells.length || row < 0 || row >= ROWS) {
            return null;
        }
        return new int[] {x + 7 + index % COLS * CELL + CELL / 2, y + 24 + row * CELL + CELL / 2};
    }

    /** O id do item da célula {@code index}, ou {@code null}; só depois que a espera do texto passou. */
    @Nullable ResourceLocation cellItem(int index) {
        return dirtyAt == 0 && index >= 0 && index < ids.length ? ids[index] : null;
    }

    void searchFocus() {
        box.setFocused(true);
    }

    /** Quanto levou a última consulta ao registro (a primeira inclui montar o catálogo). */
    long lastSearchNanos() {
        return lastSearchNanos;
    }
}
