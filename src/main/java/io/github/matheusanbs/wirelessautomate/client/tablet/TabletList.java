package io.github.matheusanbs.wirelessautomate.client.tablet;

import io.github.matheusanbs.wirelessautomate.client.GuiPaint;
import io.github.matheusanbs.wirelessautomate.client.router.NodePanel;
import io.github.matheusanbs.wirelessautomate.client.tablet.ListLayout.Hot;
import io.github.matheusanbs.wirelessautomate.client.tablet.ListLayout.Kind;
import io.github.matheusanbs.wirelessautomate.menu.TabletMenu;
import io.github.matheusanbs.wirelessautomate.menu.TabletSnapshot;
import io.github.matheusanbs.wirelessautomate.menu.TabletSnapshot.NodeView;
import io.github.matheusanbs.wirelessautomate.menu.TabletSnapshot.ProfileSummary;
import io.github.matheusanbs.wirelessautomate.menu.TabletSnapshot.Query;
import io.github.matheusanbs.wirelessautomate.menu.TabletSnapshot.QuickFilter;
import io.github.matheusanbs.wirelessautomate.network.LoadedTypes;
import io.github.matheusanbs.wirelessautomate.network.NodeIndex.NodeKey;
import io.github.matheusanbs.wirelessautomate.network.ResourceType;
import io.github.matheusanbs.wirelessautomate.packet.PanelNoticePayload;
import io.github.matheusanbs.wirelessautomate.packet.PanelNotices;
import io.github.matheusanbs.wirelessautomate.packet.TabletPanelPayload;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.IntSupplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/**
 * A aba Lista do Tablet (especificação, "Lista do Tablet"): à esquerda a busca com sugestões e etiquetas, os
 * filtros rápidos, a faixa do perfil copiado e os nós agrupados pela máquina, com os roteadores abertos em árvore
 * ({@link ListTree}); à direita o
 * painel do nó escolhido ({@link NodePanel}), com Tela completa, Copiar perfil e Colar perfil. Tudo desenhado e
 * respondido por geometria, como a {@link TabletScreen} que a hospeda; os botões fixos do modo Selecionar
 * ficam na tela ({@link SelectBar}).
 *
 * <p>Esta classe é a anfitriã da aba: guarda a lista (rolagem, nó escolhido, seleção) e repassa o desenho, o
 * mouse, o teclado e as dicas para as partes ({@link ListSearch}, {@link ChosenPanel}, {@link ListLayout},
 * {@link ListPainter}, {@link ListClicks}, {@link ListKeys}, {@link ListTooltips}). As linhas são do
 * {@link ListModel} (o modelo) e do {@link ListRows} (o desenho); as da árvore, do {@link TreeRows} (o desenho), do
 * {@link TreeClicks} (o mouse) e do {@link TreeNav} (a escolha e o teclado); as ações do perfil, do
 * {@link ListProfileActions}.
 */
public final class TabletList {
    final TabletMenu menu;
    final TabletActions actions;
    final NodeText nodes;
    /** Altura atual da tela do Tablet (o cubo do painel encolhe nas baixas). */
    final IntSupplier screenHeight;
    final ListSearch search;
    final ChosenPanel chosenPanel;
    final ListTexts texts;
    private final ListLayout layout;
    private final ListPainter painter;
    private final ListRows rows;
    private final ListKeys keys;
    private final ListTooltips tooltips;
    private final ListClicks clicks;
    /** As linhas: os nós agrupados, a árvore e os grupos recolhidos. */
    final ListModel model;
    final TreeRows treeRows;
    final TreeNav treeNav;
    final TreeClicks treeClicks;
    final ListProfileActions profileActions;

    // lista
    int scroll;
    @Nullable NodeKey chosen;
    /** Nó a pôr na vista no próximo arranjo (o restaurado ao abrir, o escolhido pelas setas). */
    @Nullable NodeKey revealKey;
    boolean multi;
    final Set<NodeKey> selected = new LinkedHashSet<>();
    ResourceType chipType = LoadedTypes.LIST.isEmpty() ? ResourceType.ITEM : LoadedTypes.LIST.getFirst();

    TabletList(TabletMenu menu, TabletActions actions, NodeText nodes, IntSupplier screenHeight) {
        this.menu = menu;
        this.actions = actions;
        this.nodes = nodes;
        this.screenHeight = screenHeight;
        this.model = new ListModel(menu::snapshot);
        this.profileActions = new ListProfileActions(this);
        Query query = menu.snapshot().query();
        this.search = new ListSearch(this, query);
        search.typeFilter.ifPresent(t -> chipType = t);
        this.chosen = menu.restoredNode();
        this.revealKey = chosen;
        this.chosenPanel = new ChosenPanel(this, menu, chosen);
        this.texts = new ListTexts(this);
        this.treeRows = new TreeRows(this);
        this.treeNav = new TreeNav(this);
        this.treeClicks = new TreeClicks(this);
        this.layout = new ListLayout(this);
        this.painter = new ListPainter(this, layout);
        this.rows = new ListRows(this, layout);
        this.keys = new ListKeys(this);
        this.tooltips = new ListTooltips(this, layout);
        this.clicks = new ListClicks(this, layout);
    }

    /** A caixa de busca da tela (um widget dela), refeita a cada {@code init}. */
    void attach(EditBox box) {
        search.attach(box);
    }

    // ------------------------------------------------------------------ estado

    TabletSnapshot snapshot() {
        return menu.snapshot();
    }

    static Font font() {
        return Minecraft.getInstance().font;
    }

    /** Há busca, etiqueta, filtro rápido ou filtro de tipo. */
    boolean filtering() {
        return search.filtering();
    }

    Optional<ResourceType> typeFilter() {
        return search.typeFilter;
    }

    boolean multi() {
        return multi;
    }

    Set<NodeKey> selected() {
        return selected;
    }

    public @Nullable NodeKey chosen() {
        return chosen;
    }

    /** O tipo dos quadradinhos das linhas: a aba do painel. */
    ResourceType chipType() {
        return chipType;
    }

    Optional<ProfileSummary> profile() {
        return snapshot().profile();
    }

    /** O painel do servidor para o nó escolhido; um painel atrasado de outro nó não vale. */
    @Nullable TabletPanelPayload panelPayload() {
        return chosenPanel.payload();
    }

    /** O componente do painel do nó escolhido, ou {@code null} sem nó ou antes de o servidor responder. */
    @Nullable NodePanel panel() {
        return chosenPanel.panel();
    }

    /** A cada tique da tela: a consulta sai um pouco depois da última letra. */
    void tick() {
        search.tick();
        chosenPanel.followServerDrop();
    }

    /** Snapshot novo: as sugestões e a seleção que sumiu. */
    void onSnapshot() {
        search.onSnapshot();
        Set<NodeKey> present = new HashSet<>();
        snapshot().nodes().forEach(n -> present.add(n.key()));
        selected.retainAll(present);
    }

    // ------------------------------------------------------------------ ações

    /** Tira a busca, as etiquetas, os filtros rápidos e o de tipo. */
    public void clearAll() {
        search.clearAll();
    }

    /**
     * O clique numa linha dos Fluxos: só os nós da rede (a única etiqueta de rede) e do tipo, com o painel na
     * aba dele; sem tipo, a rede com todos os tipos.
     */
    void filterFlow(Optional<ResourceType> type, Optional<UUID> network) {
        network.ifPresent(search::onlyNetwork);
        if (type.isPresent()) {
            filterType(type.get());
        } else {
            search.clearType();
        }
    }

    /** Só os nós com o tipo, e o painel nessa aba. */
    void filterType(ResourceType type) {
        search.typeFilter = Optional.of(type);
        chipType = type;
        NodePanel panel = chosenPanel.current();
        if (panel != null) {
            panel.select(type);
        }
        search.sendQuery();
    }

    /**
     * Escolhe o nó do painel (no servidor também). O aviso da regra D1 e o do tirar a rede das faces sem modo
     * saem: o Desfazer deles age no nó escolhido, e ele passaria a ser outro.
     */
    void choose(@Nullable NodeKey key) {
        if (key != null && key.equals(chosen)) {
            return;
        }
        chosen = key;
        PanelNoticePayload notice = PanelNotices.current();
        if (notice != null && (notice.undo() == PanelNoticePayload.Undo.CLEAR_FACE_NETWORK
                || notice.undo() == PanelNoticePayload.Undo.RESTORE_IDLE_NETWORKS)) {
            PanelNotices.clear();
        }
        actions.selectNode(key);
        revealKey = key;
    }

    void toggleMulti() {
        multi = !multi;
        if (!multi) {
            selected.clear();
        }
    }

    /** Todos: marca todos os nós da lista; se já estavam, desmarca. */
    void selectAll() {
        List<NodeKey> all = snapshot().nodes().stream().map(NodeView::key).toList();
        if (selected.containsAll(all)) {
            all.forEach(selected::remove);
        } else {
            selected.addAll(all);
        }
    }

    // ------------------------------------------------------------------ geometria

    /** O retângulo da aba (de cima da busca até o fim da tela). */
    void layout(int x, int y, int w, int bottomY) {
        layout.layout(x, y, w, bottomY);
    }

    /** A área do tipo no quadro arranjado, ou {@code null}. */
    @Nullable Hot layoutHot(Kind kind, int arg) {
        return layout.hot(kind, arg);
    }

    int listWidth() {
        return layout.listW;
    }

    int barY() {
        return layout.barY();
    }

    /** Onde as linhas de ação do modo Selecionar começam (duas linhas acima do fim). */
    int multiBarY() {
        return layout.multiBarY();
    }

    // ------------------------------------------------------------------ entrada

    boolean mouseClicked(double mx, double my, int button) {
        return clicks.mouseClicked(mx, my, button);
    }

    boolean mouseScrolled(double mx, double my, double scrollY) {
        return clicks.mouseScrolled(mx, my, scrollY);
    }

    /** O teclado da Lista (especificação, "Teclado"); {@code false} deixa a tecla para a busca ou a tela. */
    boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return keys.keyPressed(keyCode, scanCode, modifiers);
    }

    /** Fecha sem salvar a caixa do nome aberta no painel (a aba trocou). */
    void cancelLabel() {
        NodePanel p = chosenPanel.current();
        if (p != null) {
            p.cancelLabel();
        }
    }

    /** Uma letra para a caixa do nome aberta no painel; {@code false} deixa a letra para a busca. */
    boolean charTyped(char codePoint, int modifiers) {
        NodePanel p = chosenPanel.current();
        return p != null && p.charTyped(codePoint, modifiers);
    }

    /**
     * A dica do ponto, ou {@code null}. Um texto cortado sob o mouse vem inteiro primeiro e a dica da área depois.
     */
    public @Nullable Component tooltip(double mx, double my) {
        return tooltips.tooltip(mx, my);
    }

    // ------------------------------------------------------------------ desenho

    void render(GuiGraphics g, int mouseX, int mouseY) {
        layout.arrangeForFrame();
        Font font = font();
        NodePanel p = chosenPanel.current();
        boolean covered = (p != null && p.popupOpen()) || search.suggestions.contains(mouseX, mouseY);
        int mx = covered ? -1 : mouseX;
        int my = covered ? -1 : mouseY;
        painter.renderSearch(g, font, mx, my);
        painter.renderQuick(g, font, mx, my);
        painter.renderBanner(g, font, mx, my);
        rows.render(g, font, mx, my);
        painter.renderBar(g, font, mx, my);
        g.fill(layout.x0 + layout.listW + 4, layout.y0, layout.x0 + layout.listW + 5, layout.bottom, GuiPaint.LINE);
        painter.renderPanel(g, font, mx, my);
    }

    /** Por cima de tudo: as sugestões, as listas abertas do painel e os avisos. */
    void renderOverlay(GuiGraphics g, int mouseX, int mouseY) {
        layout.arrangeForFrame();
        Font font = font();
        NodePanel p = chosenPanel.current();
        if (p != null) {
            p.renderOverlay(g, mouseX, mouseY);
        } else {
            painter.renderOwnNotice(g, font, mouseX, mouseY);
        }
        search.suggestions.render(g, font, mouseX, mouseY, texts::suggestRight);
    }

    // ------------------------------------------------------------------ ganchos do e2e e das capturas

    void previewMulti(boolean on, int count) {
        multi = on;
        selected.clear();
        if (on) {
            snapshot().nodes().stream().limit(count).forEach(node -> selected.add(node.key()));
        }
    }

    public void previewChoose(@Nullable NodeKey key) {
        chosen = key;
        revealKey = key;
    }

    public void previewText(String value) {
        search.previewText(value);
    }

    public void previewCollapse(ResourceLocation machine) {
        model.collapse(machine);
    }

    /** Rola a lista até o cabeçalho do grupo da máquina. */
    public void previewScrollTo(ResourceLocation machine) {
        layout.arrange();
        scroll = Math.max(0, model.tree().headerLine(machine));
    }

    /** Centro da linha do nó, ou {@code null} se ela não está na vista (grupo recolhido ou rolado). */
    int @Nullable [] nodeRowCenter(NodeKey key) {
        layout.arrange();
        Hot hot = layout.hot(Kind.ROW, model.indexOf(key));
        return hot == null ? null : new int[] {hot.x() + hot.w() / 2, hot.y() + hot.h() / 2};
    }

    /**
     * Centro da linha da árvore do nó {@code node} ({@code kind} e {@code id} como no {@link TreeNav.Key}; num
     * {@link ListTree.Kind#NODE}, a linha do nó), ou {@code null} fora da vista.
     */
    public int @Nullable [] treeLineCenter(NodeKey node, ListTree.Kind kind, int id) {
        return center(kind == ListTree.Kind.NODE ? Kind.ROW : Kind.TREE, kind == ListTree.Kind.NODE
                ? model.indexOf(node) : treeNav.lineOf(node, kind, id));
    }

    /** O ▸/▾ de um nó ({@link ListTree.Kind#NODE}) ou de um grupo da árvore. */
    public int @Nullable [] treeToggleCenter(NodeKey node, ListTree.Kind kind, int id) {
        return center(Kind.TREE_TOGGLE, treeNav.lineOf(node, kind, id));
    }

    /** O Olho da máquina {@code index} do nó. */
    public int @Nullable [] treeEyeCenter(NodeKey node, int index) {
        return center(Kind.TREE_EYE, treeNav.lineOf(node, ListTree.Kind.MACHINE, index));
    }

    /** O "Mostrar menos" do grupo {@code group} do nó. */
    public int @Nullable [] treeFewCenter(NodeKey node, int group) {
        return center(Kind.TREE_FEW, treeNav.lineOf(node, ListTree.Kind.GROUP, group));
    }

    /** Centro da sugestão do valor {@code id} (máquina, rede ou nome) na lista aberta, ou {@code null}. */
    public int @Nullable [] suggestionCenter(Object id) {
        layout.arrange();
        return search.suggestions.center(id);
    }

    /** Centro do ✕ da etiqueta {@code index}. */
    public int @Nullable [] labelCenter(int index) {
        layout.arrange();
        Hot hot = layout.hot(Kind.LABEL, index);
        return hot == null ? null : new int[] {hot.x() + hot.w() - 5, hot.y() + hot.h() / 2};
    }

    public int @Nullable [] quickCenter(QuickFilter filter) {
        return center(Kind.QUICK, filter.ordinal());
    }

    public int @Nullable [] typeChipCenter() {
        return center(Kind.TYPE_CHIP, 0);
    }

    /** O "Colar nas N" do grupo da máquina. */
    public int @Nullable [] pasteGroupCenter(ResourceLocation machine) {
        layout.arrange();
        int row = model.tree().headerLine(machine);
        Hot hot = row < 0 ? null : layout.hot(Kind.PASTE_GROUP, row);
        return hot == null ? null : hot.center();
    }

    int @Nullable [] groupHeaderCenter(ResourceLocation machine) {
        layout.arrange();
        int row = model.tree().headerLine(machine);
        Hot hot = row < 0 ? null : layout.hot(Kind.HEADER, row);
        return hot == null ? null : new int[] {hot.x() + 20, hot.y() + hot.h() / 2};
    }

    public int @Nullable [] copyCenter() {
        return center(Kind.COPY, 0);
    }

    int @Nullable [] pasteCenter() {
        return center(Kind.PASTE, 0);
    }

    int @Nullable [] fullScreenCenter() {
        return center(Kind.FULL_SCREEN, 0);
    }

    public int @Nullable [] forgetCenter() {
        return center(Kind.FORGET, 0);
    }

    int @Nullable [] clearCenter() {
        return center(Kind.CLEAR, 0);
    }

    /** O Desfazer do aviso: o do painel, ou o da faixa de aviso sem painel. */
    public int @Nullable [] undoCenter() {
        layout.arrange();
        NodePanel p = chosenPanel.current();
        if (p != null) {
            return p.undoCenter();
        }
        Hot hot = layout.hot(Kind.UNDO, 0);
        return hot == null ? null : hot.center();
    }

    /** Centro da área do tipo, depois de arranjar o quadro, ou {@code null}. */
    private int @Nullable [] center(Kind kind, int arg) {
        layout.arrange();
        Hot hot = layout.hot(kind, arg);
        return hot == null ? null : hot.center();
    }

    public boolean suggestionsOpen() {
        return search.suggestions.shown();
    }

    public List<Object> labelIds() {
        return search.labelIds();
    }
}
