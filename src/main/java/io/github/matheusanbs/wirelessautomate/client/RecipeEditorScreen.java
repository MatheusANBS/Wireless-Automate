package io.github.matheusanbs.wirelessautomate.client;

import io.github.matheusanbs.wirelessautomate.block.RouterTier;
import io.github.matheusanbs.wirelessautomate.menu.RecipeEditorMenu;
import io.github.matheusanbs.wirelessautomate.menu.RecipeEditorSnapshot;
import io.github.matheusanbs.wirelessautomate.menu.RecipeEditorSnapshot.Row;
import io.github.matheusanbs.wirelessautomate.packet.RecipeEditorActionPayload;
import io.github.matheusanbs.wirelessautomate.packet.RecipeEditorActionPayload.Action;
import io.github.matheusanbs.wirelessautomate.recipe.edit.RecipeDraft;
import io.github.matheusanbs.wirelessautomate.recipe.edit.RecipeEditor;
import io.github.matheusanbs.wirelessautomate.recipe.edit.RecipeSlot;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

/**
 * Tela do editor de receitas ({@code /wa recipes}), pelo mockup aprovado
 * ({@code docs/preview/editor-de-receitas.html}): à esquerda a busca, a lista das receitas do mod e a
 * recarga pendente; à direita a receita escolhida (grade 3×3 de slots fantasmas, resultado com a
 * quantidade, o inspetor do slot e os botões Salvar, Restaurar padrão e Desativar); embaixo o inventário
 * do jogador, de onde se escolhem os itens sem tirá-los do lugar.
 *
 * <p>O rascunho editado fica só aqui até Salvar; cada snapshot novo do servidor mantém a receita
 * escolhida e troca o rascunho pelo que está salvo. Tamanho fixo, maior que o do mockup (a fonte do jogo
 * é mais larga que a do mockup, e a barra do inventário passava da borda dele).
 */
public class RecipeEditorScreen extends AbstractContainerScreen<RecipeEditorMenu> {
    static final int W = 356;
    static final int H = 276;
    private static final int M = 6;

    // coluna da esquerda: busca, lista e recarga
    private static final int LIST_X = M;
    private static final int LIST_W = 134;
    private static final int SEARCH_Y = 22;
    private static final int LIST_Y = 37;
    private static final int ROW_H = 20;
    private static final int ROWS = 5;
    private static final int LIST_H = ROWS * ROW_H + 4;
    private static final int PENDING_Y = LIST_Y + LIST_H + 10;

    // coluna da direita: a receita escolhida
    private static final int X0 = 148;
    private static final int RW = W - M - X0;
    private static final int GRID_X = X0 + 4;
    private static final int GRID_Y = 64;
    private static final int ARROW_X = X0 + 66;
    private static final int ARROW_Y = GRID_Y + 20;
    private static final int OUT_X = X0 + 92;
    private static final int OUT_Y = GRID_Y + 14;
    private static final int OUT_SIZE = 26;
    private static final int STEP_X = X0 + 124;
    private static final int INSPECT_Y = 122;
    private static final int INSPECT_H = 34;
    private static final int ACTIONS_Y = 162;

    private final List<FlatButton> buttons = new ArrayList<>();
    private EditBox searchBox;
    private FlatButton reloadButton;
    private FlatButton minusButton;
    private FlatButton plusButton;
    private FlatButton itemButton;
    private FlatButton tagButton;
    private FlatButton prevButton;
    private FlatButton nextButton;
    private FlatButton clearButton;
    private FlatButton saveButton;
    private FlatButton restoreButton;
    private FlatButton toggleButton;

    private @Nullable ResourceLocation selectedId;
    private @Nullable RecipeDraft draft;
    private int selectedSlot;
    /** O item de onde saiu a tag de cada slot: as setas percorrem as tags dele e Item volta a ele. */
    private final Item[] origin = new Item[RecipeDraft.SLOTS];
    private int scroll;
    private String search = "";

    public RecipeEditorScreen(RecipeEditorMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = W;
        this.imageHeight = H;
        this.inventoryLabelY = -1000;
        this.titleLabelY = -1000;
        onSnapshot(menu.snapshot());
    }

    static Component tr(String key, Object... args) {
        return Component.translatable("gui.wirelessautomate.recipes." + key, args);
    }

    // ------------------------------------------------------------------ estado

    private RecipeEditorSnapshot snapshot() {
        return menu.snapshot();
    }

    private @Nullable Row selectedRow() {
        if (selectedId == null) {
            return null;
        }
        for (Row row : snapshot().rows()) {
            if (row.id().equals(selectedId)) {
                return row;
            }
        }
        return null;
    }

    /** Snapshot novo: a receita escolhida continua escolhida e o rascunho passa a ser o salvo. */
    private void onSnapshot(RecipeEditorSnapshot snapshot) {
        Row row = selectedRow();
        if (row == null) {
            row = snapshot.rows().isEmpty() ? null : snapshot.rows().get(0);
            select(row);
            return;
        }
        draft = row.current();
        java.util.Arrays.fill(origin, null);
        refresh();
    }

    private void select(@Nullable Row row) {
        selectedId = row == null ? null : row.id();
        draft = row == null ? null : row.current();
        java.util.Arrays.fill(origin, null);
        selectedSlot = 0;
        if (draft != null) {
            for (int i = 0; i < RecipeDraft.SLOTS; i++) {
                if (!draft.slots().get(i).isEmpty()) {
                    selectedSlot = i;
                    break;
                }
            }
        }
        refresh();
    }

    private boolean dirty() {
        Row row = selectedRow();
        return row != null && draft != null && !draft.equals(row.current());
    }

    private boolean disabled() {
        return draft != null && draft.disabled();
    }

    /** A grade aceita itens: há receita escolhida e ela não está desativada. */
    public boolean canEditGrid() {
        return draft != null && !draft.disabled();
    }

    private List<Row> visibleRows() {
        String q = search.trim().toLowerCase(Locale.ROOT);
        List<Row> out = new ArrayList<>();
        for (Row row : snapshot().rows()) {
            if (q.isEmpty() || resultStack(row).getHoverName().getString().toLowerCase(Locale.ROOT).contains(q)
                    || row.id().getPath().contains(q)) {
                out.add(row);
            }
        }
        return out;
    }

    private int maxScroll() {
        return Math.max(0, visibleRows().size() - ROWS);
    }

    private static ItemStack resultStack(Row row) {
        return new ItemStack(BuiltInRegistries.ITEM.get(row.resultItem()));
    }

    /** Quantidade máxima do resultado: 64 ou o tamanho de pilha do item, o menor. */
    private int maxCount() {
        Row row = selectedRow();
        return row == null ? RecipeDraft.MAX_COUNT
                : Math.max(RecipeDraft.MIN_COUNT, Math.min(RecipeDraft.MAX_COUNT, resultStack(row).getMaxStackSize()));
    }

    // ------------------------------------------------------------------ itens e tags

    private static Item item(String id) {
        ResourceLocation rl = ResourceLocation.tryParse(id);
        return rl == null ? Items.AIR : BuiltInRegistries.ITEM.get(rl);
    }

    /** O primeiro item da tag (o de referência, que dá o ícone), ou ar. */
    private static Item tagReference(String id) {
        ResourceLocation rl = ResourceLocation.tryParse(id);
        if (rl == null) {
            return Items.AIR;
        }
        Optional<HolderSet.Named<Item>> tag = BuiltInRegistries.ITEM.getTag(TagKey.create(Registries.ITEM, rl));
        return tag.flatMap(t -> t.stream().findFirst()).map(Holder::value).orElse(Items.AIR);
    }

    /** O item que o slot mostra: o próprio, ou o de onde a tag saiu, ou o de referência da tag. */
    private Item shownItem(int i, RecipeSlot slot) {
        return switch (slot.kind()) {
            case EMPTY -> Items.AIR;
            case ITEM -> item(slot.id());
            case TAG -> origin[i] != null ? origin[i] : tagReference(slot.id());
        };
    }

    /** As tags do item, em ordem alfabética do id. */
    private static List<String> tagsOf(Item item) {
        if (item == Items.AIR) {
            return List.of();
        }
        return new ItemStack(item).getTags().map(t -> t.location().toString()).sorted().toList();
    }

    /** As tags oferecidas no slot escolhido (as do item dele). */
    private List<String> slotTags() {
        if (draft == null) {
            return List.of();
        }
        RecipeSlot slot = draft.slots().get(selectedSlot);
        List<String> tags = tagsOf(shownItem(selectedSlot, slot));
        if (slot.kind() == RecipeSlot.Kind.TAG && !tags.contains(slot.id())) {
            List<String> withOwn = new ArrayList<>(tags);
            withOwn.add(0, slot.id());
            return withOwn;
        }
        return tags;
    }

    private static Component itemName(Item item) {
        return new ItemStack(item).getHoverName();
    }

    private void setSlot(int i, RecipeSlot slot) {
        if (!canEditGrid()) {
            return;
        }
        draft = draft.withSlot(i, slot);
        if (slot.kind() != RecipeSlot.Kind.TAG) {
            origin[i] = null;
        }
    }

    /** Fantasma do JEI (ou do cursor) no slot {@code i} da grade. */
    public void setGhost(int i, ItemStack stack) {
        if (stack.isEmpty() || !canEditGrid() || i < 0 || i >= RecipeDraft.SLOTS) {
            return;
        }
        selectedSlot = i;
        setSlot(i, RecipeSlot.item(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString()));
    }

    /** A área de um slot da grade, em coordenadas da tela (para o JEI). */
    public Rect2i gridSlotArea(int i) {
        return new Rect2i(leftPos + GRID_X + (i % 3) * 18, topPos + GRID_Y + (i / 3) * 18, 18, 18);
    }

    // ------------------------------------------------------------------ montagem

    @Override
    protected void init() {
        super.init();
        buttons.clear();
        int x = leftPos;
        int y = topPos;
        menu.setListener(this::onSnapshot);

        searchBox = new EditBox(font, x + LIST_X + 4, y + SEARCH_Y + 3, LIST_W - 8, 9, tr("search"));
        searchBox.setBordered(false);
        searchBox.setMaxLength(64);
        searchBox.setTextColor(GuiPaint.FG);
        searchBox.setTextShadow(false); // tinta sobre porcelana, sem sombra
        searchBox.setHint(tr("search").copy().withColor(GuiPaint.DISABLED));
        searchBox.setValue(search);
        searchBox.setResponder(value -> {
            search = value;
            scroll = 0;
        });
        addRenderableWidget(searchBox);

        reloadButton = add(new FlatButton(x + LIST_X, y + ACTIONS_Y, LIST_W, 16, tr("reload"),
                (g, b, hovered) -> paintButton(g, b, hovered, tr("reload"), false),
                () -> PacketDistributor.sendToServer(RecipeEditorActionPayload.reload())));

        minusButton = add(new FlatButton(x + STEP_X, y + OUT_Y, 12, 12, Component.literal("-"),
                (g, b, hovered) -> paintButton(g, b, hovered, Component.literal("-"), false), () -> stepCount(-1)));
        plusButton = add(new FlatButton(x + STEP_X, y + OUT_Y + 14, 12, 12, Component.literal("+"),
                (g, b, hovered) -> paintButton(g, b, hovered, Component.literal("+"), false), () -> stepCount(1)));

        int iy = y + INSPECT_Y + 16;
        itemButton = add(new FlatButton(x + X0 + 5, iy, 32, 13, tr("item"),
                (g, b, hovered) -> paintChoice(g, b, hovered, tr("item"), slotKind() == RecipeSlot.Kind.ITEM),
                this::toItem));
        tagButton = add(new FlatButton(x + X0 + 39, iy, 28, 13, tr("tag"),
                (g, b, hovered) -> paintChoice(g, b, hovered, tr("tag"), slotKind() == RecipeSlot.Kind.TAG),
                this::toTag));
        prevButton = add(new FlatButton(x + X0 + 71, iy, 12, 13, Component.literal("<"),
                (g, b, hovered) -> paintArrow(g, b, hovered, false), () -> cycleTag(-1)));
        nextButton = add(new FlatButton(x + X0 + 102, iy, 12, 13, Component.literal(">"),
                (g, b, hovered) -> paintArrow(g, b, hovered, true), () -> cycleTag(1)));
        int clearW = Math.max(38, font.width(tr("clear")) + 10);
        clearButton = add(new FlatButton(x + X0 + RW - 4 - clearW, iy, clearW, 13, tr("clear"),
                (g, b, hovered) -> paintButton(g, b, hovered, tr("clear"), false),
                () -> setSlot(selectedSlot, RecipeSlot.EMPTY)));

        // Salvar, Restaurar padrão e Desativar/Reativar dividem a largura da coluna pelo tamanho dos textos
        int saveW = font.width(tr("save")) + 12;
        int toggleW = Math.max(font.width(tr("disable")), font.width(tr("enable"))) + 10;
        int restoreW = RW - saveW - toggleW - 6;
        saveButton = add(new FlatButton(x + X0, y + ACTIONS_Y, saveW, 16, tr("save"),
                (g, b, hovered) -> paintButton(g, b, hovered, tr("save"), true), this::save));
        restoreButton = add(new FlatButton(x + X0 + saveW + 3, y + ACTIONS_Y, restoreW, 16, tr("restore"),
                (g, b, hovered) -> paintButton(g, b, hovered, tr("restore"), false), () -> send(Action.RESTORE)));
        toggleButton = add(new FlatButton(x + X0 + RW - toggleW, y + ACTIONS_Y, toggleW, 16, tr("disable"),
                (g, b, hovered) -> paintButton(g, b, hovered, disabled() ? tr("enable") : tr("disable"), false),
                () -> send(disabled() ? Action.ENABLE : Action.DISABLE)));
        refresh();
    }

    private FlatButton add(FlatButton button) {
        buttons.add(button);
        return addRenderableWidget(button);
    }

    @Override
    public void removed() {
        menu.setListener(null);
        super.removed();
    }

    /** Visibilidade e estado dos botões, pelo estado atual. */
    private void refresh() {
        if (reloadButton == null) {
            return; // antes do init
        }
        scroll = Math.max(0, Math.min(scroll, maxScroll()));
        boolean has = draft != null;
        boolean edit = canEditGrid();
        reloadButton.visible = snapshot().pending() > 0;
        minusButton.visible = plusButton.visible = has;
        minusButton.active = edit && draft.count() > RecipeDraft.MIN_COUNT;
        plusButton.active = edit && draft.count() < maxCount();
        itemButton.visible = tagButton.visible = clearButton.visible = edit;
        RecipeSlot.Kind kind = slotKind();
        List<String> tags = edit ? slotTags() : List.of();
        itemButton.active = kind == RecipeSlot.Kind.TAG && shownItem(selectedSlot, draft.slots().get(selectedSlot)) != Items.AIR;
        tagButton.active = kind == RecipeSlot.Kind.ITEM && !tags.isEmpty();
        boolean arrows = edit && kind == RecipeSlot.Kind.TAG && tags.size() > 1;
        prevButton.visible = nextButton.visible = arrows;
        if (arrows) {
            nextButton.setX(leftPos + X0 + 86 + font.width(tagIndexText(tags)) + 3);
        }
        clearButton.active = kind != RecipeSlot.Kind.EMPTY;
        saveButton.visible = restoreButton.visible = toggleButton.visible = has;
        saveButton.active = dirty() && !draft.isEmpty();
        Row row = selectedRow();
        restoreButton.active = row != null && (row.state() != RecipeEditor.State.DEFAULT || dirty());
    }

    private RecipeSlot.Kind slotKind() {
        return draft == null ? RecipeSlot.Kind.EMPTY : draft.slots().get(selectedSlot).kind();
    }

    private Component tagIndexText(List<String> tags) {
        String current = draft.slots().get(selectedSlot).id();
        return Component.literal((tags.indexOf(current) + 1) + "/" + tags.size());
    }

    // ------------------------------------------------------------------ ações

    private void stepCount(int delta) {
        if (canEditGrid()) {
            draft = draft.withCount(Math.max(RecipeDraft.MIN_COUNT, Math.min(maxCount(), draft.count() + delta)));
        }
    }

    private void toItem() {
        RecipeSlot slot = draft.slots().get(selectedSlot);
        Item item = shownItem(selectedSlot, slot);
        if (slot.kind() == RecipeSlot.Kind.TAG && item != Items.AIR) {
            setSlot(selectedSlot, RecipeSlot.item(BuiltInRegistries.ITEM.getKey(item).toString()));
        }
    }

    private void toTag() {
        RecipeSlot slot = draft.slots().get(selectedSlot);
        if (slot.kind() != RecipeSlot.Kind.ITEM) {
            return;
        }
        Item item = item(slot.id());
        List<String> tags = tagsOf(item);
        if (!tags.isEmpty()) {
            setSlot(selectedSlot, RecipeSlot.tag(tags.get(0)));
            origin[selectedSlot] = item;
        }
    }

    private void cycleTag(int delta) {
        List<String> tags = slotTags();
        RecipeSlot slot = draft.slots().get(selectedSlot);
        if (slot.kind() != RecipeSlot.Kind.TAG || tags.size() < 2) {
            return;
        }
        Item from = shownItem(selectedSlot, slot);
        int k = tags.indexOf(slot.id());
        String next = tags.get(Math.floorMod(k + delta, tags.size()));
        setSlot(selectedSlot, RecipeSlot.tag(next));
        // a referência da tag nova pode ser outro item: as setas continuam nas tags do mesmo
        origin[selectedSlot] = from;
    }

    private void save() {
        Row row = selectedRow();
        if (row != null && draft != null && !draft.isEmpty()) {
            PacketDistributor.sendToServer(RecipeEditorActionPayload.save(row.id(), draft));
        }
    }

    private void send(Action action) {
        if (selectedId != null) {
            PacketDistributor.sendToServer(RecipeEditorActionPayload.of(action, selectedId));
        }
    }

    // ------------------------------------------------------------------ entrada

    private int gridSlotAt(double mx, double my) {
        for (int i = 0; i < RecipeDraft.SLOTS; i++) {
            Rect2i r = gridSlotArea(i);
            if (mx >= r.getX() && mx < r.getX() + r.getWidth() && my >= r.getY() && my < r.getY() + r.getHeight()) {
                return i;
            }
        }
        return -1;
    }

    private @Nullable Slot inventorySlotAt(double mx, double my) {
        for (Slot slot : menu.slots) {
            int sx = leftPos + slot.x;
            int sy = topPos + slot.y;
            if (mx >= sx - 1 && mx < sx + 17 && my >= sy - 1 && my < sy + 17) {
                return slot;
            }
        }
        return null;
    }

    private int rowAt(double mx, double my) {
        int lx = leftPos + LIST_X + 1;
        int ly = topPos + LIST_Y + 2;
        if (mx < lx || mx >= lx + LIST_W - 2 || my < ly || my >= ly + ROWS * ROW_H) {
            return -1;
        }
        int index = scroll + (int) ((my - ly) / ROW_H);
        return index < visibleRows().size() ? index : -1;
    }

    private boolean overResult(double mx, double my) {
        int x = leftPos + OUT_X;
        int y = topPos + OUT_Y;
        return mx >= x && mx < x + OUT_SIZE && my >= y && my < y + OUT_SIZE;
    }

    /** Os botões seguem o estado já no clique seguinte, sem esperar o próximo quadro. */
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        boolean handled = click(mouseX, mouseY, button);
        refresh();
        return handled;
    }

    private boolean click(double mouseX, double mouseY, int button) {
        if (getFocused() == searchBox && !searchBox.isMouseOver(mouseX, mouseY)) {
            searchBox.setFocused(false);
            setFocused(null);
        }
        int row = rowAt(mouseX, mouseY);
        if (row >= 0) {
            Row picked = visibleRows().get(row);
            if (!picked.id().equals(selectedId)) {
                // trocar de receita descarta o rascunho não salvo
                select(picked);
            }
            return true;
        }
        int grid = gridSlotAt(mouseX, mouseY);
        if (grid >= 0 && draft != null) {
            selectedSlot = grid;
            if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
                setSlot(grid, RecipeSlot.EMPTY);
            } else if (!menu.getCarried().isEmpty()) {
                setGhost(grid, menu.getCarried());
            }
            return true;
        }
        // o inventário só empresta o item: nada se move
        Slot slot = inventorySlotAt(mouseX, mouseY);
        if (slot != null) {
            if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && slot.hasItem()) {
                setGhost(selectedSlot, slot.getItem());
            }
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    /** Nenhum clique, tecla de atalho ou soltar move item: a tela só lê o inventário. */
    @Override
    protected void slotClicked(@Nullable Slot slot, int slotId, int mouseButton, ClickType type) {
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (mouseX >= leftPos + LIST_X && mouseX < leftPos + LIST_X + LIST_W && mouseY >= topPos + LIST_Y
                && mouseY < topPos + LIST_Y + LIST_H) {
            scroll = Math.max(0, Math.min(maxScroll(), scroll - (int) Math.signum(scrollY)));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (getFocused() == searchBox && searchBox.isFocused()) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE || keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                searchBox.setFocused(false);
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
        refresh();
        GuiText.beginFrame();
        super.render(g, mouseX, mouseY, partialTick);
        List<Component> tip = tooltipAt(mouseX, mouseY);
        if (tip != null) {
            List<FormattedCharSequence> lines = new ArrayList<>();
            for (Component line : tip) {
                lines.add(line.getVisualOrderText());
            }
            setTooltipForNextRenderPass(lines);
            return;
        }
        Component clipped = GuiText.clipAt(mouseX, mouseY);
        if (clipped != null && hoveredSlot == null) {
            setTooltipForNextRenderPass(font.split(clipped, 220));
            return;
        }
        renderTooltip(g, mouseX, mouseY);
    }

    private @Nullable List<Component> tooltipAt(int mx, int my) {
        if (draft == null) {
            return null;
        }
        Row row = selectedRow();
        int grid = gridSlotAt(mx, my);
        if (grid >= 0) {
            List<Component> lines = new ArrayList<>();
            RecipeSlot slot = draft.slots().get(grid);
            switch (slot.kind()) {
                case EMPTY -> {
                    String empty = tr("slot.empty").getString();
                    lines.add(Component.literal(empty.isEmpty() ? empty
                            : empty.substring(0, 1).toUpperCase(Locale.ROOT) + empty.substring(1))
                            .withColor(GuiPaint.TOOLTIP_MUTED));
                    if (canEditGrid()) {
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
            if (canEditGrid() && !slot.isEmpty()) {
                lines.add(tr("tooltip.clear").copy().withColor(GuiPaint.TOOLTIP_MUTED));
            }
            return lines;
        }
        if (row != null && overResult(mx, my)) {
            return List.of(resultStack(row).getHoverName().copy().append(" ×" + draft.count()),
                    Component.literal(row.resultItem().toString()).withColor(GuiPaint.TOOLTIP_MUTED));
        }
        return null;
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        // os textos são desenhados em renderBg, em coordenadas da tela
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        int x = leftPos;
        int y = topPos;
        GuiPaint.panel(g, x, y, W, H, GuiPaint.ACCENT);

        // cabeçalho: o Olho, o título e a pílula de operador
        Component operator = tr("operator");
        int opW = font.width(operator) + 8;
        GuiPaint.pill(g, x + W - M - opW, y + 7, opW, 11, GuiPaint.BEVEL_LIGHT, GuiPaint.FG);
        GuiPaint.text(g, font, operator, x + W - M - opW + 4, y + 9, GuiPaint.FG);
        GuiPaint.eye(g, x + 8, y + 10, GuiPaint.tierColor(RouterTier.ULTIMATE));
        GuiText.draw(g, font, title, x + 17, y + 9, W - M - opW - 6 - 17, GuiPaint.FG);

        renderList(g, x, y, mouseX, mouseY);

        Row row = selectedRow();
        if (row != null && draft != null) {
            renderRecipe(g, x, y, row, mouseX, mouseY);
        }

        // inventário do jogador
        Slot first = menu.slots.get(0);
        GuiText.draw(g, font, tr("inventory", selectedSlot + 1), x + first.x - 1, y + first.y - 11,
                W - M - first.x + 1, GuiPaint.MUTED);
        for (Slot slot : menu.slots) {
            GuiPaint.slot(g, x + slot.x - 1, y + slot.y - 1);
        }
    }

    private void renderList(GuiGraphics g, int x, int y, int mouseX, int mouseY) {
        GuiPaint.box(g, x + LIST_X, y + SEARCH_Y, LIST_W, 13, GuiPaint.BEVEL_LIGHT,
                searchBox.isFocused() ? GuiPaint.ACCENT : GuiPaint.LINE);
        GuiPaint.inset(g, x + LIST_X, y + LIST_Y, LIST_W, LIST_H);
        List<Row> rows = visibleRows();
        if (rows.isEmpty()) {
            GuiText.draw(g, font, tr("none"), x + LIST_X + 5, y + LIST_Y + 6, LIST_W - 10, GuiPaint.MUTED);
        }
        int hover = rowAt(mouseX, mouseY);
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
            GuiPaint.dot(g, rx + rw - 8, ry + 8, stateColor(row.state()));
        }
        if (bar) {
            int trackY = y + LIST_Y + 2;
            int trackH = ROWS * ROW_H;
            int thumbH = Math.max(8, trackH * ROWS / rows.size());
            int thumbY = trackY + (trackH - thumbH) * scroll / Math.max(1, maxScroll());
            g.fill(x + LIST_X + LIST_W - 4, thumbY, x + LIST_X + LIST_W - 2, thumbY + thumbH, GuiPaint.BEVEL_DARK);
        }

        int pending = snapshot().pending();
        if (pending > 0) {
            Component text = pending == 1 ? tr("pending.one") : tr("pending", pending);
            GuiText.draw(g, font, text, x + LIST_X, y + PENDING_Y, LIST_W, GuiPaint.WARN);
        } else {
            GuiText.draw(g, font, tr("applied"), x + LIST_X, y + PENDING_Y, LIST_W, GuiPaint.MUTED);
        }
    }

    private static int stateColor(RecipeEditor.State state) {
        return switch (state) {
            case DEFAULT -> GuiPaint.OK;
            case EDITED -> GuiPaint.ACCENT;
            case DISABLED -> GuiPaint.DISABLED;
        };
    }

    private void renderRecipe(GuiGraphics g, int x, int y, Row row, int mouseX, int mouseY) {
        boolean off = draft.disabled();
        boolean dirty = dirty();

        // nome, pílula de estado, forma e id
        Component state = dirty ? tr("state.unsaved") : tr("state." + row.state().name().toLowerCase(Locale.ROOT));
        int stateColor = dirty ? GuiPaint.WARN : stateColor(row.state());
        int pillW = font.width(state) + 8;
        int pillX = x + X0 + RW - pillW;
        GuiPaint.pill(g, pillX, y + 20, pillW, 11, GuiPaint.BEVEL_LIGHT, stateColor);
        GuiPaint.text(g, font, state, pillX + 4, y + 22, stateColor);
        GuiText.draw(g, font, resultStack(row).getHoverName(), x + X0, y + 22, pillX - 4 - (x + X0), GuiPaint.FG);
        Component shape = tr(draft.shape() == RecipeDraft.Shape.SHAPED ? "shaped" : "shapeless");
        GuiText.draw(g, font, shape.copy().append(" · " + row.id().getPath()), x + X0, y + 32, RW, GuiPaint.MUTED);
        if (row.divergent()) {
            GuiText.wrap(g, font, tr("divergent"), x + X0, y + 42, RW, 2, GuiPaint.WARN);
        }

        // grade
        int hover = gridSlotAt(mouseX, mouseY);
        for (int i = 0; i < RecipeDraft.SLOTS; i++) {
            int sx = x + GRID_X + (i % 3) * 18;
            int sy = y + GRID_Y + (i / 3) * 18;
            boolean selected = i == selectedSlot && !off;
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
            Item item = shownItem(i, slot);
            if (item != Items.AIR) {
                g.renderItem(new ItemStack(item), sx + 1, sy + 1);
                // fantasma: o item apagado; desativada, mais ainda
                overlay(g, sx + 1, sy + 1, 16, 16, selected ? GuiPaint.BEVEL_LIGHT : GuiPaint.INSET, off ? 0xB0 : 0x60);
            }
            if (slot.kind() == RecipeSlot.Kind.TAG) {
                tagMark(g, sx, sy, off);
            }
        }

        // seta, resultado e quantidade
        arrow(g, x + ARROW_X, y + ARROW_Y, off ? GuiPaint.DISABLED : GuiPaint.MUTED);
        int ox = x + OUT_X;
        int oy = y + OUT_Y;
        GuiPaint.inset(g, ox, oy, OUT_SIZE, OUT_SIZE);
        ItemStack result = resultStack(row);
        g.renderItem(result, ox + 5, oy + 5);
        if (off) {
            overlay(g, ox + 5, oy + 5, 16, 16, GuiPaint.INSET, 0xB0);
        } else if (draft.count() > 1) {
            // a quantidade em tinta de grafite no canto (o branco do jogo some na porcelana)
            Component qty = Component.literal(String.valueOf(draft.count()));
            g.pose().pushPose();
            g.pose().translate(0, 0, 200);
            GuiPaint.text(g, font, qty, ox + OUT_SIZE - 2 - font.width(qty), oy + OUT_SIZE - 9, GuiPaint.FG);
            g.pose().popPose();
        }
        Component count = draft.count() == 1 ? tr("count.one") : tr("count", draft.count());
        GuiText.draw(g, font, count, x + STEP_X + 16, y + OUT_Y + 9, x + X0 + RW - (x + STEP_X + 16),
                off ? GuiPaint.DISABLED : GuiPaint.MUTED);

        // inspetor do slot escolhido, ou o aviso de desativada
        if (off) {
            GuiText.wrap(g, font, tr("disabled_hint"), x + X0 + 4, y + INSPECT_Y + 2, RW - 8, 3, GuiPaint.MUTED);
            return;
        }
        GuiPaint.inset(g, x + X0, y + INSPECT_Y, RW, INSPECT_H);
        RecipeSlot slot = draft.slots().get(selectedSlot);
        MutableComponent value = switch (slot.kind()) {
            case EMPTY -> tr("slot.empty").copy().withColor(GuiPaint.MUTED);
            case ITEM -> itemName(item(slot.id())).copy().withColor(GuiPaint.FG);
            case TAG -> Component.literal("#" + slot.id()).withColor(GuiPaint.FG);
        };
        GuiText.draw(g, font, tr("slot", selectedSlot + 1, value), x + X0 + 5, y + INSPECT_Y + 4, RW - 10, GuiPaint.FG);
        List<String> tags = slotTags();
        if (slot.kind() == RecipeSlot.Kind.TAG && tags.size() > 1) {
            GuiPaint.text(g, font, tagIndexText(tags), x + X0 + 86, y + INSPECT_Y + 19, GuiPaint.MUTED);
        } else if (slot.kind() == RecipeSlot.Kind.ITEM && tags.isEmpty()) {
            GuiText.draw(g, font, tr("no_tags"), x + X0 + 71, y + INSPECT_Y + 19,
                    clearButton.getX() - 4 - (x + X0 + 71), GuiPaint.DISABLED);
        }
    }

    /** Uma camada translúcida por cima de um item (desenhado mais perto da tela que ele). */
    private static void overlay(GuiGraphics g, int x, int y, int w, int h, int color, int alpha) {
        g.pose().pushPose();
        g.pose().translate(0, 0, 200);
        g.fill(x, y, x + w, y + h, (color & 0x00FFFFFF) | alpha << 24);
        g.pose().popPose();
    }

    /** O {@code #} coral no canto de um slot de tag: 7×7 px, em pixel art. */
    private static void tagMark(GuiGraphics g, int x, int y, boolean off) {
        g.pose().pushPose();
        g.pose().translate(0, 0, 210);
        g.fill(x, y, x + 7, y + 7, off ? GuiPaint.DISABLED : GuiPaint.ACCENT);
        int c = GuiPaint.BEVEL_LIGHT;
        g.fill(x + 2, y + 1, x + 3, y + 6, c);
        g.fill(x + 4, y + 1, x + 5, y + 6, c);
        g.fill(x + 1, y + 2, x + 6, y + 3, c);
        g.fill(x + 1, y + 4, x + 6, y + 5, c);
        g.pose().popPose();
    }

    /** Seta de 20×13 px para a direita (haste de 3 px, cabeça de 11 px). */
    private static void arrow(GuiGraphics g, int x, int y, int color) {
        g.fill(x + 1, y + 5, x + 13, y + 8, color);
        for (int i = 0; i < 6; i++) {
            g.fill(x + 13 + i, y + 1 + i, x + 14 + i, y + 12 - i, color);
        }
    }

    // ------------------------------------------------------------------ botões

    private void paintButton(GuiGraphics g, FlatButton b, boolean hovered, Component text, boolean primary) {
        int fill;
        int border;
        int ink;
        if (!b.active) {
            fill = GuiPaint.PANEL;
            border = GuiPaint.DISABLED;
            ink = GuiPaint.DISABLED;
        } else if (primary) {
            fill = GuiPaint.ACCENT;
            border = hovered ? GuiPaint.FG : GuiPaint.ACCENT_DEEP;
            ink = GuiPaint.BEVEL_LIGHT;
        } else {
            fill = GuiPaint.BUTTON;
            border = hovered ? GuiPaint.BUTTON_HOVER_BORDER : GuiPaint.BUTTON_BORDER;
            ink = GuiPaint.FG;
        }
        GuiPaint.box(g, b.getX(), b.getY(), b.getWidth(), b.getHeight(), fill, border);
        GuiPaint.textCentered(g, font, text, b.getX() + b.getWidth() / 2 + 1, b.getY() + (b.getHeight() - 8) / 2, ink);
    }

    /** Item ou Tag: escolhido (grafite), disponível ou apagado. */
    private void paintChoice(GuiGraphics g, FlatButton b, boolean hovered, Component text, boolean on) {
        if (on) {
            GuiPaint.box(g, b.getX(), b.getY(), b.getWidth(), b.getHeight(), GuiPaint.SELECTED, GuiPaint.SELECTED);
            GuiPaint.textCentered(g, font, text, b.getX() + b.getWidth() / 2 + 1, b.getY() + (b.getHeight() - 8) / 2,
                    GuiPaint.SELECTED_TEXT);
            return;
        }
        paintButton(g, b, hovered, text, false);
    }

    private void paintArrow(GuiGraphics g, FlatButton b, boolean hovered, boolean right) {
        GuiPaint.box(g, b.getX(), b.getY(), b.getWidth(), b.getHeight(), GuiPaint.BUTTON,
                hovered ? GuiPaint.BUTTON_HOVER_BORDER : GuiPaint.BUTTON_BORDER);
        int ax = b.getX() + (b.getWidth() - 3) / 2;
        int ay = b.getY() + (b.getHeight() - 5) / 2;
        if (right) {
            GuiPaint.arrowRight(g, ax, ay, GuiPaint.FG);
        } else {
            g.fill(ax + 2, ay, ax + 3, ay + 5, GuiPaint.FG);
            g.fill(ax + 1, ay + 1, ax + 2, ay + 4, GuiPaint.FG);
            g.fill(ax, ay + 2, ax + 1, ay + 3, GuiPaint.FG);
        }
    }

    // ------------------------------------------------------------------ ganchos do e2e

    /** A receita escolhida. */
    @Nullable ResourceLocation selectedId() {
        return selectedId;
    }

    /** O rascunho na tela (com as mudanças não salvas). */
    @Nullable RecipeDraft draft() {
        return draft;
    }

    /** Rola a lista até a receita e devolve o centro da linha dela, ou {@code null} se não está na lista. */
    int @Nullable [] rowCenter(ResourceLocation id) {
        List<Row> rows = visibleRows();
        for (int i = 0; i < rows.size(); i++) {
            if (rows.get(i).id().equals(id)) {
                if (i < scroll || i >= scroll + ROWS) {
                    scroll = Math.max(0, Math.min(maxScroll(), i));
                }
                return new int[] {leftPos + LIST_X + LIST_W / 2, topPos + LIST_Y + 2 + (i - scroll) * ROW_H + ROW_H / 2};
            }
        }
        return null;
    }

    FlatButton plusButton() {
        return plusButton;
    }

    FlatButton saveButton() {
        return saveButton;
    }

    FlatButton restoreButton() {
        return restoreButton;
    }

    FlatButton reloadButton() {
        return reloadButton;
    }
}
