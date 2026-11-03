package io.github.matheusanbs.wirelessautomate.client.tablet;

import static io.github.matheusanbs.wirelessautomate.client.tablet.TabletLayout.BODY_Y;
import static io.github.matheusanbs.wirelessautomate.client.tablet.TabletLayout.MIN_H;
import static io.github.matheusanbs.wirelessautomate.client.tablet.TabletLayout.MIN_W;
import static io.github.matheusanbs.wirelessautomate.client.tablet.TabletLayout.X0;
import static io.github.matheusanbs.wirelessautomate.client.tablet.TabletText.tr;

import io.github.matheusanbs.wirelessautomate.client.FlatButton;
import io.github.matheusanbs.wirelessautomate.client.GuiPaint;
import io.github.matheusanbs.wirelessautomate.client.GuiText;
import io.github.matheusanbs.wirelessautomate.client.ResizeGrip;
import io.github.matheusanbs.wirelessautomate.client.ThemeToggle;
import io.github.matheusanbs.wirelessautomate.client.access.AccessPanel;
import io.github.matheusanbs.wirelessautomate.client.router.NodePanel;
import io.github.matheusanbs.wirelessautomate.client.tablet.kit.ModalLayer;
import io.github.matheusanbs.wirelessautomate.client.tablet.networks.NetworksPanel;
import io.github.matheusanbs.wirelessautomate.menu.TabletMenu;
import io.github.matheusanbs.wirelessautomate.menu.TabletSnapshot;
import io.github.matheusanbs.wirelessautomate.network.NodeIndex.NodeKey;
import io.github.matheusanbs.wirelessautomate.packet.PanelNotices;
import io.github.matheusanbs.wirelessautomate.packet.TabletPanelPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

/**
 * Tela do Tablet de rede (especificação, "Telas da interface › Tablet de rede"), com três abas na barra, Redes,
 * Máquina (por ora, a Lista na visão Máquinas) e Fluxos; o Mapa continua na tela só para as capturas e o e2e até a
 * etapa 4:
 * <ul>
 *   <li><b>Lista:</b> a busca com sugestões e etiquetas, o seletor Roteadores · Máquinas, os filtros rápidos, os
 *       nós agrupados pela máquina (ou, por máquinas, as máquinas pelo tipo de bloco), cada
 *       roteador com máquinas vinculadas aberto em árvore (a presa, os grupos e as máquinas com estado e vazão), e
 *       o painel do nó escolhido, com copiar e colar o perfil ({@link TabletList}); Selecionar para mover ou
 *       colar em vários nós ({@link SelectBar});
 *   <li><b>Fluxos:</b> tempo do mod por tick e o que entra e sai de cada tipo, Por tipo ou Por rede, média
 *       dos últimos 5 s ({@link FlowsTab});
 *   <li><b>Mapa:</b> vista de cima centrada no jogador, pontos coloridos por status; clicar escolhe o nó, o mesmo
 *       nó escolhido da Lista, e mostra as máquinas dele, com Abrir ({@link MapTab});
 *   <li><b>Redes:</b> os grupos com as redes deles à esquerda e, à direita, a rede escolhida (grupo, acesso e o
 *       que há nela) ou o grupo, com "+ Rede" e "+ Grupo" embaixo da coluna ({@link NetworksPanel}).
 * </ul>
 *
 * <p>Tudo vem de {@link TabletMenu#snapshot()}; a tela só pede (consulta, ações, pelo {@link TabletActions}) e
 * espera o servidor. A tela é a anfitriã: os botões das abas e os campos, e repassa às partes o tamanho
 * ({@link TabletResize}), a moldura, as abas e o aviso ({@link TabletFrame}), o corpo da aba aberta ({@link TabletBody}:
 * desenho, cliques, roda e dicas), os botões de cada aba ({@link TabletTabs}), o teclado ({@link TabletKeys}) e a
 * dica do quadro ({@link TabletTips}). Linhas de lista, pontos do mapa e cores
 * são desenhados e clicados por geometria, sem um widget por item; os botões fixos são {@link FlatButton}s que leem o
 * estado a cada quadro.
 */
public class TabletScreen extends AbstractContainerScreen<TabletMenu> {
    public enum Tab { LIST, FLOWS, MAP, NETWORKS }

    /** Põe um botão fixo na tela; {@code owner} nulo: visível em todas as abas. */
    @FunctionalInterface
    public interface Buttons {
        FlatButton add(@Nullable Tab owner, FlatButton button);
    }

    private final TabletLayout layout = new TabletLayout();
    private final TabletActions actions;
    private final TabletList list;
    private final SelectBar selectBar;
    private final MapTab map;
    private final FlowsTab flows;
    private final NetworksPanel networks;
    private final TabletBody body;
    private final TabletTabs tabs;
    private final TabletKeys keys;
    private final TabletTips tips;
    private final TabletFrame frame;
    private final TabletResize resize = new TabletResize(layout);

    private Tab tab = Tab.LIST;
    private int lastVersion = -1;
    private int lastHeader = -1;
    private EditBox searchBox;

    public TabletScreen(TabletMenu menu, Inventory inventory, Component title) {
        this(menu, inventory, title, false);
    }

    /** @param preview sem servidor (captura de desenvolvimento): nada é enviado */
    public TabletScreen(TabletMenu menu, Inventory inventory, Component title, boolean preview) {
        super(menu, inventory, title);
        this.imageWidth = MIN_W;
        this.imageHeight = MIN_H;
        this.frame = new TabletFrame(layout, menu.snapshot().noticeId());
        if (menu.restoredTab() >= 0 && menu.restoredTab() < Tab.values().length) {
            // o Mapa não está na barra
            this.tab = menu.restoredTab() == Tab.MAP.ordinal() ? Tab.NETWORKS : Tab.values()[menu.restoredTab()];
        }
        this.actions = new TabletActions(menu, preview);
        NodeText nodes = new NodeText(menu::snapshot);
        this.list = new TabletList(menu, actions, nodes, () -> layout.h);
        if (tab == Tab.LIST && menu.restoredNode() == null) {
            // aberto na aba Máquina; com um nó lembrado (a volta do filtro, o Abrir no Tablet) fica a visão de antes
            list.showMachines();
        }
        this.selectBar = new SelectBar(layout, menu::snapshot, nodes, actions, list);
        this.map = new MapTab(layout, menu::snapshot, menu::mapPoints, nodes, actions, list);
        this.flows = new FlowsTab(layout, menu, (type, network) -> {
            switchTab(Tab.LIST);
            list.filterFlow(type, network);
        });
        this.networks = new NetworksPanel(layout, menu, menu::snapshot, actions);
        this.body = new TabletBody(list, map, flows, networks);
        this.tabs = new TabletTabs(selectBar, map, networks);
        this.keys = new TabletKeys(this, list, networks, () -> tab);
        this.tips = new TabletTips(resize, tabs.buttons(), body);
        // o aviso de outra tela agiria no alvo errado
        PanelNotices.clear();
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
        tabs.clear();
        int x = leftPos;
        int y = topPos;

        frame.addTabs(this::add, font, () -> tab, t -> {
            boolean machines = tab != t && t == Tab.LIST; // entrar na aba Máquina pede a visão por máquinas
            switchTab(t);
            if (machines) {
                list.showMachines();
            }
        });

        // Lista: a busca (posição e largura acertadas pela lista a cada quadro) e os botões do Selecionar
        list.layout(x + X0, y + BODY_Y, layout.x1() - X0, y + h - 8);
        int lw = list.listWidth();
        searchBox = new EditBox(font, x + X0 + 4, y + BODY_Y + 3, lw - 8, 9, tr("search"));
        searchBox.setBordered(false);
        searchBox.setMaxLength(TabletSnapshot.MAX_TEXT);
        searchBox.setTextShadow(false); // tinta sobre porcelana, sem sombra
        keys.searchBox(searchBox);
        list.attach(searchBox);
        addRenderableWidget(searchBox);
        selectBar.addButtons(this::add, font);
        map.addButtons(this::add);
        flows.addButtons(this::add, font);
        networks.addButtons(this::add, font);
        networks.createBoxes(font).forEach(this::addRenderableWidget);
        refresh();
        if (tab == Tab.LIST) {
            keys.focusSearch();
        }
    }

    /** {@code owner} nulo: visível em todas as abas. */
    private FlatButton add(@Nullable Tab owner, FlatButton button) {
        return addRenderableWidget(tabs.register(owner, button));
    }

    /** Visibilidade e estado que dependem da aba e do snapshot. */
    private void refresh() {
        if (menu.version() != lastVersion) {
            lastVersion = menu.version();
            onSnapshot();
        }
        tabs.refresh(tab, searchBox);
    }

    /**
     * Snapshot novo: a seleção que sumiu sai e o aviso aparece. A aba Redes só lê o cabeçalho e só se remonta quando
     * ele chega, não a cada página ou amostra ao vivo da aba Máquina.
     */
    private void onSnapshot() {
        TabletSnapshot s = snapshot();
        frame.onSnapshot(s);
        if (menu.headerVersion() != lastHeader) {
            lastHeader = menu.headerVersion();
            networks.onSnapshot(s, font);
        }
        selectBar.onSnapshot(s);
        list.onSnapshot();
    }

    // ------------------------------------------------------------------ abas

    /** Sem pedir a visão: o link dos Fluxos filtra os nós na visão em que a Lista estiver. */
    private void switchTab(Tab t) {
        if (tab != t) {
            list.cancelLabel();
            tab = t;
            actions.tab(t.ordinal());
            if (t == Tab.FLOWS) {
                flows.shown();
            }
            setFocused(null);
            if (t == Tab.LIST) {
                keys.focusSearch();
            }
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

    /** O acesso à rede escolhida (convidados e times), para os ganchos do e2e e das capturas. */
    public AccessPanel access() {
        return networks.access();
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

    /** A janela aberta da aba Redes grava (a cor) antes do menu fechar: o servidor recusa o que vem depois. */
    @Override
    public void onClose() {
        networks.closeWindows();
        super.onClose();
    }

    @Override
    public void removed() {
        networks.closeWindows();
        PanelNotices.clear();
        super.removed();
    }

    /** A grade de ícones aberta na aba Redes, onde o JEI solta um item ({@code x, y, w, h}), ou {@code null}. */
    public int @Nullable [] ghostBounds() {
        return tab == Tab.NETWORKS ? networks.ghostBounds() : null;
    }

    /** Um ingrediente solto do JEI na grade de ícones (só item conta). */
    public void acceptGhost(Object ingredient) {
        networks.acceptGhost(ingredient);
    }

    // ------------------------------------------------------------------ geometria

    public int themeX() {
        return frame.themeX();
    }

    public int themeY() {
        return frame.themeY();
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
        if (tab == Tab.NETWORKS && networks.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        if (tab == Tab.LIST && list.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && body.mouseClicked(tab, mouseX, mouseY)) {
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (resize.dragging()) {
            if (resize.drag(mouseX, mouseY, width, height)) {
                rebuild();
            }
            return true;
        }
        if (body.mouseDragged(tab, mouseX, mouseY, dragX, dragY)) {
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
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && body.mouseReleased(tab, mouseX, mouseY)) {
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    /**
     * Refaz os widgets no tamanho lembrado: aba, filtros, seleção e rolagens ficam em campos e sobrevivem; o nome em
     * edição e a janela aberta da aba Redes fecham sem gravar.
     */
    private void rebuild() {
        setFocused(null);
        rebuildWidgets();
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
        return keys.keyPressed(keyCode, scanCode, modifiers) || super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        return keys.charTyped(codePoint, modifiers) || super.charTyped(codePoint, modifiers);
    }

    // ------------------------------------------------------------------ desenho

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        keys.ink();
        refresh();
        GuiText.beginFrame();
        boolean popup = tab == Tab.LIST && list.panel() != null && list.panel().popupOpen()
                || tab == Tab.NETWORKS && networks.modalOpen();
        super.render(g, popup ? -1 : mouseX, popup ? -1 : mouseY, partialTick);
        if (tab == Tab.LIST) {
            list.renderOverlay(g, mouseX, mouseY);
        }
        // o aviso antes da janela da aba Redes: os dois usam o z 300 do véu, e a janela fica por cima
        frame.renderNotice(g, font);
        if (tab == Tab.NETWORKS) {
            networks.renderOverlay(g, font, mouseX, mouseY);
        }
        if (popup) {
            Component modalTip = tab == Tab.NETWORKS ? networks.tooltip(mouseX, mouseY) : null;
            Component clipped = modalTip != null ? modalTip : GuiText.clipAt(mouseX, mouseY);
            if (clipped != null) {
                // desenhada aqui e não no fim do quadro: no z de sempre, os itens da janela a cobririam
                ModalLayer.tooltip(g, font, clipped, mouseX, mouseY);
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
        frame.render(g, font, mouseX, mouseY);
        body.render(tab, g, font, mouseX, mouseY);
        // alça de redimensionar: três riscos na diagonal, acesos com o mouse em cima ou arrastando
        ResizeGrip.renderDotted(g, leftPos, topPos, layout.w, layout.h, resize.hover(mouseX, mouseY), GuiPaint.FG);
    }

    // ------------------------------------------------------------------ captura e teste de ponta a ponta

    public void previewTab(Tab t) {
        switchTab(t);
    }

    /** Liga ou desliga a Grade da Lista (captura sem mouse). */
    public void previewGrid(boolean on) {
        list.previewGrid(on);
    }

    public void previewMulti(boolean on, int count) {
        list.previewMulti(on, count);
    }

    /** O que o Selecionar marcou, {@code {roteadores, máquinas}} (gancho do e2e). */
    public int[] markCounts() {
        return new int[] {list.marks.routers(), list.marks.machineCount()};
    }

    /** Centro do "Colar nos marcados", ou {@code null} fora da vista (gancho do e2e). */
    public int @Nullable [] pasteMarkedCenter() {
        return selectBar.pasteCenter();
    }

    public void previewMapSelect(int index) {
        map.previewSelect(index);
    }

    /** Centro da máquina {@code machine} do roteador escolhido no Mapa, ou {@code null} (gancho do e2e). */
    public int @Nullable [] mapMachineCenter(int machine) {
        return map.machineCenter(machine);
    }

    /** Quantas máquinas e pixels de linha o Mapa desenha (gancho do e2e). */
    public int mapMachinesDrawn() {
        return map.machinesDrawn();
    }

    /** O quanto o Mapa foi movido do jogador, em blocos {@code {x, z}} (gancho do e2e). */
    public double[] mapPan() {
        return map.pan();
    }

    /** Um ponto vazio do Mapa, perto do canto de baixo à esquerda (gancho do e2e). */
    public int[] mapEmptyPoint() {
        return map.emptyPoint();
    }

    public void previewNetwork(int index) {
        networks.preview(index);
    }

    public void previewGroup(int index) {
        networks.previewGroup(index);
    }

    /** A aba Redes, para os ganchos do e2e e das capturas. */
    public NetworksPanel networks() {
        return networks;
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

    public EditBox searchBox() {
        return searchBox;
    }
}
