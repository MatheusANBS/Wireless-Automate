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
 * {@link TreeClicks} (o mouse) e do {@link TreeNav} (a escolha e o teclado); as da visão por máquinas, do
 * {@link MachineRows} (o desenho) e do {@link MachineNav} (o mouse, a escolha e o teclado), com o seletor no
 * {@link ViewBar}; as ações do perfil, do {@link ListProfileActions}.
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
    final MachineRows machineRows;
    final GridRows gridRows;
    final MachineNav machineNav;
    final ListProfileActions profileActions;
    final ListRename rename;

    // lista
    int scroll;
    @Nullable NodeKey chosen;
    /** Nó a pôr na vista no próximo arranjo (o restaurado ao abrir, o escolhido pelas setas). */
    @Nullable NodeKey revealKey;
    boolean multi;
    /** O que o Selecionar marcou; {@link #selected} são os roteadores. */
    final ListMarks marks = new ListMarks();
    final Set<NodeKey> selected = marks.nodes();
    final MarkRows markRows;
    ResourceType chipType = LoadedTypes.LIST.isEmpty() ? ResourceType.ITEM : LoadedTypes.LIST.getFirst();

    TabletList(TabletMenu menu, TabletActions actions, NodeText nodes, IntSupplier screenHeight) {
        this.menu = menu;
        this.actions = actions;
        this.nodes = nodes;
        this.screenHeight = screenHeight;
        this.model = new ListModel(menu::snapshot);
        this.profileActions = new ListProfileActions(this);
        this.markRows = new MarkRows(this);
        this.rename = new ListRename(this);
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
        this.machineRows = new MachineRows(this);
        this.gridRows = new GridRows(this);
        this.machineNav = new MachineNav(this);
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

    /**
     * A Grade está escolhida. Só do cliente (a página do servidor é a dos roteadores); estática de propósito, como o
     * tamanho das telas: a escolha vale para a sessão e a próxima Lista já abre nela.
     */
    private static boolean grid;

    /** A Lista mostra a Grade: escolhida e a página dos roteadores (a por máquinas pede outra). */
    boolean gridView() {
        return grid && !machineView();
    }

    void setGrid(boolean on) {
        grid = on;
    }

    /** A Lista está na visão por máquinas (a página do servidor). */
    boolean machineView() {
        return snapshot().machineRows().active();
    }

    /** A lista não tem linhas na visão aberta. */
    boolean listEmpty() {
        return machineView() ? snapshot().machineRows().rows().isEmpty() : snapshot().nodes().isEmpty();
    }

    /** Snapshot novo: as sugestões, a visão pedida que chegou e a seleção que sumiu. */
    void onSnapshot() {
        search.onSnapshot();
        layout.viewBar().onSnapshot();
        if (machineView()) {
            // a página por máquinas não traz nós: os marcados ficam
            return;
        }
        Set<NodeKey> present = new HashSet<>();
        snapshot().nodes().forEach(n -> present.add(n.key()));
        marks.retainNodes(present);
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
     * Escolhe o nó do painel (no servidor também). O aviso de tirar a rede das faces sem modo
     * sai: o Desfazer age no nó escolhido, e ele passaria a ser outro.
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
            marks.clear();
        }
    }

    /** Todos: marca o que a visão aberta mostra (os nós, ou os blocos na visão por máquinas); se já estavam, desmarca. */
    void selectAll() {
        markRows.selectAll();
    }

    // ------------------------------------------------------------------ geometria

    /** O retângulo da aba (de cima da busca até o fim da tela). */
    void layout(int x, int y, int w, int bottomY) {
        layout.layout(x, y, w, bottomY);
    }

    /** Posições e áreas de clique refeitas agora (os ganchos). */
    void arrangeNow() {
        layout.arrange();
    }

    /** A área do tipo no quadro arranjado, ou {@code null}. */
    @Nullable Hot layoutHot(Kind kind, int arg) {
        return layout.hot(kind, arg);
    }

    int listWidth() {
        return layout.listW;
    }

    /** A largura da lista com o painel ao lado (a Grade fica com a aba toda): a dos botões da barra de baixo. */
    int splitListWidth() {
        return layout.splitListW;
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
        rename.editor().finish(false);
        NodePanel p = chosenPanel.current();
        if (p != null) {
            p.cancelLabel();
        }
    }

    /** Uma letra para a caixa do nome aberta no painel; {@code false} deixa a letra para a busca. */
    boolean charTyped(char codePoint, int modifiers) {
        if (rename.editor().charTyped(codePoint, modifiers)) {
            return true;
        }
        NodePanel p = chosenPanel.current();
        return p != null && p.charTyped(codePoint, modifiers);
    }

    /**
     * A dica do ponto, ou {@code null}. Um texto cortado sob o mouse vem inteiro primeiro e a dica da área depois.
     */
    public @Nullable Component tooltip(double mx, double my) {
        Component renaming = rename.tooltip(mx, my);
        return renaming != null ? renaming : tooltips.tooltip(mx, my);
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
        rename.beginFrame();
        rows.render(g, font, mx, my);
        rename.endFrame(g, mx, my);
        painter.renderBar(g, font, mx, my);
        if (!gridView()) {
            g.fill(layout.x0 + layout.listW + 4, layout.y0, layout.x0 + layout.listW + 5, layout.bottom, GuiPaint.LINE);
        }
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

    void previewGrid(boolean on) {
        grid = on;
    }

    void previewMulti(boolean on, int count) {
        multi = on;
        marks.clear();
        if (on) {
            marks.addNodes(snapshot().nodes().stream().limit(count).map(NodeView::key).toList());
        }
    }

    public void previewChoose(@Nullable NodeKey key) {
        chosen = key;
        revealKey = key;
    }

    /** A caixa do nome da Lista está aberta (gancho do e2e). */
    public boolean renaming() {
        return rename.editor().editing();
    }

    /** A visão por máquinas: o mouse, o teclado e os ganchos do e2e. */
    public MachineNav machines() {
        return machineNav;
    }

    public void previewText(String value) {
        search.previewText(value);
    }

    /** Centro da linha do nó, ou {@code null} se ela não está na vista (rolada para fora). */
    int @Nullable [] nodeRowCenter(NodeKey key) {
        layout.arrange();
        Hot hot = layout.hot(Kind.ROW, model.indexOf(key));
        return hot == null ? null : new int[] {hot.x() + hot.w() / 2, hot.y() + hot.h() / 2};
    }

    /** As áreas da árvore que os ganchos acham: a linha, o ▸/▾ (de um nó ou de um grupo), o Olho e o "Mostrar menos". */
    public enum TreePart { LINE, TOGGLE, EYE, FEW }

    /**
     * Centro da área {@code part} da linha do nó {@code node} ({@link ListTree.Kind#NODE}) ou de uma linha da árvore
     * dele ({@code kind} e {@code id} como no {@link TreeNav.Key}), ou {@code null} fora da vista.
     */
    public int @Nullable [] treeCenter(TreePart part, NodeKey node, ListTree.Kind kind, int id) {
        int line = treeNav.lineOf(node, kind, id);
        return switch (part) {
            case LINE -> kind == ListTree.Kind.NODE ? center(Kind.ROW, model.indexOf(node)) : center(Kind.TREE, line);
            case TOGGLE -> center(Kind.TREE_TOGGLE, line);
            case EYE -> center(Kind.TREE_EYE, line);
            case FEW -> center(Kind.TREE_FEW, line);
        };
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
