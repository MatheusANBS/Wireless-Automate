package io.github.matheusanbs.wirelessautomate.client;

import io.github.matheusanbs.wirelessautomate.block.RouterTier;
import io.github.matheusanbs.wirelessautomate.item.TierCoreItem;
import io.github.matheusanbs.wirelessautomate.menu.ListKind;
import io.github.matheusanbs.wirelessautomate.menu.StorageListMenu;
import io.github.matheusanbs.wirelessautomate.menu.StorageListView;
import io.github.matheusanbs.wirelessautomate.network.Chemicals;
import io.github.matheusanbs.wirelessautomate.packet.StorageActionPayload;
import io.github.matheusanbs.wirelessautomate.storage.StorageKind;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

/**
 * Tela em lista dos armazenamentos por tipo (Baú, Tanque, Tanque Químico): os tipos guardados numa
 * grade com rolagem, busca (nome, ou {@code @mod}) e ordenação (quantidade, nome, mod), a ocupação
 * do tier e o inventário do jogador. A lista vem da {@link StorageListView} e só é refeita quando
 * ela, a busca ou a ordem mudam. O que muda de um armazenamento para outro (desenhar, nomear e
 * clicar num tipo) está nos métodos por {@link ListKind}.
 *
 * <p>A janela é redimensionável: arraste a borda direita, a de baixo ou a alça do canto. Ela cresce
 * em torno do centro (a borda puxada segue o mouse e a oposta se move igual), então continua
 * centralizada; a grade ganha colunas e linhas, o inventário fica centralizado embaixo e o tamanho
 * é lembrado na sessão.
 *
 * <p>Baú: clique numa célula põe uma pilha no cursor; botão direito, meia pilha; Shift + clique,
 * uma pilha no inventário, e Shift + arrastar pela grade, uma pilha de cada tipo por onde passar;
 * com um item no cursor, Shift + duplo clique (como no vanilla), o máximo do tipo que couber no
 * inventário (o cursor fica como está). A rodinha sobre um tipo tira um item
 * (para baixo) ou guarda um do inventário (para cima); sobre um slot do inventário, guarda um dele
 * (para baixo) ou puxa um do Baú (para cima), como o Mouse Tweaks; sobre o vazio, rola a lista. Com um item no cursor, clicar na grade guarda tudo (botão direito: um).
 * Tanques: com um recipiente no cursor (balde, tanque de outro mod), clicar num tipo enche o
 * recipiente com ele, ou o esvazia se ele já estiver cheio; botão direito esvazia um. Shift + clique
 * no inventário guarda a pilha (ou esvazia os recipientes). O servidor valida tudo.
 */
public class StorageListScreen extends AbstractContainerScreen<StorageListMenu<?>> {
    private static final int X0 = 8;
    private static final int HEAD_Y = 6;
    /** Véu de grafite translúcido sobre a célula sob o mouse. */
    private static final int HOVER_VEIL = 0x282A2730;
    /** Quantidade no canto da célula: branco com sombra, como as pilhas do próprio jogo. */
    private static final int COUNT_TEXT = 0xFFFFFFFF;
    private static final int SEARCH_Y = 21;
    private static final int ROW_H = 13;
    private static final int GRID_X = 14;
    private static final int GRID_Y = 38;
    private static final int CELL = 18;
    /** Intervalo do duplo clique, o mesmo dos slots do vanilla. */
    private static final long DOUBLE_CLICK_MS = 250;
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
    /** Nome em minúsculas por tipo (as chaves da visão são os mesmos objetos até saírem). */
    private final Map<Object, String> names = new IdentityHashMap<>();
    private List<Object> shown = List.of();
    private int shownVersion = -1;
    private String shownSearch = "";
    private Sort shownSort = sort;
    private int scrollRow;
    private boolean draggingBar;
    /**
     * Shift + arrasto na grade do Baú (como o Mouse Tweaks faz nos slots, que a grade não tem):
     * os tipos já mandados para o inventário neste arrasto, para cada um ir uma vez só.
     */
    private @Nullable List<ItemStack> shiftDragTaken;
    /** O último Shift + clique com item no cursor num tipo do Baú e quando, para o Shift + duplo clique. */
    private ItemStack lastClickKey = ItemStack.EMPTY;
    private long lastClickTime;
    private EditBox searchBox;
    private FlatButton filterButton;
    private FlatButton sortButton;
    private int cols = savedCols;
    private int rows = savedRows;
    /** Arrasto de redimensionar: pela borda direita, pela de baixo ou pela alça do canto. */
    private final ResizeHandle resizeHandle = new ResizeHandle(GRIP, EDGE);

    public StorageListScreen(StorageListMenu<?> menu, Inventory inventory, Component title) {
        this(menu, inventory, title, false);
    }

    /** @param preview sem servidor (captura de desenvolvimento): os cliques não fazem nada */
    public StorageListScreen(StorageListMenu<?> menu, Inventory inventory, Component title, boolean preview) {
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

    @SuppressWarnings("unchecked")
    private StorageListView<Object> view() {
        return (StorageListView<Object>) menu.view();
    }

    private StorageKind storageKind() {
        return menu.kind().storage;
    }

    private boolean items() {
        return storageKind() == StorageKind.CHEST;
    }

    private RouterTier tier() {
        return minecraft != null && minecraft.level != null
                ? RouterTier.values()[StorageListMenu.tierOrdinal(minecraft.level, menu.pos())]
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
                            view().header().filtered() ? GuiPaint.ACCENT : GuiPaint.DISABLED);
                },
                () -> send(StorageActionPayload.Action.OPEN_FILTER, null))
                .tooltip(() -> view().header().filtered() ? tr("filter.on.tooltip") : tr("filter.off.tooltip")));

        sortButton = add(new FlatButton(0, 0, SORT_W, ROW_H, Component.empty(),
                (g, b, hovered) -> paintButton(g, b, hovered, tr("sort." + sort.name().toLowerCase(Locale.ROOT)), 0),
                () -> sort = sort.next())
                .tooltip(() -> tr("sort.tooltip")));

        searchBox = new EditBox(font, 0, 0, 10, 9, tr("search"));
        searchBox.setBordered(false);
        searchBox.setMaxLength(64);
        searchBox.setTextColor(GuiPaint.FG);
        searchBox.setTextShadow(false); // tinta sobre porcelana, sem sombra
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

    private void send(StorageActionPayload.Action action, @Nullable Object key) {
        send(action, key, -1);
    }

    private void send(StorageActionPayload.Action action, @Nullable Object key, int slot) {
        if (!preview) {
            PacketDistributor.sendToServer(new StorageActionPayload(menu.containerId, storageKind(), action,
                    Optional.ofNullable(key), slot));
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
        List<Object> keys = view().keys();
        names.keySet().retainAll(new java.util.HashSet<>(keys));
        List<Object> kept = new ArrayList<>(keys.size());
        for (Object key : keys) {
            if (matches(key, search)) {
                kept.add(key);
            }
        }
        Comparator<Object> byName = Comparator.comparing(this::name);
        Comparator<Object> order = switch (sort) {
            case COUNT -> Comparator.<Object>comparingLong(key -> view().count(key)).reversed().thenComparing(byName);
            case NAME -> byName;
            case MOD -> Comparator.<Object, String>comparing(StorageListScreen::namespace).thenComparing(byName);
        };
        kept.sort(order);
        shown = kept;
        scrollRow = Math.min(scrollRow, maxScroll());
    }

    /** Busca: cada palavra precisa aparecer no nome; {@code @texto} procura no id do mod. */
    private boolean matches(Object key, String search) {
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
    private String name(Object key) {
        return names.computeIfAbsent(key, k -> displayName(k).getString().toLowerCase(Locale.ROOT));
    }

    // ------------------------------------------------------------------ por tipo de armazenamento

    /** Nome do tipo para mostrar: o do item, o do fluido ou o do químico. */
    private static Component displayName(Object key) {
        return switch (key) {
            case ItemStack stack -> stack.getHoverName();
            case FluidStack fluid -> fluid.getHoverName();
            case ResourceLocation id -> Chemicals.name(id);
            default -> Component.literal(String.valueOf(key));
        };
    }

    /** Mod do tipo, para a busca {@code @mod} e a ordem por mod. */
    private static String namespace(Object key) {
        return switch (key) {
            case ItemStack stack -> BuiltInRegistries.ITEM.getKey(stack.getItem()).getNamespace();
            case FluidStack fluid -> BuiltInRegistries.FLUID.getKey(fluid.getFluid()).getNamespace();
            case ResourceLocation id -> id.getNamespace();
            default -> "";
        };
    }

    /** O tipo na célula: o item, ou a textura do fluido ou do químico tingida. */
    private void renderKey(GuiGraphics g, Object key, int x, int y, int trim) {
        switch (key) {
            case ItemStack stack -> g.renderItem(stack, x, y);
            case FluidStack fluid -> GuiPaint.fluid(g, fluid, x, y);
            case ResourceLocation id -> GuiPaint.chemical(g, font, id, x, y, trim);
            default -> {
            }
        }
    }

    /** Quantidade com a unidade para a dica: "Quantidade: 12.600.000" ou "12.600.000 mB". */
    private Component amountLine(long count) {
        return items() ? tr("amount", TierCoreItem.grouped(count))
                : tr("amount.mb", TierCoreItem.grouped(count));
    }

    /** Dica do tipo sob o mouse: a do item (com os componentes) ou o nome, e a quantidade. */
    private void renderKeyTooltip(GuiGraphics g, Object key, int mouseX, int mouseY) {
        List<Component> lines = new ArrayList<>();
        if (key instanceof ItemStack stack) {
            lines.addAll(getTooltipFromContainerItem(stack));
        } else {
            lines.add(displayName(key));
            if (minecraft != null && minecraft.options.advancedItemTooltips) {
                String id = key instanceof FluidStack fluid ? BuiltInRegistries.FLUID.getKey(fluid.getFluid()).toString()
                        : String.valueOf(key);
                lines.add(Component.literal(id).withStyle(ChatFormatting.DARK_GRAY));
            }
        }
        lines.add(amountLine(view().count(key)).copy().withStyle(ChatFormatting.AQUA));
        lines.add(tr(items() ? "click.hint" : "click.hint.tank").withStyle(ChatFormatting.DARK_GRAY));
        if (key instanceof ItemStack stack) {
            g.renderTooltip(font, lines, stack.getTooltipImage(), stack, mouseX, mouseY);
        } else {
            g.renderComponentTooltip(font, lines, mouseX, mouseY);
        }
    }

    /** Quantidade abreviada com a unidade: "12.6M" nos itens, "12.6M mB" nos tanques. */
    private String abbreviated(long value) {
        return items() ? RateFormat.abbreviate(value) : RateFormat.abbreviate(value) + " mB";
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
        int lines = (shown.size() + cols - 1) / cols;
        return Math.max(0, lines - rows);
    }

    /** O tipo na célula sob o mouse, ou {@code null}. */
    private @Nullable Object keyAt(double mouseX, double mouseY) {
        int col = (int) Math.floor((mouseX - leftPos - GRID_X + 1) / CELL);
        int row = (int) Math.floor((mouseY - topPos - GRID_Y + 1) / CELL);
        if (col < 0 || col >= cols || row < 0 || row >= rows) {
            return null;
        }
        int index = (scrollRow + row) * cols + col;
        return index < shown.size() ? shown.get(index) : null;
    }

    /** Um tipo da lista como ingrediente, com o canto da célula dele (para o JEI). */
    public record Hovered(Object ingredient, int x, int y) {
    }

    /**
     * O tipo sob o mouse como ingrediente do JEI (item, fluido ou {@code ChemicalStack}), para os atalhos dele
     * (receita, usos, favoritar) funcionarem na lista; {@code null} fora da grade ou num químico que não existe.
     */
    public @Nullable Hovered ingredientAt(double mouseX, double mouseY) {
        Object key = keyAt(mouseX, mouseY);
        Object ingredient = switch (key) {
            case null -> null;
            case ItemStack stack -> stack.copyWithCount(1);
            case FluidStack fluid -> fluid.copyWithAmount(1_000);
            case ResourceLocation id -> Chemicals.ingredient(id);
            default -> null;
        };
        if (ingredient == null) {
            return null;
        }
        int col = (int) Math.floor((mouseX - leftPos - GRID_X + 1) / CELL);
        int row = (int) Math.floor((mouseY - topPos - GRID_Y + 1) / CELL);
        return new Hovered(ingredient, leftPos + GRID_X + col * CELL, topPos + GRID_Y + row * CELL);
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

    /**
     * Redimensiona em torno do centro: a borda arrastada segue o mouse e a oposta se move igual,
     * espelhada, então o painel continua centralizado e a borda sob o cursor é sempre a que se puxa.
     * O tamanho anda de célula em célula (18 px), sempre pelo mais perto do mouse.
     */
    private void dragResize(double mouseX, double mouseY) {
        int newCols = cols;
        int newRows = rows;
        if (resizeHandle.changesWidth()) {
            double wanted = resizeHandle.wantedWidth(mouseX);
            newCols = (int) Math.round((wanted - FIXED_W) / CELL);
            newCols = Math.max(MIN_COLS, Math.min(newCols, maxCols()));
        }
        if (resizeHandle.changesHeight()) {
            double wanted = resizeHandle.wantedHeight(mouseY);
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
        resizeHandle.end();
        super.resize(minecraft, width, height);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && resizeHandle.begin(mouseX, mouseY, leftPos, topPos, imageWidth, imageHeight)) {
            return true;
        }
        if (inGrid(mouseX, mouseY) && (button == 0 || button == 1)) {
            setFocused(null);
            Object key = keyAt(mouseX, mouseY);
            // Com um item no cursor, Shift + clique num tipo do Baú manda uma pilha dele para o
            // inventário (o cursor fica como está, como no vanilla), e Shift + duplo clique, o máximo
            // do tipo que couber. Sem item no cursor, o duplo clique não faz nada a mais, para não
            // encher o inventário sem querer.
            if (items() && button == 0 && key instanceof ItemStack stack && Screen.hasShiftDown()
                    && !menu.getCarried().isEmpty()) {
                long now = Util.getMillis();
                boolean doubleClick = now - lastClickTime < DOUBLE_CLICK_MS
                        && ItemStack.isSameItemSameComponents(lastClickKey, stack);
                lastClickKey = doubleClick ? ItemStack.EMPTY : stack;
                lastClickTime = now;
                send(doubleClick ? StorageActionPayload.Action.TAKE_ALL_TO_INVENTORY
                        : StorageActionPayload.Action.TAKE_TO_INVENTORY, stack);
                return true;
            }
            if (!menu.getCarried().isEmpty()) {
                // Tanques: clique num tipo enche o recipiente com ele (ou o esvazia, se cheio).
                if (!items() && button == 0 && key != null) {
                    send(StorageActionPayload.Action.TAKE_STACK, key);
                } else {
                    send(button == 0 ? StorageActionPayload.Action.INSERT_CARRIED
                            : StorageActionPayload.Action.INSERT_CARRIED_ONE, null);
                }
                return true;
            }
            if (key != null && items()) {
                StorageActionPayload.Action action = button == 1 ? StorageActionPayload.Action.TAKE_HALF
                        : Screen.hasShiftDown() ? StorageActionPayload.Action.TAKE_TO_INVENTORY
                        : StorageActionPayload.Action.TAKE_STACK;
                send(action, key);
                if (action == StorageActionPayload.Action.TAKE_TO_INVENTORY) {
                    shiftDragTaken = new ArrayList<>(List.of((ItemStack) key));
                }
            } else if (items() && button == 0 && Screen.hasShiftDown()) {
                shiftDragTaken = new ArrayList<>();
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
        if (resizeHandle.dragging()) {
            dragResize(mouseX, mouseY);
            return true;
        }
        if (draggingBar) {
            dragBar(mouseY);
            return true;
        }
        if (shiftDragTaken != null && button == 0) {
            if (Screen.hasShiftDown() && menu.getCarried().isEmpty() && inGrid(mouseX, mouseY)
                    && keyAt(mouseX, mouseY) instanceof ItemStack key
                    && shiftDragTaken.stream().noneMatch(taken -> ItemStack.isSameItemSameComponents(taken, key))) {
                shiftDragTaken.add(key);
                send(StorageActionPayload.Action.TAKE_TO_INVENTORY, key);
            }
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (resizeHandle.dragging()) {
            resizeHandle.end();
            return true;
        }
        if (draggingBar) {
            draggingBar = false;
            return true;
        }
        if (shiftDragTaken != null && button == 0) {
            shiftDragTaken = null;
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
        // Rodinha do Baú, no sentido do Mouse Tweaks: para baixo empurra um item para o outro lado
        // (da grade para o inventário, do slot para o Baú), para cima puxa um de volta. Sobre uma
        // célula vazia ou a barra, a rodinha rola a lista.
        if (items() && scrollY != 0) {
            boolean push = scrollY < 0;
            if (inGrid(mouseX, mouseY) && keyAt(mouseX, mouseY) instanceof ItemStack key) {
                send(push ? StorageActionPayload.Action.TAKE_ONE_TO_INVENTORY
                        : StorageActionPayload.Action.INSERT_ONE_FROM_INVENTORY, key);
                return true;
            }
            if (hoveredSlot != null && hoveredSlot.hasItem()) {
                send(push ? StorageActionPayload.Action.INSERT_ONE_FROM_SLOT
                        : StorageActionPayload.Action.TAKE_ONE_TO_SLOT, null, hoveredSlot.index);
                return true;
            }
        }
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
        if (!resizeHandle.dragging()
                && resizeHandle.at(mouseX, mouseY, leftPos, topPos, imageWidth, imageHeight) != null) {
            setTooltipForNextRenderPass(tr("resize.tooltip", cols, rows));
            return;
        }
        Object key = (menu.getCarried().isEmpty() || !items()) && !resizeHandle.dragging() ? keyAt(mouseX, mouseY) : null;
        if (key != null) {
            renderKeyTooltip(g, key, mouseX, mouseY);
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

        // cabeçalho: nome e tier, sem passar do botão Filtro (o nome é cortado com reticências)
        Component tierName = Component.translatable(tier.translationKey());
        int pillW = font.width(tierName) + 8;
        int titleMax = Math.max(20, filterButton.getX() - 6 - (x + X0) - pillW - 6);
        FormattedCharSequence titleText = GuiPaint.ellipsize(font, title, titleMax);
        GuiPaint.text(g, font, titleText, x + X0, y + HEAD_Y + 3, GuiPaint.FG);
        int pillX = x + X0 + font.width(titleText) + 6;
        GuiPaint.pill(g, pillX, y + HEAD_Y + 1, pillW, 11, GuiPaint.PANEL, trim);
        GuiPaint.text(g, font, tierName, pillX + 4, y + HEAD_Y + 3, GuiPaint.FG);

        // busca
        GuiPaint.box(g, x + X0, y + SEARCH_Y, searchBox.getWidth() + 8, ROW_H, GuiPaint.INSET,
                searchBox.isFocused() ? GuiPaint.ACCENT : GuiPaint.LINE);

        // grade
        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < cols; col++) {
                GuiPaint.slot(g, x + GRID_X - 1 + col * CELL, y + GRID_Y - 1 + row * CELL);
            }
        }
        int first = scrollRow * cols;
        for (int i = 0; i < rows * cols && first + i < shown.size(); i++) {
            Object key = shown.get(first + i);
            int cx = x + GRID_X + (i % cols) * CELL;
            int cy = y + GRID_Y + (i / cols) * CELL;
            renderKey(g, key, cx, cy, trim);
            drawCount(g, view().count(key), cx, cy);
        }
        Object hovered = keyAt(mouseX, mouseY);
        if (hovered != null || inGrid(mouseX, mouseY) && !menu.getCarried().isEmpty()) {
            int col = (int) Math.floor((mouseX - x - GRID_X + 1) / (double) CELL);
            int row = (int) Math.floor((mouseY - y - GRID_Y + 1) / (double) CELL);
            int hx = x + GRID_X + col * CELL;
            int hy = y + GRID_Y + row * CELL;
            g.fill(hx, hy, hx + 16, hy + 16, HOVER_VEIL);
        }
        if (shown.isEmpty()) {
            // Quebrado em linhas dentro da grade: na largura mínima a frase não cabe numa só.
            Component empty = view().types() > 0 ? tr("no_match") : tr(items() ? "empty" : "empty.tank");
            List<FormattedCharSequence> lines = font.split(empty, cols * CELL - 8);
            int lineY = y + GRID_Y + (rows * CELL - lines.size() * 10) / 2;
            for (FormattedCharSequence line : lines) {
                GuiPaint.text(g, font, line, x + GRID_X + (cols * CELL - font.width(line)) / 2, lineY, GuiPaint.MUTED);
                lineY += 10;
            }
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
                    draggingBar || onBar(mouseX, mouseY) ? GuiPaint.ACCENT : GuiPaint.BEVEL_DARK);
        }

        // ocupação
        StorageListView.Header header = view().header();
        Component usage = header.capacity() <= 0
                ? tr("usage.unlimited", abbreviated(header.total()), TierCoreItem.grouped(view().types()))
                : tr("usage", abbreviated(header.total()), abbreviated(header.capacity()),
                        TierCoreItem.grouped(view().types()));
        // Cortada com reticências antes da porcentagem, se não couber.
        int usageMax = barX() + 4 - GRID_X + 1 - (header.capacity() > 0 ? 30 : 0);
        GuiPaint.text(g, font, GuiPaint.ellipsize(font, usage, usageMax), x + GRID_X - 1, y + usageY(), GuiPaint.MUTED);
        if (header.capacity() > 0) {
            Component percent = Component.literal(percent(header.total(), header.capacity()));
            GuiPaint.textRight(g, font, percent, x + barX() + 4, y + usageY(), GuiPaint.FG);
            int barX = x + GRID_X - 1;
            int barW = barX() + 4 - GRID_X + 1;
            g.fill(barX, y + usageY() + 10, barX + barW, y + usageY() + 12, GuiPaint.INSET);
            int filled = (int) Math.min(barW, Math.round((double) barW * header.total() / header.capacity()));
            g.fill(barX, y + usageY() + 10, barX + filled, y + usageY() + 12, usageColor(header.total(), header.capacity()));
        }

        // alça de redimensionar: três riscos na diagonal, acesos com o mouse em cima ou arrastando
        ResizeGrip.renderDotted(g, x, y, imageWidth, imageHeight,
                resizeHandle.hover(mouseX, mouseY, x, y, imageWidth, imageHeight), GuiPaint.ACCENT);

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

    /** Quantidade abreviada no canto da célula, em meia escala, por cima do tipo (mB nos tanques). */
    private void drawCount(GuiGraphics g, long count, int cx, int cy) {
        if (count <= 1 && items()) {
            return;
        }
        String text = RateFormat.abbreviate(count);
        g.pose().pushPose();
        g.pose().translate(0, 0, 200);
        g.pose().scale(0.5f, 0.5f, 1f);
        int tx = (cx + 16) * 2 - font.width(text) - 1;
        int ty = (cy + 16) * 2 - 8;
        g.drawString(font, text, tx, ty, COUNT_TEXT, true);
        g.pose().popPose();
    }

    /** Cor da barra de ocupação pelo estado: ok até 60%, atenção até 90%, erro acima. */
    private static int usageColor(long total, long capacity) {
        double used = (double) total / capacity;
        return used < 0.6 ? GuiPaint.OK : used < 0.9 ? GuiPaint.WARN : GuiPaint.DANGER;
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
