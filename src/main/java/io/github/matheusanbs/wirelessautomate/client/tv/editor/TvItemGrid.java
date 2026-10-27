package io.github.matheusanbs.wirelessautomate.client.tv.editor;

import io.github.matheusanbs.wirelessautomate.client.GuiPaint;
import io.github.matheusanbs.wirelessautomate.client.GuiText;
import io.github.matheusanbs.wirelessautomate.packet.TvStorageContentsPayload;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

/**
 * A grade com os itens do Baú ligado ({@link TvStorageClient}, pedidos ao servidor só com o editor aberto), com o campo de
 * busca no topo (o servidor filtra por nome e id, e a grade mostra "N de TOTAL" quando cortou): seis por
 * linha, três linhas à vista e a roda rola; o item escolhido do dado fica marcado e o clique escolhe (outro clique no
 * mesmo o tira). Os {@code ItemStack} são criados quando a resposta chega, não por quadro; sem resposta, uma linha
 * diz que está carregando, e com o Baú descarregado ou vazio, o que falta. A dica da célula tem o nome e a
 * quantidade.
 */
final class TvItemGrid extends TvControl {
    static final int CELL = 18;
    static final int COLUMNS = 6;
    static final int ROWS = 3;
    private static final int SEARCH_H = TvTextField.H;
    private static final int GRID_Y = SEARCH_H + 2;
    private static final int GRID_H = ROWS * CELL + 2;
    static final int HEIGHT = GRID_Y + GRID_H + 10;
    private static final int MAX_QUERY = 48;
    private static final long DEBOUNCE_MS = 150;

    private final Font font;
    private final Supplier<GlobalPos> chest;
    private final Supplier<Optional<ResourceLocation>> selected;
    private final Consumer<Optional<ResourceLocation>> choose;
    private final Consumer<String> search;
    private final TvTextField searchField;
    private String query;
    private @Nullable String pending;
    private long pendingAt;
    private @Nullable GuiText.Fitted footer;
    private int footerShown = -1;
    private int footerTotal = -1;
    private ItemStack[] stacks = new ItemStack[0];
    private long[] counts = new long[0];
    private int builtVersion = -1;
    private @Nullable GlobalPos builtChest;
    private Optional<ResourceLocation> current = Optional.empty();
    private int scroll;
    private @Nullable GuiText.Fitted status;
    private String statusKey = "";

    TvItemGrid(Font font, String id, int x, int y, Supplier<GlobalPos> chest,
            Supplier<Optional<ResourceLocation>> selected, Consumer<Optional<ResourceLocation>> choose,
            Supplier<String> query, Consumer<String> search) {
        super(id, x, y, COLUMNS * CELL + 2, HEIGHT);
        this.font = font;
        this.search = search;
        this.query = query.get();
        this.searchField = new TvTextField(font, id + ".search", x, y, w, MAX_QUERY, null,
                TvPropsCtx.key("data.items.search"), () -> this.query, this::typed);
        this.chest = chest;
        this.selected = selected;
        this.choose = choose;
        refresh();
    }

    /** Cada letra guarda o texto; o pedido ao servidor sai {@link #DEBOUNCE_MS} depois da última, no desenho. */
    private void typed(String text) {
        query = text;
        pending = text;
        pendingAt = System.currentTimeMillis();
    }

    @Override
    void place(int originX, int originY) {
        super.place(originX, originY);
        searchField.place(originX, originY);
    }

    @Override
    void refresh() {
        searchField.refresh();
        current = selected.get();
        GlobalPos wanted = chest.get();
        if (builtVersion == TvStorageClient.version() && java.util.Objects.equals(wanted, builtChest)) {
            return;
        }
        builtVersion = TvStorageClient.version();
        builtChest = wanted;
        List<TvStorageContentsPayload.Entry> items = TvStorageClient.itemsOf(wanted);
        stacks = new ItemStack[items.size()];
        counts = new long[items.size()];
        for (int i = 0; i < stacks.length; i++) {
            Item item = BuiltInRegistries.ITEM.get(items.get(i).item());
            stacks[i] = item == Items.AIR ? ItemStack.EMPTY : new ItemStack(item);
            counts[i] = items.get(i).count();
        }
        scroll = Mth.clamp(scroll, 0, maxScroll());
        int total = TvStorageClient.totalOf(wanted);
        if (items.size() != footerShown || total != footerTotal) {
            footerShown = items.size();
            footerTotal = total;
            footer = total > items.size()
                    ? GuiText.fit(font, TvPropsCtx.key("data.items.count", items.size(), total), w) : null;
        }
        String key = wanted == null ? "pick" : !TvStorageClient.answeredAt(wanted) ? "loading"
                : !TvStorageClient.availableAt(wanted) ? "unavailable" : items.isEmpty() ? (query.isEmpty() ? "empty" : "none") : "";
        if (!key.equals(statusKey)) {
            statusKey = key;
            status = key.isEmpty() ? null : GuiText.fit(font, TvPropsCtx.key("data.items." + key), w - 8);
        }
    }

    private int maxScroll() {
        return Math.max(0, (stacks.length + COLUMNS - 1) / COLUMNS - ROWS);
    }

    @Override
    void render(GuiGraphics g, int mouseX, int mouseY) {
        if (pending != null && System.currentTimeMillis() - pendingAt >= DEBOUNCE_MS) {
            String text = pending;
            pending = null;
            search.accept(text);
        }
        searchField.render(g, mouseX, mouseY);
        int gridY = ay + GRID_Y;
        GuiPaint.inset(g, ax, gridY, w, GRID_H);
        if (footer != null) {
            GuiText.draw(g, font, footer, ax, gridY + GRID_H + 1, GuiPaint.MUTED);
        }
        if (status != null) {
            GuiText.draw(g, font, status, ax + 4, gridY + 5, GuiPaint.MUTED);
            return;
        }
        int first = scroll * COLUMNS;
        int end = Math.min(stacks.length, first + COLUMNS * ROWS);
        for (int i = first; i < end; i++) {
            int cx = ax + 1 + (i - first) % COLUMNS * CELL;
            int cy = gridY + 1 + (i - first) / COLUMNS * CELL;
            boolean chosen = current.isPresent() && current.get().equals(BuiltInRegistries.ITEM.getKey(
                    stacks[i].getItem()));
            if (chosen) {
                g.fill(cx, cy, cx + CELL, cy + CELL, GuiPaint.ROW_SELECTED);
                GuiPaint.outline(g, cx, cy, CELL, CELL, GuiPaint.ACCENT);
            } else if (mouseX >= cx && mouseX < cx + CELL && mouseY >= cy && mouseY < cy + CELL) {
                g.fill(cx, cy, cx + CELL, cy + CELL, GuiPaint.ROW_HOVER);
            }
            g.renderItem(stacks[i], cx + 1, cy + 1);
        }
        if (maxScroll() > 0) {
            int track = GRID_H - 2;
            int rowsTotal = (stacks.length + COLUMNS - 1) / COLUMNS;
            int thumb = Math.max(6, track * ROWS / rowsTotal);
            int thumbY = gridY + 1 + (track - thumb) * scroll / maxScroll();
            g.fill(ax + w - 2, thumbY, ax + w - 1, thumbY + thumb, GuiPaint.BUTTON_BORDER);
        }
    }

    private int indexAt(double mouseX, double mouseY) {
        int gridY = ay + GRID_Y;
        if (mouseX < ax || mouseX >= ax + w || mouseY < gridY || mouseY >= gridY + GRID_H || status != null) {
            return -1;
        }
        int col = (int) (mouseX - ax - 1) / CELL;
        int row = (int) (mouseY - gridY - 1) / CELL;
        int index = (scroll + row) * COLUMNS + col;
        return col < COLUMNS && row < ROWS && index < stacks.length ? index : -1;
    }

    @Override
    boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (searchField.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        if (!contains(mouseX, mouseY)) {
            return false;
        }
        int index = indexAt(mouseX, mouseY);
        if (index >= 0) {
            ResourceLocation id = BuiltInRegistries.ITEM.getKey(stacks[index].getItem());
            choose.accept(current.isPresent() && current.get().equals(id) ? Optional.empty() : Optional.of(id));
        }
        return true;
    }

    @Override
    boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (!contains(mouseX, mouseY) || mouseY < ay + GRID_Y || maxScroll() == 0) {
            return false;
        }
        scroll = Mth.clamp(scroll - (int) Math.signum(delta), 0, maxScroll());
        return true;
    }

    @Override
    boolean wantsKeys() {
        return searchField.wantsKeys();
    }

    @Override
    void blur() {
        searchField.blur();
    }

    @Override
    boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return searchField.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    boolean charTyped(char c, int modifiers) {
        return searchField.charTyped(c, modifiers);
    }

    @Override
    @Nullable Component tooltip(double mouseX, double mouseY) {
        int index = indexAt(mouseX, mouseY);
        if (index < 0) {
            return null;
        }
        return Component.empty().append(stacks[index].getHoverName()).append(" · " + counts[index]);
    }

    /** O centro da célula de um item, se está à vista (gancho do e2e). */
    int @Nullable [] centerOf(ResourceLocation id) {
        int first = scroll * COLUMNS;
        for (int i = first; i < Math.min(stacks.length, first + COLUMNS * ROWS); i++) {
            if (BuiltInRegistries.ITEM.getKey(stacks[i].getItem()).equals(id)) {
                return new int[] {ax + 1 + (i - first) % COLUMNS * CELL + CELL / 2,
                        ay + GRID_Y + 1 + (i - first) / COLUMNS * CELL + CELL / 2};
            }
        }
        return null;
    }

    /** O centro do campo de busca (gancho do e2e). */
    int[] searchCenter() {
        return new int[] {ax + w / 2, ay + SEARCH_H / 2};
    }

    /** O retângulo da grade na tela, para o JEI arrastar um item até ela. */
    int[] bounds() {
        return new int[] {ax, ay, w, h};
    }
}
