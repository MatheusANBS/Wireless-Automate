package io.github.matheusanbs.wirelessautomate.client.filter;

import static io.github.matheusanbs.wirelessautomate.client.filter.FilterLayout.BOX_Y;
import static io.github.matheusanbs.wirelessautomate.client.filter.FilterLayout.EDGE;
import static io.github.matheusanbs.wirelessautomate.client.filter.FilterLayout.GRIP;
import static io.github.matheusanbs.wirelessautomate.client.filter.FilterLayout.HEAD_Y;
import static io.github.matheusanbs.wirelessautomate.client.filter.FilterLayout.IW;
import static io.github.matheusanbs.wirelessautomate.client.filter.FilterLayout.PANEL_W;
import static io.github.matheusanbs.wirelessautomate.client.filter.FilterLayout.SEG_H;
import static io.github.matheusanbs.wirelessautomate.client.filter.FilterLayout.X0;
import static io.github.matheusanbs.wirelessautomate.client.filter.FilterText.tr;

import io.github.matheusanbs.wirelessautomate.block.RouterBlock;
import io.github.matheusanbs.wirelessautomate.client.FlatButton;
import io.github.matheusanbs.wirelessautomate.client.GuiPaint;
import io.github.matheusanbs.wirelessautomate.client.GuiText;
import io.github.matheusanbs.wirelessautomate.client.GuiThemes;
import io.github.matheusanbs.wirelessautomate.client.ResizeGrip;
import io.github.matheusanbs.wirelessautomate.client.ResizeHandle;
import io.github.matheusanbs.wirelessautomate.client.ThemeToggle;
import io.github.matheusanbs.wirelessautomate.filter.Filter;
import io.github.matheusanbs.wirelessautomate.filter.FilterEntry;
import io.github.matheusanbs.wirelessautomate.filter.FilterEntry.ChemicalEntry;
import io.github.matheusanbs.wirelessautomate.filter.FilterEntry.FluidEntry;
import io.github.matheusanbs.wirelessautomate.filter.ItemRule;
import io.github.matheusanbs.wirelessautomate.menu.FilterMenu;
import io.github.matheusanbs.wirelessautomate.menu.FilterView;
import io.github.matheusanbs.wirelessautomate.network.Chemicals;
import io.github.matheusanbs.wirelessautomate.network.ResourceType;
import io.github.matheusanbs.wirelessautomate.packet.EditFilterPayload;
import io.github.matheusanbs.wirelessautomate.packet.FilterEntriesPayload;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

/**
 * Tela de filtro (especificação, "Telas da interface › Filtro"). À esquerda, as entradas em lista
 * ({@link EntryList}: ícone, nome, tipo e estoque, com busca); embaixo, o inventário do jogador
 * ({@link InventoryArea}; Shift + clique adiciona o item exato, pelo {@link FilterMenu#quickMoveStack}). À
 * direita, quatro abas:
 * <ul>
 *   <li><b>Entrada</b> ({@link EntryTab}): a selecionada, com o que ela pega, o estoque, Remover, e Ver tags
 *       (item) ou Editar (regra);</li>
 *   <li><b>Tags</b> ({@link TagsTab}): o inspetor e a busca em todas as tags do jogo, com a prévia dos itens
 *       da tag sob o mouse; {@code @texto} busca mods. Nos químicos, que não têm tags, a aba vira
 *       "Adicionar" (id do químico ou mod);</li>
 *   <li><b>Regra</b> ({@link RuleTab}, só itens): regra por propriedade ({@link ItemRule}), com o inventário
 *       aceso no que ela pega antes de adicionar;</li>
 *   <li><b>Mais</b> ({@link MoreTab}): Cartão de Filtro, componentes e Limpar.</li>
 * </ul>
 * A janela é redimensionável pelas bordas direita e de baixo e pela alça do canto, em torno do
 * centro (como a do Baú), e o tamanho fica lembrado na sessão.
 *
 * <p>Esta classe é a anfitriã: tamanho, a entrada escolhida, a aba aberta, o foco, o repasse dos eventos às
 * partes e os ganchos das capturas e do e2e. Tudo vem de {@link FilterMenu#view()}; quando
 * {@link FilterMenu#version()} muda a tela se acerta sem recriar widgets. Cada ação vira um pacote
 * ({@link EditFilterPayload}, {@link FilterEntriesPayload}, por {@link FilterActions}); o servidor valida e
 * manda a visão nova.
 *
 * <p>Ingredientes fantasmas (JEI): a parte pública com esse nome serve o plugin em
 * {@code compat/jei}: arrastar um ingrediente para a lista acrescenta a entrada exata, e para o
 * slot do inspetor (aba Tags) mostra as tags dele. Esta classe não conhece nenhuma classe do JEI.
 */
public class FilterScreen extends AbstractContainerScreen<FilterMenu> {
    public enum Tab {
        ENTRY,
        TAGS,
        RULE,
        MORE
    }

    private final List<FlatButton> buttons = new ArrayList<>();
    private final ResizeHandle resizeHandle = new ResizeHandle(GRIP, EDGE);
    private final FilterFrame frame = new FilterFrame(this);
    private final FilterText text;
    private final FilterActions actions;
    private final FilterHeader header;
    private final EntryList list;
    private final InventoryArea inventory;
    private final EntryTab entry;
    private final TagsTab tags;
    private final RuleTab rule;
    private final MoreTab more;
    private final EntrySelection selection = new EntrySelection(this);

    private ItemStack storageIcon = ItemStack.EMPTY;
    private int lastVersion = -1;
    /** Cor do tier; nulo = sem tier, e vale o destaque do tema (lido na hora, para seguir o tema). */
    private @Nullable Integer trim;
    private @Nullable Integer trimOverride;
    private Tab tab = Tab.ENTRY;
    /** Campo que recebe o foco depois do clique (a tela o daria ao botão clicado). */
    private @Nullable EditBox focusAfterClick;
    /** Versão do tema com que os campos de texto foram pintados ({@link GuiThemes#version()}). */
    private int inkVersion = -1;

    public FilterScreen(FilterMenu menu, Inventory inventory, Component title) {
        this(menu, inventory, title, false);
    }

    /**
     * @param preview sem servidor (captura de desenvolvimento): as mudanças valem só na visão local
     */
    public FilterScreen(FilterMenu menu, Inventory inventory, Component title, boolean preview) {
        super(menu, inventory, title);
        this.imageWidth = FilterSize.savedWidth();
        this.imageHeight = FilterSize.savedHeight();
        FilterMembers members = new FilterMembers(this);
        this.text = new FilterText(this, members);
        EntryIcons icons = new EntryIcons(this, members);
        this.actions = new FilterActions(menu, preview);
        this.header = new FilterHeader(this, text, actions);
        this.list = new EntryList(this, text, icons);
        this.tags = new TagsTab(this, members, actions);
        this.rule = new RuleTab(this, actions);
        this.inventory = new InventoryArea(this, text, rule);
        this.entry = new EntryTab(this, text, icons, members, actions, this.inventory, tags, rule);
        this.more = new MoreTab(this, actions);
        selection.bind(list, entry);
    }

    // ------------------------------------------------------------------ estado, lido pelas partes

    FilterView view() {
        return menu.view();
    }

    Filter filter() {
        return view().filter();
    }

    private ResourceType type() {
        return view().type();
    }

    boolean isItem() {
        return type() == ResourceType.ITEM;
    }

    boolean isFluid() {
        return type() == ResourceType.FLUID;
    }

    boolean isChemical() {
        return type() == ResourceType.CHEMICAL;
    }

    /** Fluidos e químicos contam em mB (estoque, passos, unidade); itens em unidades. */
    /** A unidade dos limites: mB para fluidos e químicos, itens para itens. */
    Component unit() {
        return inMb() ? tr("unit.mb") : tr("unit.items");
    }

    /** Manda os limites digitados que ainda não foram (o Voltar troca de tela, então vem antes). */
    void commitEdits() {
        entry.commitStock();
    }

    boolean inMb() {
        return isFluid() || isChemical();
    }

    boolean tabAvailable(Tab t) {
        return t != Tab.RULE || isItem();
    }

    void setTab(Tab t) {
        tab = t;
    }

    EntrySelection selection() {
        return selection;
    }

    FilterFrame frame() {
        return frame;
    }

    public int themeX() {
        return leftPos + imageWidth - 4 - ThemeToggle.SIZE;
    }

    public int themeY() {
        return topPos + HEAD_Y + (SEG_H - ThemeToggle.SIZE) / 2;
    }

    /** A visão mudou (ou é a primeira): recupera a seleção pelo alvo e acerta o que depende dela. */
    private void syncView() {
        lastVersion = menu.version();
        Filter f = filter();
        selection.recover(f);
        entry.syncLimitBoxes();
        if (f.isEmpty()) {
            more.disarm();
        }
        rule.dropStaleEditing(f);
        trim = resolveTrim();
        storageIcon = resolveStorageIcon();
        list.rebuildShown(true);
        selection.revealPending();
    }

    /** O bloco do armazenamento como item (o ícone da linha fixa), ou vazio fora do armazenamento. */
    ItemStack storageIcon() {
        return storageIcon;
    }

    private ItemStack resolveStorageIcon() {
        Optional<BlockPos> pos = view().router();
        if (view().isStorage() && pos.isPresent() && minecraft != null && minecraft.level != null
                && minecraft.level.isLoaded(pos.get())) {
            return new ItemStack(minecraft.level.getBlockState(pos.get()).getBlock());
        }
        return ItemStack.EMPTY;
    }

    int trim() {
        return trim != null ? trim : GuiPaint.ACCENT;
    }

    /** Cor do tier do roteador (ou do armazenamento), lida do bloco no mundo do cliente; sem ele, nulo (o destaque do tema). */
    private @Nullable Integer resolveTrim() {
        if (trimOverride != null) {
            return trimOverride;
        }
        Optional<BlockPos> pos = view().router();
        if (pos.isPresent() && minecraft != null && minecraft.level != null && minecraft.level.isLoaded(pos.get())) {
            BlockState state = minecraft.level.getBlockState(pos.get());
            // O roteador e os armazenamentos usam a mesma propriedade de tier.
            if (state.hasProperty(RouterBlock.TIER)) {
                return GuiPaint.tierColor(state.getValue(RouterBlock.TIER));
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ montagem

    @Override
    protected void init() {
        imageWidth = FilterSize.width(width);
        imageHeight = FilterSize.height(height);
        super.init();
        buttons.clear();
        if (!tabAvailable(tab)) {
            tab = Tab.ENTRY;
        }
        // a ordem dos widgets é a do desenho, do clique e das dicas
        header.addWidgets();
        list.addWidgets();
        entry.addWidgets();
        tags.addWidgets();
        rule.addWidgets();
        more.addWidgets();
        layout();
        // os campos ganham a largura de verdade antes do valor: o EditBox rola o texto pela largura que tem
        refresh();
        syncView();
        refresh();
    }

    FlatButton add(FlatButton button) {
        buttons.add(button);
        return addRenderableWidget(button);
    }

    /** Um valor salvo num campo: do início, para nada ficar escondido pela rolagem do texto. */
    static void show(EditBox box, String value) {
        box.setValue(value);
        box.moveCursorToStart(false);
    }

    /** Campo de texto sem borda (a moldura é desenhada pela tela). */
    EditBox box(Component name, int max, String value, Consumer<String> responder) {
        EditBox box = new EditBox(font, 0, 0, 10, 9, name);
        box.setBordered(false);
        box.setMaxLength(max);
        inkVersion = -1;
        box.setTextShadow(false); // tinta sobre porcelana, sem sombra
        box.setValue(value);
        box.setResponder(responder);
        return addRenderableWidget(box);
    }

    /** Centraliza o painel e põe o inventário do jogador no lugar, para o tamanho atual. */
    private void layout() {
        leftPos = (width - imageWidth) / 2;
        topPos = (height - imageHeight) / 2;
        inventory.place();
        list.clampScroll();
    }

    /** Posições, visibilidade e estado que dependem da visão, da aba ou do tamanho. */
    private void refresh() {
        int x = leftPos;
        int y = topPos;
        header.refresh(x, y, imageWidth);
        list.refresh(x, y);
        int px = x + frame.ix();
        int bottom = y + frame.innerBottom();
        int half = (IW - 3) / 2;
        entry.refresh(px, bottom, half);
        tags.refresh(px, y, bottom);
        rule.refresh(px, y, bottom, half);
        more.refresh(px, y, bottom, half);
    }

    // ------------------------------------------------------------------ ações da anfitriã

    void openTab(Tab t) {
        if (!tabAvailable(t)) {
            return;
        }
        commitEdits();
        unfocus();
        tab = t;
        more.disarm();
        if (t == Tab.TAGS) {
            setFocused(tags.searchBox());
            focusAfterClick = tags.searchBox();
        }
    }

    void unfocus() {
        if (getFocused() instanceof EditBox box) {
            box.setFocused(false);
        }
        setFocused(null);
    }

    /** Põe o item no inspetor (cópia de 1); num filtro de fluidos, inspeciona o fluido que ele contém. */
    public void inspect(ItemStack stack) {
        tags.inspect(stack);
    }

    /** A dica de um item como o inventário a mostra. */
    List<Component> itemTooltip(ItemStack stack) {
        return getTooltipFromContainerItem(stack);
    }

    @Override
    public void setFocused(@Nullable GuiEventListener listener) {
        GuiEventListener before = getFocused();
        super.setFocused(listener);
        if (before == listener) {
            return;
        }
        rule.picker().focusChanged(before, listener);
        rule.focusChanged(before, listener);
    }

    // ------------------------------------------------------------------ entrada

    /** Redimensiona em torno do centro, como o Baú ({@link FilterSize#drag}). */
    private void dragResize(double mouseX, double mouseY) {
        int[] size = FilterSize.drag(resizeHandle, mouseX, mouseY, imageWidth, imageHeight, width, height);
        if (size[0] != imageWidth || size[1] != imageHeight) {
            imageWidth = size[0];
            imageHeight = size[1];
            layout();
        }
    }

    @Override
    public void resize(Minecraft minecraft, int width, int height) {
        resizeHandle.end();
        super.resize(minecraft, width, height);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (ThemeToggle.mouseClicked(themeX(), themeY(), mouseX, mouseY, button)) {
            return true;
        }
        if (entry.isLimitBox(getFocused()) && !entry.overLimitBox(mouseX, mouseY)) {
            entry.commitStock();
            unfocus();
        }
        // lista de sugestões aberta: o clique nela escolhe e não vaza para os widgets de baixo
        if (rule.picker().mouseClicked(mouseX, mouseY, button) || rule.suggestionClicked(mouseX, mouseY, button)) {
            return true;
        }
        if (button == 0 && resizeHandle.begin(mouseX, mouseY, leftPos, topPos, imageWidth, imageHeight)) {
            return true;
        }
        // Ctrl + clique num item do inventário: inspeciona as tags dele (não mexe no item).
        if (hasControlDown() && hoveredSlot != null && hoveredSlot.hasItem() && !isChemical()) {
            inspect(hoveredSlot.getItem());
            openTab(Tab.TAGS);
            return true;
        }
        if (tags.mouseClicked(mouseX, mouseY, button) || list.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        more.disarmUnlessOver(mouseX, mouseY);
        boolean handled = super.mouseClicked(mouseX, mouseY, button);
        // botão direito volta a porcentagem
        if (button == 1 && tab == Tab.RULE && rule.rightClickPercent(mouseX, mouseY)) {
            return true;
        }
        rule.picker().reopenOnClick(mouseX, mouseY);
        rule.reopenOnClick(mouseX, mouseY);
        // a tela dá o foco ao botão clicado; os campos ficam com ele só quando clicados
        if (focusAfterClick != null) {
            setFocused(focusAfterClick);
            focusAfterClick = null;
        } else if (getFocused() instanceof EditBox box) {
            if (!box.isMouseOver(mouseX, mouseY)) {
                unfocus();
            }
        } else {
            setFocused(null);
        }
        return handled;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (resizeHandle.dragging()) {
            dragResize(mouseX, mouseY);
            return true;
        }
        if (list.mouseDragged(mouseY)) {
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (resizeHandle.dragging() || list.pressed()) {
            resizeHandle.end();
            list.release();
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int step = (int) -Math.signum(scrollY);
        if (rule.picker().scrollSuggestions(mouseX, mouseY, step) || rule.scrollSuggestions(mouseX, mouseY, step)
                || entry.mouseScrolled(mouseX, mouseY, -step)
                || list.mouseScrolled(mouseX, mouseY, step)
                || tags.mouseScrolled(mouseX, mouseY, step)
                || rule.mouseScrolled(mouseX, mouseY, scrollX, scrollY, step)) {
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (getFocused() instanceof EditBox box && box.isFocused()) {
            boolean enter = keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER;
            if (rule.picker().keyPressed(box, keyCode, enter) || rule.suggestionKey(box, keyCode, enter)) {
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                entry.resetLimitBox(box);
                unfocus();
            } else if (enter && entry.isLimitBox(box)) {
                entry.commitStock();
                unfocus();
            } else if (enter && box == tags.searchBox()) {
                tags.addTyped();
            } else if (enter && (box == rule.scopeBox() || box == rule.componentBox())) {
                rule.submitRule();
            } else if (enter) {
                unfocus();
            } else {
                box.keyPressed(keyCode, scanCode, modifiers);
            }
            // nenhuma tecla fecha a tela enquanto se digita (nem a do inventário)
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (getFocused() instanceof EditBox box && box.isFocused()) {
            return box.charTyped(codePoint, modifiers);
        }
        return super.charTyped(codePoint, modifiers);
    }

    @Override
    public void removed() {
        commitEdits();
        super.removed();
    }

    // ------------------------------------------------------------------ desenho

    /** Cor do texto e das dicas dos campos: o EditBox guarda a cor, então segue o tema por aqui. */
    private void inkBoxes() {
        inkVersion = GuiThemes.version();
        GuiPaint.ink(list.searchBox(), tr("search.hint"));
        GuiPaint.ink(entry.stockBox(), tr("stock.none"));
        GuiPaint.ink(entry.keepBox(), tr("stock.none"));
        GuiPaint.ink(entry.globalBox(), tr("stock.none"));
        GuiPaint.ink(tags.searchBox(), tr(isChemical() ? "tags.search.chemical" : "tags.search.hint"));
        GuiPaint.ink(rule.picker().enchantBox(), tr("rule.enchantment.hint"));
        GuiPaint.ink(rule.scopeBox(), tr("rule.scope.hint"));
        GuiPaint.ink(rule.componentBox(), tr("rule.component.hint"));
        GuiPaint.ink(rule.picker().levelBox());
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        if (inkVersion != GuiThemes.version()) {
            inkBoxes();
        }
        if (menu.version() != lastVersion) {
            syncView();
        }
        list.rebuildShown(false);
        if (tab == Tab.TAGS) {
            tags.rebuildCandidates();
        }
        refresh();
        GuiText.beginFrame();
        tags.hover(mouseX, mouseY);
        super.render(g, mouseX, mouseY, partialTick);
        rule.picker().renderSuggestions(g, mouseX, mouseY);
        rule.renderSuggestions(g, mouseX, mouseY);
        if (resizeHandle.dragging()) {
            return;
        }
        renderHoverTip(g, mouseX, mouseY);
    }

    /** A dica do que está sob o mouse, da mais de cima para a de baixo; no máximo uma. */
    private void renderHoverTip(GuiGraphics g, int mouseX, int mouseY) {
        if (ThemeToggle.hovered(themeX(), themeY(), mouseX, mouseY)) {
            g.renderComponentTooltip(font, ThemeToggle.tooltip(), mouseX, mouseY);
            return;
        }
        if (rule.picker().tooltip(mouseX, mouseY) || rule.suggestionTooltip(mouseX, mouseY)) {
            return;
        }
        for (FlatButton button : buttons) {
            if (button.visible && button.isHovered()) {
                Component tooltip = button.currentTooltip();
                if (tooltip != null) {
                    setTooltipForNextRenderPass(font.split(tooltip, 220));
                }
                return;
            }
        }
        if (resizeHandle.at(mouseX, mouseY, leftPos, topPos, imageWidth, imageHeight) != null) {
            setTooltipForNextRenderPass(tr("resize.tooltip"));
            return;
        }
        int index = list.entryAt(mouseX, mouseY);
        if (index >= 0) {
            setTooltipForNextRenderPass(text.entryTooltip(filter().entries().get(index)));
            return;
        }
        if (tags.tooltip(g, mouseX, mouseY)) {
            return;
        }
        if (FilterText.JEI && mouseX >= leftPos + X0 && mouseX < leftPos + X0 + frame.listW()
                && mouseY >= topPos + frame.invTop() - 11 && mouseY < topPos + frame.invTop() - 1) {
            setTooltipForNextRenderPass(font.split(tr("jei.tooltip." + (isChemical() ? "chemical" : isFluid() ? "fluid" : "item")), 200));
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
        GuiPaint.panel(g, leftPos, topPos, imageWidth, imageHeight, trim());
        header.render(g, mouseX, mouseY);
        list.render(g, mouseX, mouseY);
        inventory.render(g);

        // painel da direita
        GuiPaint.box(g, leftPos + frame.panelX(), topPos + BOX_Y, PANEL_W, imageHeight - X0 - BOX_Y, GuiPaint.INSET, GuiPaint.LINE);
        switch (tab) {
            case ENTRY -> entry.render(g);
            case TAGS -> tags.render(g, mouseX, mouseY);
            case RULE -> rule.render(g);
            case MORE -> more.render(g);
        }
        ResizeGrip.renderSolid(g, leftPos, topPos, imageWidth, imageHeight,
                resizeHandle.hover(mouseX, mouseY, leftPos, topPos, imageWidth, imageHeight), GuiPaint.ACCENT);
    }

    // ------------------------------------------------------------------ ingredientes fantasmas (JEI)

    /**
     * A entrada que um ingrediente de fora (o JEI) acrescenta a este filtro: {@link ItemStack} num
     * filtro de itens; {@link FluidStack}, ou um item que contém fluido (balde), num de fluidos.
     * Vazio se não serve.
     */
    public Optional<FilterEntry> ghostEntry(Object ingredient) {
        return switch (ingredient) {
            case ItemStack stack -> FilterMenu.entryFor(view().type(), stack);
            case FluidStack stack when isFluid() && !stack.isEmpty() -> Optional.of(new FluidEntry(stack, 0));
            default -> isChemical()
                    ? Chemicals.ingredientId(ingredient).map(id -> new ChemicalEntry(id, 0))
                    : Optional.empty();
        };
    }

    /** Onde soltar um ingrediente para acrescentá-lo: a lista de entradas, em coordenadas da tela. */
    public Rect2i ghostArea() {
        return list.ghostArea();
    }

    /** O slot do inspetor, se a aba Tags está aberta e o filtro tem tags (itens ou fluidos); senão, nulo. */
    public @Nullable Rect2i inspectArea() {
        return tags.inspectArea();
    }

    /** O ingrediente serve para o inspetor (item num filtro de itens; fluido ou balde num de fluidos)? */
    public boolean canInspect(Object ingredient) {
        return tags.canInspect(ingredient);
    }

    /** Põe um ingrediente de fora no inspetor (só a tela; nada vai ao servidor). */
    public void inspectGhost(Object ingredient) {
        tags.inspectGhost(ingredient);
    }

    /**
     * Acrescenta uma entrada vinda de fora, sem o jogador ter o item; o servidor valida e manda a
     * visão nova, e a entrada fica selecionada quando chegar. Duplicada, só é selecionada.
     */
    public void addGhost(FilterEntry entry) {
        selection.pendingReveal(entry);
        if (filter().indexOf(entry) >= 0) {
            selection.revealPending();
        } else {
            actions.addEntry(entry);
        }
    }

    /** O que o painel ocupa, para o JEI não se sobrepor. */
    public List<Rect2i> extraAreas() {
        return List.of(new Rect2i(leftPos, topPos, imageWidth, imageHeight));
    }

    // ------------------------------------------------------------------ captura e teste de ponta a ponta

    public void previewTrim(int color) {
        trimOverride = color;
        trim = color;
    }

    public void previewSelect(int index) {
        selection.select(index);
    }

    public void previewTab(Tab t) {
        openTab(t);
    }

    public void previewTagSearch(String text) {
        openTab(Tab.TAGS);
        tags.searchBox().setValue(text);
    }

    public void previewInspect(ItemStack stack) {
        inspect(stack);
        openTab(Tab.TAGS);
    }

    public void previewCheck(int candidate) {
        tags.previewCheck(candidate);
    }

    public void previewRule(ItemRule draft) {
        rule.preview(draft);
    }

    public void previewComponentSamples(String id, List<Object> trees) {
        rule.previewSamples(id, trees);
    }

    public void previewComponent(List<String> written, String typing) {
        rule.previewComponent(written, typing);
    }

    public void previewArmClear() {
        more.armForPreview();
    }

    public Tab tab() {
        return tab;
    }

    public EditBox stockBox() {
        return entry.stockBox();
    }

    /** O campo do limite por tipo (aparece com a linha fixa escolhida). */
    public EditBox typeLimitBox() {
        return entry.globalBox();
    }

    /** O índice da linha fixa do limite por tipo, para {@link #previewEntryCenter}. */
    public static final int GLOBAL_ROW = EntrySelection.GLOBAL;

    public EditBox tagSearchBox() {
        return tags.searchBox();
    }

    public EditBox scopeBox() {
        return rule.scopeBox();
    }

    public EditBox enchantBox() {
        return rule.picker().enchantBox();
    }

    public EditBox levelBox() {
        return rule.picker().levelBox();
    }

    /** Nomes das sugestões da lista aberta (todas, não só as cinco à vista); vazia com a lista fechada. */
    public List<String> suggestionLabels() {
        return rule.picker().suggestionLabels();
    }

    public ItemRule ruleDraft() {
        return rule.draft();
    }

    /** Linhas da aba Tags (rótulos), para o teste de ponta a ponta. */
    public List<String> candidateLabels() {
        return tags.candidateLabels();
    }

    /** Rótulos das linhas marcadas (fora as que já estão no filtro). */
    public List<String> checkedLabels() {
        return tags.checkedLabels();
    }

    /** Centro da linha {@code index} da aba Tags visível, em coordenadas da tela. */
    public int[] candidateCenter(int index) {
        return tags.candidateCenter(index);
    }

    /** Centro do slot do inspetor, em coordenadas da tela. */
    public int[] inspectorCenter() {
        return tags.inspectorCenter();
    }

    /** Centro da linha da entrada {@code index} visível, para simular o mouse em cima na captura. */
    public int[] previewEntryCenter(int index) {
        return list.entryCenter(index);
    }

    /** Ponto no meio da alça de redimensionar, em coordenadas da tela. */
    public int[] gripPoint() {
        return new int[] {leftPos + imageWidth - 4, topPos + imageHeight - 4};
    }

    public int panelWidth() {
        return imageWidth;
    }

    public int panelHeight() {
        return imageHeight;
    }

    /** O inventário acompanhou a tela: o primeiro slot da mochila no lugar certo. */
    public boolean inventoryFollows() {
        return inventory.follows();
    }
}
