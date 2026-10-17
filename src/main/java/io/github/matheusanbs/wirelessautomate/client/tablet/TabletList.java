package io.github.matheusanbs.wirelessautomate.client.tablet;

import static io.github.matheusanbs.wirelessautomate.client.tablet.TabletText.tr;

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
import io.github.matheusanbs.wirelessautomate.packet.TabletProfilePayload;
import io.github.matheusanbs.wirelessautomate.packet.TabletProfilePayload.Action;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.IntSupplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

/**
 * A aba Lista do Tablet (especificação, "Lista do Tablet"): à esquerda a busca com sugestões e etiquetas, os
 * filtros rápidos, a faixa do perfil copiado e os nós agrupados pela máquina ({@link ListGroups}); à direita o
 * painel do nó escolhido ({@link NodePanel}), com Tela completa, Copiar perfil e Colar perfil. Tudo desenhado e
 * respondido por geometria, como a {@link TabletScreen} que a hospeda; os botões fixos do modo Selecionar
 * ficam na tela ({@link SelectBar}).
 *
 * <p>Esta classe é a anfitriã da aba: guarda a lista (grupos recolhidos, rolagem, nó escolhido, seleção), repassa
 * o desenho, o mouse, o teclado e as dicas para as partes ({@link ListSearch}, {@link ChosenPanel},
 * {@link ListLayout}, {@link ListPainter}, {@link ListRows}, {@link ListKeys}, {@link ListTooltips}) e manda as
 * ações do perfil.
 */
public final class TabletList {
    private static final long DOUBLE_CLICK_MS = 400;

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

    // lista
    final Set<ResourceLocation> collapsed = new HashSet<>();
    int scroll;
    @Nullable NodeKey chosen;
    /** Nó a pôr na vista no próximo arranjo (o restaurado ao abrir, o escolhido pelas setas). */
    @Nullable NodeKey revealKey;
    private @Nullable NodeKey lastClicked;
    private long lastClickAt;
    boolean multi;
    final Set<NodeKey> selected = new LinkedHashSet<>();
    ResourceType chipType = LoadedTypes.LIST.isEmpty() ? ResourceType.ITEM : LoadedTypes.LIST.getFirst();
    private @Nullable ListGroups<ResourceLocation> groups;
    private @Nullable List<NodeView> groupsFor;
    private Set<ResourceLocation> groupsCollapsed = Set.of();

    TabletList(TabletMenu menu, TabletActions actions, NodeText nodes, IntSupplier screenHeight) {
        this.menu = menu;
        this.actions = actions;
        this.nodes = nodes;
        this.screenHeight = screenHeight;
        Query query = menu.snapshot().query();
        this.search = new ListSearch(this, query);
        search.typeFilter.ifPresent(t -> chipType = t);
        this.chosen = menu.restoredNode();
        this.revealKey = chosen;
        this.chosenPanel = new ChosenPanel(this, menu, chosen);
        this.texts = new ListTexts(this);
        this.layout = new ListLayout(this);
        this.painter = new ListPainter(this, layout);
        this.rows = new ListRows(this, layout);
        this.keys = new ListKeys(this);
        this.tooltips = new ListTooltips(this, layout);
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

    ListGroups<ResourceLocation> groups() {
        List<NodeView> nodeViews = snapshot().nodes();
        if (groups == null || groupsFor != nodeViews || !groupsCollapsed.equals(collapsed)) {
            groups = new ListGroups<>(nodeViews.stream().map(NodeView::machine).toList(), Set.copyOf(collapsed));
            groupsFor = nodeViews;
            groupsCollapsed = Set.copyOf(collapsed);
        }
        return groups;
    }

    int indexOf(@Nullable NodeKey key) {
        List<NodeView> nodeViews = snapshot().nodes();
        for (int i = 0; i < nodeViews.size(); i++) {
            if (nodeViews.get(i).key().equals(key)) {
                return i;
            }
        }
        return -1;
    }

    @Nullable NodeView node(@Nullable NodeKey key) {
        int index = indexOf(key);
        return index < 0 ? null : snapshot().nodes().get(index);
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

    /** Só os nós com o tipo (o clique num cartão das Estatísticas), e o painel nessa aba. */
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

    private void profileAction(Action action, @Nullable NodeKey key, @Nullable ResourceLocation machine,
            List<NodeKey> keys) {
        actions.profile(action, key, machine, keys);
    }

    void copyProfile() {
        if (chosen != null && chosenPanel.canEdit()) {
            profileAction(Action.COPY, chosen, null, List.of());
        }
    }

    void pasteProfile() {
        if (chosen != null && profile().isPresent() && chosenPanel.canEdit()) {
            profileAction(Action.PASTE_ONE, chosen, null, List.of());
        }
    }

    void undo() {
        profileAction(Action.UNDO, null, null, List.of());
    }

    private void forgetProfile() {
        profileAction(Action.FORGET, null, null, List.of());
    }

    void pasteSelected() {
        if (profile().isPresent() && !selected.isEmpty()) {
            List<NodeKey> keys = new ArrayList<>(selected);
            profileAction(Action.PASTE_SELECTED, null, null,
                    List.copyOf(keys.size() > TabletProfilePayload.MAX_KEYS ? keys.subList(0, TabletProfilePayload.MAX_KEYS)
                            : keys));
        }
    }

    /** Quantos nós do grupo o "Colar nas N" atinge: os da consulta, menos a origem do perfil. */
    int pasteCount(ResourceLocation machine) {
        ProfileSummary summary = profile().orElse(null);
        if (summary == null) {
            return 0;
        }
        TabletSnapshot s = snapshot();
        int count = s.groupCount(machine);
        if (s.originGroup().equals(Optional.of(machine))) {
            count--;
        }
        return Math.min(count, TabletMenu.MAX_MOVE);
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
        layout.arrange();
        NodePanel p = chosenPanel.current();
        if (p != null && p.popupOpen()) {
            return p.mouseClicked(mx, my, button);
        }
        SearchSuggestions suggestions = search.suggestions;
        if (suggestions.contains(mx, my)) {
            if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                suggestions.click(mx, my, search.picker);
            }
            return true;
        }
        EditBox searchBox = search.box();
        if (suggestions.shown() && !(searchBox != null && searchBox.isMouseOver(mx, my))) {
            suggestions.close();
        }
        if (p != null && mx >= layout.panelX && p.mouseClicked(mx, my, button)) {
            return true;
        }
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            return false;
        }
        Hot hot = layout.hotAt(mx, my);
        if (hot == null) {
            return clickEmpty(mx, my);
        }
        clickHot(hot);
        return true;
    }

    private void clickHot(Hot hot) {
        List<NodeView> nodeViews = snapshot().nodes();
        switch (hot.kind()) {
            case LABEL -> search.removeLabel(hot.arg());
            case QUICK -> search.toggleQuick(QuickFilter.values()[hot.arg()]);
            case TYPE_CHIP -> search.clearType();
            case CLEAR -> clearAll();
            case FORGET -> forgetProfile();
            case HEADER -> {
                ResourceLocation group = groups().rows().get(hot.arg()).group();
                if (!collapsed.remove(group)) {
                    collapsed.add(group);
                }
            }
            case PASTE_GROUP -> profileAction(Action.PASTE_GROUP, null, groups().rows().get(hot.arg()).group(), List.of());
            case ROW -> clickRow(nodeViews.get(hot.arg()).key());
            case FULL_SCREEN -> {
                if (chosen != null) {
                    actions.openNode(chosen);
                }
            }
            case COPY -> copyProfile();
            case PASTE -> pasteProfile();
            case UNDO -> {
                PanelNotices.clear();
                undo();
            }
        }
    }

    /** O "Limpar a busca" da lista vazia. */
    private boolean clickEmpty(double mx, double my) {
        int[] clear = layout.emptyClearCenter();
        if (clear != null && Math.abs(mx - clear[0]) <= font().width(tr("list.clear_search")) / 2 + 2
                && Math.abs(my - clear[1]) <= 6) {
            clearAll();
            return true;
        }
        return mx >= layout.x0 && mx < layout.x0 + layout.listW && my >= layout.rowsTop && my < layout.rowsBottom;
    }

    private void clickRow(NodeKey key) {
        long now = System.currentTimeMillis();
        boolean twice = key.equals(lastClicked) && now - lastClickAt <= DOUBLE_CLICK_MS;
        lastClicked = key;
        lastClickAt = now;
        if (multi) {
            if (!selected.remove(key)) {
                selected.add(key);
            }
            return;
        }
        if (twice) {
            lastClicked = null;
            actions.openNode(key);
            return;
        }
        choose(key);
    }

    boolean mouseScrolled(double mx, double my, double scrollY) {
        NodePanel p = chosenPanel.current();
        if (p != null && p.mouseScrolled(mx, my, scrollY)) {
            return true;
        }
        if (mx < layout.x0 + layout.listW) {
            scroll = Math.max(0, Math.min(layout.maxScroll(), scroll - (int) Math.signum(scrollY)));
        }
        return true;
    }

    /** O teclado da Lista (especificação, "Teclado"); {@code false} deixa a tecla para a busca ou a tela. */
    boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return keys.keyPressed(keyCode, scanCode, modifiers);
    }

    /**
     * A dica do ponto, ou {@code null}. Um texto cortado sob o mouse vem inteiro primeiro e a dica da área depois.
     */
    public @Nullable Component tooltip(double mx, double my) {
        return tooltips.tooltip(mx, my);
    }

    // ------------------------------------------------------------------ desenho

    void render(GuiGraphics g, int mouseX, int mouseY) {
        layout.arrange();
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
        layout.arrange();
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
        collapsed.add(machine);
    }

    /** Rola a lista até o cabeçalho do grupo da máquina. */
    public void previewScrollTo(ResourceLocation machine) {
        layout.arrange();
        scroll = Math.max(0, groups().headerRow(machine));
    }

    /** Centro da linha do nó, ou {@code null} se ela não está na vista (grupo recolhido ou rolado). */
    int @Nullable [] nodeRowCenter(NodeKey key) {
        layout.arrange();
        Hot hot = layout.hot(Kind.ROW, indexOf(key));
        return hot == null ? null : new int[] {hot.x() + hot.w() / 2, hot.y() + hot.h() / 2};
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
        int row = groups().headerRow(machine);
        Hot hot = row < 0 ? null : layout.hot(Kind.PASTE_GROUP, row);
        return hot == null ? null : hot.center();
    }

    int @Nullable [] groupHeaderCenter(ResourceLocation machine) {
        layout.arrange();
        int row = groups().headerRow(machine);
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
