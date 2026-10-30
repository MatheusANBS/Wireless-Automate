package io.github.matheusanbs.wirelessautomate.client.storage;

import static io.github.matheusanbs.wirelessautomate.client.storage.StorageListLayout.CELL;
import static io.github.matheusanbs.wirelessautomate.client.storage.StorageListLayout.EDGE;
import static io.github.matheusanbs.wirelessautomate.client.storage.StorageListLayout.FIXED_H;
import static io.github.matheusanbs.wirelessautomate.client.storage.StorageListLayout.FIXED_W;
import static io.github.matheusanbs.wirelessautomate.client.storage.StorageListLayout.GRID_X;
import static io.github.matheusanbs.wirelessautomate.client.storage.StorageListLayout.GRID_Y;
import static io.github.matheusanbs.wirelessautomate.client.storage.StorageListLayout.GRIP;
import static io.github.matheusanbs.wirelessautomate.client.storage.StorageListLayout.HEAD_Y;
import static io.github.matheusanbs.wirelessautomate.client.storage.StorageListLayout.MIN_COLS;
import static io.github.matheusanbs.wirelessautomate.client.storage.StorageListLayout.MIN_ROWS;
import static io.github.matheusanbs.wirelessautomate.client.storage.StorageListLayout.ROW_H;
import static io.github.matheusanbs.wirelessautomate.client.storage.StorageListLayout.SEARCH_Y;
import static io.github.matheusanbs.wirelessautomate.client.storage.StorageListLayout.SORT_W;
import static io.github.matheusanbs.wirelessautomate.client.storage.StorageListLayout.X0;

import io.github.matheusanbs.wirelessautomate.block.RouterTier;
import io.github.matheusanbs.wirelessautomate.client.FlatButton;
import io.github.matheusanbs.wirelessautomate.client.GuiPaint;
import io.github.matheusanbs.wirelessautomate.client.GuiText;
import io.github.matheusanbs.wirelessautomate.client.GuiThemes;
import io.github.matheusanbs.wirelessautomate.client.ResizeGrip;
import io.github.matheusanbs.wirelessautomate.client.ResizeHandle;
import io.github.matheusanbs.wirelessautomate.client.ThemeToggle;
import io.github.matheusanbs.wirelessautomate.client.storage.upgrade.UpgradePanels;
import io.github.matheusanbs.wirelessautomate.item.TierCoreItem;
import io.github.matheusanbs.wirelessautomate.menu.ListKind;
import io.github.matheusanbs.wirelessautomate.menu.StorageListMenu;
import io.github.matheusanbs.wirelessautomate.menu.StorageListView;
import io.github.matheusanbs.wirelessautomate.packet.StorageActionPayload;
import io.github.matheusanbs.wirelessautomate.storage.StorageKind;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

/**
 * Tela em lista dos armazenamentos por tipo (Baú, Tanque, Tanque Químico): os tipos guardados numa
 * grade com rolagem ({@link StorageGrid}), busca (nome, {@code @mod} ou {@code #tag}) e ordenação
 * (quantidade, nome, mod), a ocupação do tier ({@link UsageLine}) e o inventário do jogador. A lista vem
 * da {@link StorageListView} e só é refeita quando ela, a busca ou a ordem mudam ({@link ShownList}). O
 * que muda de um armazenamento para outro (desenhar, nomear e clicar num tipo) segue a {@link ListKind},
 * em {@link StorageKeys} e nos cliques da grade.
 *
 * <p>A janela é redimensionável: arraste a borda direita, a de baixo ou a alça do canto. Ela cresce
 * em torno do centro (a borda puxada segue o mouse e a oposta se move igual), então continua
 * centralizada; a grade ganha colunas e linhas, o inventário fica centralizado embaixo e o tamanho
 * é lembrado na sessão.
 *
 * <p>Baú: clique numa célula põe uma pilha no cursor; botão direito, meia pilha; Shift + clique,
 * uma pilha no inventário (com o Shift apertado a ordem fica travada, como no AE2), e Shift + arrastar pela grade, uma pilha de cada tipo por onde passar;
 * com um item no cursor, Shift + duplo clique (como no vanilla), o máximo do tipo que couber no
 * inventário (o cursor fica como está). A rodinha sobre um tipo tira um item
 * (para baixo) ou guarda um do inventário (para cima); sobre um slot do inventário, guarda um dele
 * (para baixo) ou puxa um do Baú (para cima), como o Mouse Tweaks; sobre o vazio, rola a lista. Com um item no cursor, clicar na grade guarda tudo (botão direito: um).
 * Tanques: com um recipiente no cursor (balde, tanque de outro mod), clicar num tipo enche o
 * recipiente com ele, ou o esvazia se ele já estiver cheio; botão direito esvazia um. Shift + clique
 * no inventário guarda a pilha (ou esvazia os recipientes). O servidor valida tudo.
 */
public class StorageListScreen extends AbstractContainerScreen<StorageListMenu<?>> {
    /** Ordem da lista; fica entre aberturas da tela na mesma sessão. */
    private static ListSort sort = ListSort.COUNT;
    /** Tamanho da grade escolhido pelo jogador; fica entre aberturas da tela na mesma sessão. */
    private static int savedCols = 13;
    private static int savedRows = 6;
    /** Shift simulado pelo teste de ponta a ponta, que não aperta teclas de verdade. */
    public static boolean e2eShift;

    private final boolean preview;
    private final List<FlatButton> buttons = new ArrayList<>();
    private final ShownList shown;
    private final StorageGrid grid;
    private EditBox searchBox;
    private FlatButton filterButton;
    private FlatButton sortButton;
    private int cols = savedCols;
    private int rows = savedRows;
    /** Arrasto de redimensionar: pela borda direita, pela de baixo ou pela alça do canto. */
    private final ResizeHandle resizeHandle = new ResizeHandle(GRIP, EDGE);
    /** A coluna dos upgrades e o painel aberto, dos lados do painel (só no Baú). */
    private final UpgradePanels upgrades;

    public StorageListScreen(StorageListMenu<?> menu, Inventory inventory, Component title) {
        this(menu, inventory, title, false);
    }

    /** @param preview sem servidor (captura de desenvolvimento): os cliques não fazem nada */
    public StorageListScreen(StorageListMenu<?> menu, Inventory inventory, Component title, boolean preview) {
        super(menu, inventory, title);
        this.preview = preview;
        this.inventoryLabelY = -1000;
        this.shown = new ShownList(menu, sort);
        this.grid = new StorageGrid(this, menu, shown);
        this.upgrades = new UpgradePanels(menu, preview, this::layout);
    }

    // ------------------------------------------------------------------ medidas (mudam com o tamanho)

    private int panelW() {
        return FIXED_W + cols * CELL;
    }

    private int panelH() {
        return FIXED_H + rows * CELL;
    }

    public int themeX() {
        return leftPos + imageWidth - 4 - ThemeToggle.SIZE;
    }

    public int themeY() {
        return topPos + HEAD_Y + (ROW_H - ThemeToggle.SIZE) / 2;
    }

    private int x1() {
        return panelW() - 8;
    }

    int barX() {
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
        return Math.max(MIN_COLS, (width - 8 - FIXED_W - upgrades.extraLeft() - upgrades.extraRight()) / CELL);
    }

    private int maxRows() {
        return Math.max(MIN_ROWS, (height - 8 - FIXED_H) / CELL);
    }

    static MutableComponent tr(String key, Object... args) {
        return Component.translatable("gui.wirelessautomate.storage." + key, args);
    }

    private StorageListView<?> view() {
        return menu.view();
    }

    private StorageKind storageKind() {
        return menu.kind().storage;
    }

    boolean items() {
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
        searchBox.setTextShadow(false); // tinta sobre porcelana, sem sombra
        inkVersion = -1;
        // Ao digitar, a lista volta ao topo.
        searchBox.setResponder(value -> grid.resetScroll());
        addRenderableWidget(searchBox);
        layout();
    }

    /**
     * Põe o painel no centro da janela do jogo, e os widgets e o inventário do jogador no lugar,
     * para o tamanho atual.
     */
    private void layout() {
        cols = Math.max(MIN_COLS, Math.min(cols, maxCols()));
        imageWidth = panelW();
        imageHeight = panelH();
        // a janela inteira (coluna, painel e painel do upgrade) fica no centro
        leftPos = (width - imageWidth - upgrades.extraLeft() - upgrades.extraRight()) / 2 + upgrades.extraLeft();
        topPos = (height - imageHeight) / 2;
        filterButton.setPosition(leftPos + x1() - ThemeToggle.SIZE - 4 - filterButton.getWidth(), topPos + HEAD_Y);
        sortButton.setPosition(leftPos + x1() - SORT_W, topPos + SEARCH_Y);
        searchBox.setPosition(leftPos + X0 + 4, topPos + SEARCH_Y + 3);
        searchBox.setWidth(x1() - X0 - SORT_W - 12);
        menu.placeInventory(invX(), invY());
        upgrades.layout(leftPos, topPos, imageWidth, imageHeight);
        grid.clampScroll();
    }

    private FlatButton add(FlatButton button) {
        buttons.add(button);
        return addRenderableWidget(button);
    }

    void send(StorageActionPayload.Action action, @Nullable Object key) {
        send(action, key, -1);
    }

    void send(StorageActionPayload.Action action, @Nullable Object key, int slot) {
        if (!preview) {
            PacketDistributor.sendToServer(new StorageActionPayload(menu.containerId, storageKind(), action,
                    Optional.ofNullable(key), slot));
        }
    }

    /** Refaz a lista mostrada se a visão, a busca ou a ordem mudaram. */
    private void rebuild() {
        if (shown.rebuild(searchBox.getValue(), sort)) {
            grid.clampScroll();
        }
    }

    /** Dica do tipo sob o mouse: a do item (com os componentes) ou o nome, e a quantidade. */
    private void renderKeyTooltip(GuiGraphics g, Object key, int mouseX, int mouseY) {
        List<Component> lines = new ArrayList<>();
        if (key instanceof ItemStack stack) {
            lines.addAll(getTooltipFromContainerItem(stack));
        } else {
            lines.add(StorageKeys.displayName(key));
            if (minecraft != null && minecraft.options.advancedItemTooltips) {
                String id = key instanceof FluidStack fluid ? BuiltInRegistries.FLUID.getKey(fluid.getFluid()).toString()
                        : String.valueOf(key);
                lines.add(Component.literal(id).withStyle(ChatFormatting.DARK_GRAY));
            }
        }
        lines.add(amountLine(shown.count(key)).copy().withStyle(ChatFormatting.AQUA));
        lines.add(tr(items() ? "click.hint" : "click.hint.tank").withStyle(ChatFormatting.DARK_GRAY));
        if (key instanceof ItemStack stack) {
            g.renderTooltip(font, lines, stack.getTooltipImage(), stack, mouseX, mouseY);
        } else {
            g.renderComponentTooltip(font, lines, mouseX, mouseY);
        }
    }

    /** Quantidade com a unidade para a dica: "Quantidade: 12.600.000" ou "12.600.000 mB". */
    private Component amountLine(long count) {
        return items() ? tr("amount", TierCoreItem.grouped(count))
                : tr("amount.mb", TierCoreItem.grouped(count));
    }

    // ------------------------------------------------------------------ ganchos do e2e e do JEI

    public int shownCount() {
        rebuild();
        return shown.size();
    }

    /** O tipo na posição {@code index} da lista mostrada e a quantidade dele (0 = apagado). */
    public Object shownKey(int index) {
        rebuild();
        return shown.get(index);
    }

    public long shownAmount(int index) {
        return shown.count(shown.get(index));
    }

    public EditBox searchBox() {
        return searchBox;
    }

    public int cols() {
        return cols;
    }

    public int rows() {
        return rows;
    }

    /** Ponto no meio da alça de redimensionar, em coordenadas da tela. */
    public int[] gripPoint() {
        return new int[] {leftPos + imageWidth - 4, topPos + imageHeight - 4};
    }

    /** Centro da célula {@code index} da página atual, em coordenadas da tela. */
    public int[] cellCenter(int index) {
        return grid.cellCenter(index);
    }

    public UpgradePanels upgradePanels() {
        return upgrades;
    }

    /** Um tipo da lista como ingrediente, com o canto da célula dele (para o JEI). */
    public record Hovered(Object ingredient, int x, int y) {
    }

    /**
     * O tipo sob o mouse como ingrediente do JEI (item, fluido ou {@code ChemicalStack}), para os atalhos dele
     * (receita, usos, favoritar) funcionarem na lista; {@code null} fora da grade ou num químico que não existe.
     */
    public @Nullable Hovered ingredientAt(double mouseX, double mouseY) {
        return grid.ingredientAt(mouseX, mouseY);
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
        if (upgrades.mouseClicked(mouseX, mouseY, button)
                || ThemeToggle.mouseClicked(themeX(), themeY(), mouseX, mouseY, button)) {
            return true;
        }
        if (button == 0 && resizeHandle.begin(mouseX, mouseY, leftPos, topPos, imageWidth, imageHeight)) {
            return true;
        }
        if (grid.mouseClicked(mouseX, mouseY, button)) {
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
        if (grid.mouseDragged(mouseX, mouseY, button) || upgrades.mouseDragged(mouseY)) {
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
        if (grid.mouseReleased(button) || upgrades.mouseReleased()) {
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (upgrades.mouseScrolled(mouseX, mouseY, scrollY)
                || grid.mouseScrolled(mouseX, mouseY, scrollY, hoveredSlot)) {
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
        if (upgrades.keyPressed(keyCode)) {
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    protected boolean hasClickedOutside(double mouseX, double mouseY, int guiLeft, int guiTop, int button) {
        return !upgrades.isInside(mouseX, mouseY) && super.hasClickedOutside(mouseX, mouseY, guiLeft, guiTop, button);
    }

    @Override
    protected void containerTick() {
        upgrades.tick();
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
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
        GuiPaint.ink(searchBox, tr("search.hint"));
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        if (inkVersion != GuiThemes.version()) {
            inkBoxes();
        }
        rebuild();
        GuiText.beginFrame();
        super.render(g, mouseX, mouseY, partialTick);
        if (ThemeToggle.hovered(themeX(), themeY(), mouseX, mouseY)) {
            g.renderComponentTooltip(font, ThemeToggle.tooltip(), mouseX, mouseY);
            return;
        }
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
        Object key = (menu.getCarried().isEmpty() || !items()) && !resizeHandle.dragging()
                ? grid.keyAt(mouseX, mouseY) : null;
        if (key != null) {
            renderKeyTooltip(g, key, mouseX, mouseY);
            return;
        }
        List<Component> upgradeTip = upgrades.tooltip(mouseX, mouseY);
        if (upgradeTip != null) {
            g.renderComponentTooltip(font, upgradeTip, mouseX, mouseY);
            return;
        }
        renderTooltip(g, mouseX, mouseY);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        // os textos são desenhados em renderBg, em coordenadas da tela; aqui só o que fica por cima dos slots
        upgrades.renderOver(g, mouseX, mouseY);
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
        ThemeToggle.render(g, themeX(), themeY(), mouseX, mouseY);

        // busca
        GuiPaint.box(g, x + X0, y + SEARCH_Y, searchBox.getWidth() + 8, ROW_H, GuiPaint.INSET,
                searchBox.isFocused() ? GuiPaint.ACCENT : GuiPaint.LINE);

        // grade e barra de rolagem
        grid.render(g, x, y, mouseX, mouseY, trim);

        // ocupação
        UsageLine.render(g, font, view(), items(), x, y, usageY(), barX());

        // alça de redimensionar: três riscos na diagonal, acesos com o mouse em cima ou arrastando
        ResizeGrip.renderDotted(g, x, y, imageWidth, imageHeight,
                resizeHandle.hover(mouseX, mouseY, x, y, imageWidth, imageHeight), GuiPaint.ACCENT);

        upgrades.renderBg(g, mouseX, mouseY, trim);

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

    private void paintButton(GuiGraphics g, FlatButton b, boolean hovered, Component text, int shift) {
        GuiPaint.box(g, b.getX(), b.getY(), b.getWidth(), b.getHeight(), GuiPaint.BUTTON,
                hovered ? GuiPaint.BUTTON_HOVER_BORDER : GuiPaint.BUTTON_BORDER);
        GuiPaint.textCentered(g, font, text, b.getX() + (b.getWidth() + shift) / 2 + 1, b.getY() + (b.getHeight() - 8) / 2,
                GuiPaint.FG);
    }
}
