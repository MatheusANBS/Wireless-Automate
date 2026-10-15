package io.github.matheusanbs.wirelessautomate.client.recipe;

import static io.github.matheusanbs.wirelessautomate.client.recipe.RecipeLayout.ACTIONS_Y;
import static io.github.matheusanbs.wirelessautomate.client.recipe.RecipeLayout.GRID_X;
import static io.github.matheusanbs.wirelessautomate.client.recipe.RecipeLayout.GRID_Y;
import static io.github.matheusanbs.wirelessautomate.client.recipe.RecipeLayout.H;
import static io.github.matheusanbs.wirelessautomate.client.recipe.RecipeLayout.INSPECT_Y;
import static io.github.matheusanbs.wirelessautomate.client.recipe.RecipeLayout.LIST_W;
import static io.github.matheusanbs.wirelessautomate.client.recipe.RecipeLayout.LIST_X;
import static io.github.matheusanbs.wirelessautomate.client.recipe.RecipeLayout.M;
import static io.github.matheusanbs.wirelessautomate.client.recipe.RecipeLayout.OUT_SIZE;
import static io.github.matheusanbs.wirelessautomate.client.recipe.RecipeLayout.OUT_X;
import static io.github.matheusanbs.wirelessautomate.client.recipe.RecipeLayout.OUT_Y;
import static io.github.matheusanbs.wirelessautomate.client.recipe.RecipeLayout.RW;
import static io.github.matheusanbs.wirelessautomate.client.recipe.RecipeLayout.STEP_X;
import static io.github.matheusanbs.wirelessautomate.client.recipe.RecipeLayout.W;
import static io.github.matheusanbs.wirelessautomate.client.recipe.RecipeLayout.X0;

import io.github.matheusanbs.wirelessautomate.block.RouterTier;
import io.github.matheusanbs.wirelessautomate.client.FlatButton;
import io.github.matheusanbs.wirelessautomate.client.GuiPaint;
import io.github.matheusanbs.wirelessautomate.client.GuiText;
import io.github.matheusanbs.wirelessautomate.client.GuiThemes;
import io.github.matheusanbs.wirelessautomate.client.ThemeToggle;
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
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

/**
 * Tela do editor de receitas ({@code /wa recipes}), pelo mockup aprovado
 * ({@code docs/preview/editor-de-receitas.html}): à esquerda a busca, a lista das receitas do mod e a
 * recarga pendente ({@link RecipeList}); à direita a receita escolhida (grade 3×3 de slots fantasmas,
 * resultado com a quantidade, o inspetor do slot, em {@link RecipeView}, e os botões Salvar, Restaurar
 * padrão e Desativar); embaixo o inventário do jogador, de onde se escolhem os itens sem tirá-los do lugar.
 *
 * <p>O rascunho editado fica só aqui até Salvar ({@link RecipeEdit}); cada snapshot novo do servidor
 * mantém a receita escolhida e troca o rascunho pelo que está salvo. Tamanho fixo, maior que o do mockup,
 * porque a fonte do jogo é mais larga.
 */
public class RecipeEditorScreen extends AbstractContainerScreen<RecipeEditorMenu> {
    private final List<FlatButton> buttons = new ArrayList<>();
    private final RecipeEdit edit;
    private final RecipeList list;
    private final RecipeView view;
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

    public RecipeEditorScreen(RecipeEditorMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = W;
        this.imageHeight = H;
        this.inventoryLabelY = -1000;
        this.titleLabelY = -1000;
        this.edit = new RecipeEdit(menu);
        this.list = new RecipeList(menu);
        this.view = new RecipeView(this, edit);
        onSnapshot(menu.snapshot());
    }

    static Component tr(String key, Object... args) {
        return Component.translatable("gui.wirelessautomate.recipes." + key, args);
    }

    // ------------------------------------------------------------------ estado

    /** Snapshot novo: a receita escolhida continua escolhida e o rascunho passa a ser o salvo. */
    private void onSnapshot(RecipeEditorSnapshot snapshot) {
        edit.onSnapshot(snapshot);
        refresh();
    }

    private void select(@Nullable Row row) {
        edit.select(row);
        refresh();
    }

    /** A grade aceita itens: há receita escolhida e ela não está desativada. */
    public boolean canEditGrid() {
        return edit.canEditGrid();
    }

    /** Fantasma do JEI (ou do cursor) no slot {@code i} da grade. */
    public void setGhost(int i, ItemStack stack) {
        edit.setGhost(i, stack);
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

        EditBox searchBox = list.createSearchBox(x, y);
        inkVersion = -1;
        addRenderableWidget(searchBox);

        reloadButton = add(new FlatButton(x + LIST_X, y + ACTIONS_Y, LIST_W, 16, tr("reload"),
                (g, b, hovered) -> RecipePaint.paintButton(g, b, hovered, tr("reload"), false),
                () -> PacketDistributor.sendToServer(RecipeEditorActionPayload.reload())));

        minusButton = add(new FlatButton(x + STEP_X, y + OUT_Y, 12, 12, Component.literal("-"),
                (g, b, hovered) -> RecipePaint.paintButton(g, b, hovered, Component.literal("-"), false),
                () -> edit.stepCount(-1)));
        plusButton = add(new FlatButton(x + STEP_X, y + OUT_Y + 14, 12, 12, Component.literal("+"),
                (g, b, hovered) -> RecipePaint.paintButton(g, b, hovered, Component.literal("+"), false),
                () -> edit.stepCount(1)));

        int iy = y + INSPECT_Y + 16;
        itemButton = add(new FlatButton(x + X0 + 5, iy, 32, 13, tr("item"),
                (g, b, hovered) -> RecipePaint.paintChoice(g, b, hovered, tr("item"), edit.slotKind() == RecipeSlot.Kind.ITEM),
                edit::toItem));
        tagButton = add(new FlatButton(x + X0 + 39, iy, 28, 13, tr("tag"),
                (g, b, hovered) -> RecipePaint.paintChoice(g, b, hovered, tr("tag"), edit.slotKind() == RecipeSlot.Kind.TAG),
                edit::toTag));
        prevButton = add(new FlatButton(x + X0 + 71, iy, 12, 13, Component.literal("<"),
                (g, b, hovered) -> RecipePaint.paintArrow(g, b, hovered, false), () -> edit.cycleTag(-1)));
        nextButton = add(new FlatButton(x + X0 + 102, iy, 12, 13, Component.literal(">"),
                (g, b, hovered) -> RecipePaint.paintArrow(g, b, hovered, true), () -> edit.cycleTag(1)));
        int clearW = Math.max(38, font.width(tr("clear")) + 10);
        clearButton = add(new FlatButton(x + X0 + RW - 4 - clearW, iy, clearW, 13, tr("clear"),
                (g, b, hovered) -> RecipePaint.paintButton(g, b, hovered, tr("clear"), false),
                () -> edit.setSlot(edit.selectedSlot(), RecipeSlot.EMPTY)));

        // Salvar, Restaurar padrão e Desativar/Reativar dividem a largura da coluna pelo tamanho dos textos
        int saveW = font.width(tr("save")) + 12;
        int toggleW = Math.max(font.width(tr("disable")), font.width(tr("enable"))) + 10;
        int restoreW = RW - saveW - toggleW - 6;
        saveButton = add(new FlatButton(x + X0, y + ACTIONS_Y, saveW, 16, tr("save"),
                (g, b, hovered) -> RecipePaint.paintButton(g, b, hovered, tr("save"), true), this::save));
        restoreButton = add(new FlatButton(x + X0 + saveW + 3, y + ACTIONS_Y, restoreW, 16, tr("restore"),
                (g, b, hovered) -> RecipePaint.paintButton(g, b, hovered, tr("restore"), false), () -> send(Action.RESTORE)));
        toggleButton = add(new FlatButton(x + X0 + RW - toggleW, y + ACTIONS_Y, toggleW, 16, tr("disable"),
                (g, b, hovered) -> RecipePaint.paintButton(g, b, hovered, edit.disabled() ? tr("enable") : tr("disable"),
                        false),
                () -> send(edit.disabled() ? Action.ENABLE : Action.DISABLE)));
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
        list.clampScroll();
        RecipeDraft draft = edit.draft();
        boolean has = draft != null;
        boolean editable = edit.canEditGrid();
        reloadButton.visible = menu.snapshot().pending() > 0;
        minusButton.visible = plusButton.visible = has && !edit.ring();
        minusButton.active = editable && draft.count() > RecipeDraft.MIN_COUNT;
        plusButton.active = editable && draft.count() < edit.maxCount();
        itemButton.visible = tagButton.visible = clearButton.visible = editable;
        RecipeSlot.Kind kind = edit.slotKind();
        List<String> tags = editable ? edit.slotTags() : List.of();
        int selectedSlot = edit.selectedSlot();
        itemButton.active = kind == RecipeSlot.Kind.TAG
                && edit.shownItem(selectedSlot, draft.slots().get(selectedSlot)) != Items.AIR;
        tagButton.active = kind == RecipeSlot.Kind.ITEM && !tags.isEmpty();
        boolean arrows = editable && kind == RecipeSlot.Kind.TAG && tags.size() > 1;
        prevButton.visible = nextButton.visible = arrows;
        if (arrows) {
            nextButton.setX(leftPos + X0 + 86 + font.width(edit.tagIndexText(tags)) + 3);
        }
        clearButton.active = kind != RecipeSlot.Kind.EMPTY;
        saveButton.visible = restoreButton.visible = toggleButton.visible = has;
        saveButton.active = edit.dirty() && !draft.isEmpty();
        Row row = edit.selectedRow();
        restoreButton.active = row != null && (row.state() != RecipeEditor.State.DEFAULT || edit.dirty());
    }

    int clearButtonX() {
        return clearButton.getX();
    }

    // ------------------------------------------------------------------ ações

    private void save() {
        Row row = edit.selectedRow();
        RecipeDraft draft = edit.draft();
        if (row != null && draft != null && !draft.isEmpty()) {
            PacketDistributor.sendToServer(RecipeEditorActionPayload.save(row.id(), draft));
        }
    }

    private void send(Action action) {
        ResourceLocation selectedId = edit.selectedId();
        if (selectedId != null) {
            PacketDistributor.sendToServer(RecipeEditorActionPayload.of(action, selectedId));
        }
    }

    // ------------------------------------------------------------------ entrada

    int gridSlotAt(double mx, double my) {
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

    boolean overResult(double mx, double my) {
        int x = leftPos + OUT_X;
        int y = topPos + OUT_Y;
        return mx >= x && mx < x + OUT_SIZE && my >= y && my < y + OUT_SIZE;
    }

    /** Os botões seguem o estado já no clique seguinte, sem esperar o próximo quadro. */
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (ThemeToggle.mouseClicked(themeX(), themeY(), mouseX, mouseY, button)) {
            return true;
        }
        boolean handled = click(mouseX, mouseY, button);
        refresh();
        return handled;
    }

    public int themeX() {
        return leftPos + W - 4 - ThemeToggle.SIZE;
    }

    public int themeY() {
        return topPos + 7;
    }

    private boolean click(double mouseX, double mouseY, int button) {
        EditBox searchBox = list.searchBox();
        if (getFocused() == searchBox && !searchBox.isMouseOver(mouseX, mouseY)) {
            searchBox.setFocused(false);
            setFocused(null);
        }
        int row = list.rowAt(mouseX, mouseY, leftPos, topPos);
        if (row >= 0) {
            Row picked = list.visibleRows().get(row);
            if (!picked.id().equals(edit.selectedId())) {
                // trocar de receita descarta o rascunho não salvo
                select(picked);
            }
            return true;
        }
        int grid = gridSlotAt(mouseX, mouseY);
        if (grid >= 0 && edit.draft() != null) {
            if (edit.draft().locked(grid)) {
                return true; // o centro do anel: só o aviso, no tooltip
            }
            edit.selectSlot(grid);
            if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
                edit.setSlot(grid, RecipeSlot.EMPTY);
            } else if (!menu.getCarried().isEmpty()) {
                edit.setGhost(grid, menu.getCarried());
            }
            return true;
        }
        // o inventário só empresta o item: nada se move
        Slot slot = inventorySlotAt(mouseX, mouseY);
        if (slot != null) {
            if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && slot.hasItem()) {
                edit.setGhost(edit.selectedSlot(), slot.getItem());
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
        if (list.mouseScrolled(mouseX, mouseY, scrollY, leftPos, topPos)) {
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        EditBox searchBox = list.searchBox();
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
        EditBox searchBox = list.searchBox();
        if (getFocused() == searchBox && searchBox.isFocused()) {
            return searchBox.charTyped(codePoint, modifiers);
        }
        return super.charTyped(codePoint, modifiers);
    }

    // ------------------------------------------------------------------ desenho

    /** Versão do tema com que os campos de texto foram pintados ({@link GuiThemes#version()}). */
    private int inkVersion = -1;

    /** Cor do texto e da dica do campo: o EditBox guarda a cor, então segue o tema por aqui. */
    private void inkBoxes() {
        inkVersion = GuiThemes.version();
        GuiPaint.ink(list.searchBox(), tr("search"));
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        if (inkVersion != GuiThemes.version()) {
            inkBoxes();
        }
        refresh();
        GuiText.beginFrame();
        super.render(g, mouseX, mouseY, partialTick);
        if (ThemeToggle.hovered(themeX(), themeY(), mouseX, mouseY)) {
            g.renderComponentTooltip(font, ThemeToggle.tooltip(), mouseX, mouseY);
            return;
        }
        List<Component> tip = view.tooltipAt(mouseX, mouseY);
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
        int opX = x + W - M - ThemeToggle.SIZE - 4 - opW;
        GuiPaint.pill(g, opX, y + 7, opW, 11, GuiPaint.BEVEL_LIGHT, GuiPaint.FG);
        ThemeToggle.render(g, themeX(), themeY(), mouseX, mouseY);
        GuiPaint.text(g, font, operator, opX + 4, y + 9, GuiPaint.FG);
        GuiPaint.eye(g, x + 8, y + 10, GuiPaint.tierColor(RouterTier.ULTIMATE));
        GuiText.draw(g, font, title, x + 17, y + 9, W - M - ThemeToggle.SIZE - 4 - opW - 6 - 17, GuiPaint.FG);

        list.render(g, x, y, mouseX, mouseY, edit.selectedId());

        Row row = edit.selectedRow();
        if (row != null && edit.draft() != null) {
            view.render(g, x, y, row, mouseX, mouseY);
        }

        // inventário do jogador
        Slot first = menu.slots.get(0);
        GuiText.draw(g, font, tr("inventory", edit.selectedSlot() + 1), x + first.x - 1, y + first.y - 11,
                W - M - first.x + 1, GuiPaint.MUTED);
        for (Slot slot : menu.slots) {
            GuiPaint.slot(g, x + slot.x - 1, y + slot.y - 1);
        }
    }

    // ------------------------------------------------------------------ ganchos do e2e

    public @Nullable ResourceLocation selectedId() {
        return edit.selectedId();
    }

    /** O rascunho na tela (com as mudanças não salvas). */
    public @Nullable RecipeDraft draft() {
        return edit.draft();
    }

    /** Rola a lista até a receita e devolve o centro da linha dela, ou {@code null} se não está na lista. */
    public int @Nullable [] rowCenter(ResourceLocation id) {
        return list.rowCenter(id, leftPos, topPos);
    }

    public FlatButton plusButton() {
        return plusButton;
    }

    public FlatButton saveButton() {
        return saveButton;
    }

    public FlatButton restoreButton() {
        return restoreButton;
    }

    public FlatButton reloadButton() {
        return reloadButton;
    }
}
