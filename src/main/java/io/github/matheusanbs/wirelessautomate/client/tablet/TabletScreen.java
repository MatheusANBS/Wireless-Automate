package io.github.matheusanbs.wirelessautomate.client.tablet;

import static io.github.matheusanbs.wirelessautomate.client.tablet.TabletLayout.BODY_Y;
import static io.github.matheusanbs.wirelessautomate.client.tablet.TabletLayout.MIN_H;
import static io.github.matheusanbs.wirelessautomate.client.tablet.TabletLayout.MIN_W;
import static io.github.matheusanbs.wirelessautomate.client.tablet.TabletLayout.TAB_H;
import static io.github.matheusanbs.wirelessautomate.client.tablet.TabletLayout.TAB_Y;
import static io.github.matheusanbs.wirelessautomate.client.tablet.TabletLayout.X0;
import static io.github.matheusanbs.wirelessautomate.client.tablet.TabletText.tr;

import io.github.matheusanbs.wirelessautomate.client.FlatButton;
import io.github.matheusanbs.wirelessautomate.client.GuiPaint;
import io.github.matheusanbs.wirelessautomate.client.GuiText;
import io.github.matheusanbs.wirelessautomate.client.GuiThemes;
import io.github.matheusanbs.wirelessautomate.client.ResizeGrip;
import io.github.matheusanbs.wirelessautomate.client.ThemeToggle;
import io.github.matheusanbs.wirelessautomate.client.router.NodePanel;
import io.github.matheusanbs.wirelessautomate.menu.TabletMenu;
import io.github.matheusanbs.wirelessautomate.menu.TabletMenu.Action;
import io.github.matheusanbs.wirelessautomate.menu.TabletSnapshot;
import io.github.matheusanbs.wirelessautomate.network.NodeIndex.NodeKey;
import io.github.matheusanbs.wirelessautomate.packet.PanelNotices;
import io.github.matheusanbs.wirelessautomate.packet.TabletPanelPayload;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

/**
 * Tela do Tablet de rede (especificação, "Telas da interface › Tablet de rede"), com cinco abas:
 * <ul>
 *   <li><b>Lista:</b> a busca com sugestões e etiquetas, os filtros rápidos, os nós agrupados pela máquina e
 *       o painel do nó escolhido, com copiar e colar o perfil ({@link TabletList}); Selecionar para mover ou
 *       colar em vários nós ({@link SelectBar});
 *   <li><b>Mapa:</b> vista de cima centrada no jogador, pontos coloridos por status; clicar
 *       seleciona e mostra o nó, com Abrir ({@link MapTab});
 *   <li><b>Fluxos:</b> tempo do mod por tick e o que entra e sai de cada tipo, Por tipo ou Por rede, média
 *       dos últimos 5 s ({@link FlowsTab});
 *   <li><b>Redes:</b> lista à esquerda e a rede escolhida à direita (nome, cor, privacidade, usar
 *       como ativa, remover); nova rede fica recolhida ({@link NetworksTab});
 *   <li><b>Grupos:</b> o mesmo para grupos: pausar e retomar o grupo todo e marcar as redes dele
 *       ({@link GroupsTab}).
 * </ul>
 *
 * <p>Tudo vem de {@link TabletMenu#snapshot()}; a tela só pede (consulta, ações, pelo {@link TabletActions}) e
 * espera o servidor. A tela é a anfitriã: os botões das abas e os campos, e repassa às partes o tamanho
 * ({@link TabletResize}), o cabeçalho e o aviso ({@link TabletHeader}), o corpo da aba aberta ({@link TabletBody}:
 * desenho, cliques, roda e dicas) e a dica do quadro ({@link TabletTips}). Linhas de lista, pontos do mapa e cores são desenhados e clicados por
 * geometria, sem um widget por item; os botões fixos são {@link FlatButton}s que leem o estado a cada quadro.
 */
public class TabletScreen extends AbstractContainerScreen<TabletMenu> {
    public enum Tab { LIST, MAP, FLOWS, NETWORKS, GROUPS }

    /** Põe um botão fixo na tela; {@code owner} nulo: visível em todas as abas. */
    @FunctionalInterface
    interface Buttons {
        FlatButton add(@Nullable Tab owner, FlatButton button);
    }

    private final List<FlatButton> buttons = new ArrayList<>();
    private final Map<Tab, List<FlatButton>> tabWidgets = new EnumMap<>(Tab.class);
    private final TabletLayout layout = new TabletLayout();
    private final TabletActions actions;
    private final TabletList list;
    private final SelectBar selectBar;
    private final MapTab map;
    private final FlowsTab flows;
    private final NameFields names;
    private final NetworksTab networks;
    private final GroupsTab groups;
    private final TabletBody body;
    private final TabletTips tips;
    private final TabletHeader header;
    private final TabletResize resize = new TabletResize(layout);

    private Tab tab = Tab.LIST;
    private int lastVersion = -1;
    private EditBox searchBox;
    /** Versão do tema com que os campos de texto foram pintados ({@link GuiThemes#version()}). */
    private int inkVersion = -1;

    public TabletScreen(TabletMenu menu, Inventory inventory, Component title) {
        this(menu, inventory, title, false);
    }

    /** @param preview sem servidor (captura de desenvolvimento): nada é enviado */
    public TabletScreen(TabletMenu menu, Inventory inventory, Component title, boolean preview) {
        super(menu, inventory, title);
        this.imageWidth = MIN_W;
        this.imageHeight = MIN_H;
        this.header = new TabletHeader(layout, menu.snapshot().noticeId());
        if (menu.restoredTab() >= 0 && menu.restoredTab() < Tab.values().length) {
            this.tab = Tab.values()[menu.restoredTab()];
        }
        this.actions = new TabletActions(menu, preview);
        NodeText nodes = new NodeText(menu::snapshot);
        this.list = new TabletList(menu, actions, nodes, () -> layout.h);
        this.selectBar = new SelectBar(layout, menu::snapshot, nodes, actions, list);
        this.map = new MapTab(layout, menu::snapshot, nodes, actions, list);
        this.flows = new FlowsTab(layout, menu, (type, network) -> {
            switchTab(Tab.LIST);
            list.filterFlow(type, network);
        });
        SideList side = new SideList(layout);
        this.names = new NameFields(layout, actions, () -> networks().selected(), () -> groups().selected(),
                this::setFocused, this::refresh);
        this.networks = new NetworksTab(layout, menu::snapshot, actions, side, names);
        this.groups = new GroupsTab(layout, menu::snapshot, actions, side, names);
        this.body = new TabletBody(list, map, flows, side, networks, groups, () -> snapshot().networks().size());
        this.tips = new TabletTips(resize, buttons, body);
        // o aviso de outra tela agiria no alvo errado
        PanelNotices.clear();
    }

    private NetworksTab networks() {
        return networks;
    }

    private GroupsTab groups() {
        return groups;
    }

    private TabletSnapshot snapshot() {
        return menu.snapshot();
    }

    // ------------------------------------------------------------------ montagem

    @Override
    protected void init() {
        int w = TabletResize.width(width);
        int h = TabletResize.height(height);
        imageWidth = w;
        imageHeight = h;
        super.init();
        layout.place(leftPos, topPos, w, h);
        buttons.clear();
        tabWidgets.clear();
        for (Tab t : Tab.values()) {
            tabWidgets.put(t, new ArrayList<>());
        }
        int x = leftPos;
        int y = topPos;

        int tabX = x + X0;
        for (Tab t : Tab.values()) {
            Component label = tr("tab." + t.name().toLowerCase(Locale.ROOT));
            int width = font.width(label) + 12;
            add(null, new FlatButton(tabX, y + TAB_Y, width, TAB_H, label,
                    (g, b, hovered) -> TabletButtons.paintTab(g, b, hovered, t == tab), () -> switchTab(t)));
            tabX += width + 3;
        }

        // Lista: a busca (posição e largura acertadas pela lista a cada quadro) e os botões do Selecionar
        list.layout(x + X0, y + BODY_Y, layout.x1() - X0, y + h - 8);
        int lw = list.listWidth();
        searchBox = new EditBox(font, x + X0 + 4, y + BODY_Y + 3, lw - 8, 9, tr("search"));
        searchBox.setBordered(false);
        searchBox.setMaxLength(TabletSnapshot.MAX_TEXT);
        searchBox.setTextShadow(false); // tinta sobre porcelana, sem sombra
        inkVersion = -1;
        list.attach(searchBox);
        addRenderableWidget(searchBox);
        selectBar.addButtons(this::add, font);
        map.addButtons(this::add);
        flows.addButtons(this::add, font);
        networks.addButtons(this::add);
        groups.addButtons(this::add);

        names.createBoxes(font);
        addRenderableWidget(names.newBox());
        addRenderableWidget(names.renameBox());
        refresh();
        if (tab == Tab.LIST) {
            focusSearch();
        }
    }

    /** {@code owner} nulo: visível em todas as abas. */
    private FlatButton add(@Nullable Tab owner, FlatButton button) {
        buttons.add(button);
        if (owner != null) {
            tabWidgets.get(owner).add(button);
        }
        return addRenderableWidget(button);
    }

    /** Visibilidade e estado que dependem da aba e do snapshot. */
    private void refresh() {
        if (menu.version() != lastVersion) {
            lastVersion = menu.version();
            onSnapshot();
        }
        for (Map.Entry<Tab, List<FlatButton>> entry : tabWidgets.entrySet()) {
            for (FlatButton button : entry.getValue()) {
                button.visible = entry.getKey() == tab;
            }
        }
        searchBox.visible = tab == Tab.LIST;
        selectBar.refresh(tab == Tab.LIST);
        map.refresh(tab == Tab.MAP);
        networks.refresh(tab == Tab.NETWORKS);
        groups.refresh(tab == Tab.GROUPS);
        names.refresh(tab == Tab.NETWORKS || tab == Tab.GROUPS);
    }

    /** Snapshot novo: a seleção que sumiu sai e o aviso aparece. */
    private void onSnapshot() {
        TabletSnapshot s = snapshot();
        header.onSnapshot(s);
        networks.onSnapshot(s);
        groups.onSnapshot(s);
        selectBar.onSnapshot(s);
        list.onSnapshot();
    }

    // ------------------------------------------------------------------ abas

    private void switchTab(Tab t) {
        if (tab != t) {
            list.cancelLabel();
            tab = t;
            actions.tab(t.ordinal());
            if (t == Tab.FLOWS) {
                flows.shown();
            }
            names.reset();
            setFocused(null);
            if (t == Tab.LIST) {
                focusSearch();
            }
        }
    }

    /** A busca com o cursor: digitar na Lista procura. */
    private void focusSearch() {
        if (searchBox != null) {
            searchBox.visible = true;
            setFocused(searchBox);
        }
    }

    /** O último painel do nó escolhido que o servidor mandou (snapshot, acesso e distância), ou {@code null}. */
    public @Nullable TabletPanelPayload panel() {
        return menu.panel();
    }

    /** Escolhe o nó do painel no servidor ({@code null}: nenhum); o painel volta num {@link TabletPanelPayload}. */
    public void chooseNode(@Nullable NodeKey key) {
        list.choose(key);
    }

    /** A aba Lista (busca, nós e o painel), para os ganchos do e2e e das capturas. */
    public TabletList list() {
        return list;
    }

    /** O painel do nó escolhido na Lista, ou {@code null}. */
    public @Nullable NodePanel nodePanel() {
        return list.panel();
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        list.tick();
    }

    @Override
    public void removed() {
        PanelNotices.clear();
        super.removed();
    }

    // ------------------------------------------------------------------ geometria

    public int themeX() {
        return header.themeX();
    }

    public int themeY() {
        return header.themeY();
    }

    // ------------------------------------------------------------------ entrada

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (ThemeToggle.mouseClicked(themeX(), themeY(), mouseX, mouseY, button)) {
            return true;
        }
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && resize.begin(mouseX, mouseY)) {
            return true;
        }
        // um snapshot pode ter chegado depois do último quadro: botões ativos e visíveis em dia
        refresh();
        if (names.renaming() && !names.renameBox().isMouseOver(mouseX, mouseY)) {
            names.finishRename(true);
        }
        if (names.removeArmed() && !overRemoveButton(mouseX, mouseY)) {
            names.disarm();
        }
        if (tab == Tab.LIST && list.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && body.mouseClicked(tab, mouseX, mouseY)) {
            return true;
        }
        boolean wasOpen = names.newOpen();
        boolean handled = super.mouseClicked(mouseX, mouseY, button);
        if (names.newOpen() && !wasOpen) {
            // a tela dá o foco ao botão clicado depois do onPress, tirando-o do campo
            setFocused(names.newBox());
        }
        return handled;
    }

    private boolean overRemoveButton(double mouseX, double mouseY) {
        FlatButton remove = tab == Tab.NETWORKS ? networks.removeButton()
                : tab == Tab.GROUPS ? groups.removeButton() : null;
        return remove != null && remove.visible && remove.isMouseOver(mouseX, mouseY);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (resize.dragging()) {
            if (resize.drag(mouseX, mouseY, width, height)) {
                rebuild();
            }
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (resize.dragging()) {
            resize.end();
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    /**
     * Refaz os widgets no tamanho lembrado: aba, filtros, seleção e rolagens ficam em campos e
     * sobrevivem; o texto da nova rede ou grupo volta ao campo; o nome em edição é fechado sem gravar.
     */
    private void rebuild() {
        EditBox newBox = names.newBox();
        String newText = newBox == null ? "" : newBox.getValue();
        names.clearRename();
        setFocused(null);
        rebuildWidgets();
        names.newBox().setValue(newText);
    }

    @Override
    public void resize(Minecraft minecraft, int width, int height) {
        resize.end();
        super.resize(minecraft, width, height);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        body.mouseScrolled(tab, mouseX, mouseY, scrollY);
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (tab == Tab.LIST) {
            if (list.keyPressed(keyCode, scanCode, modifiers)) {
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                // sem busca nem filtro, Esc fecha o Tablet
                return super.keyPressed(keyCode, scanCode, modifiers);
            }
            if (getFocused() != searchBox) {
                focusSearch();
            }
        }
        if (getFocused() instanceof EditBox box && box.isFocused()) {
            boolean enter = keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER;
            if (box == names.renameBox() && (enter || keyCode == GLFW.GLFW_KEY_ESCAPE)) {
                names.finishRename(enter);
            } else if (box == names.newBox() && enter) {
                names.create(tab == Tab.GROUPS ? Action.GROUP_CREATE : Action.NETWORK_CREATE);
            } else if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                setFocused(null);
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
        if (tab == Tab.LIST && list.charTyped(codePoint, modifiers)) {
            return true;
        }
        if (tab == Tab.LIST && getFocused() != searchBox) {
            focusSearch();
        }
        if (getFocused() instanceof EditBox box && box.isFocused()) {
            return box.charTyped(codePoint, modifiers);
        }
        return super.charTyped(codePoint, modifiers);
    }

    // ------------------------------------------------------------------ desenho

    /** Cor do texto e da dica dos campos: o EditBox guarda a cor, então segue o tema por aqui. */
    private void inkBoxes() {
        inkVersion = GuiThemes.version();
        // a dica da busca é desenhada pela lista, que a corta e a mostra também com o cursor na caixa
        GuiPaint.ink(searchBox);
        GuiPaint.ink(names.newBox());
        GuiPaint.ink(names.renameBox());
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        if (inkVersion != GuiThemes.version()) {
            inkBoxes();
        }
        refresh();
        GuiText.beginFrame();
        boolean popup = tab == Tab.LIST && list.panel() != null && list.panel().popupOpen();
        super.render(g, popup ? -1 : mouseX, popup ? -1 : mouseY, partialTick);
        if (tab == Tab.LIST) {
            list.renderOverlay(g, mouseX, mouseY);
        }
        header.renderNotice(g, font);
        if (popup) {
            Component clipped = GuiText.clipAt(mouseX, mouseY);
            if (clipped != null) {
                setTooltipForNextRenderPass(clipped);
            }
            return;
        }
        renderHoverTip(g, mouseX, mouseY);
    }

    /** A dica do quadro: o botão do tema, ou a do {@link TabletTips}. */
    private void renderHoverTip(GuiGraphics g, int mouseX, int mouseY) {
        if (ThemeToggle.hovered(themeX(), themeY(), mouseX, mouseY)) {
            g.renderComponentTooltip(font, ThemeToggle.tooltip(), mouseX, mouseY);
            return;
        }
        Component tooltip = tips.at(tab, mouseX, mouseY);
        if (tooltip != null) {
            setTooltipForNextRenderPass(tooltip);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        // os textos são desenhados em renderBg, em coordenadas da tela
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        TabletSnapshot s = snapshot();
        int x = leftPos;
        int y = topPos;
        int w = layout.w;
        int h = layout.h;
        GuiPaint.panel(g, x, y, w, h, GuiPaint.ACCENT);
        header.render(g, font, s, mouseX, mouseY);
        body.render(tab, g, font, mouseX, mouseY);
        // alça de redimensionar: três riscos na diagonal, acesos com o mouse em cima ou arrastando
        ResizeGrip.renderDotted(g, x, y, w, h, resize.hover(mouseX, mouseY), GuiPaint.ACCENT);
    }

    // ------------------------------------------------------------------ captura e teste de ponta a ponta

    public void previewTab(Tab t) {
        switchTab(t);
    }

    public void previewMulti(boolean on, int count) {
        list.previewMulti(on, count);
    }

    public void previewMapSelect(int index) {
        map.previewSelect(index);
    }

    /** Centro da máquina {@code machine} do roteador escolhido no Mapa, ou {@code null} (gancho do e2e). */
    public int @Nullable [] mapMachineCenter(int machine) {
        return map.machineCenter(machine);
    }

    public void previewNetwork(int index) {
        networks.preview(index);
    }

    public void previewNewOpen(String text) {
        names.previewOpen(text);
    }

    Tab tab() {
        return tab;
    }

    /** e2e: muda o tamanho como se a borda tivesse sido arrastada. */
    public void previewResize(int width, int height) {
        TabletResize.remember(width, height);
        rebuild();
    }

    /** Tamanho atual do painel, {@code {largura, altura}} (gancho do e2e). */
    public int[] size() {
        return new int[] {layout.w, layout.h};
    }

    /** A aba Fluxos, para os ganchos do e2e e das capturas. */
    public FlowsTab flows() {
        return flows;
    }

    public Tab currentTab() {
        return tab;
    }

    /** Centro da linha do nó na lista, ou {@code null} se ele não aparece (grupo recolhido ou rolado). */
    public int @Nullable [] nodeRowCenter(NodeKey key) {
        return list.nodeRowCenter(key);
    }

    /** Centro da linha do grupo na lista lateral, ou {@code null}. */
    int @Nullable [] groupRowCenter(UUID id) {
        return groups.groupRowCenter(id);
    }

    /** Centro da caixa de marcar da rede na coluna do grupo, ou {@code null}. */
    public int @Nullable [] groupNetworkCenter(UUID id) {
        return groups.groupNetworkCenter(id);
    }

    @Nullable EditBox newBox() {
        return names.newBox();
    }

    public EditBox searchBox() {
        return searchBox;
    }
}
