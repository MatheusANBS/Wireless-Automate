package io.github.matheusanbs.wirelessautomate.client;

import io.github.matheusanbs.wirelessautomate.block.RouterBlock;
import io.github.matheusanbs.wirelessautomate.filter.Filter;
import io.github.matheusanbs.wirelessautomate.filter.FilterEntry;
import io.github.matheusanbs.wirelessautomate.filter.FilterEntry.ChemicalEntry;
import io.github.matheusanbs.wirelessautomate.filter.FilterEntry.FluidEntry;
import io.github.matheusanbs.wirelessautomate.filter.FilterEntry.ItemEntry;
import io.github.matheusanbs.wirelessautomate.filter.FilterEntry.ModEntry;
import io.github.matheusanbs.wirelessautomate.filter.FilterEntry.RuleEntry;
import io.github.matheusanbs.wirelessautomate.filter.FilterEntry.TagEntry;
import io.github.matheusanbs.wirelessautomate.filter.ItemRule;
import io.github.matheusanbs.wirelessautomate.filter.ItemRule.Property;
import io.github.matheusanbs.wirelessautomate.menu.FilterMenu;
import io.github.matheusanbs.wirelessautomate.menu.FilterView;
import io.github.matheusanbs.wirelessautomate.network.Chemicals;
import io.github.matheusanbs.wirelessautomate.network.ResourceType;
import io.github.matheusanbs.wirelessautomate.packet.AddFilterEntryPayload;
import io.github.matheusanbs.wirelessautomate.packet.EditFilterPayload;
import io.github.matheusanbs.wirelessautomate.packet.EditFilterPayload.Op;
import io.github.matheusanbs.wirelessautomate.packet.FilterEntriesPayload;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Stream;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidUtil;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

/**
 * Tela de filtro (especificação, "Telas da interface › Filtro"). À esquerda, as entradas em lista
 * (ícone, nome, tipo e estoque) com busca; embaixo, o inventário do jogador (Shift + clique
 * adiciona o item exato, pelo {@link FilterMenu#quickMoveStack}). À direita, quatro abas:
 * <ul>
 *   <li><b>Entrada</b>: a selecionada, com o que ela pega, o estoque, Remover, e Ver tags (item) ou
 *       Editar (regra);</li>
 *   <li><b>Tags</b>: o inspetor (um item colocado no slot mostra todas as tags dele para marcar) e a
 *       busca em todas as tags do jogo, com a prévia dos itens da tag sob o mouse; {@code @texto}
 *       busca mods. Nos químicos, que não têm tags, a aba vira "Adicionar" (id do químico ou mod);</li>
 *   <li><b>Regra</b> (só itens): regra por propriedade ({@link ItemRule}), com o inventário aceso no
 *       que ela pega antes de adicionar;</li>
 *   <li><b>Mais</b>: Cartão de Filtro, componentes e Limpar.</li>
 * </ul>
 * A janela é redimensionável pelas bordas direita e de baixo e pela alça do canto, em torno do
 * centro (como a do Baú), e o tamanho fica lembrado na sessão.
 *
 * <p>Tudo vem de {@link FilterMenu#view()}; quando {@link FilterMenu#version()} muda a tela se
 * acerta sem recriar widgets. Cada ação vira um pacote ({@link EditFilterPayload},
 * {@link FilterEntriesPayload}); o servidor valida e manda a visão nova.
 *
 * <p>"Aba JEI": a parte pública "Ingredientes fantasmas" desta tela serve o plugin em
 * {@code compat/jei}: arrastar um ingrediente para a lista acrescenta a entrada exata, e para o
 * slot do inspetor (aba Tags) mostra as tags dele. Esta classe não conhece nenhuma classe do JEI.
 */
public class FilterScreen extends AbstractContainerScreen<FilterMenu> {
    // tamanho: mínimo, padrão (lembrado na sessão) e máximo
    private static final int MIN_W = 344;
    private static final int MIN_H = 252;
    private static final int MAX_W = 560;
    private static final int MAX_H = 450;
    private static int savedW = 356;
    private static int savedH = 270;
    // moldura
    private static final int X0 = 8;
    private static final int PANEL_W = 160;
    private static final int GAP = 6;
    private static final int PAD = 5;
    private static final int IW = PANEL_W - 2 * PAD;
    private static final int HEAD_Y = 5;
    private static final int ROW2_Y = 21;
    private static final int SEG_H = 13;
    private static final int LABEL_Y = 39;
    private static final int SEARCH_Y = 48;
    private static final int FIELD_H = 12;
    private static final int LIST_Y = 63;
    private static final int ROW = 18;
    private static final int TABS_Y = 37;
    private static final int TAB_H = 13;
    private static final int BOX_Y = 50;
    private static final int INV_H = 76;
    private static final int GRIP = 7;
    private static final int EDGE = 3;
    private static final int BTN_H = 14;
    // aba Tags
    private static final int INSPECT_Y = BOX_Y + 6;
    private static final int TAG_SEARCH_Y = BOX_Y + 29;
    private static final int CAND_Y = BOX_Y + 45;
    private static final int CAND_ROW = 11;
    private static final int PREVIEW_H = 30;
    // aba Regra
    private static final int COND_Y = BOX_Y + 17;
    private static final int COND_ROW = 11;
    private static final int TRI_W = 20;

    private static final int ACCENT = 0xFF45D6CC;
    private static final int DANGER = 0xFFE5534B;
    private static final int RULE_COLOR = 0xFFA46CFF;
    private static final int RULE_TEXT = 0xFFD8C4FF;
    private static final int SEL_ROW = 0xFF1D2A33;
    private static final int HOVER_ROW = 0xFF18202A;
    private static final long CLEAR_CONFIRM_MS = 3000;
    /** JEI instalado: as dicas falam dele e o plugin em {@code compat/jei} oferece arrastar e clicar. */
    private static final boolean JEI = ModList.get().isLoaded("jei");

    public enum Tab {
        ENTRY,
        TAGS,
        RULE,
        MORE
    }

    /** Uma linha da aba Tags: tag, mod ou químico para marcar; {@code count} = itens que ela pega (−1 = não se sabe). */
    private record Candidate(FilterEntry entry, String label, int count) {
    }

    private final boolean preview;
    private final List<FlatButton> buttons = new ArrayList<>();
    private final Map<String, List<ItemStack>> itemIcons = new HashMap<>();
    private final Map<String, List<FluidStack>> fluidIcons = new HashMap<>();

    private int lastVersion = -1;
    private int trim = ACCENT;
    private @Nullable Integer trimOverride;
    private Tab tab = Tab.ENTRY;
    private final ResizeHandle resizeHandle = new ResizeHandle(GRIP, EDGE);

    // lista de entradas
    private @Nullable FilterEntry selected;
    private int selectedIndex = -1;
    /** Índices das entradas mostradas (a busca filtra), refeitos quando a visão ou a busca mudam. */
    private List<Integer> shown = List.of();
    private int shownVersion = -1;
    private String shownSearch = "";
    private int scroll;
    private boolean draggingBar;
    private boolean listPress;
    private long clearArmedUntil;
    private String stockDraft = "";
    /** Entrada vinda do JEI ou da tela: quando ela aparecer na visão, fica selecionada e à vista. */
    private @Nullable FilterEntry pendingReveal;

    // aba Tags
    private ItemStack inspected = ItemStack.EMPTY;
    private FluidStack inspectedFluid = FluidStack.EMPTY;
    private final Set<FilterEntry> checked = new LinkedHashSet<>();
    private List<Candidate> candidates = List.of();
    private String candidatesKey = "";
    private int candScroll;
    private @Nullable Candidate hoveredCandidate;
    private @Nullable List<Candidate> allTags;
    private @Nullable Map<String, Integer> modCounts;

    // aba Regra
    private ItemRule draft = ItemRule.EMPTY;
    private int editing = -1;
    private @Nullable ItemRule compiledFor;
    private Predicate<ItemStack> compiled = stack -> false;
    private @Nullable List<Holder<Enchantment>> enchantments;

    // seleção: o que acende no inventário
    private @Nullable FilterEntry highlightFor;
    private Predicate<ItemStack> highlight = stack -> false;

    private FlatButton backButton;
    private FlatButton whiteButton;
    private FlatButton blackButton;
    private FlatButton componentsButton;
    private final FlatButton[] tabButtons = new FlatButton[Tab.values().length];
    private FlatButton minusButton;
    private FlatButton plusButton;
    private FlatButton removeButton;
    private FlatButton seeTagsButton;
    private FlatButton editButton;
    private FlatButton addTagsButton;
    private final FlatButton[][] triButtons = new FlatButton[Property.values().length][3];
    private FlatButton enchantToggle;
    private FlatButton enchantPrev;
    private FlatButton enchantNext;
    private FlatButton enchantLevel;
    private final FlatButton[] durabilityButtons = new FlatButton[3];
    private FlatButton durabilityPercent;
    private FlatButton ruleResetButton;
    private FlatButton ruleAddButton;
    private FlatButton importButton;
    private FlatButton exportButton;
    private FlatButton clearButton;
    private EditBox searchBox;
    private EditBox stockBox;
    private EditBox tagSearchBox;
    private EditBox scopeBox;
    private String searchDraft = "";
    /** Campo que recebe o foco depois do clique (a tela o daria ao botão clicado). */
    private @Nullable EditBox focusAfterClick;
    private String tagSearchDraft = "";

    public FilterScreen(FilterMenu menu, Inventory inventory, Component title) {
        this(menu, inventory, title, false);
    }

    /**
     * @param preview sem servidor (captura de desenvolvimento): as mudanças valem só na visão local
     */
    public FilterScreen(FilterMenu menu, Inventory inventory, Component title, boolean preview) {
        super(menu, inventory, title);
        this.preview = preview;
        this.imageWidth = savedW;
        this.imageHeight = savedH;
    }

    // ------------------------------------------------------------------ estado

    private FilterView view() {
        return menu.view();
    }

    private Filter filter() {
        return view().filter();
    }

    private ResourceType type() {
        return view().type();
    }

    private boolean isItem() {
        return type() == ResourceType.ITEM;
    }

    private boolean isFluid() {
        return type() == ResourceType.FLUID;
    }

    private boolean isChemical() {
        return type() == ResourceType.CHEMICAL;
    }

    /** Fluidos e químicos contam em mB (estoque, passos, unidade); itens em unidades. */
    private boolean inMb() {
        return isFluid() || isChemical();
    }

    private static Component tr(String key, Object... args) {
        return Component.translatable("gui.wirelessautomate.filter." + key, args);
    }

    private static Component typeName(ResourceType type) {
        return ResourceStyle.name(type);
    }

    /** Nome do armazenamento do filtro de entrada, pelo bloco no mundo do cliente. */
    private Component storageName(FilterView v) {
        if (minecraft != null && minecraft.level != null && v.router().isPresent()) {
            return minecraft.level.getBlockState(v.router().get()).getBlock().getName();
        }
        return Component.translatable("block.wirelessautomate.storage_chest");
    }

    /** "Baixo · Itens", "Tanque Wireless · Fluidos" ou "Cartão de Filtro · Itens". */
    private Component context() {
        FilterView v = view();
        Component where = v.face().isPresent()
                ? Component.translatable("gui.wirelessautomate.router.face." + v.face().get().getName())
                : v.isCard() ? Component.translatable("item.wirelessautomate.filter_card")
                : storageName(v);
        return where.copy().append(" · ").append(typeName(v.type()));
    }

    private boolean clearArmed() {
        return clearArmedUntil > Util.getMillis();
    }

    private boolean tabAvailable(Tab t) {
        return t != Tab.RULE || isItem();
    }

    // medidas que dependem do tamanho

    private int panelX() {
        return imageWidth - X0 - PANEL_W;
    }

    private int ix() {
        return panelX() + PAD;
    }

    private int innerBottom() {
        return imageHeight - X0 - PAD;
    }

    private int listW() {
        return imageWidth - X0 - PANEL_W - GAP - X0;
    }

    private int invTop() {
        return imageHeight - X0 - INV_H;
    }

    private int listRows() {
        return Math.max(1, (invTop() - 12 - LIST_Y) / ROW);
    }

    private int maxScroll() {
        return Math.max(0, shown.size() - listRows());
    }

    private int candRows() {
        int bottom = innerBottom() - BTN_H - 3 - PREVIEW_H;
        return Math.max(1, (bottom - CAND_Y) / CAND_ROW);
    }

    private int maxCandScroll() {
        return Math.max(0, candidates.size() - candRows());
    }

    private int maxW() {
        return Math.max(MIN_W, Math.min(MAX_W, width - 8));
    }

    private int maxH() {
        int room = Math.max(MIN_H, Math.min(MAX_H, height - 8));
        return MIN_H + (room - MIN_H) / ROW * ROW;
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
        if (editing >= 0 && (editing >= f.entries().size() || !(f.entries().get(editing) instanceof RuleEntry))) {
            editing = -1;
        }
        trim = resolveTrim();
        rebuildShown(true);
        revealPending();
    }

    /** Refaz a lista mostrada se a visão ou a busca mudaram. */
    private void rebuildShown(boolean force) {
        String search = searchBox.getValue().strip().toLowerCase(Locale.ROOT);
        if (!force && shownVersion == menu.version() && search.equals(shownSearch)) {
            return;
        }
        shownVersion = menu.version();
        shownSearch = search;
        List<FilterEntry> entries = filter().entries();
        List<Integer> list = new ArrayList<>(entries.size());
        for (int i = 0; i < entries.size(); i++) {
            if (search.isEmpty() || matchesSearch(entries.get(i), search)) {
                list.add(i);
            }
        }
        shown = list;
        scroll = Math.max(0, Math.min(scroll, maxScroll()));
    }

    /** Busca na lista: cada palavra no nome ou no id da entrada ({@code #tag}, {@code @mod} também). */
    private boolean matchesSearch(FilterEntry entry, String search) {
        String text = (entryName(entry).getString() + " " + entryId(entry)).toLowerCase(Locale.ROOT);
        for (String word : search.split("\\s+")) {
            if (!text.contains(word)) {
                return false;
            }
        }
        return true;
    }

    /** Seleciona e rola até a entrada pendente, se ela já está no filtro. */
    private void revealPending() {
        if (pendingReveal == null) {
            return;
        }
        int index = filter().indexOf(pendingReveal);
        if (index < 0) {
            return;
        }
        pendingReveal = null;
        select(index);
        int row = shown.indexOf(index);
        if (row < 0 && !searchBox.getValue().isEmpty()) {
            searchBox.setValue("");
            rebuildShown(true);
            row = shown.indexOf(index);
        }
        if (row >= 0 && row < scroll) {
            scroll = row;
        } else if (row >= scroll + listRows()) {
            scroll = Math.min(maxScroll(), row - listRows() + 1);
        }
    }

    /** Cor do tier do roteador (ou do armazenamento), lida do bloco no mundo do cliente; sem ele, o destaque padrão. */
    private int resolveTrim() {
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
        return ACCENT;
    }

    // ------------------------------------------------------------------ montagem

    @Override
    protected void init() {
        imageWidth = Math.max(MIN_W, Math.min(savedW, maxW()));
        imageHeight = Math.max(MIN_H, Math.min(savedH, maxH()));
        super.init();
        buttons.clear();
        if (!tabAvailable(tab)) {
            tab = Tab.ENTRY;
        }

        backButton = add(new FlatButton(0, 0, 40, SEG_H, Component.empty(),
                (g, b, hovered) -> paintTextButton(g, b, hovered, backLabel(), false), this::back)
                .tooltip(() -> view().isCard() ? tr("close.tooltip") : tr("back.tooltip")));
        Component white = tr("list.white");
        Component black = tr("list.black");
        whiteButton = add(new FlatButton(0, 0, font.width(white) + 12, SEG_H, white,
                (g, b, hovered) -> paintSegment(g, b, hovered, white, filter().listMode() == Filter.ListMode.WHITELIST),
                () -> setListMode(Filter.ListMode.WHITELIST)).tooltip(() -> tr("list.white.tooltip")));
        blackButton = add(new FlatButton(0, 0, font.width(black) + 12, SEG_H, black,
                (g, b, hovered) -> paintSegment(g, b, hovered, black, filter().listMode() == Filter.ListMode.BLACKLIST),
                () -> setListMode(Filter.ListMode.BLACKLIST)).tooltip(() -> tr("list.black.tooltip")));
        Component components = tr("components");
        componentsButton = add(new FlatButton(0, 0, 9 + 4 + font.width(components) + 6, SEG_H, components,
                this::paintComponents, this::toggleComponents)
                .tooltip(() -> filter().matchComponents() ? tr("components.on.tooltip") : tr("components.off.tooltip")));
        for (Tab t : Tab.values()) {
            Component label = tabLabel(t);
            tabButtons[t.ordinal()] = add(new FlatButton(0, 0, 40, TAB_H, label,
                    (g, b, hovered) -> paintSegment(g, b, hovered, tabLabel(t), tab == t), () -> openTab(t))
                    .tooltip(() -> tr("tab." + t.name().toLowerCase(Locale.ROOT) + ".tooltip")));
        }

        // lista
        searchBox = box(tr("search"), 64, searchDraft, value -> {
            searchDraft = value;
            scroll = 0;
        });
        searchBox.setHint(tr("search.hint").copy().withColor(GuiPaint.DISABLED));

        // aba Entrada
        minusButton = add(new FlatButton(0, 0, 14, BTN_H, tr("stock.decrease"),
                (g, b, hovered) -> paintTextButton(g, b, hovered, Component.literal("-"), false),
                () -> stepStock(-1)).tooltip(() -> stepTooltip("stock.decrease.tooltip")));
        plusButton = add(new FlatButton(0, 0, 14, BTN_H, tr("stock.increase"),
                (g, b, hovered) -> paintTextButton(g, b, hovered, Component.literal("+"), false),
                () -> stepStock(1)).tooltip(() -> stepTooltip("stock.increase.tooltip")));
        stockBox = box(tr("stock"), 15, stockDraft, value -> stockDraft = value);
        stockBox.setFilter(s -> s.chars().allMatch(Character::isDigit));
        stockBox.setHint(tr("stock.none").copy().withColor(GuiPaint.DISABLED));
        removeButton = add(new FlatButton(0, 0, 40, BTN_H, tr("remove"),
                (g, b, hovered) -> paintDangerButton(g, b, hovered, tr("remove"), false), this::removeSelected)
                .tooltip(() -> tr("remove.tooltip")));
        seeTagsButton = add(new FlatButton(0, 0, 40, BTN_H, tr("see_tags"),
                (g, b, hovered) -> paintTextButton(g, b, hovered, tr("see_tags"), false), this::seeTags)
                .tooltip(() -> tr("see_tags.tooltip")));
        editButton = add(new FlatButton(0, 0, 40, BTN_H, tr("edit"),
                (g, b, hovered) -> paintTextButton(g, b, hovered, tr("edit"), false), this::editSelected)
                .tooltip(() -> tr("edit.tooltip")));

        // aba Tags
        tagSearchBox = box(tr("tags.search"), 128, tagSearchDraft, value -> {
            tagSearchDraft = value;
            candScroll = 0;
        });
        tagSearchBox.setHint(tr(isChemical() ? "tags.search.chemical" : "tags.search.hint").copy().withColor(GuiPaint.DISABLED));
        addTagsButton = add(new FlatButton(0, 0, IW, BTN_H, tr("tags.add.action"),
                (g, b, hovered) -> paintPrimary(g, b, hovered, addLabel()), this::addChecked)
                .tooltip(() -> uncheckedCount() == 0 ? tr("tags.add.empty") : null));

        // aba Regra
        Property[] properties = Property.values();
        for (Property property : properties) {
            for (int v = 0; v < 3; v++) {
                Boolean value = v == 0 ? null : v == 1;
                Component label = v == 0 ? Component.literal("-") : tr(v == 1 ? "rule.yes" : "rule.no");
                triButtons[property.ordinal()][v] = add(new FlatButton(0, 0, TRI_W, COND_ROW - 1, label,
                        (g, b, hovered) -> paintTri(g, b, hovered, label, sameFlag(draft.flag(property), value),
                                Boolean.FALSE.equals(value)),
                        () -> setDraft(draft.withFlag(property, value)))
                        .tooltip(() -> tr("rule.prop." + property.getSerializedName() + ".tooltip")));
            }
        }
        enchantToggle = add(new FlatButton(0, 0, 70, COND_ROW, tr("rule.enchantment"),
                (g, b, hovered) -> paintCheck(g, b, hovered, tr("rule.enchantment"), draft.enchantment().isPresent()),
                this::toggleEnchantment).tooltip(() -> tr("rule.enchantment.tooltip")));
        enchantPrev = add(new FlatButton(0, 0, 9, COND_ROW, Component.literal("<"),
                (g, b, hovered) -> paintTextButton(g, b, hovered, Component.literal("<"), false), () -> cycleEnchantment(-1)));
        enchantNext = add(new FlatButton(0, 0, 9, COND_ROW, Component.literal(">"),
                (g, b, hovered) -> paintTextButton(g, b, hovered, Component.literal(">"), false), () -> cycleEnchantment(1)));
        enchantLevel = add(new FlatButton(0, 0, 26, COND_ROW, tr("rule.level"),
                (g, b, hovered) -> paintTextButton(g, b, hovered, levelLabel(), false), () -> stepLevel(1))
                .tooltip(() -> tr("rule.level.tooltip")));
        for (int v = 0; v < 3; v++) {
            int mode = v;
            Component label = Component.literal(v == 0 ? "-" : v == 1 ? "≥" : "<");
            durabilityButtons[v] = add(new FlatButton(0, 0, 14, COND_ROW - 1, label,
                    (g, b, hovered) -> paintTri(g, b, hovered, label, durabilityMode() == mode, false),
                    () -> setDurabilityMode(mode)).tooltip(() -> tr("rule.durability.tooltip." + mode)));
        }
        durabilityPercent = add(new FlatButton(0, 0, 28, COND_ROW - 1, tr("rule.percent"),
                (g, b, hovered) -> paintTextButton(g, b, hovered, Component.literal(
                        draft.durability().map(d -> d.percent() + "%").orElse("")), false), () -> stepPercent(10))
                .tooltip(() -> tr("rule.percent.tooltip")));
        scopeBox = box(tr("rule.scope"), ItemRule.MAX_SCOPE, draft.scope(), value -> setDraft(draft.withScope(value)));
        scopeBox.setHint(tr("rule.scope.hint").copy().withColor(GuiPaint.DISABLED));
        ruleResetButton = add(new FlatButton(0, 0, 40, BTN_H, Component.empty(),
                (g, b, hovered) -> paintTextButton(g, b, hovered, editing >= 0 ? tr("rule.cancel") : tr("rule.reset"), false),
                this::resetRule).tooltip(() -> editing >= 0 ? tr("rule.cancel.tooltip") : tr("rule.reset.tooltip")));
        ruleAddButton = add(new FlatButton(0, 0, 40, BTN_H, tr("rule.add"),
                (g, b, hovered) -> paintPrimary(g, b, hovered, editing >= 0 ? tr("rule.save") : tr("rule.add")),
                this::submitRule).tooltip(() -> !draft.validScope() ? tr("rule.scope.invalid")
                        : draft.isEmpty() ? tr("rule.empty") : null));

        // aba Mais
        importButton = add(new FlatButton(0, 0, 40, BTN_H, tr("card.import"),
                (g, b, hovered) -> paintTextButton(g, b, hovered, tr("card.import"), false), () -> send(Op.IMPORT_CARD, 0, 0, ""))
                .tooltip(() -> view().hasCard() ? tr("card.import.tooltip") : tr("card.missing")));
        exportButton = add(new FlatButton(0, 0, 40, BTN_H, tr("card.export"),
                (g, b, hovered) -> paintTextButton(g, b, hovered, tr("card.export"), false), () -> send(Op.EXPORT_CARD, 0, 0, ""))
                .tooltip(() -> view().hasCard() ? tr("card.export.tooltip") : tr("card.missing")));
        clearButton = add(new FlatButton(0, 0, IW, BTN_H, tr("clear"),
                (g, b, hovered) -> paintDangerButton(g, b, hovered, clearArmed() ? tr("clear.confirm") : tr("clear"), clearArmed()),
                this::clear).tooltip(() -> clearArmed() ? tr("clear.confirm.tooltip", filter().entries().size()) : tr("clear.tooltip")));

        layout();
        syncView();
        refresh();
    }

    private FlatButton add(FlatButton button) {
        buttons.add(button);
        return addRenderableWidget(button);
    }

    /** Campo de texto sem borda (a moldura é desenhada pela tela). */
    private EditBox box(Component name, int max, String value, java.util.function.Consumer<String> responder) {
        EditBox box = new EditBox(font, 0, 0, 10, 9, name);
        box.setBordered(false);
        box.setMaxLength(max);
        box.setTextColor(GuiPaint.FG);
        box.setValue(value);
        box.setResponder(responder);
        return addRenderableWidget(box);
    }

    private Component tabLabel(Tab t) {
        return t == Tab.TAGS && isChemical() ? tr("tab.add") : tr("tab." + t.name().toLowerCase(Locale.ROOT));
    }

    private Component backLabel() {
        return view().isCard() ? tr("close") : tr("back");
    }

    /** Centraliza o painel e põe o inventário do jogador no lugar, para o tamanho atual. */
    private void layout() {
        leftPos = (width - imageWidth) / 2;
        topPos = (height - imageHeight) / 2;
        menu.placeInventory(X0 + 1, invTop() + 1);
        scroll = Math.max(0, Math.min(scroll, maxScroll()));
    }

    /** Posições, visibilidade e estado que dependem da visão, da aba ou do tamanho. */
    private void refresh() {
        int x = leftPos;
        int y = topPos;
        int w = imageWidth;
        Component back = backLabel();
        backButton.setWidth(font.width(back) + 12);
        backButton.setPosition(x + w - X0 - backButton.getWidth(), y + HEAD_Y);
        backButton.setMessage(back);
        whiteButton.setPosition(x + X0, y + ROW2_Y);
        blackButton.setPosition(x + X0 + whiteButton.getWidth() + 2, y + ROW2_Y);
        componentsButton.setPosition(blackButton.getX() + blackButton.getWidth() + 8, y + ROW2_Y);
        componentsButton.visible = isItem();

        // abas, só as que valem para o tipo
        List<Tab> tabs = new ArrayList<>();
        for (Tab t : Tab.values()) {
            tabButtons[t.ordinal()].visible = tabAvailable(t);
            if (tabAvailable(t)) {
                tabs.add(t);
            }
        }
        int tx = x + panelX();
        for (int i = 0; i < tabs.size(); i++) {
            int right = x + panelX() + PANEL_W * (i + 1) / tabs.size();
            FlatButton b = tabButtons[tabs.get(i).ordinal()];
            b.setPosition(tx, y + TABS_Y);
            b.setWidth(right - tx + (i + 1 < tabs.size() ? 1 : 0));
            tx = right;
        }

        searchBox.setPosition(x + X0 + 4, y + SEARCH_Y + 2);
        searchBox.setWidth(listW() - 8);

        int px = x + ix();
        int bottom = y + innerBottom();
        int half = (IW - 3) / 2;

        // Entrada
        boolean entry = tab == Tab.ENTRY && selected != null;
        minusButton.visible = plusButton.visible = stockBox.visible = removeButton.visible = entry;
        seeTagsButton.visible = entry && selected instanceof ItemEntry || entry && selected instanceof FluidEntry;
        editButton.visible = entry && selected instanceof RuleEntry;
        boolean canStock = filter().listMode() == Filter.ListMode.WHITELIST;
        minusButton.active = entry && selected.stock() > 0;
        plusButton.active = canStock;
        int stockY = bottom - BTN_H - 4 - BTN_H;
        minusButton.setPosition(px, stockY);
        plusButton.setPosition(px + IW - 14, stockY);
        stockBox.setPosition(px + 20, stockY + 3);
        stockBox.setWidth(IW - 40 - (inMb() ? font.width(tr("unit.mb")) + 2 : 0));
        boolean secondButton = seeTagsButton.visible || editButton.visible;
        removeButton.setPosition(px, bottom - BTN_H);
        removeButton.setWidth(secondButton ? half : IW);
        seeTagsButton.setPosition(px + IW - half, bottom - BTN_H);
        seeTagsButton.setWidth(half);
        editButton.setPosition(px + IW - half, bottom - BTN_H);
        editButton.setWidth(half);

        // Tags
        boolean tags = tab == Tab.TAGS;
        tagSearchBox.visible = addTagsButton.visible = tags;
        tagSearchBox.setPosition(px + 4, y + TAG_SEARCH_Y + 2);
        tagSearchBox.setWidth(IW - 8);
        addTagsButton.setPosition(px, bottom - BTN_H);
        addTagsButton.active = uncheckedCount() > 0;

        // Regra
        boolean rule = tab == Tab.RULE;
        Property[] properties = Property.values();
        for (Property property : properties) {
            for (int v = 0; v < 3; v++) {
                FlatButton b = triButtons[property.ordinal()][v];
                b.visible = rule;
                b.setPosition(px + IW - 3 * TRI_W + v * TRI_W, y + COND_Y + property.ordinal() * COND_ROW);
            }
        }
        int ry = y + COND_Y + properties.length * COND_ROW + 3;
        enchantToggle.visible = rule;
        enchantToggle.setPosition(px, ry);
        enchantToggle.setWidth(IW);
        boolean enchant = rule && draft.enchantment().isPresent();
        enchantPrev.visible = enchantNext.visible = enchantLevel.visible = enchant;
        if (enchant) {
            ry += COND_ROW + 1;
            enchantPrev.setPosition(px + 10, ry);
            enchantNext.setPosition(px + IW - 26 - 3 - 9, ry);
            enchantLevel.setPosition(px + IW - 26, ry);
        }
        ry += COND_ROW + 3;
        for (int v = 0; v < 3; v++) {
            durabilityButtons[v].visible = rule;
            durabilityButtons[v].setPosition(px + IW - 28 - 4 - 3 * 14 + v * 14, ry);
        }
        durabilityPercent.visible = rule && draft.durability().isPresent();
        durabilityPercent.setPosition(px + IW - 28, ry);
        ry += COND_ROW + 3;
        scopeBox.visible = rule;
        int scopeLabel = font.width(tr("rule.scope")) + 5;
        scopeBox.setPosition(px + scopeLabel + 4, ry + 2);
        scopeBox.setWidth(IW - scopeLabel - 8);
        ruleResetButton.visible = ruleAddButton.visible = rule;
        ruleResetButton.setPosition(px, bottom - BTN_H);
        ruleResetButton.setWidth(half);
        ruleAddButton.setPosition(px + IW - half, bottom - BTN_H);
        ruleAddButton.setWidth(half);
        ruleAddButton.active = draft.isValid();

        // Mais
        boolean more = tab == Tab.MORE;
        importButton.visible = exportButton.visible = more && !view().isCard();
        importButton.active = exportButton.active = view().hasCard();
        importButton.setPosition(px, y + BOX_Y + 40);
        importButton.setWidth(half);
        exportButton.setPosition(px + IW - half, y + BOX_Y + 40);
        exportButton.setWidth(half);
        clearButton.visible = more;
        clearButton.active = !filter().isEmpty();
        clearButton.setPosition(px, bottom - BTN_H);
    }

    private int scopeRowY() {
        int ry = COND_Y + Property.values().length * COND_ROW + 3;
        if (draft.enchantment().isPresent()) {
            ry += COND_ROW + 1;
        }
        return ry + 2 * (COND_ROW + 3);
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
        applyLocally(next);
    }

    private void applyLocally(Filter next) {
        FilterView v = view();
        menu.applyView(new FilterView(v.type(), v.router(), v.face(), next, v.hasCard()));
    }

    /** Manda tags, mods e regras ({@link FilterEntriesPayload}); químicos exatos vão pelo pacote do JEI. */
    private void sendEntries(int replace, List<FilterEntry> entries) {
        if (entries.isEmpty()) {
            return;
        }
        if (preview) {
            applyLocally(replace < 0 ? filter().withEntries(entries) : filter().withReplaced(replace, entries.getFirst()));
            return;
        }
        List<FilterEntry> rules = new ArrayList<>();
        for (FilterEntry entry : entries) {
            if (entry instanceof ChemicalEntry) {
                PacketDistributor.sendToServer(new AddFilterEntryPayload(menu.containerId, entry));
            } else {
                rules.add(entry);
            }
        }
        for (int i = 0; i < rules.size(); i += FilterEntriesPayload.MAX) {
            PacketDistributor.sendToServer(new FilterEntriesPayload(menu.containerId, replace,
                    List.copyOf(rules.subList(i, Math.min(rules.size(), i + FilterEntriesPayload.MAX)))));
        }
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

    private void openTab(Tab t) {
        if (!tabAvailable(t)) {
            return;
        }
        commitStock();
        unfocus();
        tab = t;
        clearArmedUntil = 0;
        if (t == Tab.TAGS) {
            setFocused(tagSearchBox);
            focusAfterClick = tagSearchBox;
        }
    }

    private void unfocus() {
        if (getFocused() instanceof EditBox box) {
            box.setFocused(false);
        }
        setFocused(null);
    }

    private void select(int index) {
        commitStock();
        List<FilterEntry> entries = filter().entries();
        selected = index >= 0 && index < entries.size() ? entries.get(index) : null;
        selectedIndex = selected == null ? -1 : index;
        if (getFocused() == stockBox) {
            unfocus();
        }
        stockBox.setValue(stockText());
        if (selected != null) {
            tab = Tab.ENTRY;
        }
    }

    private String stockText() {
        return selected == null || selected.stock() == 0 ? "" : Long.toString(selected.stock());
    }

    private long stockStep() {
        return inMb() ? (hasShiftDown() ? 10_000 : 1_000) : (hasShiftDown() ? 64 : 1);
    }

    private Component stepTooltip(String key) {
        return inMb() ? tr(key, tr("unit.buckets", 1), tr("unit.buckets", 10)) : tr(key, 1, 64);
    }

    private void stepStock(int direction) {
        if (selected == null) {
            return;
        }
        long value = Math.max(0, parseStock() + direction * stockStep());
        stockBox.setValue(value == 0 ? "" : Long.toString(value));
        if (getFocused() == stockBox) {
            unfocus();
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

    /** Item (ou fluido) selecionado vai para o inspetor, na aba Tags. */
    private void seeTags() {
        switch (selected) {
            case ItemEntry e -> inspect(e.stack());
            case FluidEntry e -> inspectFluid(e.stack());
            case null, default -> {
                return;
            }
        }
        openTab(Tab.TAGS);
    }

    /** Regra selecionada vai para o montador, para editar. */
    private void editSelected() {
        if (selected instanceof RuleEntry rule) {
            editing = selectedIndex;
            setDraft(rule.rule());
            scopeBox.setValue(rule.rule().scope());
            openTab(Tab.RULE);
        }
    }

    // inspetor e busca de tags

    /** Põe o item no inspetor (cópia de 1); num filtro de fluidos, inspeciona o fluido que ele contém. */
    public void inspect(ItemStack stack) {
        if (isFluid()) {
            FluidUtil.getFluidContained(stack).filter(f -> !f.isEmpty()).ifPresent(this::inspectFluid);
            return;
        }
        if (!isItem() || stack.isEmpty()) {
            return;
        }
        inspected = stack.copyWithCount(1);
        afterInspect();
    }

    private void inspectFluid(FluidStack stack) {
        if (!isFluid() || stack.isEmpty()) {
            return;
        }
        inspectedFluid = stack.copyWithAmount(1000);
        afterInspect();
    }

    private void afterInspect() {
        checked.clear();
        tagSearchBox.setValue("");
        candScroll = 0;
        candidatesKey = "";
    }

    private void clearInspect() {
        inspected = ItemStack.EMPTY;
        inspectedFluid = FluidStack.EMPTY;
        checked.clear();
        candidatesKey = "";
    }

    private boolean hasInspected() {
        return !inspected.isEmpty() || !inspectedFluid.isEmpty();
    }

    private boolean inFilter(FilterEntry entry) {
        return filter().indexOf(entry) >= 0;
    }

    private int uncheckedCount() {
        int n = 0;
        for (FilterEntry entry : checked) {
            if (!inFilter(entry)) {
                n++;
            }
        }
        return n;
    }

    private Component addLabel() {
        int n = uncheckedCount();
        return n == 0 ? tr("tags.add.none") : tr(n == 1 ? "tags.add.one" : "tags.add", n);
    }

    private void toggleCandidate(Candidate candidate) {
        if (inFilter(candidate.entry())) {
            pendingReveal = candidate.entry();
            revealPending();
            return;
        }
        if (!checked.remove(candidate.entry())) {
            checked.add(candidate.entry());
        }
    }

    private void addChecked() {
        List<FilterEntry> entries = new ArrayList<>();
        for (FilterEntry entry : checked) {
            if (!inFilter(entry)) {
                entries.add(entry);
            }
        }
        checked.clear();
        sendEntries(-1, entries);
    }

    /** Enter na busca: marca e adiciona o que foi digitado ({@code #tag}, {@code @mod} ou um químico). */
    private void addTyped() {
        FilterEntry typed = typedEntry(tagSearchDraft);
        if (typed == null) {
            if (candidates.size() == 1) {
                typed = candidates.getFirst().entry();
            } else {
                return;
            }
        }
        if (inFilter(typed)) {
            pendingReveal = typed;
            revealPending();
            return;
        }
        pendingReveal = typed;
        sendEntries(-1, List.of(typed));
        tagSearchBox.setValue("");
    }

    /** O que o texto da busca adiciona sozinho, ou {@code null}: {@code @mod}, {@code #tag} ou o id de um químico. */
    private @Nullable FilterEntry typedEntry(String raw) {
        String text = raw.strip().toLowerCase(Locale.ROOT);
        if (text.startsWith("@")) {
            String mod = text.substring(1);
            return mod.matches("[a-z0-9_.-]{1,64}") ? new ModEntry(mod, 0) : null;
        }
        if (isChemical()) {
            ResourceLocation id = ResourceLocation.tryParse(text);
            return id != null && text.contains(":") && Chemicals.exists(id) ? new ChemicalEntry(id, 0) : null;
        }
        String tag = text.startsWith("#") ? text.substring(1) : text;
        ResourceLocation id = tag.contains(":") ? ResourceLocation.tryParse(tag) : null;
        return id == null || id.getPath().isEmpty() ? null : new TagEntry(id, 0);
    }

    /** Refaz as linhas da aba Tags se a busca, o inspetor ou o tipo mudaram. */
    private void rebuildCandidates() {
        String search = tagSearchDraft.strip().toLowerCase(Locale.ROOT);
        String key = search + "|" + System.identityHashCode(inspected) + "|" + System.identityHashCode(inspectedFluid);
        if (key.equals(candidatesKey)) {
            return;
        }
        candidatesKey = key;
        List<Candidate> list = new ArrayList<>();
        if (search.startsWith("@")) {
            String q = search.substring(1);
            Map<String, Integer> counts = modCounts();
            ModList.get().getMods().stream()
                    .map(info -> info.getModId())
                    .filter(id -> id.contains(q) && (isChemical() || counts.getOrDefault(id, 0) > 0))
                    .sorted(Comparator.comparingInt((String id) -> -counts.getOrDefault(id, 0)).thenComparing(id -> id))
                    .forEach(id -> list.add(new Candidate(new ModEntry(id, 0), "@" + id, isChemical() ? -1 : counts.getOrDefault(id, 0))));
        } else if (!search.isEmpty()) {
            if (isChemical()) {
                FilterEntry typed = typedEntry(search);
                if (typed != null) {
                    list.add(new Candidate(typed, Chemicals.name(((ChemicalEntry) typed).chemical()).getString(), -1));
                }
            } else {
                String q = search.startsWith("#") ? search.substring(1) : search;
                for (Candidate tag : allTags()) {
                    if (tag.label().contains(q)) {
                        list.add(tag);
                    }
                }
                FilterEntry typed = typedEntry(search);
                if (typed instanceof TagEntry t && list.stream().noneMatch(c -> c.entry().sameTarget(t))) {
                    list.addFirst(new Candidate(typed, "#" + t.tag(), 0));
                }
            }
        } else if (!inspected.isEmpty()) {
            Registry<Item> registry = BuiltInRegistries.ITEM;
            inspected.getTags().map(tag -> tagCandidate(tag.location(), registry.getTag(tag).map(HolderSet::size).orElse(0)))
                    .sorted(Comparator.comparing(Candidate::label)).forEach(list::add);
            String mod = BuiltInRegistries.ITEM.getKey(inspected.getItem()).getNamespace();
            list.add(new Candidate(new ModEntry(mod, 0), "@" + mod, modCounts().getOrDefault(mod, 0)));
        } else if (!inspectedFluid.isEmpty()) {
            Registry<Fluid> registry = BuiltInRegistries.FLUID;
            fluidTags(inspectedFluid.getFluid())
                    .map(tag -> tagCandidate(tag.location(), registry.getTag(tag).map(HolderSet::size).orElse(0)))
                    .sorted(Comparator.comparing(Candidate::label)).forEach(list::add);
            String mod = BuiltInRegistries.FLUID.getKey(inspectedFluid.getFluid()).getNamespace();
            list.add(new Candidate(new ModEntry(mod, 0), "@" + mod, modCounts().getOrDefault(mod, 0)));
        }
        candidates = list;
        candScroll = Math.max(0, Math.min(candScroll, maxCandScroll()));
    }

    @SuppressWarnings("deprecation")
    private static Stream<TagKey<Fluid>> fluidTags(Fluid fluid) {
        return fluid.builtInRegistryHolder().tags();
    }

    private static Candidate tagCandidate(ResourceLocation tag, int count) {
        return new Candidate(new TagEntry(tag, 0), "#" + tag, count);
    }

    /** Todas as tags do tipo (itens ou fluidos), com quantos membros têm; montada uma vez por tela. */
    private List<Candidate> allTags() {
        if (allTags == null) {
            List<Candidate> list = new ArrayList<>();
            if (isFluid()) {
                BuiltInRegistries.FLUID.getTags().forEach(pair -> list.add(tagCandidate(pair.getFirst().location(), pair.getSecond().size())));
            } else {
                BuiltInRegistries.ITEM.getTags().forEach(pair -> list.add(tagCandidate(pair.getFirst().location(), pair.getSecond().size())));
            }
            list.sort(Comparator.comparing(Candidate::label));
            allTags = list;
        }
        return allTags;
    }

    /** Itens (ou fluidos) de cada mod, para a busca {@code @mod}; montado uma vez por tela. */
    private Map<String, Integer> modCounts() {
        if (modCounts == null) {
            Map<String, Integer> counts = new HashMap<>();
            if (isFluid()) {
                for (Fluid fluid : BuiltInRegistries.FLUID) {
                    if (isSource(fluid)) {
                        counts.merge(BuiltInRegistries.FLUID.getKey(fluid).getNamespace(), 1, Integer::sum);
                    }
                }
            } else {
                for (ResourceLocation id : BuiltInRegistries.ITEM.keySet()) {
                    counts.merge(id.getNamespace(), 1, Integer::sum);
                }
            }
            modCounts = counts;
        }
        return modCounts;
    }

    // montador de regra

    private static boolean sameFlag(@Nullable Boolean a, @Nullable Boolean b) {
        return a == null ? b == null : a.equals(b);
    }

    private void setDraft(ItemRule rule) {
        draft = rule;
    }

    private Predicate<ItemStack> draftPredicate() {
        if (!draft.equals(compiledFor)) {
            compiledFor = draft;
            compiled = draft.isValid() ? draft.compile() : stack -> false;
        }
        return compiled;
    }

    private List<Holder<Enchantment>> enchantments() {
        if (enchantments == null) {
            List<Holder<Enchantment>> list = new ArrayList<>();
            if (minecraft != null && minecraft.level != null) {
                minecraft.level.registryAccess().registry(Registries.ENCHANTMENT)
                        .ifPresent(registry -> registry.holders().forEach(list::add));
            }
            list.sort(Comparator.comparing(h -> h.value().description().getString()));
            enchantments = list;
        }
        return enchantments;
    }

    private @Nullable Holder<Enchantment> draftEnchantment() {
        ResourceLocation id = draft.enchantment().map(ItemRule.Enchant::id).orElse(null);
        if (id == null) {
            return null;
        }
        for (Holder<Enchantment> holder : enchantments()) {
            if (holder.unwrapKey().map(k -> k.location().equals(id)).orElse(false)) {
                return holder;
            }
        }
        return null;
    }

    private void toggleEnchantment() {
        if (draft.enchantment().isPresent()) {
            setDraft(draft.withEnchantment(Optional.empty()));
            return;
        }
        List<Holder<Enchantment>> list = enchantments();
        if (list.isEmpty()) {
            return;
        }
        Holder<Enchantment> first = list.stream()
                .filter(h -> h.unwrapKey().map(k -> k.location().getPath().equals("fortune")).orElse(false))
                .findFirst().orElse(list.getFirst());
        setDraft(draft.withEnchantment(Optional.of(new ItemRule.Enchant(first.unwrapKey().orElseThrow().location(), 1))));
    }

    private void cycleEnchantment(int direction) {
        List<Holder<Enchantment>> list = enchantments();
        if (list.isEmpty() || draft.enchantment().isEmpty()) {
            return;
        }
        int index = list.indexOf(draftEnchantment());
        int next = Math.floorMod(index + direction, list.size());
        Holder<Enchantment> holder = list.get(next);
        int level = Math.min(draft.enchantment().get().minLevel(), holder.value().getMaxLevel());
        setDraft(draft.withEnchantment(Optional.of(new ItemRule.Enchant(holder.unwrapKey().orElseThrow().location(), level))));
    }

    private void stepLevel(int direction) {
        draft.enchantment().ifPresent(e -> {
            Holder<Enchantment> holder = draftEnchantment();
            int max = holder == null ? ItemRule.Enchant.MAX_LEVEL : Math.max(1, holder.value().getMaxLevel());
            int level = Math.floorMod(e.minLevel() - 1 + direction, max) + 1;
            setDraft(draft.withEnchantment(Optional.of(new ItemRule.Enchant(e.id(), level))));
        });
    }

    private Component levelLabel() {
        int level = draft.enchantment().map(ItemRule.Enchant::minLevel).orElse(1);
        return Component.literal("≥ ").append(ItemRule.levelName(level));
    }

    /** 0 = tanto faz, 1 = pelo menos, 2 = abaixo de. */
    private int durabilityMode() {
        return draft.durability().map(d -> d.atLeast() ? 1 : 2).orElse(0);
    }

    private void setDurabilityMode(int mode) {
        int percent = draft.durability().map(ItemRule.Durability::percent).orElse(mode == 2 ? 25 : 50);
        setDraft(draft.withDurability(mode == 0 ? Optional.empty() : Optional.of(new ItemRule.Durability(mode == 1, percent))));
    }

    private void stepPercent(int delta) {
        draft.durability().ifPresent(d -> {
            int step = hasShiftDown() ? Integer.signum(delta) : delta;
            int percent = d.percent() + step;
            percent = percent > 100 ? 1 : percent < 1 ? 100 : percent;
            setDraft(draft.withDurability(Optional.of(new ItemRule.Durability(d.atLeast(), percent))));
        });
    }

    private void resetRule() {
        editing = -1;
        setDraft(ItemRule.EMPTY);
        scopeBox.setValue("");
    }

    private void submitRule() {
        if (!draft.isValid()) {
            return;
        }
        RuleEntry entry = new RuleEntry(draft, 0);
        pendingReveal = entry;
        sendEntries(editing, List.of(entry));
        editing = -1;
        setDraft(ItemRule.EMPTY);
        scopeBox.setValue("");
        tab = Tab.ENTRY;
        unfocus();
    }

    private Component ruleText(ItemRule rule) {
        return rule.describe();
    }

    /** O que acende no inventário: a regra em montagem (aba Regra) ou a entrada selecionada (itens). */
    private Predicate<ItemStack> currentHighlight() {
        if (tab == Tab.RULE) {
            return draftPredicate();
        }
        if (tab == Tab.ENTRY && selected != null && isItem()) {
            if (!selected.equals(highlightFor)) {
                highlightFor = selected;
                Filter single = new Filter(Filter.ListMode.WHITELIST, filter().matchComponents(), List.of(selected.withStock(0)));
                highlight = single::testItem;
            }
            return highlight;
        }
        return stack -> false;
    }

    private int inventoryMatches(Predicate<ItemStack> test) {
        int n = 0;
        for (Slot slot : menu.slots) {
            if (slot.hasItem() && test.test(slot.getItem())) {
                n++;
            }
        }
        return n;
    }

    // ------------------------------------------------------------------ entrada

    /** Índice (na lista do filtro) da entrada na linha sob o mouse; −2 numa linha vazia; −1 fora. */
    private int entryAt(double mouseX, double mouseY) {
        double rx = mouseX - (leftPos + X0 + 1);
        double ry = mouseY - (topPos + LIST_Y);
        if (rx < 0 || ry < 0 || rx >= listW() - 6 || ry >= listRows() * ROW) {
            return -1;
        }
        int row = scroll + (int) ry / ROW;
        return row < shown.size() ? shown.get(row) : -2;
    }

    private boolean overList(double mouseX, double mouseY) {
        return mouseX >= leftPos + X0 && mouseX < leftPos + X0 + listW()
                && mouseY >= topPos + LIST_Y - 1 && mouseY < topPos + LIST_Y + listRows() * ROW + 1;
    }

    private boolean overBar(double mouseX, double mouseY) {
        int bx = leftPos + X0 + listW() - 5;
        return mouseX >= bx - 1 && mouseX < bx + 5 && mouseY >= topPos + LIST_Y && mouseY < topPos + LIST_Y + listRows() * ROW;
    }

    private void dragBar(double mouseY) {
        int max = maxScroll();
        int h = listRows() * ROW;
        int thumb = thumbHeight(h, listRows(), shown.size());
        double t = (mouseY - (topPos + LIST_Y) - thumb / 2.0) / (h - thumb);
        scroll = (int) Math.round(Math.max(0, Math.min(1, t)) * max);
    }

    private static int thumbHeight(int h, int visible, int total) {
        return Math.max(8, h * visible / Math.max(visible, total));
    }

    /** A linha da aba Tags sob o mouse, ou {@code null}. */
    private @Nullable Candidate candidateAt(double mouseX, double mouseY) {
        if (tab != Tab.TAGS) {
            return null;
        }
        double rx = mouseX - (leftPos + ix() - 2);
        double ry = mouseY - (topPos + CAND_Y);
        if (rx < 0 || rx >= IW + 4 || ry < 0 || ry >= candRows() * CAND_ROW) {
            return null;
        }
        int index = candScroll + (int) ry / CAND_ROW;
        return index < candidates.size() ? candidates.get(index) : null;
    }

    private boolean overCandidates(double mouseX, double mouseY) {
        return tab == Tab.TAGS && mouseX >= leftPos + ix() - 2 && mouseX < leftPos + ix() + IW + 2
                && mouseY >= topPos + CAND_Y && mouseY < topPos + CAND_Y + candRows() * CAND_ROW;
    }

    private boolean overInspector(double mouseX, double mouseY) {
        return tab == Tab.TAGS && !isChemical() && mouseX >= leftPos + ix() - 1 && mouseX < leftPos + ix() + 17
                && mouseY >= topPos + INSPECT_Y - 1 && mouseY < topPos + INSPECT_Y + 17;
    }

    private boolean overInspectorClear(double mouseX, double mouseY) {
        return overInspectorRow(mouseX, mouseY) && hasInspected() && mouseX >= leftPos + ix() + IW - 9;
    }

    private boolean overInspectorRow(double mouseX, double mouseY) {
        return tab == Tab.TAGS && mouseY >= topPos + INSPECT_Y - 1 && mouseY < topPos + INSPECT_Y + 17
                && mouseX >= leftPos + ix() - 1 && mouseX < leftPos + ix() + IW;
    }

    /**
     * Redimensiona em torno do centro, como o Baú: a borda arrastada segue o mouse e a oposta se move
     * igual, então o painel continua centralizado. A altura anda de linha em linha da lista.
     */
    private void dragResize(double mouseX, double mouseY) {
        int w = imageWidth;
        int h = imageHeight;
        if (resizeHandle.changesWidth()) {
            double wanted = resizeHandle.wantedWidth(mouseX);
            w = (int) Math.max(MIN_W, Math.min(maxW(), Math.round(wanted / 2) * 2));
        }
        if (resizeHandle.changesHeight()) {
            double wanted = resizeHandle.wantedHeight(mouseY);
            int steps = (int) Math.round((wanted - MIN_H) / ROW);
            h = Math.max(MIN_H, Math.min(maxH(), MIN_H + steps * ROW));
        }
        if (w != imageWidth || h != imageHeight) {
            imageWidth = savedW = w;
            imageHeight = savedH = h;
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
        if (getFocused() == stockBox && !stockBox.isMouseOver(mouseX, mouseY)) {
            commitStock();
            unfocus();
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
        if (overInspectorClear(mouseX, mouseY)) {
            clearInspect();
            return true;
        }
        if (overInspector(mouseX, mouseY)) {
            ItemStack carried = menu.getCarried();
            if (!carried.isEmpty()) {
                inspect(carried);
            } else if (button == 1) {
                clearInspect();
            }
            return true;
        }
        Candidate candidate = candidateAt(mouseX, mouseY);
        if (candidate != null) {
            toggleCandidate(candidate);
            return true;
        }
        if (button == 0 && overBar(mouseX, mouseY) && maxScroll() > 0) {
            draggingBar = true;
            dragBar(mouseY);
            return true;
        }
        int index = entryAt(mouseX, mouseY);
        if (index != -1) {
            listPress = true;
            if (getFocused() == searchBox) {
                unfocus();
            }
            select(index >= 0 && index != selectedIndex ? index : -1);
            return true;
        }
        if (clearArmed() && !clearButton.isMouseOver(mouseX, mouseY)) {
            clearArmedUntil = 0;
        }
        boolean handled = super.mouseClicked(mouseX, mouseY, button);
        if (button == 1 && tab == Tab.RULE) {
            // botão direito volta: nível, porcentagem
            if (enchantLevel.visible && enchantLevel.isMouseOver(mouseX, mouseY)) {
                stepLevel(-1);
                return true;
            }
            if (durabilityPercent.visible && durabilityPercent.isMouseOver(mouseX, mouseY)) {
                stepPercent(-10);
                return true;
            }
        }
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
        if (draggingBar) {
            dragBar(mouseY);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (resizeHandle.dragging() || draggingBar || listPress) {
            resizeHandle.end();
            draggingBar = false;
            listPress = false;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int step = (int) -Math.signum(scrollY);
        if (overList(mouseX, mouseY)) {
            scroll = Math.max(0, Math.min(maxScroll(), scroll + step));
            return true;
        }
        if (overCandidates(mouseX, mouseY)) {
            candScroll = Math.max(0, Math.min(maxCandScroll(), candScroll + step));
            return true;
        }
        if (tab == Tab.RULE && draft.enchantment().isPresent() && enchantRowContains(mouseX, mouseY)) {
            if (enchantLevel.isMouseOver(mouseX, mouseY)) {
                stepLevel(-step);
            } else {
                cycleEnchantment(step);
            }
            return true;
        }
        if (durabilityPercent.visible && durabilityPercent.isMouseOver(mouseX, mouseY)) {
            stepPercent(-step * 5);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    private boolean enchantRowContains(double mouseX, double mouseY) {
        return mouseX >= enchantPrev.getX() && mouseX < enchantLevel.getX() + enchantLevel.getWidth()
                && mouseY >= enchantPrev.getY() && mouseY < enchantPrev.getY() + COND_ROW;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (getFocused() instanceof EditBox box && box.isFocused()) {
            boolean enter = keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER;
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                if (box == stockBox) {
                    stockBox.setValue(stockText());
                }
                unfocus();
            } else if (enter && box == stockBox) {
                commitStock();
                unfocus();
            } else if (enter && box == tagSearchBox) {
                addTyped();
            } else if (enter && box == scopeBox) {
                submitRule();
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
        commitStock();
        super.removed();
    }

    // ------------------------------------------------------------------ desenho

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        if (menu.version() != lastVersion) {
            syncView();
        }
        rebuildShown(false);
        if (tab == Tab.TAGS) {
            rebuildCandidates();
        }
        refresh();
        hoveredCandidate = candidateAt(mouseX, mouseY);
        super.render(g, mouseX, mouseY, partialTick);
        if (resizeHandle.dragging()) {
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
        int index = entryAt(mouseX, mouseY);
        if (index >= 0) {
            setTooltipForNextRenderPass(entryTooltip(filter().entries().get(index)));
            return;
        }
        if (hoveredCandidate != null) {
            setTooltipForNextRenderPass(candidateTooltip(hoveredCandidate));
            return;
        }
        if (overInspector(mouseX, mouseY)) {
            if (!inspected.isEmpty()) {
                g.renderTooltip(font, getTooltipFromContainerItem(inspected), inspected.getTooltipImage(), inspected, mouseX, mouseY);
            } else {
                setTooltipForNextRenderPass(font.split(tr(isFluid() ? "inspect.tooltip.fluid" : "inspect.tooltip"), 200));
            }
            return;
        }
        if (overInspectorClear(mouseX, mouseY)) {
            setTooltipForNextRenderPass(tr("inspect.clear"));
            return;
        }
        if (JEI && mouseX >= leftPos + X0 && mouseX < leftPos + X0 + listW()
                && mouseY >= topPos + invTop() - 11 && mouseY < topPos + invTop() - 1) {
            setTooltipForNextRenderPass(font.split(tr("jei.tooltip." + (isChemical() ? "chemical" : isFluid() ? "fluid" : "item")), 200));
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
        int w = imageWidth;
        int h = imageHeight;
        GuiPaint.panel(g, x, y, w, h, trim);

        // cabeçalho
        GuiPaint.text(g, font, tr("title"), x + X0, y + HEAD_Y + 3, GuiPaint.FG);
        int titleRight = x + X0 + font.width(tr("title")) + 8;
        int contextRight = backButton.getX() - 6;
        Component context = context();
        int contextW = Math.max(0, Math.min(font.width(context), contextRight - titleRight));
        GuiPaint.text(g, font, GuiPaint.ellipsize(font, context, contextW), contextRight - contextW, y + HEAD_Y + 3,
                GuiPaint.MUTED);

        renderList(g, mouseX, mouseY);
        renderInventory(g);

        // painel da direita
        int px = x + panelX();
        GuiPaint.box(g, px, y + BOX_Y, PANEL_W, h - X0 - BOX_Y, GuiPaint.INSET, GuiPaint.LINE);
        switch (tab) {
            case ENTRY -> renderEntryTab(g);
            case TAGS -> renderTagsTab(g, mouseX, mouseY);
            case RULE -> renderRuleTab(g);
            case MORE -> renderMoreTab(g);
        }
        ResizeGrip.renderSolid(g, leftPos, topPos, imageWidth, imageHeight,
                resizeHandle.hover(mouseX, mouseY, leftPos, topPos, imageWidth, imageHeight), trim);
    }

    private void renderList(GuiGraphics g, int mouseX, int mouseY) {
        int x = leftPos;
        int y = topPos;
        int lw = listW();
        List<FilterEntry> entries = filter().entries();
        Component label = entries.size() >= Filter.MAX_ENTRIES ? tr("entries.full", entries.size())
                : tr("entries", entries.size());
        GuiPaint.text(g, font, label, x + X0, y + LABEL_Y, GuiPaint.MUTED);
        GuiPaint.box(g, x + X0, y + SEARCH_Y, lw, FIELD_H, GuiPaint.INSET, searchBox.isFocused() ? trim : GuiPaint.LINE);

        int rows = listRows();
        int top = y + LIST_Y;
        g.fill(x + X0, top - 1, x + X0 + lw, top + rows * ROW + 1, GuiPaint.BEVEL_DARK);
        g.fill(x + X0 + 1, top, x + X0 + lw - 1, top + rows * ROW, GuiPaint.INSET);
        if (shown.isEmpty()) {
            Component empty = entries.isEmpty() ? tr("empty") : tr("search.none");
            List<FormattedCharSequence> lines = font.split(empty, lw - 16);
            int ty = top + (rows * ROW - lines.size() * 10) / 2;
            for (FormattedCharSequence line : lines) {
                GuiPaint.text(g, font, line, x + X0 + (lw - font.width(line)) / 2, ty, GuiPaint.MUTED);
                ty += 10;
            }
        }
        int hovered = entryAt(mouseX, mouseY);
        int textW = lw - 8 - 22;
        for (int r = 0; r < rows && scroll + r < shown.size(); r++) {
            int index = shown.get(scroll + r);
            FilterEntry entry = entries.get(index);
            int ry = top + r * ROW;
            int rx = x + X0 + 1;
            if (index == selectedIndex) {
                g.fill(rx, ry, x + X0 + lw - 6, ry + ROW, SEL_ROW);
                g.fill(rx, ry, rx + 1, ry + ROW, trim);
            } else if (index == hovered) {
                g.fill(rx, ry, x + X0 + lw - 6, ry + ROW, HOVER_ROW);
            }
            renderEntryIcon(g, entry, rx + 2, ry + 1);
            String stock = entry.stock() > 0 ? "≥ " + stockShort(entry.stock()) : "";
            int stockW = stock.isEmpty() ? 0 : font.width(stock) + 4;
            GuiPaint.text(g, font, GuiPaint.ellipsize(font, entryName(entry), textW - stockW), rx + 21, ry + 1,
                    entry instanceof RuleEntry ? RULE_TEXT : GuiPaint.FG);
            GuiPaint.text(g, font, GuiPaint.ellipsize(font, entryKind(entry), textW), rx + 21, ry + 10, GuiPaint.DISABLED);
            if (!stock.isEmpty()) {
                GuiPaint.textRight(g, font, Component.literal(stock), x + X0 + lw - 8, ry + 1, trim);
            }
        }
        // barra de rolagem
        int max = maxScroll();
        if (max > 0) {
            int bx = x + X0 + lw - 5;
            int hh = rows * ROW;
            g.fill(bx, top, bx + 4, top + hh, GuiPaint.PANEL);
            int thumb = thumbHeight(hh, rows, shown.size());
            int ty = top + Math.round((hh - thumb) * (scroll / (float) max));
            boolean active = draggingBar || overBar(mouseX, mouseY);
            g.fill(bx, ty, bx + 4, ty + thumb, active ? GuiPaint.BUTTON_HOVER_BORDER : GuiPaint.BEVEL_LIGHT);
        }
    }

    private void renderInventory(GuiGraphics g) {
        int x = leftPos;
        int y = topPos;
        int inv = y + invTop();
        GuiPaint.text(g, font, GuiPaint.ellipsize(font, inventoryHint(), listW()), x + X0, inv - 10, GuiPaint.MUTED);
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                GuiPaint.slot(g, x + X0 + col * 18, inv + row * 18);
            }
        }
        for (int col = 0; col < 9; col++) {
            GuiPaint.slot(g, x + X0 + col * 18, inv + 58);
        }
        // o que a regra (ou a entrada selecionada) pega acende, embaixo do item
        Predicate<ItemStack> test = currentHighlight();
        for (Slot slot : menu.slots) {
            if (slot.hasItem() && test.test(slot.getItem())) {
                int sx = x + slot.x - 1;
                int sy = y + slot.y - 1;
                g.fill(sx + 1, sy + 1, sx + 17, sy + 17, GuiPaint.mix(GuiPaint.INSET, trim, 0.35f));
                GuiPaint.outline(g, sx, sy, 18, 18, trim);
            }
        }
    }

    private void renderEntryTab(GuiGraphics g) {
        int x = leftPos + ix();
        int y = topPos;
        if (selected == null) {
            Component hint = !filter().isEmpty() ? tr("select.hint") : JEI ? tr("select.empty.jei") : tr("select.empty");
            wrapped(g, hint, x, y + BOX_Y + 6, IW, 10, GuiPaint.MUTED);
            return;
        }
        GuiPaint.slot(g, x - 1, y + BOX_Y + 5);
        renderEntryIcon(g, selected, x, y + BOX_Y + 6);
        int lines = wrapped(g, entryName(selected), x + 21, y + BOX_Y + 6, IW - 21, 2,
                selected instanceof RuleEntry ? RULE_TEXT : GuiPaint.FG);
        int ty = y + BOX_Y + 6 + Math.max(2, lines) * 10;
        GuiPaint.text(g, font, GuiPaint.ellipsize(font, Component.literal(entryId(selected)), IW), x, ty, GuiPaint.DISABLED);
        ty += 12;
        int stockLabelY = y + innerBottom() - BTN_H - 4 - BTN_H - 11;
        switch (selected) {
            case TagEntry e -> ty = renderMembers(g, e, x, ty, stockLabelY);
            case ModEntry e -> ty = renderMembers(g, e, x, ty, stockLabelY);
            case RuleEntry e -> {
                int n = inventoryMatches(currentHighlight());
                wrapped(g, tr("rule.inventory", n), x, ty, IW, 2, trim);
            }
            case ItemEntry e -> {
                long tags = e.stack().getTags().count();
                wrapped(g, tr("entry.item", tags), x, ty, IW, 3, GuiPaint.MUTED);
            }
            default -> {
            }
        }
        // estoque
        boolean white = filter().listMode() == Filter.ListMode.WHITELIST;
        GuiPaint.text(g, font, GuiPaint.ellipsize(font, white ? tr("stock") : tr("stock.blacklist"), IW), x, stockLabelY,
                GuiPaint.MUTED);
        int fy = y + innerBottom() - BTN_H - 4 - BTN_H;
        GuiPaint.box(g, x + 16, fy, IW - 32, BTN_H, GuiPaint.PANEL, stockBox.isFocused() ? trim : GuiPaint.LINE);
        if (inMb()) {
            GuiPaint.textRight(g, font, tr("unit.mb"), x + IW - 20, fy + 3, GuiPaint.MUTED);
        }
    }

    /** "Pega 38 itens" e os ícones dos primeiros, até {@code limit}; devolve onde parou. */
    private int renderMembers(GuiGraphics g, FilterEntry entry, int x, int y, int limit) {
        if (isChemical()) {
            GuiPaint.text(g, font, tr(entry instanceof ModEntry ? "entry.mod" : "entry.tag"), x, y, GuiPaint.MUTED);
            return y + 10;
        }
        int count;
        List<ItemStack> items = List.of();
        List<FluidStack> fluids = List.of();
        if (isFluid()) {
            fluids = fluidMembers(entry);
            count = fluids.size();
        } else {
            items = itemMembers(entry);
            count = items.size();
        }
        GuiPaint.text(g, font, tr(count >= 64 ? "entry.members.many" : "entry.members", count), x, y, GuiPaint.MUTED);
        y += 11;
        int perRow = IW / 18;
        int rows = Math.max(0, (limit - y - 2) / 18);
        int shownCount = Math.min(count, perRow * rows);
        for (int i = 0; i < shownCount; i++) {
            int cx = x + (i % perRow) * 18;
            int cy = y + (i / perRow) * 18;
            if (isFluid()) {
                GuiPaint.fluid(g, fluids.get(i), cx, cy);
            } else {
                g.renderItem(items.get(i), cx, cy);
            }
        }
        return y + (shownCount + perRow - 1) / perRow * 18;
    }

    private void renderTagsTab(GuiGraphics g, int mouseX, int mouseY) {
        int x = leftPos + ix();
        int y = topPos;
        if (isChemical()) {
            wrapped(g, tr("tags.chemical.hint"), x, y + INSPECT_Y, IW, 2, GuiPaint.MUTED);
        } else {
            // inspetor
            GuiPaint.slot(g, x - 1, y + INSPECT_Y - 1);
            if (overInspector(mouseX, mouseY)) {
                GuiPaint.outline(g, x - 1, y + INSPECT_Y - 1, 18, 18, trim);
            }
            Component name;
            Component sub;
            if (!inspected.isEmpty()) {
                g.renderItem(inspected, x, y + INSPECT_Y);
                name = inspected.getHoverName();
                sub = tr("inspect.tags", inspected.getTags().count());
            } else if (!inspectedFluid.isEmpty()) {
                GuiPaint.fluid(g, inspectedFluid, x, y + INSPECT_Y);
                name = inspectedFluid.getHoverName();
                sub = tr("inspect.tags", fluidTags(inspectedFluid.getFluid()).count());
            } else {
                name = tr("inspect");
                sub = tr("inspect.hint");
            }
            int textW = IW - 21 - (hasInspected() ? 10 : 0);
            GuiPaint.text(g, font, GuiPaint.ellipsize(font, name, textW), x + 21, y + INSPECT_Y, GuiPaint.FG);
            GuiPaint.text(g, font, GuiPaint.ellipsize(font, sub, textW), x + 21, y + INSPECT_Y + 9, GuiPaint.MUTED);
            if (hasInspected()) {
                GuiPaint.text(g, font, Component.literal("×"), x + IW - 7, y + INSPECT_Y,
                        overInspectorClear(mouseX, mouseY) ? DANGER : GuiPaint.MUTED);
            }
        }
        GuiPaint.box(g, x, y + TAG_SEARCH_Y, IW, FIELD_H, GuiPaint.PANEL, tagSearchBox.isFocused() ? trim : GuiPaint.LINE);

        // linhas
        int rows = candRows();
        if (candidates.isEmpty()) {
            Component empty = !tagSearchDraft.isBlank() ? tr("tags.none")
                    : isChemical() ? tr("tags.chemical.empty") : tr(isFluid() ? "tags.empty.fluid" : "tags.empty");
            wrapped(g, empty, x, y + CAND_Y + 2, IW, rows, GuiPaint.DISABLED);
        }
        for (int r = 0; r < rows && candScroll + r < candidates.size(); r++) {
            Candidate c = candidates.get(candScroll + r);
            int ry = y + CAND_Y + r * CAND_ROW;
            boolean had = inFilter(c.entry());
            boolean on = had || checked.contains(c.entry());
            if (c == hoveredCandidate) {
                g.fill(x - 2, ry, x + IW + 2, ry + CAND_ROW, HOVER_ROW);
            }
            GuiPaint.checkbox(g, x, ry + 1, on, had ? GuiPaint.DISABLED : trim);
            String count = c.count() < 0 ? "" : RateFormat.abbreviate(c.count());
            int countW = count.isEmpty() ? 0 : font.width(count) + 4;
            GuiPaint.text(g, font, GuiPaint.ellipsize(font, Component.literal(c.label()), IW - 12 - countW), x + 12, ry + 2,
                    had ? GuiPaint.DISABLED : c.entry() instanceof ModEntry ? 0xFFF2C04A : GuiPaint.FG);
            if (!count.isEmpty()) {
                GuiPaint.textRight(g, font, Component.literal(count), x + IW, ry + 2, GuiPaint.DISABLED);
            }
        }
        int max = maxCandScroll();
        if (max > 0) {
            int bx = x + IW + 1;
            int hh = rows * CAND_ROW;
            int thumb = thumbHeight(hh, rows, candidates.size());
            int ty = y + CAND_Y + Math.round((hh - thumb) * (candScroll / (float) max));
            g.fill(bx, y + CAND_Y, bx + 2, y + CAND_Y + hh, GuiPaint.PANEL);
            g.fill(bx, ty, bx + 2, ty + thumb, GuiPaint.BEVEL_LIGHT);
        }

        // prévia: a linha sob o mouse ou a última marcada
        int py = y + innerBottom() - BTN_H - 3 - PREVIEW_H;
        g.fill(x - 2, py, x + IW + 2, py + 1, GuiPaint.LINE);
        Candidate pv = hoveredCandidate;
        if (pv == null && !checked.isEmpty()) {
            FilterEntry last = null;
            for (FilterEntry entry : checked) {
                last = entry;
            }
            for (Candidate c : candidates) {
                if (c.entry().equals(last)) {
                    pv = c;
                }
            }
        }
        if (pv == null || pv.entry() instanceof ChemicalEntry) {
            wrapped(g, tr("tags.preview.hint"), x, py + 4, IW, 2, GuiPaint.DISABLED);
            return;
        }
        GuiPaint.text(g, font, GuiPaint.ellipsize(font, Component.literal(pv.label()), IW), x, py + 3, trim);
        int perRow = IW / 17;
        if (isFluid()) {
            List<FluidStack> fluids = fluidMembers(pv.entry());
            for (int i = 0; i < Math.min(perRow, fluids.size()); i++) {
                GuiPaint.fluid(g, fluids.get(i), x + i * 17, py + 13);
            }
        } else if (!isChemical()) {
            List<ItemStack> items = itemMembers(pv.entry());
            for (int i = 0; i < Math.min(perRow, items.size()); i++) {
                g.renderItem(items.get(i), x + i * 17, py + 13);
            }
        }
    }

    private void renderRuleTab(GuiGraphics g) {
        int x = leftPos + ix();
        int y = topPos;
        GuiPaint.text(g, font, editing >= 0 ? tr("rule.editing") : tr("rule.new"), x, y + BOX_Y + 6, GuiPaint.FG);
        for (Property property : Property.values()) {
            int ry = y + COND_Y + property.ordinal() * COND_ROW;
            boolean set = draft.flag(property) != null;
            GuiPaint.text(g, font, GuiPaint.ellipsize(font, tr("rule.prop." + property.getSerializedName()), IW - 3 * TRI_W - 4),
                    x, ry + 1, set ? GuiPaint.FG : GuiPaint.MUTED);
        }
        int ry = y + COND_Y + Property.values().length * COND_ROW + 3;
        if (draft.enchantment().isPresent()) {
            ry += COND_ROW + 1;
            Holder<Enchantment> holder = draftEnchantment();
            Component name = holder != null ? holder.value().description()
                    : Component.literal(draft.enchantment().get().id().toString());
            int left = enchantPrev.getX() + 9 + 2;
            int right = enchantNext.getX() - 2;
            FormattedCharSequence text = GuiPaint.ellipsize(font, name, right - left);
            GuiPaint.text(g, font, text, (left + right - font.width(text)) / 2, ry + 2, GuiPaint.FG);
        } else {
            GuiPaint.textRight(g, font, tr("rule.any"), x + IW, ry + 2, GuiPaint.DISABLED);
        }
        ry += COND_ROW + 3;
        GuiPaint.text(g, font, GuiPaint.ellipsize(font, tr("rule.durability"), IW - 28 - 4 - 3 * 14 - 2), x, ry + 1,
                draft.durability().isPresent() ? GuiPaint.FG : GuiPaint.MUTED);
        ry += COND_ROW + 3;
        GuiPaint.text(g, font, tr("rule.scope"), x, ry + 3, draft.scope().isEmpty() ? GuiPaint.MUTED : GuiPaint.FG);
        int scopeX = x + font.width(tr("rule.scope")) + 5;
        int border = !draft.validScope() ? DANGER : scopeBox.isFocused() ? trim : GuiPaint.LINE;
        GuiPaint.box(g, scopeX, ry, x + IW - scopeX, FIELD_H, GuiPaint.PANEL, border);
        ry += FIELD_H + 5;
        int limit = y + innerBottom() - BTN_H - 3;
        if (draft.isEmpty()) {
            wrapped(g, tr("rule.empty"), x, ry, IW, (limit - ry) / 10, GuiPaint.DISABLED);
        } else if (!draft.validScope()) {
            wrapped(g, tr("rule.scope.invalid"), x, ry, IW, (limit - ry) / 10, DANGER);
        } else {
            int lines = wrapped(g, ruleText(draft), x, ry, IW, Math.max(1, (limit - ry) / 10 - 1), RULE_TEXT);
            int n = inventoryMatches(draftPredicate());
            if (ry + lines * 10 + 10 <= limit) {
                GuiPaint.text(g, font, GuiPaint.ellipsize(font, tr("rule.inventory", n), IW), x, ry + lines * 10, GuiPaint.MUTED);
            }
        }
    }

    private void renderMoreTab(GuiGraphics g) {
        int x = leftPos + ix();
        int y = topPos;
        int ty = y + BOX_Y + 6;
        if (!view().isCard()) {
            GuiPaint.text(g, font, tr("card"), x, ty, GuiPaint.FG);
            wrapped(g, tr("card.hint"), x, ty + 11, IW, 2, GuiPaint.MUTED);
            ty = y + BOX_Y + 40 + BTN_H + 8;
        }
        if (isItem()) {
            GuiPaint.text(g, font, tr("components"), x, ty, GuiPaint.FG);
            ty += 11 + 10 * wrapped(g, filter().matchComponents() ? tr("components.on.hint") : tr("components.off.hint"),
                    x, ty + 11, IW, 4, GuiPaint.MUTED);
            ty += 6;
        }
        wrapped(g, tr("more.hint"), x, ty, IW, Math.max(0, (y + innerBottom() - BTN_H - 4 - ty) / 10), GuiPaint.DISABLED);
    }

    /** Texto quebrado em linhas até {@code maxLines}; devolve quantas linhas usou. */
    private int wrapped(GuiGraphics g, Component text, int x, int y, int width, int maxLines, int color) {
        List<FormattedCharSequence> lines = font.split(text, width);
        int n = Math.min(lines.size(), maxLines);
        for (int i = 0; i < n; i++) {
            FormattedCharSequence line = lines.get(i);
            if (i == n - 1 && lines.size() > n) {
                // a última linha que cabe leva as reticências
                StringBuilder rest = new StringBuilder();
                for (int j = i; j < lines.size(); j++) {
                    lines.get(j).accept((index, style, codePoint) -> {
                        rest.appendCodePoint(codePoint);
                        return true;
                    });
                    rest.append(' ');
                }
                line = GuiPaint.ellipsize(font, Component.literal(rest.toString().strip()), width);
            }
            GuiPaint.text(g, font, line, x, y + i * 10, color);
        }
        return n;
    }

    // ------------------------------------------------------------------ entradas

    private void renderEntryIcon(GuiGraphics g, FilterEntry entry, int x, int y) {
        switch (entry) {
            case ItemEntry e -> g.renderItem(e.stack(), x, y);
            case FluidEntry e -> GuiPaint.fluid(g, e.stack(), x, y);
            case TagEntry e -> renderMark(g, e, "#", x, y);
            case ModEntry e -> renderMark(g, e, "@", x, y);
            case ChemicalEntry e -> GuiPaint.chemical(g, font, e.chemical(), x, y, trim);
            case RuleEntry e -> renderRuleIcon(g, x, y);
        }
    }

    /** Losango roxo da regra por propriedade. */
    private static void renderRuleIcon(GuiGraphics g, int x, int y) {
        int cx = x + 8;
        for (int i = 0; i < 6; i++) {
            g.fill(cx - i - 1, y + 2 + i, cx + i + 1, y + 3 + i, RULE_COLOR);
            g.fill(cx - i - 1, y + 13 - i, cx + i + 1, y + 14 - i, GuiPaint.mix(RULE_COLOR, 0xFF000000, 0.25f));
        }
        g.fill(cx - 3, y + 5, cx - 1, y + 7, 0xFFE9DDFF);
    }

    /** Tag ou mod: um membro (trocando a cada segundo) com a marca {@code #}/{@code @}, ou só a marca. */
    private void renderMark(GuiGraphics g, FilterEntry entry, String mark, int x, int y) {
        int count;
        if (isChemical()) {
            count = 0;
        } else if (isFluid()) {
            List<FluidStack> members = fluidMembers(entry);
            count = members.size();
            if (count > 0) {
                GuiPaint.fluid(g, members.get((int) (Util.getMillis() / 1000 % count)), x, y);
            }
        } else {
            List<ItemStack> members = itemMembers(entry);
            count = members.size();
            if (count > 0) {
                g.renderItem(members.get((int) (Util.getMillis() / 1000 % count)), x, y);
            }
        }
        int color = mark.equals("@") ? 0xFFF2C04A : trim;
        g.pose().pushPose();
        g.pose().translate(0, 0, 200);
        if (count == 0) {
            g.fill(x, y, x + 16, y + 16, GuiPaint.mix(GuiPaint.INSET, color, 0.18f));
            GuiPaint.textCentered(g, font, Component.literal(mark), x + 8, y + 4, color);
        } else {
            g.fill(x - 1, y - 1, x + 6, y + 8, GuiPaint.BEVEL_DARK);
            g.drawString(font, mark, x, y, color, false);
        }
        g.pose().popPose();
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
            case TagEntry e -> Component.literal("#" + e.tag());
            case ModEntry e -> Component.literal(ModList.get().getModContainerById(e.modId())
                    .map(c -> c.getModInfo().getDisplayName()).orElse("@" + e.modId()));
            case ChemicalEntry e -> Chemicals.name(e.chemical());
            case RuleEntry e -> ruleText(e.rule());
        };
    }

    /** Segunda linha da entrada na lista: o tipo e quanto ela pega. */
    private Component entryKind(FilterEntry entry) {
        return switch (entry) {
            case ItemEntry e -> filter().matchComponents() ? tr("kind.item.components") : tr("kind.item");
            case FluidEntry e -> tr("kind.fluid");
            case ChemicalEntry e -> tr("kind.chemical");
            case TagEntry e -> isChemical() ? tr("kind.tag.plain") : tr("kind.tag", memberCount(e));
            case ModEntry e -> isChemical() ? tr("kind.mod.plain") : tr("kind.mod", modCounts().getOrDefault(e.modId(), 0));
            case RuleEntry e -> tr("kind.rule");
        };
    }

    private int memberCount(TagEntry e) {
        return isFluid()
                ? BuiltInRegistries.FLUID.getTag(TagKey.create(Registries.FLUID, e.tag())).map(HolderSet::size).orElse(0)
                : BuiltInRegistries.ITEM.getTag(TagKey.create(Registries.ITEM, e.tag())).map(HolderSet::size).orElse(0);
    }

    private static String entryId(FilterEntry entry) {
        return switch (entry) {
            case ItemEntry e -> BuiltInRegistries.ITEM.getKey(e.stack().getItem()).toString();
            case FluidEntry e -> BuiltInRegistries.FLUID.getKey(e.stack().getFluid()).toString();
            case TagEntry e -> "#" + e.tag();
            case ModEntry e -> "@" + e.modId();
            case ChemicalEntry e -> e.chemical().toString();
            case RuleEntry e -> e.rule().scope();
        };
    }

    private List<FormattedCharSequence> entryTooltip(FilterEntry entry) {
        List<FormattedCharSequence> lines = new ArrayList<>(font.split(entryName(entry), 220));
        if (!entryId(entry).isEmpty()) {
            lines.add(Component.literal(entryId(entry)).withStyle(ChatFormatting.DARK_GRAY).getVisualOrderText());
        }
        if (entry instanceof TagEntry e) {
            Component name = Component.translatableWithFallback(Tags.getTagTranslationKey(isFluid()
                    ? TagKey.create(Registries.FLUID, e.tag()) : TagKey.create(Registries.ITEM, e.tag())), "");
            if (!name.getString().isEmpty()) {
                lines.add(name.copy().withStyle(ChatFormatting.GRAY).getVisualOrderText());
            }
            lines.add(tr("entry.tag").copy().withStyle(ChatFormatting.GRAY).getVisualOrderText());
        } else if (entry instanceof ModEntry) {
            lines.add(tr("entry.mod").copy().withStyle(ChatFormatting.GRAY).getVisualOrderText());
        } else if (entry instanceof RuleEntry) {
            lines.add(tr("entry.rule").copy().withStyle(ChatFormatting.GRAY).getVisualOrderText());
        }
        if (entry.stock() > 0) {
            Component amount = inMb() ? Component.literal(entry.stock() + " ").append(tr("unit.mb"))
                    : Component.literal(Long.toString(entry.stock()));
            lines.add(tr("stock.line", amount).copy().withStyle(ChatFormatting.GRAY).getVisualOrderText());
        }
        return lines;
    }

    private List<FormattedCharSequence> candidateTooltip(Candidate c) {
        List<FormattedCharSequence> lines = new ArrayList<>();
        lines.add(Component.literal(c.label()).getVisualOrderText());
        if (c.entry() instanceof TagEntry e) {
            Component name = Component.translatableWithFallback(Tags.getTagTranslationKey(isFluid()
                    ? TagKey.create(Registries.FLUID, e.tag()) : TagKey.create(Registries.ITEM, e.tag())), "");
            if (!name.getString().isEmpty()) {
                lines.add(name.copy().withStyle(ChatFormatting.GRAY).getVisualOrderText());
            }
        } else if (c.entry() instanceof ModEntry e) {
            ModList.get().getModContainerById(e.modId()).ifPresent(mod -> lines.add(Component.literal(
                    mod.getModInfo().getDisplayName()).withStyle(ChatFormatting.GRAY).getVisualOrderText()));
        }
        if (c.count() >= 0) {
            lines.add(tr(isFluid() ? "tags.count.fluid" : "tags.count", c.count()).copy().withStyle(ChatFormatting.GRAY)
                    .getVisualOrderText());
        }
        lines.add((inFilter(c.entry()) ? tr("tags.in_filter") : checked.contains(c.entry()) ? tr("tags.uncheck") : tr("tags.check"))
                .copy().withStyle(ChatFormatting.DARK_GRAY).getVisualOrderText());
        return lines;
    }

    /** Estoque abreviado: itens como estão; fluidos e químicos em milhares de mB a partir de 1.000. */
    private String stockShort(long stock) {
        if (!inMb()) {
            return RateFormat.abbreviate(stock);
        }
        return stock < 1000 ? stock + " mB" : RateFormat.abbreviate(stock / 1000) + " B";
    }

    /** A linha acima do inventário: como adicionar; com o JEI, cita ele também. */
    private Component inventoryHint() {
        String type = isChemical() ? "chemical" : isFluid() ? "fluid" : "item";
        return tr("hint." + type + (JEI ? ".jei" : ""));
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
        return new Rect2i(leftPos + X0, topPos + LIST_Y - 1, listW(), listRows() * ROW + 2);
    }

    /** O slot do inspetor, se a aba Tags está aberta e o filtro tem tags (itens ou fluidos); senão, nulo. */
    public @Nullable Rect2i inspectArea() {
        return tab == Tab.TAGS && !isChemical() ? new Rect2i(leftPos + ix() - 1, topPos + INSPECT_Y - 1, 18, 18) : null;
    }

    /** O ingrediente serve para o inspetor (item num filtro de itens; fluido ou balde num de fluidos)? */
    public boolean canInspect(Object ingredient) {
        return switch (ingredient) {
            case ItemStack stack when isItem() -> !stack.isEmpty();
            case ItemStack stack when isFluid() -> FluidUtil.getFluidContained(stack).filter(f -> !f.isEmpty()).isPresent();
            case FluidStack stack when isFluid() -> !stack.isEmpty();
            default -> false;
        };
    }

    /** Põe um ingrediente de fora no inspetor (só a tela; nada vai ao servidor). */
    public void inspectGhost(Object ingredient) {
        switch (ingredient) {
            case ItemStack stack -> inspect(stack);
            case FluidStack stack -> inspectFluid(stack);
            default -> {
            }
        }
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
            applyLocally(filter().withEntry(entry));
        } else {
            PacketDistributor.sendToServer(new AddFilterEntryPayload(menu.containerId, entry));
        }
    }

    /** O que o painel ocupa, para o JEI não se sobrepor. */
    public List<Rect2i> extraAreas() {
        return List.of(new Rect2i(leftPos, topPos, imageWidth, imageHeight));
    }

    // ------------------------------------------------------------------ pintura dos botões

    private void paintTextButton(GuiGraphics g, FlatButton b, boolean hovered, Component text, boolean pressed) {
        int border = pressed ? trim : hovered ? GuiPaint.BUTTON_HOVER_BORDER : GuiPaint.BUTTON_BORDER;
        GuiPaint.box(g, b.getX(), b.getY(), b.getWidth(), b.getHeight(), b.active ? GuiPaint.BUTTON : GuiPaint.INSET,
                b.active ? border : GuiPaint.LINE);
        FormattedCharSequence label = GuiPaint.ellipsize(font, text, b.getWidth() - 4);
        g.drawString(font, label, b.getX() + (b.getWidth() - font.width(label)) / 2 + 1, b.getY() + (b.getHeight() - 8) / 2,
                b.active ? GuiPaint.FG : GuiPaint.DISABLED, false);
    }

    /** Botão principal, na cor do tier. */
    private void paintPrimary(GuiGraphics g, FlatButton b, boolean hovered, Component text) {
        if (!b.active) {
            paintTextButton(g, b, false, text, false);
            return;
        }
        int fill = hovered ? GuiPaint.mix(trim, 0xFFFFFFFF, 0.2f) : trim;
        GuiPaint.box(g, b.getX(), b.getY(), b.getWidth(), b.getHeight(), fill, fill);
        FormattedCharSequence label = GuiPaint.ellipsize(font, text, b.getWidth() - 4);
        g.drawString(font, label, b.getX() + (b.getWidth() - font.width(label)) / 2 + 1, b.getY() + (b.getHeight() - 8) / 2,
                GuiPaint.DARK_TEXT, false);
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

    /** Parte de um controle segmentado (lista, abas): a escolhida fica cheia na cor do tier. */
    private void paintSegment(GuiGraphics g, FlatButton b, boolean hovered, Component text, boolean selected) {
        if (selected) {
            GuiPaint.box(g, b.getX(), b.getY(), b.getWidth(), b.getHeight(), trim, trim);
        } else {
            GuiPaint.box(g, b.getX(), b.getY(), b.getWidth(), b.getHeight(), GuiPaint.BUTTON,
                    hovered ? GuiPaint.BUTTON_HOVER_BORDER : GuiPaint.BUTTON_BORDER);
        }
        FormattedCharSequence label = GuiPaint.ellipsize(font, text, b.getWidth() - 4);
        g.drawString(font, label, b.getX() + (b.getWidth() - font.width(label)) / 2, b.getY() + (b.getHeight() - 8) / 2,
                selected ? GuiPaint.DARK_TEXT : GuiPaint.FG, false);
    }

    /** Uma opção de — / Sim / Não: a escolhida cheia (Não em vermelho). */
    private void paintTri(GuiGraphics g, FlatButton b, boolean hovered, Component text, boolean selected, boolean negative) {
        int color = negative ? DANGER : trim;
        if (selected) {
            GuiPaint.box(g, b.getX(), b.getY(), b.getWidth(), b.getHeight(), color, color);
        } else {
            GuiPaint.box(g, b.getX(), b.getY(), b.getWidth(), b.getHeight(), GuiPaint.BUTTON,
                    hovered ? GuiPaint.BUTTON_HOVER_BORDER : GuiPaint.BUTTON_BORDER);
        }
        GuiPaint.textCentered(g, font, text, b.getX() + b.getWidth() / 2 + 1, b.getY() + (b.getHeight() - 8) / 2 + 1,
                selected ? (negative ? 0xFFFFFFFF : GuiPaint.DARK_TEXT) : GuiPaint.MUTED);
    }

    /** Caixa de marcar com o texto ao lado, sem moldura. */
    private void paintCheck(GuiGraphics g, FlatButton b, boolean hovered, Component text, boolean on) {
        GuiPaint.checkbox(g, b.getX(), b.getY() + 1, on, trim);
        GuiPaint.text(g, font, GuiPaint.ellipsize(font, text, 64), b.getX() + 12, b.getY() + 2,
                on || hovered ? GuiPaint.FG : GuiPaint.MUTED);
    }

    private void paintComponents(GuiGraphics g, FlatButton b, boolean hovered) {
        boolean on = filter().matchComponents();
        GuiPaint.box(g, b.getX(), b.getY(), b.getWidth(), b.getHeight(), GuiPaint.BUTTON,
                hovered ? GuiPaint.BUTTON_HOVER_BORDER : GuiPaint.BUTTON_BORDER);
        GuiPaint.checkbox(g, b.getX() + 3, b.getY() + 2, on, trim);
        GuiPaint.text(g, font, tr("components"), b.getX() + 16, b.getY() + 3, on ? GuiPaint.FG : GuiPaint.MUTED);
    }

    // ------------------------------------------------------------------ captura e teste de ponta a ponta

    void previewTrim(int color) {
        trimOverride = color;
        trim = color;
    }

    void previewSelect(int index) {
        select(index);
    }

    void previewTab(Tab t) {
        openTab(t);
    }

    void previewTagSearch(String text) {
        openTab(Tab.TAGS);
        tagSearchBox.setValue(text);
    }

    void previewInspect(ItemStack stack) {
        inspect(stack);
        openTab(Tab.TAGS);
    }

    void previewCheck(int candidate) {
        rebuildCandidates();
        if (candidate >= 0 && candidate < candidates.size()) {
            toggleCandidate(candidates.get(candidate));
        }
    }

    void previewRule(ItemRule rule) {
        openTab(Tab.RULE);
        setDraft(rule);
        scopeBox.setValue(rule.scope());
    }

    void previewArmClear() {
        clearArmedUntil = Util.getMillis() + 60_000;
    }

    Tab tab() {
        return tab;
    }

    EditBox tagSearchBox() {
        return tagSearchBox;
    }

    EditBox scopeBox() {
        return scopeBox;
    }

    /** Linhas da aba Tags (rótulos), para o teste de ponta a ponta. */
    List<String> candidateLabels() {
        rebuildCandidates();
        return candidates.stream().map(Candidate::label).toList();
    }

    /** Rótulos das linhas marcadas (fora as que já estão no filtro). */
    List<String> checkedLabels() {
        List<String> labels = new ArrayList<>();
        for (FilterEntry entry : checked) {
            labels.add(entryId(entry));
        }
        return labels;
    }

    /** Centro da linha {@code index} da aba Tags visível, em coordenadas da tela. */
    int[] candidateCenter(int index) {
        return new int[] {leftPos + ix() + 30, topPos + CAND_Y + (index - candScroll) * CAND_ROW + CAND_ROW / 2};
    }

    /** Centro do slot do inspetor, em coordenadas da tela. */
    int[] inspectorCenter() {
        return new int[] {leftPos + ix() + 8, topPos + INSPECT_Y + 8};
    }

    /** Centro da linha da entrada {@code index} visível, para simular o mouse em cima na captura. */
    int[] previewEntryCenter(int index) {
        int row = shown.indexOf(index) - scroll;
        return new int[] {leftPos + X0 + 40, topPos + LIST_Y + row * ROW + ROW / 2};
    }

    /** Ponto no meio da alça de redimensionar, em coordenadas da tela. */
    int[] gripPoint() {
        return new int[] {leftPos + imageWidth - 4, topPos + imageHeight - 4};
    }

    int panelWidth() {
        return imageWidth;
    }

    int panelHeight() {
        return imageHeight;
    }

    /** O inventário acompanhou a tela: o primeiro slot da mochila no lugar certo. */
    boolean inventoryFollows() {
        Slot first = menu.slots.get(0);
        return first.x == X0 + 1 && first.y == invTop() + 1;
    }
}
