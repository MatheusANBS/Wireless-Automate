package io.github.matheusanbs.wirelessautomate.client;

import com.mojang.blaze3d.systems.RenderSystem;
import io.github.matheusanbs.wirelessautomate.block.RouterBlock;
import io.github.matheusanbs.wirelessautomate.filter.Filter;
import io.github.matheusanbs.wirelessautomate.filter.FilterEntry;
import io.github.matheusanbs.wirelessautomate.filter.FilterEntry.FluidEntry;
import io.github.matheusanbs.wirelessautomate.filter.FilterEntry.ItemEntry;
import io.github.matheusanbs.wirelessautomate.filter.FilterEntry.ModEntry;
import io.github.matheusanbs.wirelessautomate.filter.FilterEntry.TagEntry;
import io.github.matheusanbs.wirelessautomate.menu.FilterMenu;
import io.github.matheusanbs.wirelessautomate.menu.FilterView;
import io.github.matheusanbs.wirelessautomate.network.ResourceType;
import io.github.matheusanbs.wirelessautomate.packet.AddFilterEntryPayload;
import io.github.matheusanbs.wirelessautomate.packet.EditFilterPayload;
import io.github.matheusanbs.wirelessautomate.packet.EditFilterPayload.Op;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

/**
 * Tela de filtro (especificação, "Telas da interface › Filtro"): lista branca ou negra, componentes
 * (só itens), grade de entradas com rolagem, a entrada selecionada com estoque e Remover, regras por
 * tag ou mod e o Cartão de Filtro recolhidos em "Mais", e o inventário do jogador embaixo (Shift +
 * clique adiciona, pelo {@link FilterMenu#quickMoveStack}).
 *
 * <p>Tudo vem de {@link FilterMenu#view()}; quando {@link FilterMenu#version()} muda a tela se
 * acerta sem recriar widgets, mantendo rolagem, seleção e o que está aberto. Cada ação vira um
 * {@link EditFilterPayload}; o servidor valida e manda a visão nova.
 *
 * <p>"Aba JEI": com o JEI instalado, a dica do inventário passa a citá-lo, e o plugin em
 * {@code compat/jei} usa a parte pública "Ingredientes fantasmas" desta tela: arrastar um ingrediente
 * para a grade (ou Shift + clique nele na lista) acrescenta a entrada, mesmo sem ter o item, pelo
 * {@link AddFilterEntryPayload}. Esta classe não conhece nenhuma classe do JEI.
 */
public class FilterScreen extends AbstractContainerScreen<FilterMenu> {
    private static final int W = 256;
    private static final int H = 232;
    private static final int X0 = 9;
    private static final int X1 = W - 9;
    private static final int HEAD_Y = 8;
    private static final int PILL_H = 13;
    private static final int ROW2_Y = 26;
    private static final int SEG_H = 15;
    private static final int SEP_Y = 45;
    private static final int LABEL_Y = 49;
    // grade de entradas
    private static final int CELL = 18;
    private static final int COLS = 6;
    private static final int ROWS = 4;
    private static final int GRID_Y = 59;
    private static final int GRID_W = COLS * CELL + 2;
    private static final int GRID_H = ROWS * CELL + 2;
    private static final int BAR_X = X0 + GRID_W + 2;
    private static final int BAR_W = 4;
    // coluna da direita: entrada selecionada ou "Mais"
    private static final int RX = X0 + GRID_W + 2 + BAR_W + 4;
    private static final int RW = X1 - RX;
    private static final int BOX_Y = 46;
    private static final int BOX_H = 74;
    private static final int PAD = 5;
    private static final int IX = RX + PAD;
    private static final int IW = RW - 2 * PAD;
    private static final int ROW_H = 14;
    private static final int STOCK_Y = 83;
    private static final int REMOVE_Y = 100;
    private static final int RULE_Y = 60;
    private static final int ADD_Y = 77;
    private static final int CARD_Y = 103;
    private static final int MORE_Y = 123;
    private static final int HINT_Y = 137;

    /** Contorno quando não se sabe o tier (cartão, roteador fora do alcance): o destaque do rascunho. */
    private static final int ACCENT = 0xFF45D6CC;
    private static final int DANGER = 0xFFE5534B;
    private static final int SEL_BOX = 0xFF151A21;
    private static final long CLEAR_CONFIRM_MS = 3000;
    /** JEI instalado: a dica fala dele e o plugin em {@code compat/jei} oferece arrastar e clicar. */
    private static final boolean JEI = ModList.get().isLoaded("jei");

    private final boolean preview;
    private final List<FlatButton> buttons = new ArrayList<>();
    private final Map<String, List<ItemStack>> itemIcons = new HashMap<>();
    private final Map<String, List<FluidStack>> fluidIcons = new HashMap<>();

    private int lastVersion = -1;
    private int trim = ACCENT;
    private @Nullable Integer trimOverride;
    private @Nullable FilterEntry selected;
    private int selectedIndex = -1;
    private int scrollRow;
    private boolean draggingBar;
    /** O clique foi da grade: a soltura também é nossa, e não do contêiner. */
    private boolean gridPress;
    private boolean moreOpen;
    private long clearArmedUntil;
    private String stockDraft = "";
    private String ruleDraft = "";
    /** Entrada vinda do JEI: quando ela aparecer na visão, fica selecionada e à vista. */
    private @Nullable FilterEntry pendingReveal;

    private FlatButton backButton;
    private FlatButton whiteButton;
    private FlatButton blackButton;
    private FlatButton componentsButton;
    private FlatButton minusButton;
    private FlatButton plusButton;
    private FlatButton removeButton;
    private FlatButton moreButton;
    private FlatButton clearButton;
    private FlatButton addRuleButton;
    private FlatButton importButton;
    private FlatButton exportButton;
    private EditBox stockBox;
    private EditBox ruleBox;

    public FilterScreen(FilterMenu menu, Inventory inventory, Component title) {
        this(menu, inventory, title, false);
    }

    /**
     * @param preview sem servidor (captura de desenvolvimento): as mudanças valem só na visão local
     */
    public FilterScreen(FilterMenu menu, Inventory inventory, Component title, boolean preview) {
        super(menu, inventory, title);
        this.preview = preview;
        this.imageWidth = W;
        this.imageHeight = H;
    }

    // ------------------------------------------------------------------ estado

    private FilterView view() {
        return menu.view();
    }

    private Filter filter() {
        return view().filter();
    }

    private boolean isFluid() {
        return view().type() == ResourceType.FLUID;
    }

    private static Component tr(String key, Object... args) {
        return Component.translatable("gui.wirelessautomate.filter." + key, args);
    }

    private static Component typeName(ResourceType type) {
        return Component.translatable("gui.wirelessautomate.router.type." + type.name().toLowerCase(Locale.ROOT));
    }

    /** "Baixo · Itens" ou "Cartão de Filtro · Itens". */
    private Component context() {
        FilterView v = view();
        Component where = v.face().isPresent()
                ? Component.translatable("gui.wirelessautomate.router.face." + v.face().get().getName())
                : Component.translatable("item.wirelessautomate.filter_card");
        return where.copy().append(" · ").append(typeName(v.type()));
    }

    private int maxScroll() {
        int rows = (filter().entries().size() + COLS - 1) / COLS;
        return Math.max(0, rows - ROWS);
    }

    private boolean clearArmed() {
        return clearArmedUntil > Util.getMillis();
    }

    /** A visão mudou (ou é a primeira): recupera a seleção pelo alvo e acerta o que depende dela. */
    private void syncView() {
        lastVersion = menu.version();
        Filter f = filter();
        selectedIndex = selected == null ? -1 : f.indexOf(selected);
        selected = selectedIndex < 0 ? null : f.entries().get(selectedIndex);
        if (!stockBox.isFocused()) {
            stockBox.setValue(stockText());
        }
        if (f.isEmpty()) {
            clearArmedUntil = 0;
        }
        scrollRow = Math.max(0, Math.min(scrollRow, maxScroll()));
        trim = resolveTrim();
        revealPending();
    }

    /** Seleciona e rola até a entrada que veio do JEI, se ela já está no filtro. */
    private void revealPending() {
        if (pendingReveal == null) {
            return;
        }
        int index = filter().indexOf(pendingReveal);
        if (index < 0) {
            return;
        }
        pendingReveal = null;
        select(filter().entries().get(index), index);
        int row = index / COLS;
        if (row < scrollRow) {
            scrollRow = row;
        } else if (row >= scrollRow + ROWS) {
            scrollRow = Math.min(maxScroll(), row - ROWS + 1);
        }
    }

    /** Cor do tier do roteador, lida do bloco no mundo do cliente; sem ele, o destaque padrão. */
    private int resolveTrim() {
        if (trimOverride != null) {
            return trimOverride;
        }
        Optional<BlockPos> pos = view().router();
        if (pos.isPresent() && minecraft != null && minecraft.level != null && minecraft.level.isLoaded(pos.get())) {
            BlockState state = minecraft.level.getBlockState(pos.get());
            if (state.getBlock() instanceof RouterBlock) {
                return GuiPaint.tierColor(state.getValue(RouterBlock.TIER));
            }
        }
        return ACCENT;
    }

    // ------------------------------------------------------------------ montagem

    @Override
    protected void init() {
        super.init();
        buttons.clear();
        int x = leftPos;
        int y = topPos;

        backButton = add(new FlatButton(x + X1 - 40, y + HEAD_Y, 40, PILL_H, Component.empty(),
                (g, b, hovered) -> paintTextButton(g, b, hovered, backLabel(), false), this::back)
                .tooltip(() -> view().isCard() ? tr("close.tooltip") : tr("back.tooltip")));

        Component white = tr("list.white");
        Component black = tr("list.black");
        int whiteW = font.width(white) + 12;
        int blackW = font.width(black) + 12;
        whiteButton = add(new FlatButton(x + X0, y + ROW2_Y, whiteW, SEG_H, white,
                (g, b, hovered) -> paintSegment(g, b, hovered, white, filter().listMode() == Filter.ListMode.WHITELIST),
                () -> setListMode(Filter.ListMode.WHITELIST)).tooltip(() -> tr("list.white.tooltip")));
        blackButton = add(new FlatButton(x + X0 + whiteW + 2, y + ROW2_Y, blackW, SEG_H, black,
                (g, b, hovered) -> paintSegment(g, b, hovered, black, filter().listMode() == Filter.ListMode.BLACKLIST),
                () -> setListMode(Filter.ListMode.BLACKLIST)).tooltip(() -> tr("list.black.tooltip")));
        Component components = tr("components");
        int compW = 9 + 4 + font.width(components) + 6;
        componentsButton = add(new FlatButton(x + X1 - compW, y + ROW2_Y, compW, SEG_H, components,
                this::paintComponents, this::toggleComponents)
                .tooltip(() -> filter().matchComponents() ? tr("components.on.tooltip") : tr("components.off.tooltip")));

        // entrada selecionada: estoque e Remover
        minusButton = add(new FlatButton(x + IX, y + STOCK_Y, 14, ROW_H, tr("stock.decrease"),
                (g, b, hovered) -> paintTextButton(g, b, hovered, Component.literal("-"), false),
                () -> stepStock(-1)).tooltip(() -> stepTooltip("stock.decrease.tooltip")));
        plusButton = add(new FlatButton(x + IX + IW - 14, y + STOCK_Y, 14, ROW_H, tr("stock.increase"),
                (g, b, hovered) -> paintTextButton(g, b, hovered, Component.literal("+"), false),
                () -> stepStock(1)).tooltip(() -> stepTooltip("stock.increase.tooltip")));
        stockBox = new EditBox(font, x + IX + 20, y + STOCK_Y + 3, IW - 40, 9, tr("stock"));
        stockBox.setBordered(false);
        stockBox.setMaxLength(15);
        stockBox.setTextColor(GuiPaint.FG);
        stockBox.setFilter(s -> s.chars().allMatch(Character::isDigit));
        stockBox.setHint(tr("stock.none").copy().withColor(GuiPaint.DISABLED));
        stockBox.setValue(stockDraft);
        stockBox.setResponder(value -> stockDraft = value);
        addRenderableWidget(stockBox);
        removeButton = add(new FlatButton(x + IX, y + REMOVE_Y, IW, ROW_H, tr("remove"),
                (g, b, hovered) -> paintDangerButton(g, b, hovered, tr("remove"), false), this::removeSelected)
                .tooltip(() -> tr("remove.tooltip")));

        // recolhido: regra por tag ou mod e o cartão
        ruleBox = new EditBox(font, x + IX + 4, y + RULE_Y + 3, IW - 8, 9, tr("rule"));
        ruleBox.setBordered(false);
        ruleBox.setMaxLength(EditFilterPayload.MAX_TEXT);
        ruleBox.setTextColor(GuiPaint.FG);
        ruleBox.setHint(tr("rule.hint").copy().withColor(GuiPaint.DISABLED));
        ruleBox.setValue(ruleDraft);
        ruleBox.setResponder(value -> ruleDraft = value);
        addRenderableWidget(ruleBox);
        addRuleButton = add(new FlatButton(x + IX, y + ADD_Y, IW, ROW_H, tr("rule.add"),
                (g, b, hovered) -> paintPrimary(g, b, hovered, tr("rule.add")), this::addRule)
                .tooltip(() -> parseRule(ruleDraft) == null && !ruleDraft.isBlank() ? tr("rule.invalid") : tr("rule.add.tooltip")));
        int half = (IW - 2) / 2;
        importButton = add(new FlatButton(x + IX, y + CARD_Y, half, ROW_H, tr("card.import"),
                (g, b, hovered) -> paintTextButton(g, b, hovered, tr("card.import"), false), () -> send(Op.IMPORT_CARD, 0, 0, ""))
                .tooltip(() -> view().hasCard() ? tr("card.import.tooltip") : tr("card.missing")));
        exportButton = add(new FlatButton(x + IX + IW - half, y + CARD_Y, half, ROW_H, tr("card.export"),
                (g, b, hovered) -> paintTextButton(g, b, hovered, tr("card.export"), false), () -> send(Op.EXPORT_CARD, 0, 0, ""))
                .tooltip(() -> view().hasCard() ? tr("card.export.tooltip") : tr("card.missing")));

        moreButton = add(new FlatButton(x + RX, y + MORE_Y, 40, 12, tr("more"), this::paintMore, this::toggleMore)
                .tooltip(() -> view().isCard() ? tr("more.card.tooltip") : tr("more.tooltip")));
        clearButton = add(new FlatButton(x + X1 - 40, y + MORE_Y - 1, 40, 12, tr("clear"),
                (g, b, hovered) -> paintDangerButton(g, b, hovered, clearArmed() ? tr("clear.confirm") : tr("clear"), clearArmed()),
                this::clear).tooltip(() -> clearArmed() ? tr("clear.confirm.tooltip", filter().entries().size()) : tr("clear.tooltip")));

        syncView();
        refresh();
    }

    private FlatButton add(FlatButton button) {
        buttons.add(button);
        return addRenderableWidget(button);
    }

    private Component backLabel() {
        return view().isCard() ? tr("close") : tr("back");
    }

    /** Posições, visibilidade e estado que dependem da visão ou da tela. */
    private void refresh() {
        Component back = backLabel();
        int backW = font.width(back) + 12;
        backButton.setWidth(backW);
        backButton.setX(leftPos + X1 - backW);
        backButton.setMessage(back);

        boolean items = !isFluid();
        componentsButton.visible = items;

        boolean showSelection = !moreOpen && selected != null;
        minusButton.visible = plusButton.visible = removeButton.visible = stockBox.visible = showSelection;
        minusButton.active = showSelection && selected.stock() > 0;
        if (isFluid()) {
            stockBox.setWidth(IW - 40 - font.width(tr("unit.mb")) - 2);
        }

        ruleBox.visible = addRuleButton.visible = moreOpen;
        addRuleButton.active = parseRule(ruleDraft) != null;
        boolean cardRow = moreOpen && !view().isCard();
        importButton.visible = exportButton.visible = cardRow;
        importButton.active = exportButton.active = view().hasCard();

        Component more = moreOpen ? tr("less") : tr("more");
        moreButton.setWidth(11 + font.width(more) + 2);
        moreButton.setMessage(more);

        Component clear = clearArmed() ? tr("clear.confirm") : tr("clear");
        int clearW = font.width(clear) + 10;
        clearButton.setWidth(clearW);
        clearButton.setX(leftPos + X1 - clearW);
        clearButton.setMessage(clear);
        clearButton.active = !filter().isEmpty();
    }

    // ------------------------------------------------------------------ ações

    private void send(Op op, int index, long value, String text) {
        EditFilterPayload payload = new EditFilterPayload(menu.containerId, op, index, value, text);
        if (preview) {
            applyLocally(payload);
        } else {
            PacketDistributor.sendToServer(payload);
        }
    }

    /** Captura sem servidor: aplica a edição na visão local, como o servidor faria. */
    private void applyLocally(EditFilterPayload p) {
        Filter f = filter();
        Filter next = switch (p.op()) {
            case ADD_TAG -> {
                ResourceLocation tag = ResourceLocation.tryParse(p.text());
                yield tag == null ? f : f.withEntry(new TagEntry(tag, 0));
            }
            case ADD_MOD -> f.withEntry(new ModEntry(p.text(), 0));
            case REMOVE -> f.withoutEntry(p.index());
            case SET_STOCK -> f.withStock(p.index(), p.value());
            case SET_LIST_MODE -> f.withListMode(p.value() == 0 ? Filter.ListMode.WHITELIST : Filter.ListMode.BLACKLIST);
            case SET_COMPONENTS -> f.withMatchComponents(p.value() != 0);
            case CLEAR -> f.cleared();
            case IMPORT_CARD, EXPORT_CARD, BACK -> f;
        };
        FilterView v = view();
        menu.applyView(new FilterView(v.type(), v.router(), v.face(), next, v.hasCard()));
    }

    private void back() {
        if (view().isCard()) {
            onClose();
        } else if (!preview) {
            send(Op.BACK, 0, 0, "");
        }
    }

    private void setListMode(Filter.ListMode mode) {
        if (filter().listMode() != mode) {
            send(Op.SET_LIST_MODE, 0, mode == Filter.ListMode.WHITELIST ? 0 : 1, "");
        }
    }

    private void toggleComponents() {
        send(Op.SET_COMPONENTS, 0, filter().matchComponents() ? 0 : 1, "");
    }

    private void select(@Nullable FilterEntry entry, int index) {
        commitStock();
        selected = entry;
        selectedIndex = entry == null ? -1 : index;
        moreOpen = false;
        if (getFocused() == stockBox || getFocused() == ruleBox) {
            setFocused(null);
        }
        stockBox.setValue(stockText());
    }

    private String stockText() {
        return selected == null || selected.stock() == 0 ? "" : Long.toString(selected.stock());
    }

    private long stockStep() {
        return isFluid() ? (hasShiftDown() ? 10_000 : 1_000) : (hasShiftDown() ? 64 : 1);
    }

    private Component stepTooltip(String key) {
        return isFluid()
                ? tr(key, tr("unit.buckets", 1), tr("unit.buckets", 10))
                : tr(key, 1, 64);
    }

    private void stepStock(int direction) {
        if (selected == null) {
            return;
        }
        long value = Math.max(0, parseStock() + direction * stockStep());
        stockBox.setValue(value == 0 ? "" : Long.toString(value));
        if (getFocused() == stockBox) {
            setFocused(null);
        }
        commitStock();
    }

    private long parseStock() {
        try {
            return stockDraft.isEmpty() ? 0 : Long.parseLong(stockDraft);
        } catch (NumberFormatException e) {
            return Long.MAX_VALUE;
        }
    }

    /** Manda o estoque digitado se ele mudou. */
    private void commitStock() {
        if (selected == null || selectedIndex < 0) {
            return;
        }
        long value = parseStock();
        if (value != selected.stock()) {
            send(Op.SET_STOCK, selectedIndex, value, "");
        }
    }

    private void removeSelected() {
        if (selected != null && selectedIndex >= 0) {
            int index = selectedIndex;
            selected = null;
            selectedIndex = -1;
            send(Op.REMOVE, index, 0, "");
        }
    }

    private void clear() {
        if (filter().isEmpty()) {
            return;
        }
        if (!clearArmed()) {
            clearArmedUntil = Util.getMillis() + CLEAR_CONFIRM_MS;
            return;
        }
        clearArmedUntil = 0;
        selected = null;
        selectedIndex = -1;
        send(Op.CLEAR, 0, 0, "");
    }

    private void toggleMore() {
        commitStock();
        moreOpen = !moreOpen;
        if (moreOpen) {
            setFocused(ruleBox);
        } else if (getFocused() == ruleBox) {
            setFocused(null);
        }
    }

    /** Uma regra do campo de texto: {@code #tag} ou tag sem prefixo, ou {@code @mod}; nula se inválida. */
    private static @Nullable EditFilterPayload rule(int containerId, String raw) {
        String text = raw.strip();
        if (text.startsWith("@")) {
            String mod = text.substring(1);
            return mod.matches("[a-z0-9_.-]{1,64}") ? new EditFilterPayload(containerId, Op.ADD_MOD, 0, 0, mod) : null;
        }
        String tag = text.startsWith("#") ? text.substring(1) : text;
        if (tag.isEmpty() || tag.length() > EditFilterPayload.MAX_TEXT || ResourceLocation.tryParse(tag) == null) {
            return null;
        }
        return new EditFilterPayload(containerId, Op.ADD_TAG, 0, 0, ResourceLocation.parse(tag).toString());
    }

    private @Nullable EditFilterPayload parseRule(String raw) {
        return rule(menu.containerId, raw);
    }

    private void addRule() {
        EditFilterPayload payload = parseRule(ruleDraft);
        if (payload == null) {
            return;
        }
        send(payload.op(), 0, 0, payload.text());
        ruleBox.setValue("");
    }

    // ------------------------------------------------------------------ entrada

    /** Índice da entrada sob o mouse; −2 numa célula vazia da grade; −1 fora dela. */
    private int entryAt(double mouseX, double mouseY) {
        double gx = mouseX - (leftPos + X0 + 1);
        double gy = mouseY - (topPos + GRID_Y + 1);
        if (gx < 0 || gy < 0 || gx >= COLS * CELL || gy >= ROWS * CELL) {
            return -1;
        }
        int index = (scrollRow + (int) gy / CELL) * COLS + (int) gx / CELL;
        return index < filter().entries().size() ? index : -2;
    }

    private boolean overGrid(double mouseX, double mouseY) {
        return mouseX >= leftPos + X0 && mouseX < leftPos + BAR_X + BAR_W
                && mouseY >= topPos + GRID_Y && mouseY < topPos + GRID_Y + GRID_H;
    }

    private boolean overBar(double mouseX, double mouseY) {
        return mouseX >= leftPos + BAR_X - 1 && mouseX < leftPos + BAR_X + BAR_W + 1
                && mouseY >= topPos + GRID_Y && mouseY < topPos + GRID_Y + GRID_H;
    }

    private void dragBar(double mouseY) {
        int max = maxScroll();
        int thumb = thumbHeight();
        double t = (mouseY - (topPos + GRID_Y + 1) - thumb / 2.0) / (GRID_H - 2 - thumb);
        scrollRow = (int) Math.round(Math.max(0, Math.min(1, t)) * max);
    }

    private int thumbHeight() {
        int rows = Math.max(ROWS, (filter().entries().size() + COLS - 1) / COLS);
        return Math.max(8, (GRID_H - 2) * ROWS / rows);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (getFocused() == stockBox && !stockBox.isMouseOver(mouseX, mouseY)) {
            commitStock();
            setFocused(null);
        }
        if (button == 0 && overBar(mouseX, mouseY) && maxScroll() > 0) {
            draggingBar = true;
            dragBar(mouseY);
            return true;
        }
        int index = entryAt(mouseX, mouseY);
        if (index != -1) {
            gridPress = true;
            if (index >= 0) {
                FilterEntry entry = filter().entries().get(index);
                boolean same = selected != null && index == selectedIndex;
                select(same ? null : entry, index);
            } else {
                select(null, -1);
            }
            return true;
        }
        if (clearArmed() && !clearButton.isMouseOver(mouseX, mouseY)) {
            clearArmedUntil = 0;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (draggingBar) {
            dragBar(mouseY);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (draggingBar || gridPress) {
            draggingBar = false;
            gridPress = false;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (overGrid(mouseX, mouseY)) {
            scrollRow = Math.max(0, Math.min(maxScroll(), scrollRow - (int) Math.signum(scrollY)));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (getFocused() instanceof EditBox box && box.isFocused()) {
            boolean enter = keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER;
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                if (box == stockBox) {
                    stockBox.setValue(stockText());
                }
                setFocused(null);
            } else if (enter && box == stockBox) {
                commitStock();
                setFocused(null);
            } else if (enter && box == ruleBox) {
                addRule();
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
        commitStock();
        super.removed();
    }

    // ------------------------------------------------------------------ desenho

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        if (menu.version() != lastVersion) {
            syncView();
        }
        refresh();
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
        int index = entryAt(mouseX, mouseY);
        if (index >= 0) {
            setTooltipForNextRenderPass(entryTooltip(filter().entries().get(index)));
            return;
        }
        if (selected != null && !moreOpen && mouseX >= leftPos + IX && mouseX < leftPos + IX + IW
                && mouseY >= topPos + STOCK_Y - 11 && mouseY < topPos + STOCK_Y - 2) {
            setTooltipForNextRenderPass(tr("stock.tooltip"));
            return;
        }
        if (JEI && mouseX >= leftPos + FilterMenu.INVENTORY_X - 1 && mouseX < leftPos + X1
                && mouseY >= topPos + HINT_Y - 1 && mouseY < topPos + HINT_Y + 9) {
            setTooltipForNextRenderPass(font.split(isFluid() ? tr("jei.tooltip.fluid") : tr("jei.tooltip.item"), 200));
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
        GuiPaint.panel(g, x, y, W, H, trim);

        // cabeçalho
        GuiPaint.text(g, font, tr("title"), x + X0, y + HEAD_Y + 3, GuiPaint.FG);
        int titleRight = x + X0 + font.width(tr("title")) + 8;
        int contextRight = backButton.getX() - 6;
        Component context = context();
        int contextW = Math.min(font.width(context), contextRight - titleRight);
        GuiPaint.text(g, font, GuiPaint.ellipsize(font, context, contextW), contextRight - contextW, y + HEAD_Y + 3,
                GuiPaint.MUTED);
        g.fill(x + X0, y + SEP_Y, x + X1, y + SEP_Y + 1, GuiPaint.LINE);

        renderGrid(g, mouseX, mouseY);
        if (moreOpen) {
            renderMore(g);
        } else {
            renderSelection(g);
        }

        // inventário do jogador
        GuiPaint.text(g, font, GuiPaint.ellipsize(font, inventoryHint(), X1 - FilterMenu.INVENTORY_X + 1),
                x + FilterMenu.INVENTORY_X - 1, y + HINT_Y, GuiPaint.MUTED);
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                GuiPaint.slot(g, x + FilterMenu.INVENTORY_X - 1 + col * CELL, y + FilterMenu.INVENTORY_Y - 1 + row * CELL);
            }
        }
        for (int col = 0; col < 9; col++) {
            GuiPaint.slot(g, x + FilterMenu.INVENTORY_X - 1 + col * CELL, y + FilterMenu.INVENTORY_Y + 57);
        }
    }

    private void renderGrid(GuiGraphics g, int mouseX, int mouseY) {
        int x = leftPos;
        int y = topPos;
        List<FilterEntry> entries = filter().entries();
        Component label = entries.size() >= Filter.MAX_ENTRIES ? tr("entries.full", entries.size())
                : tr("entries", entries.size());
        GuiPaint.text(g, font, label, x + X0, y + LABEL_Y, GuiPaint.MUTED);

        int gx = x + X0;
        int gy = y + GRID_Y;
        g.fill(gx, gy, gx + GRID_W, gy + GRID_H, GuiPaint.BEVEL_DARK);
        g.fill(gx + 1, gy + 1, gx + GRID_W - 1, gy + GRID_H - 1, GuiPaint.INSET);
        if (entries.isEmpty()) {
            List<FormattedCharSequence> lines = font.split(tr("empty"), GRID_W - 12);
            int ty = gy + (GRID_H - lines.size() * 10) / 2 + 1;
            for (FormattedCharSequence line : lines) {
                GuiPaint.text(g, font, line, gx + (GRID_W - font.width(line)) / 2, ty, GuiPaint.MUTED);
                ty += 10;
            }
        } else {
            int hovered = entryAt(mouseX, mouseY);
            for (int row = 0; row < ROWS; row++) {
                for (int col = 0; col < COLS; col++) {
                    int index = (scrollRow + row) * COLS + col;
                    int cx = gx + 1 + col * CELL;
                    int cy = gy + 1 + row * CELL;
                    GuiPaint.slot(g, cx, cy);
                    if (index >= entries.size()) {
                        continue;
                    }
                    renderEntry(g, entries.get(index), cx + 1, cy + 1);
                    if (index == selectedIndex) {
                        GuiPaint.outline(g, cx - 1, cy - 1, CELL + 2, CELL + 2, trim);
                        GuiPaint.outline(g, cx, cy, CELL, CELL, trim);
                    } else if (index == hovered) {
                        g.fill(cx + 1, cy + 1, cx + CELL - 1, cy + CELL - 1, 0x40FFFFFF);
                    }
                }
            }
        }

        // barra de rolagem
        int bx = x + BAR_X;
        g.fill(bx, gy, bx + BAR_W, gy + GRID_H, GuiPaint.INSET);
        int max = maxScroll();
        if (max > 0) {
            int thumb = thumbHeight();
            int ty = gy + 1 + Math.round((GRID_H - 2 - thumb) * (scrollRow / (float) max));
            boolean active = draggingBar || overBar(mouseX, mouseY);
            g.fill(bx, ty, bx + BAR_W, ty + thumb, active ? GuiPaint.BUTTON_HOVER_BORDER : GuiPaint.BEVEL_LIGHT);
        }
    }

    private void renderSelection(GuiGraphics g) {
        int x = leftPos;
        int y = topPos;
        GuiPaint.box(g, x + RX, y + BOX_Y, RW, BOX_H, SEL_BOX, GuiPaint.LINE);
        if (selected == null) {
            Component hint = !filter().isEmpty() ? tr("select.hint") : JEI ? tr("select.empty.jei") : tr("select.empty");
            List<FormattedCharSequence> lines = font.split(hint, IW);
            for (int i = 0; i < lines.size() && i < 6; i++) {
                GuiPaint.text(g, font, lines.get(i), x + IX, y + BOX_Y + 5 + i * 10, GuiPaint.MUTED);
            }
            return;
        }
        GuiPaint.text(g, font, GuiPaint.ellipsize(font, entryName(selected), IW), x + IX, y + BOX_Y + 5, GuiPaint.FG);
        GuiPaint.text(g, font, GuiPaint.ellipsize(font, Component.literal(entryId(selected)), IW), x + IX,
                y + BOX_Y + 15, GuiPaint.MUTED);
        GuiPaint.text(g, font, tr("stock"), x + IX, y + STOCK_Y - 10, GuiPaint.MUTED);
        int fx = x + IX + 16;
        int fw = IW - 32;
        GuiPaint.box(g, fx, y + STOCK_Y, fw, ROW_H, GuiPaint.INSET, stockBox.isFocused() ? trim : GuiPaint.LINE);
        if (isFluid()) {
            GuiPaint.textRight(g, font, tr("unit.mb"), fx + fw - 4, y + STOCK_Y + 3, GuiPaint.MUTED);
        }
    }

    private void renderMore(GuiGraphics g) {
        int x = leftPos;
        int y = topPos;
        GuiPaint.box(g, x + RX, y + BOX_Y, RW, BOX_H, SEL_BOX, GuiPaint.LINE);
        GuiPaint.text(g, font, tr("rule"), x + IX, y + RULE_Y - 10, GuiPaint.MUTED);
        boolean invalid = !ruleDraft.isBlank() && parseRule(ruleDraft) == null;
        int border = invalid ? DANGER : ruleBox.isFocused() ? trim : GuiPaint.LINE;
        GuiPaint.box(g, x + IX, y + RULE_Y, IW, ROW_H, GuiPaint.INSET, border);
        if (!view().isCard()) {
            GuiPaint.text(g, font, tr("card"), x + IX, y + CARD_Y - 10, GuiPaint.MUTED);
        }
    }

    // ------------------------------------------------------------------ entradas

    private void renderEntry(GuiGraphics g, FilterEntry entry, int x, int y) {
        switch (entry) {
            case ItemEntry e -> g.renderItem(e.stack(), x, y);
            case FluidEntry e -> renderFluid(g, e.stack(), x, y);
            case TagEntry e -> renderRule(g, e, "#", x, y);
            case ModEntry e -> renderRule(g, e, "@", x, y);
        }
        if (entry.stock() > 0) {
            String text = stockShort(entry.stock());
            // como a quantidade de uma pilha, no canto; o que não cabe na célula encolhe
            float scale = Math.min(1f, 17f / font.width(text));
            g.pose().pushPose();
            g.pose().translate(x + 17, y + 17, 200);
            g.pose().scale(scale, scale, 1f);
            g.drawString(font, text, -font.width(text), -8, 0xFFFFFFFF, true);
            g.pose().popPose();
        }
    }

    /** Tag ou mod: um membro (trocando a cada segundo) com a marca {@code #}/{@code @}, ou só a marca. */
    private void renderRule(GuiGraphics g, FilterEntry entry, String mark, int x, int y) {
        int count;
        int pick;
        if (isFluid()) {
            List<FluidStack> members = fluidMembers(entry);
            count = members.size();
            pick = count == 0 ? -1 : (int) (Util.getMillis() / 1000 % count);
            if (pick >= 0) {
                renderFluid(g, members.get(pick), x, y);
            }
        } else {
            List<ItemStack> members = itemMembers(entry);
            count = members.size();
            pick = count == 0 ? -1 : (int) (Util.getMillis() / 1000 % count);
            if (pick >= 0) {
                g.renderItem(members.get(pick), x, y);
            }
        }
        g.pose().pushPose();
        g.pose().translate(0, 0, 200);
        if (count == 0) {
            g.fill(x, y, x + 16, y + 16, GuiPaint.mix(GuiPaint.INSET, trim, 0.18f));
            GuiPaint.textCentered(g, font, Component.literal(mark), x + 8, y + 4, trim);
        } else {
            g.fill(x - 1, y - 1, x + 6, y + 8, GuiPaint.BEVEL_DARK);
            g.drawString(font, mark, x, y, trim, false);
        }
        g.pose().popPose();
    }

    private void renderFluid(GuiGraphics g, FluidStack stack, int x, int y) {
        if (stack.isEmpty()) {
            return;
        }
        IClientFluidTypeExtensions ext = IClientFluidTypeExtensions.of(stack.getFluid());
        TextureAtlasSprite sprite = minecraft.getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(ext.getStillTexture(stack));
        int tint = ext.getTintColor(stack);
        RenderSystem.enableBlend();
        g.blit(x, y, 0, 16, 16, sprite, ((tint >> 16) & 0xFF) / 255f, ((tint >> 8) & 0xFF) / 255f, (tint & 0xFF) / 255f,
                ((tint >>> 24) & 0xFF) / 255f);
        RenderSystem.disableBlend();
    }

    private List<ItemStack> itemMembers(FilterEntry entry) {
        return switch (entry) {
            case TagEntry e -> itemIcons.computeIfAbsent("#" + e.tag(), k -> {
                List<ItemStack> list = new ArrayList<>();
                BuiltInRegistries.ITEM.getTag(TagKey.create(Registries.ITEM, e.tag()))
                        .ifPresent(set -> set.stream().limit(64).forEach(h -> list.add(new ItemStack(h))));
                return list;
            });
            case ModEntry e -> itemIcons.computeIfAbsent("@" + e.modId(), k -> {
                List<ItemStack> list = new ArrayList<>();
                for (Item item : BuiltInRegistries.ITEM) {
                    if (list.size() < 64 && BuiltInRegistries.ITEM.getKey(item).getNamespace().equals(e.modId())) {
                        list.add(new ItemStack(item));
                    }
                }
                return list;
            });
            default -> List.of();
        };
    }

    private List<FluidStack> fluidMembers(FilterEntry entry) {
        return switch (entry) {
            case TagEntry e -> fluidIcons.computeIfAbsent("#" + e.tag(), k -> {
                List<FluidStack> list = new ArrayList<>();
                BuiltInRegistries.FLUID.getTag(TagKey.create(Registries.FLUID, e.tag())).map(HolderSet::stream)
                        .ifPresent(stream -> stream.map(Holder::value).filter(FilterScreen::isSource).limit(64)
                                .forEach(f -> list.add(new FluidStack(f, 1000))));
                return list;
            });
            case ModEntry e -> fluidIcons.computeIfAbsent("@" + e.modId(), k -> {
                List<FluidStack> list = new ArrayList<>();
                for (Fluid fluid : BuiltInRegistries.FLUID) {
                    if (list.size() < 64 && isSource(fluid)
                            && BuiltInRegistries.FLUID.getKey(fluid).getNamespace().equals(e.modId())) {
                        list.add(new FluidStack(fluid, 1000));
                    }
                }
                return list;
            });
            default -> List.of();
        };
    }

    private static boolean isSource(Fluid fluid) {
        return fluid != Fluids.EMPTY && fluid.isSource(fluid.defaultFluidState());
    }

    private Component entryName(FilterEntry entry) {
        return switch (entry) {
            case ItemEntry e -> e.stack().getHoverName();
            case FluidEntry e -> e.stack().getHoverName();
            case TagEntry e -> Component.translatableWithFallback(Tags.getTagTranslationKey(isFluid()
                    ? TagKey.create(Registries.FLUID, e.tag())
                    : TagKey.create(Registries.ITEM, e.tag())), "#" + e.tag());
            case ModEntry e -> Component.literal(ModList.get().getModContainerById(e.modId())
                    .map(c -> c.getModInfo().getDisplayName()).orElse("@" + e.modId()));
        };
    }

    private static String entryId(FilterEntry entry) {
        return switch (entry) {
            case ItemEntry e -> BuiltInRegistries.ITEM.getKey(e.stack().getItem()).toString();
            case FluidEntry e -> BuiltInRegistries.FLUID.getKey(e.stack().getFluid()).toString();
            case TagEntry e -> "#" + e.tag();
            case ModEntry e -> "@" + e.modId();
        };
    }

    private List<FormattedCharSequence> entryTooltip(FilterEntry entry) {
        List<FormattedCharSequence> lines = new ArrayList<>();
        lines.add(entryName(entry).getVisualOrderText());
        lines.add(Component.literal(entryId(entry)).withStyle(ChatFormatting.DARK_GRAY).getVisualOrderText());
        if (entry instanceof TagEntry) {
            lines.add(tr("entry.tag").copy().withStyle(ChatFormatting.GRAY).getVisualOrderText());
        } else if (entry instanceof ModEntry) {
            lines.add(tr("entry.mod").copy().withStyle(ChatFormatting.GRAY).getVisualOrderText());
        }
        if (entry.stock() > 0) {
            Component amount = isFluid() ? Component.literal(entry.stock() + " ").append(tr("unit.mb"))
                    : Component.literal(Long.toString(entry.stock()));
            lines.add(tr("stock.line", amount).copy().withStyle(ChatFormatting.GRAY).getVisualOrderText());
        }
        return lines;
    }

    /** Estoque no canto da célula: itens como estão; fluidos em baldes a partir de 1.000 mB. */
    private String stockShort(long stock) {
        if (!isFluid()) {
            return RateFormat.abbreviate(stock);
        }
        return stock < 1000 ? stock + "m" : RateFormat.abbreviate(stock / 1000) + "B";
    }

    /** A linha acima do inventário: como adicionar; com o JEI, cita ele também. */
    private Component inventoryHint() {
        if (JEI) {
            return isFluid() ? tr("hint.fluid.jei") : tr("hint.item.jei");
        }
        return isFluid() ? tr("hint.fluid") : tr("hint.item");
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
            default -> Optional.empty();
        };
    }

    /** Onde soltar um ingrediente arrastado: a grade de entradas, em coordenadas da tela. */
    public Rect2i ghostArea() {
        return new Rect2i(leftPos + X0, topPos + GRID_Y, GRID_W, GRID_H);
    }

    /**
     * Acrescenta uma entrada vinda de fora, sem o jogador ter o item; o servidor valida e manda a
     * visão nova, e a entrada fica selecionada quando chegar. Duplicada, só é selecionada.
     */
    public void addGhost(FilterEntry entry) {
        pendingReveal = entry;
        if (filter().indexOf(entry) >= 0) {
            revealPending();
        } else if (preview) {
            FilterView v = view();
            menu.applyView(new FilterView(v.type(), v.router(), v.face(), filter().withEntry(entry), v.hasCard()));
        } else {
            PacketDistributor.sendToServer(new AddFilterEntryPayload(menu.containerId, entry));
        }
    }

    /**
     * O que o painel ocupa além do retângulo da imagem, para o JEI não se sobrepor. Hoje o painel
     * é a própria imagem; a lista fica aqui para quem acrescentar abas ou gavetas por fora.
     */
    public List<Rect2i> extraAreas() {
        return List.of(new Rect2i(leftPos, topPos, W, H));
    }

    // ------------------------------------------------------------------ pintura dos botões

    private void paintTextButton(GuiGraphics g, FlatButton b, boolean hovered, Component text, boolean pressed) {
        int border = pressed ? trim : hovered ? GuiPaint.BUTTON_HOVER_BORDER : GuiPaint.BUTTON_BORDER;
        GuiPaint.box(g, b.getX(), b.getY(), b.getWidth(), b.getHeight(), b.active ? GuiPaint.BUTTON : GuiPaint.INSET,
                b.active ? border : GuiPaint.LINE);
        GuiPaint.textCentered(g, font, text, b.getX() + b.getWidth() / 2 + 1, b.getY() + (b.getHeight() - 8) / 2,
                b.active ? GuiPaint.FG : GuiPaint.DISABLED);
    }

    /** Botão principal, na cor do tier. */
    private void paintPrimary(GuiGraphics g, FlatButton b, boolean hovered, Component text) {
        if (!b.active) {
            paintTextButton(g, b, false, text, false);
            return;
        }
        int fill = hovered ? GuiPaint.mix(trim, 0xFFFFFFFF, 0.2f) : trim;
        GuiPaint.box(g, b.getX(), b.getY(), b.getWidth(), b.getHeight(), fill, fill);
        GuiPaint.textCentered(g, font, text, b.getX() + b.getWidth() / 2 + 1, b.getY() + (b.getHeight() - 8) / 2,
                GuiPaint.DARK_TEXT);
    }

    /** Remover e Limpar: neutros até o mouse chegar; armado (esperando confirmação), vermelhos. */
    private void paintDangerButton(GuiGraphics g, FlatButton b, boolean hovered, Component text, boolean armed) {
        if (!b.active) {
            paintTextButton(g, b, false, text, false);
            return;
        }
        int fill = armed ? GuiPaint.mix(GuiPaint.BUTTON, DANGER, 0.3f) : GuiPaint.BUTTON;
        int border = armed || hovered ? DANGER : GuiPaint.BUTTON_BORDER;
        GuiPaint.box(g, b.getX(), b.getY(), b.getWidth(), b.getHeight(), fill, border);
        GuiPaint.textCentered(g, font, text, b.getX() + b.getWidth() / 2 + 1, b.getY() + (b.getHeight() - 8) / 2,
                armed || hovered ? GuiPaint.mix(DANGER, 0xFFFFFFFF, 0.45f) : GuiPaint.FG);
    }

    /** Metade de um controle segmentado: a escolhida fica cheia na cor do tier, como as abas do roteador. */
    private void paintSegment(GuiGraphics g, FlatButton b, boolean hovered, Component text, boolean selected) {
        if (selected) {
            GuiPaint.box(g, b.getX(), b.getY(), b.getWidth(), b.getHeight(), trim, trim);
        } else {
            GuiPaint.box(g, b.getX(), b.getY(), b.getWidth(), b.getHeight(), GuiPaint.BUTTON,
                    hovered ? GuiPaint.BUTTON_HOVER_BORDER : GuiPaint.BUTTON_BORDER);
        }
        GuiPaint.textCentered(g, font, text, b.getX() + b.getWidth() / 2, b.getY() + 4,
                selected ? GuiPaint.DARK_TEXT : GuiPaint.FG);
    }

    private void paintComponents(GuiGraphics g, FlatButton b, boolean hovered) {
        boolean on = filter().matchComponents();
        GuiPaint.box(g, b.getX(), b.getY(), b.getWidth(), b.getHeight(), GuiPaint.BUTTON,
                hovered ? GuiPaint.BUTTON_HOVER_BORDER : GuiPaint.BUTTON_BORDER);
        GuiPaint.checkbox(g, b.getX() + 3, b.getY() + 3, on, trim);
        GuiPaint.text(g, font, tr("components"), b.getX() + 16, b.getY() + 4, on ? GuiPaint.FG : GuiPaint.MUTED);
    }

    private void paintMore(GuiGraphics g, FlatButton b, boolean hovered) {
        int color = hovered || moreOpen ? GuiPaint.FG : GuiPaint.MUTED;
        if (moreOpen) {
            GuiPaint.arrowDown(g, b.getX() + 1, b.getY() + 3, color);
        } else {
            GuiPaint.arrowRight(g, b.getX() + 2, b.getY() + 2, color);
        }
        GuiPaint.text(g, font, moreOpen ? tr("less") : tr("more"), b.getX() + 10, b.getY() + 1, color);
    }

    // ------------------------------------------------------------------ captura de desenvolvimento

    void previewTrim(int color) {
        trimOverride = color;
        trim = color;
    }

    void previewSelect(int index) {
        List<FilterEntry> entries = filter().entries();
        select(index >= 0 && index < entries.size() ? entries.get(index) : null, index);
    }

    void previewMore(boolean open, String draft) {
        moreOpen = open;
        ruleBox.setValue(draft);
        if (open) {
            setFocused(ruleBox);
        } else {
            setFocused(null);
        }
    }

    void previewArmClear() {
        clearArmedUntil = Util.getMillis() + 60_000;
    }

    /** Centro da célula de uma entrada visível, para simular o mouse em cima na captura. */
    int[] previewEntryCenter(int index) {
        int row = index / COLS - scrollRow;
        int col = index % COLS;
        return new int[] {leftPos + X0 + 1 + col * CELL + CELL / 2, topPos + GRID_Y + 1 + row * CELL + CELL / 2};
    }
}
