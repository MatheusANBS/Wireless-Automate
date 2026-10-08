package io.github.matheusanbs.wirelessautomate.client;

import io.github.matheusanbs.wirelessautomate.block.RouterTier;
import io.github.matheusanbs.wirelessautomate.item.TierCoreItem;
import io.github.matheusanbs.wirelessautomate.menu.StorageChestMenu;
import io.github.matheusanbs.wirelessautomate.menu.StorageChestView;
import io.github.matheusanbs.wirelessautomate.packet.StorageActionPayload;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

/**
 * Tela do Baú: a lista de tipos guardados numa grade com rolagem, busca (nome, ou {@code @mod}) e
 * ordenação (quantidade, nome, mod), a ocupação do tier e o inventário do jogador. A lista vem da
 * {@link StorageChestView} e só é refeita quando ela, a busca ou a ordem mudam.
 *
 * <p>A janela é redimensionável: arraste a borda direita, a de baixo ou a alça do canto. Ela cresce
 * em torno do centro (a borda puxada segue o mouse e a oposta se move igual), então continua
 * centralizada; a grade ganha colunas e linhas, o inventário fica centralizado embaixo e o tamanho
 * é lembrado na sessão.
 *
 * <p>Clique numa célula: uma pilha para o cursor; botão direito: meia pilha; Shift + clique: uma
 * pilha para o inventário. Com um item no cursor, clicar na grade guarda tudo (botão direito: um).
 * Shift + clique no inventário guarda a pilha. O servidor valida tudo.
 */
public class StorageChestScreen extends AbstractContainerScreen<StorageChestMenu> {
    private static final int X0 = 8;
    private static final int HEAD_Y = 6;
    private static final int SEARCH_Y = 21;
    private static final int ROW_H = 13;
    private static final int GRID_X = 14;
    private static final int GRID_Y = 38;
    private static final int CELL = 18;
    private static final int SORT_W = 52;
    /** Menor tamanho da grade: a largura do inventário do jogador e três linhas. */
    private static final int MIN_COLS = 9;
    private static final int MIN_ROWS = 3;
    /** Alça de redimensionar no canto e a faixa das bordas que também redimensionam. */
    private static final int GRIP = 9;
    private static final int EDGE = 3;
    /** Altura fixa do painel além das linhas da grade (cabeçalho, ocupação e inventário). */
    private static final int FIXED_H = GRID_Y + 4 + 18 + 82;
    /** Largura fixa além das colunas (margens e barra de rolagem). */
    private static final int FIXED_W = GRID_X + 3 + 4 + 8;

    /** Arrasto de redimensionar: pela borda direita, pela de baixo ou pela alça do canto. */
    private enum Resize {
        WIDTH, HEIGHT, BOTH
    }

    /** Ordem da lista; fica entre aberturas da tela na mesma sessão. */
    private enum Sort {
        COUNT, NAME, MOD;

        Sort next() {
            return values()[(ordinal() + 1) % values().length];
        }
    }

    private static Sort sort = Sort.COUNT;
    private static String lastSearch = "";
    /** Tamanho da grade escolhido pelo jogador; fica entre aberturas da tela na mesma sessão. */
    private static int savedCols = 13;
    private static int savedRows = 6;

    private final boolean preview;
    private final List<FlatButton> buttons = new ArrayList<>();
    private final Map<ItemStack, String> names = new HashMap<>();
    private List<ItemStack> shown = List.of();
    private int shownVersion = -1;
    private String shownSearch = "";
    private Sort shownSort = sort;
    private int scrollRow;
    private boolean draggingBar;
    private EditBox searchBox;
    private FlatButton filterButton;
    private FlatButton sortButton;
    private int cols = savedCols;
    private int rows = savedRows;
    private @Nullable Resize resizing;
    /** Centro da janela no começo do arrasto: ele não se mexe enquanto o tamanho muda. */
    private double resizeCenterX;
    private double resizeCenterY;
    /** Distância do mouse até a borda arrastada no começo, para a borda não pular para o cursor. */
    private double resizeGrabX;
    private double resizeGrabY;

    public StorageChestScreen(StorageChestMenu menu, Inventory inventory, Component title) {
        this(menu, inventory, title, false);
    }

    /** @param preview sem servidor (captura de desenvolvimento): os cliques não fazem nada */
    public StorageChestScreen(StorageChestMenu menu, Inventory inventory, Component title, boolean preview) {
        super(menu, inventory, title);
        this.preview = preview;
        this.inventoryLabelY = -1000;
    }

    // ------------------------------------------------------------------ medidas (mudam com o tamanho)

    private int panelW() {
        return FIXED_W + cols * CELL;
    }

    private int panelH() {
        return FIXED_H + rows * CELL;
    }

    private int x1() {
        return panelW() - 8;
    }

    private int barX() {
        return GRID_X + cols * CELL + 3;
    }

    private int usageY() {
        return GRID_Y + rows * CELL + 4;
    }

    /** Canto do inventário do jogador: centralizado embaixo. */
    private int invX() {
        return (panelW() - 9 * CELL) / 2 + 1;
    }

    private int invY() {
        return usageY() + 18;
    }

    /** Colunas que cabem na janela do jogo, com 4 px de folga de cada lado (o painel é centralizado). */
    private int maxCols() {
        return Math.max(MIN_COLS, (width - 8 - FIXED_W) / CELL);
    }

    private int maxRows() {
        return Math.max(MIN_ROWS, (height - 8 - FIXED_H) / CELL);
    }

    private static MutableComponent tr(String key, Object... args) {
        return Component.translatable("gui.wirelessautomate.storage." + key, args);
    }

    private StorageChestView view() {
        return menu.view();
    }

    private RouterTier tier() {
        return minecraft != null && minecraft.level != null
                ? RouterTier.values()[StorageChestMenu.tierOrdinal(minecraft.level, menu.pos())]
                : RouterTier.BASIC;
    }

    // ------------------------------------------------------------------ montagem

    @Override
    protected void init() {
        cols = Math.max(MIN_COLS, Math.min(savedCols, maxCols()));
        rows = Math.max(MIN_ROWS, Math.min(savedRows, maxRows()));
        imageWidth = panelW();
        imageHeight = panelH();
        super.init();
        buttons.clear();

        Component filterLabel = tr("filter");
        int filterW = font.width(filterLabel) + 20;
        filterButton = add(new FlatButton(0, 0, filterW, ROW_H, filterLabel,
                (g, b, hovered) -> {
                    paintButton(g, b, hovered, filterLabel, 8);
                    GuiPaint.dot(g, b.getX() + 5, b.getY() + 4,
                            view().header().filtered() ? GuiPaint.tierColor(tier()) : GuiPaint.DISABLED);
                },
                () -> send(StorageActionPayload.Action.OPEN_FILTER, ItemStack.EMPTY))
                .tooltip(() -> view().header().filtered() ? tr("filter.on.tooltip") : tr("filter.off.tooltip")));

        sortButton = add(new FlatButton(0, 0, SORT_W, ROW_H, Component.empty(),
                (g, b, hovered) -> paintButton(g, b, hovered, tr("sort." + sort.name().toLowerCase(Locale.ROOT)), 0),
                () -> sort = sort.next())
                .tooltip(() -> tr("sort.tooltip")));

        searchBox = new EditBox(font, 0, 0, 10, 9, tr("search"));
        searchBox.setBordered(false);
        searchBox.setMaxLength(64);
        searchBox.setTextColor(GuiPaint.FG);
        searchBox.setHint(tr("search.hint").copy().withColor(GuiPaint.DISABLED));
        searchBox.setValue(lastSearch);
        searchBox.setResponder(value -> {
            lastSearch = value;
            scrollRow = 0;
        });
        addRenderableWidget(searchBox);
        layout();
    }

    /**
     * Põe o painel no centro da janela do jogo, e os widgets e o inventário do jogador no lugar,
     * para o tamanho atual.
     */
    private void layout() {
        imageWidth = panelW();
        imageHeight = panelH();
        leftPos = (width - imageWidth) / 2;
        topPos = (height - imageHeight) / 2;
        filterButton.setPosition(leftPos + x1() - filterButton.getWidth(), topPos + HEAD_Y);
        sortButton.setPosition(leftPos + x1() - SORT_W, topPos + SEARCH_Y);
        searchBox.setPosition(leftPos + X0 + 4, topPos + SEARCH_Y + 3);
        searchBox.setWidth(x1() - X0 - SORT_W - 12);
        menu.placeInventory(invX(), invY());
        scrollRow = Math.min(scrollRow, maxScroll());
    }

    private FlatButton add(FlatButton button) {
        buttons.add(button);
        return addRenderableWidget(button);
    }

    private void send(StorageActionPayload.Action action, ItemStack key) {
        if (!preview) {
            PacketDistributor.sendToServer(new StorageActionPayload(menu.containerId, action, key));
        }
    }

    // ------------------------------------------------------------------ lista

    /** Refaz a lista mostrada se a visão, a busca ou a ordem mudaram. */
    private void rebuild() {
        String search = searchBox.getValue().trim().toLowerCase(Locale.ROOT);
        if (view().version() == shownVersion && search.equals(shownSearch) && sort == shownSort) {
            return;
        }
        shownVersion = view().version();
        shownSearch = search;
        shownSort = sort;
        List<ItemStack> keys = view().keys();
        List<ItemStack> kept = new ArrayList<>(keys.size());
        for (ItemStack key : keys) {
            if (matches(key, search)) {
                kept.add(key);
            }
        }
        Comparator<ItemStack> byName = Comparator.comparing(this::name);
        Comparator<ItemStack> order = switch (sort) {
            case COUNT -> Comparator.<ItemStack>comparingLong(key -> view().count(key)).reversed().thenComparing(byName);
            case NAME -> byName;
            case MOD -> Comparator.<ItemStack, String>comparing(StorageChestScreen::namespace).thenComparing(byName);
        };
        kept.sort(order);
        shown = kept;
        scrollRow = Math.min(scrollRow, maxScroll());
    }

    /** Busca: cada palavra precisa aparecer no nome; {@code @texto} procura no id do mod. */
    private boolean matches(ItemStack key, String search) {
        if (search.isEmpty()) {
            return true;
        }
        String name = name(key);
        for (String word : search.split("\\s+")) {
            if (word.startsWith("@")) {
                if (!namespace(key).contains(word.substring(1))) {
                    return false;
                }
            } else if (!name.contains(word)) {
                return false;
            }
        }
        return true;
    }

    /** Nome em minúsculas, guardado por tipo (o nome de um item com componentes pode ser caro). */
    private String name(ItemStack key) {
        return names.computeIfAbsent(key, k -> k.getHoverName().getString().toLowerCase(Locale.ROOT));
    }

    private static String namespace(ItemStack key) {
        return BuiltInRegistries.ITEM.getKey(key.getItem()).getNamespace();
    }

    // Para o teste de ponta a ponta (DevEndToEnd).

    int shownCount() {
        rebuild();
        return shown.size();
    }

    EditBox searchBox() {
        return searchBox;
    }

    int cols() {
        return cols;
    }

    int rows() {
        return rows;
    }

    /** Ponto no meio da alça de redimensionar, em coordenadas da tela. */
    int[] gripPoint() {
        return new int[] {leftPos + imageWidth - 4, topPos + imageHeight - 4};
    }

    /** Centro da célula {@code index} da página atual, em coordenadas da tela. */
    int[] cellCenter(int index) {
        return new int[] {leftPos + GRID_X + (index % cols) * CELL + 8, topPos + GRID_Y + (index / cols) * CELL + 8};
    }

    private int maxScroll() {
        int rows = (shown.size() + cols - 1) / cols;
        return Math.max(0, rows - rows);
    }

    /** O tipo na célula sob o mouse, ou {@code null}. */
    private @Nullable ItemStack keyAt(double mouseX, double mouseY) {
        int col = (int) Math.floor((mouseX - leftPos - GRID_X + 1) / CELL);
        int row = (int) Math.floor((mouseY - topPos - GRID_Y + 1) / CELL);
        if (col < 0 || col >= cols || row < 0 || row >= rows) {
            return null;
        }
        int index = (scrollRow + row) * cols + col;
        return index < shown.size() ? shown.get(index) : null;
    }

    private boolean inGrid(double mouseX, double mouseY) {
        return mouseX >= leftPos + GRID_X - 1 && mouseX < leftPos + GRID_X - 1 + cols * CELL
                && mouseY >= topPos + GRID_Y - 1 && mouseY < topPos + GRID_Y - 1 + rows * CELL;
    }

    private boolean onBar(double mouseX, double mouseY) {
        return mouseX >= leftPos + barX() && mouseX < leftPos + barX() + 4
                && mouseY >= topPos + GRID_Y - 1 && mouseY < topPos + GRID_Y - 1 + rows * CELL;
    }

    // ------------------------------------------------------------------ entrada

    /** O que o mouse redimensiona ali, ou {@code null}: a alça do canto, a borda direita ou a de baixo. */
    private @Nullable Resize resizeAt(double mouseX, double mouseY) {
        double right = leftPos + imageWidth;
        double bottom = topPos + imageHeight;
        if (mouseX < leftPos || mouseY < topPos || mouseX >= right || mouseY >= bottom) {
            return null;
        }
        if (mouseX >= right - GRIP && mouseY >= bottom - GRIP) {
            return Resize.BOTH;
        }
        if (mouseX >= right - EDGE) {
            return Resize.WIDTH;
        }
        if (mouseY >= bottom - EDGE) {
            return Resize.HEIGHT;
        }
        return null;
    }

    /**
     * Redimensiona em torno do centro: a borda arrastada segue o mouse e a oposta se move igual,
     * espelhada, então o painel continua centralizado e a borda sob o cursor é sempre a que se puxa.
     * O tamanho anda de célula em célula (18 px), sempre pelo mais perto do mouse.
     */
    private void dragResize(double mouseX, double mouseY) {
        int newCols = cols;
        int newRows = rows;
        if (resizing != Resize.HEIGHT) {
            double wanted = 2 * (mouseX + resizeGrabX - resizeCenterX);
            newCols = (int) Math.round((wanted - FIXED_W) / CELL);
            newCols = Math.max(MIN_COLS, Math.min(newCols, maxCols()));
        }
        if (resizing != Resize.WIDTH) {
            double wanted = 2 * (mouseY + resizeGrabY - resizeCenterY);
            newRows = (int) Math.round((wanted - FIXED_H) / CELL);
            newRows = Math.max(MIN_ROWS, Math.min(newRows, maxRows()));
        }
        if (newCols != cols || newRows != rows) {
            cols = newCols;
            rows = newRows;
            savedCols = cols;
            savedRows = rows;
            layout();
        }
    }

    @Override
    public void resize(net.minecraft.client.Minecraft minecraft, int width, int height) {
        resizing = null;
        super.resize(minecraft, width, height);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        Resize edge = button == 0 ? resizeAt(mouseX, mouseY) : null;
        if (edge != null) {
            resizing = edge;
            resizeCenterX = leftPos + imageWidth / 2.0;
            resizeCenterY = topPos + imageHeight / 2.0;
            resizeGrabX = leftPos + imageWidth - mouseX;
            resizeGrabY = topPos + imageHeight - mouseY;
            return true;
        }
        if (inGrid(mouseX, mouseY) && (button == 0 || button == 1)) {
            setFocused(null);
            if (!menu.getCarried().isEmpty()) {
                send(button == 0 ? StorageActionPayload.Action.INSERT_CARRIED
                        : StorageActionPayload.Action.INSERT_CARRIED_ONE, ItemStack.EMPTY);
                return true;
            }
            ItemStack key = keyAt(mouseX, mouseY);
            if (key != null) {
                StorageActionPayload.Action action = button == 1 ? StorageActionPayload.Action.TAKE_HALF
                        : Screen.hasShiftDown() ? StorageActionPayload.Action.TAKE_TO_INVENTORY
                        : StorageActionPayload.Action.TAKE_STACK;
                send(action, key);
            }
            return true;
        }
        if (button == 0 && onBar(mouseX, mouseY) && maxScroll() > 0) {
            draggingBar = true;
            dragBar(mouseY);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (resizing != null) {
            dragResize(mouseX, mouseY);
            return true;
        }
        if (draggingBar) {
            dragBar(mouseY);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (resizing != null) {
            resizing = null;
            return true;
        }
        if (draggingBar) {
            draggingBar = false;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    private void dragBar(double mouseY) {
        double t = (mouseY - topPos - GRID_Y) / (rows * CELL - 2);
        scrollRow = (int) Math.round(Math.max(0, Math.min(1, t)) * maxScroll());
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if ((inGrid(mouseX, mouseY) || onBar(mouseX, mouseY)) && scrollY != 0) {
            scrollRow = Math.max(0, Math.min(maxScroll(), scrollRow - (int) Math.signum(scrollY)));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (getFocused() == searchBox && searchBox.isFocused()) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE || keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                setFocused(null);
            } else {
                searchBox.keyPressed(keyCode, scanCode, modifiers);
            }
            // nenhuma tecla fecha a tela enquanto se digita (nem a do inventário)
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (getFocused() == searchBox && searchBox.isFocused()) {
            return searchBox.charTyped(codePoint, modifiers);
        }
        return super.charTyped(codePoint, modifiers);
    }

    // ------------------------------------------------------------------ desenho

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        rebuild();
        super.render(g, mouseX, mouseY, partialTick);
        for (FlatButton button : buttons) {
            if (button.visible && button.isHovered()) {
                Component tooltip = button.currentTooltip();
                if (tooltip != null) {
                    setTooltipForNextRenderPass(tooltip);
                }
                return;
            }
        }
        if (resizing == null && resizeAt(mouseX, mouseY) != null) {
            setTooltipForNextRenderPass(tr("resize.tooltip", cols, rows));
            return;
        }
        ItemStack key = menu.getCarried().isEmpty() && resizing == null ? keyAt(mouseX, mouseY) : null;
        if (key != null) {
            List<Component> lines = new ArrayList<>(getTooltipFromContainerItem(key));
            lines.add(tr("amount", TierCoreItem.grouped(view().count(key))).withStyle(ChatFormatting.AQUA));
            lines.add(tr("click.hint").withStyle(ChatFormatting.DARK_GRAY));
            g.renderTooltip(font, lines, key.getTooltipImage(), key, mouseX, mouseY);
            return;
        }
        renderTooltip(g, mouseX, mouseY);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        // os textos são desenhados em renderBg, em coordenadas da tela
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        int x = leftPos;
        int y = topPos;
        RouterTier tier = tier();
        int trim = GuiPaint.tierColor(tier);
        GuiPaint.panel(g, x, y, imageWidth, imageHeight, trim);

        // cabeçalho: nome e tier
        GuiPaint.text(g, font, title, x + X0, y + HEAD_Y + 3, GuiPaint.FG);
        Component tierName = Component.translatable(tier.translationKey());
        int pillX = x + X0 + font.width(title) + 6;
        int pillW = font.width(tierName) + 8;
        GuiPaint.pill(g, pillX, y + HEAD_Y + 1, pillW, 11, GuiPaint.INSET, trim);
        GuiPaint.text(g, font, tierName, pillX + 4, y + HEAD_Y + 3, trim);

        // busca
        GuiPaint.box(g, x + X0, y + SEARCH_Y, searchBox.getWidth() + 8, ROW_H, GuiPaint.INSET,
                searchBox.isFocused() ? trim : GuiPaint.BUTTON_BORDER);

        // grade
        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < cols; col++) {
                GuiPaint.slot(g, x + GRID_X - 1 + col * CELL, y + GRID_Y - 1 + row * CELL);
            }
        }
        int first = scrollRow * cols;
        for (int i = 0; i < rows * cols && first + i < shown.size(); i++) {
            ItemStack key = shown.get(first + i);
            int cx = x + GRID_X + (i % cols) * CELL;
            int cy = y + GRID_Y + (i / cols) * CELL;
            g.renderItem(key, cx, cy);
            drawCount(g, view().count(key), cx, cy);
        }
        ItemStack hovered = keyAt(mouseX, mouseY);
        if (hovered != null || inGrid(mouseX, mouseY) && !menu.getCarried().isEmpty()) {
            int col = (int) Math.floor((mouseX - x - GRID_X + 1) / (double) CELL);
            int row = (int) Math.floor((mouseY - y - GRID_Y + 1) / (double) CELL);
            int hx = x + GRID_X + col * CELL;
            int hy = y + GRID_Y + row * CELL;
            g.fill(hx, hy, hx + 16, hy + 16, 0x50FFFFFF);
        }
        if (shown.isEmpty()) {
            Component empty = view().types() == 0 ? tr("empty") : tr("no_match");
            GuiPaint.textCentered(g, font, empty, x + GRID_X + cols * CELL / 2, y + GRID_Y + rows * CELL / 2 - 4,
                    GuiPaint.MUTED);
        }

        // barra de rolagem
        int barTop = y + GRID_Y - 1;
        int barH = rows * CELL;
        g.fill(x + barX(), barTop, x + barX() + 4, barTop + barH, GuiPaint.INSET);
        int max = maxScroll();
        if (max > 0) {
            int total = max + rows;
            int thumbH = Math.max(8, barH * rows / total);
            int thumbY = barTop + (barH - thumbH) * scrollRow / max;
            g.fill(x + barX(), thumbY, x + barX() + 4, thumbY + thumbH,
                    draggingBar || onBar(mouseX, mouseY) ? trim : GuiPaint.BUTTON_HOVER_BORDER);
        }

        // ocupação
        StorageChestView.Header header = view().header();
        Component usage = header.capacity() <= 0
                ? tr("usage.unlimited", RateFormat.abbreviate(header.total()), TierCoreItem.grouped(view().types()))
                : tr("usage", RateFormat.abbreviate(header.total()), RateFormat.abbreviate(header.capacity()),
                        TierCoreItem.grouped(view().types()));
        GuiPaint.text(g, font, usage, x + GRID_X - 1, y + usageY(), GuiPaint.MUTED);
        if (header.capacity() > 0) {
            Component percent = Component.literal(percent(header.total(), header.capacity()));
            GuiPaint.textRight(g, font, percent, x + barX() + 4, y + usageY(), GuiPaint.FG);
            int barX = x + GRID_X - 1;
            int barW = barX() + 4 - GRID_X + 1;
            g.fill(barX, y + usageY() + 10, barX + barW, y + usageY() + 12, GuiPaint.INSET);
            int filled = (int) Math.min(barW, Math.round((double) barW * header.total() / header.capacity()));
            g.fill(barX, y + usageY() + 10, barX + filled, y + usageY() + 12, trim);
        }

        // alça de redimensionar: três riscos na diagonal, acesos com o mouse em cima ou arrastando
        Resize hover = resizing != null ? resizing : resizeAt(mouseX, mouseY);
        int grip = hover != null ? trim : GuiPaint.BUTTON_HOVER_BORDER;
        int gx = x + imageWidth - 4;
        int gy = y + imageHeight - 4;
        for (int i = 0; i < 3; i++) {
            int d = 2 + i * 2;
            for (int k = 0; k <= d; k += 1) {
                if (k % 2 == 0) {
                    g.fill(gx - d + k, gy - k, gx - d + k + 1, gy - k + 1, grip);
                }
            }
        }
        if (hover == Resize.WIDTH || hover == Resize.BOTH) {
            g.fill(x + imageWidth - 3, y + 3, x + imageWidth - 2, y + imageHeight - 3, grip);
        }
        if (hover == Resize.HEIGHT || hover == Resize.BOTH) {
            g.fill(x + 3, y + imageHeight - 3, x + imageWidth - 3, y + imageHeight - 2, grip);
        }

        // inventário do jogador
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                GuiPaint.slot(g, x + invX() - 1 + col * CELL,
                        y + invY() - 1 + row * CELL);
            }
        }
        for (int col = 0; col < 9; col++) {
            GuiPaint.slot(g, x + invX() - 1 + col * CELL, y + invY() + 57);
        }
    }

    /** Quantidade abreviada no canto da célula, em meia escala, por cima do item. */
    private void drawCount(GuiGraphics g, long count, int cx, int cy) {
        if (count <= 1) {
            return;
        }
        String text = RateFormat.abbreviate(count);
        g.pose().pushPose();
        g.pose().translate(0, 0, 200);
        g.pose().scale(0.5f, 0.5f, 1f);
        int tx = (cx + 16) * 2 - font.width(text) - 1;
        int ty = (cy + 16) * 2 - 8;
        g.drawString(font, text, tx, ty, 0xFFFFFFFF, true);
        g.pose().popPose();
    }

    private static String percent(long total, long capacity) {
        double value = 100.0 * total / capacity;
        if (total > 0 && value < 1) {
            return "<1%";
        }
        return Math.min(100, Math.round(value)) + "%";
    }

    private void paintButton(GuiGraphics g, FlatButton b, boolean hovered, Component text, int shift) {
        GuiPaint.box(g, b.getX(), b.getY(), b.getWidth(), b.getHeight(), GuiPaint.BUTTON,
                hovered ? GuiPaint.BUTTON_HOVER_BORDER : GuiPaint.BUTTON_BORDER);
        GuiPaint.textCentered(g, font, text, b.getX() + (b.getWidth() + shift) / 2 + 1, b.getY() + (b.getHeight() - 8) / 2,
                GuiPaint.FG);
    }
}
